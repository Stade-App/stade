package dev.stade.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberAttachmentBytes(key: String, decode: () -> ByteArray?): ByteArray? {
    var bytes by remember(key) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(key) {
        bytes = withContext(Dispatchers.Default) { runCatching { decode() }.getOrNull() }
    }
    return bytes
}
