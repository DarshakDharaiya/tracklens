package io.github.tracklens

/** Where the floating bubble is drawn. */
public enum class OverlayMode {
    /** Inside each activity's window. Needs no permission; hidden behind dialogs and bottom sheets. */
    IN_APP,

    /**
     * Above all app windows, including dialogs, when the host app declares `SYSTEM_ALERT_WINDOW` in its debug manifest
     * and the user granted it. Falls back to [IN_APP] otherwise. TrackLens never declares the permission itself.
     */
    SYSTEM_OVERLAY_IF_PERMITTED,

    /** No bubble. Open TrackLens with the launcher shortcut or [TrackLens.openPanel]. */
    NONE,
}

/** Screen edge, respecting layout direction. */
public enum class Edge {
    /** Left in left-to-right layouts, right in right-to-left layouts. */
    START,

    /** Right in left-to-right layouts, left in right-to-left layouts. */
    END,
}

/**
 * The floating bubble and other entry points to the TrackLens panel.
 *
 * @property overlayMode Where the bubble is drawn.
 * @property initialEdge Edge the bubble snaps to before the user moves it.
 * @property showAppShortcut Add a "TrackLens" launcher shortcut (needs no permission).
 * @property excludedActivities Fully qualified class names of activities that never show the bubble.
 */
public class BubbleConfig internal constructor(
    builder: Builder,
) {
    public val overlayMode: OverlayMode = builder.overlayMode
    public val initialEdge: Edge = builder.initialEdge
    public val showAppShortcut: Boolean = builder.showAppShortcut
    public val excludedActivities: Set<String> = builder.excludedActivities.toSet()

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [BubbleConfig]. Every setting has a default, so `Builder().build()` is valid. */
    @TrackLensDsl
    public class Builder() {
        internal var overlayMode = OverlayMode.IN_APP
        internal var initialEdge = Edge.END
        internal var showAppShortcut = true
        internal val excludedActivities = LinkedHashSet<String>()

        internal constructor(config: BubbleConfig) : this() {
            overlayMode = config.overlayMode
            initialEdge = config.initialEdge
            showAppShortcut = config.showAppShortcut
            excludedActivities += config.excludedActivities
        }

        /** Default [OverlayMode.IN_APP]. */
        public fun overlayMode(mode: OverlayMode): Builder = apply { overlayMode = mode }

        /** Default [Edge.END]. */
        public fun initialEdge(edge: Edge): Builder = apply { initialEdge = edge }

        /** Default `true`. */
        public fun showAppShortcut(enabled: Boolean): Builder = apply { showAppShortcut = enabled }

        /** Adds activities (fully qualified class names) that never show the bubble. */
        public fun excludeActivities(vararg classNames: String): Builder = apply { excludedActivities += classNames }

        /** Creates the configuration. */
        public fun build(): BubbleConfig = BubbleConfig(this)
    }

    /** Defaults for [BubbleConfig]. */
    public companion object {
        /** The default configuration. */
        @JvmField
        public val DEFAULT: BubbleConfig = Builder().build()
    }
}
