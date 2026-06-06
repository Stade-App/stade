package app.stade.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

actual suspend fun saveImageToGallery(bytes: ByteArray, suggestedName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val ext = when {
                suggestedName.endsWith(".png", true) -> "png"
                suggestedName.endsWith(".gif", true) -> "gif"
                suggestedName.endsWith(".webp", true) -> "webp"
                else -> "jpg"
            }
            val defaultName = "stade_" + System.currentTimeMillis() + "." + ext
            val chosen = askUserForSaveLocation(defaultName, ext) ?: return@withContext false
            val finalFile =
                if (chosen.extension.isNotEmpty()) chosen
                else File(chosen.parentFile, chosen.name + "." + ext)
            finalFile.parentFile?.mkdirs()
            finalFile.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

/**
 * Modal bir kayıt iletişim kutusu açar ve kullanıcının seçtiği [File]'ı döndürür.
 * Kullanıcı iptal ederse `null` döner.
 */
private fun askUserForSaveLocation(defaultName: String, ext: String): File? {
    val ref = java.util.concurrent.atomic.AtomicReference<File?>(null)
    val task = Runnable {
        val parent: Frame? = Frame.getFrames().firstOrNull { it.isShowing }
        val dialog = FileDialog(parent, "Medyayı kaydet", FileDialog.SAVE)
        dialog.file = defaultName
        val home = System.getProperty("user.home")
        if (home != null) {
            val pictures = File(home, "Pictures")
            val downloads = File(home, "Downloads")
            dialog.directory = when {
                pictures.isDirectory -> pictures.absolutePath
                downloads.isDirectory -> downloads.absolutePath
                else -> home
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) {
            ref.set(File(dir, name))
            return@Runnable
        }

        val chooser = JFileChooser().apply {
            dialogTitle = "Medyayı kaydet"
            selectedFile = File(defaultName)
            fileFilter = FileNameExtensionFilter("Görüntü (*.${ext})", ext)
        }
        val result = chooser.showSaveDialog(parent)
        if (result == JFileChooser.APPROVE_OPTION) {
            ref.set(chooser.selectedFile)
        }
    }
    if (SwingUtilities.isEventDispatchThread()) {
        task.run()
    } else {
        SwingUtilities.invokeAndWait(task)
    }
    return ref.get()
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

