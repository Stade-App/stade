package dev.stade.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javax.sound.sampled.AudioFormat as JavaAudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

actual class AudioRecorder(private val onMaxDurationReached: (RecordedClip?) -> Unit) {
    private var line: TargetDataLine? = null
    private var captureThread: Thread? = null
    @Volatile private var recording = false
    @Volatile private var finished = true
    @Volatile private var maxDurationHit = false
    private var startedAt = 0L
    private val chunks = ArrayList<ShortArray>()
    private val lock = Any()

    private fun javaFormat() = JavaAudioFormat(
        AudioFormat.SAMPLE_RATE.toFloat(), 16, AudioFormat.CHANNELS, true, false
    )

    actual fun start() {
        if (recording) return
        val fmt = javaFormat()
        val info = DataLine.Info(TargetDataLine::class.java, fmt)
        if (!AudioSystem.isLineSupported(info)) return
        val targetLine = runCatching { AudioSystem.getLine(info) as TargetDataLine }.getOrNull() ?: return
        val opened = runCatching { targetLine.open(fmt) }.isSuccess
        if (!opened) return

        line = targetLine
        synchronized(lock) { chunks.clear() }
        recording = true
        finished = false
        maxDurationHit = false
        startedAt = System.currentTimeMillis()
        targetLine.start()

        captureThread = Thread {
            val byteBuf = ByteArray(AudioFormat.FRAME_SAMPLES * 2)
            while (recording) {
                val read = targetLine.read(byteBuf, 0, byteBuf.size)
                if (read > 0) {
                    val shorts = ShortArray(read / 2)
                    for (i in shorts.indices) {
                        val lo = byteBuf[i * 2].toInt() and 0xFF
                        val hi = byteBuf[i * 2 + 1].toInt()
                        shorts[i] = ((hi shl 8) or lo).toShort()
                    }
                    synchronized(lock) { chunks.add(shorts) }
                }
                if (recording && System.currentTimeMillis() - startedAt >= MAX_VOICE_DURATION_MS) {
                    recording = false
                    maxDurationHit = true
                }
            }
            if (maxDurationHit) {
                val clip = finishRecording()
                finished = true
                onMaxDurationReached(clip)
            }
        }.apply { start() }
    }

    private fun finishRecording(): RecordedClip? {
        val activeLine = line
        line = null
        val durationMs = (System.currentTimeMillis() - startedAt).toInt()
        runCatching { activeLine?.stop() }
        runCatching { activeLine?.close() }
        val totalSamples = synchronized(lock) { chunks.sumOf { it.size } }
        if (totalSamples == 0) return null
        val pcm = ShortArray(totalSamples)
        var offset = 0
        synchronized(lock) {
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, pcm, offset, chunk.size)
                offset += chunk.size
            }
            chunks.clear()
        }
        return RecordedClip(OpusCodec.encode(pcm), durationMs)
    }

    actual fun stop(): RecordedClip? {
        if (finished) return null
        recording = false
        val thread = captureThread
        captureThread = null
        if (Thread.currentThread() != thread) {
            thread?.join(2000)
        }
        if (finished) return null
        finished = true
        return finishRecording()
    }

    actual fun cancel() {
        if (finished) return
        recording = false
        val thread = captureThread
        captureThread = null
        if (Thread.currentThread() != thread) {
            thread?.join(2000)
        }
        if (finished) return
        finished = true
        finishRecording()
    }
}

@Composable
actual fun rememberAudioRecorder(onMaxDurationReached: (RecordedClip?) -> Unit): AudioRecorder {
    return remember { AudioRecorder(onMaxDurationReached) }
}
