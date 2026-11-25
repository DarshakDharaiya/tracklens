package io.github.tracklens

import android.app.Application
import android.content.Context
import android.os.Bundle
import io.github.tracklens.internal.ForwardingAnalyticsDelegate
import io.github.tracklens.internal.TrackLensImpl
import java.util.concurrent.Executor

/**
 * Entry point of TrackLens: a debug-only companion that shows analytics events live on the device and records
 * test sessions for review.
 *
 * Add `tracklens` to debug builds and `tracklens-noop` to release builds. Both expose exactly this API (the build
 * fails if they drift apart), so the same code compiles against either. In the no-op every call does nothing,
 * except [wrap], which still forwards to your analytics SDK.
 *
 * No method throws into your app: internal failures are caught and shown in TrackLens' diagnostics view.
 * Every method can be called from any thread.
 */
@Suppress("TooManyFunctions") // The facade is the whole public API, one function per call.
public object TrackLens {
    /** `true` in tracklens-noop, `false` here. Lets host code skip debug-only work. */
    @JvmStatic
    public val isNoOp: Boolean = false

    /** Version of the TrackLens artifact in use. */
    @JvmStatic
    public val libraryVersion: String
        get() = LIBRARY_VERSION

    // ---- Setup -------------------------------------------------------------------------------------------------

    /**
     * Starts TrackLens. Call it once from `Application.onCreate()`; later calls are ignored.
     *
     * TrackLens stays inactive on Android versions below its minSdk and, unless [TrackLensConfig.mainProcessOnly]
     * is `false`, in processes other than the main one.
     */
    @JvmStatic
    @JvmOverloads
    public fun install(
        application: Application,
        config: TrackLensConfig = TrackLensConfig.DEFAULT,
    ) {
        TrackLensImpl.install(application, config)
    }

    /** `true` once [install] has run in this process. */
    @JvmStatic
    public val isInstalled: Boolean
        get() = TrackLensImpl.isInstalled

    /**
     * Kill switch. While `false`, logging calls return immediately and nothing is recorded.
     * Starts as [TrackLensConfig.enabled].
     */
    @JvmStatic
    public var isEnabled: Boolean
        get() = TrackLensImpl.isEnabled
        set(value) {
            TrackLensImpl.isEnabled = value
        }

    // ---- Logging -----------------------------------------------------------------------------------------------

    /** Records an event without parameters. */
    @JvmStatic
    public fun log(name: String) {
        TrackLensImpl.log(name, emptyMap(), null)
    }

    /**
     * Records an event. [params] is copied, so it can be changed after the call.
     * [source] tags the analytics SDK, for filtering when several are used.
     */
    @JvmStatic
    @JvmOverloads
    public fun log(
        name: String,
        params: Map<String, Any?>,
        source: String? = null,
    ) {
        TrackLensImpl.log(name, params, source)
    }

    /** Records an event whose parameters are in a [Bundle], as Firebase takes them. [params] is copied. */
    @JvmStatic
    @JvmOverloads
    public fun log(
        name: String,
        params: Bundle,
        source: String? = null,
    ) {
        TrackLensImpl.log(name, params, source)
    }

    /** Records a user property change; `null` clears the property. */
    @JvmStatic
    @JvmOverloads
    public fun setUserProperty(
        name: String,
        value: String?,
        source: String? = null,
    ) {
        TrackLensImpl.setUserProperty(name, value, source)
    }

    /** Records a user id change; `null` clears it. Stored according to [RedactionConfig.userId] (hashed by default). */
    @JvmStatic
    @JvmOverloads
    public fun setUserId(
        userId: String?,
        source: String? = null,
    ) {
        TrackLensImpl.setUserId(userId, source)
    }

    /**
     * Sets the current screen, for screens TrackLens can't see automatically (Compose destinations, custom view
     * systems). Overrides automatic tracking until the next Activity change.
     */
    @JvmStatic
    @JvmOverloads
    public fun setScreen(
        name: String,
        screenClass: String? = null,
    ) {
        TrackLensImpl.setScreen(name, screenClass)
    }

    // ---- Wrapping ----------------------------------------------------------------------------------------------

    /**
     * Returns a delegate that records each call in TrackLens and then forwards the original, unredacted values to
     * [delegate]. Exceptions thrown by [delegate] reach the caller unchanged, so the app behaves the same in debug
     * and release builds.
     *
     * [delegate] comes last so a lambda can follow the call: `TrackLens.wrap(source = "firebase") { name, params -> … }`.
     * From Java: `TrackLens.wrap("firebase", delegate)` or `TrackLens.wrap(delegate)`.
     */
    @JvmStatic
    @JvmOverloads
    public fun wrap(
        source: String = AnalyticsDelegate.DEFAULT_SOURCE,
        delegate: AnalyticsDelegate,
    ): AnalyticsDelegate = ForwardingAnalyticsDelegate(delegate, source)

    // ---- Sessions ----------------------------------------------------------------------------------------------

    /**
     * Ends the current session and starts a manual one, for example labelled `"Checkout regression - build 142"`.
     * Manual sessions aren't ended by backgrounding unless [SessionConfig.manualSessionsSurviveBackground] is `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun startSession(label: String? = null) {
        TrackLensImpl.startSession(label)
    }

    /** Ends the current session. A new one starts on the next foreground if [SessionConfig.autoStart] is set. */
    @JvmStatic
    public fun endSession() {
        TrackLensImpl.endSession()
    }

    /** Sets or clears the label of the current session. */
    @JvmStatic
    public fun setSessionLabel(label: String?) {
        TrackLensImpl.setSessionLabel(label)
    }

    /** Sets or clears tester notes on the current session. */
    @JvmStatic
    public fun setSessionNotes(notes: String?) {
        TrackLensImpl.setSessionNotes(notes)
    }

    /** Id of the current session, or `null` if none is active. */
    @JvmStatic
    public val currentSessionId: String?
        get() = TrackLensImpl.currentSessionId

    // ---- UI ----------------------------------------------------------------------------------------------------

    /** Opens the TrackLens panel. */
    @JvmStatic
    public fun openPanel(context: Context) {
        TrackLensImpl.openPanel(context)
    }

    /** Shows the floating bubble if it was hidden. */
    @JvmStatic
    public fun showBubble() {
        TrackLensImpl.showBubble()
    }

    /** Hides the floating bubble until [showBubble] or the next app start. */
    @JvmStatic
    public fun hideBubble() {
        TrackLensImpl.hideBubble()
    }

    // ---- Data --------------------------------------------------------------------------------------------------

    /** Deletes all sessions, events, exports and TrackLens preferences. [callback] runs on the main thread. */
    @JvmStatic
    @JvmOverloads
    public fun clearAllData(callback: CompletionCallback? = null) {
        TrackLensImpl.clearAllData(callback)
    }

    /** Exports the session [sessionId] in [format]. [callback] runs on the main thread. */
    @JvmStatic
    public fun exportSession(
        context: Context,
        sessionId: String,
        format: ExportFormat,
        callback: ExportCallback,
    ) {
        TrackLensImpl.exportSession(context, sessionId, format, callback)
    }

    // ---- Observation -------------------------------------------------------------------------------------------

    /**
     * Calls [listener] for every recorded event, on [executor] (the main thread if `null`).
     * Returns a [Subscription] to stop.
     */
    @JvmStatic
    @JvmOverloads
    public fun addListener(
        listener: TrackLensListener,
        executor: Executor? = null,
    ): Subscription = TrackLensImpl.addListener(listener, executor)
}
