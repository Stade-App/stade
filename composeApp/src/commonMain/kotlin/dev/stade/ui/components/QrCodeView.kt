package dev.stade.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun QrCodeView(
    matrix: Array<BooleanArray>,
    modifier: Modifier = Modifier,
    foreground: Color = Color.Black,
    background: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val rows = matrix.size
        val cols = matrix.firstOrNull()?.size ?: 0
        if (rows == 0 || cols == 0) return@Canvas
        val cell = minOf(size.width / cols, size.height / rows)
        val offsetX = (size.width - cell * cols) / 2f
        val offsetY = (size.height - cell * rows) / 2f
        drawRect(color = background, size = size)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (matrix[y][x]) {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(offsetX + x * cell, offsetY + y * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}
