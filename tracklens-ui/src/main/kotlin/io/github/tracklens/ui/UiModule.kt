package io.github.tracklens.ui

import android.app.Application
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.OverlayMode
import io.github.tracklens.internal.spi.TrackLensModule
import io.github.tracklens.internal.spi.TrackLensRuntime
import io.github.tracklens.ui.overlay.OverlayAttacher

/**
 * Plugs tracklens-ui into tracklens-core. Found by `ServiceLoader` through
 * `META-INF/services/io.github.tracklens.internal.spi.TrackLensModule`; kept by the consumer R8 rules.
 */
@InternalTrackLensApi
public class UiModule : TrackLensModule {
    override fun onInstall(
        application: Application,
        runtime: TrackLensRuntime,
    ) {
        // SYSTEM_OVERLAY_IF_PERMITTED falls back to IN_APP until Task 46.
        if (runtime.config.bubble.overlayMode != OverlayMode.NONE) {
            OverlayAttacher(application, runtime).install()
        }
    }
}
