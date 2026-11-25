package io.github.tracklens.internal

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.tracklens.EventKind
import io.github.tracklens.TrackedEvent
import java.util.concurrent.Executor

/** Time sources, injectable so tests don't depend on real clocks. */
internal interface Clock {
    /** Wall-clock time for display and export. */
    fun wallMillis(): Long

    /** Monotonic time, unaffected by clock changes; used for ordering durations and charts. */
    fun elapsedNanos(): Long

    object Android : Clock {
        override fun wallMillis(): Long = System.currentTimeMillis()

        override fun elapsedNanos(): Long = SystemClock.elapsedRealtimeNanos()
    }
}

/** Runs tasks on the main thread; the default executor for listeners. */
internal object MainThreadExecutor : Executor {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun execute(command: Runnable) {
        handler.post(command)
    }
}

/** Everything that travels through the pipeline's single queue, so its consumer sees one total order. */
internal sealed interface PipelineMessage {
    /** A logging call, timestamped on the caller's thread. */
    class Call(
        val kind: EventKind,
        val name: String,
        val params: NormalizedParams,
        val source: String?,
        val wallMillis: Long,
        val elapsedNanos: Long,
    ) : PipelineMessage

    /** The live view became visible; unread events count as read. */
    object MarkAllRead : PipelineMessage

    /** Completes [done] once every earlier message has been processed and the snapshot is published. */
    class Barrier(
        val done: () -> Unit,
    ) : PipelineMessage
}

/**
 * What the live view shows: the newest events (oldest first) plus counters. Published by the pipeline at most
 * every [EventPipeline.PUBLISH_INTERVAL_MILLIS].
 */
internal class LiveSnapshot(
    val events: List<TrackedEvent>,
    /** Events processed since install (including those no longer in [events]). */
    val totalEvents: Long,
    /** Logging calls dropped because the queue was over its cap. */
    val droppedEvents: Long,
    /** Events since the live view was last visible. */
    val unreadCount: Int,
    /** Increases with every publication. */
    val version: Long,
) {
    companion object {
        val EMPTY = LiveSnapshot(emptyList(), totalEvents = 0, droppedEvents = 0, unreadCount = 0, version = 0)
    }
}

/** Fixed-capacity buffer of the newest events. Pipeline-thread only. */
internal class EventRingBuffer(
    val capacity: Int,
) {
    private val slots = arrayOfNulls<TrackedEvent>(capacity)
    private var next = 0
    private var size = 0

    fun add(event: TrackedEvent) {
        slots[next] = event
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    /** Copy of the contents, oldest first. */
    fun toList(): List<TrackedEvent> {
        val start = if (size < capacity) 0 else next
        return List(size) { index -> checkNotNull(slots[(start + index) % capacity]) }
    }

    fun clear() {
        slots.fill(null)
        next = 0
        size = 0
    }
}
