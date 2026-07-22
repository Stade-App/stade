package dev.stade.media

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

actual fun applyMediaEdits(original: ByteArray, crop: CropRect?, strokes: List<EditStroke>): ByteArray {
    val src = ImageIO.read(ByteArrayInputStream(original)) ?: return original
    val w = src.width
    val h = src.height

    val mutable = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = mutable.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.drawImage(src, 0, 0, null)

    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val awtColor = Color(
            (stroke.color.red * 255).roundToInt().coerceIn(0, 255),
            (stroke.color.green * 255).roundToInt().coerceIn(0, 255),
            (stroke.color.blue * 255).roundToInt().coerceIn(0, 255),
            (stroke.color.alpha * 255).roundToInt().coerceIn(0, 255)
        )
        g.color = awtColor
        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            val r = (stroke.widthFraction * w) / 2f
            g.fill(Ellipse2D.Float(p.x * w - r, p.y * h - r, r * 2f, r * 2f))
        } else {
            g.stroke = BasicStroke(
                stroke.widthFraction * w,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            )
            val path = Path2D.Float()
            path.moveTo((stroke.points[0].x * w).toDouble(), (stroke.points[0].y * h).toDouble())
            for (i in 1 until stroke.points.size) {
                path.lineTo((stroke.points[i].x * w).toDouble(), (stroke.points[i].y * h).toDouble())
            }
            g.draw(path)
        }
    }
    g.dispose()

    val edited = if (crop != null) {
        val left = (crop.left * w).roundToInt().coerceIn(0, w - 1)
        val top = (crop.top * h).roundToInt().coerceIn(0, h - 1)
        val right = (crop.right * w).roundToInt().coerceIn(left + 1, w)
        val bottom = (crop.bottom * h).roundToInt().coerceIn(top + 1, h)
        mutable.getSubimage(left, top, right - left, bottom - top)
    } else {
        mutable
    }

    val rgbImage = BufferedImage(edited.width, edited.height, BufferedImage.TYPE_INT_RGB)
    val g2 = rgbImage.createGraphics()
    g2.color = Color.WHITE
    g2.fillRect(0, 0, edited.width, edited.height)
    g2.drawImage(edited, 0, 0, null)
    g2.dispose()

    val out = ByteArrayOutputStream()
    ImageIO.write(rgbImage, "jpg", out)
    return out.toByteArray()
}
