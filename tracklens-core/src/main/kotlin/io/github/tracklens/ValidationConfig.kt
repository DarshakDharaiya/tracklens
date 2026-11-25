package io.github.tracklens

import java.io.InputStream

/** Supplies the tracking-plan file when it isn't bundled as an asset, for example a file pushed with `adb`. */
public fun interface TrackingPlanProvider {
    /** Opens the plan (YAML or JSON), or returns `null` if none is available. TrackLens closes the stream. */
    public fun open(): InputStream?
}

/**
 * Which checks run on every event.
 *
 * @property firebaseLimits Check Firebase Analytics limits: name lengths, parameter counts, reserved names and so on.
 * @property trackingPlanAsset Path of the tracking plan inside the app's assets, for example
 * `"tracklens/tracking_plan.yaml"`. Put the file in `src/debug/assets` so it never ships in release builds.
 * @property trackingPlanProvider Alternative source for the plan; used when [trackingPlanAsset] is `null`.
 * @property rules Custom rules, run after the built-in ones.
 * @property severityOverrides Severity to use for a rule code instead of its default.
 * @property disabledCodes Rule codes that never produce issues.
 */
public class ValidationConfig internal constructor(
    builder: Builder,
) {
    public val firebaseLimits: Boolean = builder.firebaseLimits
    public val trackingPlanAsset: String? = builder.trackingPlanAsset
    public val trackingPlanProvider: TrackingPlanProvider? = builder.trackingPlanProvider
    public val rules: List<ValidationRule> = builder.rules.toList()
    public val severityOverrides: Map<String, Severity> = builder.severityOverrides.toMap()
    public val disabledCodes: Set<String> = builder.disabledCodes.toSet()

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [ValidationConfig]. Every setting has a default, so `Builder().build()` is valid. */
    @TrackLensDsl
    public class Builder() {
        internal var firebaseLimits = true
        internal var trackingPlanAsset: String? = null
        internal var trackingPlanProvider: TrackingPlanProvider? = null
        internal val rules = mutableListOf<ValidationRule>()
        internal val severityOverrides = LinkedHashMap<String, Severity>()
        internal val disabledCodes = LinkedHashSet<String>()

        internal constructor(config: ValidationConfig) : this() {
            firebaseLimits = config.firebaseLimits
            trackingPlanAsset = config.trackingPlanAsset
            trackingPlanProvider = config.trackingPlanProvider
            rules += config.rules
            severityOverrides += config.severityOverrides
            disabledCodes += config.disabledCodes
        }

        /** Default `true`. */
        public fun firebaseLimits(enabled: Boolean): Builder = apply { firebaseLimits = enabled }

        /** Default `null` (no plan). */
        public fun trackingPlanAsset(path: String?): Builder = apply { trackingPlanAsset = path }

        /** Default `null`. */
        public fun trackingPlanProvider(provider: TrackingPlanProvider?): Builder = apply { trackingPlanProvider = provider }

        /** Adds a custom rule. */
        public fun rule(rule: ValidationRule): Builder = apply { rules += rule }

        /** Reports the rule [code] with [severity] instead of its default severity. */
        public fun severity(
            code: String,
            severity: Severity,
        ): Builder = apply { severityOverrides[code] = severity }

        /** Turns off the rules with the given [codes]. */
        public fun disable(vararg codes: String): Builder = apply { disabledCodes += codes }

        /** Creates the configuration. */
        public fun build(): ValidationConfig = ValidationConfig(this)
    }

    /** Defaults for [ValidationConfig]. */
    public companion object {
        /** The default configuration: Firebase limit checks only. */
        @JvmField
        public val DEFAULT: ValidationConfig = Builder().build()
    }
}
