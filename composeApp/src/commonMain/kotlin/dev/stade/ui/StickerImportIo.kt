package dev.stade.ui

import androidx.compose.runtime.Composable
import dev.stade.sticker.ImportedImage

class PickedStickerFile(val name: String, val bytes: ByteArray)

expect class StickerImportLauncher {
    fun pickFiles()
}

@Composable
expect fun rememberStickerImportLauncher(
    onPicked: (List<PickedStickerFile>) -> Unit
): StickerImportLauncher

fun PickedStickerFile.asImportedImage(): ImportedImage = ImportedImage(name, bytes)
