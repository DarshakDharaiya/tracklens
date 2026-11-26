package io.github.tracklens.ui.live

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.tracklens.EventKind
import io.github.tracklens.Severity
import io.github.tracklens.ui.R
import io.github.tracklens.ui.design.TrackLensIcons

@get:StringRes
internal val EventKind.label: Int
    get() =
        when (this) {
            EventKind.EVENT -> R.string.tracklens_kind_event
            EventKind.USER_PROPERTY -> R.string.tracklens_kind_user_property
            EventKind.USER_ID -> R.string.tracklens_kind_user_id
            EventKind.SCREEN -> R.string.tracklens_kind_screen
        }

@get:StringRes
internal val Severity.label: Int
    get() =
        when (this) {
            Severity.INFO -> R.string.tracklens_severity_info
            Severity.WARNING -> R.string.tracklens_severity_warning
            Severity.ERROR -> R.string.tracklens_severity_error
        }

/** Severity shown by shape and color, never color alone; callers add the spoken description. */
@Composable
internal fun SeverityIcon(
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    Icon(
        when (severity) {
            Severity.INFO -> TrackLensIcons.Info
            Severity.WARNING -> TrackLensIcons.Warning
            Severity.ERROR -> TrackLensIcons.Error
        },
        contentDescription = null,
        tint = severity.color(),
        modifier = modifier.size(20.dp),
    )
}

@Composable
internal fun Severity.color(): Color =
    when (this) {
        Severity.INFO -> MaterialTheme.colorScheme.secondary
        Severity.WARNING -> MaterialTheme.colorScheme.tertiary
        Severity.ERROR -> MaterialTheme.colorScheme.error
    }
