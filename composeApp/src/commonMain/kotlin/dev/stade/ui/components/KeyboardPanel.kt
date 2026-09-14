package dev.stade.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FALLBACK_PANEL_HEIGHT = 300.dp
private val MIN_REAL_KEYBOARD = 140.dp

@Composable
fun rememberPanelHeight(): Dp {
    val density = LocalDensity.current
    val keyboard = with(density) {
        val ime = WindowInsets.ime.getBottom(this)
        val nav = WindowInsets.navigationBars.getBottom(this)
        (ime - nav).coerceAtLeast(0).toDp()
    }
    var remembered by remember { mutableStateOf(FALLBACK_PANEL_HEIGHT) }
    LaunchedEffect(keyboard) {
        if (keyboard >= MIN_REAL_KEYBOARD) remembered = keyboard
    }
    return remembered
}

@Composable
fun InlineKeyboardPanel(
    visible: Boolean,
    height: Dp,
    content: @Composable () -> Unit
) {
    if (!visible) return
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Box(Modifier.fillMaxWidth().height(height)) { content() }
    }
}
