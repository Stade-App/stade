package app.stade.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stade.qr.QrEncoder
import app.stade.qr.QrErrorCorrection
import app.stade.qr.QrMatrix

@Composable
fun QrCodeView(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    foreground: Color = Color.Black,
    background: Color = Color.White,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M
) {
    val matrix = remember(payload, errorCorrection) {
        runCatching { QrEncoder.encode(payload, errorCorrection) }.getOrNull()
    }
    Canvas(
        modifier = modifier
            .background(background)
    ) {
        if (matrix != null) drawMatrix(matrix, foreground)
    }
}

private fun DrawScope.drawMatrix(matrix: QrMatrix, color: Color) {
    val cell = size.minDimension / matrix.size
    for (y in 0 until matrix.size) {
        for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                    size = androidx.compose.ui.geometry.Size(cell, cell)
                )
            }
        }
    }
}
