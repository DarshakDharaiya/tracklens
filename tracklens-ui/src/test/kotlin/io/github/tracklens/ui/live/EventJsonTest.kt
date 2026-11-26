package io.github.tracklens.ui.live

import io.github.tracklens.RedactionConfig
import io.github.tracklens.Severity
import io.github.tracklens.internal.UnknownValue
import io.github.tracklens.ui.live.LiveFixtures.event
import io.github.tracklens.ui.live.LiveFixtures.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EventJsonTest {
    @Test
    fun writesTheWholeEventAsIndentedJson() {
        val json = EventJson.encode(session[2])

        assertEquals(
            """
            {
              "sequence": 3,
              "name": "add_to_cart",
              "kind": "EVENT",
              "timestamp": "2023-11-14T22:13:23.750Z",
              "timestampMillis": 1700000003750,
              "elapsedMillis": 3750,
              "screen": "Product",
              "source": "firebase",
              "sessionId": "3f2c9a1e-5b7d-4e2a-9c1f-0d8e6b4a2c71",
              "params": {
                "item_id": "SKU-2",
                "email": "[REDACTED]",
                "tags": [
                  "sale",
                  "new"
                ]
              },
              "issues": [
                {
                  "code": "RULE_0",
                  "severity": "WARNING",
                  "message": "Problem 0 with add_to_cart",
                  "paramKey": "item_id"
                }
              ]
            }
            """.trimIndent(),
            json,
        )
    }

    @Test
    fun escapesTextAndKeepsNumbersValid() {
        val json =
            EventJson.encode(
                event(
                    1,
                    "quote\"back\\slash",
                    linkedMapOf(
                        "text" to "line\nbreak\ttab\u0001",
                        "nan" to Double.NaN,
                        "inf" to Float.POSITIVE_INFINITY,
                        "big" to BigDecimal("12345678901234567890.5"),
                        "empty" to emptyMap<String, Any>(),
                        "none" to emptyList<Any>(),
                        "null" to null,
                        "flag" to true,
                        "unknown" to UnknownValue("com.example.Thing", "Thing(1)"),
                    ),
                ),
            )

        assertTrue(json, "\"name\": \"quote\\\"back\\\\slash\"" in json)
        assertTrue(json, "\"text\": \"line\\nbreak\\ttab\\u0001\"" in json)
        assertTrue(json, "\"nan\": \"NaN\"" in json)
        assertTrue(json, "\"inf\": \"Infinity\"" in json)
        assertTrue(json, "\"big\": 12345678901234567890.5" in json)
        assertTrue(json, "\"empty\": {}" in json)
        assertTrue(json, "\"none\": []" in json)
        assertTrue(json, "\"null\": null" in json)
        assertTrue(json, "\"flag\": true" in json)
        assertTrue(json, "\"unknown\": \"Thing(1)\"" in json)
    }

    @Test
    fun formatsTypesValuesAndTimes() {
        assertEquals("text", EventFormat.type("a"))
        assertEquals("integer", EventFormat.type(3L))
        assertEquals("decimal", EventFormat.type(9.99))
        assertEquals("boolean", EventFormat.type(false))
        assertEquals("map", EventFormat.type(mapOf("a" to 1)))
        assertEquals("list", EventFormat.type(listOf(1)))
        assertEquals("null", EventFormat.type(null))
        assertEquals("Thing", EventFormat.type(UnknownValue("com.example.Thing", "t")))
        assertEquals("{a: [1, x]}", EventFormat.value(mapOf("a" to listOf(1, "x"))))
        assertEquals("+1:02.345", EventFormat.elapsed(62_345))
        assertEquals("+0:00.000", EventFormat.elapsed(0))
    }

    @Test
    fun recognizesRedactedValues() {
        val defaults = RedactionConfig.DEFAULT
        val hashing = RedactionConfig.Builder().hashValues(true).build()

        assertTrue(EventFormat.isRedacted("[REDACTED]", defaults))
        assertTrue(EventFormat.isRedacted("mail [REDACTED] now", defaults))
        assertTrue(EventFormat.isRedacted(listOf("ok", mapOf("e" to "[REDACTED]")), defaults))
        assertFalse(EventFormat.isRedacted("plain", defaults))
        assertFalse(EventFormat.isRedacted(42L, defaults))
        assertTrue(EventFormat.isRedacted("sha256:1a2b3c4d", hashing))
        assertFalse(EventFormat.isRedacted("sha256:1a2b3c4d", defaults))
        assertEquals(Severity.ERROR, EventFormat.worstSeverity(session[4]))
    }
}
