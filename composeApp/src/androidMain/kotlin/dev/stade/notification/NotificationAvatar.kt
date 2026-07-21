package dev.stade.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/**
 * Mirrors the in-app Avatar composable (dev.stade.ui.components.Avatar) pixel-for-pixel logic
 * (same palette, same name->color hash, same initial rule) so notification large icons match
 * the avatar a contact is shown with everywhere else in the app.
 */
internal object NotificationAvatar {
    private val palette = listOf(
        0xFF1E6091.toInt() to 0xFF61A5C2.toInt(),
        0xFF166B5C.toInt() to 0xFF5BC0AB.toInt(),
        0xFF7A3E9D.toInt() to 0xFFB695D8.toInt(),
        0xFF9C4221.toInt() to 0xFFE3956A.toInt(),
        0xFF8E2A4A.toInt() to 0xFFD66A8C.toInt(),
        0xFF1F4F8F.toInt() to 0xFF6FA3DD.toInt(),
        0xFF4D6B1F.toInt() to 0xFFA8C46C.toInt(),
        0xFF7A4D00.toInt() to 0xFFD9A24E.toInt()
    )

    fun bitmapFor(name: String, sizePx: Int = 128): Bitmap {
        val seed = name.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
        val (colorA, colorB) = palette[seed % palette.size]
        val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), colorA, colorB, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.42f
            isFakeBoldText = true
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, radius, textY, textPaint)

        return bitmap
    }
}
