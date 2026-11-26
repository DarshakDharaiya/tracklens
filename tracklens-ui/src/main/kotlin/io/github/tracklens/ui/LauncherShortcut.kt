package io.github.tracklens.ui

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * The "TrackLens" launcher shortcut (long-press the app icon), the way into TrackLens when the bubble is hidden or
 * covered by a dialog. Dynamic, so it needs no manifest entry and disappears with the debug build.
 */
internal object LauncherShortcut {
    const val ID = "tracklens"

    /**
     * Publishes the shortcut, or removes an earlier one when [enabled] is `false` (`BubbleConfig.showAppShortcut`).
     * Android attaches shortcuts to the app's launcher activity, so an app without one (a library's test APK, a
     * headless app) gets no shortcut; that is not an error.
     */
    fun update(
        context: Context,
        enabled: Boolean,
    ) {
        if (!enabled) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID))
            return
        }
        if (context.packageManager.getLaunchIntentForPackage(context.packageName) == null) return
        val shortcut =
            ShortcutInfoCompat
                .Builder(context, ID)
                .setShortLabel(context.getString(R.string.tracklens_title))
                .setLongLabel(context.getString(R.string.tracklens_shortcut_long_label))
                .setIcon(IconCompat.createWithResource(context, R.drawable.tracklens_ic_shortcut))
                .setIntent(TrackLensActivity.intent(context))
                .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
