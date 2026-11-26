package io.github.tracklens.ui.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** The bottom navigation tabs, in display order. */
internal enum class Tab {
    LIVE,
    SESSIONS,
    PLAN,
    MORE,
}

/**
 * A place in TrackLens' UI (plan section 5.1). Each destination has a stable [route], which is how the back stack
 * survives configuration changes and process death without Parcelize.
 */
internal sealed interface Destination {
    /** Stable text form, e.g. `live` or `event/42`. */
    val route: String

    /** The root screen of a tab. */
    data class TabRoot(
        val tab: Tab,
    ) : Destination {
        override val route: String get() = tab.name.lowercase()
    }

    /** One event of the live session, by sequence number (Task 20). */
    data class EventDetail(
        val sequence: Long,
    ) : Destination {
        override val route: String get() = "$EVENT_PREFIX$sequence"
    }

    companion object {
        private const val EVENT_PREFIX = "event/"

        /** The destination for [route], or `null` if it is not one (an older or newer TrackLens wrote it). */
        fun parse(route: String): Destination? =
            Tab.entries.firstOrNull { it.name.lowercase() == route }?.let(::TabRoot)
                ?: route
                    .removePrefix(EVENT_PREFIX)
                    .takeIf { route.startsWith(EVENT_PREFIX) }
                    ?.toLongOrNull()
                    ?.let(::EventDetail)
    }
}

/**
 * TrackLens' back stack: immutable, never empty, and always rooted at Live. Tabs follow Material guidance: switching
 * to another tab shows its root above Live, so Back from any tab root returns to Live, and Back from Live leaves
 * TrackLens ([pop] returns `null`). Tapping the current tab again returns to its root.
 */
internal class BackStack private constructor(
    val entries: List<Destination>,
) {
    /** The screen on top. */
    val current: Destination get() = entries.last()

    /** The tab the current screen belongs to: the nearest tab root at or below the top. */
    val currentTab: Tab get() = entries.filterIsInstance<Destination.TabRoot>().last().tab

    /** Whether Back stays inside TrackLens. */
    val canGoBack: Boolean get() = entries.size > 1

    fun push(destination: Destination): BackStack = BackStack(entries + destination)

    /** Replaces the top screen (Previous/Next in event detail), so Back still returns to where it was opened. */
    fun replaceTop(destination: Destination): BackStack = if (canGoBack) BackStack(entries.dropLast(1) + destination) else push(destination)

    /** The stack after Back, or `null` when Back should leave TrackLens. */
    fun pop(): BackStack? = if (canGoBack) BackStack(entries.dropLast(1)) else null

    fun selectTab(tab: Tab): BackStack =
        when (tab) {
            Tab.LIVE -> START
            else -> BackStack(listOf(LIVE_ROOT, Destination.TabRoot(tab)))
        }

    override fun equals(other: Any?): Boolean = other is BackStack && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = entries.joinToString(" > ", prefix = "BackStack(", postfix = ")") { it.route }

    companion object {
        private val LIVE_ROOT = Destination.TabRoot(Tab.LIVE)

        /** Where TrackLens opens. */
        val START = BackStack(listOf(LIVE_ROOT))

        /** Rebuilds a stack from routes, keeping its rules: unknown routes are dropped and Live is always the root. */
        fun of(routes: List<String>): BackStack {
            val destinations = routes.mapNotNull(Destination::parse).dropWhile { it == LIVE_ROOT }
            return BackStack(listOf(LIVE_ROOT) + destinations)
        }

        /** Saves the stack as its routes, for `rememberSaveable`. */
        val Saver: Saver<BackStack, Any> = listSaver(save = { stack -> stack.entries.map { it.route } }, restore = ::of)
    }
}
