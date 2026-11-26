package io.github.tracklens.ui.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.tracklens.Edge
import io.github.tracklens.OverlayMode
import io.github.tracklens.internal.spi.TrackLensRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Puts the bubble on whichever activity is in front (plan section 4.8, IN_APP mode): attached to the window's decor
 * view on resume, removed on pause, at the position the user last chose. Skips TrackLens' own screens
 * ([NoBubbleScreen]) and the activities in `BubbleConfig.excludedActivities`.
 *
 * Main thread only. Every callback is guarded: after [MAX_FAILURES] failures the overlay turns itself off and
 * reports why, without affecting the host app or the rest of TrackLens.
 */
@Suppress("TooManyFunctions") // Seven are the ActivityLifecycleCallbacks overrides, five of them no-ops.
internal class OverlayAttacher(
    private val application: Application,
    private val runtime: TrackLensRuntime,
    private val store: BubblePositionStore = BubblePositionStore(application),
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resumed: WeakReference<Activity>? = null
    private var attachment: Attachment? = null
    private var failures = 0

    /** The attachment on the resumed activity, if any; for tests. */
    val currentAttachment: Attachment? get() = attachment

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            // Skip the current value: on install no activity is attached yet.
            runtime.bubbleVisible.drop(1).collect { visible ->
                guarded {
                    if (visible) resumed?.get()?.let(::attachIfAllowed) else detach()
                }
            }
        }
    }

    fun uninstall() {
        application.unregisterActivityLifecycleCallbacks(this)
        detach()
        scope.cancel()
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        guarded { attachIfAllowed(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
        guarded { if (attachment?.activity === activity) detach() }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun attachIfAllowed(activity: Activity) {
        if (!shouldShowOn(activity)) return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        detach()
        attachment = Attachment(activity, decor).also { it.attach() }
    }

    private fun detach() {
        attachment?.detach()
        attachment = null
    }

    private fun shouldShowOn(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return failures < MAX_FAILURES &&
            runtime.config.bubble.overlayMode != OverlayMode.NONE &&
            runtime.bubbleVisible.value &&
            activity !is NoBubbleScreen &&
            name !in runtime.config.bubble.excludedActivities
    }

    @Suppress("TooGenericExceptionCaught") // The overlay must never crash the host app.
    private inline fun guarded(block: () -> Unit) {
        if (failures >= MAX_FAILURES) return
        try {
            block()
        } catch (failure: Throwable) {
            failures++
            runtime.reportFailure("TrackLens overlay failed ($failures/$MAX_FAILURES)", failure)
            if (failures >= MAX_FAILURES) {
                runCatching { detach() }
                runtime.reportFailure("TrackLens overlay turned off after $MAX_FAILURES failures", failure)
            }
        }
    }

    /** The bubble (and, while open, the mini panel or dismiss target) on one activity's window. */
    @Suppress("TooManyFunctions") // The BubbleView.Listener callbacks plus panel and dismiss-target handling.
    inner class Attachment(
        val activity: Activity,
        private val decor: ViewGroup,
    ) : BubbleView.Listener {
        val bubble: BubbleView =
            BubbleView(activity).apply {
                position = store.load(BubblePosition(runtime.config.bubble.initialEdge, BubblePosition.DEFAULT.yFraction))
                listener = this@Attachment
            }
        var miniPanel: ComposeView? = null
            private set
        var dismissTarget: DismissTargetView? = null
            private set
        private var panelOwner: OverlayViewTreeOwner? = null
        private var unreadJob: Job? = null

        fun attach() {
            decor.addView(bubble, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            bubble.bringToFront()
            unreadJob =
                scope.launch {
                    runtime.liveSnapshot.collect { snapshot ->
                        if (miniPanel != null && snapshot.unreadCount > 0) runtime.markAllRead()
                        bubble.unreadCount = if (miniPanel != null) 0 else snapshot.unreadCount
                    }
                }
        }

        fun detach() {
            unreadJob?.cancel()
            closeMiniPanel()
            removeDismissTarget()
            decor.removeView(bubble)
        }

        override fun onTap() = guarded { if (miniPanel == null) openMiniPanel() else closeMiniPanel() }

        override fun onHide() = guarded { runtime.setBubbleVisible(false) }

        override fun onPositionChanged(position: BubblePosition) = guarded { store.save(position) }

        override fun onDragStarted() =
            guarded {
                closeMiniPanel()
                showDismissTarget()
            }

        override fun onDragMoved(
            centerX: Float,
            centerY: Float,
        ) = guarded { dismissTarget?.let { it.highlighted = it.contains(centerX, centerY) } }

        override fun onDragEnded(
            centerX: Float,
            centerY: Float,
        ): Boolean {
            val dismissed = dismissTarget?.contains(centerX, centerY) == true
            guarded {
                removeDismissTarget()
                if (dismissed) runtime.setBubbleVisible(false)
            }
            return dismissed
        }

        fun openMiniPanel() {
            if (miniPanel != null) return
            runtime.markAllRead()
            bubble.unreadCount = 0
            val owner = OverlayViewTreeOwner()
            val panel =
                createMiniPanelView(
                    context = activity,
                    snapshots = runtime.liveSnapshot,
                    theme = runtime.config.theme,
                    owner = owner,
                    onOpen = {
                        closeMiniPanel()
                        runtime.openPanel(activity)
                    },
                    onClose = ::closeMiniPanel,
                )
            decor.addView(panel, panelLayoutParams())
            bubble.bringToFront()
            panelOwner = owner
            miniPanel = panel
        }

        fun closeMiniPanel() {
            miniPanel?.let(decor::removeView)
            panelOwner?.destroy()
            miniPanel = null
            panelOwner = null
        }

        /** Next to the bubble on its side of the screen: below it in the upper half, above it in the lower half. */
        private fun panelLayoutParams(): FrameLayout.LayoutParams {
            val density = activity.resources.displayMetrics.density
            val gap = (PANEL_GAP_DP * density).roundToInt()
            // START/END resolve against the decor's layout direction, as the bubble's edge does.
            val horizontal = if (bubble.position.edge == Edge.START) Gravity.START else Gravity.END
            val upperHalf = bubble.centerY() < decor.height / 2f
            return FrameLayout
                .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply {
                    leftMargin = gap
                    rightMargin = gap
                    if (upperHalf) {
                        gravity = Gravity.TOP or horizontal
                        topMargin = (bubble.top + bubble.translationY + bubble.height).roundToInt() + gap
                    } else {
                        gravity = Gravity.BOTTOM or horizontal
                        bottomMargin = (decor.height - bubble.top - bubble.translationY).roundToInt() + gap
                    }
                }
        }

        private fun showDismissTarget() {
            if (dismissTarget != null) return
            val target = DismissTargetView(activity)
            val bottomInset =
                ViewCompat
                    .getRootWindowInsets(decor)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())
                    ?.bottom ?: 0
            val offset = (DismissTargetView.BOTTOM_OFFSET_DP * activity.resources.displayMetrics.density).roundToInt()
            decor.addView(
                target,
                FrameLayout
                    .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        bottomMargin = bottomInset + offset
                    },
            )
            bubble.bringToFront()
            dismissTarget = target
        }

        private fun removeDismissTarget() {
            dismissTarget?.let(decor::removeView)
            dismissTarget = null
        }
    }

    companion object {
        const val MAX_FAILURES = 5
        private const val PANEL_GAP_DP = 8
    }
}
