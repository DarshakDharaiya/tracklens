package io.github.tracklens.internal

import android.app.Application
import android.content.Context
import android.os.Bundle
import io.github.tracklens.CompletionCallback
import io.github.tracklens.ExportCallback
import io.github.tracklens.ExportFormat
import io.github.tracklens.Subscription
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.TrackLensListener
import java.util.concurrent.Executor

/**
 * Implementation behind the [io.github.tracklens.TrackLens] facade.
 *
 * Task 8 stub: it holds the installed/enabled state and accepts every call. The pipeline (Tasks 11-15), sessions
 * (Task 25), UI (Tasks 17-20) and export (Tasks 44-45) replace the no-op bodies.
 */
@Suppress("UnusedParameter", "TooManyFunctions") // One function per facade call; parameters are used once implemented.
internal object TrackLensImpl {
    @Volatile
    var isInstalled: Boolean = false
        private set

    @Volatile
    var isEnabled: Boolean = true

    @Volatile
    var config: TrackLensConfig = TrackLensConfig.DEFAULT
        private set

    @Synchronized
    fun install(
        application: Application,
        config: TrackLensConfig,
    ) {
        if (isInstalled) return
        this.config = config
        isEnabled = config.enabled
        isInstalled = true
    }

    fun log(
        name: String,
        params: Map<String, Any?>,
        source: String?,
    ) = Unit

    fun log(
        name: String,
        params: Bundle,
        source: String?,
    ) = Unit

    fun setUserProperty(
        name: String,
        value: String?,
        source: String?,
    ) = Unit

    fun setUserId(
        userId: String?,
        source: String?,
    ) = Unit

    fun setScreen(
        name: String,
        screenClass: String?,
    ) = Unit

    fun startSession(label: String?) = Unit

    fun endSession() = Unit

    fun setSessionLabel(label: String?) = Unit

    fun setSessionNotes(notes: String?) = Unit

    val currentSessionId: String? get() = null

    fun openPanel(context: Context) = Unit

    fun showBubble() = Unit

    fun hideBubble() = Unit

    fun clearAllData(callback: CompletionCallback?) {
        callback?.onComplete(true)
    }

    fun exportSession(
        context: Context,
        sessionId: String,
        format: ExportFormat,
        callback: ExportCallback,
    ) {
        callback.onFailed("Export is not available yet in this TrackLens version.")
    }

    fun addListener(
        listener: TrackLensListener,
        executor: Executor?,
    ): Subscription = Subscription {}
}
