package dev.stade.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? =
    runCatching {
        SkiaImage.makeFromEncoded(this).toComposeImageBitmap()
    }.getOrNull()

