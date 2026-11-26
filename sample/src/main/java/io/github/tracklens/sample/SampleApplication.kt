package io.github.tracklens.sample

import android.app.Application
import io.github.tracklens.TrackLens
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.sample.sdk.SampleAnalytics

/**
 * Installs TrackLens the way the README tells host apps to: one line in `Application.onCreate`. The same code
 * compiles against both artifacts: `debugImplementation(tracklens)` records and shows everything,
 * `releaseImplementation(tracklens-noop)` makes every call a no-op.
 */
class SampleApplication : Application() {
    lateinit var flows: DemoFlows
        private set

    override fun onCreate() {
        super.onCreate()
        TrackLens.install(this, TrackLensConfig.DEFAULT)
        flows = DemoFlows(SampleAnalytics())
    }
}
