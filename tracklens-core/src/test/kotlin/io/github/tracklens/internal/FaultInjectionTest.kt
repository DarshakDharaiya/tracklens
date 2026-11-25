package io.github.tracklens.internal

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.TrackLens
import io.github.tracklens.trackLensConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 11 definition of done: with a failing component behind the facade, no exception reaches the caller, and the
 * component's breaker turns it off after five consecutive failures.
 */
@RunWith(RobolectricTestRunner::class)
class FaultInjectionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun reset() = TrackLensImpl.resetForTests()

    @Test
    fun failingPipelineNeverThrowsIntoTheCallerAndTripsAfterFiveFailures() {
        TrackLens.install(app)
        val sink = FailingSink(IllegalStateException("pipeline exploded"))
        TrackLensImpl.eventSink = sink

        repeat(4) {
            TrackLens.log("event")
            TrackLens.log("event", mapOf("k" to "v"))
            TrackLens.log("event", Bundle())
            TrackLens.setUserProperty("p", "v")
            TrackLens.setUserId("u")
            TrackLens.setScreen("Home")
        }

        assertEquals("the sink is not called once the breaker is open", 5, sink.calls)
        assertTrue(TrackLensImpl.pipelineBreaker.isOpen)
        assertEquals(5, Diagnostics.snapshot().count { it.component == Component.PIPELINE && it.error != null })
        assertTrue(Diagnostics.snapshot().any { "PIPELINE turned off after 5 consecutive failures" in it.message })
        assertTrue("only the failing component is off", TrackLensImpl.isEnabled)
    }

    @Test
    fun fatalErrorFromAComponentEngagesTheKillSwitch() {
        TrackLens.install(app)
        val sink = FailingSink(OutOfMemoryError("simulated"))
        TrackLensImpl.eventSink = sink

        TrackLens.log("first")
        TrackLens.log("second")

        assertFalse(TrackLens.isEnabled)
        assertEquals("disabled TrackLens stops calling components", 1, sink.calls)
    }

    @Test
    fun killSwitchStopsCallsBeforeTheyReachComponents() {
        TrackLens.install(app, trackLensConfig { enabled(false) })
        val sink = FailingSink(IllegalStateException("must not be reached"))
        TrackLensImpl.eventSink = sink

        TrackLens.log("while_disabled")
        TrackLens.isEnabled = true
        TrackLens.log("after_enabling")

        assertEquals(1, sink.calls)
    }

    @Test
    fun callsBeforeInstallAreIgnored() {
        val sink = FailingSink(IllegalStateException("must not be reached"))
        TrackLensImpl.eventSink = sink

        TrackLens.log("too_early")

        assertEquals(0, sink.calls)
    }

    @Test
    fun wrapStillForwardsWhenThePipelineFails() {
        TrackLens.install(app)
        TrackLensImpl.eventSink = FailingSink(IllegalStateException("pipeline exploded"))
        val forwarded = mutableListOf<String>()

        repeat(10) { TrackLens.wrap { name, _ -> forwarded += name }.logEvent("e$it", emptyMap()) }

        assertEquals(10, forwarded.size)
    }

    @Test
    fun installWarnsWhenTheDebugArtifactRunsInANonDebuggableBuild() {
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        try {
            TrackLens.install(app)

            assertTrue(
                Diagnostics.snapshot().any { it.level == DiagnosticLevel.ERROR && it.message == TrackLensImpl.NON_DEBUGGABLE_MESSAGE },
            )
            assertTrue("the warning does not disable TrackLens", TrackLensImpl.isActive)
        } finally {
            app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        }
    }

    @Test
    fun installInTheMainProcessActivatesTrackLens() {
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        TrackLens.install(app)

        assertTrue(TrackLensImpl.isActive)
        assertFalse(Diagnostics.snapshot().any { it.message == TrackLensImpl.NON_DEBUGGABLE_MESSAGE })
    }

    private class FailingSink(
        private val failure: Throwable,
    ) : EventSink {
        var calls = 0

        private fun fail(): Nothing {
            calls++
            throw failure
        }

        override fun log(
            name: String,
            params: Map<String, Any?>,
            source: String?,
        ) = fail()

        override fun log(
            name: String,
            params: Bundle,
            source: String?,
        ) = fail()

        override fun setUserProperty(
            name: String,
            value: String?,
            source: String?,
        ) = fail()

        override fun setUserId(
            userId: String?,
            source: String?,
        ) = fail()

        override fun setScreen(
            name: String,
            screenClass: String?,
        ) = fail()
    }
}
