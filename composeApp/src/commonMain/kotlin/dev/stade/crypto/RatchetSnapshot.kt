package dev.stade.crypto

import kotlinx.serialization.Serializable

@Serializable
data class RatchetSnapshot(
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
)

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
