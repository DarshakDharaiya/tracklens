package io.github.tracklens.internal.storage

import android.app.Application
import io.github.tracklens.InternalTrackLensApi
import io.github.tracklens.internal.spi.TrackLensModule
import io.github.tracklens.internal.spi.TrackLensRuntime

/**
 * Plugs tracklens-storage into tracklens-core: every recorded event goes to the [BatchedWriter], which flushes on
 * its thresholds and on the [FlushSignals] and [CrashFlushHandler] signals. Found by `ServiceLoader` through
 * `META-INF/services/io.github.tracklens.internal.spi.TrackLensModule`; kept by the consumer R8 rules.
 */
@InternalTrackLensApi
public class StorageModule : TrackLensModule {
    override fun onInstall(
        application: Application,
        runtime: TrackLensRuntime,
    ) {
        val writer =
            BatchedWriter(
                // Opened on the writer thread on the first flush: never on the main thread.
                database = { TrackLensDatabase.open(application) },
                limits = WriterLimits(maxEventsPerSession = runtime.config.retention.maxEventsPerSession),
                report = runtime::reportStorageProblem,
            )
        runtime.addRecordObserver { event, redactedKeys -> writer.enqueue(PendingRecord(event, redactedKeys)) }
        FlushSignals(writer::flush).install(application)
        CrashFlushHandler.install(drainPipeline = runtime::awaitPipelineIdle, flushWriter = writer::flushBlocking)
    }
}
