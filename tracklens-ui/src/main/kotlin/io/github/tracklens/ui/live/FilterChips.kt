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

/** Kind, Screen, Source and Severity menus plus a "Has issues" toggle; options come from the events shown. */
@Composable
internal fun FilterChips(
    filter: LiveFilter,
    options: List<TrackedEvent>,
    onChange: (LiveFilter) -> Unit,
) {
    val screens = remember(options) { options.mapNotNull { it.screen }.distinct().sorted() }
    val sources = remember(options) { options.mapNotNull { it.source }.distinct().sorted() }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MultiSelectChip(
            label = stringResource(R.string.tracklens_filter_kind),
            options = EventKind.entries,
            selected = filter.kinds,
            optionLabel = { stringResource(it.label) },
            onChange = { onChange(filter.copy(kinds = it)) },
        )
        MultiSelectChip(
            label = stringResource(R.string.tracklens_filter_screen),
            options = screens,
            selected = filter.screens,
            optionLabel = { it },
            onChange = { onChange(filter.copy(screens = it)) },
        )
        if (sources.isNotEmpty() || filter.sources.isNotEmpty()) {
            MultiSelectChip(
                label = stringResource(R.string.tracklens_filter_source),
                options = sources,
                selected = filter.sources,
                optionLabel = { it },
                onChange = { onChange(filter.copy(sources = it)) },
            )
        }
        SeverityChip(filter.minSeverity) { onChange(filter.copy(minSeverity = it)) }
        FilterChip(
            selected = filter.onlyWithIssues,
            onClick = { onChange(filter.copy(onlyWithIssues = !filter.onlyWithIssues)) },
            label = { Text(stringResource(R.string.tracklens_filter_issues)) },
        )
    }
}

@Composable
private fun <T> MultiSelectChip(
    label: String,
    options: List<T>,
    selected: Set<T>,
    optionLabel: @Composable (T) -> String,
    onChange: (Set<T>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected.isNotEmpty(),
            onClick = { open = true },
            label = { Text(if (selected.isEmpty()) label else stringResource(R.string.tracklens_filter_count, label, selected.size)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracklens_filter_no_options)) },
                    onClick = { open = false },
                    enabled = false,
                )
            }
            for (option in options) {
                val checked = option in selected
                DropdownMenuItem(
                    text = { Text((if (checked) "✓  " else "    ") + optionLabel(option)) },
                    onClick = { onChange(if (checked) selected - option else selected + option) },
                )
            }
            if (selected.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracklens_filter_any)) },
                    onClick = {
                        onChange(emptySet())
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SeverityChip(
    minSeverity: Severity?,
    onChange: (Severity?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.tracklens_filter_severity)
    Box {
        FilterChip(
            selected = minSeverity != null,
            onClick = { open = true },
            label = {
                Text(minSeverity?.let { stringResource(R.string.tracklens_filter_severity_at_least, stringResource(it.label)) } ?: label)
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.tracklens_filter_any)) }, onClick = {
                onChange(null)
                open = false
            })
            for (severity in Severity.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracklens_filter_severity_at_least, stringResource(severity.label))) },
                    onClick = {
                        onChange(severity)
                        open = false
                    },
                )
            }
        }
    }
}
