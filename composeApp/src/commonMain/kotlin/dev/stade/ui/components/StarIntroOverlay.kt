package dev.stade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.stade.db.StadeDb
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STAR_INTRO_KEY = "intro.star.v0.2.7"

fun starIntroPending(db: StadeDb): Boolean =
    runCatching { db.stadeDbQueries.getKv(STAR_INTRO_KEY).executeAsOneOrNull() }.getOrNull() == null

fun markStarIntroSeen(db: StadeDb) {
    runCatching { db.stadeDbQueries.putKv(STAR_INTRO_KEY, "1".encodeToByteArray()) }
}

private const val WALK_IN_MS = 1000
private const val HANDOFF_MS = 420
private const val WALK_OUT_MS = 900

@Composable
fun StarIntroOverlay(
    targetCenter: Offset?,
    onStarPlaced: () -> Unit,
    onFinished: () -> Unit
) {
    if (targetCenter == null) return
    val density = LocalDensity.current
    val robotSize = 34.dp
    val robotPx = with(density) { robotSize.toPx() }
    val startX = with(density) { (-70).dp.toPx() }
    val exitX = targetCenter.x + with(density) { 220.dp.toPx() }
    val handoffX = targetCenter.x - with(density) { 46.dp.toPx() }

    val walk = remember { Animatable(startX) }
    val carry = remember { Animatable(0f) }
    val bobPhase = remember { Animatable(0f) }
    var carrying by remember { mutableStateOf(true) }

    LaunchedEffect(targetCenter) {
        bobPhase.snapTo(0f)
        bobPhase.animateTo(
            targetValue = 12f,
            animationSpec = tween(WALK_IN_MS + HANDOFF_MS + WALK_OUT_MS, easing = LinearEasing)
        )
    }

    LaunchedEffect(targetCenter) {
        walk.snapTo(startX)
        carry.snapTo(0f)
        carrying = true
        walk.animateTo(handoffX, tween(WALK_IN_MS, easing = FastOutSlowInEasing))
        carry.animateTo(1f, tween(HANDOFF_MS, easing = FastOutSlowInEasing))
        carrying = false
        onStarPlaced()
        walk.animateTo(exitX, tween(WALK_OUT_MS, easing = FastOutSlowInEasing))
        onFinished()
    }

    val bobOffset = sin(bobPhase.value * 2f * PI.toFloat()) * with(density) { 2.5.dp.toPx() }
    val robotY = targetCenter.y - robotPx / 2f + bobOffset

    Box(Modifier.fillMaxSize()) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset { IntOffset(walk.value.roundToInt(), robotY.roundToInt()) }
                .size(robotSize)
                .graphicsLayer {
                    rotationZ = sin(bobPhase.value * 2f * PI.toFloat()) * 5f
                }
        )
        if (carrying || carry.value > 0f) {
            val p = carry.value
            val starHalf = with(density) { 10.dp.toPx() }
            val handX = walk.value + robotPx * 0.9f
            val destX = targetCenter.x - starHalf
            val destY = targetCenter.y - starHalf
            val starX = handX + (destX - handX) * p
            val starY = robotY + (destY - robotY) * p
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = SPARKLE_GOLD,
                modifier = Modifier
                    .offset { IntOffset(starX.roundToInt(), starY.roundToInt()) }
                    .size(20.dp)
                    .graphicsLayer {
                        val s = 0.7f + 0.5f * sin(p * PI.toFloat())
                        scaleX = s
                        scaleY = s
                        rotationZ = p * 240f
                    }
            )
        }
    }
}
