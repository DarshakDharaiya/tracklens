package io.github.tracklens

/** How serious a [ValidationIssue] is. */
public enum class Severity {
    /** Worth knowing; nothing is wrong yet. */
    INFO,

    /** Probably a tracking mistake. */
    WARNING,

    /** The event breaks a hard rule: the SDK will drop or truncate it, or it violates the tracking plan. */
    ERROR,
}

/**
 * A validation finding attached to a [TrackedEvent].
 *
 * @property code Stable rule code, for example `FB_EVENT_NAME_TOO_LONG`. Use it in [ValidationConfig.Builder.severity]
 * and [ValidationConfig.Builder.disable].
 * @property severity How serious the finding is, after configured overrides.
 * @property message Human-readable explanation.
 * @property paramKey Parameter the finding is about, if it concerns a single parameter.
 */
public class ValidationIssue internal constructor(
    public val code: String,
    public val severity: Severity,
    public val message: String,
    public val paramKey: String?,
) {
    override fun toString(): String = "$severity $code: $message" + (paramKey?.let { " [$it]" } ?: "")
}

/** Factory for [ValidationIssue], for use in custom [ValidationRule]s. */
public object ValidationIssues {
    /** Creates an issue with the given [code], [severity], [message] and optional [paramKey]. */
    @JvmStatic
    @JvmOverloads
    public fun create(
        code: String,
        severity: Severity,
        message: String,
        paramKey: String? = null,
    ): ValidationIssue = ValidationIssue(code, severity, message, paramKey)
}

/**
 * A custom check, registered with [ValidationConfig.Builder.rule].
 *
 * Runs on TrackLens' background thread for every event, so it must be fast and must not block.
 * If it throws, TrackLens reports the failure in its diagnostics view and stops calling it.
 */
public fun interface ValidationRule {
    /** Returns the problems found in [event], or an empty list. */
    public fun validate(event: TrackedEvent): List<ValidationIssue>
}
