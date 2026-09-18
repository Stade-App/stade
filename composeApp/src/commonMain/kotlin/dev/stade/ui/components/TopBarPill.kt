package dev.stade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.stade.ui.rememberGearHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val TOP_PILL_SIZE = 40.dp
val TOP_PILL_GAP = 8.dp

private const val GEAR_SPIN_MS = 400
private const val GEAR_SPIN_HANDOFF_MS = GEAR_SPIN_MS.toLong()

private const val SPARKLE_MS = 460
private const val SPARKLE_HANDOFF_MS = 300L
internal val SPARKLE_GOLD = Color(0xFFFFC53D)

@Composable
fun TopBarPill(
    icon: ImageVector,
    contentDescription: String,
    spinOnClick: Boolean = false,
    sparkleOnClick: Boolean = false,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val haptic = rememberGearHaptic()
    val baseTint = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.size(TOP_PILL_SIZE),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (sparkleOnClick && burst.value > 0f && burst.value < 1f) {
                SparkleBurst(progress = burst.value)
            }
            IconButton(
                onClick = {
                    when {
                        spinOnClick -> {
                            scope.launch {
                                spin.animateTo(
                                    targetValue = spin.value + 360f,
                                    animationSpec = tween(GEAR_SPIN_MS, easing = FastOutSlowInEasing)
                                )
                            }
                            scope.launch { haptic.play() }
                            scope.launch {
                                delay(GEAR_SPIN_HANDOFF_MS)
                                onClick()
                            }
                        }
                        sparkleOnClick -> {
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, tween(SPARKLE_MS, easing = LinearEasing))
                                burst.snapTo(0f)
                            }
                            scope.launch { haptic.play() }
                            scope.launch {
                                delay(SPARKLE_HANDOFF_MS)
                                onClick()
                            }
                        }
                        else -> onClick()
                    }
                },
                modifier = Modifier.size(TOP_PILL_SIZE)
            ) {
                val pop = sin(burst.value * PI.toFloat())
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = if (sparkleOnClick) lerp(baseTint, SPARKLE_GOLD, pop) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = spin.value
                            val s = 1f + pop * 0.5f
                            scaleX = s
                            scaleY = s
                        }
                )
            }
        }
    }
}

@Composable
private fun SparkleBurst(progress: Float) {
    Canvas(Modifier.size(TOP_PILL_SIZE)) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = SPARKLE_GOLD.copy(alpha = (1f - progress) * 0.32f),
            radius = radius * (0.45f + progress * 0.85f)
        )
        val points = 6
        for (i in 0 until points) {
            val angle = (i / points.toFloat()) * 2f * PI.toFloat() + progress * 1.2f
            val distance = radius * (0.35f + progress * 0.8f)
            val dotRadius = (1f - progress) * 2.4.dp.toPx()
            if (dotRadius > 0f) {
                drawCircle(
                    color = SPARKLE_GOLD.copy(alpha = (1f - progress) * 0.9f),
                    radius = dotRadius,
                    center = center + Offset(cos(angle) * distance, sin(angle) * distance)
                )
            }
        }
    }
}
