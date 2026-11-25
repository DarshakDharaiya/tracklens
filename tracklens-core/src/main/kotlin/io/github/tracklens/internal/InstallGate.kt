package io.github.tracklens.internal

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process

/** Whether TrackLens may run in this process, decided once by `TrackLens.install`. */
internal enum class InstallDecision(
    val explanation: String,
) {
    ACTIVE("TrackLens is active."),
    UNSUPPORTED_SDK(
        "TrackLens is inactive: Android API ${Build.VERSION.SDK_INT} is below ${InstallGate.MIN_SUPPORTED_SDK}, the minimum of the " +
            "debug artifact. Logging calls are ignored; wrapped analytics calls are still forwarded.",
    ),
    SECONDARY_PROCESS(
        "TrackLens is inactive in this secondary process (TrackLensConfig.mainProcessOnly). Logging calls are ignored; wrapped " +
            "analytics calls are still forwarded.",
    ),
}

internal object InstallGate {
    /**
     * Must equal `android-minSdk` in gradle/libs.versions.toml (the debug artifacts' minSdk, also asserted by
     * `verifyReleaseAarFloors`). Hosts with a lower minSdk can override the library minSdk in debug builds, so
     * TrackLens checks at runtime instead of crashing on missing APIs.
     */
    const val MIN_SUPPORTED_SDK = 23

    /** Pure decision, unit-tested; [processName] is `null` when it can't be determined (then treated as main). */
    fun evaluate(
        sdkInt: Int,
        processName: String?,
        mainProcessName: String,
        mainProcessOnly: Boolean,
    ): InstallDecision =
        when {
            sdkInt < MIN_SUPPORTED_SDK -> InstallDecision.UNSUPPORTED_SDK
            mainProcessOnly && processName != null && processName != mainProcessName -> InstallDecision.SECONDARY_PROCESS
            else -> InstallDecision.ACTIVE
        }

    fun evaluate(
        application: Application,
        mainProcessOnly: Boolean,
    ): InstallDecision =
        evaluate(
            sdkInt = Build.VERSION.SDK_INT,
            processName = currentProcessName(application),
            mainProcessName = application.applicationInfo.processName ?: application.packageName,
            mainProcessOnly = mainProcessOnly,
        )

    fun isDebuggable(application: Application): Boolean = (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Unknown process name means "assume main".
    private fun currentProcessName(application: Application): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                activityManager?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
            }
        } catch (_: Throwable) {
            null
        }
}
