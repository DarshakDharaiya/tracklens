package io.github.tracklens.ui.live

import io.github.tracklens.RedactionConfig
import io.github.tracklens.Severity
import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.UnknownValue
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** How the Live screen and event detail present events: times, parameter types and values, redaction. */
internal object EventFormat {
    /** Wall-clock time of day, to the millisecond, in the device's time zone. Main thread only. */
    private val timeOfDay = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun time(millis: Long): String {
        // Follow time zone changes while the app runs (and tests that pin one).
        timeOfDay.timeZone = TimeZone.getDefault()
        return timeOfDay.format(Date(millis))
    }

    /** Time since the session started: `+1:02.345`. */
    fun elapsed(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = millis / MILLIS_PER_SECOND % SECONDS_PER_MINUTE
        return "+%d:%02d.%03d".format(Locale.US, minutes, seconds, millis % MILLIS_PER_SECOND)
    }

    /** The type badge in the params table. Analytics SDKs care about text vs integer vs decimal. */
    fun type(value: Any?): String =
        when (value) {
            null -> "null"
            is String, is Char -> "text"
            is Int, is Long, is Short, is Byte, is BigInteger -> "integer"
            is Double, is Float, is BigDecimal -> "decimal"
            is Boolean -> "boolean"
            is Map<*, *> -> "map"
            is Iterable<*> -> "list"
            is UnknownValue -> value.className.substringAfterLast('.')
            else -> value.javaClass.simpleName
        }

    /** A value as one line of text; maps and lists in compact JSON-like form. */
    fun value(value: Any?): String =
        when (value) {
            null -> "null"
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, nested) -> "$key: ${value(nested)}" }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { value(it) }
            is BigDecimal -> value.toPlainString()
            else -> value.toString()
        }

    /**
     * Whether redaction changed this value, so the detail can mark it. TrackLens keeps only the redacted value, so this
     * recognizes the configured replacement text or hash; a value that merely contains that text is marked too.
     */
    fun isRedacted(
        value: Any?,
        redaction: RedactionConfig,
    ): Boolean =
        when (value) {
            is String -> redaction.replacement in value || (redaction.hashValues && HASH_PREFIX in value)
            is Map<*, *> -> value.values.any { isRedacted(it, redaction) }
            is Iterable<*> -> value.any { isRedacted(it, redaction) }
            else -> false
        }

    /** The most severe issue of [event], or `null` when it has none. */
    fun worstSeverity(event: TrackedEvent): Severity? = event.issues.maxOfOrNull { it.severity }

    private const val HASH_PREFIX = "sha256:"
    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
}
