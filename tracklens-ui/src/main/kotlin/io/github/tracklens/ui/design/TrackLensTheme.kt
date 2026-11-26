package io.github.tracklens.ui.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.tracklens.DarkMode
import io.github.tracklens.ThemeConfig

/**
 * The theme of every TrackLens screen and of the overlay's Compose content, following `TrackLensConfig.theme`:
 * dark mode, an optional seed color, and Material You dynamic color on Android 12+ when enabled.
 */
@Composable
internal fun TrackLensTheme(
    config: ThemeConfig = ThemeConfig.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark =
        when (config.darkMode) {
            DarkMode.SYSTEM -> isSystemInDarkTheme()
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
        }
    val context = LocalContext.current
    val colors =
        if (config.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            remember(config.seedColorArgb, dark) { colorSchemeFor(config.seedColorArgb, dark) }
        }
    MaterialTheme(colorScheme = colors, content = content)
}

/** The scheme for a seed (`null`: the TrackLens palette) and mode, without dynamic color. */
internal fun colorSchemeFor(
    seedArgb: Int?,
    dark: Boolean,
): ColorScheme =
    when {
        seedArgb != null -> TrackLensColors.fromSeed(Color(seedArgb), dark)
        dark -> TrackLensColors.Dark
        else -> TrackLensColors.Light
    }
