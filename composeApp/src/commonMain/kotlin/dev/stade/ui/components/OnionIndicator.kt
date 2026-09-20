package dev.stade.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stade.transport.TransportInfo
import dev.stade.ui.i18n.LocalStrings

enum class OnionState { Offline, Connecting, Connected, Failed }

private val ONION_AMBER = Color(0xFFE0A22C)
private val ONION_RED = Color(0xFFD6453D)
private val ONION_GREY = Color(0xFF98A2B3)

private const val PULSE_FAST_MS = 900
private const val PULSE_SLOW_MS = 2200

fun onionStateOf(networkOnline: Boolean, info: TransportInfo?): OnionState = when {
    !networkOnline -> OnionState.Offline
    info == null -> OnionState.Connecting
    info.running && info.available -> OnionState.Connected
    info.failed -> OnionState.Failed
    else -> OnionState.Connecting
}

@Composable
fun onionStateLabel(state: OnionState): String {
    val strings = LocalStrings.current
    return when (state) {
        OnionState.Offline -> strings.torStatusOffline
        OnionState.Connecting -> strings.torStatusConnecting
        OnionState.Connected -> strings.torStatusConnected
        OnionState.Failed -> strings.torStatusFailed
    }
}

@Composable
fun OnionIndicator(
    state: OnionState,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val tint by animateColorAsState(
        targetValue = when (state) {
            OnionState.Connected -> TOR_READY_GREEN
            OnionState.Connecting -> ONION_AMBER
            OnionState.Failed -> ONION_RED
            OnionState.Offline -> ONION_GREY
        },
        animationSpec = tween(420),
        label = "onionTint"
    )
    val pulsing = state == OnionState.Connecting
    val breathe = rememberInfiniteTransition(label = "onionBreathe")
    val glow by breathe.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (pulsing) PULSE_FAST_MS else PULSE_SLOW_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "onionGlow"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val stemTop = h * 0.06f
        val bulbTop = h * 0.24f
        val bottom = h * 0.96f

        val bodyAlpha = when {
            pulsing -> 0.55f + 0.45f * glow
            state == OnionState.Offline -> 0.5f
            else -> 1f
        }
        val stroke = tint.copy(alpha = bodyAlpha)

        drawLine(
            color = stroke,
            start = Offset(cx, stemTop),
            end = Offset(cx, bulbTop + h * 0.04f),
            strokeWidth = w * 0.07f
        )

        val body = onionPath(cx, bulbTop, bottom, w * 0.44f)
        if (state == OnionState.Connected) {
            drawPath(body, color = tint.copy(alpha = 0.22f + 0.18f * glow))
        }
        drawPath(body, color = stroke, style = Stroke(width = w * 0.09f))

        drawPath(
            onionPath(cx, bulbTop + h * 0.16f, bottom - h * 0.1f, w * 0.26f),
            color = stroke.copy(alpha = bodyAlpha * 0.8f),
            style = Stroke(width = w * 0.07f)
        )
        drawPath(
            onionPath(cx, bulbTop + h * 0.30f, bottom - h * 0.22f, w * 0.12f),
            color = stroke.copy(alpha = bodyAlpha * 0.65f),
            style = Stroke(width = w * 0.06f)
        )

        if (state == OnionState.Offline) {
            drawLine(
                color = tint,
                start = Offset(w * 0.14f, h * 0.10f),
                end = Offset(w * 0.86f, h * 0.90f),
                strokeWidth = w * 0.12f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.onionPath(cx: Float, top: Float, bottom: Float, halfWidth: Float): Path {
    val shoulder = top + (bottom - top) * 0.28f
    return Path().apply {
        moveTo(cx, top)
        cubicTo(
            cx - halfWidth * 1.35f, shoulder,
            cx - halfWidth * 1.12f, bottom,
            cx, bottom
        )
        cubicTo(
            cx + halfWidth * 1.12f, bottom,
            cx + halfWidth * 1.35f, shoulder,
            cx, top
        )
        close()
    }
}
