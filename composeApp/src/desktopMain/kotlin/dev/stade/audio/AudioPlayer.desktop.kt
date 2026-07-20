package dev.stade.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import javax.sound.sampled.AudioFormat as JavaAudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

actual class AudioPlayer {
    private var cachedOpus: ByteArray? = null
    private var pcm: ShortArray? = null
    private var playThread: Thread? = null
    @Volatile private var stopRequested = false
    @Volatile private var offsetSamples = 0

    private var playingState by mutableStateOf(false)
    actual val isPlaying: Boolean get() = playingState

    private var positionState by mutableStateOf(0)
    actual val positionMs: Int get() = positionState

    private var durationState by mutableStateOf(0)
    actual val durationMs: Int get() = durationState

    private fun javaFormat() = JavaAudioFormat(
        AudioFormat.SAMPLE_RATE.toFloat(), 16, AudioFormat.CHANNELS, true, false
    )

    actual fun play(opusBytes: ByteArray) {
        if (playingState) return
        val samples: ShortArray
        val cached = pcm
        if (cachedOpus === opusBytes && cached != null) {
            samples = cached
        } else {
            samples = runCatching { OpusCodec.decode(opusBytes) }.getOrNull() ?: return
            if (samples.isEmpty()) return
            cachedOpus = opusBytes
            pcm = samples
            offsetSamples = 0
            durationState = (samples.size * 1000L / AudioFormat.SAMPLE_RATE).toInt()
        }
        startPlaybackThread(samples)
    }

    private fun startPlaybackThread(samples: ShortArray) {
        val fmt = javaFormat()
        val info = DataLine.Info(SourceDataLine::class.java, fmt)
        if (!AudioSystem.isLineSupported(info)) return
        val sourceLine = runCatching { AudioSystem.getLine(info) as SourceDataLine }.getOrNull() ?: return
        val opened = runCatching { sourceLine.open(fmt) }.isSuccess
        if (!opened) return

        stopRequested = false
        playingState = true
        positionState = (offsetSamples * 1000 / AudioFormat.SAMPLE_RATE)
        sourceLine.start()

        playThread = Thread {
            var offset = offsetSamples
            val chunkSamples = AudioFormat.FRAME_SAMPLES
            val byteBuf = ByteArray(chunkSamples * 2)
            while (!stopRequested && offset < samples.size) {
                val len = minOf(chunkSamples, samples.size - offset)
                for (i in 0 until len) {
                    val s = samples[offset + i].toInt()
                    byteBuf[i * 2] = (s and 0xFF).toByte()
                    byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                sourceLine.write(byteBuf, 0, len * 2)
                offset += len
                offsetSamples = offset
                positionState = (offset * 1000 / AudioFormat.SAMPLE_RATE)
            }
            if (!stopRequested) runCatching { sourceLine.drain() }
            runCatching { sourceLine.stop() }
            runCatching { sourceLine.close() }
            playingState = false
            if (!stopRequested) {
                offsetSamples = 0
                positionState = 0
            }
        }.apply { start() }
    }

    actual fun pause() {
        stopRequested = true
        val t = playThread
        playThread = null
        if (Thread.currentThread() != t) {
            t?.join(1000)
        }
        playingState = false
    }

    actual fun stop() {
        pause()
        offsetSamples = 0
        positionState = 0
        pcm = null
        cachedOpus = null
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    return remember { AudioPlayer() }
}
