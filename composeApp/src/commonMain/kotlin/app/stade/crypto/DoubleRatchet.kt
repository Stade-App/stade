package app.stade.crypto

class DoubleRatchet(private val crypto: CryptoApi) {

    data class State(
        var rootKey: ByteArray,
        var sendChainKey: ByteArray?,
        var recvChainKey: ByteArray?,
        var dhSendPriv: ByteArray,
        var dhSendPub: ByteArray,
        var dhRecvPub: ByteArray?,
        var sendCounter: Int,
        var recvCounter: Int,
        var previousSendCounter: Int,
        val skipped: MutableMap<SkippedKey, ByteArray>
    )

    data class SkippedKey(val dhPub: List<Byte>, val counter: Int)

    data class Header(
        val dhPub: ByteArray,
        val previousCounter: Int,
        val counter: Int
    ) {
        fun encode(): ByteArray {
            val out = ByteArray(dhPub.size + 8)
            dhPub.copyInto(out)
            writeInt(out, dhPub.size, previousCounter)
            writeInt(out, dhPub.size + 4, counter)
            return out
        }

        companion object {
            fun decode(bytes: ByteArray, dhPubSize: Int = 32): Header {
                val pub = bytes.copyOfRange(0, dhPubSize)
                val prev = readInt(bytes, dhPubSize)
                val ctr = readInt(bytes, dhPubSize + 4)
                return Header(pub, prev, ctr)
            }
        }
    }

    fun initAlice(rootSeed: ByteArray, peerDhPub: ByteArray): State {
        val kp = crypto.generateAgreementKeyPair()
        val shared = crypto.keyAgreement(kp.privateKey, peerDhPub)
        val derived = crypto.hkdf(shared, rootSeed, "stade-dr-init".encodeToByteArray(), 64)
        return State(
            rootKey = derived.copyOfRange(0, 32),
            sendChainKey = derived.copyOfRange(32, 64),
            recvChainKey = null,
            dhSendPriv = kp.privateKey,
            dhSendPub = kp.publicKey,
            dhRecvPub = peerDhPub,
            sendCounter = 0,
            recvCounter = 0,
            previousSendCounter = 0,
            skipped = mutableMapOf()
        )
    }

    fun initBob(rootSeed: ByteArray, ownDh: KeyPair): State {
        return State(
            rootKey = rootSeed.copyOf(),
            sendChainKey = null,
            recvChainKey = null,
            dhSendPriv = ownDh.privateKey,
            dhSendPub = ownDh.publicKey,
            dhRecvPub = null,
            sendCounter = 0,
            recvCounter = 0,
            previousSendCounter = 0,
            skipped = mutableMapOf()
        )
    }

    fun encrypt(state: State, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val chain = state.sendChainKey ?: error("send chain not initialized")
        val (newChain, messageKey) = kdfChain(chain)
        state.sendChainKey = newChain
        val header = Header(state.dhSendPub, state.previousSendCounter, state.sendCounter)
        state.sendCounter += 1
        val nonce = crypto.hkdf(messageKey, ByteArray(0), "stade-dr-nonce".encodeToByteArray(), 12)
        val key = crypto.hkdf(messageKey, ByteArray(0), "stade-dr-key".encodeToByteArray(), 32)
        val headerBytes = header.encode()
        val ad = associatedData + headerBytes
        val ct = crypto.aeadSeal(key, nonce, plaintext, ad)
        val out = ByteArray(4 + headerBytes.size + ct.size)
        writeInt(out, 0, headerBytes.size)
        headerBytes.copyInto(out, 4)
        ct.copyInto(out, 4 + headerBytes.size)
        return out
    }

    fun decrypt(state: State, frame: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray? {
        if (frame.size < 4) return null
        val headerLen = readInt(frame, 0)
        if (headerLen < 32 || frame.size < 4 + headerLen) return null
        val headerBytes = frame.copyOfRange(4, 4 + headerLen)
        val ct = frame.copyOfRange(4 + headerLen, frame.size)
        val header = Header.decode(headerBytes)
        val ad = associatedData + headerBytes
        val skippedKey = SkippedKey(header.dhPub.toList(), header.counter)
        state.skipped.remove(skippedKey)?.let { mk ->
            return openWith(mk, ct, ad)
        }
        if (state.dhRecvPub == null || !header.dhPub.contentEquals(state.dhRecvPub!!)) {
            skipMessageKeys(state, header.previousCounter)
            dhRatchet(state, header.dhPub)
        }
        skipMessageKeys(state, header.counter)
        val chain = state.recvChainKey ?: return null
        val (newChain, messageKey) = kdfChain(chain)
        state.recvChainKey = newChain
        state.recvCounter += 1
        return openWith(messageKey, ct, ad)
    }

    private fun openWith(messageKey: ByteArray, ct: ByteArray, ad: ByteArray): ByteArray? {
        val nonce = crypto.hkdf(messageKey, ByteArray(0), "stade-dr-nonce".encodeToByteArray(), 12)
        val key = crypto.hkdf(messageKey, ByteArray(0), "stade-dr-key".encodeToByteArray(), 32)
        return crypto.aeadOpen(key, nonce, ct, ad)
    }

    private fun skipMessageKeys(state: State, until: Int) {
        val chain = state.recvChainKey ?: return
        val recvPub = state.dhRecvPub ?: return
        var localChain = chain
        var counter = state.recvCounter
        val limit = until.coerceAtMost(counter + 64)
        while (counter < limit) {
            val (next, mk) = kdfChain(localChain)
            state.skipped[SkippedKey(recvPub.toList(), counter)] = mk
            localChain = next
            counter += 1
        }
        state.recvChainKey = localChain
        state.recvCounter = counter
    }

    private fun dhRatchet(state: State, peerDhPub: ByteArray) {
        state.previousSendCounter = state.sendCounter
        state.sendCounter = 0
        state.recvCounter = 0
        state.dhRecvPub = peerDhPub
        val sharedRecv = crypto.keyAgreement(state.dhSendPriv, peerDhPub)
        val derivedRecv = crypto.hkdf(sharedRecv, state.rootKey, "stade-dr-step".encodeToByteArray(), 64)
        state.rootKey = derivedRecv.copyOfRange(0, 32)
        state.recvChainKey = derivedRecv.copyOfRange(32, 64)
        val newKp = crypto.generateAgreementKeyPair()
        state.dhSendPriv = newKp.privateKey
        state.dhSendPub = newKp.publicKey
        val sharedSend = crypto.keyAgreement(state.dhSendPriv, peerDhPub)
        val derivedSend = crypto.hkdf(sharedSend, state.rootKey, "stade-dr-step".encodeToByteArray(), 64)
        state.rootKey = derivedSend.copyOfRange(0, 32)
        state.sendChainKey = derivedSend.copyOfRange(32, 64)
    }

    private fun kdfChain(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val out = crypto.hkdf(chainKey, ByteArray(0), "stade-dr-chain".encodeToByteArray(), 64)
        return out.copyOfRange(0, 32) to out.copyOfRange(32, 64)
    }

    companion object {
        private fun writeInt(out: ByteArray, offset: Int, value: Int) {
            out[offset] = ((value ushr 24) and 0xff).toByte()
            out[offset + 1] = ((value ushr 16) and 0xff).toByte()
            out[offset + 2] = ((value ushr 8) and 0xff).toByte()
            out[offset + 3] = (value and 0xff).toByte()
        }

        private fun readInt(src: ByteArray, offset: Int): Int =
            ((src[offset].toInt() and 0xff) shl 24) or
                ((src[offset + 1].toInt() and 0xff) shl 16) or
                ((src[offset + 2].toInt() and 0xff) shl 8) or
                (src[offset + 3].toInt() and 0xff)
    }
}
