package io.github.tracklens.contract

import io.github.tracklens.AnalyticsDelegate

/** Stands in for an analytics SDK and records exactly what it receives. */
class RecordingDelegate : AnalyticsDelegate {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    val userProperties = mutableListOf<Pair<String, String?>>()
    val userIds = mutableListOf<String?>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        events += name to params
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        userProperties += name to value
    }

    override fun setUserId(userId: String?) {
        userIds += userId
    }
}
