package dev.stade.audio

import androidx.compose.runtime.Composable

const val MAX_VOICE_DURATION_MS = 5 * 60 * 1000

data class RecordedClip(val opusBytes: ByteArray, val durationMs: Int)

expect class AudioRecorder {
    fun start()
    fun stop(): RecordedClip?
    fun cancel()
}

@Composable
expect fun rememberAudioRecorder(onMaxDurationReached: (RecordedClip?) -> Unit): AudioRecorder
