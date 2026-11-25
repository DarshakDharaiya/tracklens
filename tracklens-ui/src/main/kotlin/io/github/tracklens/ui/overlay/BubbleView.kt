package io.github.tracklens.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.github.tracklens.ui.R
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating TrackLens bubble: a round button with an unread badge that the user can drag anywhere and that snaps
 * to the nearest screen edge, clear of system bars and display cutouts.
 *
 * Plain `View` (not Compose) so it can be added to any host activity's window, Compose-based or not. Placement is
 * delegated to [BubblePositioner]; attaching it to activities and persisting [position] is Task 18.
 */
@SuppressLint("ViewConstructor") // Created in code only, never inflated.
@Suppress("TooManyFunctions") // View overrides plus small drawing and placement helpers.
internal class BubbleView(
    context: Context,
) : View(context) {
    /** Receives the user's actions on the bubble. */
    interface Listener {
        fun onTap()

        fun onHide()

        fun onPositionChanged(position: BubblePosition)

        /** A drag began (past the touch slop). */
        fun onDragStarted() {}

        /** The bubble's center moved to ([centerX], [centerY]) in parent coordinates. */
        fun onDragMoved(
            centerX: Float,
            centerY: Float,
        ) {}

        /** The drag ended at ([centerX], [centerY]); return `true` if the drop was handled (the bubble won't snap). */
        fun onDragEnded(
            centerX: Float,
            centerY: Float,
        ): Boolean = false
    }

    var listener: Listener? = null

    /** Unread events shown on the badge; 0 hides it, above 99 shows "99+". */
    var unreadCount: Int = 0
        set(value) {
            val count = value.coerceAtLeast(0)
            if (field == count) return
            field = count
            updateContentDescription()
            invalidate()
        }

    private var currentPosition = BubblePosition.DEFAULT

    /** Resting position. Setting it moves the bubble without animation and without notifying [listener]. */
    var position: BubblePosition
        get() = currentPosition
        set(value) {
            currentPosition = value.normalized()
            applyPosition(animate = false)
        }

    /** Skip the snap animation. Defaults to the system's "remove animations" setting. */
    var reducedMotion: Boolean = isAnimationDisabled(context)

    private val bubbleSize = dp(BUBBLE_SIZE_DP)
    private val margin = dp(MARGIN_DP)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var hasDispatchedInsets = false
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    private var downRawX = 0f
    private var downRawY = 0f
    private var downTranslationX = 0f
    private var downTranslationY = 0f
    private var dragging = false

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BODY_COLOR }
    private val iconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BADGE_COLOR }
    private val badgeTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SP, resources.displayMetrics)
        }
    private val badgeRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        elevation = dp(ELEVATION_DP).toFloat()
        outlineProvider = OVAL_OUTLINE
        updateContentDescription()
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            context.getString(R.string.tracklens_action_open),
                        ),
                    )
                }
            },
        )
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.tracklens_action_move)) { _, _ ->
            moveToOtherEdge()
            true
        }
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.tracklens_action_hide)) { _, _ ->
            listener?.onHide()
            true
        }
    }

    /** Text on the badge, or `null` when it is hidden. */
    fun badgeText(): String? =
        when {
            unreadCount <= 0 -> null
            unreadCount > MAX_BADGE_COUNT -> "$MAX_BADGE_COUNT+"
            else -> unreadCount.toString()
        }

    /** Same as the TalkBack action: snap to the opposite edge at the same height. */
    fun moveToOtherEdge() {
        settle(position.onOtherEdge())
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(bubbleSize, bubbleSize)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        if (!dragging) applyPosition(animate = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Added after the window dispatched its insets (the usual case for an overlay): ask for them again.
        hasDispatchedInsets = false
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var bars = WindowInsetsCompat.toWindowInsetsCompat(insets, this).getInsets(INSET_TYPES)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Before Android 11 a sibling may consume insets before they reach us; never trust less than the window's.
            bars = rootInsets()?.let { Insets.max(it, bars) } ?: bars
        }
        hasDispatchedInsets = true
        setInsets(bars)
        if (!dragging) applyPosition(animate = false)
        return insets
    }

    private fun setInsets(bars: Insets) {
        insetLeft = bars.left
        insetTop = bars.top
        insetRight = bars.right
        insetBottom = bars.bottom
    }

    private fun rootInsets(): Insets? = ViewCompat.getRootWindowInsets(this)?.getInsets(INSET_TYPES)

    override fun onDraw(canvas: Canvas) {
        val radius = width / 2f
        canvas.drawCircle(radius, radius, radius, bodyPaint)
        drawLens(canvas, radius)
        badgeText()?.let { drawBadge(canvas, it) }
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() is called for taps below.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downTranslationX = translationX
                downTranslationY = translationY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    listener?.onDragStarted()
                }
                if (dragging) {
                    currentArea()?.let { area ->
                        val (x, y) = BubblePositioner.clamp(downTranslationX + dx, downTranslationY + dy, area)
                        translationX = x
                        translationY = y
                        listener?.onDragMoved(centerX(), centerY())
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) settleAfterDrag() else performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) settleAfterDrag()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        listener?.onTap()
        return true
    }

    private fun settleAfterDrag() {
        dragging = false
        if (listener?.onDragEnded(centerX(), centerY()) == true) return
        val area = currentArea() ?: return
        settle(BubblePositioner.positionFor(translationX, translationY, area, isRtl()))
    }

    /** Moves to [newPosition] with the snap animation and reports it; the user (or TalkBack) chose it. */
    private fun settle(newPosition: BubblePosition) {
        currentPosition = newPosition.normalized()
        applyPosition(animate = true)
        listener?.onPositionChanged(currentPosition)
    }

    private fun applyPosition(animate: Boolean) {
        val area = currentArea() ?: return
        val (x, y) = BubblePositioner.translationFor(position, area, isRtl())
        if (animate && !reducedMotion && isAttachedToWindow) {
            animate()
                .translationX(x)
                .translationY(y)
                .setDuration(SNAP_DURATION_MS)
                .start()
        } else {
            animate().cancel()
            translationX = x
            translationY = y
        }
    }

    private fun currentArea(): BubbleArea? {
        val container = (parent as? View)?.takeIf { it.width > 0 && it.height > 0 } ?: return null
        // Until a dispatch arrives, the window's own insets are the safe answer.
        if (!hasDispatchedInsets) rootInsets()?.let(::setInsets)
        return BubblePositioner.area(
            parentWidth = container.width,
            parentHeight = container.height,
            bubbleSize = bubbleSize,
            margin = margin,
            insetLeft = insetLeft,
            insetTop = insetTop,
            insetRight = insetRight,
            insetBottom = insetBottom,
        )
    }

    /** Center in parent coordinates. */
    fun centerX(): Float = left + translationX + width / 2f

    /** Center in parent coordinates. */
    fun centerY(): Float = top + translationY + height / 2f

    private fun isRtl(): Boolean = (parent as? View ?: this).layoutDirection == LAYOUT_DIRECTION_RTL

    private fun drawLens(
        canvas: Canvas,
        radius: Float,
    ) {
        iconPaint.strokeWidth = width * ICON_STROKE
        val lensRadius = width * LENS_RADIUS
        val lensCenter = radius - width * LENS_OFFSET
        canvas.drawCircle(lensCenter, lensCenter, lensRadius, iconPaint)
        val handleStart = lensCenter + lensRadius * HANDLE_START
        val handleEnd = radius + width * HANDLE_END
        canvas.drawLine(handleStart, handleStart, handleEnd, handleEnd, iconPaint)
    }

    private fun drawBadge(
        canvas: Canvas,
        text: String,
    ) {
        val height = width * BADGE_HEIGHT
        val textWidth = badgeTextPaint.measureText(text)
        val badgeWidth = maxOf(height, textWidth + height * BADGE_PADDING)
        badgeRect.set(width - badgeWidth, 0f, width.toFloat(), height)
        canvas.drawRoundRect(badgeRect, height / 2, height / 2, badgePaint)
        val baseline = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
        canvas.drawText(text, badgeRect.centerX(), baseline, badgeTextPaint)
    }

    private fun updateContentDescription() {
        contentDescription =
            if (unreadCount == 0) {
                context.getString(R.string.tracklens_bubble_description)
            } else {
                resources.getQuantityString(R.plurals.tracklens_bubble_description_unread, unreadCount, unreadCount)
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val BUBBLE_SIZE_DP = 56
        const val MARGIN_DP = 8
        const val MAX_BADGE_COUNT = 99
        private val INSET_TYPES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        private const val ELEVATION_DP = 6
        private const val BADGE_TEXT_SP = 11f
        private const val SNAP_DURATION_MS = 200L

        /** Teal 800: white text and icon on it have a contrast ratio above 6:1. */
        private val BODY_COLOR = Color.rgb(0x00, 0x69, 0x5C)

        /** Red 800: white badge text on it has a contrast ratio above 5:1. */
        private val BADGE_COLOR = Color.rgb(0xC6, 0x28, 0x28)

        // Proportions of the lens icon and badge, relative to the bubble width.
        private const val ICON_STROKE = 0.07f
        private const val LENS_RADIUS = 0.2f
        private const val LENS_OFFSET = 0.06f
        private const val HANDLE_START = 0.71f
        private const val HANDLE_END = 0.2f
        private const val BADGE_HEIGHT = 0.36f
        private const val BADGE_PADDING = 0.6f

        private val OVAL_OUTLINE =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: android.graphics.Outline,
                ) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }

        private fun isAnimationDisabled(context: Context): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
