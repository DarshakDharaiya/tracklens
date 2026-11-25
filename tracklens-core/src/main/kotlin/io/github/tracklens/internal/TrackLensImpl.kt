package io.github.tracklens.internal

import android.app.Application
import android.content.Context
import android.os.Bundle
import io.github.tracklens.CompletionCallback
import io.github.tracklens.ExportCallback
import io.github.tracklens.ExportFormat
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.Subscription
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.TrackLensListener
import java.util.concurrent.Executor

/**
 * Implementation behind the [io.github.tracklens.TrackLens] facade, which wraps every call in [guard].
 *
 * Logging calls pass the kill switch, then are normalized ([ParamNormalizer]) and handed to [eventSink] (the
 * [EventPipeline] once installed), both inside [pipelineBreaker]. Sessions (Task 25), UI
 * (Tasks 17-20) and export (Tasks 44-45) replace the remaining stub bodies.
 */
@OptIn(InternalTrackLensApi::class)
@Suppress("UnusedParameter", "TooManyFunctions") // One function per facade call; parameters are used once implemented.
internal object TrackLensImpl {
    @Volatile
    var isInstalled: Boolean = false
        private set

    /** `false` until [install] accepts this process (supported API level, main process). */
    @Volatile
    var isActive: Boolean = false
        private set

    /** Kill switch: `TrackLens.isEnabled`. */
    @Volatile
    var isEnabled: Boolean = true

    @Volatile
    var config: TrackLensConfig = TrackLensConfig.DEFAULT
        private set

    /** Where accepted logging calls go. Task 13 installs the pipeline here; tests install failing sinks. */
    @Volatile
    var eventSink: EventSink = EventSink.NONE

    val pipelineBreaker = CircuitBreaker(Component.PIPELINE)

    /** Registered `TrackLens.addListener` listeners; kept across installs so early registrations still work. */
    val listeners = ListenerRegistry(MainThreadExecutor)

    /** The running pipeline once [install] accepted this process; also the default [eventSink]. */
    @Volatile
    var pipeline: EventPipeline? = null
        private set

    const val NON_DEBUGGABLE_MESSAGE: String =
        "The TrackLens debug artifact is running in a non-debuggable build. It is meant for debug builds only: " +
            "depend on io.github.tracklens:tracklens-noop with releaseImplementation for release builds."

    @Synchronized
    fun install(
        application: Application,
        config: TrackLensConfig,
    ) {
        if (isInstalled) return
        isInstalled = true
        this.config = config
        isEnabled = config.enabled

        if (!InstallGate.isDebuggable(application)) {
            Diagnostics.report(Component.INSTALL, DiagnosticLevel.ERROR, NON_DEBUGGABLE_MESSAGE)
        }
        val decision = InstallGate.evaluate(application, config.mainProcessOnly)
        if (decision == InstallDecision.ACTIVE) {
            val started =
                EventPipeline(
                    liveBufferSize = config.liveBufferSize,
                    softCap = EventPipeline.DEFAULT_SOFT_CAP,
                    clock = Clock.Android,
                    dispatcher = EventPipeline.newPipelineDispatcher(),
                    listeners = listeners,
                    redactor = Redactor(config.redaction),
                ).also { it.start() }
            pipeline = started
            eventSink = started
        }
        isActive = decision == InstallDecision.ACTIVE
        val level = if (isActive) DiagnosticLevel.INFO else DiagnosticLevel.WARNING
        Diagnostics.report(Component.INSTALL, level, decision.explanation)
    }

    fun log(
        name: String,
        params: Map<String, Any?>,
        source: String?,
    ) = record { it.event(name, ParamNormalizer.normalize(params), source) }

    fun log(
        name: String,
        params: Bundle,
        source: String?,
    ) = record { it.event(name, ParamNormalizer.normalize(params), source) }

    fun setUserProperty(
        name: String,
        value: String?,
        source: String?,
    ) = record { it.setUserProperty(name, value, source) }

    fun setUserId(
        userId: String?,
        source: String?,
    ) = record { it.setUserId(userId, source) }

    fun setScreen(
        name: String,
        screenClass: String?,
    ) = record { it.setScreen(name, screenClass) }

    fun startSession(label: String?) = Unit

    fun endSession() = Unit

    fun setSessionLabel(label: String?) = Unit

    fun setSessionNotes(notes: String?) = Unit

    /** The pipeline's implicit session until real sessions arrive (Task 25). */
    val currentSessionId: String? get() = pipeline?.sessionId

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
    ): Subscription = listeners.add(listener, executor)

    /** Kill switch first (two volatile reads), then the pipeline breaker. */
    private inline fun record(crossinline call: (EventSink) -> Unit) {
        if (!isActive || !isEnabled) return
        pipelineBreaker.run(Unit) { call(eventSink) }
    }

    /** Back to the state before [install]; for tests only, since the facade is a process-wide singleton. */
    fun resetForTests() {
        synchronized(this) {
            isInstalled = false
            isActive = false
            isEnabled = true
            config = TrackLensConfig.DEFAULT
            eventSink = EventSink.NONE
            pipeline?.close()
            pipeline = null
            listeners.clear()
            pipelineBreaker.reset()
        }
        Diagnostics.clear()
    }
}
