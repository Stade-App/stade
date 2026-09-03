package dev.stade.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val DOT_DIAMETER = 6.dp
private val DOT_GAP = 3.dp
private val DOT_STROKE = 1.2.dp

@Composable
fun DeliveryStatusDots(delivered: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(DOT_DIAMETER * 2 + DOT_GAP)
            .height(DOT_DIAMETER)
    ) {
        val radius = size.height / 2f
        val strokeWidth = DOT_STROKE.toPx()
        val centers = listOf(radius, size.width - radius)
        centers.forEach { centerX ->
            if (delivered) {
                drawCircle(color = tint, radius = radius, center = Offset(centerX, radius))
            } else {
                drawCircle(
                    color = tint,
                    radius = radius - strokeWidth / 2f,
                    center = Offset(centerX, radius),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}
