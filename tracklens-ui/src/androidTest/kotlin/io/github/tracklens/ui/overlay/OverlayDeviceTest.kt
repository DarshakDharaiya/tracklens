package io.github.tracklens.ui.overlay

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.tracklens.TrackLens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class HostActivityOne : Activity()

class HostActivityTwo : Activity()

class HostActivityThree : Activity()

/**
 * Task 18 on a real device, through the real install path: `TrackLens.install` finds tracklens-ui with ServiceLoader,
 * and the bubble follows the user across three activities and a rotation, keeping the position they dragged it to.
 */
@RunWith(AndroidJUnit4::class)
class OverlayDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application

    @Before
    fun installTrackLens() {
        BubblePositionStore(application).clear()
        TrackLens.install(application)
        TrackLens.showBubble()
        instrumentation.waitForIdleSync()
    }

    @After
    fun hideForOtherTests() {
        TrackLens.hideBubble()
        instrumentation.waitForIdleSync()
    }

    @Test
    fun theBubbleFollowsAcrossThreeActivitiesAndARotation() {
        ActivityScenario.launch(HostActivityOne::class.java).use {
            val one = awaitResumed(HostActivityOne::class.java)
            assertEquals(1, onMain { bubblesOn(one) })

            // Drag it to the other side; the next screens must show it there.
            val bubble = onMain { bubbleOn(one) }
            val target = onMain { (one.window.decorView.width * 0.1f) to (one.window.decorView.height * 0.6f) }
            drag(from = centerOnScreen(bubble), to = target)
            val dragged = onMain { bubble.position }

            onMain { one.startActivity(Intent(one, HostActivityTwo::class.java)) }
            val two = awaitResumed(HostActivityTwo::class.java)
            assertEquals("left behind on the paused screen", 0, onMain { bubblesOn(one) })
            assertEquals(1, onMain { bubblesOn(two) })
            assertEquals(dragged, onMain { bubbleOn(two).position })

            onMain { two.startActivity(Intent(two, HostActivityThree::class.java)) }
            val three = awaitResumed(HostActivityThree::class.java)
            assertEquals(1, onMain { bubblesOn(three) })
            assertEquals(dragged, onMain { bubbleOn(three).position })

            onMain { three.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val rotated = awaitResumed(HostActivityThree::class.java) { it !== three && it.isLandscape() }
            assertNotSame(three, rotated)
            assertEquals(1, onMain { bubblesOn(rotated) })
            assertEquals("same edge after rotation", dragged.edge, onMain { bubbleOn(rotated).position.edge })
            assertEquals(dragged.yFraction, onMain { bubbleOn(rotated).position.yFraction }, 0.001f)
            onMain { rotated.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }

            onMain { rotated.finish() }
            val backOnTwo = awaitResumed(HostActivityTwo::class.java)
            assertEquals(1, onMain { bubblesOn(backOnTwo) })
            onMain { backOnTwo.finish() }
        }
    }

    @Test
    fun hidingRemovesTheBubbleAndShowingBringsItBack() {
        ActivityScenario.launch(HostActivityOne::class.java).use {
            val one = awaitResumed(HostActivityOne::class.java)
            assertEquals(1, onMain { bubblesOn(one) })

            TrackLens.hideBubble()
            instrumentation.waitForIdleSync()
            assertEquals(0, onMain { bubblesOn(one) })

            TrackLens.showBubble()
            instrumentation.waitForIdleSync()
            assertEquals(1, onMain { bubblesOn(one) })
        }
    }

    private fun Activity.isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun bubbles(activity: Activity): List<BubbleView> {
        val decor = activity.window.decorView as ViewGroup
        return (0 until decor.childCount).mapNotNull { decor.getChildAt(it) as? BubbleView }
    }

    private fun bubblesOn(activity: Activity): Int = bubbles(activity).size

    private fun bubbleOn(activity: Activity): BubbleView = bubbles(activity).single()

    private fun <T : Activity> awaitResumed(
        type: Class<T>,
        accept: (T) -> Boolean = { true },
    ): T {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val found =
                onMain {
                    ActivityLifecycleMonitorRegistry
                        .getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance(type)
                        .firstOrNull(accept)
                }
            if (found != null) {
                instrumentation.waitForIdleSync()
                return found
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("${type.simpleName} was not resumed within $TIMEOUT_MILLIS ms")
    }

    private fun centerOnScreen(view: BubbleView): Pair<Float, Float> =
        onMain {
            val location = IntArray(2).also(view::getLocationOnScreen)
            (location[0] + view.width / 2f) to (location[1] + view.height / 2f)
        }

    private fun drag(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        steps: Int = 20,
    ) {
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, from)
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            inject(
                MotionEvent.ACTION_MOVE,
                downTime,
                (from.first + (to.first - from.first) * fraction) to (from.second + (to.second - from.second) * fraction),
            )
        }
        inject(MotionEvent.ACTION_UP, downTime, to)
        instrumentation.waitForIdleSync()
    }

    private fun inject(
        action: Int,
        downTime: Long,
        point: Pair<Float, Float>,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
        instrumentation.sendPointerSync(event)
        event.recycle()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
