package app.stade.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

actual class ImagePickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

@Composable
actual fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@runCatching
                onImage(compressImageAndroid(raw))
            }
        }
    }
    return ImagePickerLauncher { launcher.launch("image/*") }
}

@Composable
actual fun rememberMultiImagePickerLauncher(onImages: (List<ByteArray>) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val results = uris.mapNotNull { uri ->
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: return@runCatching null
                compressImageAndroid(raw)
            }.getOrNull()
        }
        if (results.isNotEmpty()) onImages(results)
    }
    return ImagePickerLauncher { launcher.launch("image/*") }
}

private fun compressImageAndroid(bytes: ByteArray): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val maxDim = 1280
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return out.toByteArray()
}

