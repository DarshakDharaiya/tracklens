package io.github.tracklens.sample.sdk

import android.os.Bundle

/**
 * Stand-in for a Firebase-style analytics SDK: events take a [Bundle], plus user properties and a user id. It only
 * writes to [SdkCallLog]; nothing leaves the device.
 */
class FakeFirebaseAnalytics {
    fun logEvent(
        name: String,
        params: Bundle?,
    ) = SdkCallLog.record(TAG, "logEvent $name ${params?.describe() ?: "{}"}")

    fun setUserProperty(
        name: String,
        value: String?,
    ) = SdkCallLog.record(TAG, "setUserProperty $name=$value")

    fun setUserId(id: String?) = SdkCallLog.record(TAG, "setUserId $id")

    companion object {
        const val TAG = "FakeFirebase"
    }
}

/** Stand-in for an Amplitude-style SDK: events are "tracked" with a property map; users are identified. */
class FakeAmplitude {
    fun track(
        eventType: String,
        eventProperties: Map<String, Any?> = emptyMap(),
    ) = SdkCallLog.record(TAG, "track \"$eventType\" $eventProperties")

    fun identify(userProperties: Map<String, Any?>) = SdkCallLog.record(TAG, "identify $userProperties")

    fun setUserId(userId: String?) = SdkCallLog.record(TAG, "setUserId $userId")

    companion object {
        const val TAG = "FakeAmplitude"
    }
}

/** `{key=value, …}` of a bundle, including nested bundles and bundle lists (Firebase's "items"). */
internal fun Bundle.describe(): String =
    keySet().sorted().joinToString(prefix = "{", postfix = "}") { key ->
        @Suppress("DEPRECATION") // Bundle.get(key) is the only untyped accessor; fine for a debug description.
        val value = get(key)
        val text =
            when (value) {
                is Bundle -> value.describe()
                is ArrayList<*> -> value.joinToString(prefix = "[", postfix = "]") { (it as? Bundle)?.describe() ?: it.toString() }
                else -> value.toString()
            }
        "$key=$text"
    }
