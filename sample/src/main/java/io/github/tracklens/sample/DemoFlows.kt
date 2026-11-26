package io.github.tracklens.sample

import io.github.tracklens.sample.sdk.SampleAnalytics

/**
 * The demo flows behind the sample's buttons. Each one is what a real app would log for that user journey, so
 * TrackLens has realistic traffic to show: screens, e-commerce events with items, sign-in, an Amplitude event,
 * sensitive values (for redaction) and events that break Firebase rules (for validation, Task 38+).
 */
class DemoFlows(
    private val analytics: SampleAnalytics,
) {
    /** The flows, by the id used on buttons and with `adb shell am start … --es flow <id>`. */
    enum class Flow(
        val id: String,
        val title: String,
    ) {
        BROWSE("browse", "Browse products"),
        ADD_TO_CART("add_to_cart", "Add to cart"),
        CHECKOUT("checkout", "Checkout"),
        SIGN_IN("sign_in", "Sign in"),
        SIGN_OUT("sign_out", "Sign out"),
        PLAY_SONG("play_song", "Play a song (Amplitude)"),
        SENSITIVE("sensitive", "Send sensitive data"),
        INVALID("invalid", "Send invalid events"),
        BURST("burst", "Fire 1,000 events"),
        ;

        companion object {
            fun byId(id: String?): Flow? = entries.firstOrNull { it.id == id }
        }
    }

    fun run(flow: Flow) =
        when (flow) {
            Flow.BROWSE -> browse()
            Flow.ADD_TO_CART -> addToCart()
            Flow.CHECKOUT -> checkout()
            Flow.SIGN_IN -> signIn()
            Flow.SIGN_OUT -> signOut()
            Flow.PLAY_SONG -> playSong()
            Flow.SENSITIVE -> sensitive()
            Flow.INVALID -> invalid()
            Flow.BURST -> burst()
        }

    fun browse() {
        analytics.screen("Home")
        analytics.firebase.logEvent("view_item_list", mapOf("item_list_id" to "featured", "items" to listOf(SHOES, SOCKS)))
        analytics.screen("Product")
        analytics.firebase.logEvent("view_item", mapOf("currency" to "EUR", "value" to SHOES_PRICE, "items" to listOf(SHOES)))
    }

    fun addToCart() {
        analytics.screen("Product")
        analytics.firebase.logEvent("add_to_cart", mapOf("currency" to "EUR", "value" to SHOES_PRICE, "items" to listOf(SHOES)))
    }

    fun checkout() {
        analytics.screen("Cart")
        val cart = mapOf("currency" to "EUR", "value" to CART_VALUE, "items" to listOf(SHOES, SOCKS))
        analytics.firebase.logEvent("begin_checkout", cart)
        analytics.screen("Checkout")
        analytics.firebase.logEvent("add_shipping_info", cart + ("shipping_tier" to "standard"))
        analytics.firebase.logEvent("add_payment_info", cart + ("payment_type" to "card"))
        analytics.screen("Confirmation")
        analytics.firebase.logEvent("purchase", cart + mapOf("transaction_id" to "T-1001", "tax" to TAX, "shipping" to SHIPPING))
    }

    fun signIn() {
        analytics.screen("Sign in")
        analytics.firebase.logEvent("login", mapOf("method" to "email"))
        analytics.firebase.setUserId("user-42")
        analytics.firebase.setUserProperty("plan", "pro")
        analytics.amplitude.setUserId("user-42")
        analytics.amplitude.setUserProperty("plan", "pro")
    }

    fun signOut() {
        analytics.firebase.logEvent("logout", emptyMap())
        analytics.firebase.setUserId(null)
        analytics.amplitude.setUserId(null)
    }

    fun playSong() {
        analytics.screen("Player")
        analytics.amplitude.logEvent("Song Played", mapOf("title" to "Blue in Green", "duration_s" to SONG_SECONDS, "shuffle" to false))
    }

    /** Values TrackLens' default redaction masks in what it stores; the SDKs still receive them unchanged. */
    fun sensitive() {
        analytics.screen("Profile")
        analytics.firebase.logEvent(
            "profile_updated",
            mapOf(
                "email" to "jane.doe@example.com",
                "phone" to "+31 6 1234 5678",
                "note" to "card 4111 1111 1111 1111 on file",
                "password" to "hunter2",
            ),
        )
    }

    /** Events that break Firebase's rules; TrackLens flags them once validation arrives (Task 38). */
    fun invalid() {
        analytics.firebase.logEvent("firebase_reserved_prefix", emptyMap())
        analytics.firebase.logEvent("this_event_name_is_far_too_long_for_firebase_analytics", emptyMap())
        analytics.firebase.logEvent("begin-checkout", mapOf("value" to "12.50"))
        analytics.firebase.logEvent("too_many_params", (1..TOO_MANY_PARAMS).associate { "param_$it" to it.toLong() })
    }

    /** A burst, as a busy screen or a loop bug produces; run it off the main thread. */
    fun burst(count: Int = BURST_SIZE) {
        repeat(count) { analytics.firebase.logEvent("burst_event", mapOf("index" to it.toLong())) }
    }

    private companion object {
        val SHOES = mapOf("item_id" to "SKU-SHOES", "item_name" to "Running shoes", "price" to 89.95, "quantity" to 1L)
        val SOCKS = mapOf("item_id" to "SKU-SOCKS", "item_name" to "Sport socks", "price" to 9.95, "quantity" to 2L)
        const val SHOES_PRICE = 89.95
        const val CART_VALUE = 109.85
        const val TAX = 19.07
        const val SHIPPING = 4.95
        const val SONG_SECONDS = 337L
        const val TOO_MANY_PARAMS = 30
        const val BURST_SIZE = 1_000
    }
}
