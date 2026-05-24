package app.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

actual class ImagePickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

private fun openNativeImageDialog(multiSelect: Boolean): Array<File> {
    // java.awt.FileDialog işletim sisteminin native dosya gezginini kullanır
    // (Windows Explorer / macOS Finder / Linux GTK). JFileChooser yerine bunu kullanıyoruz.
    val dialog = FileDialog(null as Frame?, "Fotoğraf seç", FileDialog.LOAD)
    dialog.isMultipleMode = multiSelect
    dialog.setFilenameFilter { _, name ->
        val ext = name.substringAfterLast('.', "").lowercase()
        ext in IMAGE_EXTS
    }
    dialog.isVisible = true
    val files = dialog.files
    return files ?: emptyArray()
}

@Composable
actual fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher {
    val scope = rememberCoroutineScope()
    return remember {
        ImagePickerLauncher {
            scope.launch(Dispatchers.IO) {
                val files = openNativeImageDialog(multiSelect = false)
                val file = files.firstOrNull() ?: return@launch
                runCatching {
                    onImage(compressImageDesktop(file.readBytes()))
                }
            }
        }
    }
}

@Composable
actual fun rememberMultiImagePickerLauncher(onImages: (List<ByteArray>) -> Unit): ImagePickerLauncher {
    val scope = rememberCoroutineScope()
    return remember {
        ImagePickerLauncher {
            scope.launch(Dispatchers.IO) {
                val files = openNativeImageDialog(multiSelect = true)
                if (files.isEmpty()) return@launch
                val results = files.mapNotNull { f ->
                    runCatching { compressImageDesktop(f.readBytes()) }.getOrNull()
                }
                if (results.isNotEmpty()) onImages(results)
            }
        }
    }
}

private fun compressImageDesktop(bytes: ByteArray): ByteArray {
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

