package io.github.tracklens.internal

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.internal.spi.TrackLensModule
import io.github.tracklens.internal.spi.TrackLensRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(InternalTrackLensApi::class)
@RunWith(RobolectricTestRunner::class)
class ModuleLoaderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clear() = Diagnostics.clear()

    @Test
    fun aFailingModuleDoesNotStopTheOthers() {
        val failing =
            object : TrackLensModule {
                override fun onInstall(
                    application: Application,
                    runtime: TrackLensRuntime,
                ) = error("broken module")
            }
        val working = RecordingModule()

        val installed = ModuleLoader.installAll(application, StubRuntime, discover = { listOf(failing, working) })

        assertEquals(listOf(working), installed)
        assertTrue(working.installed)
        assertTrue(Diagnostics.snapshot().any { it.level == DiagnosticLevel.ERROR && "failed to install" in it.message })
    }

    @Test
    fun aBrokenDiscoveryInstallsNothingAndIsReported() {
        val installed = ModuleLoader.installAll(application, StubRuntime, discover = { throw ServiceConfigurationError() })

        assertTrue(installed.isEmpty())
        assertTrue(Diagnostics.snapshot().any { "could not be loaded" in it.message })
    }

    @Test
    fun modulesWithoutAScreenDoNotOpenOne() {
        assertFalse(RecordingModule().openPanel(application))
    }

    private class ServiceConfigurationError : Error("bad META-INF/services entry")

    private class RecordingModule : TrackLensModule {
        var installed = false

        override fun onInstall(
            application: Application,
            runtime: TrackLensRuntime,
        ) {
            installed = true
        }
    }

    private object StubRuntime : TrackLensRuntime {
        override val config: TrackLensConfig = TrackLensConfig.DEFAULT
        override val liveSnapshot: StateFlow<LiveSnapshot> = MutableStateFlow(LiveSnapshot.EMPTY)
        override val bubbleVisible: StateFlow<Boolean> = MutableStateFlow(true)

        override fun setBubbleVisible(visible: Boolean) = Unit

        override fun markAllRead() = Unit

        override fun clearLive() = Unit

        override fun openPanel(context: Context) = Unit

        override fun reportFailure(
            message: String,
            failure: Throwable,
        ) = Unit
    }
}
