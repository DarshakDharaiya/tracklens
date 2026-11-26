package io.github.tracklens.ui.overlay

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import io.github.tracklens.Edge
import io.github.tracklens.OverlayMode
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.internal.spi.TrackLensModule
import io.github.tracklens.ui.UiModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.ServiceLoader

class FirstActivity : Activity()

class SecondActivity : Activity()

class OwnScreenActivity :
    Activity(),
    NoBubbleScreen

@RunWith(RobolectricTestRunner::class)
class OverlayAttacherTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = BubblePositionStore(application)
    private lateinit var runtime: FakeRuntime
    private lateinit var attacher: OverlayAttacher

    @Before
    fun setUp() {
        store.clear()
        install()
    }

    @After
    fun tearDown() {
        attacher.uninstall()
    }

    private fun install(config: TrackLensConfig = TrackLensConfig.DEFAULT) {
        if (::attacher.isInitialized) attacher.uninstall()
        runtime = FakeRuntime(config)
        attacher = OverlayAttacher(application, runtime, store).also { it.install() }
    }

    private fun <T : Activity> resume(type: Class<T>): ActivityController<T> = Robolectric.buildActivity(type).setup().also { idle() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun bubblesOn(activity: Activity): Int {
        val decor = activity.window.decorView as ViewGroup
        return (0 until decor.childCount).count { decor.getChildAt(it) is BubbleView }
    }

    @Test
    fun theBubbleMovesToWhicheverActivityIsResumed() {
        val first = resume(FirstActivity::class.java)
        assertEquals(1, bubblesOn(first.get()))

        first.pause()
        val second = resume(SecondActivity::class.java)

        assertEquals("removed from the paused activity", 0, bubblesOn(first.get()))
        assertEquals(1, bubblesOn(second.get()))
        assertSame(second.get(), attacher.currentAttachment?.activity)
    }

    @Test
    fun excludedActivitiesOwnScreensAndNoneModeGetNoBubble() {
        install(TrackLensConfig.Builder().bubble { excludeActivities(FirstActivity::class.java.name) }.build())
        assertEquals(0, bubblesOn(resume(FirstActivity::class.java).get()))
        assertEquals(1, bubblesOn(resume(SecondActivity::class.java).get()))

        assertEquals("TrackLens' own screens", 0, bubblesOn(resume(OwnScreenActivity::class.java).get()))

        install(TrackLensConfig.Builder().bubble { overlayMode(OverlayMode.NONE) }.build())
        assertEquals(0, bubblesOn(resume(SecondActivity::class.java).get()))
    }

    @Test
    fun theBadgeFollowsUnreadEvents() {
        resume(FirstActivity::class.java)
        runtime.publishUnread(7)
        idle()

        assertEquals(7, attacher.currentAttachment!!.bubble.unreadCount)
    }

    @Test
    fun tappingOpensTheMiniPanelAndMarksEventsRead() {
        resume(FirstActivity::class.java)
        runtime.publishUnread(3)
        idle()
        val attachment = attacher.currentAttachment!!

        attachment.onTap()
        idle()

        assertNotNull(attachment.miniPanel)
        assertTrue("markAllRead" in runtime.calls)
        assertEquals(0, attachment.bubble.unreadCount)

        attachment.onTap()
        assertNull("a second tap closes it", attachment.miniPanel)
    }

    @Test
    fun hidingRemovesTheBubbleUntilShownAgain() {
        val activity = resume(FirstActivity::class.java).get()
        attacher.currentAttachment!!.onHide()
        idle()
        assertEquals(0, bubblesOn(activity))

        runtime.setBubbleVisible(true)
        idle()
        assertEquals(1, bubblesOn(activity))
    }

    @Test
    fun droppingOnTheDismissTargetHidesTheBubble() {
        val activity = resume(FirstActivity::class.java).get()
        val attachment = attacher.currentAttachment!!

        attachment.onDragStarted()
        val target = attachment.dismissTarget!!
        target.layout(100, 1000, 200, 1100)
        attachment.onDragMoved(150f, 1050f)
        assertTrue(target.highlighted)

        val dismissed = attachment.onDragEnded(150f, 1050f)
        idle()

        assertTrue(dismissed)
        assertNull(attachment.dismissTarget)
        assertEquals(listOf("setBubbleVisible(false)"), runtime.calls)
        assertEquals(0, bubblesOn(activity))
    }

    @Test
    fun aDropElsewhereKeepsTheBubble() {
        resume(FirstActivity::class.java)
        val attachment = attacher.currentAttachment!!
        attachment.onDragStarted()
        attachment.dismissTarget!!.layout(100, 1000, 200, 1100)

        assertFalse(attachment.onDragEnded(150f, 200f))
        assertNull(attachment.dismissTarget)
        assertTrue(runtime.calls.isEmpty())
    }

    @Test
    fun thePositionIsRememberedAcrossActivities() {
        resume(FirstActivity::class.java).pause()
        val moved = BubblePosition(Edge.START, 0.75f)
        store.save(moved)

        resume(SecondActivity::class.java)

        assertEquals(moved, attacher.currentAttachment!!.bubble.position)
    }

    @Test
    fun repeatedFailuresTurnTheOverlayOff() {
        val activity = resume(FirstActivity::class.java).get()
        val attachment = attacher.currentAttachment!!
        runtime.failOnSetVisible = true

        repeat(OverlayAttacher.MAX_FAILURES) { attachment.onHide() }

        // One report per failure, plus one saying the overlay is off.
        assertEquals(OverlayAttacher.MAX_FAILURES + 1, runtime.failures.size)
        assertNull(attacher.currentAttachment)
        assertEquals(0, bubblesOn(activity))
        assertEquals("stays off", 0, bubblesOn(resume(SecondActivity::class.java).get()))
    }

    @Test
    fun serviceLoaderFindsTheUiModule() {
        val modules = ServiceLoader.load(TrackLensModule::class.java, TrackLensModule::class.java.classLoader).toList()

        // tracklens-ui depends on tracklens-storage, so its StorageModule is found too.
        assertEquals(1, modules.count { it is UiModule })
    }
}
