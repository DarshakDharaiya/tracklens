package io.github.tracklens

import android.net.Uri

/** Receives every event TrackLens records. Register with [TrackLens.addListener]. */
public fun interface TrackLensListener {
    /** Called once per recorded event, after redaction and validation. */
    public fun onEvent(event: TrackedEvent)
}

/** Handle returned by [TrackLens.addListener]. */
public fun interface Subscription {
    /** Stops delivering events to the listener. Safe to call more than once. */
    public fun cancel()
}

/** Result of an asynchronous operation such as [TrackLens.clearAllData]. */
public fun interface CompletionCallback {
    /** Called once when the operation finishes; [success] is `false` if it failed. */
    public fun onComplete(success: Boolean)
}

/** Result of [TrackLens.exportSession]. Exactly one method is called, once. */
public interface ExportCallback {
    /** The export was written to [uri] (a `content://` URI that can be shared) with the given [mimeType]. */
    public fun onExported(
        uri: Uri,
        mimeType: String,
    )

    /** The export failed; [reason] is a human-readable explanation. */
    public fun onFailed(reason: String)
}

/** File formats for [TrackLens.exportSession]. */
public enum class ExportFormat {
    /** Machine-readable session dump (schema `tracklens.session.v1`). */
    JSON,

    /** Spreadsheet-friendly event table. */
    CSV,

    /** Self-contained report with inline charts, for bug reports. */
    HTML,
}
