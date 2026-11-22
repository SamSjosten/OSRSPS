package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.content.Destination

/** The fairy ring table's rows read as codes, as the 242 cache's `fairyring` rows (dbtable 89) hold them. */
class FairyRingLoaderTest {
    private fun coord(x: Int, z: Int, level: Int) = (level shl 28) or (x shl 14) or z

    @Test
    fun `a row reads as its code, state, landing, log entry and flags`() {
        // fairyrings_dlr: id 112, multiloc_state 22, 0_34_48_37_27, "D L R", badger2, no_staff_return.
        val row = DbRow(
            FairyRingLoader.TABLE,
            mapOf(
                0 to listOf(112), 1 to listOf(22), 2 to listOf(coord(2213, 3099, 0)), 3 to listOf("D L R"),
                // The log's entry and its star as packed components; the line is a stand-in for the cache's words.
                4 to listOf((381 shl 16) or 100), 5 to listOf((381 shl 16) or 101), 8 to listOf("<br>line"),
                9 to listOf(1234), 11 to listOf(1),
            ),
        )
        val code = FairyRingLoader.code(row)!!
        assertEquals(112, code.id)
        assertEquals(22, code.state)
        assertEquals("DLR", code.code)
        assertEquals(Destination(2213, 3099, 0), code.destination)
        assertEquals(1234, code.apparition)
        assertEquals(true, code.noStaffReturn)
        assertEquals(100, code.textComponent)
        assertEquals(101, code.faveIconComponent)
        assertEquals("<br>line", code.description)
        assertEquals("D L R", code.spaced)
    }

    @Test
    fun `the unused codes and DIQ lead nowhere`() {
        // 1_1_1_1_1 (AIP and the other unused codes) and 0_0_0_0_0 (DIQ).
        assertNull(FairyRingLoader.destination(coord(65, 65, 1)))
        assertNull(FairyRingLoader.destination(0))
        assertEquals(Destination(3129, 3496, 0), FairyRingLoader.destination(coord(3129, 3496, 0)))
        val aip = FairyRingLoader.code(DbRow(FairyRingLoader.TABLE, mapOf(0 to listOf(0), 1 to listOf(0), 2 to listOf(coord(65, 65, 1)), 3 to listOf("A I P"))))!!
        assertNull(aip.destination)
        assertEquals(false, aip.noStaffReturn)
        assertNull(aip.apparition)
        assertEquals("", aip.description)
        assertEquals(-1, aip.textComponent)
    }
}
