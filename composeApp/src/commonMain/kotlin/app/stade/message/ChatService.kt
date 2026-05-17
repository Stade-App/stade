package app.stade.message

import app.stade.contact.Contact
import app.stade.identity.LocalIdentity
import app.stade.sync.SyncEngine
import kotlinx.datetime.Clock

class ChatService(
    private val messages: MessageManager,
    private val sync: SyncEngine
) {
    suspend fun send(owner: LocalIdentity, contact: Contact, body: String): Message {
        val now = Clock.System.now().toEpochMilliseconds()
        val msg = messages.saveOutgoing(contact.id, body, now)
        runCatching {
            sync.queueOutgoing(owner, contact, msg.id, body, now)
        }
        return msg
    }


    suspend fun sendImage(owner: LocalIdentity, contact: Contact, imageBytes: ByteArray): Message {
        val body = encodeImageBody(imageBytes)
        return send(owner, contact, body)
    }
}
