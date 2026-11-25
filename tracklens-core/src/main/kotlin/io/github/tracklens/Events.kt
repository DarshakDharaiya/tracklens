package io.github.tracklens

/** What kind of call produced a [TrackedEvent]. */
public enum class EventKind {
    /** An analytics event ([TrackLens.log]). */
    EVENT,

    /** A user property change ([TrackLens.setUserProperty]). */
    USER_PROPERTY,

    /** A user id change ([TrackLens.setUserId]). */
    USER_ID,

    /** A screen change, automatic or from [TrackLens.setScreen]. */
    SCREEN,
}

/**
 * One recorded call, as TrackLens stores and displays it. Instances are created by TrackLens and are immutable.
 *
 * @property sequence Position in the session, starting at 1.
 * @property timestampMillis Wall-clock time (epoch milliseconds), for display and export.
 * @property elapsedMillis Time since the session started, from a monotonic clock; used for charts.
 * @property name Event name, user property name, `"user_id"` or screen name, depending on [kind].
 * @property kind What kind of call produced this record.
 * @property params Parameters after normalization and redaction.
 * @property screen Screen that was current when the call was made, if known.
 * @property sessionId Id of the session the record belongs to.
 * @property source SDK tag given to [TrackLens.wrap] or the logging call, if any.
 * @property issues Validation findings for this record; empty when it passed every check.
 */
@Suppress("LongParameterList") // An immutable record; only TrackLens constructs it.
public class TrackedEvent internal constructor(
    public val sequence: Long,
    public val timestampMillis: Long,
    public val elapsedMillis: Long,
    public val name: String,
    public val kind: EventKind,
    public val params: Map<String, Any?>,
    public val screen: String?,
    public val sessionId: String,
    public val source: String?,
    public val issues: List<ValidationIssue>,
) {
    override fun toString(): String = "TrackedEvent(#$sequence $kind $name, params=$params, screen=$screen, issues=${issues.size})"
}
