package io.github.tracklens.internal

/**
 * Receives the logging calls that pass the kill switch, with parameters already normalized on the caller's thread.
 * Task 13 replaces [NONE] with the event pipeline; tests replace it to inject failures. Calls arrive through the
 * pipeline's [CircuitBreaker].
 */
internal interface EventSink {
    fun event(
        name: String,
        params: NormalizedParams,
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
        override fun event(
            name: String,
            params: NormalizedParams,
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
