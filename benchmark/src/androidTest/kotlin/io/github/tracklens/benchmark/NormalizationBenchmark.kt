package io.github.tracklens.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tracklens.internal.ParamNormalizer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parameter normalization alone (Task 12 target: 10 params <= 5 us). The whole `log()` call is measured by
 * [LogOverheadBenchmark] and [LogLatencyDistributionTest].
 */
@RunWith(AndroidJUnit4::class)
class NormalizationBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun normalizeTenParamMap() {
        benchmarkRule.measureRepeated { ParamNormalizer.normalize(TEN_PARAMS) }
    }

    @Test
    fun normalizeTenParamBundle() {
        benchmarkRule.measureRepeated { ParamNormalizer.normalize(TEN_PARAM_BUNDLE) }
    }
}
