package dev.stade.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width > MAX_DECODABLE_IMAGE_DIMENSION ||
            height > MAX_DECODABLE_IMAGE_DIMENSION || width.toLong() * height > MAX_DECODABLE_IMAGE_PIXELS
        ) return@runCatching null
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    }.getOrNull()

