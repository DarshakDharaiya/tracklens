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
}
