package io.github.tracklens.ui.overlay

import io.github.tracklens.Edge

/**
 * Where the bubble rests: the screen edge it snapped to, and how far down the usable height it sits (0 = top,
 * 1 = bottom). Stored as a fraction so it survives rotation and different screen sizes (Task 18 persists it).
 */
internal data class BubblePosition(
    val edge: Edge,
    val yFraction: Float,
) {
    fun normalized(): BubblePosition = copy(yFraction = yFraction.coerceIn(0f, 1f))

    fun onOtherEdge(): BubblePosition = copy(edge = if (edge == Edge.START) Edge.END else Edge.START)

    companion object {
        /** End edge, a third of the way down: clear of most toolbars and bottom navigation. */
        val DEFAULT = BubblePosition(Edge.END, yFraction = 1f / 3)
    }
}

/** Allowed range for the bubble's top-left corner, in parent coordinates. */
internal data class BubbleArea(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

/** Pure placement rules for the bubble; no Android types, so they are unit-tested directly. */
internal object BubblePositioner {
    /**
     * The area inside the parent where the bubble may sit: the parent minus system-bar and display-cutout insets,
     * minus a margin. If the parent is too small, the range collapses to its start instead of going negative.
     */
    @Suppress("LongParameterList") // Plain numbers keep this free of Android types.
    fun area(
        parentWidth: Int,
        parentHeight: Int,
        bubbleSize: Int,
        margin: Int,
        insetLeft: Int,
        insetTop: Int,
        insetRight: Int,
        insetBottom: Int,
    ): BubbleArea {
        val minX = (insetLeft + margin).toFloat()
        val minY = (insetTop + margin).toFloat()
        val maxX = (parentWidth - insetRight - margin - bubbleSize).toFloat().coerceAtLeast(minX)
        val maxY = (parentHeight - insetBottom - margin - bubbleSize).toFloat().coerceAtLeast(minY)
        return BubbleArea(minX, maxX, minY, maxY)
    }

    /** Top-left corner for [position]; `START` is the left edge in left-to-right layouts, the right edge otherwise. */
    fun translationFor(
        position: BubblePosition,
        area: BubbleArea,
        isRtl: Boolean,
    ): Pair<Float, Float> {
        val onLeft = (position.edge == Edge.START) != isRtl
        val x = if (onLeft) area.minX else area.maxX
        val y = area.minY + (area.maxY - area.minY) * position.normalized().yFraction
        return x to y
    }

    /** Where a bubble dropped at ([x], [y]) settles: the nearer edge, at its clamped height. */
    fun positionFor(
        x: Float,
        y: Float,
        area: BubbleArea,
        isRtl: Boolean,
    ): BubblePosition {
        val onLeft = x - area.minX <= area.maxX - x
        val edge = if (onLeft != isRtl) Edge.START else Edge.END
        val range = area.maxY - area.minY
        val fraction = if (range <= 0f) MIDDLE else ((y - area.minY) / range).coerceIn(0f, 1f)
        return BubblePosition(edge, fraction)
    }

    /** Height used when the area has no vertical room to choose from. */
    private const val MIDDLE = 0.5f

    /** Keeps a dragged bubble inside the area. */
    fun clamp(
        x: Float,
        y: Float,
        area: BubbleArea,
    ): Pair<Float, Float> = x.coerceIn(area.minX, area.maxX) to y.coerceIn(area.minY, area.maxY)
}
