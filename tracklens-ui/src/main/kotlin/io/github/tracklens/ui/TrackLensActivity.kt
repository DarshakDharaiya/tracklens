package io.github.tracklens.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.tracklens.ThemeConfig
import io.github.tracklens.ui.design.TrackLensTheme
import io.github.tracklens.ui.overlay.NoBubbleScreen

/**
 * TrackLens' own screen (plan section 5.1). It runs in a separate task (`taskAffinity` `<applicationId>.tracklens`),
 * so it shows next to the app in Recents and Back returns to wherever the user was. Never shows the bubble.
 */
internal class TrackLensActivity :
    ComponentActivity(),
    NoBubbleScreen {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Null when Android restored this screen into a process where TrackLens was never installed.
        val runtime = UiRuntime.current
        setContent {
            TrackLensTheme(runtime?.config?.theme ?: ThemeConfig.DEFAULT) {
                TrackLensApp(runtime)
            }
        }
    }

    companion object {
        /** Opens TrackLens in its own task, from any context. */
        fun intent(context: Context): Intent =
            Intent(context, TrackLensActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
