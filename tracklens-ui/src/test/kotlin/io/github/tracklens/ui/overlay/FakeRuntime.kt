package io.github.tracklens.ui.overlay

import android.content.Context
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.internal.LiveSnapshot
import io.github.tracklens.internal.spi.TrackLensRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [TrackLensRuntime] the tests drive directly; records what the overlay asked it to do. */
class FakeRuntime(
    override val config: TrackLensConfig = TrackLensConfig.DEFAULT,
) : TrackLensRuntime {
    val snapshots = MutableStateFlow(LiveSnapshot.EMPTY)
    private val visible = MutableStateFlow(true)
    val calls = mutableListOf<String>()
    val failures = mutableListOf<Throwable>()
    var failOnSetVisible = false

    override val liveSnapshot: StateFlow<LiveSnapshot> get() = snapshots
    override val bubbleVisible: StateFlow<Boolean> get() = visible

    override fun setBubbleVisible(visible: Boolean) {
        check(!failOnSetVisible) { "setBubbleVisible failed" }
        calls += "setBubbleVisible($visible)"
        this.visible.value = visible
    }

    override fun markAllRead() {
        calls += "markAllRead"
        snapshots.value = snapshots.value.let { LiveSnapshot(it.events, it.totalEvents, it.droppedEvents, 0, it.version + 1) }
    }

    override fun clearLive() {
        calls += "clearLive"
        snapshots.value = snapshots.value.let { LiveSnapshot(emptyList(), it.totalEvents, it.droppedEvents, 0, it.version + 1) }
    }

    override fun openPanel(context: Context) {
        calls += "openPanel"
    }

    override fun reportFailure(
        message: String,
        failure: Throwable,
    ) {
        failures += failure
    }

    fun publishUnread(unread: Int) {
        snapshots.value = snapshots.value.let { LiveSnapshot(it.events, it.totalEvents + unread, it.droppedEvents, unread, it.version + 1) }
    }

    /** Publishes [events] (oldest first) as the live buffer, as the pipeline would. */
    fun publish(
        events: List<io.github.tracklens.TrackedEvent>,
        dropped: Long = 0,
        unread: Int = 0,
    ) {
        snapshots.value =
            LiveSnapshot(events, events.lastOrNull()?.sequence ?: 0, dropped, unread, snapshots.value.version + 1)
    }
}
