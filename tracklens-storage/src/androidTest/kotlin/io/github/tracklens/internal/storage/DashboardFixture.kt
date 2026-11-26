package io.github.tracklens.internal.storage

import io.github.tracklens.EventKind
import io.github.tracklens.internal.storage.ScreenVisitSource.ACTIVITY
import io.github.tracklens.internal.storage.SeverityLevel.ERROR
import io.github.tracklens.internal.storage.SeverityLevel.INFO
import io.github.tracklens.internal.storage.SeverityLevel.NONE
import io.github.tracklens.internal.storage.SeverityLevel.WARNING

/**
 * The fixture every widget query is checked against: three sessions with hand-computed expected numbers.
 *
 * Session A (active), five screen visits and 14 records:
 *
 * | seq | elapsed | kind | name | visit | severity |
 * |---|---|---|---|---|---|
 * | 1 | 0 s | SCREEN | Home | 0 Home | — |
 * | 2 | 2 s | EVENT | app_open | 0 | — |
 * | 3 | 12 s | SCREEN | Product | 1 Product | — |
 * | 4 | 15 s | EVENT | view_item (SKU-1) | 1 | — |
 * | 5 | 30 s | EVENT | view_item (SKU-1) | 1 | warning |
 * | 6 | 45 s | EVENT | add_to_cart (SKU-1) | 1 | — |
 * | 7 | 61 s | SCREEN | Cart | 2 Cart | — |
 * | 8 | 65 s | EVENT | view_cart | 2 | — |
 * | 9 | 70 s | USER_PROPERTY | plan | 2 | — |
 * | 10 | 91 s | EVENT | view_item (SKU-2) | 3 Product | error |
 * | 11 | 95 s | EVENT | add_to_cart (SKU-2) | 3 | — |
 * | 12 | 111 s | EVENT | begin-checkout | 4 Checkout | error |
 * | 13 | 115 s | EVENT | add_payment_info | 4 | info |
 * | 14 | 119 s | EVENT | purchase (T-1) | none | — |
 *
 * Warnings: FB_PARAM_TYPE on 5 and 10 (warning), PLAN_MISSING_PARAM on 10 and FB_EVENT_NAME_INVALID on 12 (error),
 * PLAN_DEPRECATED on 13 (info), and a session-level SE_DROPPED_EVENTS (warning).
 *
 * Session B (ended, older): app_open, view_item, view_item, purchase, refund. Session C (ended, newest): empty.
 */
class DashboardFixture(
    private val db: TrackLensDatabase,
) {
    var a = 0L
        private set
    var b = 0L
        private set
    var c = 0L
        private set

    /** Event keys of session A by sequence number. */
    val eventIds = mutableMapOf<Long, Long>()

    suspend fun insert(): DashboardFixture {
        a = db.sessions().insert(session("A", startedAt = 1_000, endedAt = null))
        b = db.sessions().insert(session("B", startedAt = 500, endedAt = 900))
        c = db.sessions().insert(session("C", startedAt = 2_000, endedAt = 3_000))
        insertSessionA()
        insertSessionB()
        return this
    }

    private suspend fun insertSessionA() {
        val visits = insertVisitsA()
        val sku1 = mapOf("item_id" to "SKU-1")
        val sku2 = mapOf("item_id" to "SKU-2")
        record(1, 0, EventKind.SCREEN, "Home", visits[0])
        record(2, 2_000, EventKind.EVENT, "app_open", visits[0])
        record(3, 12_000, EventKind.SCREEN, "Product", visits[1])
        record(4, 15_000, EventKind.EVENT, "view_item", visits[1], params = sku1)
        record(
            5,
            30_000,
            EventKind.EVENT,
            "view_item",
            visits[1],
            WARNING,
            sku1 + ("price" to "9.99"),
            listOf(warning("FB_PARAM_TYPE", WARNING, "price")),
        )
        record(6, 45_000, EventKind.EVENT, "add_to_cart", visits[1], params = sku1)
        record(7, 61_000, EventKind.SCREEN, "Cart", visits[2])
        record(8, 65_000, EventKind.EVENT, "view_cart", visits[2])
        record(9, 70_000, EventKind.USER_PROPERTY, "plan", visits[2], params = mapOf("value" to "pro"))
        record(
            10,
            91_000,
            EventKind.EVENT,
            "view_item",
            visits[3],
            ERROR,
            sku2 + ("price" to "12"),
            listOf(warning("PLAN_MISSING_PARAM", ERROR, "currency"), warning("FB_PARAM_TYPE", WARNING, "price")),
        )
        record(11, 95_000, EventKind.EVENT, "add_to_cart", visits[3], params = sku2)
        record(
            12,
            111_000,
            EventKind.EVENT,
            "begin-checkout",
            visits[4],
            ERROR,
            warnings = listOf(warning("FB_EVENT_NAME_INVALID", ERROR, null)),
        )
        record(13, 115_000, EventKind.EVENT, "add_payment_info", visits[4], INFO, warnings = listOf(warning("PLAN_DEPRECATED", INFO, null)))
        record(14, 119_000, EventKind.EVENT, "purchase", visit = null, params = mapOf("transaction_id" to "T-1"))
        db.writes().insertWarnings(
            listOf(WarningEntity(sessionId = a, code = "SE_DROPPED_EVENTS", severity = WARNING, message = "3 dropped")),
        )
    }

    private suspend fun insertVisitsA(): List<Long> =
        listOf("Home" to (0L to 10_000L), "Product" to (10_000L to 50_000L), "Cart" to (60_000L to 30_000L))
            .plus(listOf("Product" to (90_000L to 20_000L), "Checkout" to (110_000L to 15_000L)))
            .mapIndexed { index, (screen, timing) ->
                db.writes().insertVisit(
                    ScreenVisitEntity(
                        sessionId = a,
                        orderIndex = index,
                        screenName = screen,
                        source = ACTIVITY,
                        enteredElapsedMs = timing.first,
                        exitedElapsedMs = timing.first + timing.second,
                        durationMs = timing.second,
                    ),
                )
            }

    private suspend fun insertSessionB() {
        listOf("app_open", "view_item", "view_item", "purchase", "refund").forEachIndexed { index, name ->
            db.writes().insertEvent(
                EventEntity(
                    sessionId = b,
                    seq = index + 1L,
                    timestamp = 500L + index,
                    elapsedMs = index * 1_000L,
                    kind = EventKind.EVENT,
                    name = name,
                    paramsJson = "{}",
                    paramCount = 0,
                ),
                params = emptyList(),
                warnings = emptyList(),
            )
        }
    }

    @Suppress("LongParameterList") // One fixture row.
    private suspend fun record(
        seq: Long,
        elapsedMs: Long,
        kind: EventKind,
        name: String,
        visit: Long?,
        severity: Int = NONE,
        params: Map<String, String> = emptyMap(),
        warnings: List<WarningEntity> = emptyList(),
    ) {
        val id =
            db.writes().insertEvent(
                EventEntity(
                    sessionId = a,
                    seq = seq,
                    timestamp = 1_000 + elapsedMs,
                    elapsedMs = elapsedMs,
                    kind = kind,
                    name = name,
                    screenName = null,
                    screenVisitId = visit,
                    paramsJson = params.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "\"$k\":\"$v\"" },
                    paramCount = params.size,
                    maxSeverity = severity,
                ),
                params =
                    params.map { (key, value) ->
                        EventParamEntity(eventId = 0, sessionId = a, key = key, valueType = ParamValueType.STRING, valueText = value)
                    },
                warnings = warnings,
            )
        eventIds[seq] = id
    }

    private fun warning(
        code: String,
        severity: Int,
        paramKey: String?,
    ) = WarningEntity(sessionId = a, code = code, severity = severity, message = "$code message", paramKey = paramKey)

    private fun session(
        uuid: String,
        startedAt: Long,
        endedAt: Long?,
    ) = SessionEntity(
        uuid = uuid,
        startedAt = startedAt,
        endedAt = endedAt,
        endReason = if (endedAt == null) SessionEndReason.ACTIVE else SessionEndReason.MANUAL,
        startElapsedRealtime = 0,
        libraryVersion = "test",
    )
}
