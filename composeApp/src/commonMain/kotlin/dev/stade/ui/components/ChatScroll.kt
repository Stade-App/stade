package dev.stade.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
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

suspend fun LazyListState.centerOnItem(index: Int) {
    if (index < 0) return
    runCatching {
        scrollToItem(index)
        withFrameNanos { }
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            ?: return@runCatching
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val centered = ((viewport - item.size) / 2f).coerceAtLeast(0f)
        if (centered > 0f) animateScrollBy(-centered)
    }
}
