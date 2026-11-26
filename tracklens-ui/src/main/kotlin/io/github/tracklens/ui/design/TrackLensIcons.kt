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

    private fun stroked(
        name: String,
        pathBuilder: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f)
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
