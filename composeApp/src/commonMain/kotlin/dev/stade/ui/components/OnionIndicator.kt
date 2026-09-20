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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Onion silhouette that reports whether Tor is carrying traffic. Filled and gently breathing
 * when the circuit is up, a dim outline when it is not.
 */
@Composable
fun OnionIndicator(
    active: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val tint by animateColorAsState(
        targetValue = if (active) TOR_READY_GREEN else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(420),
        label = "onionTint"
    )
    val breathe = rememberInfiniteTransition(label = "onionBreathe")
    val glow by breathe.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
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

        // stem
        drawLine(
            color = tint,
            start = Offset(cx, stemTop),
            end = Offset(cx, bulbTop + h * 0.04f),
            strokeWidth = w * 0.07f
        )

        val body = onionPath(cx, bulbTop, bottom, w * 0.44f)
        if (active) {
            drawPath(body, color = tint.copy(alpha = 0.22f + 0.18f * glow))
        }
        drawPath(body, color = tint, style = Stroke(width = w * 0.09f))

        // inner layers
        drawPath(
            onionPath(cx, bulbTop + h * 0.16f, bottom - h * 0.1f, w * 0.26f),
            color = tint.copy(alpha = 0.8f),
            style = Stroke(width = w * 0.07f)
        )
        drawPath(
            onionPath(cx, bulbTop + h * 0.30f, bottom - h * 0.22f, w * 0.12f),
            color = tint.copy(alpha = 0.65f),
            style = Stroke(width = w * 0.06f)
        )
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
