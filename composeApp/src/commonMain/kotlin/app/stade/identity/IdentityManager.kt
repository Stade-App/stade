package app.stade.identity

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class IdentityManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun observeIdentities(): Flow<List<LocalIdentity>> =
        db.stadeDbQueries.selectAllIdentities()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun create(nickname: String): LocalIdentity {
        val signing = crypto.generateSigningKeyPair()
        val handshake = crypto.generateAgreementKeyPair()
        val id = Encoding.toHex(crypto.hash(signing.publicKey)).substring(0, 32)
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertIdentity(
            id, nickname, signing.publicKey, signing.privateKey,
            handshake.publicKey, handshake.privateKey, now
        )
        return LocalIdentity(
            id, nickname, signing.publicKey, signing.privateKey,
            handshake.publicKey, handshake.privateKey, now
        )
    }

    fun get(id: String): LocalIdentity? =
        db.stadeDbQueries.selectIdentity(id).executeAsOneOrNull()?.toDomain()

    private fun app.stade.db.LocalIdentity.toDomain(): LocalIdentity =
        LocalIdentity(
            id = id,
            nickname = nickname,
            publicSigningKey = publicKey,
            privateSigningKey = privateKey,
            publicHandshakeKey = handshakePublicKey,
            privateHandshakeKey = handshakePrivateKey,
            createdAt = createdAt
        )
}
