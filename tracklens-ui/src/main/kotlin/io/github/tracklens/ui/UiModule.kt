package io.github.tracklens.ui

import android.app.Application
import android.content.Context
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.OverlayMode
import io.github.tracklens.internal.spi.TrackLensModule
import io.github.tracklens.internal.spi.TrackLensRuntime
import io.github.tracklens.ui.overlay.OverlayAttacher
import java.util.concurrent.Executor

/**
 * Plugs tracklens-ui into tracklens-core. Found by `ServiceLoader` through
 * `META-INF/services/io.github.tracklens.internal.spi.TrackLensModule`; kept by the consumer R8 rules.
 */
@InternalTrackLensApi
public class UiModule internal constructor(
    private val background: Executor,
) : TrackLensModule {
    /** The constructor `ServiceLoader` uses. */
    public constructor() : this(Executor { task -> Thread(task, "TrackLens-UiInstall").start() })

    override fun onInstall(
        application: Application,
        runtime: TrackLensRuntime,
    ) {
        UiRuntime.current = runtime
        // SYSTEM_OVERLAY_IF_PERMITTED falls back to IN_APP until Task 46.
        if (runtime.config.bubble.overlayMode != OverlayMode.NONE) {
            OverlayAttacher(application, runtime).install()
        }
        // A binder call to the system; keep it off the app's startup path.
        background.execute {
            runCatching { LauncherShortcut.update(application, runtime.config.bubble.showAppShortcut) }
                .onFailure { runtime.reportFailure("TrackLens launcher shortcut could not be updated", it) }
        }
    }

    override fun openPanel(context: Context): Boolean {
        context.startActivity(TrackLensActivity.intent(context))
        return true
    }
}
