package io.github.tracklens.ui.live

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tracklens.DarkMode
import io.github.tracklens.OverlayMode
import io.github.tracklens.ThemeConfig
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.ui.TrackLensActivity
import io.github.tracklens.ui.UiRuntime
import io.github.tracklens.ui.live.LiveFixtures.event
import io.github.tracklens.ui.live.LiveFixtures.session
import io.github.tracklens.ui.overlay.FakeRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** Task 20's Compose UI tests: the Live list and event detail, through the real activity and navigation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LiveScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var runtime: FakeRuntime
    private var scenario: ActivityScenario<TrackLensActivity>? = null

    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun pinTheTimeZone() {
        // Screenshots show times of day; render them the same on every machine and on CI.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        scenario?.close()
        UiRuntime.current = null
        TimeZone.setDefault(originalTimeZone)
    }

    private fun open(
        events: List<io.github.tracklens.TrackedEvent> = session,
        theme: ThemeConfig = ThemeConfig.DEFAULT,
    ) {
        runtime =
            FakeRuntime(
                TrackLensConfig
                    .Builder()
                    .bubble { overlayMode(OverlayMode.NONE) }
                    .theme(theme)
                    .build(),
            )
        runtime.publish(events)
        UiRuntime.current = runtime
        scenario = ActivityScenario.launch(TrackLensActivity::class.java)
        compose.waitForIdle()
    }

    private fun row(sequence: Long): SemanticsNodeInteraction = compose.onNodeWithText("#$sequence")

    private fun top(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.top

    private fun menuItem(text: String) = compose.onNode(hasText(text, substring = true) and hasAnyAncestor(isPopup()))

    @Test
    fun listsEventsNewestFirst() {
        open()

        for (sequence in 1L..5L) row(sequence).assertIsDisplayed()
        assertTrue("newest on top", top(row(5)) < top(row(4)))
        assertTrue(top(row(2)) < top(row(1)))
        compose.onNodeWithText("5 events").assertExists()
    }

    @Test
    fun searchNarrowsTheListAndCanBeCleared() {
        open()

        compose.onNode(hasSetTextAction()).performTextInput("sku-2")

        row(3).assertExists()
        for (sequence in listOf(1L, 2L, 4L, 5L)) row(sequence).assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        row(1).assertExists()
    }

    @Test
    fun chipsFilterByKindAndIssues() {
        open()

        compose.onNodeWithText("Kind").performClick()
        menuItem("Screen view").performClick()
        compose.waitForIdle()
        row(1).assertExists()
        row(2).assertDoesNotExist()

        // "Any" clears the kind filter and closes the menu; then only events with issues remain.
        menuItem("Any").performClick()
        compose.onNodeWithText("Has issues").performClick()
        row(1).assertDoesNotExist()
        row(3).assertExists()
        row(5).assertExists()
    }

    @Test
    fun noMatchesOffersToClearTheFilters() {
        open()

        compose.onNode(hasSetTextAction()).performTextInput("nothing like this")

        compose.onNodeWithText("No events match", substring = true).assertExists()
        compose.onNodeWithText("Clear filters").performClick()
        row(5).assertExists()
    }

    @Test
    fun pauseHoldsNewEventsBackUntilResume() {
        open()

        compose.onNodeWithContentDescription("Pause").performClick()
        runtime.publish(session + event(6, "purchase") + event(7, "purchase_done"))
        compose.waitForIdle()

        row(6).assertDoesNotExist()
        compose.onNodeWithText("Paused · 2 new events held back").assertExists()
        compose.onNodeWithText("Resume").performClick()
        row(7).assertExists()
    }

    @Test
    fun clearEmptiesTheListThroughTheRuntime() {
        open()

        compose.onNodeWithContentDescription("Clear the live list").performClick()
        compose.waitForIdle()

        assertTrue("clearLive" in runtime.calls)
        compose.onNodeWithText("No events yet", substring = true).assertExists()
    }

    @Test
    fun showsDroppedCallsAndMarksEventsRead() {
        open()
        runtime.publish(session, dropped = 12, unread = 3)
        compose.waitForIdle()

        compose.onNodeWithText("12 logging calls were dropped", substring = true).assertExists()
        assertTrue("markAllRead" in runtime.calls)
    }

    @Test
    fun rowsSayHowManyIssuesAndHowBad() {
        open()

        compose.onNode(hasContentDescription("2 issues, worst: Error")).assertExists()
        compose.onNode(hasContentDescription("1 issue, worst: Warning")).assertExists()
    }

    @Test
    fun detailShowsParamsIssuesAndCopiesJson() {
        open()
        row(3).performClick()

        compose.onNodeWithText("SKU-2").assertExists()
        compose.onNodeWithText("redacted").assertExists()
        compose.onNodeWithText("list").assertExists()
        compose.onNodeWithText("Problem 0 with add_to_cart").assertExists()
        compose.onNodeWithText("Copy JSON").performScrollTo().performClick()
        compose.waitForIdle()

        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        val copied =
            clipboard.primaryClip!!
                .getItemAt(0)
                .text
                .toString()
        assertEquals(EventJson.encode(session[2]), copied)
        compose.onNodeWithText("Copied").assertExists()
    }

    @Test
    fun previousAndNextStepThroughEventsAndBackReturnsToTheList() {
        open()
        row(3).performClick()

        compose.onNodeWithContentDescription("Next event").performClick()
        compose.onNodeWithText("plan").assertExists()
        compose.onNodeWithContentDescription("Previous event").performClick()
        compose.onNodeWithContentDescription("Previous event").performClick()
        compose.onNodeWithText("view_item").assertExists()

        // Previous/Next replace the screen, so one Back returns to the list.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("5 events").assertExists()
    }

    @Test
    fun anEventThatLeftTheBufferIsExplained() {
        open()
        row(2).performClick()

        runtime.clearLive()
        compose.waitForIdle()

        compose.onNodeWithText("Event #2 is no longer in the live list", substring = true).assertExists()
    }

    @Test
    fun screenshotLiveList() {
        open()
        capture("tracklens_live_list_light")
    }

    @Test
    fun screenshotLiveFilteredDark() {
        open(theme = ThemeConfig.Builder().darkMode(DarkMode.DARK).build())
        compose.onNodeWithText("Has issues").performClick()
        compose.waitForIdle()
        capture("tracklens_live_filtered_dark")
    }

    @Test
    fun screenshotEventDetail() {
        open()
        row(3).performClick()
        compose.waitForIdle()
        capture("tracklens_event_detail_light")
    }

    private fun capture(name: String) {
        requireNotNull(scenario).onActivity { it.window.decorView.captureRoboImage("src/test/screenshots/$name.png") }
    }
}
