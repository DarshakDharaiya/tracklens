package io.github.tracklens

/** How the user id passed to [TrackLens.setUserId] is stored. */
public enum class UserIdRedaction {
    /** Stored as given. */
    NONE,

    /** Stored as a short SHA-256 prefix, so equal ids stay recognizable without being readable. */
    HASH,

    /** Only the first and last characters are kept, for example `j***e`. */
    MASK,
}

/**
 * A pattern whose matches are redacted from parameter values.
 *
 * @property id Short identifier shown in TrackLens next to redacted values.
 * @property regex Java regular expression, matched anywhere in string values.
 */
public class RedactionPattern internal constructor(
    public val id: String,
    public val regex: String,
) {
    override fun toString(): String = "RedactionPattern($id)"

    /** Built-in patterns and a factory for custom ones. */
    public companion object {
        /** E-mail addresses. */
        @JvmField
        public val EMAIL: RedactionPattern = RedactionPattern("email", """[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

        /** Phone numbers with at least 8 digits, with optional `+`, spaces, dashes and parentheses. */
        @JvmField
        public val PHONE: RedactionPattern = RedactionPattern("phone", """\+?\d[\d ()-]{6,}\d""")

        /** Payment card numbers (13–19 digits, optionally grouped). */
        @JvmField
        public val CREDIT_CARD: RedactionPattern = RedactionPattern("card", """\b(?:\d[ -]?){12,18}\d\b""")

        /** IPv4 addresses. */
        @JvmField
        public val IPV4: RedactionPattern = RedactionPattern("ipv4", """\b(?:\d{1,3}\.){3}\d{1,3}\b""")

        /** Creates a pattern from a Java regular expression. */
        @JvmStatic
        public fun custom(
            id: String,
            regex: String,
        ): RedactionPattern = RedactionPattern(id, regex)
    }
}

/**
 * What TrackLens hides before storing or showing anything. Redaction happens before data reaches memory or disk;
 * the original values are still forwarded unchanged to your analytics SDK.
 *
 * Defaults redact common sensitive keys, e-mail addresses, phone numbers and card numbers, and hash the user id.
 * Call [Builder.clear] to start from nothing.
 *
 * @property keys Parameter keys whose values are always redacted. Case-insensitive; `*` matches any characters.
 * @property patterns Patterns whose matches are redacted inside string values.
 * @property replacement Text that replaces redacted values when [hashValues] is `false`.
 * @property hashValues Replace redacted values with a short hash instead of [replacement], so equal values can still
 * be compared.
 * @property userId How the user id is stored.
 */
public class RedactionConfig internal constructor(
    builder: Builder,
) {
    public val keys: Set<String> = builder.keys.toSet()
    public val patterns: List<RedactionPattern> = builder.patterns.toList()
    public val replacement: String = builder.replacement
    public val hashValues: Boolean = builder.hashValues
    public val userId: UserIdRedaction = builder.userId

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [RedactionConfig], starting from the defaults described on [RedactionConfig]. */
    @TrackLensDsl
    public class Builder() {
        internal val keys = linkedSetOf("password", "*token*", "email", "phone")
        internal val patterns = mutableListOf(RedactionPattern.EMAIL, RedactionPattern.PHONE, RedactionPattern.CREDIT_CARD)
        internal var replacement = "[REDACTED]"
        internal var hashValues = false
        internal var userId = UserIdRedaction.HASH
        private var customPatternCount = 0

        internal constructor(config: RedactionConfig) : this() {
            keys.clear()
            keys += config.keys
            patterns.clear()
            patterns += config.patterns
            replacement = config.replacement
            hashValues = config.hashValues
            userId = config.userId
        }

        /** Adds keys whose values are always redacted, for example `"email"` or `"*_token"`. */
        public fun keys(vararg keys: String): Builder = apply { this.keys += keys }

        /** Adds patterns whose matches are redacted inside string values. */
        public fun patterns(vararg patterns: RedactionPattern): Builder = apply { this.patterns += patterns }

        /** Adds a custom pattern from a Java regular expression. */
        public fun pattern(regex: String): Builder = patterns(RedactionPattern.custom("custom-${++customPatternCount}", regex))

        /** Removes all keys and patterns, including the defaults. */
        public fun clear(): Builder =
            apply {
                keys.clear()
                patterns.clear()
            }

        /** Default `"[REDACTED]"`. */
        public fun replacement(text: String): Builder = apply { replacement = text }

        /** Default `false`. */
        public fun hashValues(enabled: Boolean): Builder = apply { hashValues = enabled }

        /** Default [UserIdRedaction.HASH]. */
        public fun userId(mode: UserIdRedaction): Builder = apply { userId = mode }

        /** Creates the configuration. */
        public fun build(): RedactionConfig = RedactionConfig(this)
    }

    /** Defaults for [RedactionConfig]. */
    public companion object {
        /** The default configuration. */
        @JvmField
        public val DEFAULT: RedactionConfig = Builder().build()
    }
}
