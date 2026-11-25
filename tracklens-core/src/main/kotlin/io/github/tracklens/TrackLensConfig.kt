@file:JvmName("TrackLensConfigs")

package io.github.tracklens

/**
 * Everything [TrackLens.install] can be configured with. Build it with [trackLensConfig] in Kotlin or
 * [TrackLensConfig.Builder] in Java; anything not set keeps its default.
 *
 * ```kotlin
 * TrackLens.install(this, trackLensConfig {
 *     sessions { backgroundTimeout(30, TimeUnit.SECONDS) }
 *     redaction { keys("*_token") }
 *     validation { trackingPlanAsset("tracklens/tracking_plan.yaml") }
 * })
 * ```
 *
 * @property enabled Start enabled. Can be changed at runtime with [TrackLens.isEnabled].
 * @property mainProcessOnly Do nothing in processes other than the app's main process.
 * @property liveBufferSize Number of most recent events kept in memory for the live view.
 * @property sessions When sessions start and end.
 * @property bubble The floating bubble and other entry points.
 * @property retention How much data is kept on the device.
 * @property redaction What is hidden before anything is stored.
 * @property validation Which checks run on each event.
 * @property breadcrumbs Automatic screen tracking.
 * @property categorizer Event categories for colors in charts.
 * @property theme Appearance of the TrackLens UI.
 */
public class TrackLensConfig internal constructor(
    builder: Builder,
) {
    public val enabled: Boolean = builder.enabled
    public val mainProcessOnly: Boolean = builder.mainProcessOnly
    public val liveBufferSize: Int = builder.liveBufferSize
    public val sessions: SessionConfig = builder.sessions
    public val bubble: BubbleConfig = builder.bubble
    public val retention: RetentionConfig = builder.retention
    public val redaction: RedactionConfig = builder.redaction
    public val validation: ValidationConfig = builder.validation
    public val breadcrumbs: BreadcrumbConfig = builder.breadcrumbs
    public val categorizer: EventCategorizer = builder.categorizer
    public val theme: ThemeConfig = builder.theme

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /**
     * Builds a [TrackLensConfig]. Every setting has a default, so `Builder().build()` is valid.
     *
     * Each section can be set with a Kotlin block (`sessions { … }`, which starts from the current section) or with a
     * finished config object (`sessions(SessionConfig)`, convenient from Java).
     */
    @Suppress("TooManyFunctions") // One setter per setting, plus Kotlin-block and Java overloads per section.
    @TrackLensDsl
    public class Builder() {
        internal var enabled = true
        internal var mainProcessOnly = true
        internal var liveBufferSize = DEFAULT_LIVE_BUFFER_SIZE
        internal var sessions = SessionConfig.DEFAULT
        internal var bubble = BubbleConfig.DEFAULT
        internal var retention = RetentionConfig.DEFAULT
        internal var redaction = RedactionConfig.DEFAULT
        internal var validation = ValidationConfig.DEFAULT
        internal var breadcrumbs = BreadcrumbConfig.DEFAULT
        internal var categorizer = EventCategorizer.NONE
        internal var theme = ThemeConfig.DEFAULT

        internal constructor(config: TrackLensConfig) : this() {
            enabled = config.enabled
            mainProcessOnly = config.mainProcessOnly
            liveBufferSize = config.liveBufferSize
            sessions = config.sessions
            bubble = config.bubble
            retention = config.retention
            redaction = config.redaction
            validation = config.validation
            breadcrumbs = config.breadcrumbs
            categorizer = config.categorizer
            theme = config.theme
        }

        /** Default `true`. */
        public fun enabled(enabled: Boolean): Builder = apply { this.enabled = enabled }

        /** Default `true`. */
        public fun mainProcessOnly(enabled: Boolean): Builder = apply { mainProcessOnly = enabled }

        /** Default 2,000. Clamped to 100–50,000. */
        public fun liveBufferSize(events: Int): Builder =
            apply {
                liveBufferSize =
                    events.coerceIn(MIN_LIVE_BUFFER_SIZE, MAX_LIVE_BUFFER_SIZE)
            }

        /** Changes the session settings, starting from the current ones. */
        public fun sessions(block: SessionConfig.Builder.() -> Unit): Builder =
            apply {
                sessions =
                    sessions.newBuilder().apply(block).build()
            }

        /** Replaces the session settings. */
        public fun sessions(config: SessionConfig): Builder = apply { sessions = config }

        /** Changes the bubble settings, starting from the current ones. */
        public fun bubble(block: BubbleConfig.Builder.() -> Unit): Builder = apply { bubble = bubble.newBuilder().apply(block).build() }

        /** Replaces the bubble settings. */
        public fun bubble(config: BubbleConfig): Builder = apply { bubble = config }

        /** Changes the retention settings, starting from the current ones. */
        public fun retention(block: RetentionConfig.Builder.() -> Unit): Builder =
            apply { retention = retention.newBuilder().apply(block).build() }

        /** Replaces the retention settings. */
        public fun retention(config: RetentionConfig): Builder = apply { retention = config }

        /** Changes the redaction settings, starting from the current ones (initially the defaults). */
        public fun redaction(block: RedactionConfig.Builder.() -> Unit): Builder =
            apply { redaction = redaction.newBuilder().apply(block).build() }

        /** Replaces the redaction settings. */
        public fun redaction(config: RedactionConfig): Builder = apply { redaction = config }

        /** Changes the validation settings, starting from the current ones. */
        public fun validation(block: ValidationConfig.Builder.() -> Unit): Builder =
            apply { validation = validation.newBuilder().apply(block).build() }

        /** Replaces the validation settings. */
        public fun validation(config: ValidationConfig): Builder = apply { validation = config }

        /** Changes the screen-tracking settings, starting from the current ones. */
        public fun breadcrumbs(block: BreadcrumbConfig.Builder.() -> Unit): Builder =
            apply { breadcrumbs = breadcrumbs.newBuilder().apply(block).build() }

        /** Replaces the screen-tracking settings. */
        public fun breadcrumbs(config: BreadcrumbConfig): Builder = apply { breadcrumbs = config }

        /** Default [EventCategorizer.NONE]. */
        public fun categorizer(categorizer: EventCategorizer): Builder = apply { this.categorizer = categorizer }

        /** Changes the theme settings, starting from the current ones. */
        public fun theme(block: ThemeConfig.Builder.() -> Unit): Builder = apply { theme = theme.newBuilder().apply(block).build() }

        /** Replaces the theme settings. */
        public fun theme(config: ThemeConfig): Builder = apply { theme = config }

        /** Creates the configuration. */
        public fun build(): TrackLensConfig = TrackLensConfig(this)
    }

    /** Defaults for [TrackLensConfig]. */
    public companion object {
        private const val DEFAULT_LIVE_BUFFER_SIZE = 2_000
        private const val MIN_LIVE_BUFFER_SIZE = 100
        private const val MAX_LIVE_BUFFER_SIZE = 50_000

        /** The default configuration, used by `TrackLens.install(application)`. */
        @JvmField
        public val DEFAULT: TrackLensConfig = Builder().build()
    }
}

/** Builds a [TrackLensConfig] with the Kotlin DSL. Anything not set keeps its default. */
public inline fun trackLensConfig(block: TrackLensConfig.Builder.() -> Unit): TrackLensConfig =
    TrackLensConfig.Builder().apply(block).build()
