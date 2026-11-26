package io.github.tracklens.internal.spi

import android.app.Application
import android.content.Context
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.TrackedEvent
import io.github.tracklens.internal.LiveSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * A TrackLens module (UI, storage, validation) that plugs into tracklens-core without core depending on it
 * (plan section 1.3). Implementations are found with `ServiceLoader` through
 * `META-INF/services/io.github.tracklens.internal.spi.TrackLensModule` and must have a public no-argument
 * constructor. A missing module simply means that feature is absent.
 */
@InternalTrackLensApi
public interface TrackLensModule {
    /** Called once, on the main thread, from `TrackLens.install` when TrackLens is active in this process. */
    public fun onInstall(
        application: Application,
        runtime: TrackLensRuntime,
    )

    /**
     * Opens this module's TrackLens screen from [context], for `TrackLens.openPanel`. Returns `false` if the module
     * has no screen; core then asks the next module. Called on the main thread, after [onInstall].
     */
    public fun openPanel(context: Context): Boolean = false
}

/** What tracklens-core offers its modules. */
@InternalTrackLensApi
public interface TrackLensRuntime {
    /** The configuration TrackLens was installed with. */
    public val config: TrackLensConfig

    /** The live view's data; updated at most every 100 ms. */
    public val liveSnapshot: StateFlow<LiveSnapshot>

    /** Whether the floating bubble should be shown (`TrackLens.showBubble` / `hideBubble`). */
    public val bubbleVisible: StateFlow<Boolean>

    /** Shows or hides the bubble, as `TrackLens.showBubble` / `hideBubble` do. */
    public fun setBubbleVisible(visible: Boolean)

    /** The user has seen the live events: reset the unread count. */
    public fun markAllRead()

    /** Empties the live view's buffer ("Clear" on the Live screen); stored sessions are not affected. */
    public fun clearLive()

    /** Opens the TrackLens panel, as `TrackLens.openPanel` does. */
    public fun openPanel(context: Context)

    /**
     * Registers [observer] for every recorded event, after redaction. It runs on the pipeline thread, in recording
     * order, so it must only hand the event off (the storage writer buffers it).
     */
    public fun addRecordObserver(observer: RecordObserver)

    /**
     * Blocks until every call logged before this one has been recorded and passed to the observers, or
     * [timeoutMillis] passes. Returns whether it finished. For the crash flush, so the last events are stored too.
     */
    public fun awaitPipelineIdle(timeoutMillis: Long): Boolean

    /** Records a storage problem in diagnostics: an error with [failure], otherwise a warning. Never throws. */
    public fun reportStorageProblem(
        message: String,
        failure: Throwable?,
    )

    /** Records a failure of a module in TrackLens' diagnostics; never throws. */
    public fun reportFailure(
        message: String,
        failure: Throwable,
    )
}

/** Receives every recorded event on the pipeline thread (see [TrackLensRuntime.addRecordObserver]). */
@InternalTrackLensApi
public fun interface RecordObserver {
    /** [event] was recorded; [redactedKeys] are the top-level parameters redaction changed. */
    public fun onRecord(
        event: TrackedEvent,
        redactedKeys: Set<String>,
    )
}
