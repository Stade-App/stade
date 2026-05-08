package app.stade.contact

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.stade.crypto.CryptoApi
import app.stade.db.StadeDb
import app.stade.identity.LocalIdentity
import app.stade.identity.StadeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ContactManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun observeContacts(ownerId: String): Flow<List<Contact>> =
        db.stadeDbQueries.selectContacts(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun all(): List<Contact> =
        db.stadeDbQueries.selectAllContacts().executeAsList().map { it.toDomain() }

    fun get(id: String): Contact? =
        db.stadeDbQueries.selectContact(id).executeAsOneOrNull()?.toDomain()

    fun findByPublicKey(key: ByteArray): Contact? =
        db.stadeDbQueries.selectContactByPublicKey(key).executeAsOneOrNull()?.toDomain()

    fun findByStadeId(stadeId: String): Contact? =
        db.stadeDbQueries.selectContactByStadeId(stadeId).executeAsOneOrNull()?.toDomain()

    suspend fun addFromHandshake(
        owner: LocalIdentity,
        nickname: String,
        peerSigningKey: ByteArray,
        peerHandshakeKey: ByteArray,
        peerMlKemKey: ByteArray,
        peerMlDsaKey: ByteArray,
        rootKey: ByteArray,
        isAlice: Boolean,
        addresses: List<String> = emptyList()
    ): Contact = withContext(Dispatchers.Default) {
        val id = StadeId.derive(peerSigningKey, peerMlDsaKey, crypto::hash)
        val now = Clock.System.now().toEpochMilliseconds()
        val addrJoined = addresses.filter { it.isNotBlank() }.joinToString("\n")
        db.stadeDbQueries.insertContact(
            id, owner.id, nickname, peerSigningKey, peerHandshakeKey,
            peerMlKemKey, peerMlDsaKey,
            rootKey, null, if (isAlice) 1 else 0, 0, 0L, now, addrJoined
        )
        Contact(
            id = id,
            ownerId = owner.id,
            nickname = nickname,
            publicSigningKey = peerSigningKey,
            publicHandshakeKey = peerHandshakeKey,
            publicMlKemKey = peerMlKemKey,
            publicMlDsaKey = peerMlDsaKey,
            rootKey = rootKey,
            ratchetState = null,
            isAlice = isAlice,
            verified = false,
            lastSeen = 0L,
            createdAt = now,
            addresses = addrJoined.split("\n").filter { it.isNotBlank() }
        )
    }

    fun setAddresses(contactId: String, addresses: List<String>) {
        val joined = addresses.filter { it.isNotBlank() }.distinct().joinToString("\n")
        db.stadeDbQueries.setContactAddresses(joined, contactId)
    }

    fun saveRatchet(contactId: String, snapshot: ByteArray) {
        db.stadeDbQueries.setContactRatchet(snapshot, contactId)
    }

    fun markSeen(contactId: String, timestamp: Long) {
        db.stadeDbQueries.setContactSeen(timestamp, contactId)
    }

    fun rename(contactId: String, nickname: String) {
        db.stadeDbQueries.renameContact(nickname, contactId)
    }

    fun verify(contactId: String) {
        db.stadeDbQueries.setContactVerified(contactId)
    }

    fun delete(contactId: String) {
        db.stadeDbQueries.deleteContact(contactId)
    }

    fun purge(contactId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteOutboxForContact(contactId)
            db.stadeDbQueries.deleteMessagesForContact(contactId)
            db.stadeDbQueries.deleteContact(contactId)
        }
    }

    private fun app.stade.db.Contact.toDomain(): Contact =
        Contact(
            id = id,
            ownerId = ownerId,
            nickname = nickname,
            publicSigningKey = publicKey,
            publicHandshakeKey = handshakePublicKey,
            publicMlKemKey = mlkemPublicKey,
            publicMlDsaKey = mldsaPublicKey,
            rootKey = rootKey,
            ratchetState = ratchetState,
            isAlice = isAlice == 1L,
            verified = verified == 1L,
            lastSeen = lastSeen,
            createdAt = createdAt,
            addresses = addresses.split("\n").filter { it.isNotBlank() }
        )
}
