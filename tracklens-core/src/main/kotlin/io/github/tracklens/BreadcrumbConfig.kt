package io.github.tracklens

/** Turns an Activity or Fragment into the screen name TrackLens shows. */
public fun interface ScreenNameResolver {
    /** Returns the name for [component] (an `Activity` or a `Fragment`), or `null` to ignore it. */
    public fun resolve(component: Any): String?

    /** Built-in resolvers. */
    public companion object {
        /** Uses the simple class name, for example `CheckoutActivity`. */
        @JvmField
        public val SIMPLE_CLASS_NAME: ScreenNameResolver = ScreenNameResolver { it.javaClass.simpleName }
    }
}

/**
 * Automatic screen tracking. Screens appear in the event list, the timeline and the screen flow.
 *
 * @property activities Record a screen when an Activity resumes.
 * @property fragments Record a screen when a visible Fragment resumes. Ignored if the app doesn't use AndroidX Fragment.
 * @property screenViewEventAsScreen Treat a `screen_view` event with a `screen_name` parameter as a screen change.
 * @property screenNameResolver How Activities and Fragments are named.
 */
public class BreadcrumbConfig internal constructor(
    builder: Builder,
) {
    public val activities: Boolean = builder.activities
    public val fragments: Boolean = builder.fragments
    public val screenViewEventAsScreen: Boolean = builder.screenViewEventAsScreen
    public val screenNameResolver: ScreenNameResolver = builder.screenNameResolver

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [BreadcrumbConfig]. Every setting has a default, so `Builder().build()` is valid. */
    @TrackLensDsl
    public class Builder() {
        internal var activities = true
        internal var fragments = true
        internal var screenViewEventAsScreen = true
        internal var screenNameResolver = ScreenNameResolver.SIMPLE_CLASS_NAME

        internal constructor(config: BreadcrumbConfig) : this() {
            activities = config.activities
            fragments = config.fragments
            screenViewEventAsScreen = config.screenViewEventAsScreen
            screenNameResolver = config.screenNameResolver
        }

        /** Default `true`. */
        public fun activities(enabled: Boolean): Builder = apply { activities = enabled }

        /** Default `true`. */
        public fun fragments(enabled: Boolean): Builder = apply { fragments = enabled }

        /** Default `true`. */
        public fun screenViewEventAsScreen(enabled: Boolean): Builder = apply { screenViewEventAsScreen = enabled }

        /** Default [ScreenNameResolver.SIMPLE_CLASS_NAME]. */
        public fun screenNameResolver(resolver: ScreenNameResolver): Builder = apply { screenNameResolver = resolver }

        /** Creates the configuration. */
        public fun build(): BreadcrumbConfig = BreadcrumbConfig(this)
    }

    /** Defaults for [BreadcrumbConfig]. */
    public companion object {
        /** The default configuration. */
        @JvmField
        public val DEFAULT: BreadcrumbConfig = Builder().build()
    }
}
