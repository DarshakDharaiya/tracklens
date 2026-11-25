package io.github.tracklens.internal

import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.TrackedEvent

/**
 * What the live view shows: the newest events (oldest first) plus counters. Published by the pipeline at most
 * every 100 ms; read by the UI module through [io.github.tracklens.internal.spi.TrackLensRuntime].
 */
@InternalTrackLensApi
public class LiveSnapshot(
    /** The newest events, oldest first, up to `TrackLensConfig.liveBufferSize`. */
    public val events: List<TrackedEvent>,
    /** Events processed since install (including those no longer in [events]). */
    public val totalEvents: Long,
    /** Logging calls dropped because the queue was over its cap. */
    public val droppedEvents: Long,
    /** Events since the live view was last visible. */
    public val unreadCount: Int,
    /** Increases with every publication. */
    public val version: Long,
) {
    /** Shared empty snapshot. */
    public companion object {
        /** Before any event was recorded. */
        @JvmField
        public val EMPTY: LiveSnapshot = LiveSnapshot(emptyList(), totalEvents = 0, droppedEvents = 0, unreadCount = 0, version = 0)
    }
}
