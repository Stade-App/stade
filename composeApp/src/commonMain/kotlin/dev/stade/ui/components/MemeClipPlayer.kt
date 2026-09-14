package dev.stade.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.stade.AppContainer
import dev.stade.message.markMemePlayed
import dev.stade.message.memeAlreadyPlayed
import dev.stade.ui.video.VideoPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MemeClipPlayer(
    container: AppContainer,
    messageId: String,
    bytes: ByteArray?,
    modifier: Modifier
) {
    if (bytes == null) {
        Box(modifier)
        return
    }
    var autoPlay by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId) {
        val alreadyPlayed = withContext(Dispatchers.Default) {
            memeAlreadyPlayed(container.db, messageId)
        }
        if (!alreadyPlayed) {
            withContext(Dispatchers.Default) { markMemePlayed(container.db, messageId) }
            autoPlay = true
        }
    }
    VideoPlayerView(bytes = bytes, modifier = modifier, autoPlay = autoPlay)
}
