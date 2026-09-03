package dev.stade.message

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ScheduledMessageInfo(
    val id: String,
    val contactId: String,
    val body: String,
    val replyToId: String?,
    val scheduledAt: Long,
    val createdAt: Long
)

class ScheduledMessageManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun schedule(contactId: String, body: String, replyToId: String?, scheduledAt: Long, now: Long): ScheduledMessageInfo {
        val id = Encoding.toHex(crypto.randomBytes(16))
        db.stadeDbQueries.insertScheduledMessage(id, contactId, body, replyToId, scheduledAt, now)
        return ScheduledMessageInfo(id, contactId, body, replyToId, scheduledAt, now)
    }

    fun observeForContact(contactId: String): Flow<List<ScheduledMessageInfo>> =
        db.stadeDbQueries.selectScheduledForContact(contactId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toInfo() } }

    fun observeCountForContact(contactId: String): Flow<Long> =
        db.stadeDbQueries.countScheduledForContact(contactId)
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun countForContact(contactId: String): Long =
        db.stadeDbQueries.countScheduledForContact(contactId).executeAsOne()

    fun due(now: Long): List<ScheduledMessageInfo> =
        db.stadeDbQueries.selectDueScheduled(now).executeAsList().map { it.toInfo() }

    fun delete(id: String) {
        db.stadeDbQueries.deleteScheduledMessage(id)
    }

    private fun dev.stade.db.ScheduledMessage.toInfo(): ScheduledMessageInfo =
        ScheduledMessageInfo(id, contactId, body, replyToId, scheduledAt, createdAt)
}
