package io.github.tracklens.ui

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.tracklens.TrackLens
import io.github.tracklens.ui.overlay.BubbleView
import io.github.tracklens.ui.overlay.HostActivityOne
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 19 on a real device, through the real install: `TrackLens.openPanel` opens TrackLens in its own task, without
 * a bubble, Back returns to the app, and the launcher shortcut is published.
 */
@RunWith(AndroidJUnit4::class)
class TrackLensActivityDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application

    @Before
    fun install() {
        TrackLens.install(application)
        TrackLens.showBubble()
    }

    @Test
    fun openPanelOpensTrackLensInItsOwnTaskAndBackReturnsToTheApp() {
        ActivityScenario.launch(HostActivityOne::class.java).use {
            val host = awaitResumed(HostActivityOne::class.java)

            onMain { TrackLens.openPanel(host) }
            val trackLens = awaitResumed(TrackLensActivity::class.java)

            assertNotEquals("separate task", onMain { host.taskId }, onMain { trackLens.taskId })
            assertEquals("no bubble on TrackLens' own screen", 0, onMain { bubbles(trackLens) })

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val back = awaitResumed(HostActivityOne::class.java)
            assertEquals(host, back)
            assertTrue(onMain { trackLens.isFinishing || trackLens.isDestroyed })
        }
    }

    @Test
    fun theLauncherShortcutIsPublished() {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (ShortcutManagerCompat.getDynamicShortcuts(application).none { it.id == LauncherShortcut.ID }) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("shortcut not published within $TIMEOUT_MILLIS ms")
            Thread.sleep(POLL_MILLIS)
        }
        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(application).single { it.id == LauncherShortcut.ID }
        assertEquals(TrackLensActivity::class.java.name, shortcut.intent.component?.className)
    }

    private fun bubbles(activity: Activity): Int {
        val decor = activity.window.decorView as ViewGroup
        return (0 until decor.childCount).count { decor.getChildAt(it) is BubbleView }
    }

    private fun <T : Activity> awaitResumed(type: Class<T>): T {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val found =
                onMain {
                    ActivityLifecycleMonitorRegistry
                        .getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance(type)
                        .firstOrNull()
                }
            if (found != null) {
                instrumentation.waitForIdleSync()
                return found
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("${type.simpleName} was not resumed within $TIMEOUT_MILLIS ms")
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
