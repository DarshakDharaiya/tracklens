package io.github.tracklens.internal

import io.github.tracklens.AnalyticsDelegate
import io.github.tracklens.TrackLens

/**
 * Returned by [TrackLens.wrap]. Records each call in TrackLens first, then forwards the caller's original values to
 * [delegate]. TrackLens never throws, so forwarding always happens; exceptions from [delegate] propagate unchanged.
 */
internal class ForwardingAnalyticsDelegate(
    private val delegate: AnalyticsDelegate,
    private val source: String,
) : AnalyticsDelegate {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        TrackLens.log(name, params, source)
        delegate.logEvent(name, params)
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        TrackLens.setUserProperty(name, value, source)
        delegate.setUserProperty(name, value)
    }

    override fun setUserId(userId: String?) {
        TrackLens.setUserId(userId, source)
        delegate.setUserId(userId)
    }
}
