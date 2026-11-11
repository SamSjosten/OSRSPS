package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemSetLoaderTest {
    /** An int-to-int enum as the cache stores it: opcode 6 with its (key, value) pairs. */
    private fun enum(vararg pairs: Pair<Int, Int>) = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeByte(1); writeByte('i'.code)
            writeByte(2); writeByte('o'.code)
            writeByte(6); writeShort(pairs.size)
            for ((key, value) in pairs) { writeInt(key); writeInt(value) }
            writeByte(0)
        }
    }.toByteArray()

    @Test
    fun `sets come from enum 1033 by index, each with its set at key -1 and pieces from key 0`() {
        val enums = mapOf(
            ItemSetLoader.SETS_ENUM to enum(0 to 2000, 1 to 2001),
            2000 to enum(-1 to 13024, 0 to 1163, 1 to 1127, 2 to 1079, 3 to 1201),
            2001 to enum(-1 to 13026, 0 to 1163, 1 to 1127, 2 to 1093, 3 to 1201),
        )
        val sets = ItemSetLoader.build { enums[it] }
        assertEquals(2, sets.size)
        assertEquals(listOf(1163, 1127, 1079, 1201), sets.ofSet(13024)?.pieces)
        assertEquals(1, sets.ofSet(13026)?.index)
        assertNull(sets.ofSet(1163))
    }

    @Test
    fun `no enum, no sets`() {
        assertEquals(0, ItemSetLoader.build { null }.size)
    }
}
