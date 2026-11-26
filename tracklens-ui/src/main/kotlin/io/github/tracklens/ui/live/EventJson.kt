package io.github.tracklens.ui.live

import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.JsonEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One event as JSON, for "Copy JSON" and the raw view in event detail. The values are encoded by core's
 * [JsonEncoder], the same encoder that writes `events.params_json`, so what is copied matches what is stored.
 */
internal object EventJson {
    fun encode(event: TrackedEvent): String = JsonEncoder.encode(toTree(event), pretty = true)

    private fun toTree(event: TrackedEvent): Map<String, Any?> =
        linkedMapOf(
            "sequence" to event.sequence,
            "name" to event.name,
            "kind" to event.kind.name,
            "timestamp" to isoUtc(event.timestampMillis),
            "timestampMillis" to event.timestampMillis,
            "elapsedMillis" to event.elapsedMillis,
            "screen" to event.screen,
            "source" to event.source,
            "sessionId" to event.sessionId,
            "params" to event.params,
            "issues" to
                event.issues.map {
                    linkedMapOf("code" to it.code, "severity" to it.severity.name, "message" to it.message, "paramKey" to it.paramKey)
                },
        )

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}
