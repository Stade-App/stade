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

    private fun dev.stade.db.Sticker.toDomain(): Sticker =
        Sticker(id = id, ownerId = ownerId, imageBytes = imageBytes, createdAt = createdAt)
}
