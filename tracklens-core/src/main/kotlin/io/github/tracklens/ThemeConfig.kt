package io.github.tracklens

/** Light or dark appearance of the TrackLens UI. */
public enum class DarkMode {
    /** Follow the system setting. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark. */
    DARK,
}

/**
 * Appearance of the TrackLens UI. It never affects the host app's theme.
 *
 * @property darkMode Light or dark appearance.
 * @property seedColorArgb Color (ARGB) the palette is derived from, or `null` for the TrackLens default.
 * @property dynamicColor Use the wallpaper-based palette on Android 12+. Off by default so screenshots are stable.
 */
public class ThemeConfig internal constructor(
    builder: Builder,
) {
    public val darkMode: DarkMode = builder.darkMode
    public val seedColorArgb: Int? = builder.seedColorArgb
    public val dynamicColor: Boolean = builder.dynamicColor

    /** Returns a builder pre-filled with this configuration. */
    public fun newBuilder(): Builder = Builder(this)

    /** Builds a [ThemeConfig]. Every setting has a default, so `Builder().build()` is valid. */
    @TrackLensDsl
    public class Builder() {
        internal var darkMode = DarkMode.SYSTEM
        internal var seedColorArgb: Int? = null
        internal var dynamicColor = false

        internal constructor(config: ThemeConfig) : this() {
            darkMode = config.darkMode
            seedColorArgb = config.seedColorArgb
            dynamicColor = config.dynamicColor
        }

        /** Default [DarkMode.SYSTEM]. */
        public fun darkMode(mode: DarkMode): Builder = apply { darkMode = mode }

        /** Default `null` (TrackLens palette). */
        public fun seedColor(argb: Int?): Builder = apply { seedColorArgb = argb }

        /** Default `false`. */
        public fun dynamicColor(enabled: Boolean): Builder = apply { dynamicColor = enabled }

        /** Creates the configuration. */
        public fun build(): ThemeConfig = ThemeConfig(this)
    }

    /** Defaults for [ThemeConfig]. */
    public companion object {
        /** The default configuration. */
        @JvmField
        public val DEFAULT: ThemeConfig = Builder().build()
    }
}
