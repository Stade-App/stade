package dev.stade.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BAR = 56
private const val GAP = 18
private const val AREA = 1000

class QuickReactionBarTest {

    @Test
    fun theBarSitsAboveABubbleWithRoomOverIt() {
        val y = reactionBarOffsetY(anchorTop = 400f, anchorBottom = 500f, barHeight = BAR, gap = GAP, areaHeight = AREA)
        assertEquals(400 - BAR - GAP, y)
        assertTrue(y + BAR <= 400, "the bar must not cover the message it belongs to")
    }

    @Test
    fun theBarFlipsBelowWhenTheBubbleIsAtTheTop() {
        val y = reactionBarOffsetY(anchorTop = 10f, anchorBottom = 120f, barHeight = BAR, gap = GAP, areaHeight = AREA)
        assertEquals(120 + GAP, y)
        assertTrue(y >= 120, "flipped placement must start below the bubble")
    }

    @Test
    fun theBarNeverLeavesTheTopOfTheViewport() {
        val y = reactionBarOffsetY(anchorTop = 0f, anchorBottom = 0f, barHeight = BAR, gap = GAP, areaHeight = AREA)
        assertTrue(y >= 0)
    }

    @Test
    fun theBarNeverLeavesTheBottomOfTheViewport() {
        val y = reactionBarOffsetY(
            anchorTop = 990f,
            anchorBottom = 1400f,
            barHeight = BAR,
            gap = GAP,
            areaHeight = AREA
        )
        assertTrue(y + BAR <= AREA, "bar bottom $y+$BAR spilled past viewport $AREA")
    }

    @Test
    fun aBubbleTallerThanTheViewportStillPlacesTheBar() {
        val y = reactionBarOffsetY(anchorTop = -500f, anchorBottom = 1500f, barHeight = BAR, gap = GAP, areaHeight = AREA)
        assertTrue(y in 0..(AREA - BAR))
    }

    @Test
    fun anUnmeasuredBarDoesNotJump() {
        assertEquals(0, reactionBarOffsetY(anchorTop = 400f, anchorBottom = 500f, barHeight = 0, gap = GAP, areaHeight = AREA))
    }

    @Test
    fun aViewportSmallerThanTheBarIsSurvivable() {
        val y = reactionBarOffsetY(anchorTop = 5f, anchorBottom = 20f, barHeight = BAR, gap = GAP, areaHeight = 10)
        assertEquals(0, y)
    }

    @Test
    fun theQuickSetIsDistinctAndCompact() {
        assertEquals(QUICK_REACTIONS.size, QUICK_REACTIONS.toSet().size, "duplicate emoji in the quick row")
        assertTrue(QUICK_REACTIONS.size in 4..8, "the row must stay thumb-reachable")
        assertTrue(QUICK_REACTIONS.all { it.isNotBlank() })
    }
}
