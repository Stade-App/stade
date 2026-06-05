package app.stade.message

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun observeMessages(contactId: String): Flow<List<Message>> =
        db.stadeDbQueries.selectMessages(contactId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toMessage() } }

    fun observeLastMessage(contactId: String): Flow<Message?> =
        db.stadeDbQueries.selectLastMessage(contactId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toMessage() }

    fun observeUnreadCount(contactId: String): Flow<Long> =
        db.stadeDbQueries.countUnread(contactId)
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun lastMessage(contactId: String): Message? =
        db.stadeDbQueries.selectLastMessage(contactId).executeAsOneOrNull()?.toMessage()

    fun unreadCount(contactId: String): Long =
        db.stadeDbQueries.countUnread(contactId).executeAsOne()

    fun observeTotalUnread(): Flow<Long> =
        db.stadeDbQueries.countAllUnread()
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun totalUnread(): Long =
        db.stadeDbQueries.countAllUnread().executeAsOne()

    fun exists(messageId: String): Boolean =
        db.stadeDbQueries.messageExists(messageId).executeAsOne() > 0L

    fun markRead(contactId: String) {
        db.stadeDbQueries.markRead(contactId)
    }

    fun markDelivered(messageId: String) {
        db.stadeDbQueries.markDelivered(messageId)
        db.stadeDbQueries.deleteOutboxForMessage(messageId)
    }

    fun saveOutgoing(contactId: String, body: String, timestamp: Long): Message {
        val id = Encoding.toHex(crypto.randomBytes(16))
        db.stadeDbQueries.insertMessage(id, contactId, "OUT", body, timestamp, 0, 1)
        return Message(id, contactId, MessageDirection.OUT, body, timestamp, false, true)
    }

    fun saveIncoming(messageId: String, contactId: String, body: String, timestamp: Long): Message? {
        val exists = db.stadeDbQueries.messageExists(messageId).executeAsOne() > 0L
        if (exists) return null
        db.stadeDbQueries.insertMessage(messageId, contactId, "IN", body, timestamp, 1, 0)
        return Message(messageId, contactId, MessageDirection.IN, body, timestamp, true, false)
    }

    fun deleteMessages(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        db.stadeDbQueries.transaction {
            messageIds.forEach { id ->
                db.stadeDbQueries.deleteOutboxForMessage(id)
                db.stadeDbQueries.deleteMessageById(id)
            }
        }
    }

    private fun app.stade.db.Message.toMessage(): Message =
        Message(
            id, contactId,
            if (direction == "IN") MessageDirection.IN else MessageDirection.OUT,
            body, timestamp, delivered == 1L, read == 1L
        )
}


