package dev.stade.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? =
    runCatching {
        val info = Data.makeFromBytes(this).use { data ->
            Codec.makeFromData(data).use { codec -> codec.imageInfo }
        }
        if (info.width <= 0 || info.height <= 0 || info.width > MAX_DECODABLE_IMAGE_DIMENSION ||
            info.height > MAX_DECODABLE_IMAGE_DIMENSION || info.width.toLong() * info.height > MAX_DECODABLE_IMAGE_PIXELS
        ) return@runCatching null
        SkiaImage.makeFromEncoded(this).toComposeImageBitmap()
    }.getOrNull()

