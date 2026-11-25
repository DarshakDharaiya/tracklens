package io.github.tracklens.ui.overlay

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lifecycle, saved-state and recomposition for Compose content TrackLens adds to a host activity's window.
 *
 * A `ComposeView` needs a lifecycle and saved-state owner on its view tree, and by default it recomposes with a
 * recomposer shared by the whole window, created from the lifecycle owner of the window's root view. A plain
 * `Activity` (not `ComponentActivity`) has no such owner, and borrowing the host's would tie TrackLens' UI to the
 * host's state. So this class provides all three, and lives exactly as long as the overlay view.
 *
 * Main thread only.
 */
internal class OverlayViewTreeOwner :
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val recomposerScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
    private val recomposer = Recomposer(recomposerScope.coroutineContext)

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** Installs this owner on [view], gives it its own recomposer and moves to RESUMED. */
    fun attachTo(view: AbstractComposeView) {
        savedStateController.performRestore(null)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setParentCompositionContext(recomposer)
        recomposerScope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Ends the lifecycle and stops recomposition; Compose disposes the content. */
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        recomposer.cancel()
        recomposerScope.cancel()
    }
}
