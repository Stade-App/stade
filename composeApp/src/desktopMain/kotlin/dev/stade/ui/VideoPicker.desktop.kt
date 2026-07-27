package dev.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class VideoPickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

private val VIDEO_EXTS = setOf("mp4")

private fun openNativeVideoDialog(title: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val ext = name.substringAfterLast('.', "").lowercase()
        ext in VIDEO_EXTS
    }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

@Composable
actual fun rememberVideoPickerLauncher(onVideo: (ByteArray) -> Unit): VideoPickerLauncher {
    val scope = rememberCoroutineScope()
    val title = LocalStrings.current.selectMediaTitle
    return remember(title) {
        VideoPickerLauncher {
            scope.launch(Dispatchers.IO) {
                val file = openNativeVideoDialog(title) ?: return@launch
                runCatching { onVideo(file.readBytes()) }
            }
        }
    }
}
