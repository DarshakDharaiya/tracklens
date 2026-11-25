package io.github.tracklens

import java.util.concurrent.TimeUnit

/**
 * How much recorded data TrackLens keeps on the device. Pinned sessions and the active session are never deleted.
 *
 * @property maxSessions Number of finished sessions kept; older ones are deleted first.
 * @property maxAgeMillis Finished sessions older than this are deleted.
 * @property maxEventsPerSession After this many events a session stops being stored (the live view continues)
 * and is marked as truncated.
 */
public class RetentionConfig internal constructor(
    builder: Builder,
) {
    public val maxSessions: Int = builder.maxSessions
    public val maxAgeMillis: Long = builder.maxAgeMillis
    public val maxEventsPerSession: Int = builder.maxEventsPerSession

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [RetentionConfig]. Every setting has a default, so `Builder().build()` is valid. */
    @TrackLensDsl
    public class Builder() {
        internal var maxSessions = DEFAULT_MAX_SESSIONS
        internal var maxAgeMillis = TimeUnit.DAYS.toMillis(DEFAULT_MAX_AGE_DAYS)
        internal var maxEventsPerSession = DEFAULT_MAX_EVENTS_PER_SESSION

        internal constructor(config: RetentionConfig) : this() {
            maxSessions = config.maxSessions
            maxAgeMillis = config.maxAgeMillis
            maxEventsPerSession = config.maxEventsPerSession
        }

        /** Default 50. Values below 1 are treated as 1. */
        public fun maxSessions(count: Int): Builder = apply { maxSessions = count.coerceAtLeast(1) }

        /** Default 7 days. Values below one minute are treated as one minute. */
        public fun maxAgeMillis(millis: Long): Builder = apply { maxAgeMillis = millis.coerceAtLeast(MIN_AGE_MILLIS) }

        /** Same as [maxAgeMillis], in any unit. */
        public fun maxAge(
            duration: Long,
            unit: TimeUnit,
        ): Builder = maxAgeMillis(unit.toMillis(duration))

        /** Default 10,000. Values below 100 are treated as 100. */
        public fun maxEventsPerSession(count: Int): Builder = apply { maxEventsPerSession = count.coerceAtLeast(MIN_EVENTS_PER_SESSION) }

        /** Creates the configuration. */
        public fun build(): RetentionConfig = RetentionConfig(this)
    }

    /** Defaults for [RetentionConfig]. */
    public companion object {
        private const val DEFAULT_MAX_SESSIONS = 50
        private const val DEFAULT_MAX_AGE_DAYS = 7L
        private const val DEFAULT_MAX_EVENTS_PER_SESSION = 10_000
        private const val MIN_AGE_MILLIS = 60_000L
        private const val MIN_EVENTS_PER_SESSION = 100

        /** The default configuration. */
        @JvmField
        public val DEFAULT: RetentionConfig = Builder().build()
    }
}
