package dev.stade.ui

import androidx.compose.runtime.Composable

expect class MediaPickerLauncher {
    fun launch()
}

@Composable
expect fun rememberMediaPickerLauncher(
    onImages: (List<ByteArray>) -> Unit,
    onVideo: (ByteArray) -> Unit
): MediaPickerLauncher
