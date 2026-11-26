package io.github.tracklens.ui

import io.github.tracklens.internal.spi.TrackLensRuntime

/** The runtime tracklens-core gave [UiModule], for TrackLens' activities, which Android creates on its own. */
internal object UiRuntime {
    /** Set on install; `null` before (or in a process where TrackLens is not active). */
    @Volatile
    var current: TrackLensRuntime? = null
}
