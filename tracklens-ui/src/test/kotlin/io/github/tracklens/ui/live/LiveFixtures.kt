package io.github.tracklens.ui.live

import io.github.tracklens.EventKind
import io.github.tracklens.Severity
import io.github.tracklens.TrackedEvent
import io.github.tracklens.ValidationIssues
import io.github.tracklens.internal.TrackedEvents

/** A small, varied live session for the Live and event detail tests. Times are fixed for stable screenshots. */
object LiveFixtures {
    private const val START = 1_700_000_000_000L

    fun event(
        sequence: Long,
        name: String,
        params: Map<String, Any?> = emptyMap(),
        screen: String? = null,
        kind: EventKind = EventKind.EVENT,
        source: String? = null,
        severities: List<Severity> = emptyList(),
    ): TrackedEvent =
        TrackedEvents.create(
            sequence = sequence,
            name = name,
            params = params,
            screen = screen,
            issues =
                severities.mapIndexed {
                    index,
                    severity,
                    ->
                    ValidationIssues.create("RULE_$index", severity, "Problem $index with $name", params.keys.firstOrNull())
                },
            kind = kind,
            timestampMillis = START + sequence * 1_250,
            elapsedMillis = sequence * 1_250,
            sessionId = "3f2c9a1e-5b7d-4e2a-9c1f-0d8e6b4a2c71",
            source = source,
        )

    val session: List<TrackedEvent> =
        listOf(
            event(1, "Home", kind = EventKind.SCREEN, screen = "Home"),
            event(2, "view_item", mapOf("item_id" to "SKU-1", "price" to 9.99, "quantity" to 1L), screen = "Product", source = "firebase"),
            event(
                3,
                "add_to_cart",
                mapOf("item_id" to "SKU-2", "email" to "[REDACTED]", "tags" to listOf("sale", "new")),
                screen = "Product",
                source = "firebase",
                severities = listOf(Severity.WARNING),
            ),
            event(4, "plan", mapOf("value" to "pro"), kind = EventKind.USER_PROPERTY, screen = "Product"),
            event(
                5,
                "begin-checkout",
                mapOf("value" to 19.98),
                screen = "Cart",
                source = "amplitude",
                severities = listOf(Severity.ERROR, Severity.INFO),
            ),
        )
}
