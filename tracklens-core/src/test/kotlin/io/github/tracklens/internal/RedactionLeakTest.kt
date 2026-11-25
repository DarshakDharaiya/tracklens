package io.github.tracklens.internal

import io.github.tracklens.RedactionConfig
import io.github.tracklens.TrackedEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/**
 * Task 14 definition of done: with the default redaction settings, no sensitive raw value reaches the ring buffer
 * (the live view) or listeners, wherever it appears in the logged data.
 */
class RedactionLeakTest {
    private val delivered = ArrayList<TrackedEvent>()
    private val pipeline =
        EventPipeline(
            liveBufferSize = 100,
            softCap = EventPipeline.DEFAULT_SOFT_CAP,
            clock = FixedClock,
            dispatcher = EventPipeline.newPipelineDispatcher(),
            listeners = ListenerRegistry(Executor { it.run() }).apply { add({ delivered += it }, null) },
            redactor = Redactor(RedactionConfig.DEFAULT),
        ).apply { start() }

    @After
    fun close() = pipeline.close()

    @Test
    fun noSecretReachesTheRingBufferOrListeners() {
        pipeline.event(
            "checkout",
            ParamNormalizer.normalize(
                mapOf(
                    "email" to SECRETS[0],
                    "note" to "card ${SECRETS[1]}, call ${SECRETS[2]}",
                    "password" to SECRETS[3],
                    "contact" to mapOf("details" to listOf("reach me at ${SECRETS[0]}")),
                    "items" to listOf(mapOf("auth_token" to SECRETS[4])),
                    "object" to
                        object {
                            override fun toString() = "Customer(${SECRETS[0]})"
                        },
                    "card_number" to 4111111111111111L,
                ),
            ),
            source = "firebase",
        )
        pipeline.setUserId(SECRETS[5], source = null)
        pipeline.setUserProperty("support_email", SECRETS[0], source = null)
        pipeline.setUserProperty("referrer", "invited by ${SECRETS[2]}", source = null)
        assertTrue(pipeline.awaitIdle())

        val ringContents = pipeline.snapshot.value.events
        assertEquals(4, ringContents.size)
        assertEquals(ringContents.map { it.sequence }, delivered.map { it.sequence })
        val everything = (ringContents + delivered).joinToString("\n") { "${it.name} ${it.params}" }
        for (secret in SECRETS + "4111111111111111") {
            assertTrue("'$secret' leaked into:\n$everything", secret !in everything)
        }
        assertTrue("redacted values are visibly marked", "[REDACTED]" in everything)
    }

    private object FixedClock : Clock {
        override fun wallMillis() = 0L

        override fun elapsedNanos() = 0L
    }

    private companion object {
        val SECRETS =
            listOf(
                "jane.doe@example.com",
                "4111 1111 1111 1111",
                "+1 415-555-0100",
                "hunter2",
                "eyJhbGciOiJIUzI1NiJ9.payload.signature",
                "user-8f3a2c",
            )
    }
}
