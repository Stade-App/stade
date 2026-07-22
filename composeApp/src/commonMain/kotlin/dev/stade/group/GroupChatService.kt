package dev.stade.group

import dev.stade.contact.ContactManager
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity
import dev.stade.message.encodeImageBody
import dev.stade.message.encodeReplyBody
import dev.stade.message.encodeVoiceBody
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

class GroupChatService(
    private val groups: GroupManager,
    private val sync: SyncEngine,
    private val contacts: ContactManager,
    private val crypto: CryptoApi
) {
    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            if (event is SyncEngine.SyncEvent.ContactConnected) {
                val contact = contacts.get(event.contactId) ?: return@onEach
                val pending = groups.getPendingJoinForContact(contact.id) ?: return@onEach
                sendJoinRequest(owner, contact.id, pending)
                groups.clearPendingJoin(contact.id)
            }
        }.launchIn(scope)
    }

    suspend fun sendMessage(owner: LocalIdentity, groupId: String, body: String, replyToId: String? = null): Boolean {
        val group = groups.getGroup(groupId) ?: return false
        val members = group.memberIds
        if (members.isEmpty()) return false
        val wireBody = if (replyToId != null) encodeReplyBody(replyToId, body) else body
        val messageId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val encodedBody = "$GRP_MSG_PREFIX$groupId:${owner.stadeId}\n$wireBody"
        groups.saveOutgoing(messageId, groupId, owner.stadeId, wireBody, timestamp)
        members.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching {
                sync.queueOutgoing(owner, contact, messageId, encodedBody, timestamp)
            }
        }
        return true
    }

    suspend fun sendImage(owner: LocalIdentity, groupId: String, imageBytes: ByteArray, replyToId: String? = null): Boolean {
        return sendMessage(owner, groupId, encodeImageBody(imageBytes), replyToId)
    }

    suspend fun sendVoice(owner: LocalIdentity, groupId: String, opusBytes: ByteArray, durationMs: Int, replyToId: String? = null): Boolean {
        return sendMessage(owner, groupId, encodeVoiceBody(opusBytes, durationMs), replyToId)
    }

    suspend fun kickMember(owner: LocalIdentity, group: GroupInfo, memberId: String): Boolean {
        if (group.creatorStadeId.isBlank() || group.creatorStadeId != owner.stadeId) return false
        val targets = group.memberIds.filter { it != owner.stadeId }
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$GRP_KICK_PREFIX${group.id}:$memberId"
        targets.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching { sync.queueOutgoing(owner, contact, msgId, body, timestamp) }
        }
        groups.removeMember(group.id, memberId)
        return true
    }

    suspend fun leaveGroup(owner: LocalIdentity, group: GroupInfo) {
        val targets = group.memberIds.filter { it != owner.stadeId }
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$GRP_LEAVE_PREFIX${group.id}"
        targets.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching { sync.queueOutgoing(owner, contact, msgId, body, timestamp) }
        }
        groups.leaveGroupLocally(group.id)
    }

    suspend fun sendJoinRequest(owner: LocalIdentity, creatorContactId: String, pending: PendingJoinData) {
        val creator = contacts.get(creatorContactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$GRP_JOIN_PREFIX${pending.groupId}:${pending.inviteToken}"
        runCatching {
            sync.queueOutgoing(owner, creator, msgId, body, timestamp)
        }
    }

    /**
     * Grup oluşturulduktan hemen sonra çağrılır. Davet edilen her kişiye
     * şifrelenmiş bir davet mesajı yollar. Karşı tarafta otomatik olarak
     * importGroupInvite tetiklenir ve bağlantı açıldığında join request gönderilir.
     */
    suspend fun sendGroupInviteToContact(
        owner: LocalIdentity,
        contactId: String,
        inviteCode: String
    ) {
        val contact = contacts.get(contactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$GRP_INV_PREFIX$inviteCode"
        runCatching {
            sync.queueOutgoing(owner, contact, msgId, body, timestamp)
        }
    }

    fun importGroupInvite(owner: LocalIdentity, code: String, onCreatorAlreadyContact: (PendingJoinData, String) -> Unit): GroupInviteData? {
        val data = groups.parseInviteLink(code) ?: return null
        val pending = PendingJoinData(data.groupId, data.groupName, data.inviteToken)
        groups.storePendingJoin(data.creatorStadeId, pending)
        val existingCreator = contacts.findByStadeId(data.creatorStadeId)
        if (existingCreator != null) {
            onCreatorAlreadyContact(pending, existingCreator.id)
        }
        return data
    }
}

