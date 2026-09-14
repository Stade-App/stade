package dev.stade.sync

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinaryPayloadTest {

    @Test
    fun aPayloadSurvivesTheBinaryRoundTrip() {
        val frame = ByteArray(4096) { (it % 251).toByte() }
        val original = MessagePayload("a1b2c3d4e5f60718293a4b5c6d7e8f90", 1_726_000_000_123L, frame)
        val encoded = assertNotNull(encodeBinaryPayload(original))
        val decoded = assertNotNull(decodeBinaryPayload(encoded))
        assertEquals(original.messageId, decoded.messageId)
        assertEquals(original.timestamp, decoded.timestamp)
        assertContentEquals(original.ratchetFrame, decoded.ratchetFrame)
    }

    @Test
    fun theBinaryEnvelopeIsSmallerThanTheJsonOne() {
        val frame = ByteArray(900_000) { (it % 255).toByte() }
        val payload = MessagePayload("a".repeat(32), 1_726_000_000_000L, frame)
        val binary = assertNotNull(encodeBinaryPayload(payload)).size
        val overhead = binary - frame.size
        assertTrue(overhead in 1..64, "binary overhead should be a fixed small header, was $overhead")
        val jsonApprox = frame.size * 4 / 3
        assertTrue(
            binary < jsonApprox,
            "the binary envelope ($binary) should beat base64 in JSON (~$jsonApprox)"
        )
    }

    @Test
    fun extremeTimestampsRoundTrip() {
        for (ts in listOf(0L, 1L, Long.MAX_VALUE, 1_726_000_000_000L)) {
            val encoded = assertNotNull(encodeBinaryPayload(MessagePayload("id", ts, ByteArray(4))))
            assertEquals(ts, assertNotNull(decodeBinaryPayload(encoded)).timestamp)
        }
    }

    @Test
    fun anEmptyFrameRoundTrips() {
        val encoded = assertNotNull(encodeBinaryPayload(MessagePayload("x", 5L, ByteArray(0))))
        val decoded = assertNotNull(decodeBinaryPayload(encoded))
        assertEquals(0, decoded.ratchetFrame.size)
        assertEquals("x", decoded.messageId)
    }

    @Test
    fun truncatedOrCorruptInputIsRejectedWithoutThrowing() {
        val good = assertNotNull(
            encodeBinaryPayload(MessagePayload("abcdef", 99L, ByteArray(64) { 7 }))
        )
        for (cut in 0 until good.size) {
            decodeBinaryPayload(good.copyOf(cut))
        }
        assertNull(decodeBinaryPayload(ByteArray(0)))
        assertNull(decodeBinaryPayload(ByteArray(9)))
        val wrongVersion = good.copyOf().also { it[0] = 9 }
        assertNull(decodeBinaryPayload(wrongVersion), "an unknown envelope version must be rejected")
        val zeroLen = good.copyOf().also { it[1] = 0 }
        assertNull(decodeBinaryPayload(zeroLen), "a zero-length message id must be rejected")
    }

    @Test
    fun randomBytesNeverThrow() {
        val random = Random(4242)
        repeat(2000) {
            decodeBinaryPayload(ByteArray(random.nextInt(0, 200)) { random.nextInt().toByte() })
        }
    }

    @Test
    fun theBinaryRecordTypeIsDistinctFromTheJsonOne() {
        assertEquals(3, RecordType.MESSAGE.code.toInt())
        assertEquals(8, RecordType.MESSAGE_BIN.code.toInt())
        assertEquals(RecordType.MESSAGE_BIN, RecordType.fromCode(8))
    }

    @Test
    fun aBinaryRecordFramesAndDecodesThroughTheCodec() {
        val payload = MessagePayload("mid", 1234L, ByteArray(2048) { 3 })
        val binary = assertNotNull(encodeBinaryPayload(payload))
        val framed = FrameCodec.encode(SyncRecord(RecordType.MESSAGE_BIN, binary))
        val record = assertNotNull(FrameCodec.decode(framed))
        assertEquals(RecordType.MESSAGE_BIN, record.type)
        assertContentEquals(payload.ratchetFrame, assertNotNull(decodeBinaryPayload(record.payload)).ratchetFrame)
    }
}
