package dev.stade.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

private const val BOTTOM_ANCHOR = 100_000

suspend fun LazyListState.jumpToChatBottom(lastIndex: Int) {
    if (lastIndex < 0) return
    scrollToItem(lastIndex, BOTTOM_ANCHOR)
    withFrameNanos { }
    scrollToItem(lastIndex, BOTTOM_ANCHOR)
}

suspend fun LazyListState.animateToChatBottom(lastIndex: Int) {
    if (lastIndex < 0) return
    animateScrollToItem(lastIndex, BOTTOM_ANCHOR)
}
