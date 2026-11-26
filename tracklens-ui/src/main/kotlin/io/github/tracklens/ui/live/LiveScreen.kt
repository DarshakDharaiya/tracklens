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
import io.github.tracklens.ui.design.Banner
import io.github.tracklens.ui.design.EmptyState
import io.github.tracklens.ui.design.TrackLensIcons

/**
 * The Live tab (plan section 5.1, screen 1): every event of this run, newest first, streaming in as the app logs.
 * Search, filter chips, pause (holds new events back), follow-newest and clear. Seeing this screen marks events read.
 */
@Composable
internal fun LiveScreen(
    runtime: TrackLensRuntime,
    onOpenEvent: (sequence: Long) -> Unit,
) {
    val snapshot by runtime.liveSnapshot.collectAsState()
    var filter by rememberSaveable(stateSaver = LiveFilter.Saver) { mutableStateOf(LiveFilter.NONE) }
    // Paused at this sequence number: the list shows nothing newer until Resume. Survives rotation.
    var pausedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var follow by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(snapshot.unreadCount) { if (snapshot.unreadCount > 0) runtime.markAllRead() }

    val latest = snapshot.events.lastOrNull()?.sequence ?: 0L
    val shown = remember(snapshot, pausedAt) { upTo(snapshot.events, pausedAt) }
    val visible = remember(shown, filter) { filter.apply(shown).asReversed() }

    val listState = rememberLazyListState()
    FollowNewest(listState, newest = visible.firstOrNull()?.sequence, follow = follow, onUserScroll = { follow = false })

    Column(Modifier.fillMaxSize()) {
        LiveTopBar(
            count = visible.size,
            paused = pausedAt != null,
            follow = follow,
            onTogglePause = { pausedAt = if (pausedAt == null) latest else null },
            onToggleFollow = { follow = !follow },
            onClear = {
                pausedAt = null
                runtime.clearLive()
            },
        )
        SearchField(filter.query) { filter = filter.copy(query = it) }
        FilterChips(filter, options = shown) { filter = it }
        LiveBanners(
            heldBack = pausedAt?.let { at -> (latest - at).coerceAtLeast(0).toInt() },
            dropped = snapshot.droppedEvents,
            onResume = { pausedAt = null },
        )
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            when {
                visible.isNotEmpty() -> EventList(visible, listState, onOpenEvent)
                filter.isActive && shown.isNotEmpty() -> NoMatches(onClearFilters = { filter = LiveFilter.NONE })
                else -> EmptyState(stringResource(R.string.tracklens_live_empty))
            }
            if (!follow && visible.isNotEmpty()) {
                JumpToNewest(Modifier.align(Alignment.BottomCenter)) { follow = true }
            }
        }
    }
}

/** The events up to [pausedAt], or all of them when not paused. */
private fun upTo(
    events: List<TrackedEvent>,
    pausedAt: Long?,
): List<TrackedEvent> = if (pausedAt == null) events else events.filter { it.sequence <= pausedAt }

/** Keeps the list at the newest event while following; a drag by the user stops following. */
@Composable
private fun FollowNewest(
    listState: LazyListState,
    newest: Long?,
    follow: Boolean,
    onUserScroll: () -> Unit,
) {
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) onUserScroll() }
    }
    LaunchedEffect(newest, follow) {
        if (follow && newest != null) listState.scrollToItem(0)
    }
}

/** Newest first; rows are keyed by sequence so they keep their place while events stream in. */
@Composable
private fun EventList(
    events: List<TrackedEvent>,
    listState: LazyListState,
    onOpenEvent: (sequence: Long) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(events, key = { it.sequence }) { event ->
            EventRow(event, onClick = { onOpenEvent(event.sequence) })
        }
    }
}

/** "Paused · N held back" with Resume, and the dropped-calls warning. */
@Composable
private fun LiveBanners(
    heldBack: Int?,
    dropped: Long,
    onResume: () -> Unit,
) {
    if (heldBack != null) {
        Banner(
            pluralStringResource(R.plurals.tracklens_live_paused_banner, heldBack, heldBack),
            action = stringResource(R.string.tracklens_live_resume),
            onAction = onResume,
        )
    }
    if (dropped > 0) {
        val count = dropped.toInt()
        Banner(pluralStringResource(R.plurals.tracklens_live_dropped_banner, count, count))
    }
}

@Composable
private fun NoMatches(onClearFilters: () -> Unit) =
    EmptyState(
        stringResource(R.string.tracklens_live_no_matches),
        action = stringResource(R.string.tracklens_live_clear_filters),
        onAction = onClearFilters,
    )

@Composable
private fun JumpToNewest(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = modifier.padding(12.dp),
    ) {
        Text(
            stringResource(R.string.tracklens_live_jump_newest),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTopBar(
    count: Int,
    paused: Boolean,
    follow: Boolean,
    onTogglePause: () -> Unit,
    onToggleFollow: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.tracklens_tab_live))
                Text(pluralStringResource(R.plurals.tracklens_live_count, count, count), style = MaterialTheme.typography.bodySmall)
            }
        },
        actions = {
            IconButton(onClick = onTogglePause) {
                Icon(
                    if (paused) TrackLensIcons.Play else TrackLensIcons.Pause,
                    contentDescription = stringResource(if (paused) R.string.tracklens_live_resume else R.string.tracklens_live_pause),
                )
            }
            IconToggleButton(checked = follow, onCheckedChange = { onToggleFollow() }) {
                Icon(TrackLensIcons.Follow, contentDescription = stringResource(R.string.tracklens_live_follow))
            }
            IconButton(onClick = onClear) {
                Icon(TrackLensIcons.Clear, contentDescription = stringResource(R.string.tracklens_live_clear))
            }
        },
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.tracklens_live_search_hint)) },
        leadingIcon = { Icon(TrackLensIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(TrackLensIcons.Close, contentDescription = stringResource(R.string.tracklens_live_search_clear))
                }
            }
        },
    )
}
