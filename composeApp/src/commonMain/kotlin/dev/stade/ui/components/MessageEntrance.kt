package dev.stade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val ENTRANCE_MS = 280
private val EntranceEasing = CubicBezierEasing(0.18f, 0.9f, 0.22f, 1f)

@Stable
class MessageEntrance {
    private var primed = false
    private val seen = mutableSetOf<String>()

    val isPrimed: Boolean get() = primed

    fun prime(ids: List<String>) {
        if (primed) return
        seen.addAll(ids)
        primed = true
    }

    fun isNew(id: String): Boolean = primed && seen.add(id)
}

@Composable
fun rememberMessageEntrance(key: Any?): MessageEntrance = remember(key) { MessageEntrance() }

@Composable
fun messageEntranceModifier(isNew: Boolean, outgoing: Boolean): Modifier {
    val progress = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (isNew) progress.animateTo(1f, tween(ENTRANCE_MS, easing = EntranceEasing))
    }
    return Modifier.graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = 0.9f + 0.1f * p
        scaleX = scale
        scaleY = scale
        translationX = (1f - p) * (if (outgoing) 18.dp.toPx() else -18.dp.toPx())
        transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 0.5f)
    }
}
