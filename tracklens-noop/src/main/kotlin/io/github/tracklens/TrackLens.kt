package io.github.tracklens

import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.concurrent.Executor

/**
 * Entry point of TrackLens: **no-op release version**.
 *
 * This artifact (`tracklens-noop`) has exactly the API of the debug artifact, so the same code compiles against
 * both, but nothing here records, stores or shows anything, and it adds no dependencies, permissions or
 * components to your app. The one exception is [wrap], which returns your delegate so events still reach your
 * analytics SDK.
 *
 * The other public types in this package (configuration, events, [TrackLensBundles]) are copied from tracklens-core
 * at build time, so they are identical in both artifacts.
 */
@Suppress("UnusedParameter", "EmptyFunctionBlock", "TooManyFunctions") // Doing nothing is this artifact's job.
public object TrackLens {
    /** `true` here, `false` in the debug artifact. Lets host code skip debug-only work. */
    @JvmStatic
    public val isNoOp: Boolean = true

    /** Version of the TrackLens artifact in use. */
    @JvmStatic
    public val libraryVersion: String
        get() = LIBRARY_VERSION

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun install(
        application: Application,
        config: TrackLensConfig = TrackLensConfig.DEFAULT,
    ) {}

    /** Always `false` in tracklens-noop. */
    @JvmStatic
    public val isInstalled: Boolean
        get() = false

    /** Always `false` in tracklens-noop; setting it has no effect. */
    @JvmStatic
    public var isEnabled: Boolean
        get() = false
        set(value) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun log(name: String) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun log(
        name: String,
        params: Map<String, Any?>,
        source: String? = null,
    ) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun log(
        name: String,
        params: Bundle,
        source: String? = null,
    ) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun setUserProperty(
        name: String,
        value: String?,
        source: String? = null,
    ) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun setUserId(
        userId: String?,
        source: String? = null,
    ) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun setScreen(
        name: String,
        screenClass: String? = null,
    ) {}

    /** Returns [delegate] itself, so release builds call your analytics SDK directly. */
    @JvmStatic
    @JvmOverloads
    public fun wrap(
        source: String = AnalyticsDelegate.DEFAULT_SOURCE,
        delegate: AnalyticsDelegate,
    ): AnalyticsDelegate = delegate

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun startSession(label: String? = null) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun endSession() {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun setSessionLabel(label: String?) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun setSessionNotes(notes: String?) {}

    /** Always `null` in tracklens-noop. */
    @JvmStatic
    public val currentSessionId: String?
        get() = null

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun openPanel(context: Context) {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun showBubble() {}

    /** Does nothing in tracklens-noop. */
    @JvmStatic
    public fun hideBubble() {}

    /** Nothing is stored in tracklens-noop: calls [callback] immediately, on the calling thread, with `true`. */
    @JvmStatic
    @JvmOverloads
    public fun clearAllData(callback: CompletionCallback? = null) {
        callback?.onComplete(true)
    }

    /** Nothing is recorded in tracklens-noop: calls [ExportCallback.onFailed] immediately, on the calling thread. */
    @JvmStatic
    public fun exportSession(
        context: Context,
        sessionId: String,
        format: ExportFormat,
        callback: ExportCallback,
    ) {
        callback.onFailed("TrackLens is not active in this build (tracklens-noop).")
    }

    /** Never calls [listener] in tracklens-noop. */
    @JvmStatic
    @JvmOverloads
    public fun addListener(
        listener: TrackLensListener,
        executor: Executor? = null,
    ): Subscription = Subscription {}
}
