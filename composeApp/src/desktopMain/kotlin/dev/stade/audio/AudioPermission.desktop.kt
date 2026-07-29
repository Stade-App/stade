package dev.stade.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class AudioPermissionState {
    actual val granted: Boolean = true
    actual fun request() {
    }
}

@Composable
actual fun rememberAudioPermissionState(): AudioPermissionState {
    return remember { AudioPermissionState() }
}
