package dev.stade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
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
import dev.stade.ui.loadPanelHeightDp
import dev.stade.ui.savePanelHeightDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private val FALLBACK_PANEL_HEIGHT = 280.dp
private val MIN_REAL_KEYBOARD = 140.dp
private const val KEYBOARD_SETTLE_MS = 160L
private const val PANEL_SLIDE_MS = 210
private const val PANEL_REVEAL_MS = 170
private const val PANEL_HIDE_MS = 110
private val PANEL_REVEAL_LIFT = 18.dp
private val PanelSlideEasing = FastOutSlowInEasing

private val sharedPanelHeight = mutableStateOf(
    loadPanelHeightDp().takeIf { it >= MIN_REAL_KEYBOARD.value.toInt() }?.dp ?: FALLBACK_PANEL_HEIGHT
)

@Stable
class PanelHeightState internal constructor() {
    val height: Dp get() = sharedPanelHeight.value
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
            .collectLatest { px ->
                val dp = with(density) { px.toDp() }
                if (dp < MIN_REAL_KEYBOARD) return@collectLatest
                delay(KEYBOARD_SETTLE_MS)
                if (dp != sharedPanelHeight.value) {
                    sharedPanelHeight.value = dp
                    savePanelHeightDp(dp.value.toInt())
                }
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
    val targetPx = with(density) { state.height.roundToPx() }.toFloat()
    val keyboardPx = WindowInsets.ime.getBottom(density)

    val panelHeight = remember { Animatable(if (visible) targetPx else 0f) }
    val reveal = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(visible, targetPx) {
        val goal = if (visible) targetPx else 0f
        if (keyboardPx > 0) {
            panelHeight.snapTo(goal)
        } else {
            panelHeight.animateTo(goal, tween(PANEL_SLIDE_MS, easing = PanelSlideEasing))
        }
    }

    LaunchedEffect(visible) {
        reveal.animateTo(
            if (visible) 1f else 0f,
            tween(if (visible) PANEL_REVEAL_MS else PANEL_HIDE_MS, easing = LinearOutSlowInEasing)
        )
    }

    val panelPx = panelHeight.value.roundToInt()
    val panelInsets = remember(panelPx) { WindowInsets(bottom = panelPx) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.ime.union(panelInsets))
    ) {
        if (panelPx > 0 || reveal.value > 0.01f) {
            val shown = reveal.value
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = shown
                        translationY = (1f - shown) * PANEL_REVEAL_LIFT.toPx()
                    },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                content()
            }
        }
    }
}
