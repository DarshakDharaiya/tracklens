package io.github.tracklens.internal

import android.app.Application
import android.content.Context
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.internal.spi.TrackLensModule
import io.github.tracklens.internal.spi.TrackLensRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ServiceLoader

/** The [TrackLensRuntime] modules see: a thin view over [TrackLensImpl] and its pipeline. */
internal class RuntimeImpl(
    override val config: TrackLensConfig,
    private val pipeline: EventPipeline,
    private val bubbleVisibility: MutableStateFlow<Boolean>,
) : TrackLensRuntime {
    override val liveSnapshot: StateFlow<LiveSnapshot> get() = pipeline.snapshot

    override val bubbleVisible: StateFlow<Boolean> get() = bubbleVisibility

    override fun setBubbleVisible(visible: Boolean) {
        bubbleVisibility.value = visible
    }

    override fun markAllRead() = pipeline.markAllRead()

    override fun openPanel(context: Context) = TrackLensImpl.openPanel(context)

    override fun reportFailure(
        message: String,
        failure: Throwable,
    ) = handleInternalFailure(Component.OVERLAY, message, failure)
}

/** Finds the TrackLens modules on the classpath and installs each one; a failing module never stops the others. */
internal object ModuleLoader {
    @Suppress("TooGenericExceptionCaught") // A broken module must not break install().
    fun installAll(
        application: Application,
        runtime: TrackLensRuntime,
    ): List<String> {
        val installed = mutableListOf<String>()
        val modules =
            try {
                ServiceLoader.load(TrackLensModule::class.java, TrackLensModule::class.java.classLoader).toList()
            } catch (failure: Throwable) {
                handleInternalFailure(Component.INSTALL, "TrackLens modules could not be loaded", failure)
                emptyList()
            }
        for (module in modules) {
            try {
                module.onInstall(application, runtime)
                installed += module.javaClass.name
            } catch (failure: Throwable) {
                handleInternalFailure(Component.INSTALL, "TrackLens module ${module.javaClass.name} failed to install", failure)
            }
        }
        Diagnostics.report(
            Component.INSTALL,
            DiagnosticLevel.INFO,
            if (installed.isEmpty()) "No TrackLens modules found." else "Modules installed: ${installed.joinToString()}",
        )
        return installed
    }
}
