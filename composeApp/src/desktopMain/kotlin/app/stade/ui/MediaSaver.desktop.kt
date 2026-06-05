package app.stade.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

actual suspend fun saveImageToGallery(bytes: ByteArray, suggestedName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val home = System.getProperty("user.home") ?: return@withContext false
            val pictures = File(home, "Pictures")
            val target = if (pictures.exists()) pictures else File(home, "Downloads").takeIf { it.exists() } ?: File(home)
            target.mkdirs()
            val ext = when {
                suggestedName.endsWith(".png", true) -> "png"
                suggestedName.endsWith(".gif", true) -> "gif"
                suggestedName.endsWith(".webp", true) -> "webp"
                else -> "jpg"
            }
            val base = "stade_" + System.currentTimeMillis()
            var file = File(target, "$base.$ext")
            var counter = 1
            while (file.exists()) {
                file = File(target, "${base}_$counter.$ext")
                counter++
            }
            file.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

actual suspend fun copyImageToClipboard(bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val image: BufferedImage = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext false
            val transferable = object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                    flavor == DataFlavor.imageFlavor
                override fun getTransferData(flavor: DataFlavor): Any {
                    if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
                    return image
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            true
        }.getOrDefault(false)
    }

