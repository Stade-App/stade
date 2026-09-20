package dev.stade.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes an attachment body once, off the main thread. Calling the decoder directly from
 * composition re-runs a base64 decode of the whole payload on every recomposition, which
 * stalls the frame for large clips.
 */
@Composable
fun rememberAttachmentBytes(key: String, decode: () -> ByteArray?): ByteArray? {
    var bytes by remember(key) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(key) {
        bytes = withContext(Dispatchers.Default) { runCatching { decode() }.getOrNull() }
    }
    return bytes
}
