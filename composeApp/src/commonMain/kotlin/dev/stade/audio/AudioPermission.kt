package dev.stade.audio

import androidx.compose.runtime.Composable

expect class AudioPermissionState {
    val granted: Boolean
    fun request()
}

@Composable
expect fun rememberAudioPermissionState(): AudioPermissionState
