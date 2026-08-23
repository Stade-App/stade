package dev.stade.notification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

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

    fun bitmapFor(name: String, keySeed: ByteArray? = null, sizePx: Int = 128): Bitmap {
        val seed = if (keySeed != null && keySeed.isNotEmpty()) {
            keySeed.fold(0) { acc, byte -> (acc * 31 + byte.toInt()) and 0x7fffffff }
        } else {
            name.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
        }
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

    fun fromPhotoBytes(bytes: ByteArray, sizePx: Int = 128): Bitmap? = runCatching {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(radius, radius, radius, paint)
        bitmap
    }.getOrNull()
}
