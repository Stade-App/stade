package app.stade.ui

import androidx.compose.runtime.Composable

expect class ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher

