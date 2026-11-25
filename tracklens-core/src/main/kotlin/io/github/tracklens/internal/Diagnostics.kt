package io.github.tracklens.internal

import android.util.Log

/** Part of TrackLens a diagnostic is about. Each component that can fail on its own has its own circuit breaker. */
internal enum class Component {
    /** A public TrackLens call. */
    API,

    /** `TrackLens.install` and its environment checks. */
    INSTALL,

    /** Turning logging calls into recorded events (Task 13). */
    PIPELINE,
    STORAGE,
    VALIDATION,
    OVERLAY,
    EXPORT,
    LISTENERS,
}

internal enum class DiagnosticLevel(
    val logPriority: Int,
) {
    INFO(Log.INFO),
    WARNING(Log.WARN),
    ERROR(Log.ERROR),
}

/** One internal problem, shown in the TrackLens diagnostics view (Task 19 onwards). */
internal class DiagnosticEntry(
    val timestampMillis: Long,
    val component: Component,
    val level: DiagnosticLevel,
    val message: String,
    /** Exception class, message and the first stack frames, or `null`. */
    val error: String?,
) {
    override fun toString(): String = "[$level $component] $message" + (error?.let { "\n$it" } ?: "")
}

/**
 * Record of TrackLens' own failures: an in-memory ring of the latest [CAPACITY] entries, mirrored to Logcat.
 *
 * [report] never throws, whatever it is given: it is what every failure path ends in.
 */
internal object Diagnostics {
    const val CAPACITY = 200
    const val TAG = "TrackLens"

    /** Per component, only the first reports go to Logcat, so a failing loop can't flood it. All stay in the ring. */
    private const val LOGCAT_REPORTS_PER_COMPONENT = 10
    private const val MAX_MESSAGE_LENGTH = 1_000
    private const val MAX_STACK_FRAMES = 12

    private val lock = Any()
    private val entries = ArrayDeque<DiagnosticEntry>(CAPACITY)
    private val logcatReports = IntArray(Component.entries.size)

    /** Entries pushed out of the ring since the last [clear]. */
    @Volatile
    var evictedCount: Long = 0
        private set

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Reporting must never fail into the caller.
    fun report(
        component: Component,
        level: DiagnosticLevel,
        message: String,
        error: Throwable? = null,
    ) {
        try {
            val entry =
                DiagnosticEntry(
                    timestampMillis = System.currentTimeMillis(),
                    component = component,
                    level = level,
                    message = message.take(MAX_MESSAGE_LENGTH),
                    error = error?.let(::describe),
                )
            val toLogcat =
                synchronized(lock) {
                    if (entries.size == CAPACITY) {
                        entries.removeFirst()
                        evictedCount++
                    }
                    entries.addLast(entry)
                    ++logcatReports[component.ordinal] <= LOGCAT_REPORTS_PER_COMPONENT
                }
            if (toLogcat) logcat(entry)
        } catch (_: Throwable) {
            // Out of memory or similar: there is nothing safe left to do.
        }
    }

    /** The current entries, oldest first. */
    fun snapshot(): List<DiagnosticEntry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            logcatReports.fill(0)
            evictedCount = 0
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A broken Throwable must not break reporting.
    private fun describe(error: Throwable): String =
        try {
            buildString {
                append(error.javaClass.name)
                error.message?.let { append(": ").append(it.take(MAX_MESSAGE_LENGTH)) }
                error.stackTrace.take(MAX_STACK_FRAMES).forEach { append("\n    at ").append(it) }
                error.cause?.let { append("\nCaused by: ").append(it.javaClass.name).append(": ").append(it.message) }
            }
        } catch (_: Throwable) {
            error.javaClass.name
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // android.util.Log is a stub in plain JVM tests.
    private fun logcat(entry: DiagnosticEntry) {
        try {
            Log.println(entry.level.logPriority, TAG, entry.toString())
        } catch (_: Throwable) {
            // Logcat unavailable; the entry is still in the ring.
        }
    }
}
