package app.stade.crypto

import app.stade.contact.Contact
import app.stade.contact.ContactManager
import app.stade.identity.LocalIdentity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class RatchetSessions(
    private val crypto: CryptoApi,
    private val contacts: ContactManager
) {
    private val ratchet = DoubleRatchet(crypto)
    private val states = mutableMapOf<String, DoubleRatchet.State>()
    private val locks = mutableMapOf<String, Mutex>()
    private val locksMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun lockFor(contactId: String): Mutex =
        locksMutex.withLock { locks.getOrPut(contactId) { Mutex() } }

    private fun loadOrInit(owner: LocalIdentity, contact: Contact): DoubleRatchet.State {
        states[contact.id]?.let { return it }
        val saved = contact.ratchetState
        val state = if (saved != null) {
            val snap = json.decodeFromString(RatchetSnapshot.serializer(), saved.decodeToString())
            RatchetSerializer.fromSnapshot(snap)
        } else {
            if (contact.isAlice) {
                ratchet.initAlice(contact.rootKey, contact.publicHandshakeKey)
            } else {
                ratchet.initBob(
                    contact.rootKey,
                    KeyPair(owner.publicHandshakeKey, owner.privateHandshakeKey)
                )
            }
        }
        states[contact.id] = state
        return state
    }

    private fun persist(contactId: String, state: DoubleRatchet.State) {
        val snap = RatchetSerializer.toSnapshot(state)
        val bytes = json.encodeToString(RatchetSnapshot.serializer(), snap).encodeToByteArray()
        contacts.saveRatchet(contactId, bytes)
    }

    suspend fun seal(owner: LocalIdentity, contact: Contact, plaintext: ByteArray): ByteArray =
        lockFor(contact.id).withLock {
            // Kişinin DB durumu güncellenmiş olabilir; en taze ratchetState'i çek.
            val fresh = contacts.get(contact.id) ?: contact
            val state = loadOrInit(owner, fresh)
            val out = ratchet.encrypt(state, plaintext, contact.id.encodeToByteArray())
            persist(contact.id, state)
            out
        }

    suspend fun open(owner: LocalIdentity, contact: Contact, frame: ByteArray): ByteArray? =
        lockFor(contact.id).withLock {
            val fresh = contacts.get(contact.id) ?: contact
            val state = loadOrInit(owner, fresh)
            val out = ratchet.decrypt(state, frame, contact.id.encodeToByteArray())
            if (out != null) persist(contact.id, state)
            out
        }

    suspend fun forget(contactId: String) {
        locksMutex.withLock {
            states.remove(contactId)
            locks.remove(contactId)
        }
    }
}
