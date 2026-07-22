package dev.stade.message

import dev.stade.contact.Contact
import dev.stade.identity.LocalIdentity
import dev.stade.sync.SyncEngine
import kotlinx.datetime.Clock

class ChatService(
    private val messages: MessageManager,
    private val sync: SyncEngine
) {
    suspend fun send(owner: LocalIdentity, contact: Contact, body: String, replyToId: String? = null): Message {
        val wireBody = if (replyToId != null) encodeReplyBody(replyToId, body) else body
        val now = Clock.System.now().toEpochMilliseconds()
        val msg = messages.saveOutgoing(contact.id, wireBody, now)
        runCatching {
            sync.queueOutgoing(owner, contact, msg.id, wireBody, now)
        }
        return msg
    }


    suspend fun sendImage(owner: LocalIdentity, contact: Contact, imageBytes: ByteArray, replyToId: String? = null): Message {
        val body = encodeImageBody(imageBytes)
        return send(owner, contact, body, replyToId)
    }

    suspend fun sendVoice(owner: LocalIdentity, contact: Contact, opusBytes: ByteArray, durationMs: Int, replyToId: String? = null): Message {
        val body = encodeVoiceBody(opusBytes, durationMs)
        return send(owner, contact, body, replyToId)
    }
}
