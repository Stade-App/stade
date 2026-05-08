package app.stade.sync

import kotlinx.serialization.Serializable

enum class RecordType(val code: Byte) {
    HELLO(1),
    AUTH(2),
    MESSAGE(3),
    ACK(4),
    PING(5),
    BYE(6),

    /**
     * Yeni kişi tanışmasında karşı tarafın ML-KEM-768 public key'ine encapsulate
     * edilmiş ciphertext'i taşır. Tek seferlik; root key türetiminde kullanılır.
     */
    KEM_OFFER(7);

    companion object {
        fun fromCode(c: Byte): RecordType? = entries.firstOrNull { it.code == c }
    }
}

/**
 * v2 hello: hibrit kimlik, transcript commitment ile downgrade attack koruması.
 *
 * `transcriptCommitment = Blake2b("stade-tc-v2" || proto(BE int32) ||
 *                                 Ed25519pub || X25519pub || MLKEMpub || MLDSApub)`
 */
@Serializable
data class HelloPayload(
    val protocolVersion: Int,
    val stadeId: String,
    val nickname: String,
    val signingPublicKey: ByteArray,      // Ed25519
    val handshakePublicKey: ByteArray,    // X25519
    val mlkemPublicKey: ByteArray,        // ML-KEM-768
    val mldsaPublicKey: ByteArray,        // ML-DSA-65
    val nonce: ByteArray,                 // 32 B challenge
    val transcriptCommitment: ByteArray,  // 32 B
    val addresses: List<String> = emptyList()
)

/**
 * v2 auth: hibrit imza. İki imza da
 *   `"stade-auth-v2" || peerNonce || ownTC || peerTC`
 * üzerine üretilmek zorunda; ikisinin de geçmesi gerekir.
 */
@Serializable
data class AuthPayload(
    val stadeId: String,
    val edSignature: ByteArray,
    val mldsaSignature: ByteArray
)

@Serializable
data class KemOfferPayload(
    val ciphertext: ByteArray
)

@Serializable
data class MessagePayload(
    val messageId: String,
    val timestamp: Long,
    val ratchetFrame: ByteArray
)

@Serializable
data class AckPayload(val messageId: String)

@Serializable
data class SyncRecord(
    val type: RecordType,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is SyncRecord && other.type == type && other.payload.contentEquals(payload)
    override fun hashCode(): Int = type.hashCode() * 31 + payload.contentHashCode()
}

object FrameCodec {
    private const val MAX_LEN = 4 * 1024 * 1024

    fun encode(record: SyncRecord): ByteArray {
        val len = record.payload.size
        require(len in 0..MAX_LEN) { "frame too large" }
        val out = ByteArray(5 + len)
        out[0] = record.type.code
        out[1] = ((len ushr 24) and 0xff).toByte()
        out[2] = ((len ushr 16) and 0xff).toByte()
        out[3] = ((len ushr 8) and 0xff).toByte()
        out[4] = (len and 0xff).toByte()
        record.payload.copyInto(out, 5)
        return out
    }

    fun decode(frame: ByteArray): SyncRecord? {
        if (frame.size < 5) return null
        val type = RecordType.fromCode(frame[0]) ?: return null
        val len = ((frame[1].toInt() and 0xff) shl 24) or
            ((frame[2].toInt() and 0xff) shl 16) or
            ((frame[3].toInt() and 0xff) shl 8) or
            (frame[4].toInt() and 0xff)
        if (len !in 0..MAX_LEN) return null
        if (frame.size < 5 + len) return null
        return SyncRecord(type, frame.copyOfRange(5, 5 + len))
    }
}
