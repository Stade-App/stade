package dev.stade.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private const val WAVE_CYCLE_MS = 1100
private const val WAVE_STAGGER = 0.15f
private const val WAVE_ACTIVE_FRACTION = 0.45f
private val DOT_SIZE = 7.dp
private val DOT_SPACING = 4.dp
private val DOT_LIFT = 5.dp

private fun waveLift(phase: Float, index: Int): Float {
    val shifted = phase - index * WAVE_STAGGER
    val t = ((shifted % 1f) + 1f) % 1f
    if (t >= WAVE_ACTIVE_FRACTION) return 0f
    return sin((t / WAVE_ACTIVE_FRACTION) * PI).toFloat()
}

@Composable
fun TypingDots(
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = DOT_SIZE
) {
    val transition = rememberInfiniteTransition(label = "typingWave")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = WAVE_CYCLE_MS, easing = LinearEasing)
        ),
        label = "typingPhase"
    )
    val liftPx = with(LocalDensity.current) { DOT_LIFT.toPx() }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        val lift = waveLift(phase.value, index)
                        translationY = -liftPx * lift
                        alpha = 0.45f + 0.55f * lift
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun TypingBubble(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 18.dp
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
