package io.github.tracklens.internal.storage

import androidx.room.ColumnInfo
import io.github.tracklens.EventKind
import io.github.tracklens.InternalTrackLensApi

// Lightweight rows for the dashboard queries (plan section 3.4): never full entities.

/** A row of the session list. */
@InternalTrackLensApi
public data class SessionListItem(
    /** Internal key. */
    val id: Long,
    /** Public id. */
    val uuid: String,
    /** Tester label. */
    val label: String?,
    /** Start, epoch ms. */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** End, epoch ms; `null` while active. */
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    /** Why it ended. */
    @ColumnInfo(name = "end_reason") val endReason: SessionEndReason,
    /** Stored events. */
    @ColumnInfo(name = "event_count") val eventCount: Long,
    /** Warnings of severity warning. */
    @ColumnInfo(name = "warning_count") val warningCount: Long,
    /** Warnings of severity error. */
    @ColumnInfo(name = "error_count") val errorCount: Long,
    /** Exempt from retention. */
    val pinned: Boolean,
    /** Started manually. */
    @ColumnInfo(name = "is_manual") val isManual: Boolean,
)

/** The summary card's event counts (analytics events only). */
@InternalTrackLensApi
public data class EventTotals(
    /** Events. */
    val total: Long,
    /** Distinct event names. */
    @ColumnInfo(name = "uniq") val unique: Long,
    /** Events with an error. */
    val invalid: Long,
)

/** One tick on the timeline. */
@InternalTrackLensApi
public data class TimelinePoint(
    /** Event key. */
    val id: Long,
    /** Sequence. */
    val seq: Long,
    /** Since session start. */
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    /** Name. */
    val name: String,
    /** Category, if known. */
    val category: String?,
    /** 0–3. */
    @ColumnInfo(name = "max_severity") val maxSeverity: Int,
)

/** Events (and how many with a warning or worse) in one time bucket. */
@InternalTrackLensApi
public data class TimeBucket(
    /** Bucket index: `elapsed_ms / bucketMs`. */
    val bucket: Long,
    /** Events in it. */
    val count: Long,
    /** Of those, with severity warning or error. */
    val warn: Long,
)

/** How often a name occurs, and its worst severity. */
@InternalTrackLensApi
public data class NameCount(
    /** Event name. */
    val name: String,
    /** Occurrences. */
    val count: Long,
    /** Worst `max_severity` among them. */
    val severity: Int,
)

/** A node of the screen flow. */
@InternalTrackLensApi
public data class ScreenVisitRow(
    /** Visit key. */
    val id: Long,
    /** Order in the session. */
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    /** Screen name. */
    @ColumnInfo(name = "screen_name") val screenName: String,
    /** How it was detected. */
    val source: ScreenVisitSource,
    /** Entered, since session start. */
    @ColumnInfo(name = "entered_elapsed_ms") val enteredElapsedMs: Long,
    /** Foreground time on it; `null` while on screen. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
)

/** Events logged during one visit (`null`: outside any visit). */
@InternalTrackLensApi
public data class VisitEventCount(
    /** Visit key. */
    @ColumnInfo(name = "screen_visit_id") val screenVisitId: Long?,
    /** Events logged during it. */
    val count: Long,
)

/** One group of the warnings section. */
@InternalTrackLensApi
public data class WarningGroup(
    /** Rule code. */
    val code: String,
    /** 1–3. */
    val severity: Int,
    /** Occurrences. */
    val count: Long,
)

/** One warning with the event it is about. */
@InternalTrackLensApi
public data class WarningRow(
    /** Warning key. */
    val id: Long,
    /** Event key; `null` for session-level warnings. */
    @ColumnInfo(name = "event_id") val eventId: Long?,
    /** Rule code. */
    val code: String,
    /** 1–3. */
    val severity: Int,
    /** Explanation. */
    val message: String,
    /** Parameter it is about. */
    @ColumnInfo(name = "param_key") val paramKey: String?,
    /** Event sequence, if any. */
    val seq: Long?,
    /** Event name, if any. */
    val name: String?,
)

/** An event as the funnel evaluator needs it. */
@InternalTrackLensApi
public data class FunnelEvent(
    /** Sequence. */
    val seq: Long,
    /** Since session start. */
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    /** Name. */
    val name: String,
    /** Parameters, for `where` conditions. */
    @ColumnInfo(name = "params_json") val paramsJson: String,
)

/** A name count in one of two compared sessions. */
@InternalTrackLensApi
public data class SessionNameCount(
    /** Session key. */
    @ColumnInfo(name = "session_id") val sessionId: Long,
    /** Event name. */
    val name: String,
    /** Occurrences. */
    val count: Long,
)

/** A row of a session's event list (keyset paged by `seq`). */
@InternalTrackLensApi
public data class EventListItem(
    /** Event key. */
    val id: Long,
    /** Sequence. */
    val seq: Long,
    /** Wall clock. */
    val timestamp: Long,
    /** Since session start. */
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    /** Kind. */
    val kind: EventKind,
    /** Name. */
    val name: String,
    /** SDK tag. */
    val source: String?,
    /** Category. */
    val category: String?,
    /** Screen at log time. */
    @ColumnInfo(name = "screen_name") val screenName: String?,
    /** Top-level parameter count. */
    @ColumnInfo(name = "param_count") val paramCount: Int,
    /** 0–3. */
    @ColumnInfo(name = "max_severity") val maxSeverity: Int,
)
