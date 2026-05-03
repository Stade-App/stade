package app.stade.contact

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.db.StadeDb
import app.stade.identity.LocalIdentity
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

    suspend fun addFromHandshake(
        owner: LocalIdentity,
        nickname: String,
        peerSigningKey: ByteArray,
        peerHandshakeKey: ByteArray,
        rootKey: ByteArray,
        isAlice: Boolean
    ): Contact = withContext(Dispatchers.Default) {
        val id = Encoding.toHex(crypto.hash(peerSigningKey)).substring(0, 32)
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertContact(
            id, owner.id, nickname, peerSigningKey, peerHandshakeKey,
            rootKey, null, if (isAlice) 1 else 0, 0, 0L, now
        )
        Contact(
            id, owner.id, nickname, peerSigningKey, peerHandshakeKey, rootKey,
            null, isAlice, false, 0L, now
        )
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

    private fun app.stade.db.Contact.toDomain(): Contact =
        Contact(
            id = id,
            ownerId = ownerId,
            nickname = nickname,
            publicSigningKey = publicKey,
            publicHandshakeKey = handshakePublicKey,
            rootKey = rootKey,
            ratchetState = ratchetState,
            isAlice = isAlice == 1L,
            verified = verified == 1L,
            lastSeen = lastSeen,
            createdAt = createdAt
        )
}
