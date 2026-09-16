package dev.stade.ui

import androidx.compose.ui.graphics.ImageBitmap

const val MAX_DECODABLE_IMAGE_PIXELS = 16_000_000L
const val MAX_DECODABLE_IMAGE_DIMENSION = 8_192

expect fun ByteArray.decodeToImageBitmap(): ImageBitmap?

