package dev.stade.ui.components

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun frame(color: Color): BufferedImage {
    val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.color = color
        g.fillRect(0, 0, 8, 8)
    } finally {
        g.dispose()
    }
    return image
}

private fun animatedGif(delayCentis: Int, vararg colors: Color): ByteArray {
    val out = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("gif").next()
    val stream = ImageIO.createImageOutputStream(out)
    writer.output = stream
    val params = writer.defaultWriteParam
    val first = frame(colors.first())
    val type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(first)
    val metadata = writer.getDefaultImageMetadata(type, params)
    val format = metadata.nativeMetadataFormatName

    val root = metadata.getAsTree(format) as IIOMetadataNode
    val gce = IIOMetadataNode("GraphicControlExtension").apply {
        setAttribute("disposalMethod", "none")
        setAttribute("userInputFlag", "FALSE")
        setAttribute("transparentColorFlag", "FALSE")
        setAttribute("delayTime", delayCentis.toString())
        setAttribute("transparentColorIndex", "0")
    }
    root.appendChild(gce)
    metadata.setFromTree(format, root)

    writer.prepareWriteSequence(null)
    for (color in colors) {
        writer.writeToSequence(IIOImage(frame(color), null, metadata), params as ImageWriteParam)
    }
    writer.endWriteSequence()
    stream.close()
    writer.dispose()
    return out.toByteArray()
}

private fun staticPng(): ByteArray {
    val out = ByteArrayOutputStream()
    ImageIO.write(frame(Color.BLUE), "png", out)
    return out.toByteArray()
}

class GifStickerTest {

    @Test
    fun aGifIsRecognisedByItsHeader() {
        assertTrue(isGifBytes(animatedGif(10, Color.RED, Color.GREEN)))
        assertTrue(isGifBytes("GIF87a".encodeToByteArray() + ByteArray(20)))
        assertTrue(isGifBytes("GIF89a".encodeToByteArray() + ByteArray(20)))
    }

    @Test
    fun nonGifBytesAreNotMistakenForGifs() {
        assertFalse(isGifBytes(staticPng()))
        assertFalse(isGifBytes(ByteArray(0)))
        assertFalse(isGifBytes("GIF".encodeToByteArray()))
        assertFalse(isGifBytes("NOTAGIF!".encodeToByteArray()))
    }

    @Test
    fun everyFrameOfAnAnimatedGifIsDecoded() {
        val decoded = assertNotNull(decodeGif(animatedGif(10, Color.RED, Color.GREEN, Color.BLUE)))
        assertEquals(3, decoded.frames.size)
        assertEquals(3, decoded.delaysMs.size)
        assertTrue(decoded.frames.all { it.width == 8 && it.height == 8 })
    }

    @Test
    fun frameDelaysSurviveDecoding() {
        val decoded = assertNotNull(decodeGif(animatedGif(7, Color.RED, Color.GREEN)))
        assertTrue(
            decoded.delaysMs.all { it > 0 },
            "a zero delay would spin the animation loop at full speed"
        )
    }

    @Test
    fun aSingleFrameImageIsNotTreatedAsAnimation() {
        assertEquals(null, decodeGif(staticPng()))
        assertEquals(null, decodeGif(animatedGif(10, Color.RED)))
    }

    @Test
    fun corruptBytesDoNotThrow() {
        assertEquals(null, decodeGif(ByteArray(0)))
        assertEquals(null, decodeGif("GIF89a".encodeToByteArray() + ByteArray(32) { 0x7F }))
    }
}
