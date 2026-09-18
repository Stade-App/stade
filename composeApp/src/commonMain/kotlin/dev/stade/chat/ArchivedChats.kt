package dev.stade.chat

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

private const val AUTO_UNARCHIVE_KEY = "arch.autoUnarchive"

class ArchivedChats(private val db: StadeDb) {
    private fun kvKey(ownerId: String, chatKey: String) = "arch.$ownerId.$chatKey"

    fun setArchived(ownerId: String, chatKey: String, archived: Boolean) {
        val key = kvKey(ownerId, chatKey)
        if (archived) {
            val ts = Clock.System.now().toEpochMilliseconds()
            db.stadeDbQueries.putKv(key, ts.toString().encodeToByteArray())
        } else {
            db.stadeDbQueries.deleteKv(key)
        }
    }

    fun observeArchived(ownerId: String): Flow<Set<String>> {
        val prefix = "arch.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapTo(mutableSetOf()) { it.key.removePrefix(prefix) } }
    }

    fun autoUnarchiveOnMessage(): Boolean =
        runCatching {
            db.stadeDbQueries.getKv(AUTO_UNARCHIVE_KEY).executeAsOneOrNull()
        }.getOrNull()?.decodeToString() == "1"

    fun observeAutoUnarchive(): Flow<Boolean> =
        db.stadeDbQueries.selectKvPrefixed(AUTO_UNARCHIVE_KEY)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.any { it.key == AUTO_UNARCHIVE_KEY && it.value_.decodeToString() == "1" } }

    fun setAutoUnarchiveOnMessage(enabled: Boolean) {
        runCatching {
            if (enabled) {
                db.stadeDbQueries.putKv(AUTO_UNARCHIVE_KEY, "1".encodeToByteArray())
            } else {
                db.stadeDbQueries.deleteKv(AUTO_UNARCHIVE_KEY)
            }
        }
    }

    fun archived(ownerId: String): Set<String> {
        val prefix = "arch.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .executeAsList()
            .mapTo(mutableSetOf()) { it.key.removePrefix(prefix) }
    }
}
