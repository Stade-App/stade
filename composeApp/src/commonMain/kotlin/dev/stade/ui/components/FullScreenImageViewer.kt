package dev.stade.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private const val VIEWER_ENTER_MS = 220
private const val VIEWER_EXIT_MS = 160
private const val VIEWER_MIN_SCALE = 1f
private const val VIEWER_MAX_SCALE = 5f
private const val VIEWER_DOUBLE_TAP_SCALE = 2.5f
private const val VIEWER_ENTER_SCALE = 0.9f
private const val VIEWER_SCRIM_ALPHA = 0.94f
private const val VIEWER_ZOOM_EPSILON = 0.01f

@Composable
fun FullScreenImageViewer(
    bitmap: ImageBitmap,
    contentDescription: String?,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val strings = LocalStrings.current
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val appear = animateFloatAsState(
        targetValue = if (shown && !closing) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (closing) VIEWER_EXIT_MS else VIEWER_ENTER_MS,
            easing = FastOutSlowInEasing
        ),
        label = "viewerAppear"
    )

    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(VIEWER_EXIT_MS.toLong())
            onDismiss()
        }
    }

    val dismiss: () -> Unit = { if (!closing) closing = true }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(VIEWER_MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun panBounds(target: Float): Offset {
        if (containerSize.width == 0 || containerSize.height == 0) return Offset.Zero
        if (bitmap.width == 0 || bitmap.height == 0) return Offset.Zero
        val fit = min(
            containerSize.width.toFloat() / bitmap.width,
            containerSize.height.toFloat() / bitmap.height
        )
        val drawnWidth = bitmap.width * fit * target
        val drawnHeight = bitmap.height * fit * target
        return Offset(
            x = max(0f, (drawnWidth - containerSize.width) / 2f),
            y = max(0f, (drawnHeight - containerSize.height) / 2f)
        )
    }

    fun zoomAround(anchor: Offset, target: Float, pan: Offset) {
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
        val local = anchor - center
        val raw = local - (local - offset) / scale * target + pan
        val bounds = panBounds(target)
        scale = target
        offset = Offset(
            x = raw.x.coerceIn(-bounds.x, bounds.x),
            y = raw.y.coerceIn(-bounds.y, bounds.y)
        )
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .drawBehind { drawRect(Color.Black, alpha = VIEWER_SCRIM_ALPHA * appear.value) }
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val entering = VIEWER_ENTER_SCALE + (1f - VIEWER_ENTER_SCALE) * appear.value
                        scaleX = scale * entering
                        scaleY = scale * entering
                        translationX = offset.x
                        translationY = offset.y
                        alpha = appear.value
                    }
                    .pointerInput(bitmap) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val target = (scale * zoom).coerceIn(VIEWER_MIN_SCALE, VIEWER_MAX_SCALE)
                            zoomAround(centroid, target, pan)
                        }
                    }
                    .pointerInput(bitmap) {
                        detectTapGestures(
                            onTap = {
                                if (scale <= VIEWER_MIN_SCALE + VIEWER_ZOOM_EPSILON) dismiss()
                            },
                            onDoubleTap = { anchor ->
                                if (scale > VIEWER_MIN_SCALE + VIEWER_ZOOM_EPSILON) {
                                    scale = VIEWER_MIN_SCALE
                                    offset = Offset.Zero
                                } else {
                                    zoomAround(anchor, VIEWER_DOUBLE_TAP_SCALE, Offset.Zero)
                                }
                            }
                        )
                    }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .graphicsLayer { alpha = appear.value },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                actions()
                IconButton(onClick = dismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.closePhoto, tint = Color.White)
                }
            }
        }
    }
}
