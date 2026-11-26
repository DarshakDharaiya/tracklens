package io.github.tracklens.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tracklens.EventKind
import io.github.tracklens.Severity
import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.spi.TrackLensRuntime
import io.github.tracklens.ui.R
import io.github.tracklens.ui.design.TrackLensIcons

/**
 * One event: sequence, time, name, screen and parameter keys, kind (unless a plain event) and the worst issue.
 * Kept light, since a fling composes many rows per frame: no Surfaces, and the divider is drawn, not composed.
 */
@Composable
internal fun EventRow(
    event: TrackedEvent,
    onClick: () -> Unit,
) {
    val openLabel = stringResource(R.string.tracklens_row_open)
    val divider = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(ROW_META_WIDTH)) {
            Text("#${event.sequence}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            Text(
                EventFormat.time(event.timestampMillis),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            EventTitle(event)
            EventDetails(event)
        }
        EventFormat.worstSeverity(event)?.let { worst ->
            val description =
                pluralStringResource(R.plurals.tracklens_row_issues, event.issues.size, event.issues.size, stringResource(worst.label))
            SeverityIcon(worst, Modifier.padding(start = 8.dp).semantics { contentDescription = description })
        }
    }
}

@Composable
private fun EventTitle(event: TrackedEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            event.name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (event.kind != EventKind.EVENT) {
            Text(
                stringResource(event.kind.label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Screen and parameter keys, on one line. */
@Composable
private fun EventDetails(event: TrackedEvent) {
    val keys = event.params.keys
    val details =
        when {
            event.screen == null && keys.isEmpty() -> return
            event.screen == null -> keys.joinToString()
            keys.isEmpty() -> event.screen
            else -> event.screen + " · " + keys.joinToString()
        }
    Text(
        details.orEmpty(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val ROW_META_WIDTH = 92.dp
