package dev.stade.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

const val HIGHLIGHT_FLASH_MS = 1500L

suspend fun LazyListState.jumpToChatBottom(lastIndex: Int) {
    if (lastIndex < 0) return
    scrollToItem(0)
}

suspend fun LazyListState.animateToChatBottom(lastIndex: Int) {
    if (lastIndex < 0) return
    animateScrollToItem(0)
}

suspend fun LazyListState.centerOnChatMessage(
    chronologicalIndex: Int,
    messageCount: Int,
    leadingItems: Int = 0
) {
    if (chronologicalIndex < 0 || chronologicalIndex >= messageCount) return
    val target = leadingItems + (messageCount - 1 - chronologicalIndex)
    runCatching {
        scrollToItem(target)
        withFrameNanos { }
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == target }
            ?: return@runCatching
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val centered = ((viewport - item.size) / 2f).coerceAtLeast(0f)
        val delta = item.offset - centered
        if (delta != 0f) animateScrollBy(delta)
    }
}
