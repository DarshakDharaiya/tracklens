package io.github.tracklens

import java.util.concurrent.TimeUnit

/**
 * When test sessions start and end.
 *
 * @property autoStart Start a session automatically when the app comes to the foreground.
 * @property backgroundTimeoutMillis How long the app may stay in the background before an automatic session ends.
 * @property manualSessionsSurviveBackground Keep sessions started with [TrackLens.startSession] open across
 * background periods (for example while the tester switches to a payment or e-mail app).
 */
public class SessionConfig internal constructor(
    builder: Builder,
) {
    public val autoStart: Boolean = builder.autoStart
    public val backgroundTimeoutMillis: Long = builder.backgroundTimeoutMillis
    public val manualSessionsSurviveBackground: Boolean = builder.manualSessionsSurviveBackground

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [SessionConfig]. Every setting has a default, so `Builder().build()` is valid. */
    @TrackLensDsl
    public class Builder() {
        internal var autoStart = true
        internal var backgroundTimeoutMillis = DEFAULT_BACKGROUND_TIMEOUT_MILLIS
        internal var manualSessionsSurviveBackground = true

        internal constructor(config: SessionConfig) : this() {
            autoStart = config.autoStart
            backgroundTimeoutMillis = config.backgroundTimeoutMillis
            manualSessionsSurviveBackground = config.manualSessionsSurviveBackground
        }

        /** Default `true`. */
        public fun autoStart(enabled: Boolean): Builder = apply { autoStart = enabled }

        /** Default 30 seconds. Negative values are treated as 0. */
        public fun backgroundTimeoutMillis(millis: Long): Builder = apply { backgroundTimeoutMillis = millis.coerceAtLeast(0) }

        /** Same as [backgroundTimeoutMillis], in any unit. */
        public fun backgroundTimeout(
            duration: Long,
            unit: TimeUnit,
        ): Builder = backgroundTimeoutMillis(unit.toMillis(duration))

        /** Default `true`. */
        public fun manualSessionsSurviveBackground(enabled: Boolean): Builder = apply { manualSessionsSurviveBackground = enabled }

        /** Creates the configuration. */
        public fun build(): SessionConfig = SessionConfig(this)
    }

    /** Defaults for [SessionConfig]. */
    public companion object {
        private const val DEFAULT_BACKGROUND_TIMEOUT_MILLIS = 30_000L

        /** The default configuration. */
        @JvmField
        public val DEFAULT: SessionConfig = Builder().build()
    }
}
