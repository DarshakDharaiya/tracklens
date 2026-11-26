package io.github.tracklens.ui.live

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.tracklens.RedactionConfig
import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.spi.TrackLensRuntime
import io.github.tracklens.ui.R
import io.github.tracklens.ui.design.TrackLensIcons
import kotlinx.coroutines.launch

/**
 * Event detail (plan section 5.1, screen 2): header, parameters with type badges and a redaction marker, validation
 * issues, and the raw JSON with Copy. Previous/Next step through the live list without growing the back stack.
 */
@Composable
internal fun EventDetailScreen(
    runtime: TrackLensRuntime,
    sequence: Long,
    onNavigate: (sequence: Long) -> Unit,
    onBack: () -> Unit,
) {
    val snapshot by runtime.liveSnapshot.collectAsState()
    val index = snapshot.events.indexOfFirst { it.sequence == sequence }
    val event = snapshot.events.getOrNull(index)
    val previous = snapshot.events.getOrNull(index - 1)?.takeIf { index > 0 }
    val next = snapshot.events.getOrNull(index + 1)?.takeIf { index >= 0 }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = event?.name ?: "#$sequence",
            onBack = onBack,
            onPrevious = previous?.let { { onNavigate(it.sequence) } },
            onNext = next?.let { { onNavigate(it.sequence) } },
        )
        if (event == null) {
            Text(
                pluralStringResource(
                    R.plurals.tracklens_detail_missing,
                    runtime.config.liveBufferSize,
                    sequence,
                    runtime.config.liveBufferSize,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            EventDetails(event, runtime.config.redaction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(TrackLensIcons.Back, contentDescription = stringResource(R.string.tracklens_detail_back)) }
        },
        actions = {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(TrackLensIcons.Previous, contentDescription = stringResource(R.string.tracklens_detail_previous))
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(TrackLensIcons.Next, contentDescription = stringResource(R.string.tracklens_detail_next))
            }
        },
    )
}

@Composable
private fun EventDetails(
    event: TrackedEvent,
    redaction: RedactionConfig,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(stringResource(R.string.tracklens_detail_overview)) {
            Field(stringResource(R.string.tracklens_detail_sequence), "#${event.sequence}")
            Field(stringResource(R.string.tracklens_detail_kind), stringResource(event.kind.label))
            Field(stringResource(R.string.tracklens_detail_time), EventFormat.time(event.timestampMillis))
            Field(stringResource(R.string.tracklens_detail_elapsed), EventFormat.elapsed(event.elapsedMillis))
            Field(stringResource(R.string.tracklens_detail_screen), event.screen ?: "—")
            event.source?.let { Field(stringResource(R.string.tracklens_detail_source), it) }
            Field(stringResource(R.string.tracklens_detail_session), event.sessionId.take(SESSION_ID_CHARS))
        }
        Section(stringResource(R.string.tracklens_detail_params)) {
            if (event.params.isEmpty()) {
                Text(stringResource(R.string.tracklens_detail_no_params), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for ((key, value) in event.params) ParamRow(key, value, EventFormat.isRedacted(value, redaction))
        }
        Section(stringResource(R.string.tracklens_detail_issues)) {
            if (event.issues.isEmpty()) {
                Text(stringResource(R.string.tracklens_detail_no_issues), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (issue in event.issues.sortedByDescending { it.severity }) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityIcon(issue.severity)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${stringResource(issue.severity.label)} · ${issue.code}" + (issue.paramKey?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = issue.severity.color(),
                        )
                        Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        JsonSection(event)
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        content()
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ParamRow(
    key: String,
    value: Any?,
    redacted: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(LABEL_WIDTH)) {
            Text(key, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge(EventFormat.type(value), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                if (redacted) {
                    Badge(
                        stringResource(R.string.tracklens_detail_redacted),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        SelectionContainer(Modifier.weight(1f)) {
            Text(EventFormat.value(value), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Badge(
    text: String,
    background: Color,
    foreground: Color,
) {
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun JsonSection(event: TrackedEvent) {
    val json = remember(event) { EventJson.encode(event) }
    val colors = JsonColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    val highlighted = remember(json, colors) { highlightJson(json, colors) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(event) { mutableStateOf(false) }
    Section(stringResource(R.string.tracklens_detail_json)) {
        OutlinedButton(onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("TrackLens event #${event.sequence}", json)))
                copied = true
            }
        }) {
            Icon(TrackLensIcons.Copy, contentDescription = null)
            Text(
                stringResource(if (copied) R.string.tracklens_detail_copied else R.string.tracklens_detail_copy),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    highlighted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }
    }
}

internal data class JsonColors(
    val key: Color,
    val string: Color,
    val literal: Color,
)

/** Colors keys, strings and literals of [json] (as written by [EventJson]); not a general JSON parser. */
internal fun highlightJson(
    json: String,
    colors: JsonColors,
): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < json.length) {
            val char = json[index]
            when {
                char == '"' -> {
                    val end = stringEnd(json, index)
                    val isKey = json.startsWith(":", end + 1)
                    withStyle(SpanStyle(color = if (isKey) colors.key else colors.string)) { append(json, index, end + 1) }
                    index = end + 1
                }

                char == '-' || char.isDigit() || json.startsWith("true", index) || json.startsWith("false", index) ||
                    json.startsWith("null", index) -> {
                    var end = index
                    while (end < json.length && json[end] !in ",\n]} ") end++
                    withStyle(SpanStyle(color = colors.literal)) { append(json, index, end) }
                    index = end
                }

                else -> {
                    append(char)
                    index++
                }
            }
        }
    }

/** Index of the quote closing the string that starts at [start]. */
private fun stringEnd(
    json: String,
    start: Int,
): Int {
    var index = start + 1
    while (index < json.length) {
        when (json[index]) {
            '\\' -> index += 2
            '"' -> return index
            else -> index++
        }
    }
    return json.length - 1
}

private val LABEL_WIDTH = 120.dp
private const val SESSION_ID_CHARS = 8
