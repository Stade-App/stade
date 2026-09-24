package dev.stade.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

fun reactionBarOffsetY(
    anchorTop: Float,
    anchorBottom: Float,
    barHeight: Int,
    gap: Int,
    areaHeight: Int
): Int {
    if (barHeight <= 0) return 0
    val above = anchorTop - barHeight - gap
    val placed = if (above >= 0f) above else anchorBottom + gap
    val highest = (areaHeight - barHeight).coerceAtLeast(0)
    return placed.roundToInt().coerceIn(0, highest)
}

@Composable
fun QuickReactionBar(
    activeEmoji: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    Surface(
        modifier = modifier.graphicsLayer {
            val scale = 0.7f + 0.3f * appear.value
            scaleX = scale
            scaleY = scale
            alpha = appear.value
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                val active = emoji == activeEmoji
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (active) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onPick(emoji) }
                        .semantics { contentDescription = emoji },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}
