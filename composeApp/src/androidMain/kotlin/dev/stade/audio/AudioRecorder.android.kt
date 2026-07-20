package dev.stade.audio

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class AudioRecorder(private val onMaxDurationReached: (RecordedClip?) -> Unit) {
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var recording = false
    @Volatile private var finished = true
    @Volatile private var maxDurationHit = false
    private var startedAt = 0L
    private val chunks = ArrayList<ShortArray>()
    private val lock = Any()

    actual fun start() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(
            AudioFormat.SAMPLE_RATE,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT
        ).let { if (it > 0) it else AudioFormat.FRAME_SAMPLES * 4 }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioFormat.SAMPLE_RATE,
                AndroidAudioFormat.CHANNEL_IN_MONO,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (t: SecurityException) {
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        audioRecord = record
        synchronized(lock) { chunks.clear() }
        recording = true
        finished = false
        maxDurationHit = false
        startedAt = System.currentTimeMillis()
        record.startRecording()

        captureThread = Thread {
            val buf = ShortArray(AudioFormat.FRAME_SAMPLES)
            while (recording) {
                val read = record.read(buf, 0, buf.size)
                if (read > 0) synchronized(lock) { chunks.add(buf.copyOf(read)) }
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
        val record = audioRecord
        audioRecord = null
        val durationMs = (System.currentTimeMillis() - startedAt).toInt()
        runCatching { record?.stop() }
        runCatching { record?.release() }
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
