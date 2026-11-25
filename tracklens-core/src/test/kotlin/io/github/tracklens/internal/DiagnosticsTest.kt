package io.github.tracklens.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsTest {
    @Before
    fun clear() = Diagnostics.clear()

    @Test
    fun keepsTheNewestEntriesUpToCapacity() {
        repeat(Diagnostics.CAPACITY + 50) { Diagnostics.report(Component.PIPELINE, DiagnosticLevel.INFO, "entry $it") }

        val entries = Diagnostics.snapshot()
        assertEquals(Diagnostics.CAPACITY, entries.size)
        assertEquals("entry 50", entries.first().message)
        assertEquals("entry ${Diagnostics.CAPACITY + 49}", entries.last().message)
        assertEquals(50L, Diagnostics.evictedCount)
    }

    @Test
    fun describesTheErrorWithClassMessageAndFrames() {
        Diagnostics.report(Component.STORAGE, DiagnosticLevel.ERROR, "write failed", IllegalStateException("disk full"))

        val entry = Diagnostics.snapshot().single()
        assertEquals(Component.STORAGE, entry.component)
        assertTrue(requireNotNull(entry.error).startsWith("java.lang.IllegalStateException: disk full"))
        assertTrue(requireNotNull(entry.error).contains("\n    at "))
    }

    @Test
    fun neverThrowsEvenForBrokenThrowablesOrHugeMessages() {
        val broken =
            object : RuntimeException() {
                override val message: String get() = error("message getter fails")

                override fun getStackTrace(): Array<StackTraceElement> = error("stack fails")
            }

        Diagnostics.report(Component.API, DiagnosticLevel.ERROR, "x".repeat(1_000_000), broken)

        val entry = Diagnostics.snapshot().single()
        assertEquals(1_000, entry.message.length)
        assertEquals(broken.javaClass.name, entry.error)
    }

    @Test
    fun entriesWithoutAnErrorHaveNoDescription() {
        Diagnostics.report(Component.INSTALL, DiagnosticLevel.WARNING, "inactive")

        assertNull(Diagnostics.snapshot().single().error)
    }

    @Test
    fun concurrentReportsAreAllCounted() {
        val threads =
            List(8) {
                Thread { repeat(1_000) { Diagnostics.report(Component.PIPELINE, DiagnosticLevel.INFO, "x") } }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(Diagnostics.CAPACITY, Diagnostics.snapshot().size)
        assertEquals(8_000L - Diagnostics.CAPACITY, Diagnostics.evictedCount)
    }
}
