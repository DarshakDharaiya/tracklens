package io.github.tracklens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tracklens.ui.design.TrackLensIcons
import io.github.tracklens.ui.navigation.BackStack
import io.github.tracklens.ui.navigation.Destination
import io.github.tracklens.ui.navigation.Tab

/**
 * TrackLens' screen: a back stack ([BackStack], saved across recreation) and bottom navigation. Back pops the stack
 * through [BackHandler], which supports predictive back; at Live, Back is left to the activity, which closes.
 * The tab screens are filled in by Tasks 20 onwards.
 */
@Composable
internal fun TrackLensApp(installed: Boolean) {
    var stack by rememberSaveable(stateSaver = BackStack.Saver) { mutableStateOf(BackStack.START) }
    BackHandler(enabled = stack.canGoBack) { stack.pop()?.let { stack = it } }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (tab in Tab.entries) {
                    val label = stringResource(tab.label)
                    NavigationBarItem(
                        selected = stack.currentTab == tab,
                        onClick = { stack = stack.selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        if (installed) Screen(stack.current, padding) else NotInstalled(padding)
    }
}

@Composable
private fun Screen(
    destination: Destination,
    padding: PaddingValues,
) {
    when (destination) {
        is Destination.TabRoot -> {
            Placeholder(
                stringResource(destination.tab.label),
                stringResource(R.string.tracklens_screen_coming_soon),
                padding,
            )
        }

        is Destination.EventDetail -> {
            Placeholder(
                "#${destination.sequence}",
                stringResource(R.string.tracklens_screen_coming_soon),
                padding,
            )
        }
    }
}

@Composable
private fun NotInstalled(padding: PaddingValues) =
    Placeholder(stringResource(R.string.tracklens_title), stringResource(R.string.tracklens_not_installed), padding)

/** A centered title and explanation; also the shape of the empty states to come. */
@Composable
private fun Placeholder(
    title: String,
    body: String,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val Tab.label: Int
    get() =
        when (this) {
            Tab.LIVE -> R.string.tracklens_tab_live
            Tab.SESSIONS -> R.string.tracklens_tab_sessions
            Tab.PLAN -> R.string.tracklens_tab_plan
            Tab.MORE -> R.string.tracklens_tab_more
        }

private val Tab.icon: ImageVector
    get() =
        when (this) {
            Tab.LIVE -> TrackLensIcons.Live
            Tab.SESSIONS -> TrackLensIcons.Sessions
            Tab.PLAN -> TrackLensIcons.Plan
            Tab.MORE -> TrackLensIcons.More
        }
