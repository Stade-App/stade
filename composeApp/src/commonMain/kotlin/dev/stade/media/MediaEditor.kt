package dev.stade.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditStroke(val points: List<Offset>, val color: Color, val widthFraction: Float)

data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isFull: Boolean get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

expect fun applyMediaEdits(original: ByteArray, crop: CropRect?, strokes: List<EditStroke>): ByteArray

private enum class EditorMode { CROP, DRAW }

private val swatchColors = listOf(Color.Red, Color(0xFFFFC107), Color(0xFF2196F3), Color(0xFF4CAF50), Color.White, Color.Black)
private const val MIN_CROP_SIZE = 0.1f

@Composable
fun MediaEditorDialog(
    imageBytes: ByteArray,
    onSave: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(imageBytes) { imageBytes.decodeToImageBitmap() }

    if (bitmap == null) {
        onCancel()
        return
    }

    var mode by remember { mutableStateOf(EditorMode.CROP) }
    var crop by remember { mutableStateOf(CropRect(0f, 0f, 1f, 1f)) }
    var strokes by remember { mutableStateOf(listOf<EditStroke>()) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var saving by remember { mutableStateOf(false) }
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }

    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!saving) onCancel() }) {
                    Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = Color.White)
                }
                Row {
                    IconButton(onClick = { mode = EditorMode.CROP }) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = strings.cropToolAction,
                            tint = if (mode == EditorMode.CROP) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { mode = EditorMode.DRAW }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = strings.drawToolAction,
                            tint = if (mode == EditorMode.DRAW) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
                IconButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    applyMediaEdits(
                                        original = imageBytes,
                                        crop = if (crop.isFull) null else crop,
                                        strokes = strokes
                                    )
                                }.getOrDefault(imageBytes)
                            }
                            onSave(result)
                        }
                    }
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = strings.saveEditsAction, tint = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .aspectRatio(aspect)
                        .onSizeChanged { boxSizePx = it }
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(mode) {
                                if (mode == EditorMode.DRAW) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val w = size.width.toFloat().coerceAtLeast(1f)
                                            val h = size.height.toFloat().coerceAtLeast(1f)
                                            val frac = Offset(offset.x / w, offset.y / h)
                                            strokes = strokes + EditStroke(listOf(frac), currentColor, 0.012f)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val w = size.width.toFloat().coerceAtLeast(1f)
                                            val h = size.height.toFloat().coerceAtLeast(1f)
                                            val frac = Offset(change.position.x / w, change.position.y / h)
                                            val last = strokes.lastOrNull()
                                            if (last != null) {
                                                strokes = strokes.dropLast(1) + last.copy(points = last.points + frac)
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height

                        strokes.forEach { stroke ->
                            if (stroke.points.size >= 2) {
                                for (i in 0 until stroke.points.size - 1) {
                                    drawLine(
                                        color = stroke.color,
                                        start = Offset(stroke.points[i].x * w, stroke.points[i].y * h),
                                        end = Offset(stroke.points[i + 1].x * w, stroke.points[i + 1].y * h),
                                        strokeWidth = stroke.widthFraction * w,
                                        cap = StrokeCap.Round
                                    )
                                }
                            } else if (stroke.points.size == 1) {
                                drawCircle(
                                    color = stroke.color,
                                    radius = stroke.widthFraction * w / 2f,
                                    center = Offset(stroke.points[0].x * w, stroke.points[0].y * h)
                                )
                            }
                        }

                        if (mode == EditorMode.CROP) {
                            val scrim = Color.Black.copy(alpha = 0.55f)
                            val l = crop.left * w
                            val t = crop.top * h
                            val r = crop.right * w
                            val b = crop.bottom * h
                            drawRect(color = scrim, topLeft = Offset(0f, 0f), size = Size(w, t))
                            drawRect(color = scrim, topLeft = Offset(0f, b), size = Size(w, h - b))
                            drawRect(color = scrim, topLeft = Offset(0f, t), size = Size(l, b - t))
                            drawRect(color = scrim, topLeft = Offset(r, t), size = Size(w - r, b - t))
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(l, t),
                                size = Size(r - l, b - t),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    if (mode == EditorMode.CROP && boxSizePx.width > 0 && boxSizePx.height > 0) {
                        val bw = boxSizePx.width.toFloat()
                        val bh = boxSizePx.height.toFloat()

                        // Whole-rect move handle (drawn first so corner handles stay on top for touch)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((crop.left * bw).toInt(), (crop.top * bh).toInt()) }
                                .size(
                                    width = with(androidx.compose.ui.platform.LocalDensity.current) { ((crop.right - crop.left) * bw).toDp() },
                                    height = with(androidx.compose.ui.platform.LocalDensity.current) { ((crop.bottom - crop.top) * bh).toDp() }
                                )
                                .pointerInput(boxSizePx) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dxFrac = dragAmount.x / bw
                                        val dyFrac = dragAmount.y / bh
                                        val width = crop.right - crop.left
                                        val height = crop.bottom - crop.top
                                        val newLeft = (crop.left + dxFrac).coerceIn(0f, 1f - width)
                                        val newTop = (crop.top + dyFrac).coerceIn(0f, 1f - height)
                                        crop = CropRect(newLeft, newTop, newLeft + width, newTop + height)
                                    }
                                }
                        )

                        val handleSize = 24.dp
                        CropHandle(handleSize, boxSizePx, crop.left, crop.top) { dxFrac, dyFrac ->
                            crop = crop.copy(
                                left = (crop.left + dxFrac).coerceIn(0f, crop.right - MIN_CROP_SIZE),
                                top = (crop.top + dyFrac).coerceIn(0f, crop.bottom - MIN_CROP_SIZE)
                            )
                        }
                        CropHandle(handleSize, boxSizePx, crop.right, crop.top) { dxFrac, dyFrac ->
                            crop = crop.copy(
                                right = (crop.right + dxFrac).coerceIn(crop.left + MIN_CROP_SIZE, 1f),
                                top = (crop.top + dyFrac).coerceIn(0f, crop.bottom - MIN_CROP_SIZE)
                            )
                        }
                        CropHandle(handleSize, boxSizePx, crop.left, crop.bottom) { dxFrac, dyFrac ->
                            crop = crop.copy(
                                left = (crop.left + dxFrac).coerceIn(0f, crop.right - MIN_CROP_SIZE),
                                bottom = (crop.bottom + dyFrac).coerceIn(crop.top + MIN_CROP_SIZE, 1f)
                            )
                        }
                        CropHandle(handleSize, boxSizePx, crop.right, crop.bottom) { dxFrac, dyFrac ->
                            crop = crop.copy(
                                right = (crop.right + dxFrac).coerceIn(crop.left + MIN_CROP_SIZE, 1f),
                                bottom = (crop.bottom + dyFrac).coerceIn(crop.top + MIN_CROP_SIZE, 1f)
                            )
                        }
                    }
                }
            }

            if (mode == EditorMode.DRAW) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    swatchColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(if (c == currentColor) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { currentColor = c }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) }) {
                        Icon(Icons.Default.Undo, contentDescription = strings.undoAction, tint = Color.White)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { crop = CropRect(0f, 0f, 1f, 1f) }) {
                        Text(strings.resetCropAction, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CropHandle(
    handleSize: androidx.compose.ui.unit.Dp,
    boxSizePx: IntSize,
    xFrac: Float,
    yFrac: Float,
    onDrag: (dxFrac: Float, dyFrac: Float) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleSizePx = with(density) { handleSize.toPx() }
    val bw = boxSizePx.width.toFloat()
    val bh = boxSizePx.height.toFloat()
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (xFrac * bw - handleSizePx / 2f).toInt(),
                    (yFrac * bh - handleSizePx / 2f).toInt()
                )
            }
            .size(handleSize)
            .clip(CircleShape)
            .background(Color.White)
            .pointerInput(boxSizePx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (bw > 0 && bh > 0) {
                        onDrag(dragAmount.x / bw, dragAmount.y / bh)
                    }
                }
            }
    )
}
