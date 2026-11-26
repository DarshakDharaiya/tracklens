package io.github.tracklens.ui.live

import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.UnknownValue
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One event as JSON, for "Copy JSON" and the raw view in event detail. Hand-written (no serialization dependency):
 * values come from `ParamNormalizer`, so the set of types is small and known. Non-finite numbers, which JSON cannot
 * represent, become strings.
 */
internal object EventJson {
    fun encode(event: TrackedEvent): String = StringBuilder().apply { writeValue(toTree(event), indent = 0) }.toString()

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

    private fun StringBuilder.writeValue(
        value: Any?,
        indent: Int,
    ) {
        when (value) {
            null -> append("null")
            is Boolean, is Int, is Long, is Short, is Byte, is BigInteger -> append(value.toString())
            is BigDecimal -> append(value.toPlainString())
            is Double -> writeNumber(value)
            is Float -> writeNumber(value.toDouble())
            is Map<*, *> -> writeObject(value, indent)
            is Iterable<*> -> writeArray(value.toList(), indent)
            is UnknownValue -> writeString(value.text)
            else -> writeString(value.toString())
        }
    }

    private fun StringBuilder.writeNumber(value: Double) {
        // Finite doubles print as valid JSON numbers ("9.99", "1.0E20"); NaN and infinities do not.
        if (value.isFinite()) append(value.toString()) else writeString(value.toString())
    }

    private fun StringBuilder.writeObject(
        map: Map<*, *>,
        indent: Int,
    ) {
        if (map.isEmpty()) {
            append("{}")
            return
        }
        append("{\n")
        map.entries.forEachIndexed { index, (key, nested) ->
            pad(indent + 1)
            writeString(key.toString())
            append(": ")
            writeValue(nested, indent + 1)
            if (index < map.size - 1) append(',')
            append('\n')
        }
        pad(indent)
        append('}')
    }

    private fun StringBuilder.writeArray(
        list: List<*>,
        indent: Int,
    ) {
        if (list.isEmpty()) {
            append("[]")
            return
        }
        append("[\n")
        list.forEachIndexed { index, nested ->
            pad(indent + 1)
            writeValue(nested, indent + 1)
            if (index < list.size - 1) append(',')
            append('\n')
        }
        pad(indent)
        append(']')
    }

    private fun StringBuilder.pad(level: Int) {
        repeat(level * INDENT) { append(' ') }
    }

    private fun StringBuilder.writeString(text: String) {
        append('"')
        for (char in text) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private const val INDENT = 2
}
