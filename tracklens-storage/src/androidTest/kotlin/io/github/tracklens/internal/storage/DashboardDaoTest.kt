package io.github.tracklens.internal.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.EventKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every widget query of plan section 3.4 against [DashboardFixture]'s hand-computed numbers. */
@RunWith(AndroidJUnit4::class)
class DashboardDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: TrackLensDatabase
    private lateinit var fixture: DashboardFixture
    private val dao get() = db.dashboard()

    @Before
    fun setUp() =
        runBlocking {
            db = TrackLensDatabase.inMemory(context)
            fixture = DashboardFixture(db).insert()
        }

    @After
    fun tearDown() = db.close()

    @Test
    fun sessionListPutsTheActiveSessionFirstThenNewest() =
        runBlocking {
            val list = db.sessions().list().first()

            assertEquals(listOf("A", "C", "B"), list.map { it.uuid })
            assertNull(list.first().endedAt)
            assertEquals(SessionEndReason.ACTIVE, list.first().endReason)
        }

    @Test
    fun summaryCard() =
        runBlocking {
            assertEquals(EventTotals(total = 10, unique = 7, invalid = 2), dao.totals(fixture.a).first())
            assertEquals(4L, dao.screenCount(fixture.a).first())
            assertEquals(6L, dao.warningCount(fixture.a).first())
            assertEquals(EventTotals(total = 0, unique = 0, invalid = 0), dao.totals(fixture.c).first())
        }

    @Test
    fun timelineHasEveryRecordInOrder() =
        runBlocking {
            val timeline = dao.timeline(fixture.a).first()

            assertEquals((1L..14L).toList(), timeline.map { it.seq })
            assertEquals(119_000L, timeline.last().elapsedMs)
            assertEquals(listOf(5L, 10L, 12L, 13L), timeline.filter { it.maxSeverity > 0 }.map { it.seq })
        }

    @Test
    fun eventsPerBucketCountsEventsAndWarnings() =
        runBlocking {
            assertEquals(listOf(TimeBucket(0, 4, 1), TimeBucket(1, 6, 2)), dao.eventsPerBucket(fixture.a, bucketMs = 60_000).first())
            assertEquals(
                listOf(TimeBucket(0, 2, 0), TimeBucket(1, 2, 1), TimeBucket(2, 1, 0), TimeBucket(3, 5, 2)),
                dao.eventsPerBucket(fixture.a, bucketMs = 30_000).first(),
            )
        }

    @Test
    fun topEventsAndNameCounts() =
        runBlocking {
            assertEquals(
                listOf(NameCount("view_item", 3, 3), NameCount("add_to_cart", 2, 0), NameCount("add_payment_info", 1, 1)),
                dao.topEvents(fixture.a, limit = 3).first(),
            )
            assertEquals(
                mapOf(
                    "add_payment_info" to 1L,
                    "add_to_cart" to 2L,
                    "app_open" to 1L,
                    "begin-checkout" to 1L,
                    "purchase" to 1L,
                    "view_cart" to 1L,
                    "view_item" to 3L,
                ),
                dao.nameCounts(fixture.a).first().associate { it.name to it.count },
            )
            assertEquals(
                listOf("begin-checkout" to 1L, "view_item" to 1L),
                dao.invalidNameCounts(fixture.a).first().map {
                    it.name to
                        it.count
                },
            )
        }

    @Test
    fun screenFlow() =
        runBlocking {
            val visits = dao.screenVisits(fixture.a).first()

            assertEquals(listOf("Home", "Product", "Cart", "Product", "Checkout"), visits.map { it.screenName })
            assertEquals(listOf(10_000L, 50_000L, 30_000L, 20_000L, 15_000L), visits.map { it.durationMs })
            val perVisit = dao.eventsPerVisit(fixture.a).first().associate { it.screenVisitId to it.count }
            assertEquals(listOf(2L, 4L, 3L, 2L, 2L), visits.map { perVisit[it.id] })
            assertEquals("one event outside any visit", 1L, perVisit[null])
        }

    @Test
    fun warningsAreGroupedWorstFirstAndListedWithTheirEvents() =
        runBlocking {
            assertEquals(
                listOf(
                    WarningGroup("FB_EVENT_NAME_INVALID", 3, 1),
                    WarningGroup("PLAN_MISSING_PARAM", 3, 1),
                    WarningGroup("FB_PARAM_TYPE", 2, 2),
                    WarningGroup("SE_DROPPED_EVENTS", 2, 1),
                    WarningGroup("PLAN_DEPRECATED", 1, 1),
                ),
                db.findings().warningGroups(fixture.a).first(),
            )
            val paramType = db.findings().warningsWithCode(fixture.a, "FB_PARAM_TYPE")
            assertEquals(listOf(5L, 10L), paramType.map { it.seq })
            assertEquals(listOf("view_item", "view_item"), paramType.map { it.name })
            val sessionLevel = db.findings().warningsWithCode(fixture.a, "SE_DROPPED_EVENTS").single()
            assertNull(sessionLevel.eventId)
            assertNull(sessionLevel.seq)
        }

    @Test
    fun funnelEventsAreTheRequestedNamesInOrder() =
        runBlocking {
            val funnel = db.findings().funnelEvents(fixture.a, listOf("view_item", "add_to_cart", "purchase"))

            assertEquals(listOf(4L, 5L, 6L, 10L, 11L, 14L), funnel.map { it.seq })
            assertEquals("{\"transaction_id\":\"T-1\"}", funnel.last().paramsJson)
        }

    @Test
    fun comparisonCountsAndOrder() =
        runBlocking {
            val counts = db.findings().comparisonCounts(fixture.a, fixture.b).groupBy({ it.sessionId }, { it.name to it.count })

            assertEquals(7, counts.getValue(fixture.a).size)
            assertEquals(listOf("app_open" to 1L, "purchase" to 1L, "refund" to 1L, "view_item" to 2L), counts.getValue(fixture.b))
            assertEquals(listOf("app_open", "view_item", "view_item", "purchase", "refund"), db.findings().nameSequence(fixture.b))
            assertEquals(
                listOf(
                    "app_open",
                    "view_item",
                    "view_item",
                    "add_to_cart",
                    "view_cart",
                    "view_item",
                    "add_to_cart",
                    "begin-checkout",
                    "add_payment_info",
                    "purchase",
                ),
                db.findings().nameSequence(fixture.a),
            )
            assertEquals(3, db.findings().nameSequence(fixture.a, limit = 3).size)
        }

    @Test
    fun eventListPagesByKeyAndFilters() =
        runBlocking {
            val first = db.eventList().eventPage(fixture.a, afterSeq = 0, limit = 5)
            assertEquals((1L..5L).toList(), first.map { it.seq })
            assertEquals((6L..10L).toList(), db.eventList().eventPage(fixture.a, afterSeq = first.last().seq, limit = 5).map { it.seq })
            assertEquals(
                "add_to_cart and add_payment_info",
                listOf(
                    6L,
                    11L,
                    13L,
                ),
                db.eventList().eventPage(fixture.a, afterSeq = 0, nameLike = "add%").map {
                    it.seq
                },
            )
            assertEquals(listOf(11L, 13L), db.eventList().eventPage(fixture.a, afterSeq = 6, nameLike = "add%").map { it.seq })
            assertEquals(
                listOf(5L, 10L, 12L),
                db.eventList().eventPage(fixture.a, afterSeq = 0, minSeverity = SeverityLevel.WARNING).map { it.seq },
            )
            assertEquals(
                EventKind.USER_PROPERTY,
                db
                    .eventList()
                    .eventPage(fixture.a, afterSeq = 8, limit = 1)
                    .single()
                    .kind,
            )
        }

    @Test
    fun searchByParameter() =
        runBlocking {
            val ids = fixture.eventIds
            assertEquals(listOf(ids[10], ids[11]), db.eventList().eventsWithParam(fixture.a, "item_id", "SKU-2"))
            assertEquals(listOf(4L, 5L, 6L, 10L, 11L).map { ids[it] }, db.eventList().eventsWithParam(fixture.a, "item_id", "SKU-%"))
            assertEquals(listOf(ids[14]), db.eventList().eventsWithParam(fixture.a, "transaction_id", "%"))
            assertTrue(db.eventList().eventsWithParam(fixture.b, "item_id", "%").isEmpty())
        }

    @Test
    fun countersAndLifecycleUpdates() =
        runBlocking {
            db.sessions().addCounts(fixture.a, events = 14, warnings = 3, errors = 2, dropped = 3)
            db.sessions().addCounts(fixture.a, events = 1, warnings = 0, errors = 0, dropped = 0)
            db.sessions().setPinned(fixture.a, true)
            db.sessions().setLabel(fixture.a, "Checkout run", "Pixel")
            db.sessions().end(fixture.a, endedAt = 200_000, reason = SessionEndReason.MANUAL)

            val session = requireNotNull(db.sessions().get(fixture.a))
            assertEquals(
                listOf(15L, 3L, 2L, 3L),
                listOf(session.eventCount, session.warningCount, session.errorCount, session.droppedCount),
            )
            assertTrue(session.pinned)
            assertEquals("Checkout run", session.label)
            assertEquals(SessionEndReason.MANUAL, session.endReason)
            assertEquals(fixture.a, db.sessions().byUuid("A")?.id)
        }

    @Test
    fun deletingASessionDeletesEverythingInItAndNothingElse() =
        runBlocking {
            db.sessions().delete(listOf(fixture.a))

            assertTrue(dao.timeline(fixture.a).first().isEmpty())
            assertTrue(dao.screenVisits(fixture.a).first().isEmpty())
            assertEquals(0L, dao.warningCount(fixture.a).first())
            assertTrue(db.eventList().eventsWithParam(fixture.a, "item_id", "%").isEmpty())
            assertEquals(5, dao.timeline(fixture.b).first().size)
        }

    @Test
    fun deletingAVisitKeepsItsEventsWithoutAVisit() =
        runBlocking {
            db.openHelper.writableDatabase.execSQL("DELETE FROM screen_visits WHERE session_id = ${fixture.a} AND order_index = 0")

            val perVisit = dao.eventsPerVisit(fixture.a).first().associate { it.screenVisitId to it.count }
            assertEquals("seq 1, 2 and 14 now outside any visit", 3L, perVisit[null])
            assertEquals(14, dao.timeline(fixture.a).first().size)
        }

    @Test
    fun dashboardFlowsFollowNewWrites() =
        runBlocking {
            val before = dao.totals(fixture.c).first()
            db.writes().insertEvent(
                EventEntity(
                    sessionId = fixture.c,
                    seq = 1,
                    timestamp = 2_000,
                    elapsedMs = 0,
                    kind = EventKind.EVENT,
                    name = "late",
                    paramsJson = "{}",
                    paramCount = 0,
                ),
                params = emptyList(),
                warnings = emptyList(),
            )

            assertEquals(0L, before.total)
            assertEquals(1L, dao.totals(fixture.c).first().total)
        }

    @Test
    fun diagnosticsAreTrimmedToTheNewest() =
        runBlocking {
            repeat(5) {
                db.writes().insertDiagnostic(
                    DiagnosticEntity(timestamp = it.toLong(), component = "PIPELINE", level = "ERROR", message = "failure $it"),
                )
            }

            db.writes().trimDiagnostics(keep = 2)

            val left =
                db.openHelper.readableDatabase.query("SELECT message FROM diagnostics ORDER BY id").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
            assertEquals(listOf("failure 3", "failure 4"), left)
        }
}
