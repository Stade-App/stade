package dev.stade.ui

import androidx.compose.runtime.Composable

expect class VideoPickerLauncher {
    fun launch()
}

@Composable
expect fun rememberVideoPickerLauncher(onVideo: (ByteArray) -> Unit): VideoPickerLauncher
