package dev.stade.audio

import androidx.compose.runtime.Composable

expect class AudioPlayer {
    fun play(opusBytes: ByteArray)
    fun pause()
    fun stop()
    val isPlaying: Boolean
    val positionMs: Int
    val durationMs: Int
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer
