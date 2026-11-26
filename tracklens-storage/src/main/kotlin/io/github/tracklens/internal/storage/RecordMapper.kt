package io.github.tracklens.internal.storage

import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.Severity
import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.JsonEncoder
import io.github.tracklens.internal.UnknownValue
import java.math.BigDecimal
import java.math.BigInteger

/** A recorded event waiting in the writer's buffer, with the parameter keys redaction changed. */
@InternalTrackLensApi
public class PendingRecord(
    /** The event as recorded (already redacted). */
    public val event: TrackedEvent,
    /** Top-level parameters redaction changed. */
    public val redactedKeys: Set<String>,
)

/** How one [PendingRecord] becomes rows: the event, its parameter rows and its warnings. */
@InternalTrackLensApi
public class MappedRecord(
    /** The `events` row; `id` 0 until inserted. */
    public val event: EventEntity,
    /** The `event_params` rows; event and session keys filled in on insert. */
    public val params: List<EventParamEntity>,
    /** The `warnings` rows for its issues. */
    public val warnings: List<WarningEntity>,
)

/** Turns recorded events into rows (plan section 3.2). Pure; runs on the writer thread. */
@InternalTrackLensApi
public object RecordMapper {
    /** Maps [record] for the session with row key [sessionId]. */
    public fun map(
        record: PendingRecord,
        sessionId: Long,
    ): MappedRecord {
        val event = record.event
        val warnings =
            event.issues.map {
                WarningEntity(
                    sessionId = sessionId,
                    code = it.code,
                    severity = level(it.severity),
                    message = it.message,
                    paramKey = it.paramKey,
                )
            }
        return MappedRecord(
            event =
                EventEntity(
                    sessionId = sessionId,
                    seq = event.sequence,
                    timestamp = event.timestampMillis,
                    elapsedMs = event.elapsedMillis,
                    kind = event.kind,
                    name = event.name,
                    source = event.source,
                    screenName = event.screen,
                    paramsJson = JsonEncoder.encode(event.params),
                    paramCount = event.params.size,
                    maxSeverity = warnings.maxOfOrNull { it.severity } ?: SeverityLevel.NONE,
                ),
            params = event.params.map { (key, value) -> param(sessionId, key, value, key in record.redactedKeys) },
            warnings = warnings,
        )
    }

    /** The `warnings.severity` / `events.max_severity` level of [severity]. */
    public fun level(severity: Severity): Int =
        when (severity) {
            Severity.INFO -> SeverityLevel.INFO
            Severity.WARNING -> SeverityLevel.WARNING
            Severity.ERROR -> SeverityLevel.ERROR
        }

    private fun param(
        sessionId: Long,
        key: String,
        value: Any?,
        redacted: Boolean,
    ): EventParamEntity {
        val type = type(value)
        val text =
            when (type) {
                ParamValueType.NULL -> null
                ParamValueType.ARRAY, ParamValueType.BUNDLE -> JsonEncoder.encode(value)
                ParamValueType.DOUBLE -> (value as? BigDecimal)?.toPlainString() ?: value.toString()
                else -> (value as? UnknownValue)?.text ?: value.toString()
            }
        val number = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
        return EventParamEntity(
            eventId = 0,
            sessionId = sessionId,
            key = key,
            valueType = type,
            valueText = text,
            valueNum = number,
            redacted = redacted,
        )
    }

    private fun type(value: Any?): ParamValueType =
        when (value) {
            null -> ParamValueType.NULL
            is String, is Char -> ParamValueType.STRING
            is Long, is Int, is Short, is Byte, is BigInteger -> ParamValueType.LONG
            is Double, is Float, is BigDecimal -> ParamValueType.DOUBLE
            is Boolean -> ParamValueType.BOOLEAN
            is Map<*, *> -> ParamValueType.BUNDLE
            is Iterable<*> -> ParamValueType.ARRAY
            else -> ParamValueType.OTHER
        }
}
