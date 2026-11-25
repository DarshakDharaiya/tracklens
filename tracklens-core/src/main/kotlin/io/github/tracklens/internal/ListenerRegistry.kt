package io.github.tracklens.internal

import io.github.tracklens.Subscription
import io.github.tracklens.TrackLensListener
import io.github.tracklens.TrackedEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * Listeners registered with `TrackLens.addListener`. Lives outside the pipeline so listeners added before
 * `install` still receive events. A listener that throws is reported in diagnostics and kept; its exception never
 * reaches the pipeline or crashes the executor's thread.
 */
internal class ListenerRegistry(
    private val defaultExecutor: Executor,
) {
    /** Identity-based, so cancelling removes exactly this registration even if a listener is added twice. */
    private class Registration(
        val listener: TrackLensListener,
        val executor: Executor,
    )

    private val registrations = CopyOnWriteArrayList<Registration>()

    fun add(
        listener: TrackLensListener,
        executor: Executor?,
    ): Subscription {
        val registration = Registration(listener, executor ?: defaultExecutor)
        registrations += registration
        return Subscription { registrations.remove(registration) }
    }

    @Suppress("TooGenericExceptionCaught") // Host listener and executor code must not break the pipeline.
    fun dispatch(event: TrackedEvent) {
        for (registration in registrations) {
            try {
                registration.executor.execute {
                    try {
                        registration.listener.onEvent(event)
                    } catch (failure: Throwable) {
                        handleInternalFailure(Component.LISTENERS, "A TrackLensListener threw", failure)
                    }
                }
            } catch (failure: Throwable) {
                handleInternalFailure(Component.LISTENERS, "A listener's executor rejected an event", failure)
            }
        }
    }

    fun clear() = registrations.clear()
}
