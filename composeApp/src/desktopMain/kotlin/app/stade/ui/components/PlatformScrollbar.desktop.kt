package app.stade.ui.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(state),
        style = LocalScrollbarStyle.current.copy(thickness = 14.dp)
    )
}
