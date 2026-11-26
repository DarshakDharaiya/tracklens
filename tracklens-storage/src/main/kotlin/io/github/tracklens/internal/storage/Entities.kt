package io.github.tracklens.internal.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.tracklens.EventKind
import io.github.tracklens.InternalTrackLensApi

// The Room schema v1 (plan section 3.2). Column names follow the plan exactly; Kotlin names are their camelCase.

/** Why a session ended; `ACTIVE` while it runs. */
@InternalTrackLensApi
public enum class SessionEndReason {
    /** Still running. */
    ACTIVE,

    /** The app stayed in the background longer than `SessionConfig.backgroundTimeout`. */
    BACKGROUND_TIMEOUT,

    /** Ended with `TrackLens.endSession()`. */
    MANUAL,

    /** Replaced by `TrackLens.startSession()`. */
    NEW_MANUAL_SESSION,

    /** The process died while it was active. */
    INTERRUPTED,
}

/** How a parameter value was stored in [EventParamEntity]. */
@InternalTrackLensApi
public enum class ParamValueType {
    /** Text. */
    STRING,

    /** A whole number. */
    LONG,

    /** A decimal number. */
    DOUBLE,

    /** `true` or `false`. */
    BOOLEAN,

    /** No value. */
    NULL,

    /** A list; the values stay in `events.params_json`. */
    ARRAY,

    /** A nested map (a Bundle in Firebase terms); the values stay in `events.params_json`. */
    BUNDLE,

    /** Anything else, kept as its text. */
    OTHER,
}

/** Where a screen visit was detected. */
@InternalTrackLensApi
public enum class ScreenVisitSource {
    /** An Activity was resumed. */
    ACTIVITY,

    /** A Fragment was resumed. */
    FRAGMENT,

    /** A Compose destination reported by the app. */
    COMPOSE,

    /** `TrackLens.setScreen`. */
    MANUAL,

    /** A `screen_view` analytics event. */
    SCREEN_VIEW_EVENT,
}

/** One QA session: a run of the app from start to end, with its device and plan context and denormalized counts. */
@InternalTrackLensApi
@Entity(tableName = "sessions", indices = [Index(value = ["uuid"], unique = true), Index("started_at")])
public data class SessionEntity(
    /** Internal key. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Public session id (API and export). */
    val uuid: String,
    /** Tester-provided label. */
    val label: String? = null,
    /** Tester-provided notes. */
    val notes: String? = null,
    /** Wall-clock start, epoch milliseconds. */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** Wall-clock end; `null` while active. */
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    /** `SystemClock.elapsedRealtime()` at start, the anchor for `events.elapsed_ms`. */
    @ColumnInfo(name = "start_elapsed_realtime") val startElapsedRealtime: Long,
    /** Why it ended. */
    @ColumnInfo(name = "end_reason") val endReason: SessionEndReason = SessionEndReason.ACTIVE,
    /** Started with `TrackLens.startSession()`. */
    @ColumnInfo(name = "is_manual") val isManual: Boolean = false,
    /** Exempt from retention. */
    val pinned: Boolean = false,
    /** `maxEventsPerSession` was reached; later events were not stored. */
    val truncated: Boolean = false,
    /** Host app version name. */
    @ColumnInfo(name = "app_version_name") val appVersionName: String? = null,
    /** Host app version code. */
    @ColumnInfo(name = "app_version_code") val appVersionCode: Long? = null,
    /** `debug`, `release` or a custom build type. */
    @ColumnInfo(name = "build_type") val buildType: String? = null,
    /** Host application id. */
    @ColumnInfo(name = "app_id") val appId: String? = null,
    /** `Build.MANUFACTURER`. */
    @ColumnInfo(name = "device_manufacturer") val deviceManufacturer: String? = null,
    /** `Build.MODEL`. */
    @ColumnInfo(name = "device_model") val deviceModel: String? = null,
    /** `Build.VERSION.RELEASE`. */
    @ColumnInfo(name = "os_release") val osRelease: String? = null,
    /** `Build.VERSION.SDK_INT`. */
    @ColumnInfo(name = "os_sdk_int") val osSdkInt: Int? = null,
    /** BCP 47 language tag at start. */
    val locale: String? = null,
    /** `DisplayMetrics.densityDpi`. */
    @ColumnInfo(name = "screen_density") val screenDensity: Int? = null,
    /** Tracking plan used for validation. */
    @ColumnInfo(name = "plan_name") val planName: String? = null,
    /** Its version. */
    @ColumnInfo(name = "plan_version") val planVersion: String? = null,
    /** Hash of its content. */
    @ColumnInfo(name = "plan_hash") val planHash: String? = null,
    /** Stored events (all kinds). */
    @ColumnInfo(name = "event_count") val eventCount: Long = 0,
    /** Stored warnings of severity warning. */
    @ColumnInfo(name = "warning_count") val warningCount: Long = 0,
    /** Stored warnings of severity error. */
    @ColumnInfo(name = "error_count") val errorCount: Long = 0,
    /** Logging calls dropped under backpressure. */
    @ColumnInfo(name = "dropped_count") val droppedCount: Long = 0,
    /** TrackLens version that recorded it. */
    @ColumnInfo(name = "library_version") val libraryVersion: String,
)

/** One recorded call, as stored. */
@InternalTrackLensApi
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            entity = ScreenVisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["screen_visit_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["session_id", "seq"], unique = true),
        Index(value = ["session_id", "name"]),
        Index(value = ["session_id", "elapsed_ms"]),
        Index(value = ["session_id", "max_severity"]),
        Index(value = ["screen_visit_id"]),
    ],
)
public data class EventEntity(
    /** Internal key. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Owning session. */
    @ColumnInfo(name = "session_id") val sessionId: Long,
    /** Position in the session, from 1. */
    val seq: Long,
    /** Wall clock, epoch milliseconds. */
    val timestamp: Long,
    /** Since session start, monotonic. */
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    /** Kind of call. */
    val kind: EventKind,
    /** Event name, property name, `user_id` or screen. */
    val name: String,
    /** SDK tag. */
    val source: String? = null,
    /** From the plan, then the categorizer. */
    val category: String? = null,
    /** Screen at log time. */
    @ColumnInfo(name = "screen_name") val screenName: String? = null,
    /** The visit it was logged during. */
    @ColumnInfo(name = "screen_visit_id") val screenVisitId: Long? = null,
    /** Redacted, typed JSON of the parameters. */
    @ColumnInfo(name = "params_json") val paramsJson: String,
    /** Top-level parameter count. */
    @ColumnInfo(name = "param_count") val paramCount: Int,
    /** 0 none, 1 info, 2 warning, 3 error. */
    @ColumnInfo(name = "max_severity") val maxSeverity: Int = SeverityLevel.NONE,
)

/** A queryable copy of one top-level parameter. */
@InternalTrackLensApi
@Entity(
    tableName = "event_params",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("event_id"), Index(value = ["session_id", "key"]), Index(value = ["session_id", "key", "value_text"])],
)
public data class EventParamEntity(
    /** Internal key. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Owning event. */
    @ColumnInfo(name = "event_id") val eventId: Long,
    /** Owning session, denormalized for per-session parameter queries. */
    @ColumnInfo(name = "session_id") val sessionId: Long,
    /** Top-level key. */
    val key: String,
    /** How the value is stored. */
    @ColumnInfo(name = "value_type") val valueType: ParamValueType,
    /** Redacted text form. */
    @ColumnInfo(name = "value_text") val valueText: String? = null,
    /** Numeric form, for range queries. */
    @ColumnInfo(name = "value_num") val valueNum: Double? = null,
    /** Whether redaction changed it. */
    val redacted: Boolean = false,
)

/** Time spent on one screen, in foreground time. */
@InternalTrackLensApi
@Entity(
    tableName = "screen_visits",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id", "order_index"], unique = true)],
)
public data class ScreenVisitEntity(
    /** Internal key. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Owning session. */
    @ColumnInfo(name = "session_id") val sessionId: Long,
    /** 0..n within the session. */
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    /** Screen name. */
    @ColumnInfo(name = "screen_name") val screenName: String,
    /** Screen class, if known. */
    @ColumnInfo(name = "screen_class") val screenClass: String? = null,
    /** How it was detected. */
    val source: ScreenVisitSource,
    /** Entered, since session start. */
    @ColumnInfo(name = "entered_elapsed_ms") val enteredElapsedMs: Long,
    /** Left, since session start; `null` while on screen. */
    @ColumnInfo(name = "exited_elapsed_ms") val exitedElapsedMs: Long? = null,
    /** Set on exit. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
)

/** A validation finding, for one event or (with no event) the whole session. */
@InternalTrackLensApi
@Entity(
    tableName = "warnings",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["session_id", "code"]), Index("event_id")],
)
public data class WarningEntity(
    /** Internal key. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Owning session. */
    @ColumnInfo(name = "session_id") val sessionId: Long,
    /** The event it is about; `null` for session-level findings (truncation, dropped events). */
    @ColumnInfo(name = "event_id") val eventId: Long? = null,
    /** Stable rule code. */
    val code: String,
    /** 1 info, 2 warning, 3 error. */
    val severity: Int,
    /** Human-readable explanation. */
    val message: String,
    /** The parameter it is about. */
    @ColumnInfo(name = "param_key") val paramKey: String? = null,
)

/** An internal TrackLens failure, kept for the Diagnostics screen (at most 500, trimmed by the writer). */
@InternalTrackLensApi
@Entity(tableName = "diagnostics")
public data class DiagnosticEntity(
    /** Internal key. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall clock, epoch milliseconds. */
    val timestamp: Long,
    /** Component name. */
    val component: String,
    /** Level name. */
    val level: String,
    /** What happened. */
    val message: String,
    /** Stack trace, if any. */
    val stacktrace: String? = null,
)

/** The `max_severity` / `warnings.severity` scale. */
@InternalTrackLensApi
public object SeverityLevel {
    /** No finding. */
    public const val NONE: Int = 0

    /** `Severity.INFO`. */
    public const val INFO: Int = 1

    /** `Severity.WARNING`. */
    public const val WARNING: Int = 2

    /** `Severity.ERROR`. */
    public const val ERROR: Int = 3
}
