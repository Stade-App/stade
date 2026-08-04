package dev.stade.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

actual class MediaPickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

@Composable
actual fun rememberMediaPickerLauncher(
    onImages: (List<ByteArray>) -> Unit,
    onVideo: (ByteArray) -> Unit,
    imagesOnly: Boolean
): MediaPickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val images = mutableListOf<ByteArray>()
        var video: ByteArray? = null
        for (uri in uris) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) {
                if (video == null) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.readBytes()
                    }.getOrNull()?.let { video = it }
                }
            } else {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                        ?.let { compressImageForAttach(it) }
                }.getOrNull()?.let { images.add(it) }
            }
        }
        if (images.isNotEmpty()) onImages(images)
        video?.let { onVideo(it) }
    }
    val mediaType = if (imagesOnly) {
        ActivityResultContracts.PickVisualMedia.ImageOnly
    } else {
        ActivityResultContracts.PickVisualMedia.ImageAndVideo
    }
    return MediaPickerLauncher {
        launcher.launch(PickVisualMediaRequest(mediaType))
    }
}

private fun compressImageForAttach(bytes: ByteArray): ByteArray {
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
