package io.github.tracklens.benchmark

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tracklens.Subscription
import io.github.tracklens.TrackLens
import io.github.tracklens.TrackLensBundles
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/** A typical e-commerce event: 10 parameters of the common types. */
val TEN_PARAMS: Map<String, Any?> =
    mapOf(
        "item_id" to "SKU-1042",
        "item_name" to "Trail running shoes",
        "item_category" to "footwear",
        "price" to 89.99,
        "quantity" to 2L,
        "currency" to "USD",
        "coupon" to "SPRING25",
        "in_stock" to true,
        "index" to 3,
        "value" to 179.98,
    )

val TEN_PARAM_BUNDLE = TrackLensBundles.toBundle(TEN_PARAMS)

/** Installs TrackLens once in the benchmark process (later calls are ignored by TrackLens itself). */
fun installTrackLens() {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    TrackLens.install(app)
    TrackLens.isEnabled = true
}

/**
 * Counts events delivered by the pipeline, through the public listener API, so a benchmark can wait until the
 * pipeline has caught up. If the queue ever dropped a call, [drain] can never catch up and fails, so numbers
 * from the cheap "queue full" path can't pass as real measurements.
 */
class DeliveryCounter {
    private val delivered = AtomicLong()
    private var subscription: Subscription? = null

    /** Calls logged by the benchmark so far. */
    var logged = 0L

    fun start() {
        subscription = TrackLens.addListener({ delivered.incrementAndGet() }, Executor { it.run() })
    }

    fun stop() {
        subscription?.cancel()
    }

    /** Waits until every logged call has been delivered. */
    fun drain(timeoutMillis: Long = 30_000) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (delivered.get() < logged) {
            check(System.nanoTime() < deadline) {
                "Pipeline did not deliver all events: logged=$logged delivered=${delivered.get()}. Events were dropped " +
                    "(queue over its cap) or the pipeline stalled, so this measurement is not valid."
            }
            Thread.yield()
        }
    }

    companion object {
        /** Well below the pipeline's 10,000-call cap, so measured calls always take the normal path. */
        const val DRAIN_EVERY = 1_000L
    }
}
