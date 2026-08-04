package dev.stade.media

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun encodeStickerPng(bytes: ByteArray, maxDim: Int): ByteArray {
    val img: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return bytes
    val scaled = if (img.width > maxDim || img.height > maxDim) {
        val scale = maxDim.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()
        val dest = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = dest.createGraphics()
        try {
            g.drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
        } finally {
            g.dispose()
        }
        dest
    } else if (img.type != BufferedImage.TYPE_INT_ARGB) {
        val dest = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        val g = dest.createGraphics()
        try {
            g.drawImage(img, 0, 0, null)
        } finally {
            g.dispose()
        }
        dest
    } else img
    val out = ByteArrayOutputStream()
    ImageIO.write(scaled, "png", out)
    return out.toByteArray()
}
