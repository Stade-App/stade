package dev.stade.sticker

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private const val TRAY_MARKER = "tray"

object StickerArchive {

    fun read(fileName: String, bytes: ByteArray): ImportSourceOutcome {
        if (bytes.size > MAX_IMPORT_ARCHIVE_BYTES) {
            return ImportSourceOutcome.Rejected(ImportRejection.TooLarge)
        }
        if (!isZipArchive(bytes)) return ImportSourceOutcome.Rejected(ImportRejection.NotReadable)

        val images = mutableListOf<ImportedImage>()
        var title = ""
        var author = ""

        val read = runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name.isBlank() || name.startsWith(".")) continue
                    if (images.size >= MAX_IMPORT_ENTRIES) break

                    val content = readCapped(zip, MAX_IMPORT_ENTRY_BYTES) ?: continue
                    when {
                        name.equals("identifier", true) || name.equals("title", true) ->
                            title = content.decodeToString().trim()
                        name.equals("author", true) ->
                            author = content.decodeToString().trim()
                        name.endsWith(".json", true) -> parseManifest(content)?.let { meta ->
                            if (title.isBlank()) title = meta.first
                            if (author.isBlank()) author = meta.second
                        }
                        looksLikeSticker(content) -> {
                            if (!name.substringBeforeLast('.').equals(TRAY_MARKER, true)) {
                                images.add(ImportedImage(name, content))
                            }
                        }
                    }
                }
            }
        }
        if (read.isFailure) return ImportSourceOutcome.Rejected(ImportRejection.NotReadable)
        if (images.isEmpty()) return ImportSourceOutcome.Rejected(ImportRejection.Empty)

        return ImportSourceOutcome.Pack(
            ImportedPack(
                title = title.ifBlank { packTitleFromFileName(fileName) },
                author = author,
                images = images
            )
        )
    }

    private fun readCapped(zip: ZipInputStream, cap: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (out.size() + n > cap) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun parseManifest(content: ByteArray): Pair<String, String>? = runCatching {
        val text = content.decodeToString()
        val name = firstField(text, listOf("name", "title", "identifier"))
        val publisher = firstField(text, listOf("publisher", "author"))
        if (name == null && publisher == null) null
        else (name.orEmpty().trim() to publisher.orEmpty().trim())
    }.getOrNull()

    private fun firstField(text: String, keys: List<String>): String? {
        for (key in keys) {
            val match = Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(text)
            val value = match?.groupValues?.get(1)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}

actual fun readStickerArchive(fileName: String, bytes: ByteArray): ImportSourceOutcome =
    StickerArchive.read(fileName, bytes)
