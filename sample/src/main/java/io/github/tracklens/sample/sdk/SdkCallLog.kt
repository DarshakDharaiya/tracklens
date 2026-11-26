package io.github.tracklens.sample.sdk

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What the fake analytics SDKs received, in every build type: logcat (tags `FakeFirebase`, `FakeAmplitude`) plus the
 * newest calls for the sample's "SDK calls" list. This is how a release build shows the SDKs still work without
 * TrackLens.
 */
object SdkCallLog {
    /** One call as the SDK saw it. */
    data class Call(
        val sdk: String,
        val description: String,
    )

    private val recentCalls = MutableStateFlow<List<Call>>(emptyList())
    private val totals = MutableStateFlow(0L)

    /** The newest calls, newest first, at most [KEPT]. */
    val recent: StateFlow<List<Call>> get() = recentCalls

    /** Calls received since the app started. */
    val total: StateFlow<Long> get() = totals

    fun record(
        sdk: String,
        description: String,
    ) {
        Log.i(sdk, description)
        totals.update { it + 1 }
        recentCalls.update { (listOf(Call(sdk, description)) + it).take(KEPT) }
    }

    /** For tests. */
    fun clear() {
        recentCalls.value = emptyList()
        totals.value = 0
    }

    private const val KEPT = 30
}
