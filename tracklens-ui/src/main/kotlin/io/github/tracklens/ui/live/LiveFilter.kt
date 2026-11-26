package io.github.tracklens.ui.live

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import io.github.tracklens.EventKind
import io.github.tracklens.Severity
import io.github.tracklens.TrackedEvent

/**
 * The Live screen's search and filter chips (plan section 5.3). An empty set means "any". Free text matches the event
 * name, screen, parameter keys and parameter values, ignoring case. The category chip arrives with tracking-plan
 * categories (Task 38); events carry none before that.
 */
internal data class LiveFilter(
    val query: String = "",
    val kinds: Set<EventKind> = emptySet(),
    val screens: Set<String> = emptySet(),
    val sources: Set<String> = emptySet(),
    val onlyWithIssues: Boolean = false,
    /** Only events with at least one issue this severe or worse. */
    val minSeverity: Severity? = null,
) {
    /** Whether anything narrows the list. */
    val isActive: Boolean get() = this != NONE

    fun matches(event: TrackedEvent): Boolean =
        (kinds.isEmpty() || event.kind in kinds) &&
            (screens.isEmpty() || event.screen in screens) &&
            (sources.isEmpty() || event.source in sources) &&
            (!onlyWithIssues || event.issues.isNotEmpty()) &&
            severityMatches(event) &&
            queryMatches(event)

    /** The matching events, in their original order. */
    fun apply(events: List<TrackedEvent>): List<TrackedEvent> = if (isActive) events.filter(::matches) else events

    private fun severityMatches(event: TrackedEvent): Boolean {
        val min = minSeverity ?: return true
        return event.issues.any { it.severity >= min }
    }

    private fun queryMatches(event: TrackedEvent): Boolean {
        val text = query.trim()
        if (text.isEmpty()) return true
        return event.name.contains(text, ignoreCase = true) ||
            event.screen?.contains(text, ignoreCase = true) == true ||
            event.params.any { (key, value) -> key.contains(text, ignoreCase = true) || valueContains(value, text) }
    }

    private fun valueContains(
        value: Any?,
        text: String,
    ): Boolean =
        when (value) {
            null -> false
            is Map<*, *> -> value.any { (key, nested) -> key.toString().contains(text, ignoreCase = true) || valueContains(nested, text) }
            is Iterable<*> -> value.any { valueContains(it, text) }
            else -> value.toString().contains(text, ignoreCase = true)
        }

    companion object {
        val NONE = LiveFilter()

        private const val QUERY = "query"
        private const val KINDS = "kinds"
        private const val SCREENS = "screens"
        private const val SOURCES = "sources"
        private const val ONLY_ISSUES = "onlyIssues"
        private const val MIN_SEVERITY = "minSeverity"

        /** Keeps the filter across rotation and process death. */
        val Saver: Saver<LiveFilter, Any> =
            mapSaver(
                save = { filter ->
                    mapOf(
                        QUERY to filter.query,
                        KINDS to ArrayList(filter.kinds.map { it.name }),
                        SCREENS to ArrayList(filter.screens),
                        SOURCES to ArrayList(filter.sources),
                        ONLY_ISSUES to filter.onlyWithIssues,
                        MIN_SEVERITY to filter.minSeverity?.name,
                    )
                },
                restore = { saved ->
                    LiveFilter(
                        query = saved[QUERY] as? String ?: "",
                        kinds = strings(saved[KINDS]).mapNotNull { name -> EventKind.entries.firstOrNull { it.name == name } }.toSet(),
                        screens = strings(saved[SCREENS]).toSet(),
                        sources = strings(saved[SOURCES]).toSet(),
                        onlyWithIssues = saved[ONLY_ISSUES] as? Boolean ?: false,
                        minSeverity = (saved[MIN_SEVERITY] as? String)?.let { name -> Severity.entries.firstOrNull { it.name == name } },
                    )
                },
            )

        private fun strings(value: Any?): List<String> = (value as? List<*>)?.filterIsInstance<String>().orEmpty()
    }
}
