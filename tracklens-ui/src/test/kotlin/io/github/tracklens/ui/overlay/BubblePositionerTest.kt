package io.github.tracklens.ui.overlay

import io.github.tracklens.Edge
import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePositionerTest {
    // 1080 x 2400 screen, 168 px bubble, 24 px margin, 100 px status bar, 150 px navigation bar, 60 px cutout on the left.
    private val area =
        BubblePositioner.area(
            parentWidth = 1080,
            parentHeight = 2400,
            bubbleSize = 168,
            margin = 24,
            insetLeft = 60,
            insetTop = 100,
            insetRight = 0,
            insetBottom = 150,
        )

    @Test
    fun theAreaExcludesInsetsAndMargin() {
        assertEquals(BubbleArea(minX = 84f, maxX = 888f, minY = 124f, maxY = 2058f), area)
    }

    @Test
    fun aTooSmallParentCollapsesTheAreaInsteadOfGoingNegative() {
        val tiny = BubblePositioner.area(100, 100, 168, 24, 0, 0, 0, 0)

        assertEquals(BubbleArea(24f, 24f, 24f, 24f), tiny)
    }

    @Test
    fun startIsLeftInLtrAndRightInRtl() {
        val top = BubblePosition(Edge.START, 0f)

        assertEquals(84f to 124f, BubblePositioner.translationFor(top, area, isRtl = false))
        assertEquals(888f to 124f, BubblePositioner.translationFor(top, area, isRtl = true))
        assertEquals(888f to 2058f, BubblePositioner.translationFor(BubblePosition(Edge.END, 1f), area, isRtl = false))
    }

    @Test
    fun theFractionMapsOntoTheUsableHeight() {
        val (_, y) = BubblePositioner.translationFor(BubblePosition(Edge.END, 0.5f), area, isRtl = false)

        assertEquals(124f + (2058f - 124f) / 2, y)
    }

    @Test
    fun aDroppedBubbleSnapsToTheNearerEdge() {
        assertEquals(Edge.START, BubblePositioner.positionFor(400f, 500f, area, isRtl = false).edge)
        assertEquals(Edge.END, BubblePositioner.positionFor(600f, 500f, area, isRtl = false).edge)
        assertEquals("left is END in RTL", Edge.END, BubblePositioner.positionFor(400f, 500f, area, isRtl = true).edge)
    }

    @Test
    fun theDropHeightIsKeptAsAFractionAndClamped() {
        assertEquals(0f, BubblePositioner.positionFor(84f, -500f, area, isRtl = false).yFraction)
        assertEquals(1f, BubblePositioner.positionFor(84f, 9_999f, area, isRtl = false).yFraction)
        assertEquals(0.5f, BubblePositioner.positionFor(84f, 124f + (2058f - 124f) / 2, area, isRtl = false).yFraction, 0.0001f)
    }

    @Test
    fun aRoundTripThroughPositionKeepsTheSpot() {
        val original = BubblePosition(Edge.START, 0.42f)
        val (x, y) = BubblePositioner.translationFor(original, area, isRtl = false)

        val back = BubblePositioner.positionFor(x, y, area, isRtl = false)

        assertEquals(original.edge, back.edge)
        assertEquals(original.yFraction, back.yFraction, 0.0001f)
    }

    @Test
    fun draggingIsClampedToTheArea() {
        assertEquals(84f to 2058f, BubblePositioner.clamp(-200f, 5_000f, area))
        assertEquals(500f to 900f, BubblePositioner.clamp(500f, 900f, area))
    }

    @Test
    fun outOfRangeFractionsAreNormalized() {
        assertEquals(1f, BubblePosition(Edge.END, 3f).normalized().yFraction)
        assertEquals(Edge.START, BubblePosition(Edge.END, 0.2f).onOtherEdge().edge)
    }
}
