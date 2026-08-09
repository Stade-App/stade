package dev.stade.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

actual suspend fun shareFile(bytes: ByteArray, filename: String, mimeType: String, title: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val chosen = askUserForSaveLocation(filename, title) ?: return@withContext false
            chosen.parentFile?.mkdirs()
            chosen.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

private fun askUserForSaveLocation(defaultName: String, title: String): File? {
    val ref = AtomicReference<File?>(null)
    val task = Runnable {
        val parent: Frame? = Frame.getFrames().firstOrNull { it.isShowing }
        val dialog = FileDialog(parent, title, FileDialog.SAVE)
        dialog.file = defaultName
        val home = System.getProperty("user.home")
        if (home != null) {
            val downloads = File(home, "Downloads")
            dialog.directory = if (downloads.isDirectory) downloads.absolutePath else home
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) {
            ref.set(File(dir, name))
        }
    }
    if (SwingUtilities.isEventDispatchThread()) {
        task.run()
    } else {
        SwingUtilities.invokeAndWait(task)
    }
    return ref.get()
}
