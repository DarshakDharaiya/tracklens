package io.github.tracklens

/**
 * Bridge between TrackLens and an analytics SDK (Firebase, Amplitude, Mixpanel, Segment or your own facade).
 *
 * Implement it once per SDK and pass it to [TrackLens.wrap]. The returned delegate records every call in
 * TrackLens and then forwards it, unchanged, to yours. Only [logEvent] is required:
 *
 * ```kotlin
 * val analytics = TrackLens.wrap(source = "firebase") { name, params -> firebase.logEvent(name, params.toBundle()) }
 * ```
 */
public fun interface AnalyticsDelegate {
    /** Logs an event named [name] with [params] in the underlying SDK. */
    public fun logEvent(
        name: String,
        params: Map<String, Any?>,
    )

    /** Sets a user property in the underlying SDK. Does nothing unless overridden. */
    public fun setUserProperty(
        name: String,
        value: String?,
    ) {}

    /** Sets (or clears, with `null`) the user id in the underlying SDK. Does nothing unless overridden. */
    public fun setUserId(userId: String?) {}

    /** Constants for [AnalyticsDelegate]. */
    public companion object {
        /** Source tag used by [TrackLens.wrap] when none is given. */
        public const val DEFAULT_SOURCE: String = "default"
    }
}
