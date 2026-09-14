package dev.stade.transport

import dev.stade.crypto.platformCrypto
import dev.stade.sync.FrameCodec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val crypto = platformCrypto()

private class Pipe {
    val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    val bToA = Channel<ByteArray>(Channel.UNLIMITED)
}

private class PipeConnection(
    private val outbound: Channel<ByteArray>,
    private val inbound: Channel<ByteArray>,
    override val remoteAddress: String = "lan://test"
) : Connection {
    val sentRaw = mutableListOf<ByteArray>()
    override suspend fun send(frame: ByteArray) {
        sentRaw += frame
        outbound.send(frame)
    }
    override suspend fun receive(): ByteArray? = inbound.receive()
    override suspend fun close() {}
}

private fun linkPair(): Pair<LinkEncryption, LinkEncryption> {
    val a = crypto.generateAgreementKeyPair()
    val b = crypto.generateAgreementKeyPair()
    val la = LinkEncryption.derive(crypto, a.publicKey, a.privateKey, b.publicKey)!!
    val lb = LinkEncryption.derive(crypto, b.publicKey, b.privateKey, a.publicKey)!!
    return la to lb
}

class LinkEncryptionTest {

    @Test
    fun bothSidesAgreeOnDirectionalKeys() {
        val (a, b) = linkPair()
        val sealed = a.seal("hello".encodeToByteArray())
        assertContentEquals("hello".encodeToByteArray(), b.open(sealed))
        val back = b.seal("hi".encodeToByteArray())
        assertContentEquals("hi".encodeToByteArray(), a.open(back))
    }

    @Test
    fun eachFrameUsesADistinctNonce() {
        val (a, b) = linkPair()
        val first = a.seal("same".encodeToByteArray())
        val second = a.seal("same".encodeToByteArray())
        assertTrue(
            !first.contentEquals(second),
            "identical plaintexts produced identical ciphertexts, so the nonce is not advancing"
        )
        assertContentEquals("same".encodeToByteArray(), b.open(first))
        assertContentEquals("same".encodeToByteArray(), b.open(second))
    }

    @Test
    fun manyFramesStayInSync() {
        val (a, b) = linkPair()
        repeat(200) { i ->
            assertContentEquals("m$i".encodeToByteArray(), b.open(a.seal("m$i".encodeToByteArray())))
        }
    }

    @Test
    fun aTamperedFrameIsRejected() {
        val (a, b) = linkPair()
        val sealed = a.seal("authentic".encodeToByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertNull(b.open(sealed), "a tampered link frame must not open")
    }

    @Test
    fun aReorderedFrameIsRejected() {
        val (a, b) = linkPair()
        val first = a.seal("one".encodeToByteArray())
        val second = a.seal("two".encodeToByteArray())
        assertNull(b.open(second), "frames delivered out of order must not open")
        assertNull(b.open(first))
    }

    @Test
    fun anUnrelatedKeyCannotOpenTheFrame() {
        val (a, _) = linkPair()
        val (_, stranger) = linkPair()
        assertNull(stranger.open(a.seal("secret".encodeToByteArray())))
    }

    @Test
    fun keyExchangeFrameRoundTrips() {
        val kp = crypto.generateAgreementKeyPair()
        val frame = LinkEncryption.keyExchangeFrame(kp.publicKey)
        assertEquals(34, frame.size)
        assertContentEquals(kp.publicKey, LinkEncryption.peerKeyFrom(frame))
    }

    @Test
    fun aRegularRecordIsNotMistakenForAKeyExchange() {
        val notKx = ByteArray(34) { 0x03 }
        assertNull(LinkEncryption.peerKeyFrom(notKx))
        assertNull(LinkEncryption.peerKeyFrom(ByteArray(10)))
    }

    @Test
    fun aLegacyPeerWouldDiscardTheKeyExchangeFrame() {
        val kp = crypto.generateAgreementKeyPair()
        val frame = LinkEncryption.keyExchangeFrame(kp.publicKey)
        assertNull(
            FrameCodec.decode(frame),
            "a peer without link encryption must treat the key exchange as an unknown record " +
                "and skip it, not misparse it"
        )
    }

    @Test
    fun twoUpgradedPeersNegotiateAndEncryptTraffic() = runTest {
        val pipe = Pipe()
        val rawA = PipeConnection(pipe.aToB, pipe.bToA)
        val rawB = PipeConnection(pipe.bToA, pipe.aToB)
        val a = EncryptedLinkConnection(rawA, crypto)
        val b = EncryptedLinkConnection(rawB, crypto)

        val ja = launch { a.send("hello".encodeToByteArray()) }
        val jb = launch { b.send("hi".encodeToByteArray()) }
        ja.join()
        jb.join()

        assertContentEquals("hello".encodeToByteArray(), b.receive())
        assertContentEquals("hi".encodeToByteArray(), a.receive())

        val payloads = rawA.sentRaw.drop(1)
        assertTrue(payloads.isNotEmpty())
        assertTrue(
            payloads.none { it.contentEquals("hello".encodeToByteArray()) },
            "the plaintext payload appeared on the wire"
        )
    }

    @Test
    fun aLegacyPeerStillExchangesPlaintext() = runTest {
        val pipe = Pipe()
        val rawA = PipeConnection(pipe.aToB, pipe.bToA)
        val a = EncryptedLinkConnection(rawA, crypto)

        pipe.bToA.send("HELLO-FROM-LEGACY".encodeToByteArray())
        a.send("first".encodeToByteArray())

        assertContentEquals(
            "HELLO-FROM-LEGACY".encodeToByteArray(),
            a.receive(),
            "the first frame from a peer without link encryption must be delivered, not swallowed"
        )
        val afterKx = rawA.sentRaw.drop(1).first()
        assertContentEquals(
            "first".encodeToByteArray(),
            afterKx,
            "traffic to a legacy peer must stay in the old plaintext format"
        )
    }

    @Test
    fun negotiationHappensOnlyOnce() = runTest {
        val pipe = Pipe()
        val rawA = PipeConnection(pipe.aToB, pipe.bToA)
        val rawB = PipeConnection(pipe.bToA, pipe.aToB)
        val a = EncryptedLinkConnection(rawA, crypto)
        val b = EncryptedLinkConnection(rawB, crypto)

        val ja = launch { a.send("one".encodeToByteArray()) }
        val jb = launch { b.send("ack".encodeToByteArray()) }
        ja.join()
        jb.join()
        assertNotNull(b.receive())
        assertNotNull(a.receive())

        a.send("two".encodeToByteArray())
        a.send("three".encodeToByteArray())
        assertContentEquals("two".encodeToByteArray(), b.receive())
        assertContentEquals("three".encodeToByteArray(), b.receive())

        val kxFrames = rawA.sentRaw.count { LinkEncryption.peerKeyFrom(it) != null }
        assertEquals(1, kxFrames, "the key exchange frame should be sent exactly once")
    }
}
