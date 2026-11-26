package io.github.tracklens.ui.live

import android.app.Application
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.TrackLens
import io.github.tracklens.ui.TrackLensActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Task 20 on a real device, through the real install and pipeline: a burst of 1,000 events reaches the Live list in
 * order, and the list then scrolls smoothly under real flings.
 *
 * No Compose test rule here on purpose: while one is active, Compose runs frames and animations on the test's clock,
 * so frame timings would measure the test harness, not TrackLens. Rows are found through UiAutomation instead, and
 * frame times come from `FrameMetrics` of the activity's window.
 */
@RunWith(AndroidJUnit4::class)
class LiveBurstDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application

    @Test
    fun aThousandEventBurstArrivesInOrderAndScrollsSmoothly() {
        TrackLens.install(application)
        TrackLens.hideBubble()
        ActivityScenario.launch(TrackLensActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()

            val start = SystemClock.uptimeMillis()
            thread(name = "burst") { repeat(BURST) { TrackLens.log("burst_$it", mapOf("i" to it.toLong())) } }.join()
            val logged = SystemClock.uptimeMillis()
            awaitRow("burst_${BURST - 1}")
            val shown = SystemClock.uptimeMillis()
            Log.i(TAG, "burst: logged in ${logged - start} ms, newest row on screen ${shown - logged} ms after the last call")

            // Newest first and in order: the last three calls are the top three rows.
            val tops = (BURST - 1 downTo BURST - 3).map { rowBounds("burst_$it")!!.top }
            assertTrue("newest first: $tops", tops == tops.sorted() && tops.distinct().size == tops.size)

            val window = Rect().also { bounds -> scenario.onActivity { it.window.decorView.getGlobalVisibleRect(bounds) } }
            val x = window.exactCenterX()
            val low = window.top + window.height() * LOW_FRACTION
            val high = window.top + window.height() * HIGH_FRACTION
            // A debuggable app runs without ahead-of-time compiled code: let the JIT see the scroll path once first,
            // as it has after any user's first scroll.
            repeat(WARM_UP_FLINGS) {
                fling(x, from = low, to = high)
                fling(x, from = high, to = low)
            }
            val frames =
                recordFrames(scenario) {
                    repeat(FLINGS) { fling(x, from = low, to = high) }
                    repeat(FLINGS) { fling(x, from = high, to = low) }
                }

            val sorted = frames.sorted()
            val p50 = sorted[sorted.size / 2]
            val p90 = sorted[sorted.size * 9 / 10]
            val slow = sorted.count { it > SLOW_FRAME_MILLIS }
            Log.i(
                TAG,
                "scroll: ${sorted.size} frames, p50 $p50 ms, p90 $p90 ms, max ${sorted.last()} ms, slow (>$SLOW_FRAME_MILLIS ms) $slow",
            )
            assertTrue("enough frames measured: ${sorted.size}", sorted.size >= MIN_FRAMES)
            assertTrue("p50 frame $p50 ms", p50 <= MAX_P50_MILLIS)
            assertTrue("p90 frame $p90 ms", p90 <= MAX_P90_MILLIS)
            assertTrue("slow frames $slow of ${sorted.size}", slow * 100 <= sorted.size * MAX_SLOW_PERCENT)
        }
    }

    /** Screen bounds of the text [text], or `null` if it is not on screen. */
    private fun rowBounds(text: String): Rect? {
        // Compose's accessibility provider does not implement findAccessibilityNodeInfosByText: walk the tree.
        val node = find(instrumentation.uiAutomation.rootInActiveWindow, depth = 0) { it.text?.toString() == text } ?: return null
        return Rect().also(node::getBoundsInScreen)
    }

    private fun find(
        node: AccessibilityNodeInfo?,
        depth: Int,
        matches: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? =
        when {
            node == null || depth > MAX_DEPTH -> null
            matches(node) -> node
            else -> (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it), depth + 1, matches) }
        }

    /** What UiAutomation sees, for failure messages. */
    private fun describeScreen(): String {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return "no active window"
        val seen = mutableListOf<String>()
        find(root, depth = 0) { node ->
            node.text?.let { seen += it.toString() }
            seen.size >= DESCRIBE_LIMIT
        }
        return "${root.packageName}: $seen"
    }

    private fun awaitRow(text: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (rowBounds(text) == null) {
            if (SystemClock.uptimeMillis() >
                deadline
            ) {
                throw AssertionError("$text not on screen within $TIMEOUT_MILLIS ms; seen: ${describeScreen()}")
            }
            SystemClock.sleep(POLL_MILLIS)
        }
    }

    /** A quick finger flick from [from] to [to], one move every [STEP_MILLIS] like a real touchscreen, then settling. */
    private fun fling(
        x: Float,
        from: Float,
        to: Float,
    ) {
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, x, from)
        for (step in 1..FLING_STEPS) {
            SystemClock.sleep(STEP_MILLIS)
            inject(MotionEvent.ACTION_MOVE, downTime, x, from + (to - from) * step / FLING_STEPS)
        }
        inject(MotionEvent.ACTION_UP, downTime, x, to)
        SystemClock.sleep(FLING_SETTLE_MILLIS)
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

    /** Total duration of every frame drawn while [block] runs, in milliseconds. */
    private fun recordFrames(
        scenario: ActivityScenario<TrackLensActivity>,
        block: () -> Unit,
    ): List<Long> {
        val durations = Collections.synchronizedList(ArrayList<Long>())
        val handlerThread = HandlerThread("frame-metrics").apply { start() }
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                    durations += TimeUnit.NANOSECONDS.toMillis(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                }
            }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper)) }
        try {
            block()
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            handlerThread.quitSafely()
        }
        return ArrayList(durations)
    }

    private companion object {
        const val TAG = "LiveBurstDeviceTest"
        const val BURST = 1_000
        const val FLINGS = 4
        const val WARM_UP_FLINGS = 1
        const val FLING_STEPS = 12
        const val STEP_MILLIS = 8L
        const val FLING_SETTLE_MILLIS = 900L
        const val LOW_FRACTION = 0.75f
        const val HIGH_FRACTION = 0.35f
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
        const val DESCRIBE_LIMIT = 40
        const val MAX_DEPTH = 40

        /** Two frames at 60 Hz: a frame that takes longer visibly stutters on any refresh rate. */
        const val SLOW_FRAME_MILLIS = 33L

        // Limits for a debuggable build (TrackLens only runs in those): typical frames within one 60 Hz frame,
        // nine in ten within three, and few visible stutters. Measured runs: p50 11-12 ms, p90 27-36 ms, 6-9% slow.
        const val MAX_P50_MILLIS = 17L
        const val MAX_P90_MILLIS = 50L
        const val MAX_SLOW_PERCENT = 15
        const val MIN_FRAMES = 60
    }
}
