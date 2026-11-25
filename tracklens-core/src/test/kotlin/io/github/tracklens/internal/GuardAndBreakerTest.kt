package io.github.tracklens.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuardAndBreakerTest {
    @Before
    @After
    fun reset() = TrackLensImpl.resetForTests()

    @Test
    fun guardReturnsTheFallbackAndRecordsTheFailure() {
        val result = guard("probe", "fallback") { error("boom") }

        assertEquals("fallback", result)
        val entry = Diagnostics.snapshot().single()
        assertEquals(Component.API, entry.component)
        assertEquals("TrackLens.probe failed", entry.message)
    }

    @Test
    fun guardCatchesLinkageErrorsFromDependencyMismatches() {
        guard("probe") { throw NoSuchMethodError("androidx.core.SomeClass.newMethod()") }

        assertTrue(requireNotNull(Diagnostics.snapshot().single().error).startsWith("java.lang.NoSuchMethodError"))
        assertTrue("non-fatal errors keep TrackLens enabled", TrackLensImpl.isEnabled)
    }

    @Test
    fun fatalVmErrorsEngageTheKillSwitch() {
        guard("probe") { throw OutOfMemoryError("simulated") }

        assertFalse(TrackLensImpl.isEnabled)
        assertTrue(Diagnostics.snapshot().any { "turned itself off" in it.message })
    }

    @Test
    fun guardRestoresTheInterruptFlag() {
        try {
            guard("probe") { throw InterruptedException() }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted() // clear it for the next test
        }
    }

    @Test
    fun breakerOpensAfterFiveConsecutiveFailures() {
        val breaker = CircuitBreaker(Component.STORAGE)
        var attempts = 0

        repeat(10) {
            breaker.run(Unit) {
                attempts++
                error("write failed")
            }
        }

        assertTrue(breaker.isOpen)
        assertEquals("open breakers stop calling the component", 5, attempts)
        assertEquals(1, Diagnostics.snapshot().count { "turned off after 5 consecutive failures" in it.message })
    }

    @Test
    fun aSuccessResetsTheFailureCount() {
        val breaker = CircuitBreaker(Component.STORAGE)

        repeat(4) { breaker.run(Unit) { error("fail") } }
        breaker.run(Unit) { }
        repeat(4) { breaker.run(Unit) { error("fail") } }

        assertFalse(breaker.isOpen)
    }

    @Test
    fun openBreakerReturnsTheFallbackUntilReset() {
        val breaker = CircuitBreaker(Component.EXPORT, threshold = 1)
        breaker.run(Unit) { error("fail") }

        assertEquals("fallback", breaker.run("fallback") { "real" })
        breaker.reset()
        assertEquals("real", breaker.run("fallback") { "real" })
    }

    @Test
    fun onceCallbacksDeliverOnlyTheFirstAnswer() {
        val completions = mutableListOf<Boolean>()
        val completion = OnceCompletionCallback { completions += it }
        completion.onComplete(true)
        completion.onComplete(false)

        val failures = mutableListOf<String>()
        val export =
            OnceExportCallback(
                object : io.github.tracklens.ExportCallback {
                    override fun onExported(
                        uri: android.net.Uri,
                        mimeType: String,
                    ) = error("must not be called")

                    override fun onFailed(reason: String) {
                        failures += reason
                    }
                },
            )
        export.onFailed("first")
        export.onFailed("second")

        assertEquals(listOf(true), completions)
        assertEquals(listOf("first"), failures)
    }
}
