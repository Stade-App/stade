package dev.stade.sticker

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class StickerManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun create(ownerId: String, bytes: ByteArray): Sticker {
        val id = Encoding.toHex(crypto.randomBytes(16))
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertSticker(id, ownerId, bytes, now)
        return Sticker(id, ownerId, bytes, now)
    }

    fun observeStickers(ownerId: String): Flow<List<Sticker>> =
        db.stadeDbQueries.selectStickers(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun delete(id: String) {
        db.stadeDbQueries.deleteSticker(id)
    }

    fun observePacks(ownerId: String): Flow<List<StickerPack>> =
        db.stadeDbQueries.selectStickerPacks(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun deletePack(packId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteStickersInPack(packId)
            db.stadeDbQueries.deleteStickerPack(packId)
        }
    }

    fun importPack(ownerId: String, pack: ImportedPack, normalize: (ByteArray) -> ByteArray?): ImportSummary {
        val packId = Encoding.toHex(crypto.randomBytes(16))
        val now = Clock.System.now().toEpochMilliseconds()
        var added = 0
        var duplicates = 0
        var unreadable = 0
        val seen = mutableSetOf<String>()

        val prepared = pack.images.mapNotNull { image ->
            val normalized = runCatching { normalize(image.bytes) }.getOrNull()
            if (normalized == null || normalized.isEmpty()) {
                unreadable++
                return@mapNotNull null
            }
            val hash = Encoding.toHex(crypto.hash(normalized))
            if (!seen.add(hash)) {
                duplicates++
                return@mapNotNull null
            }
            if (db.stadeDbQueries.stickerHashCount(ownerId, hash).executeAsOne() > 0L) {
                duplicates++
                return@mapNotNull null
            }
            normalized to hash
        }

        if (prepared.isEmpty()) {
            return ImportSummary(pack.title, 0, duplicates, unreadable)
        }

        db.stadeDbQueries.transaction {
            db.stadeDbQueries.insertStickerPack(packId, ownerId, pack.title, pack.author, ORIGIN_FILE, now)
            prepared.forEachIndexed { index, (bytes, hash) ->
                val id = Encoding.toHex(crypto.randomBytes(16))
                db.stadeDbQueries.insertPackSticker(id, ownerId, bytes, now + index, packId, hash)
                added++
            }
        }
        return ImportSummary(pack.title, added, duplicates, unreadable)
    }

    fun importLoose(ownerId: String, images: List<ImportedImage>, normalize: (ByteArray) -> ByteArray?): ImportSummary {
        val now = Clock.System.now().toEpochMilliseconds()
        var added = 0
        var duplicates = 0
        var unreadable = 0
        val seen = mutableSetOf<String>()

        val prepared = images.mapNotNull { image ->
            val normalized = runCatching { normalize(image.bytes) }.getOrNull()
            if (normalized == null || normalized.isEmpty()) {
                unreadable++
                return@mapNotNull null
            }
            val hash = Encoding.toHex(crypto.hash(normalized))
            if (!seen.add(hash)) {
                duplicates++
                return@mapNotNull null
            }
            if (db.stadeDbQueries.stickerHashCount(ownerId, hash).executeAsOne() > 0L) {
                duplicates++
                return@mapNotNull null
            }
            normalized to hash
        }

        if (prepared.isNotEmpty()) {
            db.stadeDbQueries.transaction {
                prepared.forEachIndexed { index, (bytes, hash) ->
                    val id = Encoding.toHex(crypto.randomBytes(16))
                    db.stadeDbQueries.insertPackSticker(id, ownerId, bytes, now + index, null, hash)
                    added++
                }
            }
        }
        return ImportSummary("", added, duplicates, unreadable)
    }

    private fun dev.stade.db.Sticker.toDomain(): Sticker =
        Sticker(id = id, ownerId = ownerId, imageBytes = imageBytes, createdAt = createdAt, packId = packId)

    private fun dev.stade.db.StickerPack.toDomain(): StickerPack =
        StickerPack(
            id = id,
            ownerId = ownerId,
            title = title,
            author = author,
            origin = origin,
            createdAt = createdAt
        )

    companion object {
        const val ORIGIN_FILE = "file"
    }
}
