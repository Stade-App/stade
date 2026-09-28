package dev.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.stade.sticker.MAX_IMPORT_ARCHIVE_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class StickerImportLauncher(private val onPick: () -> Unit) {
    actual fun pickFiles() = onPick()
}

@Composable
actual fun rememberStickerImportLauncher(
    onPicked: (List<PickedStickerFile>) -> Unit
): StickerImportLauncher {
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)

    return remember {
        StickerImportLauncher {
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    val dialog = FileDialog(null as Frame?, "Stade", FileDialog.LOAD)
                    dialog.isMultipleMode = true
                    dialog.isVisible = true
                    dialog.files.orEmpty().mapNotNull { readPicked(it) }
                }
                callback(files)
            }
        }
    }
}

private fun readPicked(file: File): PickedStickerFile? = runCatching {
    if (!file.isFile || file.length() > MAX_IMPORT_ARCHIVE_BYTES) return null
    PickedStickerFile(file.name, file.readBytes())
}.getOrNull()
