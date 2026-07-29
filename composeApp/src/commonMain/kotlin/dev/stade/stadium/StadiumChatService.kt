package dev.stade.stadium

import dev.stade.contact.ContactManager
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity
import dev.stade.message.encodeImageBody
import dev.stade.message.encodeVideoBody
import dev.stade.message.encodeVoiceBody
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class StadiumChatService(
    private val stadiums: StadiumManager,
    private val sync: SyncEngine,
    private val contacts: ContactManager,
    private val crypto: CryptoApi
) {
    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            when (event) {
                is SyncEngine.SyncEvent.ContactConnected -> {
                    val contact = contacts.get(event.contactId) ?: return@onEach
                    val pending = stadiums.getPendingJoinForContact(contact.id) ?: return@onEach
                    if (event.isNew) runCatching { contacts.setKind(contact.id, 1) }
                    sendJoinRequest(owner, contact.id, pending)
                }
                is SyncEngine.SyncEvent.StadiumContactReleased -> {
                    scope.launch {
                        delay(2_000)
                        val stillUsed = stadiums.getPendingJoinForContact(event.contactId) != null ||
                            stadiums.allStadiums(owner.id).any { it.creatorStadeId == event.contactId } ||
                            stadiums.stadiumsForContact(event.contactId).isNotEmpty()
                        if (!stillUsed) {
                            if (event.forget) sync.forgetContact(event.contactId)
                            else sync.disconnectContact(event.contactId)
                            runCatching { contacts.purge(event.contactId) }
                        }
                    }
                }
                else -> Unit
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

    suspend fun postImage(owner: LocalIdentity, stadium: StadiumInfo, imageBytes: ByteArray, caption: String = ""): Boolean =
        post(owner, stadium, encodeImageBody(imageBytes, caption))

    suspend fun postVoice(owner: LocalIdentity, stadium: StadiumInfo, opusBytes: ByteArray, durationMs: Int): Boolean =
        post(owner, stadium, encodeVoiceBody(opusBytes, durationMs))

    suspend fun postVideo(owner: LocalIdentity, stadium: StadiumInfo, videoBytes: ByteArray, caption: String = ""): Boolean =
        post(owner, stadium, encodeVideoBody(videoBytes, caption))

    suspend fun leave(owner: LocalIdentity, stadium: StadiumInfo): Boolean {
        if (stadium.isOwner) return false
        val ownerContact = contacts.get(stadium.creatorStadeId)
        if (ownerContact != null) {
            val msgId = Encoding.toHex(crypto.randomBytes(16))
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val body = "$STD_LEAVE_PREFIX${stadium.id}"
            val queued = runCatching { sync.queueOutgoing(owner, ownerContact, msgId, body, timestamp) }.isSuccess
            stadiums.leaveStadium(stadium.id)
            if (queued && ownerContact.kind == 1 &&
                stadiums.allStadiums(owner.id).none { it.creatorStadeId == ownerContact.id }
            ) {
                stadiums.markPendingFarewell(ownerContact.id, msgId)
            }
        } else {
            stadiums.leaveStadium(stadium.id)
        }
        return true
    }

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
