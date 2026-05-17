package app.stade.ui

import androidx.compose.ui.graphics.ImageBitmap

expect fun ByteArray.decodeToImageBitmap(): ImageBitmap?

