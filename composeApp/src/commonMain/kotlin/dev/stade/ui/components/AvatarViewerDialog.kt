package dev.stade.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.stade.ui.decodeToImageBitmap

@Composable
fun AvatarViewerDialog(avatarBytes: ByteArray, onDismiss: () -> Unit) {
    val bitmap = remember(avatarBytes) { runCatching { avatarBytes.decodeToImageBitmap() }.getOrNull() }
    if (bitmap == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    FullScreenImageViewer(
        bitmap = bitmap,
        contentDescription = null,
        onDismiss = onDismiss
    )
}
