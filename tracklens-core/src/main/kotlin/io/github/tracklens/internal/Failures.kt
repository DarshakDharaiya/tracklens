package io.github.tracklens.internal

/**
 * Runs [block] for a public TrackLens call and returns its result, or [fallback] if anything at all is thrown,
 * including `LinkageError`s caused by dependency mismatches on the host's classpath. TrackLens never throws into
 * the host app (plan section 4.7).
 *
 * Never use it around host code whose exceptions must reach the caller, such as the delegate passed to
 * `TrackLens.wrap`.
 */
@Suppress("TooGenericExceptionCaught") // Catching everything is the point.
internal inline fun <T> guard(
    operation: String,
    fallback: T,
    block: () -> T,
): T =
    try {
        block()
    } catch (failure: Throwable) {
        handleInternalFailure(Component.API, "TrackLens.$operation failed", failure)
        fallback
    }

/** [guard] for calls without a result. */
internal inline fun guard(
    operation: String,
    block: () -> Unit,
) = guard(operation, Unit, block)

/**
 * Common handling for every caught internal failure: records it, restores a swallowed interrupt, and engages the
 * kill switch on fatal VM errors (out of memory, stack overflow), since continuing could make things worse.
 */
internal fun handleInternalFailure(
    component: Component,
    message: String,
    failure: Throwable,
) {
    if (failure is InterruptedException) Thread.currentThread().interrupt()
    Diagnostics.report(component, DiagnosticLevel.ERROR, message, failure)
    if (failure is VirtualMachineError) {
        TrackLensImpl.isEnabled = false
        Diagnostics.report(
            component,
            DiagnosticLevel.ERROR,
            "Fatal ${failure.javaClass.simpleName}: TrackLens turned itself off (TrackLens.isEnabled = false).",
        )
    }
}
