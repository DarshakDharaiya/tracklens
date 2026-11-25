package io.github.tracklens.ui.overlay

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import io.github.tracklens.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BubbleViewTest {
    private lateinit var container: FrameLayout
    private lateinit var bubble: BubbleView
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        container = FrameLayout(activity)
        bubble = BubbleView(activity).apply { reducedMotion = true }
        bubble.listener =
            object : BubbleView.Listener {
                override fun onTap() {
                    events += "tap"
                }

                override fun onHide() {
                    events += "hide"
                }

                override fun onPositionChanged(position: BubblePosition) {
                    events += "moved:${position.edge}"
                }
            }
        container.addView(bubble)
        activity.setContentView(container)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun badgeShowsTheCountAndCapsAtNinetyNine() {
        assertNull(bubble.badgeText())
        bubble.unreadCount = 7
        assertEquals("7", bubble.badgeText())
        bubble.unreadCount = 150
        assertEquals("99+", bubble.badgeText())
        bubble.unreadCount = -3
        assertNull(bubble.badgeText())
    }

    @Test
    fun talkBackHearsTheUnreadCount() {
        assertEquals("TrackLens", bubble.contentDescription)
        bubble.unreadCount = 1
        assertEquals("TrackLens, 1 unread event", bubble.contentDescription)
        bubble.unreadCount = 12
        assertEquals("TrackLens, 12 unread events", bubble.contentDescription)
    }

    @Test
    fun itIsAButtonWithAtLeastTheMinimumTouchTarget() {
        val node = bubble.createAccessibilityNodeInfo()

        assertEquals("android.widget.Button", node.className)
        assertTrue(node.isClickable)
        assertTrue("touch target >= 48dp", bubble.width >= (48 * bubble.resources.displayMetrics.density).toInt())
    }

    @Test
    fun tappingCallsTheListener() {
        bubble.performClick()

        assertEquals(listOf("tap"), events)
    }

    @Test
    fun talkBackCanMoveAndHideTheBubble() {
        bubble.position = BubblePosition(Edge.END, 0.5f)

        assertTrue(bubble.performAccessibilityAction(actionId("Move bubble to the other side"), null))
        assertEquals(Edge.START, bubble.position.edge)
        assertTrue(bubble.performAccessibilityAction(actionId("Hide TrackLens bubble"), null))

        assertEquals(listOf("moved:START", "hide"), events)
    }

    @Test
    fun theOpenActionHasASpokenLabel() {
        val labels = bubble.createAccessibilityNodeInfo().actionList.mapNotNull { it.label?.toString() }

        assertTrue(labels.toString(), "Open TrackLens" in labels)
    }

    @Test
    fun positionPlacesTheBubbleInsideTheMargins() {
        applyInsets(left = 0, top = 0, right = 0, bottom = 0)

        bubble.position = BubblePosition(Edge.START, 0f)
        assertEquals(margin.toFloat(), bubble.translationX)
        assertEquals(margin.toFloat(), bubble.translationY)

        bubble.position = BubblePosition(Edge.END, 1f)
        assertEquals((container.width - margin - bubble.width).toFloat(), bubble.translationX)
        assertEquals((container.height - margin - bubble.height).toFloat(), bubble.translationY)
        assertTrue("programmatic moves don't notify", events.isEmpty())
    }

    @Test
    fun systemBarsAndCutoutsPushTheBubbleInwards() {
        applyInsets(left = 30, top = 100, right = 20, bottom = 200)

        bubble.position = BubblePosition(Edge.START, 0f)
        assertEquals((30 + margin).toFloat(), bubble.translationX)
        assertEquals((100 + margin).toFloat(), bubble.translationY)

        bubble.position = BubblePosition(Edge.END, 1f)
        assertEquals((container.width - 20 - margin - bubble.width).toFloat(), bubble.translationX)
        assertEquals((container.height - 200 - margin - bubble.height).toFloat(), bubble.translationY)
    }

    private val margin get() = (BubbleView.MARGIN_DP * bubble.resources.displayMetrics.density + 0.5f).toInt()

    private fun applyInsets(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(left, top, right, bottom))
                .build()
        bubble.dispatchApplyWindowInsets(requireNotNull(insets.toWindowInsets()))
    }

    private fun actionId(label: String): Int =
        bubble
            .createAccessibilityNodeInfo()
            .actionList
            .first { it.label?.toString() == label }
            .id
}
