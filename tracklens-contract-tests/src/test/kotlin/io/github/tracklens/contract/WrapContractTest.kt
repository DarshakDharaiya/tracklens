package io.github.tracklens.contract

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.AnalyticsDelegate
import io.github.tracklens.TrackLens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `wrap()` is the one call that must behave identically in both artifacts: the host's analytics SDK receives
 * exactly what the app sent, and the SDK's own failures reach the app unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class WrapContractTest {
    private val sdk = RecordingDelegate()

    @Before
    fun install() {
        TrackLens.install(ApplicationProvider.getApplicationContext<Application>())
    }

    @After
    fun restoreKillSwitch() {
        TrackLens.isEnabled = true
    }

    @Test
    fun forwardsTheOriginalParamsInstance() {
        // TrackLens redacts "email" for its own records; the SDK must still get the caller's map, untouched.
        val params = mapOf("email" to "jane@example.com", "price" to 9.99, "items" to listOf(mapOf("id" to "SKU-1")))

        TrackLens.wrap(source = "firebase", delegate = sdk).logEvent("purchase", params)

        assertEquals("purchase", sdk.events.single().first)
        assertSame(params, sdk.events.single().second)
        assertEquals(mapOf("email" to "jane@example.com", "price" to 9.99, "items" to listOf(mapOf("id" to "SKU-1"))), params)
    }

    @Test
    fun forwardsEveryCallExactlyOnceInOrder() {
        val wrapped = TrackLens.wrap(delegate = sdk)

        wrapped.logEvent("first", emptyMap())
        wrapped.setUserProperty("tier", "gold")
        wrapped.setUserProperty("tier", null)
        wrapped.setUserId("user-42")
        wrapped.setUserId(null)
        wrapped.logEvent("second", mapOf("n" to 2))

        assertEquals(listOf("first", "second"), sdk.events.map { it.first })
        assertEquals(listOf("tier" to "gold", "tier" to null), sdk.userProperties)
        assertEquals(listOf("user-42", null), sdk.userIds)
    }

    @Test
    fun forwardsWhileTrackLensIsDisabled() {
        TrackLens.isEnabled = false

        TrackLens.wrap(delegate = sdk).logEvent("still_sent", emptyMap())

        assertEquals("still_sent", sdk.events.single().first)
    }

    @Test
    fun propagatesSdkExceptionsUnchanged() {
        val failure = IllegalStateException("SDK not initialized")
        val throwing =
            object : AnalyticsDelegate {
                override fun logEvent(
                    name: String,
                    params: Map<String, Any?>,
                ): Unit = throw failure

                override fun setUserProperty(
                    name: String,
                    value: String?,
                ): Unit = throw failure

                override fun setUserId(userId: String?): Unit = throw failure
            }
        val wrapped = TrackLens.wrap(delegate = throwing)

        assertSame(failure, assertThrows(IllegalStateException::class.java) { wrapped.logEvent("e", emptyMap()) })
        assertSame(failure, assertThrows(IllegalStateException::class.java) { wrapped.setUserProperty("p", "v") })
        assertSame(failure, assertThrows(IllegalStateException::class.java) { wrapped.setUserId("u") })
    }

    @Test
    fun keepsDefaultMethodsSafeForLambdaDelegates() {
        val names = mutableListOf<String>()
        val wrapped = TrackLens.wrap { name, _ -> names += name }

        wrapped.setUserProperty("tier", "gold")
        wrapped.setUserId("user-42")
        wrapped.logEvent("only_this", emptyMap())

        assertEquals(listOf("only_this"), names)
    }

    @Test
    fun noopReturnsTheDelegateItself() {
        assumeTrue("Only the no-op promises to return the same instance", TrackLens.isNoOp)

        assertSame(sdk, TrackLens.wrap(delegate = sdk))
        assertSame(sdk, TrackLens.wrap(source = "firebase", delegate = sdk))
    }
}
