package dev.stade.media

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private fun png(width: Int, height: Int): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.color = Color.BLUE
        g.fillRect(0, 0, width / 2, height / 4)
    } finally {
        g.dispose()
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
    val image = assertNotNull(ImageIO.read(bytes.inputStream()))
    return image.width to image.height
}

class ImageRotateTest {

    @Test
    fun rotationWrapsIntoZeroToThreeSixty() {
        assertEquals(0, normalizeRotation(0))
        assertEquals(90, normalizeRotation(90))
        assertEquals(270, normalizeRotation(-90))
        assertEquals(0, normalizeRotation(360))
        assertEquals(90, normalizeRotation(450))
        assertEquals(180, normalizeRotation(-180))
    }

    @Test
    fun aQuarterTurnSwapsTheDimensions() {
        val source = png(40, 10)
        assertEquals(40 to 10, sizeOf(source))
        assertEquals(10 to 40, sizeOf(rotateImageBytes(source, 90)))
        assertEquals(10 to 40, sizeOf(rotateImageBytes(source, 270)))
        assertEquals(10 to 40, sizeOf(rotateImageBytes(source, -90)))
    }

    @Test
    fun aHalfTurnKeepsTheDimensions() {
        val source = png(40, 10)
        assertEquals(40 to 10, sizeOf(rotateImageBytes(source, 180)))
    }

    @Test
    fun noRotationReturnsTheInputUntouched() {
        val source = png(20, 20)
        assertContentEquals(source, rotateImageBytes(source, 0))
        assertContentEquals(source, rotateImageBytes(source, 360))
    }

    @Test
    fun fourQuarterTurnsComeBackToTheStartingShape() {
        var bytes = png(40, 10)
        repeat(4) { bytes = rotateImageBytes(bytes, 90) }
        assertEquals(40 to 10, sizeOf(bytes))
    }

    @Test
    fun aQuarterTurnMovesTheMarkedCorner() {
        val source = png(40, 10)
        val before = assertNotNull(ImageIO.read(source.inputStream()))
        val after = assertNotNull(ImageIO.read(rotateImageBytes(source, 90).inputStream()))
        assertEquals(Color.BLUE.rgb, before.getRGB(1, 1), "marker starts in the top-left")
        assertEquals(
            Color.BLUE.rgb,
            after.getRGB(after.width - 2, 1),
            "a clockwise turn must carry the marker to the top-right"
        )
    }

    @Test
    fun undecodableBytesAreReturnedUnchanged() {
        val junk = "not an image".encodeToByteArray()
        assertContentEquals(junk, rotateImageBytes(junk, 90))
    }
}
