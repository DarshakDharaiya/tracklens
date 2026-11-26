package io.github.tracklens.internal

import io.github.tracklens.RedactionConfig
import io.github.tracklens.TrackedEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/** Record observers (storage's hook, Task 23): every record, in order, after redaction, with the redacted keys. */
class RecordObserverTest {
    private val pipeline =
        EventPipeline(
            liveBufferSize = 100,
            softCap = EventPipeline.DEFAULT_SOFT_CAP,
            clock =
                object : Clock {
                    override fun wallMillis() = 0L

                    override fun elapsedNanos() = 0L
                },
            dispatcher = EventPipeline.newPipelineDispatcher(),
            listeners = ListenerRegistry(Executor { it.run() }),
            redactor = Redactor(RedactionConfig.DEFAULT),
        )
    private val seen = mutableListOf<Pair<TrackedEvent, Set<String>>>()

    @After
    fun close() = pipeline.close()

    @Test
    fun observersSeeEveryRecordInOrderWithTheRedactedKeys() {
        pipeline.addRecordObserver { event, keys -> seen += event to keys }
        pipeline.start()

        pipeline.event("signup", ParamNormalizer.normalize(mapOf("email" to "jane@example.com", "plan" to "pro")), source = "firebase")
        pipeline.setScreen("Home", null)
        pipeline.setUserProperty("phone", "+31 6 1234 5678", source = null)
        pipeline.setUserProperty("plan", "pro", source = null)
        assertTrue(pipeline.awaitIdle())

        assertEquals(listOf(1L, 2L, 3L, 4L), seen.map { it.first.sequence })
        assertEquals(setOf("email"), seen[0].second)
        assertTrue(
            "already redacted",
            "jane@example.com" !in
                seen[0]
                    .first.params.values
                    .joinToString(),
        )
        assertEquals(emptySet<String>(), seen[1].second)
        assertEquals(setOf("value"), seen[2].second)
        assertEquals(emptySet<String>(), seen[3].second)
    }

    @Test
    fun aFailingObserverDoesNotStopRecordingOrTheOthers() {
        Diagnostics.clear()
        pipeline.addRecordObserver { _, _ -> error("broken observer") }
        pipeline.addRecordObserver { event, keys -> seen += event to keys }
        pipeline.start()

        repeat(3) { pipeline.event("e$it", NormalizedParams.EMPTY, source = null) }
        assertTrue(pipeline.awaitIdle())

        assertEquals(3, seen.size)
        assertEquals(3, pipeline.snapshot.value.events.size)
        assertTrue(Diagnostics.snapshot().any { "record observer failed" in it.message })
    }
}
