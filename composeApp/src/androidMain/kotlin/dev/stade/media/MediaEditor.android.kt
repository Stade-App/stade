package dev.stade.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

actual fun applyMediaEdits(original: ByteArray, crop: CropRect?, strokes: List<EditStroke>): ByteArray {
    val src = BitmapFactory.decodeByteArray(original, 0, original.size) ?: return original
    val mutable = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    val w = mutable.width.toFloat()
    val h = mutable.height.toFloat()

    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        if (stroke.points.size == 1) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = Paint.Style.FILL
            }
            val p = stroke.points[0]
            canvas.drawCircle(p.x * w, p.y * h, (stroke.widthFraction * w) / 2f, paint)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = stroke.widthFraction * w
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            path.moveTo(stroke.points[0].x * w, stroke.points[0].y * h)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
            }
            canvas.drawPath(path, paint)
        }
    }

    val edited = if (crop != null) {
        val left = (crop.left * w).roundToInt().coerceIn(0, mutable.width - 1)
        val top = (crop.top * h).roundToInt().coerceIn(0, mutable.height - 1)
        val right = (crop.right * w).roundToInt().coerceIn(left + 1, mutable.width)
        val bottom = (crop.bottom * h).roundToInt().coerceIn(top + 1, mutable.height)
        Bitmap.createBitmap(mutable, left, top, right - left, bottom - top)
    } else {
        mutable
    }

    val out = ByteArrayOutputStream()
    edited.compress(Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}
