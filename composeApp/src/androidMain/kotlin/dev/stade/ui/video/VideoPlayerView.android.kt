package dev.stade.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun VideoPlayerView(bytes: ByteArray, modifier: Modifier) {
    val context = LocalContext.current
    val file = remember(bytes) {
        val dir = File(context.cacheDir, "videos_inline").apply { mkdirs() }
        val f = File(dir, "inline_${bytes.contentHashCode()}.mp4")
        if (!f.exists()) {
            FileOutputStream(f).use { it.write(bytes) }
        }
        f
    }
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        }
    )
}
