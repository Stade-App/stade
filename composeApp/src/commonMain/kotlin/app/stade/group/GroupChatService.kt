package app.stade.group

import app.stade.contact.ContactManager
import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.identity.LocalIdentity
import app.stade.sync.SyncEngine
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

    suspend fun sendMessage(owner: LocalIdentity, groupId: String, body: String): Boolean {
        val group = groups.getGroup(groupId) ?: return false
        val members = group.memberIds
        if (members.isEmpty()) return false
        val messageId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val encodedBody = "$GRP_MSG_PREFIX$groupId:${owner.stadeId}\n$body"
        groups.saveOutgoing(messageId, groupId, owner.stadeId, body, timestamp)
        members.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching {
                sync.queueOutgoing(owner, contact, messageId, encodedBody, timestamp)
            }
        }
        return true
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

