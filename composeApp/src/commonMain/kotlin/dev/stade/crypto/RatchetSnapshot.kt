package dev.stade.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// This snapshot (including the ML-KEM keys and the skipped-message-key map, now sized up to 8000
// entries) is JSON-encoded and written to the local DB on every single seal()/open() call, i.e.
// every message sent or received. Without this, kotlinx.serialization's default ByteArray-as-
// JSON-array-of-numbers encoding makes that ~3.6x bigger than it needs to be, which is pure
// per-message CPU/GC/disk-write overhead that isn't sent over the wire at all - purely local.
@OptIn(ExperimentalEncodingApi::class)
private object RatchetByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RatchetByteArrayAsBase64", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.Default.encode(value))
    override fun deserialize(decoder: Decoder): ByteArray = Base64.Default.decode(decoder.decodeString())
}

@Serializable
data class RatchetSnapshot(
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val rootKey: ByteArray,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val sendChainKey: ByteArray?,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val recvChainKey: ByteArray?,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val dhSendPriv: ByteArray,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val dhSendPub: ByteArray,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val dhRecvPub: ByteArray?,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val mlkemSendPriv: ByteArray = ByteArray(0),
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val mlkemSendPub: ByteArray = ByteArray(0),
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val mlkemRecvPub: ByteArray? = null,
    @Serializable(with = RatchetByteArrayAsBase64Serializer::class)
    val pendingKemCiphertext: ByteArray? = null,
    val sendCounter: Int,
    val recvCounter: Int,
    val previousSendCounter: Int,
    val skipped: Map<String, @Serializable(with = RatchetByteArrayAsBase64Serializer::class) ByteArray>
)

// Matches RatchetSnapshot's pre-Base64 shape (plain ByteArray fields, default JSON-array-of-numbers
// encoding) so state persisted by older builds can still be read once and transparently migrated -
// the very next persist() call rewrites it in the compact format.
@Serializable
data class LegacyRatchetSnapshot(
    val rootKey: ByteArray,
    val sendChainKey: ByteArray?,
    val recvChainKey: ByteArray?,
    val dhSendPriv: ByteArray,
    val dhSendPub: ByteArray,
    val dhRecvPub: ByteArray?,
    val mlkemSendPriv: ByteArray = ByteArray(0),
    val mlkemSendPub: ByteArray = ByteArray(0),
    val mlkemRecvPub: ByteArray? = null,
    val pendingKemCiphertext: ByteArray? = null,
    val sendCounter: Int,
    val recvCounter: Int,
    val previousSendCounter: Int,
    val skipped: Map<String, ByteArray>
) {
    fun toCurrent(): RatchetSnapshot = RatchetSnapshot(
        rootKey, sendChainKey, recvChainKey, dhSendPriv, dhSendPub, dhRecvPub,
        mlkemSendPriv, mlkemSendPub, mlkemRecvPub, pendingKemCiphertext,
        sendCounter, recvCounter, previousSendCounter, skipped
    )
}

object RatchetSerializer {
    fun toSnapshot(state: DoubleRatchet.State): RatchetSnapshot = RatchetSnapshot(
        rootKey = state.rootKey,
        sendChainKey = state.sendChainKey,
        recvChainKey = state.recvChainKey,
        dhSendPriv = state.dhSendPriv,
        dhSendPub = state.dhSendPub,
        dhRecvPub = state.dhRecvPub,
        mlkemSendPriv = state.mlkemSendPriv,
        mlkemSendPub = state.mlkemSendPub,
        mlkemRecvPub = state.mlkemRecvPub,
        pendingKemCiphertext = state.pendingKemCiphertext,
        sendCounter = state.sendCounter,
        recvCounter = state.recvCounter,
        previousSendCounter = state.previousSendCounter,
        skipped = state.skipped.entries.associate {
            (Encoding.toHex(it.key.dhPub.toByteArray()) + ":" + it.key.counter) to it.value
        }
    )

    fun fromSnapshot(snap: RatchetSnapshot): DoubleRatchet.State {
        val mapped = snap.skipped.entries.associate { (k, v) ->
            val parts = k.split(":")
            DoubleRatchet.SkippedKey(Encoding.fromHex(parts[0]).toList(), parts[1].toInt()) to v
        }.toMutableMap()
        return DoubleRatchet.State(
            rootKey = snap.rootKey,
            sendChainKey = snap.sendChainKey,
            recvChainKey = snap.recvChainKey,
            dhSendPriv = snap.dhSendPriv,
            dhSendPub = snap.dhSendPub,
            dhRecvPub = snap.dhRecvPub,
            mlkemSendPriv = snap.mlkemSendPriv,
            mlkemSendPub = snap.mlkemSendPub,
            mlkemRecvPub = snap.mlkemRecvPub,
            pendingKemCiphertext = snap.pendingKemCiphertext,
            sendCounter = snap.sendCounter,
            recvCounter = snap.recvCounter,
            previousSendCounter = snap.previousSendCounter,
            skipped = mapped
        )
    }
}
