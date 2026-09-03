package dev.stade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.stade.ui.rememberGearHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val TOP_PILL_SIZE = 40.dp
val TOP_PILL_GAP = 8.dp

private const val GEAR_SPIN_MS = 400
private const val GEAR_SPIN_HANDOFF_MS = GEAR_SPIN_MS.toLong()

@Composable
fun TopBarPill(
    icon: ImageVector,
    contentDescription: String,
    spinOnClick: Boolean = false,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val haptic = rememberGearHaptic()

    Surface(
        modifier = Modifier.size(TOP_PILL_SIZE),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp
    ) {
        IconButton(
            onClick = {
                if (!spinOnClick) {
                    onClick()
                } else {
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
            },
            modifier = Modifier.size(TOP_PILL_SIZE)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = spin.value }
            )
        }
    }
}
