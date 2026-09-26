package dev.stade.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

actual fun rotateImageBytes(bytes: ByteArray, degrees: Int): ByteArray {
    val turn = normalizeRotation(degrees)
    if (turn == 0) return bytes
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val matrix = Matrix().apply { postRotate(turn.toFloat()) }
    val rotated = runCatching {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }.getOrNull() ?: return bytes
    val out = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
