package io.github.tracklens.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallGateTest {
    private val main = "com.example.app"

    @Test
    fun activeInTheMainProcessOnASupportedApiLevel() {
        assertEquals(
            InstallDecision.ACTIVE,
            InstallGate.evaluate(sdkInt = 35, processName = main, mainProcessName = main, mainProcessOnly = true),
        )
    }

    @Test
    fun inactiveBelowTheMinimumApiLevel() {
        assertEquals(
            InstallDecision.UNSUPPORTED_SDK,
            InstallGate.evaluate(
                sdkInt = InstallGate.MIN_SUPPORTED_SDK - 1,
                processName = main,
                mainProcessName = main,
                mainProcessOnly = true,
            ),
        )
    }

    @Test
    fun inactiveInSecondaryProcessesUnlessAllowed() {
        val secondary = "$main:remote"

        assertEquals(
            InstallDecision.SECONDARY_PROCESS,
            InstallGate.evaluate(sdkInt = 35, processName = secondary, mainProcessName = main, mainProcessOnly = true),
        )
        assertEquals(
            InstallDecision.ACTIVE,
            InstallGate.evaluate(sdkInt = 35, processName = secondary, mainProcessName = main, mainProcessOnly = false),
        )
    }

    @Test
    fun unknownProcessNameIsTreatedAsMain() {
        assertEquals(
            InstallDecision.ACTIVE,
            InstallGate.evaluate(sdkInt = 35, processName = null, mainProcessName = main, mainProcessOnly = true),
        )
    }
}
