package dev.stade.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayerView(bytes: ByteArray, modifier: Modifier)
