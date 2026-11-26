package io.github.tracklens.sample.sdk

import android.os.Bundle
import io.github.tracklens.AnalyticsDelegate
import io.github.tracklens.TrackLens

/**
 * The app's analytics, the way a host app integrates TrackLens: each SDK sits behind an [AnalyticsDelegate] wrapped
 * with [TrackLens.wrap]. In debug builds TrackLens records every call (tagged with the SDK's name) and forwards it;
 * in release builds tracklens-noop's `wrap` returns the delegate itself, so the SDKs are called directly.
 */
class SampleAnalytics(
    firebaseSdk: FakeFirebaseAnalytics = FakeFirebaseAnalytics(),
    amplitudeSdk: FakeAmplitude = FakeAmplitude(),
) {
    /** Firebase-style events: snake_case names, parameters as a Bundle in the SDK. */
    val firebase: AnalyticsDelegate = TrackLens.wrap(FIREBASE, FirebaseDelegate(firebaseSdk))

    /** Amplitude-style events: "Title Case" event types with a property map. */
    val amplitude: AnalyticsDelegate = TrackLens.wrap(AMPLITUDE, AmplitudeDelegate(amplitudeSdk))

    /** Tells TrackLens which screen is showing. */
    fun screen(name: String) = TrackLens.setScreen(name)

    companion object {
        const val FIREBASE = "firebase"
        const val AMPLITUDE = "amplitude"
    }
}

/** Adapts the Firebase-style SDK to [AnalyticsDelegate]. */
class FirebaseDelegate(
    private val sdk: FakeFirebaseAnalytics,
) : AnalyticsDelegate {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) = sdk.logEvent(name, params.toBundle())

    override fun setUserProperty(
        name: String,
        value: String?,
    ) = sdk.setUserProperty(name, value)

    override fun setUserId(userId: String?) = sdk.setUserId(userId)
}

/** Adapts the Amplitude-style SDK to [AnalyticsDelegate]. */
class AmplitudeDelegate(
    private val sdk: FakeAmplitude,
) : AnalyticsDelegate {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) = sdk.track(name, params)

    override fun setUserProperty(
        name: String,
        value: String?,
    ) = sdk.identify(mapOf(name to value))

    override fun setUserId(userId: String?) = sdk.setUserId(userId)
}

/** Firebase-style parameters: text, whole and decimal numbers, booleans, nested bundles and lists of bundles. */
internal fun Map<String, Any?>.toBundle(): Bundle =
    Bundle().also { bundle ->
        for ((key, value) in this) bundle.put(key, value)
    }

private fun Bundle.put(
    key: String,
    value: Any?,
) {
    when (value) {
        null -> putString(key, null)
        is String -> putString(key, value)
        is Int -> putLong(key, value.toLong())
        is Long -> putLong(key, value)
        is Double -> putDouble(key, value)
        is Float -> putDouble(key, value.toDouble())
        is Boolean -> putBoolean(key, value)
        is Map<*, *> -> putBundle(key, value.stringKeys().toBundle())
        is List<*> -> putParcelableArrayList(key, ArrayList(value.filterIsInstance<Map<*, *>>().map { it.stringKeys().toBundle() }))
        else -> putString(key, value.toString())
    }
}

private fun Map<*, *>.stringKeys(): Map<String, Any?> = entries.associate { (key, value) -> key.toString() to value }
