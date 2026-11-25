package io.github.tracklens.internal

import io.github.tracklens.EventKind
import io.github.tracklens.TrackedEvent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

class EventPipelineTest {
    private val received = ArrayList<TrackedEvent>()
    private val listeners = ListenerRegistry(Executor { it.run() }).apply { add({ received += it }, null) }
    private val clock = FakeClock()
    private var pipeline: EventPipeline? = null

    @Before
    fun clearDiagnostics() = Diagnostics.clear()

    @After
    fun close() {
        pipeline?.close()
    }

    @Test
    fun eightProducersKeepTheirOrderAndNothingIsLost() {
        val pipeline = newPipeline(liveBufferSize = 100, softCap = Int.MAX_VALUE).apply { start() }

        produceConcurrently(pipeline)
        assertTrue(pipeline.awaitIdle(timeoutMillis = 60_000))

        assertEquals(TOTAL, received.size)
        assertEquals((1L..TOTAL).toList(), received.map { it.sequence })
        assertEachThreadInOrder(received, expectedPerThread = EVENTS_PER_THREAD)
        val snapshot = pipeline.snapshot.value
        assertEquals(TOTAL.toLong(), snapshot.totalEvents)
        assertEquals(0L, snapshot.droppedEvents)
        assertEquals("the ring keeps the newest 100", ((TOTAL - 99).toLong()..TOTAL).toList(), snapshot.events.map { it.sequence })
    }

    @Test
    fun callsOverTheSoftCapAreDroppedAndCountedExactly() {
        // The consumer starts only after all producers finished, so exactly `softCap` calls fit in the queue.
        val pipeline = newPipeline(liveBufferSize = 100, softCap = 10_000)

        produceConcurrently(pipeline)
        assertEquals((TOTAL - 10_000).toLong(), pipeline.droppedCount)
        pipeline.start()
        assertTrue(pipeline.awaitIdle(timeoutMillis = 60_000))

        assertEquals(10_000, received.size)
        assertEquals((1L..10_000L).toList(), received.map { it.sequence })
        assertEachThreadInOrder(received, expectedPerThread = null)
        assertEquals(TOTAL.toLong(), received.size + pipeline.snapshot.value.droppedEvents)
        assertEquals("only the first drop is reported", 1, Diagnostics.snapshot().count { "dropped" in it.message })
    }

    @Test
    fun theQueueAcceptsCallsAgainOnceItDrains() {
        val pipeline = newPipeline(liveBufferSize = 10, softCap = 5)
        repeat(8) { pipeline.event("before", params(it), source = null) }
        pipeline.start()
        assertTrue(pipeline.awaitIdle())

        repeat(5) { pipeline.event("after", params(it), source = null) }
        assertTrue(pipeline.awaitIdle())

        assertEquals(listOf(5, 5), listOf(received.count { it.name == "before" }, received.count { it.name == "after" }))
        assertEquals(3L, pipeline.droppedCount)
    }

    @Test
    fun eventsCarryTheScreenThatWasCurrentWhenTheyWereLogged() {
        val pipeline = newPipeline().apply { start() }

        pipeline.event("app_open", NormalizedParams.EMPTY, source = null)
        pipeline.setScreen("Home", screenClass = "com.example.HomeActivity")
        pipeline.event("view_item", NormalizedParams.EMPTY, source = "firebase")
        pipeline.setScreen("Cart", screenClass = null)
        pipeline.event("begin_checkout", NormalizedParams.EMPTY, source = "firebase")
        assertTrue(pipeline.awaitIdle())

        assertEquals(
            listOf(
                Triple(EventKind.EVENT, "app_open", null),
                Triple(EventKind.SCREEN, "Home", "Home"),
                Triple(EventKind.EVENT, "view_item", "Home"),
                Triple(EventKind.SCREEN, "Cart", "Cart"),
                Triple(EventKind.EVENT, "begin_checkout", "Cart"),
            ),
            received.map { Triple(it.kind, it.name, it.screen) },
        )
        assertEquals(mapOf("screen_class" to "com.example.HomeActivity"), received[1].params)
        assertEquals("firebase", received[2].source)
    }

    @Test
    fun userPropertiesAndUserIdsBecomeTheirOwnKinds() {
        val pipeline = newPipeline().apply { start() }

        pipeline.setUserProperty("tier", "gold", source = "amplitude")
        pipeline.setUserId("user-42", source = null)
        assertTrue(pipeline.awaitIdle())

        assertEquals(EventKind.USER_PROPERTY, received[0].kind)
        assertEquals("tier", received[0].name)
        assertEquals(mapOf("value" to "gold"), received[0].params)
        assertEquals(EventKind.USER_ID, received[1].kind)
        assertEquals(mapOf("user_id" to "user-42"), received[1].params)
    }

    @Test
    fun timestampsComeFromTheCallerAndElapsedTimeFromSessionStart() {
        clock.nanos = 5_000_000_000L
        val pipeline = newPipeline().apply { start() }
        clock.wall = 1_700_000_000_000L
        clock.nanos += 2_500_000_000L

        pipeline.event("e", NormalizedParams.EMPTY, source = null)
        assertTrue(pipeline.awaitIdle())

        val event = received.single()
        assertEquals(1_700_000_000_000L, event.timestampMillis)
        assertEquals(2_500L, event.elapsedMillis)
        assertEquals(pipeline.sessionId, event.sessionId)
    }

    @Test
    fun unreadCountsUntilTheLiveViewIsSeen() {
        val pipeline = newPipeline().apply { start() }

        repeat(3) { pipeline.event("e", NormalizedParams.EMPTY, source = null) }
        assertTrue(pipeline.awaitIdle())
        assertEquals(3, pipeline.snapshot.value.unreadCount)

        pipeline.markAllRead()
        pipeline.event("e", NormalizedParams.EMPTY, source = null)
        assertTrue(pipeline.awaitIdle())
        assertEquals(1, pipeline.snapshot.value.unreadCount)
    }

    @Test
    fun snapshotsArePublishedAtMostEveryHundredMilliseconds() {
        val scheduler = TestCoroutineScheduler()
        val pipeline = newPipeline(dispatcher = StandardTestDispatcher(scheduler)).apply { start() }

        repeat(5) { pipeline.event("burst", NormalizedParams.EMPTY, source = null) }
        scheduler.runCurrent()
        assertEquals("the first event publishes at once", listOf(1L, 1L), versionAndCount(pipeline))

        scheduler.advanceTimeBy(EventPipeline.PUBLISH_INTERVAL_MILLIS - 1)
        scheduler.runCurrent()
        assertEquals("the rest waits for the interval", listOf(1L, 1L), versionAndCount(pipeline))

        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        assertEquals(listOf(2L, 5L), versionAndCount(pipeline))

        scheduler.advanceTimeBy(10 * EventPipeline.PUBLISH_INTERVAL_MILLIS)
        scheduler.runCurrent()
        assertEquals("nothing new, nothing published", 2L, pipeline.snapshot.value.version)
    }

    @Test
    fun aClosedPipelineIgnoresCalls() {
        val pipeline = newPipeline().apply { start() }
        pipeline.close()

        pipeline.event("late", NormalizedParams.EMPTY, source = null)
        pipeline.markAllRead()

        assertTrue(received.isEmpty())
        assertNull(Diagnostics.snapshot().firstOrNull { it.level == DiagnosticLevel.ERROR })
    }

    private fun newPipeline(
        liveBufferSize: Int = 100,
        softCap: Int = EventPipeline.DEFAULT_SOFT_CAP,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = EventPipeline.newPipelineDispatcher(),
    ): EventPipeline = EventPipeline(liveBufferSize, softCap, clock, dispatcher, listeners).also { pipeline = it }

    private fun produceConcurrently(pipeline: EventPipeline) {
        val threads =
            List(THREADS) { thread ->
                Thread { repeat(EVENTS_PER_THREAD) { pipeline.event("t$thread", params(it), source = null) } }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
    }

    /** Each producer's events must arrive in the order it sent them. */
    private fun assertEachThreadInOrder(
        events: List<TrackedEvent>,
        expectedPerThread: Int?,
    ) {
        events.groupBy { it.name }.forEach { (thread, threadEvents) ->
            val indexes = threadEvents.map { it.params.getValue("i") as Int }
            assertEquals("$thread in order", indexes.sorted(), indexes)
            assertEquals("$thread without duplicates", indexes.size, indexes.toSet().size)
            if (expectedPerThread != null) assertEquals("$thread complete", (0 until expectedPerThread).toList(), indexes)
        }
    }

    private fun versionAndCount(pipeline: EventPipeline) = pipeline.snapshot.value.let { listOf(it.version, it.events.size.toLong()) }

    private fun params(index: Int) = NormalizedParams(mapOf("i" to index), truncated = false)

    private class FakeClock : Clock {
        var wall = 0L
        var nanos = 0L

        override fun wallMillis() = wall

        override fun elapsedNanos() = nanos
    }

    private companion object {
        const val THREADS = 8
        const val EVENTS_PER_THREAD = 50_000
        const val TOTAL = THREADS * EVENTS_PER_THREAD
    }
}
