package dev.stade.audio

import java.io.ByteArrayOutputStream
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder

object AudioFormat {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val FRAME_MS = 20
    const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
}

object OpusCodec {
    private const val MAX_PACKET_BYTES = 4000

    fun encode(pcm: ShortArray, bitrate: Int = 20_000): ByteArray {
        val encoder = OpusEncoder(AudioFormat.SAMPLE_RATE, AudioFormat.CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP)
        encoder.setBitrate(bitrate)
        val frame = AudioFormat.FRAME_SAMPLES
        val out = ByteArrayOutputStream()
        val packetBuf = ByteArray(MAX_PACKET_BYTES)
        val chunk = ShortArray(frame)
        var offset = 0
        while (offset < pcm.size) {
            val remaining = pcm.size - offset
            if (remaining >= frame) {
                System.arraycopy(pcm, offset, chunk, 0, frame)
            } else {
                System.arraycopy(pcm, offset, chunk, 0, remaining)
                java.util.Arrays.fill(chunk, remaining, frame, 0)
            }
            val len = encoder.encode(chunk, 0, frame, packetBuf, 0, MAX_PACKET_BYTES)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
            out.write(packetBuf, 0, len)
            offset += frame
        }
        return out.toByteArray()
    }

    fun decode(opusStream: ByteArray): ShortArray {
        val decoder = OpusDecoder(AudioFormat.SAMPLE_RATE, AudioFormat.CHANNELS)
        val frame = AudioFormat.FRAME_SAMPLES

        var packetCount = 0
        var scan = 0
        while (scan + 2 <= opusStream.size) {
            val len = ((opusStream[scan].toInt() and 0xFF) shl 8) or (opusStream[scan + 1].toInt() and 0xFF)
            scan += 2 + len
            if (scan > opusStream.size) break
            packetCount++
        }

        val out = ShortArray(packetCount * frame)
        val pcmBuf = ShortArray(frame)
        var offset = 0
        var writeOffset = 0
        var i = 0
        while (i < packetCount) {
            val len = ((opusStream[offset].toInt() and 0xFF) shl 8) or (opusStream[offset + 1].toInt() and 0xFF)
            offset += 2
            val decoded = decoder.decode(opusStream, offset, len, pcmBuf, 0, frame, false)
            System.arraycopy(pcmBuf, 0, out, writeOffset, decoded)
            writeOffset += decoded
            offset += len
            i++
        }
        return if (writeOffset == out.size) out else out.copyOf(writeOffset)
    }
}
