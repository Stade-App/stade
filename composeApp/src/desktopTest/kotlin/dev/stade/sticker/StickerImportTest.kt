package dev.stade.sticker

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun png(size: Int, shade: Int): ByteArray {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = Color(shade, shade, shade)
    g.fillRect(0, 0, size, size)
    g.dispose()
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

private fun gif(): ByteArray = "GIF89a".encodeToByteArray() + ByteArray(32)

private fun webpHeader(animated: Boolean): ByteArray {
    val out = ByteArray(32)
    "RIFF".encodeToByteArray().copyInto(out, 0)
    "WEBP".encodeToByteArray().copyInto(out, 8)
    "VP8X".encodeToByteArray().copyInto(out, 12)
    if (animated) out[20] = 0x02
    return out
}

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

class StickerImportTest {

    @Test
    fun formatsAreRecognisedByTheirHeaders() {
        assertTrue(isPngBytes(png(8, 10)))
        assertTrue(isWebpBytes(webpHeader(animated = false)))
        assertTrue(isAnimatedWebpBytes(webpHeader(animated = true)))
        assertFalse(isAnimatedWebpBytes(webpHeader(animated = false)))
        assertFalse(isWebpBytes("not an image".encodeToByteArray()))
        assertTrue(isZipArchive(zipOf("a.png" to png(8, 20))))
        assertFalse(isZipArchive(png(8, 20)))
    }

    @Test
    fun animatedArtworkKeepsItsOwnFramesAndStillArtIsReEncoded() {
        assertTrue(keepsItsOwnFrames(gif()))
        assertTrue(keepsItsOwnFrames(webpHeader(animated = true)))
        assertFalse(keepsItsOwnFrames(png(8, 30)))

        val still = png(600, 40)
        val normalized = normalizeStickerBytes(still)
        assertTrue(normalized != null && isPngBytes(normalized))

        val animated = gif()
        assertEquals(animated.toList(), normalizeStickerBytes(animated)?.toList())
    }

    @Test
    fun junkAndOversizedFilesAreRefused() {
        assertEquals(null, normalizeStickerBytes("hello".encodeToByteArray()))
        assertEquals(null, normalizeStickerBytes(ByteArray(0)))
        assertEquals(null, normalizeStickerBytes(ByteArray(MAX_IMPORT_ENTRY_BYTES + 1)))
    }

    @Test
    fun aWaStickersArchiveYieldsItsPackAndTitle() {
        val archive = zipOf(
            "identifier" to "Cat Pack".encodeToByteArray(),
            "author" to "Someone".encodeToByteArray(),
            "tray.png" to png(96, 50),
            "01.png" to png(200, 60),
            "02.png" to png(200, 70)
        )
        val outcome = StickerArchive.read("cats.wastickers", archive)
        assertTrue(outcome is ImportSourceOutcome.Pack, "expected a pack, got $outcome")
        val pack = (outcome as ImportSourceOutcome.Pack).pack
        assertEquals("Cat Pack", pack.title)
        assertEquals("Someone", pack.author)
        assertEquals(2, pack.images.size, "the tray icon must not become a sticker")
    }

    @Test
    fun aPackWithoutMetadataIsNamedAfterItsFile() {
        val archive = zipOf("a.png" to png(120, 80), "b.png" to png(120, 90))
        val outcome = StickerArchive.read("my_holiday-pack.wastickers", archive)
        val pack = (outcome as ImportSourceOutcome.Pack).pack
        assertEquals("my holiday pack", pack.title)
        assertEquals(2, pack.images.size)
    }

    @Test
    fun aJsonManifestSuppliesTheNameAndPublisher() {
        val manifest = """{"identifier":"x","name":"Blue Set","publisher":"Studio"}"""
        val archive = zipOf(
            "contents.json" to manifest.encodeToByteArray(),
            "one.png" to png(100, 110)
        )
        val pack = (StickerArchive.read("set.wastickers", archive) as ImportSourceOutcome.Pack).pack
        assertEquals("Blue Set", pack.title)
        assertEquals("Studio", pack.author)
    }

    @Test
    fun archivesWithoutAnyArtworkAreRejected() {
        val archive = zipOf("readme.txt" to "nothing here".encodeToByteArray())
        assertEquals(
            ImportSourceOutcome.Rejected(ImportRejection.Empty),
            StickerArchive.read("empty.wastickers", archive)
        )
    }

    @Test
    fun somethingThatIsNotAnArchiveIsRejected() {
        assertEquals(
            ImportSourceOutcome.Rejected(ImportRejection.NotReadable),
            StickerArchive.read("photo.png", png(64, 120))
        )
    }

    @Test
    fun anEnormousArchiveIsRefusedBeforeItIsOpened() {
        val huge = ByteArray((MAX_IMPORT_ARCHIVE_BYTES + 1).toInt()) { 0 }
        assertEquals(
            ImportSourceOutcome.Rejected(ImportRejection.TooLarge),
            StickerArchive.read("huge.wastickers", huge)
        )
    }

    @Test
    fun anArchiveNeverYieldsMoreThanTheEntryLimit() {
        val entries = (0..MAX_IMPORT_ENTRIES + 20)
            .map { "s$it.png" to png(40, it % 200) }
            .toTypedArray()
        val pack = (StickerArchive.read("big.wastickers", zipOf(*entries)) as ImportSourceOutcome.Pack).pack
        assertTrue(pack.images.size <= MAX_IMPORT_ENTRIES, "got ${pack.images.size}")
    }
}
