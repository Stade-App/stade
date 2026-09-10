package dev.stade.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val crypto: CryptoApi = platformCrypto()
private val pq: PqCrypto = platformPq()

private class Session(
    val ratchet: DoubleRatchet,
    val a: DoubleRatchet.State,
    val b: DoubleRatchet.State,
    val aStatic: KeyPair,
    val bStatic: KeyPair
)

private fun newSession(): Session {
    val ratchet = DoubleRatchet(crypto, pq)
    val aStatic = crypto.generateAgreementKeyPair()
    val bStatic = crypto.generateAgreementKeyPair()
    val root = crypto.randomBytes(32)
    val aKem = pq.generateMlKemKeyPair()
    val bKem = pq.generateMlKemKeyPair()
    val a = ratchet.initSymmetric(root, aStatic, bStatic.publicKey, true, aKem, bKem.publicKey)
    val b = ratchet.initSymmetric(root, bStatic, aStatic.publicKey, false, bKem, aKem.publicKey)
    return Session(ratchet, a, b, aStatic, bStatic)
}

private fun headerOf(frame: ByteArray): DoubleRatchet.Header {
    val len = ((frame[0].toInt() and 0xff) shl 24) or
        ((frame[1].toInt() and 0xff) shl 16) or
        ((frame[2].toInt() and 0xff) shl 8) or
        (frame[3].toInt() and 0xff)
    return DoubleRatchet.Header.decode(frame.copyOfRange(4, 4 + len))!!
}

private fun reframe(header: DoubleRatchet.Header, original: ByteArray): ByteArray {
    val oldLen = ((original[0].toInt() and 0xff) shl 24) or
        ((original[1].toInt() and 0xff) shl 16) or
        ((original[2].toInt() and 0xff) shl 8) or
        (original[3].toInt() and 0xff)
    val body = original.copyOfRange(4 + oldLen, original.size)
    val encoded = header.encode()
    val out = ByteArray(4 + encoded.size + body.size)
    out[0] = ((encoded.size ushr 24) and 0xff).toByte()
    out[1] = ((encoded.size ushr 16) and 0xff).toByte()
    out[2] = ((encoded.size ushr 8) and 0xff).toByte()
    out[3] = (encoded.size and 0xff).toByte()
    encoded.copyInto(out, 4)
    body.copyInto(out, 4 + encoded.size)
    return out
}

class DoubleRatchetTest {

    @Test
    fun dhPublicKeyRotatesAsTheConversationProceeds() {
        val s = newSession()
        val seen = mutableSetOf<List<Byte>>()

        repeat(6) { turn ->
            val fromA = s.ratchet.encrypt(s.a, "a$turn".encodeToByteArray())
            seen += headerOf(fromA).dhPub.toList()
            assertNotNull(s.ratchet.decrypt(s.b, fromA), "B failed to decrypt turn $turn")

            val fromB = s.ratchet.encrypt(s.b, "b$turn".encodeToByteArray())
            seen += headerOf(fromB).dhPub.toList()
            assertNotNull(s.ratchet.decrypt(s.a, fromB), "A failed to decrypt turn $turn")
        }

        assertTrue(
            seen.size >= 10,
            "expected a fresh DH key per ratchet step, saw only ${seen.size} distinct keys"
        )
    }

    @Test
    fun neitherPartyKeepsUsingItsStaticHandshakeKey() {
        val s = newSession()
        s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "hello".encodeToByteArray()))
        s.ratchet.decrypt(s.a, s.ratchet.encrypt(s.b, "hi".encodeToByteArray()))

        assertFalse(
            s.a.dhSendPub.contentEquals(s.aStatic.publicKey),
            "A is still ratcheting on its long-lived handshake key"
        )
        assertFalse(
            s.b.dhSendPub.contentEquals(s.bStatic.publicKey),
            "B is still ratcheting on its long-lived handshake key"
        )
    }

    @Test
    fun rootKeyAdvancesSoAChainKeyLeakDoesNotPersist() {
        val s = newSession()
        val roots = mutableSetOf<List<Byte>>()
        roots += s.a.rootKey.toList()

        repeat(5) { turn ->
            s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "a$turn".encodeToByteArray()))
            s.ratchet.decrypt(s.a, s.ratchet.encrypt(s.b, "b$turn".encodeToByteArray()))
            roots += s.a.rootKey.toList()
        }

        assertTrue(roots.size >= 6, "root key stopped advancing: only ${roots.size} distinct values")
    }

    @Test
    fun sendAndReceiveChainsAgreeAfterARatchetStep() {
        val s = newSession()
        val fromA = s.ratchet.encrypt(s.a, "first".encodeToByteArray())
        assertNotNull(s.ratchet.decrypt(s.b, fromA))
        assertContentEquals(
            s.a.sendChainKey,
            s.b.recvChainKey,
            "A's send chain and B's receive chain diverged after the first ratchet"
        )
    }

    @Test
    fun chainsStayInSyncAcrossManyTurns() {
        val s = newSession()
        repeat(25) { turn ->
            val fromA = s.ratchet.encrypt(s.a, "ping$turn".encodeToByteArray())
            assertContentEquals(
                "ping$turn".encodeToByteArray(),
                s.ratchet.decrypt(s.b, fromA),
                "B lost sync at turn $turn"
            )
            val fromB = s.ratchet.encrypt(s.b, "pong$turn".encodeToByteArray())
            assertContentEquals(
                "pong$turn".encodeToByteArray(),
                s.ratchet.decrypt(s.a, fromB),
                "A lost sync at turn $turn"
            )
        }
    }

    @Test
    fun bobMaySendBeforeAliceEverDoes() {
        val s = newSession()
        repeat(3) { turn ->
            val fromB = s.ratchet.encrypt(s.b, "b$turn".encodeToByteArray())
            assertContentEquals(
                "b$turn".encodeToByteArray(),
                s.ratchet.decrypt(s.a, fromB),
                "A could not decrypt B's opening message $turn"
            )
        }
        val fromA = s.ratchet.encrypt(s.a, "reply".encodeToByteArray())
        assertContentEquals("reply".encodeToByteArray(), s.ratchet.decrypt(s.b, fromA))
    }

    @Test
    fun burstsInOneDirectionStayInSync() {
        val s = newSession()
        repeat(4) { round ->
            val frames = (0 until 10).map { s.ratchet.encrypt(s.a, "r$round-$it".encodeToByteArray()) }
            frames.forEachIndexed { i, f ->
                assertContentEquals("r$round-$i".encodeToByteArray(), s.ratchet.decrypt(s.b, f))
            }
            val back = s.ratchet.encrypt(s.b, "ack$round".encodeToByteArray())
            assertContentEquals("ack$round".encodeToByteArray(), s.ratchet.decrypt(s.a, back))
        }
    }

    @Test
    fun outOfOrderDeliveryStillDecrypts() {
        val s = newSession()
        s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "warmup".encodeToByteArray()))
        s.ratchet.decrypt(s.a, s.ratchet.encrypt(s.b, "warmup".encodeToByteArray()))

        val frames = (0 until 5).map { s.ratchet.encrypt(s.a, "msg$it".encodeToByteArray()) }
        for (i in frames.indices.reversed()) {
            assertContentEquals(
                "msg$i".encodeToByteArray(),
                s.ratchet.decrypt(s.b, frames[i]),
                "out-of-order message $i failed"
            )
        }
    }

    @Test
    fun messagesDelayedAcrossARatchetStepStillDecrypt() {
        val s = newSession()
        s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "warmup".encodeToByteArray()))

        val stale = s.ratchet.encrypt(s.a, "stale".encodeToByteArray())
        s.ratchet.decrypt(s.a, s.ratchet.encrypt(s.b, "turn".encodeToByteArray()))
        val fresh = s.ratchet.encrypt(s.a, "fresh".encodeToByteArray())

        assertContentEquals("fresh".encodeToByteArray(), s.ratchet.decrypt(s.b, fresh))
        assertContentEquals(
            "stale".encodeToByteArray(),
            s.ratchet.decrypt(s.b, stale),
            "a message sent before a ratchet step was lost once the step happened"
        )
    }

    @Test
    fun everyRatchetStepCarriesMlKemMaterial() {
        val s = newSession()
        var steps = 0

        repeat(5) { turn ->
            val fromA = s.ratchet.encrypt(s.a, "a$turn".encodeToByteArray())
            headerOf(fromA).let {
                assertNotNull(it.mlkemPub, "turn $turn from A advertised no ML-KEM key")
                assertNotNull(it.mlkemCt, "turn $turn from A carried no ML-KEM ciphertext")
                steps++
            }
            assertNotNull(s.ratchet.decrypt(s.b, fromA))

            val fromB = s.ratchet.encrypt(s.b, "b$turn".encodeToByteArray())
            headerOf(fromB).let {
                assertNotNull(it.mlkemPub, "turn $turn from B advertised no ML-KEM key")
                assertNotNull(it.mlkemCt, "turn $turn from B carried no ML-KEM ciphertext")
                steps++
            }
            assertNotNull(s.ratchet.decrypt(s.a, fromB))
        }

        assertEquals(10, steps)
    }

    @Test
    fun mlKemKeysAreEphemeralPerStep() {
        val s = newSession()
        val seen = mutableSetOf<List<Byte>>()
        repeat(5) { turn ->
            val fromA = s.ratchet.encrypt(s.a, "a$turn".encodeToByteArray())
            seen += headerOf(fromA).mlkemPub!!.toList()
            s.ratchet.decrypt(s.b, fromA)
            val fromB = s.ratchet.encrypt(s.b, "b$turn".encodeToByteArray())
            seen += headerOf(fromB).mlkemPub!!.toList()
            s.ratchet.decrypt(s.a, fromB)
        }
        assertTrue(seen.size >= 10, "ML-KEM keys were reused across steps: ${seen.size} distinct")
    }

    @Test
    fun aCorruptedKemCiphertextFailsClosedInsteadOfDesyncing() {
        val s = newSession()
        s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "warmup".encodeToByteArray()))
        s.ratchet.decrypt(s.a, s.ratchet.encrypt(s.b, "warmup".encodeToByteArray()))

        val frame = s.ratchet.encrypt(s.a, "hybrid".encodeToByteArray())
        val h = headerOf(frame)
        val brokenCt = h.mlkemCt!!.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val hostile = reframe(
            DoubleRatchet.Header(h.dhPub, h.previousCounter, h.counter, h.mlkemPub, brokenCt),
            frame
        )
        assertNull(s.ratchet.decrypt(s.b, hostile), "a corrupted KEM ciphertext must be rejected")

        assertContentEquals(
            "hybrid".encodeToByteArray(),
            s.ratchet.decrypt(s.b, frame),
            "the session must survive a rejected frame"
        )
        val reply = s.ratchet.encrypt(s.b, "ok".encodeToByteArray())
        assertContentEquals("ok".encodeToByteArray(), s.ratchet.decrypt(s.a, reply))
    }

    @Test
    fun aSessionThatNeverRatchetedIsDetectedAndRepaired() {
        val ratchet = DoubleRatchet(crypto, pq)
        val aStatic = crypto.generateAgreementKeyPair()
        val bStatic = crypto.generateAgreementKeyPair()
        val root = crypto.randomBytes(32)

        val aKem = pq.generateMlKemKeyPair()
        val bKem = pq.generateMlKemKeyPair()
        val legacyAlice =
            ratchet.initSymmetric(root, aStatic, bStatic.publicKey, false, aKem, bKem.publicKey)
        val bob = ratchet.initSymmetric(root, bStatic, aStatic.publicKey, false, bKem, aKem.publicKey)

        assertTrue(
            ratchet.hasNeverRatcheted(legacyAlice, aStatic.publicKey),
            "a pre-fix session should be recognised as never having ratcheted"
        )

        ratchet.startSendRatchet(legacyAlice)
        assertFalse(ratchet.hasNeverRatcheted(legacyAlice, aStatic.publicKey))

        val frame = ratchet.encrypt(legacyAlice, "after migration".encodeToByteArray())
        assertContentEquals(
            "after migration".encodeToByteArray(),
            ratchet.decrypt(bob, frame),
            "a migrated session must still be readable by the peer"
        )
        val back = ratchet.encrypt(bob, "ack".encodeToByteArray())
        assertContentEquals("ack".encodeToByteArray(), ratchet.decrypt(legacyAlice, back))
    }

    @Test
    fun replayOfAnAlreadyOpenedFrameIsRejected() {
        val s = newSession()
        val frame = s.ratchet.encrypt(s.a, "once".encodeToByteArray())
        assertNotNull(s.ratchet.decrypt(s.b, frame))
        assertNull(s.ratchet.decrypt(s.b, frame), "replayed frame was accepted a second time")
    }

    @Test
    fun tamperedHeaderIsRejected() {
        val s = newSession()
        val frame = s.ratchet.encrypt(s.a, "authentic".encodeToByteArray())
        frame[8] = (frame[8] + 1).toByte()
        assertNull(s.ratchet.decrypt(s.b, frame), "tampered header was accepted")
    }

    @Test
    fun tamperedCiphertextIsRejectedAndLeavesStateUsable() {
        val s = newSession()
        val good = s.ratchet.encrypt(s.a, "one".encodeToByteArray())
        val bad = s.ratchet.encrypt(s.a, "two".encodeToByteArray())
        bad[bad.size - 1] = (bad[bad.size - 1] + 1).toByte()

        assertNotNull(s.ratchet.decrypt(s.b, good))
        assertNull(s.ratchet.decrypt(s.b, bad))

        val next = s.ratchet.encrypt(s.a, "three".encodeToByteArray())
        assertContentEquals(
            "three".encodeToByteArray(),
            s.ratchet.decrypt(s.b, next),
            "a rejected frame must not poison the session"
        )
    }

    @Test
    fun skippedKeyStorageStaysBounded() {
        val s = newSession()
        s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "warmup".encodeToByteArray()))

        repeat(400) { s.ratchet.encrypt(s.a, "burn".encodeToByteArray()) }
        val far = s.ratchet.encrypt(s.a, "far".encodeToByteArray())
        assertNotNull(s.ratchet.decrypt(s.b, far), "a large but legitimate gap should still decrypt")

        assertTrue(
            s.b.skipped.size <= 2000,
            "skipped-key map grew to ${s.b.skipped.size}, above the retention cap"
        )
    }

    @Test
    fun anAbsurdCounterCannotExhaustMemory() {
        val s = newSession()
        s.ratchet.decrypt(s.b, s.ratchet.encrypt(s.a, "warmup".encodeToByteArray()))

        val frame = s.ratchet.encrypt(s.a, "spike".encodeToByteArray())
        val h = headerOf(frame)
        val hostile = reframe(
            DoubleRatchet.Header(h.dhPub, Int.MAX_VALUE - 1, Int.MAX_VALUE, h.mlkemPub, h.mlkemCt),
            frame
        )
        s.ratchet.decrypt(s.b, hostile)

        assertTrue(
            s.b.skipped.size <= 2000,
            "a hostile counter grew the skipped map to ${s.b.skipped.size}"
        )
    }

    @Test
    fun hybridHeaderOverheadStaysWithinExpectedBounds() {
        val s = newSession()
        val frame = s.ratchet.encrypt(s.a, "hi".encodeToByteArray())
        val header = headerOf(frame)
        assertNotNull(header.mlkemPub)
        assertNotNull(header.mlkemCt)
        assertTrue(
            frame.size in 2200..2500,
            "per-message hybrid overhead moved to ${frame.size} bytes; if this is intentional, " +
                "update the bound, but the cost is paid on every message"
        )
    }

    @Test
    fun senderCannotDecryptItsOwnFrame() {
        val s = newSession()
        val frame = s.ratchet.encrypt(s.a, "mine".encodeToByteArray())
        assertNull(s.ratchet.decrypt(s.a, frame))
    }

    @Test
    fun bobsInitialChainsMirrorAlices() {
        val s = newSession()
        assertContentEquals(
            s.a.recvChainKey,
            s.b.sendChainKey,
            "A must be able to read B's opening message"
        )
        assertEquals(0, s.a.sendCounter)
        assertEquals(0, s.b.sendCounter)
    }
}
