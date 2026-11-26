package io.github.tracklens.ui

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tracklens.DarkMode
import io.github.tracklens.OverlayMode
import io.github.tracklens.ThemeConfig
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.ui.overlay.FakeRuntime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Task 19's navigation tests: tabs, Back (including leaving TrackLens), recreation, and the not-installed state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TrackLensActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<TrackLensActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        UiRuntime.current = null
    }

    private fun launch(theme: ThemeConfig = ThemeConfig.DEFAULT): ActivityScenario<TrackLensActivity> {
        UiRuntime.current =
            FakeRuntime(
                TrackLensConfig
                    .Builder()
                    .bubble { overlayMode(OverlayMode.NONE) }
                    .theme(theme)
                    .build(),
            )
        return ActivityScenario.launch(TrackLensActivity::class.java).also { scenario = it }
    }

    private fun assertSelectedTab(label: String) {
        compose.onNode(isSelected()).assert(hasText(label))
    }

    private fun pressBack(scenario: ActivityScenario<TrackLensActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun opensOnLive() {
        launch()

        assertSelectedTab("Live")
        for (tab in listOf("Sessions", "Plan", "More")) compose.onNodeWithText(tab).assertExists()
    }

    @Test
    fun backFromAnotherTabReturnsToLiveThenLeaves() {
        val scenario = launch()

        compose.onNodeWithText("Sessions").performClick()
        assertSelectedTab("Sessions")

        pressBack(scenario)
        assertSelectedTab("Live")

        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue("Back at Live closes TrackLens", activity.isFinishing)
        }
    }

    @Test
    fun theBackStackSurvivesRecreation() {
        val scenario = launch()
        compose.onNodeWithText("Plan").performClick()

        scenario.recreate()

        assertSelectedTab("Plan")
        pressBack(scenario)
        assertSelectedTab("Live")
    }

    @Test
    fun withoutInstallItExplainsWhatToDo() {
        UiRuntime.current = null
        scenario = ActivityScenario.launch(TrackLensActivity::class.java)

        compose.onNodeWithText("TrackLens is not running", substring = true).assertExists()
    }

    @Test
    fun screenshotLight() {
        val scenario = launch()
        compose.waitForIdle()
        scenario.onActivity { it.window.decorView.captureRoboImage("src/test/screenshots/tracklens_shell_live_light.png") }
    }

    @Test
    fun screenshotDark() {
        val scenario = launch(ThemeConfig.Builder().darkMode(DarkMode.DARK).build())
        compose.onNodeWithText("Sessions").performClick()
        compose.waitForIdle()
        scenario.onActivity { it.window.decorView.captureRoboImage("src/test/screenshots/tracklens_shell_sessions_dark.png") }
    }
}
