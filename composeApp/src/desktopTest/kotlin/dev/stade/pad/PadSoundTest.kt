package dev.stade.pad

import dev.stade.audio.AudioFormat
import dev.stade.audio.OpusCodec
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadSoundTest {

    @Test
    fun aStandardOpusFileCannotBePlayedAndIsReportedRatherThanSentSilently() {
        val oggOpus = oggOpusLikeFile()
        assertTrue(
            OpusCodec.decode(oggOpus).isEmpty(),
            "an Ogg-encapsulated .opus file yields no samples in Stade's framing"
        )
        assertNull(
            preparePadSound(oggOpus),
            "an unreadable sound must be rejected so the drawer can report it"
        )
    }

    @Test
    fun a16kMonoWavBecomesPlayableAudio() {
        val prepared = assertNotNull(preparePadSound(wav(tone(16_000, 16_000, 1), 16_000, 1)))
        val samples = OpusCodec.decode(prepared.bytes)
        assertEquals(16_000, samples.size)
        assertEquals(1000L, prepared.durationMs)
        assertTrue(samples.any { abs(it.toInt()) > 500 }, "decoded audio should not be silence")
    }

    @Test
    fun stereo44kWavIsDownmixedAndResampled() {
        val prepared = assertNotNull(preparePadSound(wav(tone(44_100, 44_100, 2), 44_100, 2)))
        val samples = OpusCodec.decode(prepared.bytes)
        assertEquals(16_000, samples.size, "44.1kHz stereo should land at 16kHz mono")
        assertEquals(1000L, prepared.durationMs)
        assertTrue(samples.any { abs(it.toInt()) > 500 })
    }

    @Test
    fun eightBitAndFloatWavAreSupported() {
        val pcm = tone(16_000, 8_000, 1)
        for (spec in listOf(8 to 1, 24 to 1, 32 to 1, 32 to 3)) {
            val (bits, format) = spec
            val prepared = assertNotNull(
                preparePadSound(wav(pcm, 16_000, 1, bits, format)),
                "bits=$bits format=$format should decode"
            )
            val samples = OpusCodec.decode(prepared.bytes)
            assertEquals(8_000, samples.size, "bits=$bits format=$format")
            assertTrue(samples.any { abs(it.toInt()) > 300 }, "bits=$bits format=$format was silent")
        }
    }

    @Test
    fun theEncodedSoundIsFarSmallerThanTheSourceWav() {
        val source = wav(tone(44_100, 44_100, 2), 44_100, 2)
        val prepared = assertNotNull(preparePadSound(source))
        assertTrue(
            prepared.bytes.size * 10 < source.size,
            "opus (${prepared.bytes.size}) should be far under the wav (${source.size})"
        )
    }

    @Test
    fun alreadyFramedAudioPassesThroughUnchanged() {
        val framed = OpusCodec.encode(tone(16_000, 16_000, 1))
        val prepared = assertNotNull(preparePadSound(framed))
        assertTrue(prepared.bytes === framed, "native framing must not be re-encoded")
        assertEquals(1000L, prepared.durationMs)
    }

    @Test
    fun anOverlongSoundIsRejected() {
        val tooLong = (MAX_SOUND_DURATION_MS / 1000).toInt() + 5
        val pcm = tone(16_000, 16_000 * tooLong, 1)
        assertNull(preparePadSound(wav(pcm, 16_000, 1)))
    }

    @Test
    fun malformedInputIsRejectedWithoutThrowing() {
        assertNull(preparePadSound(ByteArray(0)))
        assertNull(preparePadSound("RIFF".encodeToByteArray()))
        assertNull(preparePadSound(ByteArray(64)))

        val good = wav(tone(16_000, 4_000, 1), 16_000, 1)
        for (cut in 0 until good.size step 7) {
            preparePadSound(good.copyOf(cut))
        }
        val random = Random(90210)
        repeat(500) {
            preparePadSound(ByteArray(random.nextInt(0, 300)) { random.nextInt().toByte() })
        }
    }

    private fun tone(rate: Int, frames: Int, channels: Int): ShortArray {
        val out = ShortArray(frames * channels)
        for (f in 0 until frames) {
            val v = (sin(2.0 * PI * 440.0 * f / rate) * 12000).toInt().toShort()
            for (c in 0 until channels) out[f * channels + c] = v
        }
        return out
    }

    private fun wav(
        interleaved: ShortArray,
        rate: Int,
        channels: Int,
        bits: Int = 16,
        format: Int = 1
    ): ByteArray {
        val body = ByteArrayOutputStream()
        for (s in interleaved) {
            when {
                format == 3 && bits == 32 -> le32(body, (s / 32768f).toRawBits())
                bits == 8 -> body.write(((s.toInt() shr 8) + 128) and 0xFF)
                bits == 16 -> le16(body, s.toInt())
                bits == 24 -> {
                    val v = s.toInt() shl 8
                    body.write(v and 0xFF); body.write((v shr 8) and 0xFF); body.write((v shr 16) and 0xFF)
                }
                else -> le32(body, s.toInt() shl 16)
            }
        }
        val data = body.toByteArray()
        val blockAlign = channels * bits / 8
        val out = ByteArrayOutputStream()
        out.write("RIFF".encodeToByteArray())
        le32(out, 36 + data.size)
        out.write("WAVE".encodeToByteArray())
        out.write("fmt ".encodeToByteArray())
        le32(out, 16)
        le16(out, format)
        le16(out, channels)
        le32(out, rate)
        le32(out, rate * blockAlign)
        le16(out, blockAlign)
        le16(out, bits)
        out.write("data".encodeToByteArray())
        le32(out, data.size)
        out.write(data)
        return out.toByteArray()
    }

    private fun le16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
    }

    private fun le32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
    }

    private fun oggOpusLikeFile(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OggS".encodeToByteArray())
        out.write(ByteArray(22))
        out.write(1)
        out.write(19)
        out.write("OpusHead".encodeToByteArray())
        out.write(ByteArray(11))
        out.write(ByteArray(400) { (it % 97).toByte() })
        return out.toByteArray()
    }
}
