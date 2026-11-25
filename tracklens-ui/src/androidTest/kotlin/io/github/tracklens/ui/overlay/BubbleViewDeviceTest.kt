package io.github.tracklens.ui.overlay

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Task 17 on a real device: real touch events through the activity window, and the device's real system bars.
 */
@RunWith(AndroidJUnit4::class)
class BubbleViewDeviceTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var decor: FrameLayout
    private lateinit var bubble: BubbleView
    private val events = mutableListOf<String>()

    @Before
    fun addBubbleToTheWindow() {
        activityRule.scenario.onActivity { activity ->
            decor = activity.window.decorView as FrameLayout
            bubble =
                BubbleView(activity).apply {
                    reducedMotion = true
                    listener =
                        object : BubbleView.Listener {
                            override fun onTap() {
                                events += "tap"
                            }

                            override fun onHide() {
                                events += "hide"
                            }

                            override fun onPositionChanged(position: BubblePosition) {
                                events += "moved:${position.edge}"
                            }
                        }
                }
            decor.addView(bubble)
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun draggingAcrossTheScreenSnapsToTheOtherEdge() {
        onMain { bubble.position = BubblePosition(Edge.END, 0.5f) }
        val start = bubbleCenterOnScreen()

        drag(from = start, to = Pair(decor.width * 0.2f, decor.height * 0.3f))

        val leftEdge = if (decor.layoutDirection == View.LAYOUT_DIRECTION_RTL) Edge.END else Edge.START
        assertEquals(leftEdge, onMain { bubble.position.edge })
        assertEquals(listOf("moved:$leftEdge"), events)
        val (minX, _) = insetsLeftTop()
        assertEquals("rests at the left edge", (minX + margin()).toFloat(), onMain { bubble.translationX }, 1f)
    }

    @Test
    fun aMovementWithinTouchSlopIsATap() {
        val (x, y) = bubbleCenterOnScreen()

        drag(from = x to y, to = (x + 2f) to (y + 2f), steps = 2)

        assertEquals(listOf("tap"), events)
    }

    @Test
    fun theBubbleNeverOverlapsTheDevicesSystemBars() {
        val bars = onMain { systemBarInsets() }

        for (target in listOf(Pair(decor.width / 2f, -500f), Pair(decor.width / 2f, decor.height + 500f))) {
            drag(from = bubbleCenterOnScreen(), to = target)
            val rect = onMain { bubbleRectInDecor() }
            assertTrue("$rect below the top bars (${bars.top})", rect.top >= bars.top)
            assertTrue("$rect above the bottom bars (${bars.bottom})", rect.bottom <= decor.height - bars.bottom)
            assertTrue("$rect clear of the left inset (${bars.left})", rect.left >= bars.left)
            assertTrue("$rect clear of the right inset (${bars.right})", rect.right <= decor.width - bars.right)
        }
    }

    @Test
    fun arbitraryInsetsAreRespected() {
        onMain {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(40, 300, 50, 400))
                    .build()
            bubble.dispatchApplyWindowInsets(requireNotNull(insets.toWindowInsets()))
            bubble.position = BubblePosition(Edge.START, 0f)
        }
        val ltr = decor.layoutDirection != View.LAYOUT_DIRECTION_RTL
        val expectedTopX = if (ltr) 40 + margin() else decor.width - 50 - margin() - bubble.width
        assertEquals(expectedTopX.toFloat(), onMain { bubble.translationX }, 1f)
        assertEquals((300 + margin()).toFloat(), onMain { bubble.translationY }, 1f)

        onMain { bubble.position = BubblePosition(Edge.START, 1f) }
        assertEquals((decor.height - 400 - margin() - bubble.height).toFloat(), onMain { bubble.translationY }, 1f)
    }

    private fun drag(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        steps: Int = 20,
    ) {
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, from.first, from.second)
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            inject(
                MotionEvent.ACTION_MOVE,
                downTime,
                from.first + (to.first - from.first) * fraction,
                from.second + (to.second - from.second) * fraction,
            )
        }
        inject(MotionEvent.ACTION_UP, downTime, to.first, to.second)
        instrumentation.waitForIdleSync()
    }

    private fun inject(
        action: Int,
        downTime: Long,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        instrumentation.sendPointerSync(event)
        event.recycle()
    }

    private fun bubbleCenterOnScreen(): Pair<Float, Float> =
        onMain {
            val location = IntArray(2).also(bubble::getLocationOnScreen)
            (location[0] + bubble.width / 2f) to (location[1] + bubble.height / 2f)
        }

    private fun bubbleRectInDecor(): Rect {
        val left = bubble.left + bubble.translationX.roundToInt()
        val top = bubble.top + bubble.translationY.roundToInt()
        return Rect(left, top, left + bubble.width, top + bubble.height)
    }

    private fun systemBarInsets(): Insets =
        requireNotNull(ViewCompat.getRootWindowInsets(decor))
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

    private fun insetsLeftTop(): Pair<Int, Int> = onMain { systemBarInsets().let { it.left to it.top } }

    private fun margin(): Int = (BubbleView.MARGIN_DP * bubble.resources.displayMetrics.density + 0.5f).toInt()

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
