package dev.stade.chat

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class PinnedChats(private val db: StadeDb) {
    private fun kvKey(ownerId: String, chatKey: String) = "pin.$ownerId.$chatKey"

    fun setPinned(ownerId: String, chatKey: String, pinned: Boolean) {
        val key = kvKey(ownerId, chatKey)
        if (pinned) {
            val ts = Clock.System.now().toEpochMilliseconds()
            db.stadeDbQueries.putKv(key, ts.toString().encodeToByteArray())
        } else {
            db.stadeDbQueries.deleteKv(key)
        }
    }

    fun observePinned(ownerId: String): Flow<Map<String, Long>> {
        val prefix = "pin.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.associate { row ->
                    row.key.removePrefix(prefix) to (row.value_.decodeToString().toLongOrNull() ?: 0L)
                }
            }
    }
}
