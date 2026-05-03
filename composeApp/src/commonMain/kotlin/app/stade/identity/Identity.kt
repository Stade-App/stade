package app.stade.identity

import kotlinx.serialization.Serializable

@Serializable
data class LocalIdentity(
    val id: String,
    val nickname: String,
    val publicSigningKey: ByteArray,
    val privateSigningKey: ByteArray,
    val publicHandshakeKey: ByteArray,
    val privateHandshakeKey: ByteArray,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean = other is LocalIdentity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class RemoteIdentity(
    val id: String,
    val nickname: String,
    val publicSigningKey: ByteArray,
    val publicHandshakeKey: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is RemoteIdentity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
