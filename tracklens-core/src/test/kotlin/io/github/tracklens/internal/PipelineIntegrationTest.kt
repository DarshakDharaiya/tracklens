package io.github.tracklens.internal

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.TrackLens
import io.github.tracklens.TrackedEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/** The public facade feeding the real pipeline, as a host app would use it. */
@RunWith(RobolectricTestRunner::class)
class PipelineIntegrationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val received = java.util.Collections.synchronizedList(ArrayList<TrackedEvent>())

    @Before
    @After
    fun reset() = TrackLensImpl.resetForTests()

    @Test
    fun loggedCallsArriveAsNumberedEventsOfTheCurrentSession() {
        TrackLens.addListener({ received += it }, Executor { it.run() })
        TrackLens.install(app)

        TrackLens.log("app_open")
        TrackLens.setScreen("Home")
        TrackLens.log("view_item", mapOf("item_id" to "SKU-1", "price" to 9.99), source = "firebase")
        TrackLens.log("legacy", Bundle().apply { putLong("n", 3L) })
        TrackLens.wrap(source = "amplitude") { _, _ -> }.logEvent("wrapped", mapOf("k" to "v"))
        assertTrue(requireNotNull(TrackLensImpl.pipeline).awaitIdle())

        assertEquals(listOf("app_open", "Home", "view_item", "legacy", "wrapped"), received.map { it.name })
        assertEquals((1L..5L).toList(), received.map { it.sequence })
        assertEquals(mapOf("item_id" to "SKU-1", "price" to 9.99), received[2].params)
        assertEquals(mapOf("n" to 3L), received[3].params)
        assertEquals("amplitude", received[4].source)
        assertEquals("Home", received[4].screen)
        val sessionId = requireNotNull(TrackLens.currentSessionId)
        assertTrue(received.all { it.sessionId == sessionId })
    }

    @Test
    fun theLiveSnapshotFollowsTheLog() {
        TrackLens.install(app)
        repeat(3) { TrackLens.log("e$it") }

        val pipeline = requireNotNull(TrackLensImpl.pipeline)
        assertTrue(pipeline.awaitIdle())

        assertEquals(
            listOf("e0", "e1", "e2"),
            pipeline.snapshot.value.events
                .map { it.name },
        )
        assertEquals(3, pipeline.snapshot.value.unreadCount)
    }

    @Test
    fun theKillSwitchKeepsEventsOutOfThePipeline() {
        TrackLens.install(app)
        TrackLens.isEnabled = false
        TrackLens.log("while_disabled")
        TrackLens.isEnabled = true
        TrackLens.log("after")

        val pipeline = requireNotNull(TrackLensImpl.pipeline)
        assertTrue(pipeline.awaitIdle())

        assertEquals(
            listOf("after"),
            pipeline.snapshot.value.events
                .map { it.name },
        )
    }

    /**
     * Task 15: one wrap() call, two views. The SDK gets the caller's own map with the real values; TrackLens keeps
     * only the redacted copy.
     */
    @Test
    fun wrapForwardsOriginalValuesWhileTrackLensKeepsRedactedOnes() {
        TrackLens.addListener({ received += it }, Executor { it.run() })
        TrackLens.install(app)
        val sdkEvents = mutableListOf<Map<String, Any?>>()
        val sdkUserIds = mutableListOf<String?>()
        val sdk =
            object : io.github.tracklens.AnalyticsDelegate {
                override fun logEvent(
                    name: String,
                    params: Map<String, Any?>,
                ) {
                    sdkEvents += params
                }

                override fun setUserId(userId: String?) {
                    sdkUserIds += userId
                }
            }
        val params = mapOf("email" to "jane@example.com", "plan" to "pro")

        val analytics = TrackLens.wrap(source = "firebase", delegate = sdk)
        analytics.logEvent("sign_up", params)
        analytics.setUserId("user-42")
        assertTrue(requireNotNull(TrackLensImpl.pipeline).awaitIdle())

        assertTrue("the SDK gets the caller's own map", sdkEvents.single() === params)
        assertEquals(listOf("user-42"), sdkUserIds)
        assertEquals(mapOf("email" to "[REDACTED]", "plan" to "pro"), received[0].params)
        assertEquals(mapOf("user_id" to Redactor.hash("user-42")), received[1].params)
        assertEquals(listOf("firebase", "firebase"), received.map { it.source })
    }
}
