package io.github.tracklens.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tracklens.TrackLens
import io.github.tracklens.sample.DemoFlows.Flow
import io.github.tracklens.sample.sdk.SdkCallLog
import io.github.tracklens.sample.ui.theme.SampleTheme
import java.util.concurrent.Executors

/**
 * The sample's one screen: demo flows, TrackLens controls and what the fake SDKs received. Scripted runs:
 * `adb shell am start -n io.github.tracklens.sample/.MainActivity --es flow checkout` runs that flow on launch.
 */
class MainActivity : ComponentActivity() {
    private val flows get() = (application as SampleApplication).flows

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            Flow.byId(intent.getStringExtra(EXTRA_FLOW))?.let(::run)
        }
        setContent {
            SampleTheme {
                SampleScreen(onRun = ::run)
            }
        }
    }

    private fun run(flow: Flow) {
        Log.i(TAG, "Running flow ${flow.id}")
        // The burst and the ticker run in the background, as real bursts do; the rest on the main thread, like clicks.
        if (flow == Flow.BURST || flow == Flow.TICKER) background.execute { flows.run(flow) } else flows.run(flow)
    }

    companion object {
        /** Intent extra: the id of a [Flow] to run on launch. */
        const val EXTRA_FLOW = "flow"
        private const val TAG = "TrackLensSample"
        private val background = Executors.newSingleThreadExecutor()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SampleScreen(onRun: (Flow) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Sample shop") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BuildStatus()
            Section("Shopping") {
                for (flow in listOf(Flow.BROWSE, Flow.ADD_TO_CART, Flow.CHECKOUT)) FlowButton(flow, onRun)
            }
            Section("Account") {
                for (flow in listOf(Flow.SIGN_IN, Flow.SIGN_OUT, Flow.PLAY_SONG)) FlowButton(flow, onRun)
            }
            Section("Edge cases") {
                for (flow in listOf(Flow.SENSITIVE, Flow.INVALID, Flow.BURST)) FlowButton(flow, onRun)
            }
            if (TrackLens.isInstalled) {
                Section("TrackLens") {
                    val context = LocalContext.current
                    OutlinedButton(onClick = { TrackLens.openPanel(context) }) { Text("Open panel") }
                    OutlinedButton(onClick = { TrackLens.showBubble() }) { Text("Show bubble") }
                    OutlinedButton(onClick = { TrackLens.hideBubble() }) { Text("Hide bubble") }
                }
            }
            SdkCalls()
        }
    }
}

@Composable
private fun BuildStatus() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (TrackLens.isInstalled) {
                Text("Debug build: TrackLens ${TrackLens.libraryVersion} is recording", style = MaterialTheme.typography.titleSmall)
                Text("Tap the bubble, or long-press the app icon for the shortcut.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Release build: no TrackLens", style = MaterialTheme.typography.titleSmall)
                Text(
                    "tracklens-noop turns every call into a no-op; the SDKs below still receive every event.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun FlowButton(
    flow: Flow,
    onRun: (Flow) -> Unit,
) {
    FilledTonalButton(onClick = { onRun(flow) }) { Text(flow.title) }
}

/** What the fake SDKs received: the proof that analytics still works with tracklens-noop. */
@Composable
private fun SdkCalls() {
    val total by SdkCallLog.total.collectAsState()
    val recent by SdkCallLog.recent.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SDK calls ($total)", style = MaterialTheme.typography.titleMedium)
        if (recent.isEmpty()) Text("None yet. Run a flow above.", style = MaterialTheme.typography.bodySmall)
        for (call in recent) {
            Text(
                "${call.sdk}: ${call.description}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
            )
        }
    }
}
