package dev.stade.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

private val FALLBACK_PANEL_HEIGHT = 280.dp
private val MIN_REAL_KEYBOARD = 140.dp

@Stable
class PanelHeightState internal constructor() {
    var height: Dp by mutableStateOf(FALLBACK_PANEL_HEIGHT)
        internal set
}

@Composable
fun rememberPanelHeightState(): PanelHeightState {
    val state = remember { PanelHeightState() }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    LaunchedEffect(state, density, imeInsets, navInsets) {
        snapshotFlow { keyboardHeightPx(imeInsets, navInsets, density) }
            .distinctUntilChanged()
            .collect { px ->
                val dp = with(density) { px.toDp() }
                if (dp >= MIN_REAL_KEYBOARD) state.height = dp
            }
    }
    return state
}

private fun keyboardHeightPx(
    ime: WindowInsets,
    navigationBars: WindowInsets,
    density: Density
): Int = (ime.getBottom(density) - navigationBars.getBottom(density)).coerceAtLeast(0)

@Composable
fun BottomInsetPanel(
    visible: Boolean,
    state: PanelHeightState,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val panelPx = if (visible) with(density) { state.height.roundToPx() } else 0
    val panelInsets = remember(panelPx) { WindowInsets(bottom = panelPx) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.ime.union(panelInsets))
    ) {
        if (visible) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                content()
            }
        }
    }
}
