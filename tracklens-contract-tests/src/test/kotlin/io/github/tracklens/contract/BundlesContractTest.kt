package io.github.tracklens.contract

import android.os.Bundle
import io.github.tracklens.TrackLensBundles
import io.github.tracklens.toBundle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Release builds forward events to the analytics SDK through these helpers, so they must convert faithfully in
 * both artifacts: same keys, same values, same value types.
 */
@RunWith(RobolectricTestRunner::class)
class BundlesContractTest {
    @Test
    fun scalarsKeepTheirValuesAndTypes() {
        val params =
            mapOf<String, Any?>(
                "string" to "text",
                "int" to 7,
                "long" to 7L,
                "double" to 9.99,
                "float" to 1.5f,
                "boolean" to true,
                "short" to 3.toShort(),
                "byte" to 2.toByte(),
                "char" to 'c',
                "null" to null,
            )

        val bundle = params.toBundle()
        val roundTrip = TrackLensBundles.toMap(bundle)

        assertEquals(params, roundTrip)
        assertEquals(params.mapValues { it.value?.javaClass }, roundTrip.mapValues { it.value?.javaClass })
        assertEquals(7L, bundle.getLong("long"))
        assertTrue(bundle.containsKey("null"))
        assertNull(bundle.getString("null"))
    }

    @Test
    fun nestedMapsBecomeNestedBundles() {
        val bundle = TrackLensBundles.toBundle(mapOf("user" to mapOf("tier" to "gold", 42 to "non-string key")))

        val nested = requireNotNull(bundle.getBundle("user"))
        assertEquals("gold", nested.getString("tier"))
        assertEquals("non-string key", nested.getString("42"))
        assertEquals(mapOf("user" to mapOf("tier" to "gold", "42" to "non-string key")), TrackLensBundles.toMap(bundle))
    }

    @Test
    fun listsOfMapsBecomeBundleListsLikeFirebaseItems() {
        val items = listOf(mapOf("item_id" to "SKU-1", "price" to 9.99), mapOf("item_id" to "SKU-2", "price" to 5.0))

        val bundle = mapOf("items" to items).toBundle()

        @Suppress("DEPRECATION")
        val list = requireNotNull(bundle.getParcelableArrayList<Bundle>("items"))
        assertEquals(listOf("SKU-1", "SKU-2"), list.map { it.getString("item_id") })
        assertEquals(mapOf("items" to items), TrackLensBundles.toMap(bundle))
    }

    @Test
    fun typedListsAndArraysKeepTheirType() {
        val bundle =
            mapOf(
                "strings" to listOf("a", "b"),
                "ints" to listOf(1, 2),
                "intArray" to intArrayOf(1, 2),
                "longArray" to longArrayOf(3L),
                "stringArray" to arrayOf("x", "y"),
            ).toBundle()

        assertEquals(arrayListOf("a", "b"), bundle.getStringArrayList("strings"))
        assertEquals(arrayListOf(1, 2), bundle.getIntegerArrayList("ints"))
        assertArrayEquals(intArrayOf(1, 2), bundle.getIntArray("intArray"))
        assertArrayEquals(longArrayOf(3L), bundle.getLongArray("longArray"))
        assertEquals(arrayListOf("x", "y"), bundle.getStringArrayList("stringArray"))
    }

    @Test
    fun unsupportedValuesAreStoredAsText() {
        val value =
            object {
                override fun toString() = "custom value"
            }

        val bundle = mapOf("custom" to value, "mixed" to listOf(1, "two")).toBundle()

        assertEquals("custom value", bundle.getString("custom"))
        assertEquals("[1, two]", bundle.getString("mixed"))
    }

    @Test
    fun bundleArraysOfBundlesBecomeListsOfMaps() {
        val source =
            Bundle().apply {
                putParcelableArray("items", arrayOf(Bundle().apply { putString("item_id", "SKU-1") }))
                putBundle("nested", Bundle().apply { putInt("n", 1) })
            }

        assertEquals(
            mapOf("items" to listOf(mapOf("item_id" to "SKU-1")), "nested" to mapOf("n" to 1)),
            TrackLensBundles.toMap(source),
        )
    }

    @Test
    fun conversionsReturnNewObjects() {
        val params = mutableMapOf<String, Any?>("a" to 1)
        val bundle = params.toBundle()
        params["a"] = 2

        assertEquals(1, bundle.getInt("a"))
    }
}
