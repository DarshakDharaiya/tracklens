package io.github.tracklens

/**
 * Assigns events to categories, which drive colors in the timeline and charts.
 * A category from the tracking plan takes precedence.
 */
public fun interface EventCategorizer {
    /** Returns the category of the event named [eventName], or `null` for none. */
    public fun categoryOf(eventName: String): String?

    /** Built-in categorizers. */
    public companion object {
        /** Assigns no categories. */
        @JvmField
        public val NONE: EventCategorizer = EventCategorizer { null }
    }
}
