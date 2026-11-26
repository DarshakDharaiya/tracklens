package io.github.tracklens.internal.storage

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.tracklens.InternalTrackLensApi

/**
 * The moments the writer must flush besides its own thresholds (plan section 4.4): the app goes to the background
 * (`ProcessLifecycleOwner` ON_STOP), the system asks to trim memory once the UI is hidden, and the app crashes.
 */
@InternalTrackLensApi
public class FlushSignals(
    private val flush: () -> Unit,
) : DefaultLifecycleObserver,
    ComponentCallbacks2 {
    /** Registers for background and trim-memory signals. Main thread (ProcessLifecycleOwner requires it). */
    public fun install(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        application.registerComponentCallbacks(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        flush()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) flush()
    }

    override fun onConfigurationChanged(newConfig: Configuration): Unit = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory(): Unit = Unit
}

/**
 * Flushes what is pending when the app crashes, then always hands the crash to the handler that was installed
 * before (the crash reporter or the platform's), with the same thread and exception. The flush gets at most
 * [budgetMillis]: first the pipeline drains what was logged just before the crash, then the writer stores it.
 */
@InternalTrackLensApi
public class CrashFlushHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val drainPipeline: (timeoutMillis: Long) -> Boolean,
    private val flushWriter: (timeoutMillis: Long) -> Boolean,
    private val budgetMillis: Long = BUDGET_MILLIS,
) : Thread.UncaughtExceptionHandler {
    @Suppress("TooGenericExceptionCaught") // Nothing may stop the crash from reaching the previous handler.
    override fun uncaughtException(
        thread: Thread,
        failure: Throwable,
    ) {
        try {
            val deadline = SystemClock.uptimeMillis() + budgetMillis
            // A crash on a TrackLens thread cannot wait for that same thread.
            if (thread.name != PIPELINE_THREAD) drainPipeline(budgetMillis / 2)
            if (thread.name != WRITER_THREAD) flushWriter((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
        } catch (ignored: Throwable) {
            // Best effort: the crash itself matters more.
        } finally {
            previous?.uncaughtException(thread, failure)
        }
    }

    /** Installing the handler. */
    public companion object {
        /** Plan section 4.4. */
        public const val BUDGET_MILLIS: Long = 800
        private const val PIPELINE_THREAD = "TrackLens-Pipeline"
        private const val WRITER_THREAD = "TrackLens-Writer"

        /** Installs a [CrashFlushHandler] in front of the current default handler. */
        public fun install(
            drainPipeline: (timeoutMillis: Long) -> Boolean,
            flushWriter: (timeoutMillis: Long) -> Boolean,
        ) {
            Thread.setDefaultUncaughtExceptionHandler(
                CrashFlushHandler(Thread.getDefaultUncaughtExceptionHandler(), drainPipeline, flushWriter),
            )
        }
    }
}
