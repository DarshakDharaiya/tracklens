package io.github.tracklens.ui.overlay

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.widget.FrameLayout
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the bubble (Roborazzi). `./gradlew recordRoborazziDebug` writes them,
 * `verifyRoborazziDebug` (run in CI) compares against them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class BubbleScreenshotTest {
    @Test
    fun withoutUnreadEvents() = capture(unread = 0, name = "bubble_unread_0")

    @Test
    fun withSomeUnreadEvents() = capture(unread = 7, name = "bubble_unread_7")

    @Test
    fun withMoreThanNinetyNineUnreadEvents() = capture(unread = 150, name = "bubble_unread_99_plus")

    private fun capture(
        unread: Int,
        name: String,
    ) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        val bubble =
            BubbleView(activity).apply {
                reducedMotion = true
                unreadCount = unread
                position = BubblePosition(io.github.tracklens.Edge.START, 0f)
            }
        container.addView(bubble)
        activity.setContentView(container)
        shadowOf(Looper.getMainLooper()).idle()

        bubble.captureRoboImage("src/test/screenshots/$name.png")
    }
}
