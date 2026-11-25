package io.github.tracklens.internal

import io.github.tracklens.RedactionConfig
import io.github.tracklens.RedactionPattern
import io.github.tracklens.UserIdRedaction
import java.math.BigInteger
import java.security.MessageDigest

/** Parameters after redaction, plus which top-level keys had something replaced (the UI and storage mark them). */
internal class RedactionResult(
    val values: Map<String, Any?>,
    val redactedKeys: Set<String>,
)

/**
 * Applies a [RedactionConfig] to recorded data on the pipeline thread, before anything reaches the ring buffer,
 * listeners or storage. The host's analytics SDK still receives the original values through `TrackLens.wrap`.
 *
 * - A parameter whose key matches a key rule (case-insensitive, `*` = any characters) is replaced entirely,
 *   whatever its type, at any nesting level.
 * - Matches of the value patterns are replaced inside strings and [UnknownValue] text. Long digit numbers are checked
 *   too, so a card number logged as a `Long` is caught.
 * - [RedactionPattern.CREDIT_CARD] matches are replaced only if they pass the Luhn checksum.
 *
 * Built once per configuration. An invalid custom regex is reported and skipped; redaction never throws.
 */
@Suppress("TooManyFunctions") // One small function per value shape and redaction mode.
internal class Redactor(
    config: RedactionConfig,
) {
    private class CompiledPattern(
        val regex: Regex,
        val requiresLuhn: Boolean,
    )

    private val exactKeys: Set<String> = config.keys.filterNot { '*' in it }.mapTo(HashSet()) { it.lowercase() }
    private val keyGlobs: List<Regex> = config.keys.filter { '*' in it }.map(::globToRegex)
    private val patterns: List<CompiledPattern> = config.patterns.mapNotNull(::compile)
    private val replacement = config.replacement
    private val hashValues = config.hashValues
    private val userIdMode = config.userId
    private val isNoOp = exactKeys.isEmpty() && keyGlobs.isEmpty() && patterns.isEmpty()

    fun redactParams(params: Map<String, Any?>): RedactionResult {
        if (isNoOp || params.isEmpty()) return RedactionResult(params, emptySet())
        val redactedKeys = HashSet<String>()
        val values = LinkedHashMap<String, Any?>(params.size * 2)
        for ((key, value) in params) {
            val redacted = if (isSensitiveKey(key)) replaceWhole(value) else redactValue(value)
            if (redacted !== value) redactedKeys += key
            values[key] = redacted
        }
        return RedactionResult(if (redactedKeys.isEmpty()) params else values, redactedKeys)
    }

    /** A user property is treated like a parameter named [name]. */
    fun redactUserProperty(
        name: String,
        value: String?,
    ): String? =
        when {
            value == null -> null
            isSensitiveKey(name) -> replaceWhole(value) as String
            else -> redactText(value)
        }

    fun redactUserId(userId: String?): String? =
        when {
            userId == null -> null
            userIdMode == UserIdRedaction.NONE -> userId
            userIdMode == UserIdRedaction.HASH -> hash(userId)
            else -> mask(userId)
        }

    fun isSensitiveKey(key: String): Boolean {
        if (exactKeys.isEmpty() && keyGlobs.isEmpty()) return false
        val lower = key.lowercase()
        return lower in exactKeys || keyGlobs.any { it.matches(lower) }
    }

    /** Returns [value] itself when nothing changed, so callers can detect redaction by identity. */
    private fun redactValue(value: Any?): Any? =
        when (value) {
            is String -> redactText(value)
            is UnknownValue -> redactText(value.text).let { if (it === value.text) value else UnknownValue(value.className, it) }
            is Map<*, *> -> redactMap(value)
            is List<*> -> redactList(value)
            is Long, is Int, is BigInteger -> redactNumber(value)
            else -> value
        }

    private fun redactMap(map: Map<*, *>): Map<*, *> {
        var changed = false
        val result = LinkedHashMap<String, Any?>(map.size * 2)
        for ((key, value) in map) {
            val name = key.toString()
            val redacted = if (isSensitiveKey(name)) replaceWhole(value) else redactValue(value)
            if (redacted !== value) changed = true
            result[name] = redacted
        }
        return if (changed) result else map
    }

    private fun redactList(list: List<*>): List<*> {
        var changed = false
        val result = list.map { element -> redactValue(element).also { if (it !== element) changed = true } }
        return if (changed) result else list
    }

    private fun redactNumber(number: Any): Any {
        val digits = number.toString()
        if (digits.length < MIN_NUMBER_DIGITS) return number
        val redacted = redactText(digits)
        return if (redacted === digits) number else redacted
    }

    /** Replaces pattern matches inside [text]; returns [text] itself when nothing matched. */
    fun redactText(text: String): String {
        var result = text
        for (pattern in patterns) {
            result =
                pattern.regex.replace(result) { match ->
                    if (pattern.requiresLuhn && !passesLuhn(match.value)) match.value else replacementFor(match.value)
                }
        }
        return if (result == text) text else result
    }

    private fun replaceWhole(value: Any?): Any? =
        when {
            value == null -> null
            hashValues -> hash(value.toString())
            else -> replacement
        }

    private fun replacementFor(original: String): String = if (hashValues) hash(original) else replacement

    private fun compile(pattern: RedactionPattern): CompiledPattern? =
        try {
            CompiledPattern(Regex(pattern.regex), requiresLuhn = pattern === RedactionPattern.CREDIT_CARD)
        } catch (failure: IllegalArgumentException) {
            Diagnostics.report(
                Component.PIPELINE,
                DiagnosticLevel.ERROR,
                "Redaction pattern '${pattern.id}' is not a valid regular expression and is skipped.",
                failure,
            )
            null
        }

    companion object {
        /** Numbers with fewer digits can't be card or phone numbers, so they skip pattern checks. */
        private const val MIN_NUMBER_DIGITS = 8
        private const val HASH_PREFIX = "sha256:"
        private const val HASH_HEX_CHARS = 8
        private const val MIN_CARD_DIGITS = 13
        private const val MAX_CARD_DIGITS = 19
        private const val LUHN_BASE = 10

        /** Redacts nothing; for pipelines built without a configuration (tests). */
        val NONE =
            Redactor(
                RedactionConfig
                    .Builder()
                    .clear()
                    .userId(UserIdRedaction.NONE)
                    .build(),
            )

        /** Short, stable SHA-256 prefix: equal inputs give equal output, the input can't be read back directly. */
        fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return HASH_PREFIX + digest.joinToString("") { "%02x".format(it) }.take(HASH_HEX_CHARS)
        }

        /** Keeps the first and last characters: `jane.doe` -> `j******e`. */
        fun mask(value: String): String =
            when (value.length) {
                0 -> value
                1, 2 -> "*".repeat(value.length)
                else -> value.first() + "*".repeat(value.length - 2) + value.last()
            }

        /** Luhn checksum over the digits of [candidate], which may contain spaces or dashes. */
        fun passesLuhn(candidate: String): Boolean {
            val digits = candidate.filter(Char::isDigit)
            if (digits.length !in MIN_CARD_DIGITS..MAX_CARD_DIGITS) return false
            var sum = 0
            digits.reversed().forEachIndexed { index, char ->
                var digit = char - '0'
                if (index % 2 == 1) {
                    digit *= 2
                    if (digit > LUHN_BASE - 1) digit -= LUHN_BASE - 1
                }
                sum += digit
            }
            return sum % LUHN_BASE == 0
        }

        private fun globToRegex(glob: String): Regex =
            Regex(glob.lowercase().split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }
}
