package app.stade.sync

import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.db.StadeDb
import kotlinx.datetime.Clock

data class OutboxItem(
    val id: String,
    val contactId: String,
    val messageId: String,
    val payload: ByteArray,
    val attempts: Int,
    val createdAt: Long
)

class Outbox(private val db: StadeDb, private val crypto: CryptoApi) {

    fun enqueue(contactId: String, messageId: String, payload: ByteArray): OutboxItem {
        val id = Encoding.toHex(crypto.randomBytes(16))
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.enqueueOutbox(id, contactId, messageId, payload, 0, now)
        return OutboxItem(id, contactId, messageId, payload, 0, now)
    }

    fun pending(contactId: String): List<OutboxItem> =
        db.stadeDbQueries.selectOutbox(contactId).executeAsList().map {
            OutboxItem(it.id, it.contactId, it.messageId, it.payload, it.attempts.toInt(), it.createdAt)
        }

    fun all(): List<OutboxItem> =
        db.stadeDbQueries.selectAllOutbox().executeAsList().map {
            OutboxItem(it.id, it.contactId, it.messageId, it.payload, it.attempts.toInt(), it.createdAt)
        }

    fun bump(id: String) { db.stadeDbQueries.bumpOutbox(id) }
    fun remove(id: String) { db.stadeDbQueries.deleteOutbox(id) }
    fun removeForMessage(messageId: String) { db.stadeDbQueries.deleteOutboxForMessage(messageId) }
}
