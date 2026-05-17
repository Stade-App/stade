package app.stade.ui

import androidx.compose.runtime.Composable

/**
 * Platform bazlı fotoğraf seçici.
 * [onImage] — seçilen görselin sıkıştırılmış JPEG baytlarını döndürür.
 */
expect class ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher

