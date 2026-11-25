package io.github.tracklens.contract

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.ExportCallback
import io.github.tracklens.ExportFormat
import io.github.tracklens.TrackLens
import io.github.tracklens.trackLensConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * TrackLens must never throw into the host app (plan section 4.7), whatever it is given and in whatever order it is
 * called. Each test passes simply by returning; callbacks must arrive exactly once.
 */
@RunWith(RobolectricTestRunner::class)
class NeverThrowsContractTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun install() {
        TrackLens.install(app)
    }

    @After
    fun restoreKillSwitch() {
        TrackLens.isEnabled = true
    }

    @Test
    fun installIsIdempotent() {
        TrackLens.install(app)
        TrackLens.install(app, trackLensConfig { enabled(false) })
    }

    @Test
    fun loggingAcceptsEdgeCaseNamesAndParams() {
        val cyclicList = mutableListOf<Any?>().also { it.add(it) }
        val cyclicMap = HashMap<String, Any?>().also { it["self"] = it }
        val params =
            mapOf(
                "null" to null,
                "nested" to mapOf(1 to listOf(null, Any(), arrayOf("a", null))),
                "cyclicList" to cyclicList,
                "cyclicMap" to cyclicMap,
                "huge" to "x".repeat(100_000),
                "array" to intArrayOf(1, 2, 3),
            )
        for (name in listOf("", " ", "x".repeat(10_000), "名前_🙂", "line\nbreak")) {
            TrackLens.log(name)
            TrackLens.log(name, params)
            TrackLens.log(name, params, source = null)
            TrackLens.log(name, (1..5_000).associate { "k$it" to it }, source = "")
            TrackLens.log(name, nestedBundle(depth = 20))
        }
    }

    @Test
    fun loggingFromManyThreadsAtOnce() {
        val pool = Executors.newFixedThreadPool(THREADS)
        repeat(THREADS) { thread ->
            pool.execute { repeat(CALLS_PER_THREAD) { TrackLens.log("event_$thread", mapOf("i" to it)) } }
        }
        pool.shutdown()
        check(pool.awaitTermination(30, TimeUnit.SECONDS))
    }

    @Test
    fun userAndScreenCallsAcceptNullsAndEmptyStrings() {
        TrackLens.setUserProperty("", null)
        TrackLens.setUserProperty("tier", "gold", source = null)
        TrackLens.setUserId(null)
        TrackLens.setUserId("", source = "firebase")
        TrackLens.setScreen("")
        TrackLens.setScreen("Checkout", screenClass = null)
    }

    @Test
    fun sessionCallsWorkInAnyOrder() {
        TrackLens.endSession()
        TrackLens.setSessionLabel(null)
        TrackLens.setSessionNotes("notes before any session")
        TrackLens.startSession()
        TrackLens.startSession("Checkout regression - build 142")
        TrackLens.setSessionLabel("renamed")
        TrackLens.endSession()
        TrackLens.endSession()
        TrackLens.currentSessionId
    }

    @Test
    fun uiCallsDoNotThrow() {
        TrackLens.hideBubble()
        TrackLens.hideBubble()
        TrackLens.showBubble()
        TrackLens.openPanel(app)
    }

    @Test
    fun killSwitchCanBeToggledAnyTime() {
        TrackLens.isEnabled = false
        TrackLens.log("while_disabled")
        TrackLens.isEnabled = true
        TrackLens.isEnabled = true
    }

    @Test
    fun clearAllDataReportsExactlyOnce() {
        var calls = 0
        TrackLens.clearAllData { calls++ }
        TrackLens.clearAllData()
        idleMainLooper()
        assertEquals(1, calls)
    }

    @Test
    fun exportReportsExactlyOnceForEveryFormat() {
        for (format in ExportFormat.entries) {
            val callback = CountingExportCallback()
            TrackLens.exportSession(app, "no-such-session", format, callback)
            idleMainLooper()
            assertEquals("callbacks for $format", 1, callback.calls)
        }
    }

    @Test
    fun listenersCanBeCancelledMoreThanOnce() {
        val first = TrackLens.addListener({ })
        first.cancel()
        first.cancel()
        TrackLens.addListener({ }, Executor { it.run() }).cancel()
    }

    private fun nestedBundle(depth: Int): Bundle =
        Bundle().apply {
            putString("level", depth.toString())
            if (depth > 0) putBundle("child", nestedBundle(depth - 1))
            putParcelableArray("items", arrayOf(Bundle().apply { putLong("price", 1L) }))
        }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private class CountingExportCallback : ExportCallback {
        var calls = 0

        override fun onExported(
            uri: Uri,
            mimeType: String,
        ) {
            calls++
        }

        override fun onFailed(reason: String) {
            calls++
        }
    }

    private companion object {
        const val THREADS = 8
        const val CALLS_PER_THREAD = 1_000
    }
}
