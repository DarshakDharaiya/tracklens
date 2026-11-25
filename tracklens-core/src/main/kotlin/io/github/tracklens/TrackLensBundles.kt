@file:JvmName("TrackLensBundleExtensions")

package io.github.tracklens

import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable

/**
 * Converts between the `Map` form TrackLens uses and the `Bundle` form many analytics SDKs take.
 *
 * Fully implemented in both tracklens-core and tracklens-noop, because release builds rely on it to forward
 * events to the real SDK.
 */
public object TrackLensBundles {
    /**
     * Copies [params] into a new [Bundle], keeping each value's type where `Bundle` supports it.
     *
     * Nested maps become nested bundles; lists of maps or bundles become a bundle list (the form Firebase uses for
     * `items`); lists of strings or integers keep their type. Any other value is stored as its `toString()`.
     */
    @JvmStatic
    public fun toBundle(params: Map<String, Any?>): Bundle {
        val bundle = Bundle(params.size)
        for ((key, value) in params) bundle.putValue(key, value)
        return bundle
    }

    /**
     * Copies [bundle] into a new map, preserving key order. Nested bundles become nested maps, and bundle arrays
     * or lists become lists of maps.
     */
    @JvmStatic
    public fun toMap(bundle: Bundle): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>(bundle.size())
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION") // No non-deprecated untyped getter exists.
            result[key] = fromBundleValue(bundle.get(key))
        }
        return result
    }

    @Suppress("CyclomaticComplexMethod") // One branch per Bundle value type.
    private fun Bundle.putValue(
        key: String,
        value: Any?,
    ) {
        when (value) {
            null -> putString(key, null)
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Short -> putShort(key, value)
            is Byte -> putByte(key, value)
            is Char -> putChar(key, value)
            is CharSequence -> putCharSequence(key, value)
            is Bundle -> putBundle(key, value)
            is Map<*, *> -> putBundle(key, toBundle(value.stringKeys()))
            is IntArray -> putIntArray(key, value)
            is LongArray -> putLongArray(key, value)
            is DoubleArray -> putDoubleArray(key, value)
            is FloatArray -> putFloatArray(key, value)
            is BooleanArray -> putBooleanArray(key, value)
            is Array<*> -> putList(key, value.asList())
            is Collection<*> -> putList(key, value)
            is Parcelable -> putParcelable(key, value)
            is Serializable -> putSerializable(key, value)
            else -> putString(key, value.toString())
        }
    }

    private fun Bundle.putList(
        key: String,
        values: Collection<*>,
    ) {
        when {
            values.all { it is Bundle || it is Map<*, *> } -> {
                putParcelableArrayList(key, values.mapTo(ArrayList()) { it as? Bundle ?: toBundle((it as Map<*, *>).stringKeys()) })
            }

            values.all { it is String } -> {
                putStringArrayList(key, values.mapTo(ArrayList()) { it as String })
            }

            values.all { it is Int } -> {
                putIntegerArrayList(key, values.mapTo(ArrayList()) { it as Int })
            }

            else -> {
                putString(key, values.toString())
            }
        }
    }

    private fun fromBundleValue(value: Any?): Any? =
        when (value) {
            is Bundle -> toMap(value)
            is Array<*> -> if (value.all { it is Bundle }) value.map { toMap(it as Bundle) } else value
            is List<*> -> if (value.isNotEmpty() && value.all { it is Bundle }) value.map { toMap(it as Bundle) } else value
            else -> value
        }

    private fun Map<*, *>.stringKeys(): Map<String, Any?> = entries.associate { (k, v) -> k.toString() to v }
}

/** Shorthand for [TrackLensBundles.toBundle]. */
public fun Map<String, Any?>.toBundle(): Bundle = TrackLensBundles.toBundle(this)
