package io.github.tracklens.internal

import android.graphics.Rect
import android.os.Bundle
import android.util.Size
import android.util.SparseArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class ParamNormalizerTest {
    @Before
    fun clearDiagnostics() = Diagnostics.clear()

    @Test
    fun scalarsKeepTheirValueAndType() {
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
                "bigDecimal" to BigDecimal("19.99"),
                "bigInteger" to BigInteger.TEN,
                "null" to null,
            )

        val normalized = ParamNormalizer.normalize(params)

        assertEquals(params, normalized.values)
        assertEquals(params.mapValues { it.value?.javaClass }, normalized.values.mapValues { it.value?.javaClass })
        assertFalse(normalized.truncated)
    }

    @Test
    fun keepsTheCallersKeyOrder() {
        val keys = listOf("z", "a", "m", "b")

        assertEquals(
            keys,
            ParamNormalizer
                .normalize(keys.associateWith { 1 })
                .values.keys
                .toList(),
        )
    }

    @Test
    @Suppress("LongMethod") // One line per Bundle value type, on purpose.
    fun everyBundleValueTypeIsNormalized() {
        val item = Bundle().apply { putString("item_id", "SKU-1") }
        val bundle =
            Bundle().apply {
                putString("string", "text")
                putString("nullString", null)
                putCharSequence("charSequence", StringBuilder("built"))
                putInt("int", 1)
                putLong("long", 2L)
                putDouble("double", 3.5)
                putFloat("float", 4.5f)
                putBoolean("boolean", true)
                putShort("short", 5)
                putByte("byte", 6)
                putChar("char", 'x')
                putIntArray("intArray", intArrayOf(1, 2))
                putLongArray("longArray", longArrayOf(3L))
                putDoubleArray("doubleArray", doubleArrayOf(1.5))
                putFloatArray("floatArray", floatArrayOf(2.5f))
                putBooleanArray("booleanArray", booleanArrayOf(true, false))
                putShortArray("shortArray", shortArrayOf(7))
                putByteArray("byteArray", byteArrayOf(8))
                putCharArray("charArray", charArrayOf('a', 'b'))
                putStringArray("stringArray", arrayOf("a", null))
                putCharSequenceArray("charSequenceArray", arrayOf<CharSequence>(StringBuilder("cs")))
                putIntegerArrayList("integerArrayList", arrayListOf(9, 10))
                putStringArrayList("stringArrayList", arrayListOf("s"))
                putCharSequenceArrayList("charSequenceArrayList", arrayListOf<CharSequence>("c"))
                putParcelableArray("itemsArray", arrayOf(item))
                putParcelableArrayList("itemsList", arrayListOf(item))
                putBundle("bundle", Bundle().apply { putInt("n", 1) })
                putParcelable("parcelable", Rect(1, 2, 3, 4))
                putSerializable("serializable", Date(0))
                putSize("size", Size(16, 9))
                putSparseParcelableArray("sparse", SparseArray<Rect>())
            }

        val values = ParamNormalizer.normalize(bundle).values

        val expected =
            mapOf<String, Any?>(
                "string" to "text",
                "nullString" to null,
                "charSequence" to "built",
                "int" to 1,
                "long" to 2L,
                "double" to 3.5,
                "float" to 4.5f,
                "boolean" to true,
                "short" to 5.toShort(),
                "byte" to 6.toByte(),
                "char" to 'x',
                "intArray" to listOf(1, 2),
                "longArray" to listOf(3L),
                "doubleArray" to listOf(1.5),
                "floatArray" to listOf(2.5f),
                "booleanArray" to listOf(true, false),
                "shortArray" to listOf(7.toShort()),
                "byteArray" to listOf(8.toByte()),
                "charArray" to listOf('a', 'b'),
                "stringArray" to listOf("a", null),
                "charSequenceArray" to listOf("cs"),
                "integerArrayList" to listOf(9, 10),
                "stringArrayList" to listOf("s"),
                "charSequenceArrayList" to listOf("c"),
                "itemsArray" to listOf(mapOf("item_id" to "SKU-1")),
                "itemsList" to listOf(mapOf("item_id" to "SKU-1")),
                "bundle" to mapOf("n" to 1),
            )
        expected.forEach { (key, value) -> assertEquals("value of $key", value, values[key]) }
        for (key in listOf("parcelable", "serializable", "size", "sparse")) {
            assertTrue("$key becomes an UnknownValue", values[key] is UnknownValue)
        }
        assertEquals(Rect::class.java.name, (values["parcelable"] as UnknownValue).className)
        assertEquals(expected.size + 4, values.size)
    }

    @Test
    fun theSnapshotIsIndependentOfTheCallersObjects() {
        val list = mutableListOf("a")
        val nested = mutableMapOf<String, Any?>("k" to "v")
        val params = mutableMapOf<String, Any?>("list" to list, "nested" to nested, "array" to intArrayOf(1))
        val bundle = Bundle().apply { putString("s", "before") }

        val fromMap = ParamNormalizer.normalize(params)
        val fromBundle = ParamNormalizer.normalize(bundle)
        list += "b"
        nested["k"] = "changed"
        params["added"] = true
        (params["array"] as IntArray)[0] = 99
        bundle.putString("s", "after")

        assertEquals(mapOf("list" to listOf("a"), "nested" to mapOf("k" to "v"), "array" to listOf(1)), fromMap.values)
        assertEquals(mapOf("s" to "before"), fromBundle.values)
    }

    @Test
    fun parameterCountIsCapped() {
        val normalized = ParamNormalizer.normalize((1..500).associate { "k$it" to it })

        assertEquals(ParamNormalizer.MAX_PARAMS, normalized.values.size)
        assertEquals("k1", normalized.values.keys.first())
        assertTrue(normalized.truncated)
    }

    @Test
    fun collectionSizeIsCapped() {
        val normalized = ParamNormalizer.normalize(mapOf("items" to List(1_000) { it }, "map" to (1..1_000).associate { "k$it" to it }))

        assertEquals(ParamNormalizer.MAX_COLLECTION_SIZE, (normalized.values["items"] as List<*>).size)
        assertEquals(ParamNormalizer.MAX_COLLECTION_SIZE, (normalized.values["map"] as Map<*, *>).size)
        assertTrue(normalized.truncated)
    }

    @Test
    fun stringsKeysAndTextAreCapped() {
        val long = "x".repeat(5_000)
        val normalized =
            ParamNormalizer.normalize(
                mapOf(
                    "value" to long,
                    long to "long key",
                    "object" to
                        object {
                            override fun toString() = long
                        },
                ),
            )

        assertEquals(ParamNormalizer.MAX_STRING_LENGTH, (normalized.values["value"] as String).length)
        assertTrue(normalized.values.keys.any { it.length == ParamNormalizer.MAX_STRING_LENGTH })
        assertEquals(ParamNormalizer.MAX_STRING_LENGTH, (normalized.values["object"] as UnknownValue).text.length)
        assertTrue(normalized.truncated)
    }

    @Test
    fun nestingDeeperThanTheLimitIsReplaced() {
        // Three container levels below the top-level parameters are kept; the fourth is replaced.
        val deep = mapOf("l1" to mapOf("l2" to mapOf("l3" to mapOf("l4" to mapOf("l5" to "x")))))

        val normalized = ParamNormalizer.normalize(deep)

        assertEquals(mapOf("l1" to mapOf("l2" to mapOf("l3" to mapOf("l4" to ParamNormalizer.TOO_DEEP)))), normalized.values)
        assertTrue(normalized.truncated)
    }

    @Test
    fun selfReferencingValuesTerminate() {
        val cyclicList = mutableListOf<Any?>().also { it.add(it) }
        val cyclicMap = HashMap<String, Any?>().also { it["self"] = it }

        val normalized = ParamNormalizer.normalize(mapOf("list" to cyclicList, "map" to cyclicMap))

        assertEquals(listOf(listOf(listOf(ParamNormalizer.TOO_DEEP))), normalized.values["list"])
        assertEquals(mapOf("self" to mapOf("self" to mapOf("self" to ParamNormalizer.TOO_DEEP))), normalized.values["map"])
    }

    @Test
    fun unknownObjectsKeepTheirTextAndClass() {
        val throwing =
            object {
                override fun toString(): String = error("broken toString")
            }
        val normalized =
            ParamNormalizer.normalize(
                mapOf("enum" to Thread.State.RUNNABLE, "atomic" to AtomicInteger(5), "throwing" to throwing, 42 to "nonStringKey").mapKeys {
                    it.key.toString()
                },
            )

        assertEquals(UnknownValue(Thread.State::class.java.name, "RUNNABLE"), normalized.values["enum"])
        assertEquals(UnknownValue(AtomicInteger::class.java.name, "5"), normalized.values["atomic"])
        val unknown = normalized.values["throwing"] as UnknownValue
        assertEquals("<toString() of ${throwing.javaClass.name} threw>", unknown.text)
        assertEquals("nonStringKey", normalized.values["42"])
    }

    @Test
    fun nestedNonStringKeysBecomeText() {
        val normalized = ParamNormalizer.normalize(mapOf("nested" to mapOf(1 to "one", null to "none")))

        assertEquals(mapOf("1" to "one", "null" to "none"), normalized.values["nested"])
    }

    @Test
    fun aMapChangedOnceDuringTheCopyIsCopiedOnRetry() {
        val flaky = ConcurrentlyModifiedMap(failures = 1)

        assertEquals(mapOf("k" to "v"), ParamNormalizer.normalize(flaky).values)
        assertTrue(Diagnostics.snapshot().isEmpty())
    }

    @Test
    fun aMapThatKeepsChangingIsRecordedWithoutParams() {
        val normalized = ParamNormalizer.normalize(ConcurrentlyModifiedMap(failures = Int.MAX_VALUE))

        assertEquals(emptyMap<String, Any?>(), normalized.values)
        assertTrue(normalized.truncated)
        assertEquals(DiagnosticLevel.WARNING, Diagnostics.snapshot().single().level)
    }

    @Test
    fun emptyInputReturnsTheSharedEmptyInstance() {
        assertSame(NormalizedParams.EMPTY, ParamNormalizer.normalize(emptyMap()))
        assertSame(NormalizedParams.EMPTY, ParamNormalizer.normalize(Bundle()))
    }

    /** Throws ConcurrentModificationException from its first [failures] iterations, like a map edited on another thread. */
    private class ConcurrentlyModifiedMap(
        private var failures: Int,
    ) : AbstractMap<String, Any?>() {
        override val entries: Set<Map.Entry<String, Any?>>
            get() =
                object : AbstractSet<Map.Entry<String, Any?>>() {
                    override val size = 1

                    override fun iterator(): Iterator<Map.Entry<String, Any?>> {
                        if (failures-- > 0) throw ConcurrentModificationException()
                        return mapOf<String, Any?>("k" to "v").entries.iterator()
                    }
                }
    }
}
