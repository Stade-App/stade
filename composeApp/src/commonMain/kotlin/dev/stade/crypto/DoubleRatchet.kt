package dev.stade.crypto

class DoubleRatchet(
    private val crypto: CryptoApi,
    private val pq: PqCrypto
) {

    data class State(
        var rootKey: ByteArray,
        var sendChainKey: ByteArray?,
        var recvChainKey: ByteArray?,
        var dhSendPriv: ByteArray,
        var dhSendPub: ByteArray,
        var dhRecvPub: ByteArray?,
        var mlkemSendPriv: ByteArray,
        var mlkemSendPub: ByteArray,
        var mlkemRecvPub: ByteArray?,
        var pendingKemCiphertext: ByteArray?,
        var sendCounter: Int,
        var recvCounter: Int,
        var previousSendCounter: Int,
        val skipped: MutableMap<SkippedKey, ByteArray>
    )

    data class SkippedKey(val dhPub: List<Byte>, val counter: Int)

    data class Header(
        val dhPub: ByteArray,
        val previousCounter: Int,
        val counter: Int,
        val mlkemPub: ByteArray? = null,
        val mlkemCt: ByteArray? = null
    ) {
        fun encode(): ByteArray {
            val flags = (if (mlkemPub != null) 1 else 0) or (if (mlkemCt != null) 2 else 0)
            val baseSize = 2 + 1 + 4 + 4 + dhPub.size
            val total = baseSize + (mlkemPub?.size ?: 0) + (mlkemCt?.size ?: 0)
            val out = ByteArray(total)
            out[0] = 0x00; out[1] = 0x02
            out[2] = (flags and 0xff).toByte()
            writeInt(out, 3, previousCounter)
            writeInt(out, 7, counter)
            dhPub.copyInto(out, 11)
            var off = 11 + dhPub.size
            mlkemPub?.let { it.copyInto(out, off); off += it.size }
            mlkemCt?.let { it.copyInto(out, off); off += it.size }
            return out
        }

        companion object {
            fun decode(bytes: ByteArray, dhPubSize: Int = 32): Header? {
                if (bytes.size < 2 + 1 + 4 + 4 + dhPubSize) return null
                if (bytes[0] != 0x00.toByte() || bytes[1] != 0x02.toByte()) return null
                val flags = bytes[2].toInt() and 0xff
                val prev = readInt(bytes, 3)
                val ctr = readInt(bytes, 7)
                val pub = bytes.copyOfRange(11, 11 + dhPubSize)
                var off = 11 + dhPubSize
                val kemPub = if ((flags and 1) != 0) {
                    if (off + 1184 > bytes.size) return null
                    bytes.copyOfRange(off, off + 1184).also { off += 1184 }
                } else null
                val kemCt = if ((flags and 2) != 0) {
                    if (off + 1088 > bytes.size) return null
                    bytes.copyOfRange(off, off + 1088).also { off += 1088 }
                } else null
                return Header(pub, prev, ctr, kemPub, kemCt)
            }
        }
    }

    fun initSymmetric(
        rootSeed: ByteArray,
        ownDh: KeyPair,
        peerDhPub: ByteArray,
        isAlice: Boolean
    ): State {
        val a2b = crypto.hkdf(rootSeed, ByteArray(0), "stade-dr-a2b".encodeToByteArray(), 32)
        val b2a = crypto.hkdf(rootSeed, ByteArray(0), "stade-dr-b2a".encodeToByteArray(), 32)
        return State(
            rootKey = rootSeed.copyOf(),
            sendChainKey = if (isAlice) a2b else b2a,
            recvChainKey = if (isAlice) b2a else a2b,
            dhSendPriv = ownDh.privateKey,
            dhSendPub = ownDh.publicKey,
            dhRecvPub = peerDhPub,
            mlkemSendPriv = ByteArray(0),
            mlkemSendPub = ByteArray(0),
            mlkemRecvPub = null,
            pendingKemCiphertext = null,
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
        val headerKemPub = state.mlkemSendPub.takeIf { it.isNotEmpty() }
        val headerKemCt = state.pendingKemCiphertext
        val header = Header(
            dhPub = state.dhSendPub,
            previousCounter = state.previousSendCounter,
            counter = state.sendCounter,
            mlkemPub = headerKemPub,
            mlkemCt = headerKemCt
        )
        state.pendingKemCiphertext = null
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
        if (headerLen < 11 + 32 || frame.size < 4 + headerLen) return null
        val headerBytes = frame.copyOfRange(4, 4 + headerLen)
        val ct = frame.copyOfRange(4 + headerLen, frame.size)
        val header = Header.decode(headerBytes) ?: return null
        val ad = associatedData + headerBytes

        val skippedKey = SkippedKey(header.dhPub.toList(), header.counter)
        state.skipped.remove(skippedKey)?.let { mk ->
            val out = openWith(mk, ct, ad)
            if (out == null) state.skipped[skippedKey] = mk
            return out
        }

        if (header.dhPub.contentEquals(state.dhSendPub)) return null
        if (state.dhRecvPub != null && header.dhPub.contentEquals(state.dhRecvPub!!) &&
            header.counter < state.recvCounter
        ) return null

        val snapshot = snapshotState(state)

        val peerSentKemCtForUs = header.mlkemCt
        val incomingKemSs: ByteArray? = if (peerSentKemCtForUs != null && state.mlkemSendPriv.isNotEmpty()) {
            runCatching { pq.mlkemDecapsulate(state.mlkemSendPriv, peerSentKemCtForUs) }.getOrNull()
        } else null

        if (state.dhRecvPub == null || !header.dhPub.contentEquals(state.dhRecvPub!!)) {
            skipMessageKeys(state, header.previousCounter)
            dhRatchet(state, header, incomingKemSs)
        } else if (incomingKemSs != null) {
            mixKemSs(state, incomingKemSs)
        }

        val peerKemPub = header.mlkemPub
        if (peerKemPub != null && (state.mlkemRecvPub == null || !peerKemPub.contentEquals(state.mlkemRecvPub!!))) {
            state.mlkemRecvPub = peerKemPub
            val enc = runCatching { pq.mlkemEncapsulate(peerKemPub) }.getOrNull()
            if (enc != null) {
                state.pendingKemCiphertext = enc.ciphertext
                mixKemSs(state, enc.sharedSecret)
            }
        }

        skipMessageKeys(state, header.counter)
        val chain = state.recvChainKey ?: run { restoreState(state, snapshot); return null }
        val (newChain, messageKey) = kdfChain(chain)
        state.recvChainKey = newChain
        state.recvCounter += 1
        val plain = openWith(messageKey, ct, ad)
        if (plain == null) {
            restoreState(state, snapshot)
            return null
        }
        return plain
    }

    private fun snapshotState(s: State): State = State(
        rootKey = s.rootKey.copyOf(),
        sendChainKey = s.sendChainKey?.copyOf(),
        recvChainKey = s.recvChainKey?.copyOf(),
        dhSendPriv = s.dhSendPriv.copyOf(),
        dhSendPub = s.dhSendPub.copyOf(),
        dhRecvPub = s.dhRecvPub?.copyOf(),
        mlkemSendPriv = s.mlkemSendPriv.copyOf(),
        mlkemSendPub = s.mlkemSendPub.copyOf(),
        mlkemRecvPub = s.mlkemRecvPub?.copyOf(),
        pendingKemCiphertext = s.pendingKemCiphertext?.copyOf(),
        sendCounter = s.sendCounter,
        recvCounter = s.recvCounter,
        previousSendCounter = s.previousSendCounter,
        skipped = LinkedHashMap(s.skipped)
    )

    private fun restoreState(s: State, snap: State) {
        s.rootKey = snap.rootKey
        s.sendChainKey = snap.sendChainKey
        s.recvChainKey = snap.recvChainKey
        s.dhSendPriv = snap.dhSendPriv
        s.dhSendPub = snap.dhSendPub
        s.dhRecvPub = snap.dhRecvPub
        s.mlkemSendPriv = snap.mlkemSendPriv
        s.mlkemSendPub = snap.mlkemSendPub
        s.mlkemRecvPub = snap.mlkemRecvPub
        s.pendingKemCiphertext = snap.pendingKemCiphertext
        s.sendCounter = snap.sendCounter
        s.recvCounter = snap.recvCounter
        s.previousSendCounter = snap.previousSendCounter
        s.skipped.clear()
        s.skipped.putAll(snap.skipped)
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
        val limit = until.coerceAtMost(counter + 128)
        while (counter < limit) {
            val (next, mk) = kdfChain(localChain)
            state.skipped[SkippedKey(recvPub.toList(), counter)] = mk
            localChain = next
            counter += 1
        }
        state.recvChainKey = localChain
        state.recvCounter = counter
    }

    private fun dhRatchet(state: State, header: Header, incomingKemSs: ByteArray?) {
        state.previousSendCounter = state.sendCounter
        state.sendCounter = 0
        state.recvCounter = 0
        state.dhRecvPub = header.dhPub

        val sharedRecv = crypto.keyAgreement(state.dhSendPriv, header.dhPub)
        val recvSecret = sharedRecv + (incomingKemSs ?: ByteArray(0))
        val derivedRecv = crypto.hkdf(recvSecret, state.rootKey, "stade-pqdr-step-v2".encodeToByteArray(), 64)
        state.rootKey = derivedRecv.copyOfRange(0, 32)
        state.recvChainKey = derivedRecv.copyOfRange(32, 64)

        val newDh = crypto.generateAgreementKeyPair()
        state.dhSendPriv = newDh.privateKey
        state.dhSendPub = newDh.publicKey

        val newKem = pq.generateMlKemKeyPair()
        state.mlkemSendPriv = newKem.privateKey
        state.mlkemSendPub = newKem.publicKey

        val sharedSend = crypto.keyAgreement(state.dhSendPriv, header.dhPub)
        val derivedSend = crypto.hkdf(sharedSend, state.rootKey, "stade-pqdr-step-v2".encodeToByteArray(), 64)
        state.rootKey = derivedSend.copyOfRange(0, 32)
        state.sendChainKey = derivedSend.copyOfRange(32, 64)
    }

    private fun mixKemSs(state: State, kemSs: ByteArray) {
        val derived = crypto.hkdf(kemSs, state.rootKey, "stade-pqdr-mix-v2".encodeToByteArray(), 32)
        state.rootKey = derived
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
