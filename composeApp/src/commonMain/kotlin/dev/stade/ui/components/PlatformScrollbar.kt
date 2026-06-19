package dev.stade.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
)

