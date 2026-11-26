package io.github.tracklens.internal.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.tracklens.InternalTrackLensApi
import kotlinx.coroutines.flow.Flow

/** Sessions: the list, lifecycle updates and the denormalized counters. */
@InternalTrackLensApi
@Dao
public interface SessionDao {
    /** Inserts a session and returns its key. */
    @Insert
    public suspend fun insert(session: SessionEntity): Long

    /** One session by key. */
    @Query("SELECT * FROM sessions WHERE id = :id")
    public suspend fun get(id: Long): SessionEntity?

    /** One session by public id. */
    @Query("SELECT * FROM sessions WHERE uuid = :uuid")
    public suspend fun byUuid(uuid: String): SessionEntity?

    /** The session list: the active session first, then newest first. */
    @Query(
        """
        SELECT id, uuid, label, started_at, ended_at, end_reason, event_count, warning_count, error_count, pinned, is_manual
        FROM sessions ORDER BY ended_at IS NOT NULL, started_at DESC
        """,
    )
    public fun list(): Flow<List<SessionListItem>>

    /** Ends a session. */
    @Query("UPDATE sessions SET ended_at = :endedAt, end_reason = :reason WHERE id = :id")
    public suspend fun end(
        id: Long,
        endedAt: Long,
        reason: SessionEndReason,
    )

    /** Adds to the denormalized counters; the writer calls it in the batch transaction. */
    @Query(
        """
        UPDATE sessions SET event_count = event_count + :events, warning_count = warning_count + :warnings,
        error_count = error_count + :errors, dropped_count = dropped_count + :dropped WHERE id = :id
        """,
    )
    public suspend fun addCounts(
        id: Long,
        events: Long,
        warnings: Long,
        errors: Long,
        dropped: Long,
    )

    /** Pins or unpins (pinned sessions are exempt from retention). */
    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :id")
    public suspend fun setPinned(
        id: Long,
        pinned: Boolean,
    )

    /** Sets the tester's label and notes. */
    @Query("UPDATE sessions SET label = :label, notes = :notes WHERE id = :id")
    public suspend fun setLabel(
        id: Long,
        label: String?,
        notes: String?,
    )

    /** Deletes sessions; their events, params, visits and warnings go with them (foreign keys cascade). */
    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    public suspend fun delete(ids: List<Long>)
}

/** Writes of one batch: events with their params and warnings, and screen visits. */
@InternalTrackLensApi
@Dao
public interface WriteDao {
    /** Inserts events and returns their keys, in order. */
    @Insert
    public suspend fun insertEvents(events: List<EventEntity>): List<Long>

    /** Inserts parameter rows. */
    @Insert
    public suspend fun insertParams(params: List<EventParamEntity>)

    /** Inserts warnings. */
    @Insert
    public suspend fun insertWarnings(warnings: List<WarningEntity>)

    /** Inserts a screen visit and returns its key. */
    @Insert
    public suspend fun insertVisit(visit: ScreenVisitEntity): Long

    /** Closes a screen visit. */
    @Query("UPDATE screen_visits SET exited_elapsed_ms = :exitedMs, duration_ms = :exitedMs - entered_elapsed_ms WHERE id = :id")
    public suspend fun exitVisit(
        id: Long,
        exitedMs: Long,
    )

    /** Records an internal failure. */
    @Insert
    public suspend fun insertDiagnostic(diagnostic: DiagnosticEntity)

    /** Keeps the newest [keep] diagnostics. */
    @Query("DELETE FROM diagnostics WHERE id NOT IN (SELECT id FROM diagnostics ORDER BY id DESC LIMIT :keep)")
    public suspend fun trimDiagnostics(keep: Int)

    /** One event with its params and warnings, atomically. */
    @Transaction
    public suspend fun insertEvent(
        event: EventEntity,
        params: List<EventParamEntity>,
        warnings: List<WarningEntity>,
    ): Long {
        val id = insertEvents(listOf(event)).single()
        insertParams(params.map { it.copy(eventId = id, sessionId = event.sessionId) })
        insertWarnings(warnings.map { it.copy(eventId = id, sessionId = event.sessionId) })
        return id
    }
}

/**
 * The dashboard's chart queries, one per widget (plan section 3.4). The `Flow`s re-emit whenever a batch is written,
 * so an active session's dashboard follows it live.
 */
@InternalTrackLensApi
@Dao
public interface DashboardDao {
    /** Summary card: analytics events, distinct names, events with an error. */
    @Query(
        """
        SELECT COUNT(*) AS total, COUNT(DISTINCT name) AS uniq, COALESCE(SUM(max_severity = 3), 0) AS invalid
        FROM events WHERE session_id = :sessionId AND kind = 'EVENT'
        """,
    )
    public fun totals(sessionId: Long): Flow<EventTotals>

    /** Summary card: distinct screens visited. */
    @Query("SELECT COUNT(DISTINCT screen_name) FROM screen_visits WHERE session_id = :sessionId")
    public fun screenCount(sessionId: Long): Flow<Long>

    /** Summary card: warnings of every severity, session-level ones included. */
    @Query("SELECT COUNT(*) FROM warnings WHERE session_id = :sessionId")
    public fun warningCount(sessionId: Long): Flow<Long>

    /** Timeline: every record of the session in order. */
    @Query("SELECT id, seq, elapsed_ms, name, category, max_severity FROM events WHERE session_id = :sessionId ORDER BY seq")
    public fun timeline(sessionId: Long): Flow<List<TimelinePoint>>

    /** Events per time bucket (empty buckets are absent; the chart fills them with zero). */
    @Query(
        """
        SELECT elapsed_ms / :bucketMs AS bucket, COUNT(*) AS count, SUM(max_severity >= 2) AS warn
        FROM events WHERE session_id = :sessionId AND kind = 'EVENT' GROUP BY bucket ORDER BY bucket
        """,
    )
    public fun eventsPerBucket(
        sessionId: Long,
        bucketMs: Long,
    ): Flow<List<TimeBucket>>

    /** Top events: the [limit] most frequent names. */
    @Query(
        """
        SELECT name, COUNT(*) AS count, MAX(max_severity) AS severity FROM events
        WHERE session_id = :sessionId AND kind = 'EVENT' GROUP BY name ORDER BY count DESC, name LIMIT :limit
        """,
    )
    public fun topEvents(
        sessionId: Long,
        limit: Int,
    ): Flow<List<NameCount>>

    /** Every name with its count: rarely or never fired, and coverage. */
    @Query(
        """
        SELECT name, COUNT(*) AS count, MAX(max_severity) AS severity FROM events
        WHERE session_id = :sessionId AND kind = 'EVENT' GROUP BY name ORDER BY name
        """,
    )
    public fun nameCounts(sessionId: Long): Flow<List<NameCount>>

    /** Coverage: names that had an error, with how often. */
    @Query(
        """
        SELECT name, COUNT(*) AS count, 3 AS severity FROM events
        WHERE session_id = :sessionId AND max_severity = 3 GROUP BY name ORDER BY name
        """,
    )
    public fun invalidNameCounts(sessionId: Long): Flow<List<NameCount>>

    /** Screen flow: the visits in order. */
    @Query(
        """
        SELECT id, order_index, screen_name, source, entered_elapsed_ms, duration_ms FROM screen_visits
        WHERE session_id = :sessionId ORDER BY order_index
        """,
    )
    public fun screenVisits(sessionId: Long): Flow<List<ScreenVisitRow>>

    /** Screen flow: events logged during each visit. */
    @Query("SELECT screen_visit_id, COUNT(*) AS count FROM events WHERE session_id = :sessionId GROUP BY screen_visit_id")
    public fun eventsPerVisit(sessionId: Long): Flow<List<VisitEventCount>>
}

/** Warnings, funnels and session comparison (plan sections 3.4, 6.5 and the Compare screen). */
@InternalTrackLensApi
@Dao
public interface FindingsDao {
    /** Warnings section: groups by code, worst first, then most frequent. */
    @Query(
        """
        SELECT code, MAX(severity) AS severity, COUNT(*) AS count FROM warnings WHERE session_id = :sessionId
        GROUP BY code ORDER BY severity DESC, count DESC, code
        """,
    )
    public fun warningGroups(sessionId: Long): Flow<List<WarningGroup>>

    /** Warnings section: one group's warnings with their events, session-level ones first. */
    @Query(
        """
        SELECT w.id, w.event_id, w.code, w.severity, w.message, w.param_key, e.seq, e.name
        FROM warnings w LEFT JOIN events e ON e.id = w.event_id
        WHERE w.session_id = :sessionId AND w.code = :code ORDER BY e.seq LIMIT :limit
        """,
    )
    public suspend fun warningsWithCode(
        sessionId: Long,
        code: String,
        limit: Int = WARNINGS_PAGE,
    ): List<WarningRow>

    /** Funnels: the session's occurrences of the funnel's events, in order. */
    @Query(
        """
        SELECT seq, elapsed_ms, name, params_json FROM events
        WHERE session_id = :sessionId AND kind = 'EVENT' AND name IN (:names) ORDER BY seq
        """,
    )
    public suspend fun funnelEvents(
        sessionId: Long,
        names: List<String>,
    ): List<FunnelEvent>

    /** Comparison: name counts in two sessions. */
    @Query(
        """
        SELECT session_id, name, COUNT(*) AS count FROM events
        WHERE session_id IN (:first, :second) AND kind = 'EVENT' GROUP BY session_id, name ORDER BY session_id, name
        """,
    )
    public suspend fun comparisonCounts(
        first: Long,
        second: Long,
    ): List<SessionNameCount>

    /** Comparison: the order of event names (for the diff), capped at [limit]. */
    @Query("SELECT name FROM events WHERE session_id = :sessionId AND kind = 'EVENT' ORDER BY seq LIMIT :limit")
    public suspend fun nameSequence(
        sessionId: Long,
        limit: Int = ORDER_DIFF_LIMIT,
    ): List<String>

    /** Caps from the plan. */
    public companion object {
        /** Warnings listed per group. */
        public const val WARNINGS_PAGE: Int = 200

        /** Longest name sequence compared by the order diff. */
        public const val ORDER_DIFF_LIMIT: Int = 5_000
    }
}

/** A session's event list: keyset pages, the detail of one event, and search by parameter. */
@InternalTrackLensApi
@Dao
public interface EventListDao {
    /**
     * Event list: the next page after [afterSeq] (keyset paging). [nameLike] is a SQL `LIKE` pattern or `null` for
     * any; [minSeverity] 0 for any.
     */
    @Query(
        """
        SELECT id, seq, timestamp, elapsed_ms, kind, name, source, category, screen_name, param_count, max_severity
        FROM events WHERE session_id = :sessionId AND seq > :afterSeq
        AND (:nameLike IS NULL OR name LIKE :nameLike) AND max_severity >= :minSeverity
        ORDER BY seq LIMIT :limit
        """,
    )
    public suspend fun eventPage(
        sessionId: Long,
        afterSeq: Long,
        nameLike: String? = null,
        minSeverity: Int = SeverityLevel.NONE,
        limit: Int = EVENT_PAGE,
    ): List<EventListItem>

    /** Event detail: the full row. */
    @Query("SELECT * FROM events WHERE id = :eventId")
    public suspend fun event(eventId: Long): EventEntity?

    /** Search by parameter: events whose [key] has a value matching the `LIKE` pattern [valueLike]. */
    @Query(
        """
        SELECT DISTINCT event_id FROM event_params WHERE session_id = :sessionId AND key = :key AND value_text LIKE :valueLike
        ORDER BY event_id
        """,
    )
    public suspend fun eventsWithParam(
        sessionId: Long,
        key: String,
        valueLike: String,
    ): List<Long>

    /** Page size from the plan. */
    public companion object {
        /** Events per page of the event list. */
        public const val EVENT_PAGE: Int = 200
    }
}
