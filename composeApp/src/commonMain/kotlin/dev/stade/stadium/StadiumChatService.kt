package dev.stade.stadium

import dev.stade.contact.ContactManager
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity
import dev.stade.message.encodeImageBody
import dev.stade.message.encodeVoiceBody
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

class StadiumChatService(
    private val stadiums: StadiumManager,
    private val sync: SyncEngine,
    private val contacts: ContactManager,
    private val crypto: CryptoApi
) {
    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            if (event is SyncEngine.SyncEvent.ContactConnected) {
                val contact = contacts.get(event.contactId) ?: return@onEach
                val pending = stadiums.getPendingJoinForContact(contact.id) ?: return@onEach
                sendJoinRequest(owner, contact.id, pending)
                stadiums.clearPendingJoin(contact.id)
            }
        }.launchIn(scope)
    }

    suspend fun post(owner: LocalIdentity, stadium: StadiumInfo, body: String): Boolean {
        if (!stadium.isOwner) return false
        val members = stadiums.getMemberContactIds(stadium.id)
        val messageId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val encodedBody = "$STD_MSG_PREFIX${stadium.id}:${members.size}\n$body"
        stadiums.saveOutgoing(messageId, stadium.id, body, timestamp)
        members.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching {
                sync.queueOutgoing(owner, contact, messageId, encodedBody, timestamp)
            }
        }
        return true
    }

    suspend fun postImage(owner: LocalIdentity, stadium: StadiumInfo, imageBytes: ByteArray): Boolean =
        post(owner, stadium, encodeImageBody(imageBytes))

    suspend fun postVoice(owner: LocalIdentity, stadium: StadiumInfo, opusBytes: ByteArray, durationMs: Int): Boolean =
        post(owner, stadium, encodeVoiceBody(opusBytes, durationMs))

    suspend fun sendJoinRequest(owner: LocalIdentity, creatorContactId: String, pending: PendingStadiumJoin) {
        val creator = contacts.get(creatorContactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$STD_JOIN_PREFIX${pending.stadiumId}:${pending.inviteToken}"
        runCatching {
            sync.queueOutgoing(owner, creator, msgId, body, timestamp)
        }
    }
}
