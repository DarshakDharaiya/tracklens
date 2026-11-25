package io.github.tracklens.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tracklens.TrackLens
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Caller-thread cost of every public logging path with the full pipeline running (plan section 4.2, Task 16).
 * Target: p50 < 10 us per call. Each test ends by checking that nothing was dropped.
 */
@RunWith(AndroidJUnit4::class)
class LogOverheadBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val counter = DeliveryCounter()

    @Before
    fun setUp() {
        installTrackLens()
        counter.start()
    }

    @After
    fun tearDown() {
        counter.stop()
        TrackLens.isEnabled = true
    }

    @Test
    fun logWithoutParams() = measure { TrackLens.log("app_open") }

    @Test
    fun logTenParamMap() = measure { TrackLens.log("add_to_cart", TEN_PARAMS) }

    @Test
    fun logTenParamBundle() = measure { TrackLens.log("add_to_cart", TEN_PARAM_BUNDLE) }

    @Test
    fun wrappedLogEventTenParams() {
        val wrapped = TrackLens.wrap { _, _ -> }
        measure { wrapped.logEvent("add_to_cart", TEN_PARAMS) }
    }

    /** The kill switch: the cost a disabled TrackLens adds to every call. */
    @Test
    fun logTenParamMapWhileDisabled() {
        TrackLens.isEnabled = false
        benchmarkRule.measureRepeated { TrackLens.log("add_to_cart", TEN_PARAMS) }
    }

    private inline fun measure(crossinline call: () -> Unit) {
        benchmarkRule.measureRepeated {
            call()
            counter.logged++
            if (counter.logged % DeliveryCounter.DRAIN_EVERY == 0L) {
                runWithMeasurementDisabled { counter.drain() }
            }
        }
        counter.drain()
    }
}
