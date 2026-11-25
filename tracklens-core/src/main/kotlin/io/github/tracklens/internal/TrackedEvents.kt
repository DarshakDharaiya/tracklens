package io.github.tracklens.internal

import io.github.tracklens.EventKind
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.TrackedEvent
import io.github.tracklens.ValidationIssue

/**
 * Builds [TrackedEvent]s outside the pipeline: for previews, screenshot tests and fixtures in the other TrackLens
 * modules. Apps never need it; their events come from logging calls.
 */
@InternalTrackLensApi
public object TrackedEvents {
    /** Creates an event with the given fields; the rest have neutral defaults. */
    @Suppress("LongParameterList") // Mirrors the record's fields.
    public fun create(
        sequence: Long,
        name: String,
        params: Map<String, Any?> = emptyMap(),
        screen: String? = null,
        issues: List<ValidationIssue> = emptyList(),
        kind: EventKind = EventKind.EVENT,
        timestampMillis: Long = 0,
        elapsedMillis: Long = 0,
        sessionId: String = "preview",
        source: String? = null,
    ): TrackedEvent = TrackedEvent(sequence, timestampMillis, elapsedMillis, name, kind, params, screen, sessionId, source, issues)
}
