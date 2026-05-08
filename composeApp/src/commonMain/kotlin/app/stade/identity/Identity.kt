package app.stade.identity

import kotlinx.serialization.Serializable

@Serializable
data class LocalIdentity(
    val id: String,                       // = stadeId (kanonik kimlik)
    val nickname: String,
    val publicSigningKey: ByteArray,      // Ed25519 (32 B)
    val privateSigningKey: ByteArray,     // Ed25519
    val publicHandshakeKey: ByteArray,    // X25519 (32 B)
    val privateHandshakeKey: ByteArray,   // X25519
    val publicMlKemKey: ByteArray,        // ML-KEM-768 (~1184 B)
    val privateMlKemKey: ByteArray,       // ML-KEM-768 (~2400 B)
    val publicMlDsaKey: ByteArray,        // ML-DSA-65 (~1952 B)
    val privateMlDsaKey: ByteArray,       // ML-DSA-65 (~4032 B)
    val createdAt: Long
) {
    val stadeId: String get() = id
    override fun equals(other: Any?): Boolean = other is LocalIdentity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class RemoteIdentity(
    val id: String,
    val nickname: String,
    val publicSigningKey: ByteArray,
    val publicHandshakeKey: ByteArray,
    val publicMlKemKey: ByteArray,
    val publicMlDsaKey: ByteArray
) {
    val stadeId: String get() = id
    override fun equals(other: Any?): Boolean = other is RemoteIdentity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
