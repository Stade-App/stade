package dev.stade.identity

import dev.stade.contact.Contact
import dev.stade.contact.ContactManager
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.message.encodeAvatarBody
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

class AvatarService(
    private val identities: IdentityManager,
    private val sync: SyncEngine,
    private val contacts: ContactManager,
    private val crypto: CryptoApi
) {
    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            if (event is SyncEngine.SyncEvent.ContactConnected && event.isNew) {
                val avatar = identities.get(owner.id)?.avatar ?: return@onEach
                val contact = contacts.get(event.contactId) ?: return@onEach
                if (contact.kind != 0) return@onEach
                pushAvatar(owner, contact, avatar)
            }
        }.launchIn(scope)
    }

    suspend fun setMyAvatar(owner: LocalIdentity, bytes: ByteArray?) {
        identities.setAvatar(owner.id, bytes)
        contacts.contacts(owner.id).forEach { contact ->
            if (contact.kind == 0) {
                pushAvatar(owner, contact, bytes)
            }
        }
    }

    private suspend fun pushAvatar(owner: LocalIdentity, contact: Contact, bytes: ByteArray?) {
        val messageId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        sync.queueOutgoing(owner, contact, messageId, encodeAvatarBody(bytes), timestamp)
    }
}
