package app.stade.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? =
    runCatching {
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    }.getOrNull()

