package io.github.tracklens.ui.overlay

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tracklens.Severity
import io.github.tracklens.ValidationIssues
import io.github.tracklens.internal.TrackedEvents
import io.github.tracklens.ui.design.TrackLensTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the mini panel, hosted the way the overlay hosts it: in a plain `Activity`, which has no
 * lifecycle owner of its own (so this also proves [OverlayViewTreeOwner] is enough on its own).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MiniPanelScreenshotTest {
    private val events =
        listOf(
            event(1, "app_open"),
            event(2, "screen_view", screen = "Home"),
            event(3, "view_item", screen = "Product", params = mapOf("item_id" to "SKU-1", "price" to 9.99)),
            event(4, "add_to_cart", screen = "Product", params = mapOf("item_id" to "SKU-1", "quantity" to 1L)),
            event(
                5,
                "begin-checkout",
                screen = "Cart",
                issues = listOf(ValidationIssues.create("FB_EVENT_NAME_INVALID", Severity.ERROR, "Invalid name")),
            ),
            event(6, "checkout_step", screen = "Checkout", params = mapOf("step" to 1L)),
        )

    @Test
    fun withEvents() = capture("mini_panel_events") { MiniPanelUnderTest(events, paused = false) }

    @Test
    fun paused() = capture("mini_panel_paused") { MiniPanelUnderTest(events, paused = true) }

    @Test
    fun empty() = capture("mini_panel_empty") { MiniPanelUnderTest(emptyList(), paused = false) }

    @Suppress("FunctionNaming")
    @androidx.compose.runtime.Composable
    private fun MiniPanelUnderTest(
        events: List<io.github.tracklens.TrackedEvent>,
        paused: Boolean,
    ) = TrackLensTheme {
        MiniPanel(
            events = events,
            totalEvents = events.size.toLong(),
            paused = paused,
            onTogglePause = {},
            onOpen = {},
            onClose = {},
            modifier = Modifier.padding(8.dp),
        )
    }

    private fun capture(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        // No action bar: it would be drawn over the captured area.
        controller.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val activity = controller.setup().get()
        val owner = OverlayViewTreeOwner()
        val view = ComposeView(activity).apply { owner.attachTo(this) }
        view.setContent(content)
        // WRAP_CONTENT, as OverlayAttacher adds it: the panel sizes itself (at most 320 dp wide).
        val container = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.setContentView(container)
        shadowOf(Looper.getMainLooper()).idle()

        view.captureRoboImage("src/test/screenshots/$name.png")
        owner.destroy()
    }

    private fun event(
        sequence: Long,
        name: String,
        screen: String? = null,
        params: Map<String, Any?> = emptyMap(),
        issues: List<io.github.tracklens.ValidationIssue> = emptyList(),
    ) = TrackedEvents.create(sequence, name, params, screen, issues)
}
