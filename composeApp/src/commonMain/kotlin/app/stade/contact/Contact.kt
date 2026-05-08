package app.stade.contact

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: String,                            // = peer Stade ID
    val ownerId: String,
    val nickname: String,
    val publicSigningKey: ByteArray,           // Ed25519
    val publicHandshakeKey: ByteArray,         // X25519
    val publicMlKemKey: ByteArray,             // ML-KEM-768
    val publicMlDsaKey: ByteArray,             // ML-DSA-65
    val rootKey: ByteArray,
    val ratchetState: ByteArray?,
    val isAlice: Boolean,
    val verified: Boolean,
    val lastSeen: Long,
    val createdAt: Long,
    val addresses: List<String> = emptyList()
) {
    val stadeId: String get() = id
    override fun equals(other: Any?): Boolean = other is Contact && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

enum class HandshakeState { CREATED, INVITE_SENT, INVITE_RECEIVED, AGREED, FAILED }

@Serializable
data class PendingContact(
    val id: String,
    val ownerId: String,
    val alias: String,
    val inviteLink: String,
    val state: HandshakeState,
    val createdAt: Long
)
