package dev.stade.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

private const val GEAR_TICKS = 9
private const val GEAR_FIRST_GAP_MS = 26L
private const val GEAR_GAP_STEP_MS = 6L

private class AndroidGearHaptic(private val view: View) : GearHaptic {
    override suspend fun play() {
        var gap = GEAR_FIRST_GAP_MS
        repeat(GEAR_TICKS) { index ->
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            if (index < GEAR_TICKS - 1) {
                delay(gap)
                gap += GEAR_GAP_STEP_MS
            }
        }
    }
}

@Composable
actual fun rememberGearHaptic(): GearHaptic {
    val view = LocalView.current
    return remember(view) { AndroidGearHaptic(view) }
}
