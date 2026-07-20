package dev.stade.audio

import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

actual class AudioPlayer {
    private var cachedOpus: ByteArray? = null
    private var pcm: ShortArray? = null
    private var track: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile private var stopRequested = false
    @Volatile private var offsetSamples = 0

    private var playingState by mutableStateOf(false)
    actual val isPlaying: Boolean get() = playingState

    private var positionState by mutableStateOf(0)
    actual val positionMs: Int get() = positionState

    private var durationState by mutableStateOf(0)
    actual val durationMs: Int get() = durationState

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
        val minBuf = AudioTrack.getMinBufferSize(
            AudioFormat.SAMPLE_RATE,
            AndroidAudioFormat.CHANNEL_OUT_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT
        ).let { if (it > 0) it else AudioFormat.FRAME_SAMPLES * 4 }

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AndroidAudioFormat.Builder()
                .setSampleRate(AudioFormat.SAMPLE_RATE)
                .setChannelMask(AndroidAudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                .build(),
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track = audioTrack
        stopRequested = false
        playingState = true
        positionState = (offsetSamples * 1000 / AudioFormat.SAMPLE_RATE)
        audioTrack.play()

        playThread = Thread {
            var offset = offsetSamples
            val chunk = AudioFormat.FRAME_SAMPLES
            while (!stopRequested && offset < samples.size) {
                val len = minOf(chunk, samples.size - offset)
                audioTrack.write(samples, offset, len)
                offset += len
                offsetSamples = offset
                positionState = (offset * 1000 / AudioFormat.SAMPLE_RATE)
            }
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            track = null
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
