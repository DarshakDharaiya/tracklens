package io.github.tracklens.internal

import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns one component off after [threshold] consecutive failures, so a broken part (for example storage) stops
 * failing on every call while the rest of TrackLens keeps working. A success resets the count. Once open, the
 * breaker stays open until [reset] (a process restart, or `TrackLens.clearAllData` in a later task).
 */
internal class CircuitBreaker(
    val component: Component,
    val threshold: Int = DEFAULT_THRESHOLD,
) {
    private val consecutiveFailures = AtomicInteger()

    @Volatile
    var isOpen: Boolean = false
        private set

    /** Runs [block] unless the breaker is open; returns [fallback] if it is open or [block] throws. */
    @Suppress("TooGenericExceptionCaught") // Any failure counts towards opening the breaker.
    inline fun <T> run(
        fallback: T,
        block: () -> T,
    ): T {
        if (isOpen) return fallback
        return try {
            block().also { recordSuccess() }
        } catch (failure: Throwable) {
            recordFailure(failure)
            fallback
        }
    }

    fun recordSuccess() {
        if (consecutiveFailures.get() != 0) consecutiveFailures.set(0)
    }

    fun recordFailure(failure: Throwable) {
        handleInternalFailure(component, "$component failed", failure)
        if (consecutiveFailures.incrementAndGet() >= threshold && !isOpen) {
            isOpen = true
            Diagnostics.report(
                component,
                DiagnosticLevel.ERROR,
                "$component turned off after $threshold consecutive failures; the rest of TrackLens keeps working.",
            )
        }
    }

    fun reset() {
        consecutiveFailures.set(0)
        isOpen = false
    }

    companion object {
        const val DEFAULT_THRESHOLD = 5
    }
}
