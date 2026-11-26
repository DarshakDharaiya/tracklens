package io.github.tracklens.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.tracklens.ui.design.TrackLensColors.MIN_TEXT_CONTRAST
import io.github.tracklens.ui.design.TrackLensColors.MIN_UI_CONTRAST
import io.github.tracklens.ui.design.TrackLensColors.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette contrast test (Task 19): every text/background pair TrackLens uses meets WCAG AA, in light and dark,
 * for the default palette and for seed colors that are hard to get right (pale, saturated, near-black, grey).
 */
class TrackLensColorsTest {
    @Test
    fun contrastMatchesWcag() {
        assertEquals(21f, contrast(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrast(Color.Red, Color.Red), 0.01f)
        assertEquals(4.54f, contrast(Color(0xFF767676), Color.White), 0.01f)
    }

    @Test
    fun defaultPaletteMeetsWcagAa() {
        assertReadable("light", TrackLensColors.Light)
        assertReadable("dark", TrackLensColors.Dark)
    }

    @Test
    fun seededPalettesMeetWcagAa() {
        for (seed in SEEDS) {
            for (dark in listOf(false, true)) {
                assertReadable("seed #%08X %s".format(seed.toArgb(), if (dark) "dark" else "light"), TrackLensColors.fromSeed(seed, dark))
            }
        }
    }

    @Test
    fun aSeedKeepsItsHueAndOnlyReplacesThePrimaryGroup() {
        val blue = TrackLensColors.fromSeed(Color(0xFF1565C0), dark = false)

        assertTrue("blue stays blue", blue.primary.blue > blue.primary.red && blue.primary.blue > blue.primary.green)
        assertEquals(TrackLensColors.Light.secondary, blue.secondary)
        assertEquals(TrackLensColors.Light.surface, blue.surface)
        assertEquals(TrackLensColors.Light, colorSchemeFor(null, dark = false))
        assertEquals(TrackLensColors.Dark, colorSchemeFor(null, dark = true))
    }

    private fun assertReadable(
        name: String,
        scheme: ColorScheme,
    ) {
        val text =
            with(scheme) {
                listOf(
                    "primary" to (onPrimary to primary),
                    "primaryContainer" to (onPrimaryContainer to primaryContainer),
                    "secondary" to (onSecondary to secondary),
                    "secondaryContainer" to (onSecondaryContainer to secondaryContainer),
                    "tertiary" to (onTertiary to tertiary),
                    "tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                    "error" to (onError to error),
                    "errorContainer" to (onErrorContainer to errorContainer),
                    "background" to (onBackground to background),
                    "surface" to (onSurface to surface),
                    "surfaceVariant text on surface" to (onSurfaceVariant to surface),
                    "surfaceVariant" to (onSurfaceVariant to surfaceVariant),
                    "surfaceContainerHighest" to (onSurface to surfaceContainerHighest),
                    "inverseSurface" to (inverseOnSurface to inverseSurface),
                    // Text buttons and links: primary on the surfaces they sit on.
                    "primary text on surface" to (primary to surface),
                    "primary text on surfaceContainer" to (primary to surfaceContainer),
                    "tertiary text on surface" to (tertiary to surface),
                    "error text on surface" to (error to surface),
                )
            }
        val ui =
            with(scheme) {
                listOf(
                    "outline on surface" to (outline to surface),
                    "inversePrimary on inverseSurface" to (inversePrimary to inverseSurface),
                )
            }
        val failures =
            text.filter { (_, pair) -> contrast(pair.first, pair.second) < MIN_TEXT_CONTRAST }.map { it.describe() } +
                ui.filter { (_, pair) -> contrast(pair.first, pair.second) < MIN_UI_CONTRAST }.map { it.describe() }
        assertTrue("$name: ${failures.joinToString()}", failures.isEmpty())
    }

    private fun Pair<String, Pair<Color, Color>>.describe() = "$first %.2f:1".format(contrast(second.first, second.second))

    private companion object {
        val SEEDS =
            listOf(
                Color(0xFF00695C), // TrackLens teal
                Color(0xFFFFEB3B), // pale yellow
                Color(0xFF00E5FF), // bright cyan
                Color(0xFFE91E63), // saturated pink
                Color(0xFF1565C0), // blue
                Color(0xFF9E9E9E), // grey
                Color(0xFF111111), // near-black
                Color(0xFFFAFAFA), // near-white
                Color(0x806200EE), // translucent purple: alpha is ignored
            )
    }
}
