package app.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilenameFilter
import javax.imageio.ImageIO

actual class ImagePickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

@Composable
actual fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher {
    val scope = rememberCoroutineScope()
    val title = LocalStrings.current.selectMediaTitle
    return remember(title) {
        ImagePickerLauncher {
            scope.launch(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                    filenameFilter = FilenameFilter { _, name ->
                        val lower = name.lowercase()
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".gif") ||
                        lower.endsWith(".bmp") || lower.endsWith(".webp")
                    }
                    isMultipleMode = false
                }
                dialog.pack()
                dialog.setLocationRelativeTo(null)
                dialog.isVisible = true
                val dir  = dialog.directory ?: return@launch
                val file = dialog.file        ?: return@launch
                runCatching {
                    val bytes = File(dir, file).readBytes()
                    onImage(compressImageDesktop(bytes))
                }
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

