package io.github.tracklens.internal

import android.os.Process
import io.github.tracklens.EventKind
import io.github.tracklens.TrackedEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Turns logging calls into [TrackedEvent]s (plan section 4.1).
 *
 * Caller thread ([EventSink] methods): timestamp, cap check, `trySend`. Nothing else.
 * Pipeline thread (one consumer, in queue order): session and sequence number, current screen, ring buffer,
 * listeners, and a [LiveSnapshot] published at most every [PUBLISH_INTERVAL_MILLIS].
 *
 * Redaction (Task 14), validation (Task 38) and storage (Task 23) join the consumer later. Until sessions arrive
 * (Task 25) the pipeline runs one implicit session from its creation.
 */
@Suppress("TooManyFunctions") // EventSink methods plus start/close/awaitIdle and one function per processing step.
internal class EventPipeline(
    liveBufferSize: Int,
    /** Maximum logging calls waiting in the queue; more are dropped and counted. Control messages are never dropped. */
    private val softCap: Int,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val listeners: ListenerRegistry,
) : EventSink {
    private val queue = Channel<PipelineMessage>(Channel.UNLIMITED)
    private val pendingCalls = AtomicInteger()
    private val droppedCalls = AtomicLong()
    private val scope =
        CoroutineScope(
            SupervisorJob() + dispatcher +
                CoroutineExceptionHandler { _, failure -> handleInternalFailure(Component.PIPELINE, "Pipeline coroutine failed", failure) },
        )
    private val processingBreaker = CircuitBreaker(Component.PIPELINE)

    // ---- Pipeline-thread state -------------------------------------------------------------------------------
    private val ring = EventRingBuffer(liveBufferSize)
    private var sequence = 0L
    private var totalEvents = 0L
    private var unread = 0
    private var currentScreen: String? = null
    private var published = 0L
    private var dirty = false
    private var coolingDown = false

    val sessionId: String = UUID.randomUUID().toString()
    private val sessionStartNanos = clock.elapsedNanos()

    private val liveState = MutableStateFlow(LiveSnapshot.EMPTY)

    /** Latest published state for the live view; replaced at most every [PUBLISH_INTERVAL_MILLIS]. */
    val snapshot: StateFlow<LiveSnapshot> = liveState.asStateFlow()

    /** Logging calls dropped since creation because the queue was over [softCap]. */
    val droppedCount: Long get() = droppedCalls.get()

    /** Starts the consumer. Calls made before this are queued (up to [softCap]). */
    fun start() {
        scope.launch {
            for (message in queue) {
                // Counted as soon as it leaves the queue, so a failing consumer can't fake overflow drops.
                if (message is PipelineMessage.Call) pendingCalls.decrementAndGet()
                processingBreaker.run(Unit) { process(message) }
            }
        }
    }

    // ---- Caller thread ---------------------------------------------------------------------------------------

    override fun event(
        name: String,
        params: NormalizedParams,
        source: String?,
    ) = offer(EventKind.EVENT, name, params, source)

    override fun setUserProperty(
        name: String,
        value: String?,
        source: String?,
    ) = offer(EventKind.USER_PROPERTY, name, NormalizedParams(mapOf("value" to value), truncated = false), source)

    override fun setUserId(
        userId: String?,
        source: String?,
    ) = offer(EventKind.USER_ID, USER_ID_NAME, NormalizedParams(mapOf("user_id" to userId), truncated = false), source)

    override fun setScreen(
        name: String,
        screenClass: String?,
    ) = offer(EventKind.SCREEN, name, NormalizedParams(mapOf("screen_class" to screenClass), truncated = false), source = null)

    private fun offer(
        kind: EventKind,
        name: String,
        params: NormalizedParams,
        source: String?,
    ) {
        if (pendingCalls.incrementAndGet() > softCap) {
            pendingCalls.decrementAndGet()
            if (droppedCalls.incrementAndGet() == 1L) {
                Diagnostics.report(
                    Component.PIPELINE,
                    DiagnosticLevel.WARNING,
                    "More than $softCap events were waiting to be processed; further events are dropped until the queue drains.",
                )
            }
            return
        }
        queue.trySend(PipelineMessage.Call(kind, name, params, source, clock.wallMillis(), clock.elapsedNanos()))
    }

    /** The live view is visible: reset the unread count. */
    fun markAllRead() {
        queue.trySend(PipelineMessage.MarkAllRead)
    }

    /** Blocks until everything queued so far is processed and published. For tests and shutdown paths. */
    fun awaitIdle(timeoutMillis: Long = AWAIT_IDLE_TIMEOUT_MILLIS): Boolean {
        val latch = CountDownLatch(1)
        queue.trySend(PipelineMessage.Barrier { latch.countDown() })
        return latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    /** Stops the consumer and its thread. Queued messages are discarded. */
    fun close() {
        queue.close()
        scope.cancel()
        (dispatcher as? ExecutorCoroutineDispatcher)?.close()
    }

    // ---- Pipeline thread -------------------------------------------------------------------------------------

    private fun process(message: PipelineMessage) {
        when (message) {
            is PipelineMessage.Call -> {
                record(message)
            }

            PipelineMessage.MarkAllRead -> {
                unread = 0
                markDirty()
            }

            is PipelineMessage.Barrier -> {
                publish()
                message.done()
            }
        }
    }

    private fun record(call: PipelineMessage.Call) {
        if (call.kind == EventKind.SCREEN) currentScreen = call.name
        val event =
            TrackedEvent(
                sequence = ++sequence,
                timestampMillis = call.wallMillis,
                elapsedMillis = TimeUnit.NANOSECONDS.toMillis(call.elapsedNanos - sessionStartNanos).coerceAtLeast(0),
                name = call.name,
                kind = call.kind,
                params = call.params.values,
                screen = currentScreen,
                sessionId = sessionId,
                source = call.source,
                issues = emptyList(),
            )
        ring.add(event)
        totalEvents++
        unread++
        listeners.dispatch(event)
        markDirty()
    }

    /** Publishes now unless one went out less than [PUBLISH_INTERVAL_MILLIS] ago; then once that interval ends. */
    private fun markDirty() {
        dirty = true
        if (!coolingDown) publish()
    }

    private fun publish() {
        dirty = false
        liveState.value = LiveSnapshot(ring.toList(), totalEvents, droppedCalls.get(), unread, ++published)
        if (coolingDown) return
        coolingDown = true
        scope.launch {
            delay(PUBLISH_INTERVAL_MILLIS)
            coolingDown = false
            if (dirty) markDirty()
        }
    }

    companion object {
        const val PUBLISH_INTERVAL_MILLIS = 100L
        const val DEFAULT_SOFT_CAP = 10_000
        const val USER_ID_NAME = "user_id"
        private const val AWAIT_IDLE_TIMEOUT_MILLIS = 10_000L
        private const val THREAD_NAME = "TrackLens-Pipeline"

        /** The production consumer thread: one background-priority daemon thread. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException") // Thread priority is best effort.
        fun newPipelineDispatcher(): ExecutorCoroutineDispatcher =
            Executors
                .newSingleThreadExecutor { runnable ->
                    Thread(
                        {
                            try {
                                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                            } catch (_: Throwable) {
                                // Not on Android (unit tests); keep the default priority.
                            }
                            runnable.run()
                        },
                        THREAD_NAME,
                    ).apply { isDaemon = true }
                }.asCoroutineDispatcher()
    }
}
