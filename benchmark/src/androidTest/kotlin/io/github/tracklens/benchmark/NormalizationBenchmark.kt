package io.github.tracklens.benchmark

import android.app.Application
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.TrackLens
import io.github.tracklens.TrackLensBundles
import io.github.tracklens.internal.ParamNormalizer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Caller-thread cost of logging (plan section 4.2). Targets: normalizing 10 params <= 5 us (Task 12); the whole
 * `log()` call p50 < 10 us and p99 < 100 us (Task 16 measures it again once the pipeline exists).
 */
@RunWith(AndroidJUnit4::class)
class NormalizationBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    /** A typical e-commerce event: 10 parameters of the common types. */
    private val tenParams: Map<String, Any?> =
        mapOf(
            "item_id" to "SKU-1042",
            "item_name" to "Trail running shoes",
            "item_category" to "footwear",
            "price" to 89.99,
            "quantity" to 2L,
            "currency" to "USD",
            "coupon" to "SPRING25",
            "in_stock" to true,
            "index" to 3,
            "value" to 179.98,
        )

    @Test
    fun normalizeTenParamMap() {
        benchmarkRule.measureRepeated { ParamNormalizer.normalize(tenParams) }
    }

    @Test
    fun normalizeTenParamBundle() {
        val bundle = TrackLensBundles.toBundle(tenParams)
        benchmarkRule.measureRepeated { ParamNormalizer.normalize(bundle) }
    }

    @Test
    fun logTenParamsThroughTheFacade() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        TrackLens.install(app)
        benchmarkRule.measureRepeated { TrackLens.log("add_to_cart", tenParams) }
    }
}
