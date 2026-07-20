package dev.stade.message

import dev.stade.contact.Contact
import dev.stade.identity.LocalIdentity
import dev.stade.sync.SyncEngine
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

    suspend fun sendVoice(owner: LocalIdentity, contact: Contact, opusBytes: ByteArray, durationMs: Int): Message {
        val body = encodeVoiceBody(opusBytes, durationMs)
        return send(owner, contact, body)
    }
}
