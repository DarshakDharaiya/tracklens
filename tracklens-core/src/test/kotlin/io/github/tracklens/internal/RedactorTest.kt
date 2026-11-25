package io.github.tracklens.internal

import io.github.tracklens.RedactionConfig
import io.github.tracklens.UserIdRedaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RedactorTest {
    private val defaults = Redactor(RedactionConfig.DEFAULT)

    @Before
    fun clearDiagnostics() = Diagnostics.clear()

    @Test
    fun defaultKeyRulesReplaceWholeValuesAtAnyDepth() {
        val result =
            defaults.redactParams(
                mapOf(
                    "user_email" to "jane@example.com",
                    "Password" to 1234,
                    "AccessToken" to mapOf("value" to "abc"),
                    "profile" to mapOf("phone_number" to 4155550100L, "name" to "Jane"),
                    "items" to listOf(mapOf("client_secret" to "s3cr3t", "item_id" to "SKU-1")),
                    "screen" to "Home",
                ),
            )

        assertEquals(
            mapOf(
                "user_email" to REDACTED,
                "Password" to REDACTED,
                "AccessToken" to REDACTED,
                "profile" to mapOf("phone_number" to REDACTED, "name" to "Jane"),
                "items" to listOf(mapOf("client_secret" to REDACTED, "item_id" to "SKU-1")),
                "screen" to "Home",
            ),
            result.values,
        )
        assertEquals(setOf("user_email", "Password", "AccessToken", "profile", "items"), result.redactedKeys)
    }

    @Test
    fun patternsReplaceOnlyTheMatchInsideText() {
        val text = "Contact jane.doe@example.com or +1 415-555-0100 today"

        assertEquals("Contact $REDACTED or $REDACTED today", defaults.redactText(text))
    }

    @Test
    fun phonePatternLeavesPlainDigitRunsAlone() {
        for (notAPhone in listOf("order 1234567890123", "1700000000000", "v2.14.1", "SKU-4155550100")) {
            assertSame(notAPhone, defaults.redactText(notAPhone))
        }
        for (phone in listOf("(415) 555-0100", "415.555.0100", "415-555-0100", "+44 20 7946 0958")) {
            assertEquals(phone, REDACTED, defaults.redactText(phone))
        }
    }

    @Test
    fun cardNumbersAreRedactedOnlyWhenTheyPassLuhn() {
        assertEquals("paid with $REDACTED", defaults.redactText("paid with 4111 1111 1111 1111"))
        assertEquals("paid with 4111 1111 1111 1112", defaults.redactText("paid with 4111 1111 1111 1112"))

        val result = defaults.redactParams(mapOf<String, Any?>("card" to 4111111111111111L, "timestamp" to 1700000000000L, "count" to 12))
        assertEquals(REDACTED, result.values["card"])
        assertEquals("not a card (fails Luhn), stays a Long", 1700000000000L, result.values["timestamp"])
        assertEquals("too short to check, stays an Int", 12, result.values["count"])
    }

    @Test
    fun luhnChecksum() {
        for (valid in listOf("4111111111111111", "5500-0000-0000-0004", "378282246310005", "6011 1111 1111 1117")) {
            assertTrue(valid, Redactor.passesLuhn(valid))
        }
        for (invalid in listOf("4111111111111112", "1234567890123", "4111", "4111111111111111111111")) {
            assertFalse(invalid, Redactor.passesLuhn(invalid))
        }
    }

    @Test
    fun unknownValueTextIsRedacted() {
        val contact = UnknownValue("com.example.Contact", "Contact(email=jane@example.com)")

        val result = defaults.redactParams(mapOf("contact" to contact))

        assertEquals(UnknownValue("com.example.Contact", "Contact(email=$REDACTED)"), result.values["contact"])
    }

    @Test
    fun hashModeKeepsEqualValuesComparable() {
        val hashing = Redactor(RedactionConfig.Builder().hashValues(true).build())

        val first = hashing.redactParams(mapOf("email" to "jane@example.com")).values.getValue("email") as String
        val again = hashing.redactParams(mapOf("email" to "jane@example.com")).values.getValue("email") as String
        val other = hashing.redactParams(mapOf("email" to "john@example.com")).values.getValue("email") as String

        assertTrue(first, first.matches(Regex("sha256:[0-9a-f]{8}")))
        assertEquals(first, again)
        assertNotEquals(first, other)
        assertEquals("note ${Redactor.hash("jane@example.com")}", hashing.redactText("note jane@example.com"))
    }

    @Test
    fun userIdModes() {
        fun redactorWith(mode: UserIdRedaction) = Redactor(RedactionConfig.Builder().userId(mode).build())

        assertEquals("user-42", redactorWith(UserIdRedaction.NONE).redactUserId("user-42"))
        assertEquals(Redactor.hash("user-42"), redactorWith(UserIdRedaction.HASH).redactUserId("user-42"))
        assertEquals("u*****2", redactorWith(UserIdRedaction.MASK).redactUserId("user-42"))
        assertEquals("**", redactorWith(UserIdRedaction.MASK).redactUserId("ab"))
        assertNull(defaults.redactUserId(null))
        assertEquals("user ids are hashed by default", Redactor.hash("user-42"), defaults.redactUserId("user-42"))
    }

    @Test
    fun userPropertiesUseKeyRulesAndPatterns() {
        assertEquals(REDACTED, defaults.redactUserProperty("email", "jane@example.com"))
        assertEquals("gold", defaults.redactUserProperty("tier", "gold"))
        assertEquals("call $REDACTED", defaults.redactUserProperty("note", "call +1 415 555 0100"))
        assertNull(defaults.redactUserProperty("tier", null))
    }

    @Test
    fun exactKeysAreCaseInsensitiveButNotPartial() {
        val exact =
            Redactor(
                RedactionConfig
                    .Builder()
                    .clear()
                    .keys("Email")
                    .build(),
            )

        assertTrue(exact.isSensitiveKey("email"))
        assertTrue(exact.isSensitiveKey("EMAIL"))
        assertFalse(exact.isSensitiveKey("my_email"))
    }

    @Test
    fun customRegexWorksAndAnInvalidOneIsSkipped() {
        val custom =
            Redactor(
                RedactionConfig
                    .Builder()
                    .clear()
                    .pattern("SKU-\\d+")
                    .pattern("([unclosed")
                    .build(),
            )

        assertEquals("bought $REDACTED", custom.redactText("bought SKU-1042"))
        assertTrue(
            Diagnostics
                .snapshot()
                .single()
                .message
                .startsWith("Redaction pattern 'custom-2'"),
        )
    }

    @Test
    fun aClearedConfigRedactsNothingAndKeepsTheSameMap() {
        val params = mapOf("email" to "jane@example.com", "card" to "4111 1111 1111 1111")

        val result = Redactor(RedactionConfig.Builder().clear().build()).redactParams(params)

        assertSame(params, result.values)
        assertTrue(result.redactedKeys.isEmpty())
    }

    @Test
    fun maskKeepsFirstAndLastCharacters() {
        assertEquals("j******e", Redactor.mask("jane.doe"))
        assertEquals("*", Redactor.mask("j"))
        assertEquals("", Redactor.mask(""))
    }

    private companion object {
        const val REDACTED = "[REDACTED]"
    }
}
