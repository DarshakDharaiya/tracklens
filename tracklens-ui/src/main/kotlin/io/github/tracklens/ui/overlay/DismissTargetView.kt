package io.github.tracklens.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import io.github.tracklens.ui.R
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The "×" drop target shown at the bottom of the screen while the bubble is dragged. Dropping the bubble on it
 * hides the bubble for the rest of the process (`TrackLens.showBubble` brings it back).
 */
@SuppressLint("ViewConstructor") // Created in code only, never inflated.
internal class DismissTargetView(
    context: Context,
) : View(context) {
    /** `true` while the dragged bubble is over the target; drawn larger and red. */
    var highlighted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val size = (SIZE_DP * resources.displayMetrics.density).roundToInt()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crossPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

    init {
        contentDescription = context.getString(R.string.tracklens_dismiss_target)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // Only exists during a drag gesture.
    }

    /** Whether a bubble centered at ([centerX], [centerY]) (parent coordinates) is over the target. */
    fun contains(
        centerX: Float,
        centerY: Float,
    ): Boolean {
        if (width == 0) return false
        val targetX = left + width / 2f
        val targetY = top + height / 2f
        return hypot(centerX - targetX, centerY - targetY) <= width * CAPTURE_RADIUS
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val center = width / 2f
        val radius = if (highlighted) center else center * IDLE_SCALE
        circlePaint.color = if (highlighted) HIGHLIGHT_COLOR else IDLE_COLOR
        canvas.drawCircle(center, center, radius, circlePaint)
        crossPaint.strokeWidth = width * CROSS_STROKE
        val arm = radius * CROSS_ARM
        canvas.drawLine(center - arm, center - arm, center + arm, center + arm, crossPaint)
        canvas.drawLine(center - arm, center + arm, center + arm, center - arm, crossPaint)
    }

    companion object {
        const val SIZE_DP = 64
        const val BOTTOM_OFFSET_DP = 32
        private const val IDLE_SCALE = 0.8f
        private const val CAPTURE_RADIUS = 1f
        private const val CROSS_STROKE = 0.06f
        private const val CROSS_ARM = 0.35f
        private val IDLE_COLOR = Color.argb(0xB3, 0x21, 0x21, 0x21)
        private val HIGHLIGHT_COLOR = Color.rgb(0xC6, 0x28, 0x28)
    }
}
