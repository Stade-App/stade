package dev.stade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.stade.ui.decodeToImageBitmap

const val MAX_GIF_FRAMES = 160
const val MAX_GIF_FRAME_PIXELS = 1_600_000L
const val DEFAULT_GIF_FRAME_DELAY_MS = 100

fun isGifBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 6) return false
    val header = bytes.copyOfRange(0, 6).decodeToString()
    return header == "GIF87a" || header == "GIF89a"
}

@Composable
expect fun AnimatedImage(
    bytes: ByteArray,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
)

@Composable
internal fun StaticImageBytes(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val bitmap: ImageBitmap? = remember(bytes) {
        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
    }
    if (bitmap == null) {
        Box(modifier)
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
