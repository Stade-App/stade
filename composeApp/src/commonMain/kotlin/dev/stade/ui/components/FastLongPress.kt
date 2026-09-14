package dev.stade.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

private const val LONG_PRESS_MS = 300L

@Composable
fun fastLongPressConfiguration(): ViewConfiguration {
    val base = LocalViewConfiguration.current
    return remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis: Long get() = LONG_PRESS_MS
        }
    }
}
