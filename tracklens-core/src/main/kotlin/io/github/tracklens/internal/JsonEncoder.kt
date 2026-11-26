package io.github.tracklens.internal

import io.github.tracklens.InternalTrackLensApi
import java.math.BigDecimal
import java.math.BigInteger

/**
 * JSON for TrackLens' own values: event parameters (`events.params_json`), "Copy JSON" and exports. Hand-written, no
 * serialization dependency: values come from [ParamNormalizer], so the set of types is small and known. Whole and
 * decimal numbers stay distinguishable (`3` vs `3.0`); non-finite numbers, which JSON cannot represent, become
 * strings; [UnknownValue]s become their text.
 */
@InternalTrackLensApi
public object JsonEncoder {
    /** [value] as compact JSON (`{"a":1}`), or indented by two spaces per level when [pretty]. */
    public fun encode(
        value: Any?,
        pretty: Boolean = false,
    ): String = StringBuilder().apply { Writer(this, pretty).value(value, level = 0) }.toString()

    private class Writer(
        private val out: StringBuilder,
        private val pretty: Boolean,
    ) {
        fun value(
            value: Any?,
            level: Int,
        ) {
            when (value) {
                null -> {
                    out.append("null")
                }

                is Boolean, is Int, is Long, is Short, is Byte, is BigInteger -> {
                    out.append(value.toString())
                }

                is BigDecimal -> {
                    out.append(value.toPlainString())
                }

                is Double -> {
                    number(value)
                }

                is Float -> {
                    number(value.toDouble())
                }

                is Map<*, *> -> {
                    container('{', '}', value.entries.toList(), level) { (key, nested) ->
                        member(
                            key.toString(),
                            nested,
                            level + 1,
                        )
                    }
                }

                is Iterable<*> -> {
                    container('[', ']', value.toList(), level) { value(it, level + 1) }
                }

                is UnknownValue -> {
                    string(value.text)
                }

                else -> {
                    string(value.toString())
                }
            }
        }

        private fun member(
            key: String,
            nested: Any?,
            level: Int,
        ) {
            string(key)
            out.append(if (pretty) ": " else ":")
            value(nested, level)
        }

        private inline fun <T> container(
            open: Char,
            close: Char,
            items: List<T>,
            level: Int,
            item: (T) -> Unit,
        ) {
            if (items.isEmpty()) {
                out.append(open).append(close)
                return
            }
            out.append(open)
            items.forEachIndexed { index, element ->
                if (index > 0) out.append(',')
                newline(level + 1)
                item(element)
            }
            newline(level)
            out.append(close)
        }

        private fun newline(level: Int) {
            if (!pretty) return
            out.append('\n')
            repeat(level * INDENT) { out.append(' ') }
        }

        /** Finite doubles print as valid JSON numbers ("9.99", "1.0E20"); NaN and infinities do not. */
        private fun number(value: Double) {
            if (value.isFinite()) out.append(value.toString()) else string(value.toString())
        }

        private fun string(text: String) {
            out.append('"')
            for (char in text) {
                when (char) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
                }
            }
            out.append('"')
        }
    }

    private const val INDENT = 2
}
