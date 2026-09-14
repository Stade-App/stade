package dev.stade.media

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class CircularCropTest {

    private fun assertSquareInPixels(r: CropRect, w: Int, h: Int, tag: String = "") {
        val wPx = (r.right - r.left) * w
        val hPx = (r.bottom - r.top) * h
        assertTrue(abs(wPx - hPx) < 0.5f, "$tag crop is ${wPx}x${hPx} px, not square")
    }

    private fun assertInBounds(r: CropRect, tag: String = "") {
        assertTrue(r.left >= -0.0001f && r.top >= -0.0001f, "$tag crop starts outside the image: $r")
        assertTrue(r.right <= 1.0001f && r.bottom <= 1.0001f, "$tag crop ends outside the image: $r")
        assertTrue(r.right > r.left && r.bottom > r.top, "$tag crop is inverted or empty: $r")
    }

    @Test
    fun aLandscapeImageGetsASquareCrop() {
        val r = toSquare(CropRect(0f, 0f, 1f, 1f), 1920, 1080)
        assertSquareInPixels(r, 1920, 1080)
        assertInBounds(r)
        assertTrue(abs((r.bottom - r.top) - 1f) < 0.001f, "should use the full height of a landscape image")
    }

    @Test
    fun aPortraitImageGetsASquareCrop() {
        val r = toSquare(CropRect(0f, 0f, 1f, 1f), 1080, 1920)
        assertSquareInPixels(r, 1080, 1920)
        assertInBounds(r)
        assertTrue(abs((r.right - r.left) - 1f) < 0.001f, "should use the full width of a portrait image")
    }

    @Test
    fun anAlreadySquareImageIsUnchanged() {
        val r = toSquare(CropRect(0f, 0f, 1f, 1f), 800, 800)
        assertSquareInPixels(r, 800, 800)
        assertTrue(r.isFull, "a square image at full crop should stay full, was $r")
    }

    @Test
    fun theSquareStaysCenteredOnTheRequestedRegion() {
        val requested = CropRect(0.2f, 0.1f, 0.8f, 0.9f)
        val r = toSquare(requested, 1000, 1000)
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        assertTrue(abs(cx - 0.5f) < 0.001f, "center x drifted: $r")
        assertTrue(abs(cy - 0.5f) < 0.001f, "center y drifted: $r")
    }

    @Test
    fun aCropNearTheEdgeIsPushedBackInsteadOfSpilling() {
        val r = toSquare(CropRect(0.9f, 0.9f, 1f, 1f), 1920, 1080)
        assertInBounds(r, "edge")
        assertSquareInPixels(r, 1920, 1080, "edge")
    }

    @Test
    fun extremeAspectRatiosStayInBounds() {
        for (dims in listOf(4000 to 100, 100 to 4000, 1 to 1000, 1000 to 1)) {
            val (w, h) = dims
            val r = toSquare(CropRect(0f, 0f, 1f, 1f), w, h)
            assertInBounds(r, "${w}x$h")
            assertSquareInPixels(r, w, h, "${w}x$h")
        }
    }

    @Test
    fun degenerateDimensionsAreReturnedUnchanged() {
        val input = CropRect(0f, 0f, 1f, 1f)
        assertTrue(toSquare(input, 0, 100) == input)
        assertTrue(toSquare(input, 100, 0) == input)
    }

    @Test
    fun repeatedSquaringIsStable() {
        var r = toSquare(CropRect(0.1f, 0.3f, 0.7f, 0.6f), 1600, 900)
        repeat(20) {
            val next = toSquare(r, 1600, 900)
            assertTrue(abs(next.left - r.left) < 0.0001f, "squaring drifted on repeat: $r -> $next")
            r = next
        }
    }

    @Test
    fun randomCropsAlwaysProduceValidSquares() {
        val random = Random(24680)
        repeat(3000) {
            val w = random.nextInt(1, 5000)
            val h = random.nextInt(1, 5000)
            val l = random.nextFloat() * 0.9f
            val t = random.nextFloat() * 0.9f
            val right = (l + random.nextFloat() * (1f - l)).coerceAtMost(1f)
            val bottom = (t + random.nextFloat() * (1f - t)).coerceAtMost(1f)
            if (right <= l || bottom <= t) return@repeat
            val r = toSquare(CropRect(l, t, right, bottom), w, h)
            assertInBounds(r, "${w}x$h")
        }
    }
}
