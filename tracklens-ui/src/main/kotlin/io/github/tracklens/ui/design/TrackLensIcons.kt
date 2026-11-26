package io.github.tracklens.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The few icons TrackLens needs, drawn here so the library does not depend on the large Material icons artifact.
 * 24 × 24 viewport, 2-unit strokes, tinted by `Icon`.
 */
@Suppress("MagicNumber") // Path coordinates.
internal object TrackLensIcons {
    /** Live: a pulse line. */
    val Live: ImageVector =
        stroked("Live") {
            moveTo(2f, 12f)
            horizontalLineTo(6.5f)
            lineTo(9.5f, 5f)
            lineTo(14.5f, 19f)
            lineTo(17.5f, 12f)
            horizontalLineTo(22f)
        }

    /** Sessions: a stack of rows. */
    val Sessions: ImageVector =
        stroked("Sessions") {
            for (y in listOf(6f, 12f, 18f)) {
                moveTo(4f, y)
                horizontalLineTo(20f)
            }
        }

    /** Tracking plan: a check mark in a box. */
    val Plan: ImageVector =
        stroked("Plan") {
            moveTo(5f, 3.5f)
            horizontalLineTo(19f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.5f, dy1 = 1.5f)
            verticalLineTo(19f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.5f, dy1 = 1.5f)
            horizontalLineTo(5f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.5f, dy1 = -1.5f)
            verticalLineTo(5f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.5f, dy1 = -1.5f)
            close()
            moveTo(7.5f, 12.5f)
            lineTo(10.5f, 15.5f)
            lineTo(16.5f, 8.5f)
        }

    /** More: three dots. */
    val More: ImageVector =
        ImageVector
            .Builder("More", 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black)) {
                for (x in listOf(5f, 12f, 19f)) circle(x, 12f, 2f)
            }.build()

    /** Search: a magnifying glass. */
    val Search: ImageVector =
        stroked("Search") {
            circle(10.5f, 10.5f, 6f)
            moveTo(15f, 15f)
            lineTo(20f, 20f)
        }

    /** Close / clear text: a cross. */
    val Close: ImageVector =
        stroked("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }

    /** Pause: two bars. */
    val Pause: ImageVector =
        stroked("Pause") {
            moveTo(8.5f, 5f)
            verticalLineTo(19f)
            moveTo(15.5f, 5f)
            verticalLineTo(19f)
        }

    /** Resume: a play triangle. */
    val Play: ImageVector =
        stroked("Play") {
            moveTo(7f, 4.5f)
            lineTo(19f, 12f)
            lineTo(7f, 19.5f)
            close()
        }

    /** Clear the list: a bin. */
    val Clear: ImageVector =
        stroked("Clear") {
            moveTo(4f, 6.5f)
            horizontalLineTo(20f)
            moveTo(9.5f, 6.5f)
            verticalLineTo(4f)
            horizontalLineTo(14.5f)
            verticalLineTo(6.5f)
            moveTo(6f, 6.5f)
            lineTo(7f, 20f)
            horizontalLineTo(17f)
            lineTo(18f, 6.5f)
            moveTo(10f, 10.5f)
            verticalLineTo(16f)
            moveTo(14f, 10.5f)
            verticalLineTo(16f)
        }

    /** Follow the newest events: an arrow up to a line. */
    val Follow: ImageVector =
        stroked("Follow") {
            moveTo(5f, 4f)
            horizontalLineTo(19f)
            moveTo(12f, 20f)
            verticalLineTo(8f)
            moveTo(7f, 13f)
            lineTo(12f, 8f)
            lineTo(17f, 13f)
        }

    /** Back: an arrow pointing to the start. Mirrored in right-to-left layouts by `autoMirror`. */
    val Back: ImageVector =
        stroked("Back", autoMirror = true) {
            moveTo(20f, 12f)
            horizontalLineTo(4.5f)
            moveTo(10.5f, 6f)
            lineTo(4.5f, 12f)
            lineTo(10.5f, 18f)
        }

    /** Previous: a chevron pointing to the start. */
    val Previous: ImageVector =
        stroked("Previous", autoMirror = true) {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }

    /** Next: a chevron pointing to the end. */
    val Next: ImageVector =
        stroked("Next", autoMirror = true) {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }

    /** Copy: two overlapping sheets. */
    val Copy: ImageVector =
        stroked("Copy") {
            moveTo(9f, 8f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(9f)
            close()
            moveTo(5f, 16f)
            verticalLineTo(4f)
            horizontalLineTo(15f)
        }

    /** Error severity: a circle with an exclamation mark. */
    val Error: ImageVector =
        stroked("Error") {
            circle(12f, 12f, 9f)
            moveTo(12f, 7f)
            verticalLineTo(13f)
            moveTo(12f, 16.5f)
            verticalLineTo(17f)
        }

    /** Warning severity: a triangle with an exclamation mark. */
    val Warning: ImageVector =
        stroked("Warning") {
            moveTo(12f, 3.5f)
            lineTo(21.5f, 20f)
            horizontalLineTo(2.5f)
            close()
            moveTo(12f, 9.5f)
            verticalLineTo(14f)
            moveTo(12f, 17f)
            verticalLineTo(17.5f)
        }

    /** Info severity: a circle with an "i". */
    val Info: ImageVector =
        stroked("Info") {
            circle(12f, 12f, 9f)
            moveTo(12f, 11f)
            verticalLineTo(17f)
            moveTo(12f, 7.5f)
            verticalLineTo(8f)
        }

    private fun stroked(
        name: String,
        autoMirror: Boolean = false,
        pathBuilder: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror)
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = pathBuilder,
            ).build()

    private fun PathBuilder.circle(
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        moveTo(centerX - radius, centerY)
        arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = radius * 2, dy1 = 0f)
        arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -radius * 2, dy1 = 0f)
        close()
    }
}
