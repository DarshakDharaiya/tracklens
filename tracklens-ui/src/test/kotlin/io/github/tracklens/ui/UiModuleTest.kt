package io.github.tracklens.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.OverlayMode
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.ui.overlay.FakeRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UiModuleTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val module = UiModule { it.run() }

    @Before
    fun giveTheAppALauncherActivity() {
        val launcher =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = application.packageName
                        name = "com.example.MainActivity"
                    }
            }
        shadowOf(application.packageManager).addResolveInfoForIntent(launcherIntent(), launcher)
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(application.packageName)

    @After
    fun tearDown() {
        UiRuntime.current = null
        ShortcutManagerCompat.removeAllDynamicShortcuts(application)
    }

    private fun config(shortcut: Boolean = true) =
        TrackLensConfig
            .Builder()
            .bubble {
                overlayMode(OverlayMode.NONE)
                showAppShortcut(shortcut)
            }.build()

    @Test
    fun installMakesTheRuntimeAvailableToTrackLensScreens() {
        val runtime = FakeRuntime(config())

        module.onInstall(application, runtime)

        assertSame(runtime, UiRuntime.current)
    }

    @Test
    fun openPanelStartsTrackLensInItsOwnTask() {
        assertTrue(module.openPanel(application))

        val started = shadowOf(application).nextStartedActivity
        assertEquals(TrackLensActivity::class.java.name, started.component?.className)
        assertTrue("own task", started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun publishesTheLauncherShortcut() {
        module.onInstall(application, FakeRuntime(config()))

        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(application).single()
        assertEquals(LauncherShortcut.ID, shortcut.id)
        assertEquals("TrackLens", shortcut.shortLabel)
        assertEquals(TrackLensActivity::class.java.name, shortcut.intent.component?.className)
        assertEquals(Intent.ACTION_VIEW, shortcut.intent.action)
    }

    @Test
    fun anAppWithoutALauncherActivityGetsNoShortcutAndNoError() {
        shadowOf(application.packageManager).removeResolveInfosForIntent(launcherIntent(), application.packageName)
        val runtime = FakeRuntime(config())

        module.onInstall(application, runtime)

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(application).isEmpty())
        assertTrue(runtime.failures.isEmpty())
    }

    @Test
    fun turningTheShortcutOffRemovesAnEarlierOne() {
        module.onInstall(application, FakeRuntime(config()))

        module.onInstall(application, FakeRuntime(config(shortcut = false)))

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(application).isEmpty())
    }
}
