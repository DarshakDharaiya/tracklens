package io.github.tracklens.ui.overlay

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.LiveSnapshot
import io.github.tracklens.ui.R
import kotlinx.coroutines.flow.StateFlow

/** How many events the mini panel lists. */
internal const val MINI_PANEL_EVENTS = 5

/**
 * The small panel that opens when the bubble is tapped: the latest events (newest first), a pause toggle and a way
 * into the full TrackLens screen. Stateless, so it can be previewed and screenshot-tested.
 */
@Composable
internal fun MiniPanel(
    events: List<TrackedEvent>,
    totalEvents: Long,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tracklens_mini_panel_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        pluralStringResource(R.plurals.tracklens_mini_panel_total, totalEvents.toInt(), totalEvents.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val closeLabel = stringResource(R.string.tracklens_mini_panel_close)
                TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeLabel }) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (paused) {
                Text(
                    stringResource(R.string.tracklens_mini_panel_paused),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            if (events.isEmpty()) {
                Text(stringResource(R.string.tracklens_mini_panel_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                events.takeLast(MINI_PANEL_EVENTS).asReversed().forEach { EventRow(it) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onTogglePause) {
                    Text(stringResource(if (paused) R.string.tracklens_mini_panel_resume else R.string.tracklens_mini_panel_pause))
                }
                Button(onClick = onOpen) { Text(stringResource(R.string.tracklens_mini_panel_open)) }
            }
        }
    }
}

@Composable
private fun EventRow(event: TrackedEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            "#${event.sequence}",
            modifier = Modifier.width(48.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                event.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val details =
                listOfNotNull(
                    event.screen,
                    event.params.keys
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(),
                )
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (event.issues.isNotEmpty()) {
            Text("⚠", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Interim TrackLens colors for overlay UI; Task 19 replaces this with the full `TrackLensTheme`. */
@Composable
internal fun OverlayTheme(content: @Composable () -> Unit) {
    val colors =
        if (isSystemInDarkTheme()) {
            darkColorScheme(primary = TealDark, tertiary = AmberDark)
        } else {
            lightColorScheme(primary = Teal, tertiary = AmberLight)
        }
    MaterialTheme(colorScheme = colors, content = content)
}

/** The bubble's teal; white text on it is about 6:1. */
private val Teal = Color(0xFF00695C)
private val TealDark = Color(0xFF4DB6AC)

/** The paused notice. */
private val AmberLight = Color(0xFF8D4A00)
private val AmberDark = Color(0xFFFFB74D)

/** Creates the `ComposeView` hosting the mini panel, with its own lifecycle (see [OverlayViewTreeOwner]). */
internal fun createMiniPanelView(
    context: Context,
    snapshots: StateFlow<LiveSnapshot>,
    owner: OverlayViewTreeOwner,
    onOpen: () -> Unit,
    onClose: () -> Unit,
): ComposeView =
    ComposeView(context).apply {
        owner.attachTo(this)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            OverlayTheme {
                val snapshot by snapshots.collectAsState()
                var frozen by remember { mutableStateOf<LiveSnapshot?>(null) }
                val shown = frozen ?: snapshot
                MiniPanel(
                    events = shown.events,
                    totalEvents = shown.totalEvents,
                    paused = frozen != null,
                    onTogglePause = { frozen = if (frozen == null) snapshot else null },
                    onOpen = onOpen,
                    onClose = onClose,
                )
            }
        }
    }
