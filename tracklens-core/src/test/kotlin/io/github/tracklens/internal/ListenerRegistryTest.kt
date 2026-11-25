package io.github.tracklens.internal

import io.github.tracklens.EventKind
import io.github.tracklens.TrackedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class ListenerRegistryTest {
    private val direct = Executor { it.run() }
    private val registry = ListenerRegistry(defaultExecutor = direct)
    private val event = TrackedEvent(1, 0, 0, "e", EventKind.EVENT, emptyMap(), null, "session", null, emptyList())

    @Before
    fun clearDiagnostics() = Diagnostics.clear()

    @Test
    fun aThrowingListenerDoesNotStopTheOthers() {
        val received = mutableListOf<String>()
        registry.add({ error("listener bug") }, null)
        registry.add({ received += it.name }, null)

        registry.dispatch(event)

        assertEquals(listOf("e"), received)
        assertEquals(Component.LISTENERS, Diagnostics.snapshot().single { it.error != null }.component)
    }

    @Test
    fun aRejectingExecutorIsReportedNotThrown() {
        registry.add({ }, Executor { throw RejectedExecutionException("shut down") })

        registry.dispatch(event)

        assertTrue(Diagnostics.snapshot().any { "rejected" in it.message })
    }

    @Test
    fun cancellingRemovesExactlyThatRegistration() {
        val received = mutableListOf<Int>()
        val listener = io.github.tracklens.TrackLensListener { received += 1 }
        val first = registry.add(listener, null)
        registry.add(listener, null)

        first.cancel()
        first.cancel()
        registry.dispatch(event)

        assertEquals("the second registration of the same listener stays", listOf(1), received)
    }

    @Test
    fun eachListenerRunsOnItsOwnExecutor() {
        val ran = mutableListOf<String>()
        registry.add({ ran += "default" }, null)
        registry.add(
            { ran += "custom" },
            Executor {
                ran += "custom executor"
                it.run()
            },
        )

        registry.dispatch(event)

        assertEquals(listOf("default", "custom executor", "custom"), ran)
    }
}
