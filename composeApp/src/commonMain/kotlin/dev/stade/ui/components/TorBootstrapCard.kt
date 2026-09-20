package dev.stade.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import dev.stade.ui.i18n.LocalStrings
import kotlin.math.abs

private const val HOPS = 4
private const val PULSE_MS = 1600
private const val SWEEP_MS = 760
private const val HOLD_MS = 1000L
private const val FADE_MS = 420

internal val TOR_READY_GREEN = Color(0xFF29A36A)

@Composable
fun TorBootstrapCard(
    percent: Int,
    phase: String,
    complete: Boolean = false,
    onDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val sweep = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(complete) {
        if (!complete) return@LaunchedEffect
        sweep.animateTo(1f, tween(SWEEP_MS, easing = FastOutSlowInEasing))
        delay(HOLD_MS)
        fade.animateTo(0f, tween(FADE_MS))
        onDismissed()
    }
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)

    val progress by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100) / 100f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "torProgress"
    )
    val transition = rememberInfiniteTransition(label = "torCircuit")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "torPulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { alpha = fade.value },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val nodeRadius = 5.dp.toPx()
                val y = size.height / 2f
                val first = nodeRadius
                val last = size.width - nodeRadius
                val span = last - first
                val step = span / (HOPS - 1)

                drawLine(
                    color = idle,
                    start = Offset(first, y),
                    end = Offset(last, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (progress > 0f) {
                    drawLine(
                        color = accent,
                        start = Offset(first, y),
                        end = Offset(first + span * progress, y),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // a packet running the built part of the circuit
                val litEnd = first + span * progress
                if (litEnd > first && sweep.value <= 0f) {
                    val travelled = first + (litEnd - first) * pulse
                    val glow = 1f - abs(pulse - 0.5f) * 2f
                    drawCircle(
                        color = accent.copy(alpha = 0.30f * glow),
                        radius = nodeRadius * 2.1f,
                        center = Offset(travelled, y)
                    )
                    drawCircle(
                        color = accent.copy(alpha = glow),
                        radius = nodeRadius * 0.65f,
                        center = Offset(travelled, y)
                    )
                }

                if (sweep.value > 0f) {
                    drawLine(
                        color = TOR_READY_GREEN,
                        start = Offset(first, y),
                        end = Offset(first + span * sweep.value, y),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                for (i in 0 until HOPS) {
                    val x = first + step * i
                    val reached = progress >= (i.toFloat() / (HOPS - 1)) - 0.001f
                    val swept = sweep.value >= (i.toFloat() / (HOPS - 1)) - 0.001f && sweep.value > 0f
                    val nodeColor = when {
                        swept -> TOR_READY_GREEN
                        reached -> accent
                        else -> idle
                    }
                    drawCircle(
                        color = if (reached || swept) nodeColor else Color.Transparent,
                        radius = nodeRadius,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = nodeColor,
                        radius = nodeRadius,
                        center = Offset(x, y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (complete) strings.torSetupComplete else strings.torConnectingTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (complete) TOR_READY_GREEN else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (!complete) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${percent.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (phase.isNotBlank() && !complete) {
                Spacer(Modifier.height(2.dp))
                Text(
                    phase,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
