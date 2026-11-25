package io.github.tracklens.internal

import android.os.Bundle
import io.github.tracklens.InternalTrackLensApi
import java.math.BigDecimal
import java.math.BigInteger

/**
 * A value the normalizer could not keep as itself (an arbitrary object, an enum, a mutable number type), kept as
 * its text plus its class name so the UI can show what the app passed and validation can flag the type.
 */
@InternalTrackLensApi
public class UnknownValue(
    /** Fully qualified class name of the original value. */
    public val className: String,
    /** `toString()` of the original value (truncated), or a note if it threw. */
    public val text: String,
) {
    override fun equals(other: Any?): Boolean = other is UnknownValue && other.className == className && other.text == text

    override fun hashCode(): Int = 31 * className.hashCode() + text.hashCode()

    override fun toString(): String = text
}

/**
 * Event parameters after [ParamNormalizer]: an immutable snapshot that no longer shares objects with the caller.
 *
 * Values are `null`, `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte`, `Char`, `BigDecimal`,
 * `BigInteger`, [UnknownValue], `List` of these, or `Map<String, *>` of these.
 */
@InternalTrackLensApi
public class NormalizedParams(
    /** Parameters in the caller's iteration order. */
    public val values: Map<String, Any?>,
    /** `true` if a limit cut something off (too many entries, too deep, too long). */
    public val truncated: Boolean,
) {
    override fun toString(): String = "NormalizedParams($values${if (truncated) ", truncated" else ""})"

    /** Empty parameters. */
    public companion object {
        /** No parameters. */
        @JvmField
        public val EMPTY: NormalizedParams = NormalizedParams(emptyMap(), truncated = false)
    }
}

/**
 * Copies event parameters on the caller's thread (plan section 4.2), so the app can mutate or reuse its objects
 * after the call. Keeps value types (validation needs `Int` vs `Long`), turns bundles into maps and arrays into
 * lists, and applies hard limits so no input can make TrackLens use unbounded memory or recurse forever.
 *
 * Never throws for any input: a `toString()` that throws, or a map modified concurrently by another thread, is
 * absorbed and reflected in the result.
 */
@InternalTrackLensApi
@Suppress("TooManyFunctions") // One small converter per value shape.
public object ParamNormalizer {
    /** Maximum nesting of maps, bundles and lists below the top-level parameters. */
    public const val MAX_DEPTH: Int = 3

    /** Maximum number of top-level parameters (Firebase allows 25; more are kept so validation can flag them). */
    public const val MAX_PARAMS: Int = 100

    /** Maximum number of elements kept per list or nested map (Firebase allows 200 `items`). */
    public const val MAX_COLLECTION_SIZE: Int = 250

    /** Maximum length of a string value, key or `toString()` text. */
    public const val MAX_STRING_LENGTH: Int = 1_024

    /** Text stored in place of containers nested deeper than [MAX_DEPTH]. */
    public const val TOO_DEEP: String = "<nested too deep>"

    private const val COPY_ATTEMPTS = 3
    private const val DEFAULT_LOAD_FACTOR = 0.75f

    /** Normalizes a parameter map. */
    public fun normalize(params: Map<String, Any?>): NormalizedParams =
        if (params.isEmpty()) NormalizedParams.EMPTY else copyWithRetry { copyMap(params, MAX_PARAMS, depth = 0) }

    /** Normalizes a parameter bundle. */
    public fun normalize(bundle: Bundle): NormalizedParams =
        if (bundle.isEmpty) NormalizedParams.EMPTY else copyWithRetry { copyBundle(bundle, MAX_PARAMS, depth = 0) }

    /** Copies again if the caller changed its map or bundle on another thread mid-copy. */
    private inline fun copyWithRetry(copy: Copy.() -> Map<String, Any?>): NormalizedParams {
        repeat(COPY_ATTEMPTS) {
            try {
                return Copy().run { NormalizedParams(copy(), truncated) }
            } catch (_: ConcurrentModificationException) {
                // Try again.
            }
        }
        return concurrentlyModified()
    }

    /** Initial capacity that holds [entries] without rehashing. */
    private fun mapCapacity(entries: Int): Int = (entries / DEFAULT_LOAD_FACTOR).toInt() + 1

    private fun concurrentlyModified(): NormalizedParams {
        Diagnostics.report(
            Component.PIPELINE,
            DiagnosticLevel.WARNING,
            "Event parameters kept changing on another thread while being copied; the event was recorded without them.",
        )
        return NormalizedParams(emptyMap(), truncated = true)
    }

    /** One normalization; holds the truncation flag so the recursion needs no extra allocations. */
    private class Copy {
        var truncated = false

        fun copyMap(
            source: Map<*, *>,
            limit: Int,
            depth: Int,
        ): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>(mapCapacity(minOf(source.size, limit)))
            for ((key, value) in source) {
                if (result.size == limit) {
                    truncated = true
                    break
                }
                result[key(key)] = value(value, depth)
            }
            return result
        }

        fun copyBundle(
            source: Bundle,
            limit: Int,
            depth: Int,
        ): Map<String, Any?> {
            val keys = source.keySet()
            val result = LinkedHashMap<String, Any?>(mapCapacity(minOf(keys.size, limit)))
            for (key in keys) {
                if (result.size == limit) {
                    truncated = true
                    break
                }
                @Suppress("DEPRECATION") // No non-deprecated untyped getter exists.
                result[key(key)] = value(source.get(key), depth)
            }
            return result
        }

        @Suppress("CyclomaticComplexMethod") // One branch per supported value shape.
        fun value(
            value: Any?,
            depth: Int,
        ): Any? =
            when (value) {
                null, is Int, is Long, is Double, is Float, is Boolean, is Short, is Byte, is Char, is BigDecimal, is BigInteger -> value
                is String -> text(value)
                is CharSequence -> safeToString(value)?.let(::text) ?: unknown(value)
                is Bundle -> nested(depth) { copyBundle(value, MAX_COLLECTION_SIZE, depth + 1) }
                is Map<*, *> -> nested(depth) { copyMap(value, MAX_COLLECTION_SIZE, depth + 1) }
                is Collection<*> -> nested(depth) { copyList(value.iterator(), depth) }
                is Array<*> -> nested(depth) { copyList(value.iterator(), depth) }
                is IntArray -> nested(depth) { copyList(value.iterator(), depth) }
                is LongArray -> nested(depth) { copyList(value.iterator(), depth) }
                is DoubleArray -> nested(depth) { copyList(value.iterator(), depth) }
                is FloatArray -> nested(depth) { copyList(value.iterator(), depth) }
                is BooleanArray -> nested(depth) { copyList(value.iterator(), depth) }
                is ShortArray -> nested(depth) { copyList(value.iterator(), depth) }
                is ByteArray -> nested(depth) { copyList(value.iterator(), depth) }
                is CharArray -> nested(depth) { copyList(value.iterator(), depth) }
                else -> unknown(value)
            }

        private fun copyList(
            elements: Iterator<*>,
            depth: Int,
        ): List<Any?> {
            val result = ArrayList<Any?>()
            for (element in elements) {
                if (result.size == MAX_COLLECTION_SIZE) {
                    truncated = true
                    break
                }
                result += value(element, depth + 1)
            }
            return result
        }

        private inline fun nested(
            depth: Int,
            copy: () -> Any,
        ): Any =
            if (depth >= MAX_DEPTH) {
                truncated = true
                TOO_DEEP
            } else {
                copy()
            }

        private fun key(key: Any?): String = text(key as? String ?: safeToString(key) ?: "null")

        private fun text(value: String): String =
            if (value.length <= MAX_STRING_LENGTH) {
                value
            } else {
                truncated = true
                value.substring(0, MAX_STRING_LENGTH)
            }

        private fun unknown(value: Any): UnknownValue {
            val className = value.javaClass.name
            val description = safeToString(value) ?: "<toString() of $className threw>"
            return UnknownValue(className, text(description))
        }

        /** Host objects may have a `toString()` that throws. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun safeToString(value: Any?): String? =
            try {
                value.toString()
            } catch (_: Throwable) {
                null
            }
    }
}
