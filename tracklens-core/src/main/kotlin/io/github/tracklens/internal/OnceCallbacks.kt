package io.github.tracklens.internal

import android.net.Uri
import io.github.tracklens.CompletionCallback
import io.github.tracklens.ExportCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Callbacks are promised to fire exactly once. These wrappers let the facade report a failure through the callback
 * after an internal error without risking a second call if the implementation had already answered.
 */
internal class OnceCompletionCallback(
    private val delegate: CompletionCallback,
) : CompletionCallback {
    private val done = AtomicBoolean()

    override fun onComplete(success: Boolean) {
        if (done.compareAndSet(false, true)) delegate.onComplete(success)
    }
}

internal class OnceExportCallback(
    private val delegate: ExportCallback,
) : ExportCallback {
    private val done = AtomicBoolean()

    override fun onExported(
        uri: Uri,
        mimeType: String,
    ) {
        if (done.compareAndSet(false, true)) delegate.onExported(uri, mimeType)
    }

    override fun onFailed(reason: String) {
        if (done.compareAndSet(false, true)) delegate.onFailed(reason)
    }
}
