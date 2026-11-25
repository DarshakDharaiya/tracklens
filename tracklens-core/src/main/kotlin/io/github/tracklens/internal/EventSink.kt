package io.github.tracklens.internal

import android.os.Bundle

/**
 * Receives the logging calls that pass the kill switch. Task 13 replaces [NONE] with the event pipeline; tests
 * replace it to inject failures. Calls arrive through the pipeline's [CircuitBreaker].
 */
internal interface EventSink {
    fun log(
        name: String,
        params: Map<String, Any?>,
        source: String?,
    )

    fun log(
        name: String,
        params: Bundle,
        source: String?,
    )

    fun setUserProperty(
        name: String,
        value: String?,
        source: String?,
    )

    fun setUserId(
        userId: String?,
        source: String?,
    )

    fun setScreen(
        name: String,
        screenClass: String?,
    )

    /** Accepts and drops everything, until the pipeline exists. */
    object NONE : EventSink {
        override fun log(
            name: String,
            params: Map<String, Any?>,
            source: String?,
        ) = Unit

        override fun log(
            name: String,
            params: Bundle,
            source: String?,
        ) = Unit

        override fun setUserProperty(
            name: String,
            value: String?,
            source: String?,
        ) = Unit

        override fun setUserId(
            userId: String?,
            source: String?,
        ) = Unit

        override fun setScreen(
            name: String,
            screenClass: String?,
        ) = Unit
    }
}
