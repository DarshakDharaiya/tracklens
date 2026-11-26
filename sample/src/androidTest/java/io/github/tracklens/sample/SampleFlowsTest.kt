package io.github.tracklens.sample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.EventKind
import io.github.tracklens.TrackLens
import io.github.tracklens.TrackedEvent
import io.github.tracklens.sample.DemoFlows.Flow
import io.github.tracklens.sample.sdk.FakeAmplitude
import io.github.tracklens.sample.sdk.FakeFirebaseAnalytics
import io.github.tracklens.sample.sdk.SampleAnalytics
import io.github.tracklens.sample.sdk.SdkCallLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The debug sample end to end: every flow reaches both TrackLens (tagged with its SDK) and the fake SDK, and
 * redaction changes only what TrackLens keeps, never what the SDK receives.
 */
@RunWith(AndroidJUnit4::class)
class SampleFlowsTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SampleApplication
    private val events = Collections.synchronizedList(ArrayList<TrackedEvent>())
    private var expected = CountDownLatch(0)
    private val subscription =
        TrackLens.addListener({ event ->
            events += event
            expected.countDown()
        }) { it.run() }

    @Before
    fun clear() = SdkCallLog.clear()

    @After
    fun stopListening() = subscription.cancel()

    private fun run(
        flow: Flow,
        expectedEvents: Int,
    ): List<TrackedEvent> {
        expected = CountDownLatch(expectedEvents)
        application.flows.run(flow)
        assertTrue("TrackLens recorded $expectedEvents events", expected.await(10, TimeUnit.SECONDS))
        return events.toList()
    }

    @Test
    fun theDebugBuildRecordsWithTrackLens() {
        assertTrue(TrackLens.isInstalled)
    }

    @Test
    fun checkoutReachesTrackLensAndTheSdkInOrder() {
        val recorded = run(Flow.CHECKOUT, expectedEvents = 7).filter { it.kind == EventKind.EVENT }

        val names = listOf("begin_checkout", "add_shipping_info", "add_payment_info", "purchase")
        assertEquals(names, recorded.map { it.name })
        assertTrue(recorded.all { it.source == SampleAnalytics.FIREBASE })
        assertEquals(listOf("Cart", "Checkout", "Checkout", "Confirmation"), recorded.map { it.screen })
        val sdkCalls =
            SdkCallLog.recent.value
                .filter { it.sdk == FakeFirebaseAnalytics.TAG }
                .asReversed()
        assertEquals(names, sdkCalls.map { it.description.split(' ')[1] })
        assertTrue("items reach the SDK as bundles", "items=[{item_id=SKU-SHOES" in sdkCalls.last().description)
    }

    @Test
    fun amplitudeEventsAreTaggedWithTheirSdk() {
        val recorded = run(Flow.PLAY_SONG, expectedEvents = 2).single { it.kind == EventKind.EVENT }

        assertEquals("Song Played", recorded.name)
        assertEquals(SampleAnalytics.AMPLITUDE, recorded.source)
        assertTrue(SdkCallLog.recent.value.any { it.sdk == FakeAmplitude.TAG && "Song Played" in it.description })
    }

    @Test
    fun signInRecordsUserIdAndPropertiesForBothSdks() {
        val recorded = run(Flow.SIGN_IN, expectedEvents = 6)

        assertEquals(2, recorded.count { it.kind == EventKind.USER_ID })
        assertEquals(
            setOf(SampleAnalytics.FIREBASE, SampleAnalytics.AMPLITUDE),
            recorded
                .filter {
                    it.kind == EventKind.USER_PROPERTY
                }.map { it.source }
                .toSet(),
        )
        assertTrue(SdkCallLog.recent.value.any { it.description == "setUserId user-42" && it.sdk == FakeAmplitude.TAG })
    }

    @Test
    fun redactionChangesWhatTrackLensKeepsNotWhatTheSdkReceives() {
        val recorded = run(Flow.SENSITIVE, expectedEvents = 2).single { it.kind == EventKind.EVENT }

        val kept = recorded.params.values.joinToString()
        assertFalse("email kept: $kept", "jane.doe@example.com" in kept)
        assertFalse("card kept: $kept", "4111 1111 1111 1111" in kept)
        assertFalse("password kept: $kept", "hunter2" in kept)
        val sent =
            SdkCallLog.recent.value
                .first { "profile_updated" in it.description }
                .description
        assertTrue("the SDK gets the real values: $sent", "jane.doe@example.com" in sent && "hunter2" in sent)
    }

    @Test
    fun aBurstArrivesCompleteAndInOrder() {
        val recorded = run(Flow.BURST, expectedEvents = 1_000).filter { it.name == "burst_event" }

        assertEquals((0L until 1_000L).toList(), recorded.map { it.params["index"] })
        assertEquals(1_000L, SdkCallLog.total.value)
    }
}
