package dev.stade.chat

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

enum class StarScope(val tag: String) {
    DIRECT("d"),
    GROUP("g"),
    STADIUM("s");

    companion object {
        fun fromTag(tag: String): StarScope? = entries.firstOrNull { it.tag == tag }
    }
}

data class StarredRef(
    val messageId: String,
    val scope: StarScope,
    val chatId: String,
    val starredAt: Long
)

private const val SEP = "\u001f"

class StarredMessages(private val db: StadeDb) {
    private fun kvKey(ownerId: String, messageId: String) = "star.$ownerId.$messageId"

    fun setStarred(ownerId: String, messageId: String, scope: StarScope, chatId: String, starred: Boolean) {
        if (messageId.isBlank()) return
        val key = kvKey(ownerId, messageId)
        if (starred) {
            val ts = Clock.System.now().toEpochMilliseconds()
            val payload = listOf(scope.tag, chatId, ts.toString()).joinToString(SEP)
            db.stadeDbQueries.putKv(key, payload.encodeToByteArray())
        } else {
            db.stadeDbQueries.deleteKv(key)
        }
    }

    fun observeStarredIds(ownerId: String): Flow<Set<String>> {
        val prefix = "star.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapTo(mutableSetOf()) { it.key.removePrefix(prefix) } }
    }

    fun observeStarred(ownerId: String): Flow<List<StarredRef>> {
        val prefix = "star.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.mapNotNull { row -> parse(row.key.removePrefix(prefix), row.value_) }
                    .sortedByDescending { it.starredAt }
            }
    }

    fun starredIds(ownerId: String): Set<String> {
        val prefix = "star.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .executeAsList()
            .mapTo(mutableSetOf()) { it.key.removePrefix(prefix) }
    }

    fun unstarAll(ownerId: String) {
        val prefix = "star.$ownerId."
        db.stadeDbQueries.selectKvPrefixed("$prefix%").executeAsList().forEach { row ->
            runCatching { db.stadeDbQueries.deleteKv(row.key) }
        }
    }

    private fun parse(messageId: String, value: ByteArray): StarredRef? {
        val parts = runCatching { value.decodeToString().split(SEP) }.getOrNull() ?: return null
        if (parts.size < 3) return null
        val scope = StarScope.fromTag(parts[0]) ?: return null
        val ts = parts[2].toLongOrNull() ?: return null
        return StarredRef(messageId = messageId, scope = scope, chatId = parts[1], starredAt = ts)
    }
}
