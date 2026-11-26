package io.github.tracklens.ui.live

import androidx.compose.runtime.saveable.SaverScope
import io.github.tracklens.EventKind
import io.github.tracklens.Severity
import io.github.tracklens.ui.live.LiveFixtures.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFilterTest {
    private fun LiveFilter.sequences() = apply(session).map { it.sequence }

    @Test
    fun noFilterKeepsEverythingAsIs() {
        assertFalse(LiveFilter.NONE.isActive)
        assertSame(session, LiveFilter.NONE.apply(session))
    }

    @Test
    fun searchMatchesNamesScreensKeysAndValuesIgnoringCase() {
        assertEquals(listOf(3L, 5L), LiveFilter(query = "CART").sequences())
        assertEquals(listOf(2L, 3L), LiveFilter(query = "item_id").sequences())
        assertEquals(listOf(3L), LiveFilter(query = "sku-2").sequences())
        assertEquals(listOf(3L), LiveFilter(query = "SALE").sequences())
        assertEquals(listOf(2L), LiveFilter(query = "9.99").sequences())
        assertEquals(session.map { it.sequence }, LiveFilter(query = "   ").sequences())
    }

    @Test
    fun chipsCombineWithAnd() {
        assertEquals(listOf(1L), LiveFilter(kinds = setOf(EventKind.SCREEN)).sequences())
        assertEquals(listOf(1L, 4L), LiveFilter(kinds = setOf(EventKind.SCREEN, EventKind.USER_PROPERTY)).sequences())
        assertEquals(listOf(2L, 3L, 4L), LiveFilter(screens = setOf("Product")).sequences())
        assertEquals(listOf(2L, 3L), LiveFilter(sources = setOf("firebase")).sequences())
        assertEquals(listOf(3L, 5L), LiveFilter(onlyWithIssues = true).sequences())
        assertEquals(listOf(3L), LiveFilter(screens = setOf("Product"), onlyWithIssues = true).sequences())
    }

    @Test
    fun severityMeansThisBadOrWorse() {
        assertEquals(listOf(3L, 5L), LiveFilter(minSeverity = Severity.INFO).sequences())
        assertEquals(listOf(3L, 5L), LiveFilter(minSeverity = Severity.WARNING).sequences())
        assertEquals(listOf(5L), LiveFilter(minSeverity = Severity.ERROR).sequences())
    }

    @Test
    fun survivesSavingAndRestoring() {
        val filter =
            LiveFilter(
                query = "cart",
                kinds = setOf(EventKind.EVENT, EventKind.SCREEN),
                screens = setOf("Cart"),
                sources = setOf("amplitude"),
                onlyWithIssues = true,
                minSeverity = Severity.WARNING,
            )
        val scope = SaverScope { true }

        val saved = with(LiveFilter.Saver) { scope.save(filter) }!!

        assertEquals(filter, LiveFilter.Saver.restore(saved))
        assertTrue(filter.isActive)
    }
}
