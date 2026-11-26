package io.github.tracklens.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * TrackLens' color schemes. The default palette is a hand-tuned teal (the bubble's color) with a slate secondary and
 * an amber tertiary; every text/background pair meets WCAG AA (4.5:1), which `TrackLensColorsTest` checks.
 */
internal object TrackLensColors {
    /** The bubble's teal, the light scheme's primary. */
    val Teal = Color(0xFF00695C)

    val Light: ColorScheme =
        lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA7F3E4),
            onPrimaryContainer = Color(0xFF00201B),
            inversePrimary = Color(0xFF84D6C6),
            secondary = Color(0xFF4A635E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCE8E1),
            onSecondaryContainer = Color(0xFF05201B),
            tertiary = Color(0xFF8D4A00),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFDCC2),
            onTertiaryContainer = Color(0xFF2E1500),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = Color(0xFFF5FBF8),
            onBackground = Color(0xFF171D1B),
            surface = Color(0xFFF5FBF8),
            onSurface = Color(0xFF171D1B),
            surfaceVariant = Color(0xFFDAE5E1),
            onSurfaceVariant = Color(0xFF3F4946),
            surfaceTint = Teal,
            inverseSurface = Color(0xFF2B3230),
            inverseOnSurface = Color(0xFFECF2EF),
            outline = Color(0xFF6F7976),
            outlineVariant = Color(0xFFBEC9C5),
            scrim = Color.Black,
            surfaceBright = Color(0xFFF5FBF8),
            surfaceDim = Color(0xFFD5DBD9),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFEFF5F2),
            surfaceContainer = Color(0xFFE9EFEC),
            surfaceContainerHigh = Color(0xFFE3EAE7),
            surfaceContainerHighest = Color(0xFFDEE4E1),
        )

    val Dark: ColorScheme =
        darkColorScheme(
            primary = Color(0xFF84D6C6),
            onPrimary = Color(0xFF003731),
            primaryContainer = Color(0xFF005047),
            onPrimaryContainer = Color(0xFFA0F2E2),
            inversePrimary = Teal,
            secondary = Color(0xFFB1CCC5),
            onSecondary = Color(0xFF1C3530),
            secondaryContainer = Color(0xFF334B46),
            onSecondaryContainer = Color(0xFFCCE8E1),
            tertiary = Color(0xFFFFB77C),
            onTertiary = Color(0xFF4C2700),
            tertiaryContainer = Color(0xFF6B3A00),
            onTertiaryContainer = Color(0xFFFFDCC2),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF0F1513),
            onBackground = Color(0xFFDEE4E1),
            surface = Color(0xFF0F1513),
            onSurface = Color(0xFFDEE4E1),
            surfaceVariant = Color(0xFF3F4946),
            onSurfaceVariant = Color(0xFFBEC9C5),
            surfaceTint = Color(0xFF84D6C6),
            inverseSurface = Color(0xFFDEE4E1),
            inverseOnSurface = Color(0xFF2B3230),
            outline = Color(0xFF899390),
            outlineVariant = Color(0xFF3F4946),
            scrim = Color.Black,
            surfaceBright = Color(0xFF343B39),
            surfaceDim = Color(0xFF0F1513),
            surfaceContainerLowest = Color(0xFF0A0F0E),
            surfaceContainerLow = Color(0xFF171D1B),
            surfaceContainer = Color(0xFF1B211F),
            surfaceContainerHigh = Color(0xFF252B2A),
            surfaceContainerHighest = Color(0xFF303634),
        )

    /**
     * The default scheme with its primary group derived from [seed] (`ThemeConfig.seedColor`). The seed's hue is kept;
     * its lightness is pushed just far enough for every pair to reach [MIN_TEXT_CONTRAST], so any seed (pale yellow,
     * near-black) gives a readable scheme.
     */
    fun fromSeed(
        seed: Color,
        dark: Boolean,
    ): ColorScheme {
        val base = if (dark) Dark else Light
        val opaque = seed.copy(alpha = 1f)
        return if (dark) {
            val onPrimary = DarkOnPrimary
            val primary =
                towards(opaque, Color.White) {
                    contrast(it, onPrimary) >= MIN_TEXT_CONTRAST &&
                        contrast(it, base.surfaceContainerHighest) >= MIN_TEXT_CONTRAST
                }
            val container =
                towards(lerp(opaque, Color.Black, CONTAINER_MIX_DARK), Color.Black) {
                    contrast(it, Color.White) >=
                        MIN_TEXT_CONTRAST + CONTAINER_HEADROOM
                }
            val onContainer =
                towards(lerp(opaque, Color.White, ON_CONTAINER_MIX), Color.White) {
                    contrast(it, container) >=
                        MIN_TEXT_CONTRAST
                }
            base.copy(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = container,
                onPrimaryContainer = onContainer,
                surfaceTint = primary,
                inversePrimary = towards(opaque, Color.Black) { contrast(it, base.inverseSurface) >= MIN_UI_CONTRAST },
            )
        } else {
            val primary =
                towards(opaque, Color.Black) {
                    contrast(it, Color.White) >= MIN_TEXT_CONTRAST &&
                        contrast(it, base.surfaceContainerHighest) >= MIN_TEXT_CONTRAST
                }
            val container =
                towards(lerp(opaque, Color.White, CONTAINER_MIX_LIGHT), Color.White) {
                    contrast(it, Color.Black) >=
                        MIN_TEXT_CONTRAST + CONTAINER_HEADROOM
                }
            val onContainer =
                towards(lerp(opaque, Color.Black, ON_CONTAINER_MIX), Color.Black) {
                    contrast(it, container) >=
                        MIN_TEXT_CONTRAST
                }
            base.copy(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = container,
                onPrimaryContainer = onContainer,
                surfaceTint = primary,
                inversePrimary = towards(opaque, Color.White) { contrast(it, base.inverseSurface) >= MIN_UI_CONTRAST },
            )
        }
    }

    /** Mixes [color] towards [target] in small steps until [good] holds; [target] itself if nothing earlier does. */
    private inline fun towards(
        color: Color,
        target: Color,
        good: (Color) -> Boolean,
    ): Color {
        for (step in 0..STEPS) {
            val candidate = lerp(color, target, step / STEPS.toFloat())
            if (good(candidate)) return candidate
        }
        return target
    }

    /** WCAG 2 contrast ratio between two opaque colors, from 1 to 21. */
    fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + WCAG_OFFSET) / (darker + WCAG_OFFSET)
    }

    /** Text on a seeded dark primary: near-black with a hint of teal. */
    private val DarkOnPrimary = Color(0xFF0B1F1C)

    /** WCAG AA for normal text. */
    const val MIN_TEXT_CONTRAST = 4.5f

    /** WCAG AA for UI components and large text. */
    const val MIN_UI_CONTRAST = 3f

    private const val WCAG_OFFSET = 0.05f
    private const val STEPS = 50
    private const val CONTAINER_MIX_LIGHT = 0.75f
    private const val CONTAINER_MIX_DARK = 0.55f
    private const val ON_CONTAINER_MIX = 0.7f
    private const val CONTAINER_HEADROOM = 2f
}
