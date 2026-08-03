package dev.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

actual class MediaPickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
private val VIDEO_EXTS = setOf("mp4")

private fun openNativeMediaDialog(title: String): Array<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.setFilenameFilter { _, name ->
        val ext = name.substringAfterLast('.', "").lowercase()
        ext in IMAGE_EXTS || ext in VIDEO_EXTS
    }
    dialog.isVisible = true
    return dialog.files ?: emptyArray()
}

@Composable
actual fun rememberMediaPickerLauncher(
    onImages: (List<ByteArray>) -> Unit,
    onVideo: (ByteArray) -> Unit
): MediaPickerLauncher {
    val scope = rememberCoroutineScope()
    val title = LocalStrings.current.selectMediaTitle
    return remember(title) {
        MediaPickerLauncher {
            scope.launch(Dispatchers.IO) {
                val files = openNativeMediaDialog(title)
                if (files.isEmpty()) return@launch
                val images = mutableListOf<ByteArray>()
                var video: ByteArray? = null
                for (file in files) {
                    val ext = file.name.substringAfterLast('.', "").lowercase()
                    if (ext in VIDEO_EXTS) {
                        if (video == null) {
                            video = runCatching { file.readBytes() }.getOrNull()
                        }
                    } else {
                        runCatching { compressImageForAttach(file.readBytes()) }.getOrNull()?.let { images.add(it) }
                    }
                }
                if (images.isNotEmpty()) onImages(images)
                video?.let { onVideo(it) }
            }
        }
    }
}

private fun compressImageForAttach(bytes: ByteArray): ByteArray {
    val img: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return bytes
    val maxDim = 1280
    val scaled = if (img.width > maxDim || img.height > maxDim) {
        val scale = maxDim.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()
        val dest = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = dest.createGraphics()
        try {
            g.drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
        } finally {
            g.dispose()
        }
        dest
    } else img
    val out = ByteArrayOutputStream()
    ImageIO.write(scaled, "jpeg", out)
    return out.toByteArray()
}
