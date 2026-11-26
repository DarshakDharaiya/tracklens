package io.github.tracklens.ui.navigation

import io.github.tracklens.ui.navigation.Destination.EventDetail
import io.github.tracklens.ui.navigation.Destination.TabRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackTest {
    private val live = TabRoot(Tab.LIVE)
    private val sessions = TabRoot(Tab.SESSIONS)

    @Test
    fun startsAtLiveAndBackLeavesTrackLens() {
        val stack = BackStack.START

        assertEquals(live, stack.current)
        assertEquals(Tab.LIVE, stack.currentTab)
        assertFalse(stack.canGoBack)
        assertNull(stack.pop())
    }

    @Test
    fun anotherTabSitsAboveLiveSoBackReturnsToLive() {
        val stack = BackStack.START.selectTab(Tab.SESSIONS)

        assertEquals(listOf(live, sessions), stack.entries)
        assertEquals(BackStack.START, stack.pop())
    }

    @Test
    fun switchingTabsDoesNotGrowTheStack() {
        val stack =
            BackStack.START
                .selectTab(Tab.SESSIONS)
                .selectTab(Tab.PLAN)
                .selectTab(Tab.MORE)

        assertEquals(listOf(live, TabRoot(Tab.MORE)), stack.entries)
        assertEquals(BackStack.START, BackStack.START.selectTab(Tab.MORE).selectTab(Tab.LIVE))
    }

    @Test
    fun pushedScreensBelongToTheirTabAndPopInOrder() {
        val stack =
            BackStack.START
                .selectTab(Tab.SESSIONS)
                .push(EventDetail(7))
                .push(EventDetail(8))

        assertEquals(EventDetail(8), stack.current)
        assertEquals(Tab.SESSIONS, stack.currentTab)
        assertEquals(EventDetail(7), stack.pop()!!.current)
        assertEquals(sessions, stack.pop()!!.pop()!!.current)
    }

    @Test
    fun reselectingTheCurrentTabReturnsToItsRoot() {
        val stack = BackStack.START.push(EventDetail(1)).push(EventDetail(2))

        assertEquals(BackStack.START, stack.selectTab(Tab.LIVE))
        assertEquals(
            listOf(live, sessions),
            BackStack.START
                .selectTab(Tab.SESSIONS)
                .push(EventDetail(3))
                .selectTab(Tab.SESSIONS)
                .entries,
        )
    }

    @Test
    fun routesRoundTrip() {
        val stack = BackStack.START.selectTab(Tab.PLAN).push(EventDetail(42))

        val routes = stack.entries.map { it.route }

        assertEquals(listOf("live", "plan", "event/42"), routes)
        assertEquals(stack, BackStack.of(routes))
    }

    @Test
    fun restoringKeepsTheRulesWhateverWasSaved() {
        assertEquals(BackStack.START, BackStack.of(emptyList()))
        assertEquals(BackStack.START, BackStack.of(listOf("timeline", "event/x", "live")))
        assertEquals(listOf(live, sessions), BackStack.of(listOf("sessions")).entries)
        assertTrue(BackStack.of(listOf("live", "live", "event/5")).entries == listOf(live, EventDetail(5)))
    }
}
