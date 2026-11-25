package io.github.tracklens.ui.overlay

import android.content.Context
import androidx.core.content.edit
import io.github.tracklens.Edge

/**
 * Remembers where the user left the bubble, so it appears in the same place in every activity, after rotation and
 * after restarts. Kept in TrackLens' own preferences file, never the host app's.
 */
internal class BubblePositionStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The saved position, or [default] if none was saved (or it is unreadable). */
    fun load(default: BubblePosition): BubblePosition {
        val edgeName = preferences.getString(KEY_EDGE, null)
        val edge = Edge.entries.firstOrNull { it.name == edgeName } ?: return default
        return BubblePosition(edge, preferences.getFloat(KEY_Y_FRACTION, default.yFraction)).normalized()
    }

    fun save(position: BubblePosition) {
        preferences
            .edit {
                putString(KEY_EDGE, position.edge.name)
                    .putFloat(KEY_Y_FRACTION, position.yFraction)
            }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    companion object {
        const val FILE_NAME = "tracklens_overlay"
        private const val KEY_EDGE = "bubble_edge"
        private const val KEY_Y_FRACTION = "bubble_y_fraction"
    }
}
