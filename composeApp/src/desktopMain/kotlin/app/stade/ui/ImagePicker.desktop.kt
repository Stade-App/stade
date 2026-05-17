package app.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual class ImagePickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

@Composable
actual fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher {
    val scope = rememberCoroutineScope()
    return remember {
        ImagePickerLauncher {
            scope.launch(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Image"
                    fileFilter = FileNameExtensionFilter(
                        "Images (JPG, PNG, GIF, BMP, WEBP)",
                        "jpg", "jpeg", "png", "gif", "bmp", "webp"
                    )
                    isAcceptAllFileFilterUsed = false
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        val bytes = chooser.selectedFile.readBytes()
                        onImage(compressImageDesktop(bytes))
                    }
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

