package dev.stade.media

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun rotateImageBytes(bytes: ByteArray, degrees: Int): ByteArray {
    val turn = normalizeRotation(degrees)
    if (turn == 0) return bytes
    val source: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return bytes

    val swapped = turn == 90 || turn == 270
    val width = if (swapped) source.height else source.width
    val height = if (swapped) source.width else source.height
    val dest = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    val g = dest.createGraphics()
    try {
        val transform = AffineTransform()
        transform.translate(width / 2.0, height / 2.0)
        transform.rotate(Math.toRadians(turn.toDouble()))
        transform.translate(-source.width / 2.0, -source.height / 2.0)
        g.drawImage(source, transform, null)
    } finally {
        g.dispose()
    }

    val out = ByteArrayOutputStream()
    ImageIO.write(dest, "png", out)
    return out.toByteArray()
}
