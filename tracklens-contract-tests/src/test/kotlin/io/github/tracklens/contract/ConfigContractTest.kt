package io.github.tracklens.contract

import io.github.tracklens.RedactionPattern
import io.github.tracklens.Severity
import io.github.tracklens.TrackLensConfig
import io.github.tracklens.UserIdRedaction
import io.github.tracklens.trackLensConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Configuration runs during the host app's startup, so builders clamp bad values instead of throwing. */
class ConfigContractTest {
    @Test
    fun outOfRangeValuesAreClamped() {
        val config =
            trackLensConfig {
                liveBufferSize(-5)
                sessions { backgroundTimeoutMillis(-1) }
                retention {
                    maxSessions(0)
                    maxAgeMillis(-1)
                    maxEventsPerSession(Int.MIN_VALUE)
                }
            }

        assertEquals(100, config.liveBufferSize)
        assertEquals(0L, config.sessions.backgroundTimeoutMillis)
        assertEquals(1, config.retention.maxSessions)
        assertEquals(60_000L, config.retention.maxAgeMillis)
        assertEquals(100, config.retention.maxEventsPerSession)
        assertEquals(50_000, trackLensConfig { liveBufferSize(Int.MAX_VALUE) }.liveBufferSize)
    }

    @Test
    fun sectionBlocksStartFromTheCurrentSettings() {
        val config =
            trackLensConfig {
                sessions { backgroundTimeout(2, TimeUnit.MINUTES) }
                sessions { autoStart(false) }
                validation { severity("FB_PARAM_VALUE_TOO_LONG", Severity.ERROR) }
                validation { disable("FB_RESERVED_PREFIX") }
            }

        assertEquals(120_000L, config.sessions.backgroundTimeoutMillis)
        assertFalse(config.sessions.autoStart)
        assertEquals(mapOf("FB_PARAM_VALUE_TOO_LONG" to Severity.ERROR), config.validation.severityOverrides)
        assertEquals(setOf("FB_RESERVED_PREFIX"), config.validation.disabledCodes)
    }

    @Test
    fun redactionDefaultsProtectCommonPersonalData() {
        val defaults = TrackLensConfig.DEFAULT.redaction

        assertTrue(defaults.patterns.containsAll(listOf(RedactionPattern.EMAIL, RedactionPattern.PHONE, RedactionPattern.CREDIT_CARD)))
        assertTrue("*email*" in defaults.keys)
        assertEquals(UserIdRedaction.HASH, defaults.userId)
        assertTrue(trackLensConfig { redaction { clear() } }.redaction.patterns.isEmpty())
    }

    @Test
    fun builtConfigsAreUnaffectedByLaterBuilderChanges() {
        val builder = TrackLensConfig.Builder().redaction { keys("first") }
        val first = builder.build()
        builder.redaction { keys("second") }

        assertFalse("second" in first.redaction.keys)
        assertEquals(first.redaction.keys + "second", builder.build().redaction.keys)
    }
}
