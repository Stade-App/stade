package dev.stade.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stade.ui.i18n.LocalStrings
import kotlin.math.abs
import kotlin.math.sin

private val PAD_ACCENT = Color(0xFFFF5C8A)
private val MEME_ACCENT = Color(0xFF7C5CFF)

@Composable
fun PadSoundBubble(
    label: String,
    durationMs: Long,
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    delivered: Boolean? = null
) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        color = PAD_ACCENT.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PAD_ACCENT),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = strings.padTapToPlay,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PadTag(strings.padSoundTag, PAD_ACCENT)
                    if (durationMs > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatPadDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (delivered != null) {
                        Spacer(Modifier.width(6.dp))
                        DeliveryStatusDots(
                            delivered = delivered,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    label.ifBlank { strings.padSoundTag },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            PadPulse(active = playing, tint = PAD_ACCENT)
        }
    }
}

@Composable
fun MemeClipBubble(
    label: String,
    durationMs: Long,
    modifier: Modifier = Modifier,
    delivered: Boolean? = null,
    content: @Composable () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MEME_ACCENT.copy(alpha = 0.14f)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                content()
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PadTag(strings.padMemeTag, MEME_ACCENT)
                Spacer(Modifier.width(8.dp))
                Text(
                    label.ifBlank { strings.padMemeTag },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (durationMs > 0) {
                    Text(
                        formatPadDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (delivered != null) {
                    Spacer(Modifier.width(6.dp))
                    DeliveryStatusDots(
                        delivered = delivered,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PadTag(text: String, accent: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = accent) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PadPulse(active: Boolean, tint: Color) {
    if (!active) {
        PadBars(tint) { 0f }
        return
    }
    val transition = rememberInfiniteTransition(label = "padPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "padPulsePhase"
    )
    PadBars(tint) { phase }
}

@Composable
private fun PadBars(tint: Color, phase: () -> Float) {
    Canvas(modifier = Modifier.size(width = 22.dp, height = 22.dp)) {
        val current = phase()
        val bars = 4
        val gap = size.width / (bars * 2f - 1f)
        val barWidth = gap
        for (i in 0 until bars) {
            val amplitude = if (current == 0f) {
                0.35f
            } else {
                0.35f + 0.65f * abs(sin(current + i * 0.8f))
            }
            val barHeight = size.height * amplitude
            val x = i * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = tint,
                start = Offset(x, size.height / 2f - barHeight / 2f),
                end = Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
