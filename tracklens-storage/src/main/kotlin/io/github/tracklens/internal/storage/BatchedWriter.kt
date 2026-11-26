package io.github.tracklens.internal.storage

import android.os.SystemClock
import androidx.room.withTransaction
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.TrackLens
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Where the writer runs: one thread, so writes never overlap and keep recording order. */
@InternalTrackLensApi
public interface WriterThread {
    /** Runs [task] on the writer thread after [delayMillis]. */
    public fun post(
        delayMillis: Long,
        task: () -> Unit,
    )

    /** Whether the caller is the writer thread. */
    public fun isCurrent(): Boolean

    /** The production writer thread: one background daemon thread named "TrackLens-Writer". */
    public class Default : WriterThread {
        @Volatile
        private var thread: Thread? = null
        private val executor: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, NAME).apply {
                    isDaemon = true
                    thread = this
                }
            }

        override fun post(
            delayMillis: Long,
            task: () -> Unit,
        ) {
            executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS)
        }

        override fun isCurrent(): Boolean = Thread.currentThread() === thread

        private companion object {
            const val NAME = "TrackLens-Writer"
        }
    }
}

/** When the writer flushes and how much it keeps (plan sections 3.5 and 4.4). */
@InternalTrackLensApi
public class WriterLimits(
    /** Buffered records that trigger an immediate flush. */
    public val batchSize: Int = BATCH_SIZE,
    /** Longest a record waits for a flush. */
    public val flushDelayMillis: Long = FLUSH_DELAY_MILLIS,
    /** More buffered records than this and the oldest are dropped (from storage only). */
    public val maxBuffered: Int = MAX_BUFFERED,
    /** Records stored per session; later ones are not stored and the session is marked truncated. */
    public val maxEventsPerSession: Int,
    /** Consecutive failed flushes after which storage turns itself off. */
    public val maxFailures: Int = MAX_FAILURES,
) {
    /** The plan's defaults. */
    public companion object {
        /** Plan section 4.4. */
        public const val BATCH_SIZE: Int = 64

        /**
         * The plan flushes 250 ms after the first unflushed record and promises that a sudden process death loses at
         * most 250 ms of events. A record that arrives while a batch is being written also waits for that write, so
         * the timer is 200 ms: the 50 ms margin covers the write and keeps the promise.
         */
        public const val FLUSH_DELAY_MILLIS: Long = 200

        /** Plan section 4.4. */
        public const val MAX_BUFFERED: Int = 5_000

        /** Plan section 4.4. */
        public const val MAX_FAILURES: Int = 5
    }
}

/**
 * Persists recorded events in batches (plan section 4.4). [enqueue] runs on the pipeline thread and only buffers;
 * writes happen on the [WriterThread], one Room transaction per batch, so a batch is stored completely or not at all.
 *
 * - Flushes when [WriterLimits.batchSize] records are buffered, [WriterLimits.flushDelayMillis] after the first
 *   unflushed record, or on request ([flush], [flushBlocking]: background, trim memory, session end, crash).
 * - Backpressure: past [WriterLimits.maxBuffered] the oldest records are dropped from storage only (the live view
 *   keeps them), counted on their session with an `SE_EVENTS_DROPPED` warning.
 * - Truncation: past [WriterLimits.maxEventsPerSession] a session stores nothing more, is marked truncated and gets
 *   one `SE_SESSION_TRUNCATED` warning; the start of a flow matters more for reproduction than its tail.
 * - A failed batch is put back and retried; after [WriterLimits.maxFailures] consecutive failures storage turns off.
 */
@InternalTrackLensApi
public class BatchedWriter(
    private val database: () -> TrackLensDatabase,
    private val limits: WriterLimits,
    private val thread: WriterThread = WriterThread.Default(),
    /** Reports a problem: an error with a throwable, otherwise a warning. */
    private val report: (message: String, failure: Throwable?) -> Unit,
) {
    private val lock = Any()
    private val buffer = ArrayDeque<PendingRecord>()
    private val droppedBySession = LinkedHashMap<String, Long>()

    /** A write is posted for "the batch is full" / for "the first record has waited long enough". */
    private var immediatePosted = false
    private var timerPosted = false
    private var failures = 0

    @Volatile
    private var enabled = true

    // Writer-thread state.
    private val db by lazy(database)
    private val sessions = HashMap<String, SessionState>()

    /** Whether storage is still on (it turns off after repeated failures). */
    public val isEnabled: Boolean get() = enabled

    /** Buffers [record] for the next flush. Pipeline thread; never blocks on the database. */
    public fun enqueue(record: PendingRecord) {
        if (!enabled) return
        synchronized(lock) {
            buffer.addLast(record)
            if (buffer.size > limits.maxBuffered) {
                val dropped = buffer.removeFirst().event.sessionId
                droppedBySession[dropped] = (droppedBySession[dropped] ?: 0) + 1
            }
            // Two independent triggers: a waiting timer must not hold back a full batch.
            if (buffer.size >= limits.batchSize && !immediatePosted) {
                immediatePosted = true
                thread.post(0) { write() }
            } else if (buffer.size == 1 && !timerPosted) {
                timerPosted = true
                thread.post(limits.flushDelayMillis) { write() }
            }
        }
    }

    /** Asks for a flush now (background, trim memory, session end). */
    public fun flush() {
        thread.post(0) { write() }
    }

    /**
     * Flushes and waits at most [timeoutMillis] (crash path). Returns whether everything buffered was written. Called
     * on the writer thread itself it writes directly instead of waiting for itself.
     */
    public fun flushBlocking(timeoutMillis: Long): Boolean {
        if (thread.isCurrent()) return write()
        val done = CountDownLatch(1)
        val written = AtomicBoolean(false)
        thread.post(0) {
            written.set(write())
            done.countDown()
        }
        return done.await(timeoutMillis, TimeUnit.MILLISECONDS) && written.get()
    }

    /** Records waiting to be written; for tests and diagnostics. */
    public val bufferedCount: Int get() = synchronized(lock) { buffer.size }

    /** Writes everything buffered in one transaction. Writer thread. Returns whether it succeeded. */
    @Suppress("TooGenericExceptionCaught") // A storage failure must never reach the app.
    private fun write(): Boolean {
        val (batch, dropped) =
            synchronized(lock) {
                immediatePosted = false
                timerPosted = false
                val taken = buffer.toList() to droppedBySession.toMap()
                buffer.clear()
                droppedBySession.clear()
                taken
            }
        if (!enabled || (batch.isEmpty() && dropped.isEmpty())) return enabled
        return try {
            runBlocking { db.withTransaction { store(batch, dropped) } }
            synchronized(lock) { failures = 0 }
            true
        } catch (failure: Throwable) {
            sessions.clear() // The transaction rolled back; reload what the database really holds.
            putBack(batch, dropped, failure)
            false
        }
    }

    private fun putBack(
        batch: List<PendingRecord>,
        dropped: Map<String, Long>,
        failure: Throwable,
    ) {
        val turnOff =
            synchronized(lock) {
                failures++
                if (failures >= limits.maxFailures) {
                    enabled = false
                    buffer.clear()
                    droppedBySession.clear()
                    true
                } else {
                    batch.asReversed().forEach(buffer::addFirst)
                    dropped.forEach { (session, count) -> droppedBySession[session] = (droppedBySession[session] ?: 0) + count }
                    timerPosted = true
                    thread.post(limits.flushDelayMillis) { write() }
                    false
                }
            }
        report("TrackLens could not store ${batch.size} events (failure $failures of ${limits.maxFailures})", failure)
        if (turnOff) report("TrackLens storage is off after ${limits.maxFailures} failed writes; the live view keeps working", null)
    }

    private suspend fun store(
        batch: List<PendingRecord>,
        dropped: Map<String, Long>,
    ) {
        val counts = HashMap<Long, Counts>()
        for (record in batch) {
            val session = session(record)
            if (session.stored >= limits.maxEventsPerSession) {
                truncate(session, counts)
                continue
            }
            val mapped = RecordMapper.map(record, session.id)
            db.writes().insertEvent(mapped.event, mapped.params, mapped.warnings)
            session.stored++
            counts.getOrPut(session.id, ::Counts).add(events = 1, warnings = mapped.warnings)
        }
        for ((uuid, count) in dropped) {
            val session = session(uuid, record = null)
            val warning =
                sessionWarning(
                    session.id,
                    DROPPED_CODE,
                    "$count events were not stored because storage fell behind; the live view kept them.",
                )
            db.writes().insertWarnings(listOf(warning))
            counts.getOrPut(session.id, ::Counts).add(events = 0, warnings = listOf(warning), dropped = count)
        }
        for ((id, count) in counts) db.sessions().addCounts(id, count.events, count.warnings, count.errors, count.dropped)
    }

    private suspend fun truncate(
        session: SessionState,
        counts: HashMap<Long, Counts>,
    ) {
        if (session.truncated) return
        session.truncated = true
        db.sessions().markTruncated(session.id)
        val warning =
            sessionWarning(
                session.id,
                TRUNCATED_CODE,
                "The session reached ${limits.maxEventsPerSession} stored events; later events were not stored.",
            )
        db.writes().insertWarnings(listOf(warning))
        counts.getOrPut(session.id, ::Counts).add(events = 0, warnings = listOf(warning))
    }

    private suspend fun session(record: PendingRecord): SessionState = session(record.event.sessionId, record)

    /**
     * The session row for [uuid], created from the first record that needs it. Task 25's session manager replaces
     * this lazy creation with explicit starts, ends and device metadata.
     */
    private suspend fun session(
        uuid: String,
        record: PendingRecord?,
    ): SessionState =
        sessions[uuid] ?: run {
            val existing = db.sessions().byUuid(uuid)
            val state =
                if (existing != null) {
                    SessionState(existing.id, existing.eventCount, existing.truncated)
                } else {
                    val now = System.currentTimeMillis()
                    val startedAt = record?.let { it.event.timestampMillis - it.event.elapsedMillis } ?: now
                    val id =
                        db.sessions().insert(
                            SessionEntity(
                                uuid = uuid,
                                startedAt = startedAt,
                                startElapsedRealtime = SystemClock.elapsedRealtime() - (now - startedAt).coerceAtLeast(0),
                                libraryVersion = TrackLens.libraryVersion,
                            ),
                        )
                    SessionState(id, stored = 0, truncated = false)
                }
            sessions[uuid] = state
            state
        }

    private fun sessionWarning(
        sessionId: Long,
        code: String,
        message: String,
    ) = WarningEntity(sessionId = sessionId, code = code, severity = SeverityLevel.WARNING, message = message)

    private class SessionState(
        val id: Long,
        var stored: Long,
        var truncated: Boolean,
    )

    private class Counts {
        var events = 0L
        var warnings = 0L
        var errors = 0L
        var dropped = 0L

        fun add(
            events: Long,
            warnings: List<WarningEntity>,
            dropped: Long = 0,
        ) {
            this.events += events
            this.warnings += warnings.count { it.severity == SeverityLevel.WARNING }
            this.errors += warnings.count { it.severity == SeverityLevel.ERROR }
            this.dropped += dropped
        }
    }

    /** Session-level warning codes (plan section 6.3). */
    public companion object {
        /** Events dropped from storage under backpressure. */
        public const val DROPPED_CODE: String = "SE_EVENTS_DROPPED"

        /** The session reached `maxEventsPerSession`. */
        public const val TRUNCATED_CODE: String = "SE_SESSION_TRUNCATED"
    }
}
