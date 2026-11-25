package io.github.tracklens.benchmark

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.TrackLens
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Per-call latency of `TrackLens.log(name, 10 params)`: every call timed on its own, so tails (GC, contention with
 * the pipeline thread) are visible, unlike benchmark averages. Targets (Task 16): p50 < 10 us, p99 < 100 us.
 *
 * Writes `tracklens-log-latency.json` next to the androidx.benchmark results.
 */
@RunWith(AndroidJUnit4::class)
class LogLatencyDistributionTest {
    private val counter = DeliveryCounter()

    @Before
    fun setUp() {
        installTrackLens()
        counter.start()
    }

    @After
    fun tearDown() = counter.stop()

    @Test
    fun perCallLatencyOfLogWithTenParams() {
        repeat(WARMUP_CALLS) { logOnce() }
        counter.drain()

        val samples = LongArray(MEASURED_CALLS)
        for (index in samples.indices) {
            val start = System.nanoTime()
            TrackLens.log("add_to_cart", TEN_PARAMS)
            samples[index] = System.nanoTime() - start
            afterCall()
        }
        counter.drain()

        samples.sort()
        val result =
            JSONObject()
                .put("benchmark", "TrackLens.log(name, 10-param Map), per call")
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("calls", MEASURED_CALLS)
                .put("warmupCalls", WARMUP_CALLS)
                .put("p50Ns", percentile(samples, 0.50))
                .put("p90Ns", percentile(samples, 0.90))
                .put("p99Ns", percentile(samples, 0.99))
                .put("p999Ns", percentile(samples, 0.999))
                .put("maxNs", samples.last())
                .put("droppedEvents", 0)
        File(outputDirectory(), "tracklens-log-latency.json").writeText(result.toString(2))

        assertTrue("p50 ${result.getLong("p50Ns")} ns must be < 10 us", result.getLong("p50Ns") < P50_TARGET_NS)
        assertTrue("p99 ${result.getLong("p99Ns")} ns must be < 100 us", result.getLong("p99Ns") < P99_TARGET_NS)
    }

    private fun logOnce() {
        TrackLens.log("add_to_cart", TEN_PARAMS)
        afterCall()
    }

    private fun afterCall() {
        counter.logged++
        if (counter.logged % DeliveryCounter.DRAIN_EVERY == 0L) counter.drain()
    }

    private fun percentile(
        sorted: LongArray,
        fraction: Double,
    ): Long = sorted[((sorted.size - 1) * fraction).toInt()]

    /** The directory androidx.benchmark also writes to; Gradle pulls it back into build/outputs. */
    private fun outputDirectory(): File {
        val fromArguments = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory =
            fromArguments?.let(::File)
                ?: requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
        return directory.apply { mkdirs() }
    }

    private companion object {
        const val WARMUP_CALLS = 50_000
        const val MEASURED_CALLS = 200_000
        const val P50_TARGET_NS = 10_000L
        const val P99_TARGET_NS = 100_000L
    }
}
