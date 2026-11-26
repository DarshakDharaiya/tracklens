package io.github.tracklens.internal.storage

import android.content.ComponentCallbacks2
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.EventKind
import io.github.tracklens.Severity
import io.github.tracklens.ValidationIssues
import io.github.tracklens.internal.TrackedEvents
import io.github.tracklens.internal.UnknownValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Task 23: batching thresholds, atomic batches, backpressure, truncation, the storage breaker and crash flushing. */
@RunWith(AndroidJUnit4::class)
class BatchedWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: TrackLensDatabase
    private val thread = ManualThread()
    private val problems = mutableListOf<Pair<String, Throwable?>>()

    @Before
    fun open() {
        db = TrackLensDatabase.inMemory(context)
    }

    @After
    fun close() = db.close()

    private fun writer(
        maxEventsPerSession: Int = 100_000,
        maxBuffered: Int = WriterLimits.MAX_BUFFERED,
        writerThread: WriterThread = thread,
    ) = BatchedWriter(
        database = { db },
        limits = WriterLimits(maxEventsPerSession = maxEventsPerSession, maxBuffered = maxBuffered),
        thread = writerThread,
        report = { message, failure -> synchronized(problems) { problems += message to failure } },
    )

    private fun record(
        sequence: Long,
        session: String = "S1",
        params: Map<String, Any?> = emptyMap(),
        redacted: Set<String> = emptySet(),
        issues: List<io.github.tracklens.ValidationIssue> = emptyList(),
    ) = PendingRecord(
        TrackedEvents.create(
            sequence,
            "event_$sequence",
            params,
            issues = issues,
            timestampMillis = 10_000 + sequence,
            elapsedMillis = sequence,
            sessionId = session,
        ),
        redacted,
    )

    private fun stored(session: String = "S1"): List<Long> =
        runBlocking {
            val id = db.sessions().byUuid(session)?.id ?: return@runBlocking emptyList()
            db
                .dashboard()
                .timeline(id)
                .first()
                .map { it.seq }
        }

    private fun paramRows(): List<List<Any?>> =
        db.openHelper.readableDatabase.query(PARAM_ROWS).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(listOf(c.getString(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getDouble(3), c.getInt(4)))
                }
            }
        }

    private fun session(uuid: String = "S1") = runBlocking { requireNotNull(db.sessions().byUuid(uuid)) }

    @Test
    fun theFirstRecordArmsTheTimerAndTheTimerWritesEverything() {
        val writer = writer()

        repeat(10) { writer.enqueue(record(it + 1L)) }

        assertEquals("one timer, not one per record", listOf(WriterLimits.FLUSH_DELAY_MILLIS), thread.delays)
        thread.runAll()
        assertEquals((1L..10L).toList(), stored())
        assertEquals(10L, session().eventCount)
        assertEquals(0, writer.bufferedCount)
    }

    @Test
    fun aFullBatchIsWrittenImmediately() {
        val writer = writer()

        repeat(WriterLimits.BATCH_SIZE) { writer.enqueue(record(it + 1L)) }

        assertEquals(listOf(WriterLimits.FLUSH_DELAY_MILLIS, 0L), thread.delays)
        thread.runAll()
        assertEquals(WriterLimits.BATCH_SIZE, stored().size)
    }

    @Test
    fun flushRequestsWriteWithoutWaiting() {
        val writer = writer()
        writer.enqueue(record(1))

        writer.flush()

        assertEquals(0L, thread.delays.last())
        thread.runAll()
        assertEquals(listOf(1L), stored())
    }

    @Test
    fun aBatchIsStoredCompletelyOrNotAtAll() {
        val writer = writer()
        writer.enqueue(record(1))
        thread.runAll()

        // Sequence 3 appears twice: the unique (session_id, seq) index fails the batch in the middle.
        listOf(2L, 3L, 3L, 4L).forEach { writer.enqueue(record(it)) }
        thread.runAll(times = 1)

        assertEquals("nothing of the failed batch", listOf(1L), stored())
        assertEquals(1L, session().eventCount)
        assertEquals(1, problems.size)
        assertEquals("put back for a retry", 4, writer.bufferedCount)
        assertTrue(writer.isEnabled)
    }

    @Test
    fun fiveFailedBatchesTurnStorageOff() {
        val writer = writer()
        listOf(1L, 1L).forEach { writer.enqueue(record(it)) }

        repeat(WriterLimits.MAX_FAILURES) { thread.runAll(times = 1) }

        assertFalse(writer.isEnabled)
        assertTrue(problems.last().first.contains("storage is off"))
        writer.enqueue(record(2))
        assertEquals("ignored once off", 0, writer.bufferedCount)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun backpressureDropsTheOldestFromStorageOnly() {
        val writer = writer(maxBuffered = 100)

        (1L..110L).forEach { writer.enqueue(record(it)) }
        thread.runAll()

        assertEquals((11L..110L).toList(), stored())
        val session = session()
        assertEquals(10L, session.droppedCount)
        assertEquals(100L, session.eventCount)
        val dropped = runBlocking { db.findings().warningsWithCode(session.id, BatchedWriter.DROPPED_CODE) }.single()
        assertNull(dropped.eventId)
        assertTrue(dropped.message.startsWith("10 events"))
    }

    @Test
    fun aSessionStopsAtItsCapAndIsMarkedTruncatedOnce() {
        val writer = writer(maxEventsPerSession = 10)

        (1L..15L).forEach { writer.enqueue(record(it)) }
        thread.runAll()
        (16L..20L).forEach { writer.enqueue(record(it)) }
        thread.runAll()

        assertEquals("the start of the flow is kept", (1L..10L).toList(), stored())
        val session = session()
        assertTrue(session.truncated)
        assertEquals(1, runBlocking { db.findings().warningsWithCode(session.id, BatchedWriter.TRUNCATED_CODE) }.size)

        writer.enqueue(record(1, session = "S2"))
        thread.runAll()
        assertEquals("other sessions are unaffected", listOf(1L), stored("S2"))
    }

    @Test
    fun recordsAreMappedToEventParamAndWarningRows() {
        val writer = writer()
        val params =
            linkedMapOf(
                "name" to "Jane",
                "count" to 3L,
                "price" to 9.99,
                "paid" to true,
                "note" to null,
                "items" to listOf(mapOf("id" to "A")),
                "address" to mapOf("city" to "Delft"),
                "thing" to UnknownValue("com.example.Thing", "Thing(1)"),
                "email" to "[REDACTED]",
            )
        val issues =
            listOf(
                ValidationIssues.create("FB_PARAM_TYPE", Severity.WARNING, "price is text", "price"),
                ValidationIssues.create("PLAN_UNKNOWN", Severity.ERROR, "not in plan"),
            )

        writer.enqueue(record(1, params = params, redacted = setOf("email"), issues = issues))
        val property = TrackedEvents.create(2, "plan", mapOf("value" to "pro"), kind = EventKind.USER_PROPERTY, sessionId = "S1")
        writer.enqueue(PendingRecord(property, emptySet()))
        thread.runAll()

        val session = session()
        val event =
            runBlocking {
                db.eventList().event(
                    db
                        .dashboard()
                        .timeline(session.id)
                        .first()
                        .first()
                        .id,
                )
            }!!
        assertEquals(
            "{\"name\":\"Jane\",\"count\":3,\"price\":9.99,\"paid\":true,\"note\":null,\"items\":[{\"id\":\"A\"}]," +
                "\"address\":{\"city\":\"Delft\"},\"thing\":\"Thing(1)\",\"email\":\"[REDACTED]\"}",
            event.paramsJson,
        )
        assertEquals(9, event.paramCount)
        assertEquals(SeverityLevel.ERROR, event.maxSeverity)
        assertEquals(EXPECTED_PARAM_ROWS, paramRows())
        assertEquals(listOf(1L, 1L, 2L), listOf(session.warningCount, session.errorCount, session.eventCount))
        assertEquals(EventKind.USER_PROPERTY, runBlocking { db.eventList().eventPage(session.id, afterSeq = 1) }.single().kind)
    }

    @Test
    fun theCrashHandlerFlushesThenAlwaysCallsThePreviousHandler() {
        val writerThread = WriterThread.Default()
        val writer = writer(writerThread = writerThread)
        (1L..5L).forEach { writer.enqueue(record(it)) } // Below the batch size: waiting for the timer.
        val handed = mutableListOf<Pair<Thread, Throwable>>()
        val handler =
            CrashFlushHandler(previous = { t, e -> handed += t to e }, drainPipeline = { true }, flushWriter = writer::flushBlocking)
        val crash = IllegalStateException("boom")

        handler.uncaughtException(Thread.currentThread(), crash)

        assertEquals((1L..5L).toList(), stored())
        assertEquals(listOf(Thread.currentThread() to crash), handed)
    }

    @Test
    fun theCrashHandlerGivesUpAfterItsBudget() {
        val writerThread = WriterThread.Default()
        val writer = writer(writerThread = writerThread)
        val release = CountDownLatch(1)
        writerThread.post(0) { release.await(5, TimeUnit.SECONDS) } // The writer is stuck.
        writer.enqueue(record(1))
        val handed = CountDownLatch(1)
        val handler =
            CrashFlushHandler(
                previous = { _, _ -> handed.countDown() },
                drainPipeline = { true },
                flushWriter = writer::flushBlocking,
                budgetMillis = 300,
            )

        val start = SystemClock.uptimeMillis()
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        val took = SystemClock.uptimeMillis() - start
        release.countDown()

        assertEquals(0L, handed.count)
        assertTrue("took $took ms", took in 250L..1_000L)
    }

    @Test
    fun flushSignalsFollowBackgroundAndTrimMemory() {
        var flushes = 0
        val signals = FlushSignals { flushes++ }

        signals.onStop(androidx.lifecycle.ProcessLifecycleOwner.get())
        signals.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        signals.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        @Suppress("DEPRECATION")
        signals.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) // Below UI_HIDDEN: the app is still visible.

        assertEquals(3, flushes)
    }

    private companion object {
        const val PARAM_ROWS = "SELECT key, value_type, value_text, value_num, redacted FROM event_params ORDER BY id"

        /** Rows of the record in recordsAreMappedToEventParamAndWarningRows: key, type, text, number, redacted. */
        val EXPECTED_PARAM_ROWS =
            listOf(
                listOf("name", "STRING", "Jane", null, 0),
                listOf("count", "LONG", "3", 3.0, 0),
                listOf("price", "DOUBLE", "9.99", 9.99, 0),
                listOf("paid", "BOOLEAN", "true", null, 0),
                listOf("note", "NULL", null, null, 0),
                listOf("items", "ARRAY", "[{\"id\":\"A\"}]", null, 0),
                listOf("address", "BUNDLE", "{\"city\":\"Delft\"}", null, 0),
                listOf("thing", "OTHER", "Thing(1)", null, 0),
                listOf("email", "STRING", "[REDACTED]", null, 1),
                listOf("value", "STRING", "pro", null, 0),
            )
    }

    /** A writer thread the test drives: tasks run when the test says, on the test thread. */
    private class ManualThread : WriterThread {
        private val tasks = ArrayDeque<() -> Unit>()
        val delays = mutableListOf<Long>()
        private var running = false

        override fun post(
            delayMillis: Long,
            task: () -> Unit,
        ) {
            delays += delayMillis
            tasks.addLast(task)
        }

        override fun isCurrent(): Boolean = running

        /** Runs queued tasks (including ones they post) [times] times, or until none are left. */
        fun runAll(times: Int = Int.MAX_VALUE) {
            var left = times
            while (tasks.isNotEmpty() && left-- > 0) {
                running = true
                try {
                    tasks.removeFirst().invoke()
                } finally {
                    running = false
                }
            }
        }
    }
}
