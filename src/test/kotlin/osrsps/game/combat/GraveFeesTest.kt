package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.SettingsSide
import osrsps.game.VarTypes
import osrsps.game.VarbitType

class GraveFeesTest {
    private val types = VarTypes(6_000, mapOf(SettingsSide.VARBIT_IRONMAN to VarbitType(499, 3, 5)))

    private fun player(ironman: Int = 0, clears: Int = 0) = Player(1, "alice", 3222, 3218, 0, types).also {
        it.vars.setVarbit(SettingsSide.VARBIT_IRONMAN, ironman)
        it.vars[GraveFees.VARP_ARCHIVE_CLEARS] = clears
    }

    /** A tile of Ocellus's arena in the cache's map, 64,88 on level 1, and one in the lobby, 64,86. */
    private val archive = GraveFees.coord(4128, 5637, 1)
    private val lobby = GraveFees.coord(4136, 5551, 0)
    private val lumbridge = GraveFees.coord(3222, 3218, 0)

    @Test
    fun `a unit is free under 100,000, then 1,000, 10,000 and 100,000 a unit`() {
        assertEquals(listOf(0L, 1_000L, 1_000L, 10_000L, 100_000L), listOf(99_999L, 100_000L, 999_999L, 1_000_000L, 10_000_000L).map(GraveFees::unitFee))
    }

    @Test
    fun `the total is capped at 500,000, an Ironman pays half, and a raid death under 5 clears a quarter`() {
        val units = listOf(20_000_000L to 6, 150_000L to 2)
        assertEquals(500_000L, GraveFees.total(player(), units, lumbridge, merged = false))
        assertEquals(102_000L, GraveFees.total(player(), listOf(20_000_000L to 1, 150_000L to 2), lumbridge, merged = false))
        assertEquals(51_000L, GraveFees.total(player(ironman = 1), listOf(20_000_000L to 1, 150_000L to 2), lumbridge, merged = false))
        // The quarter's cap is a quarter too: 125,000.
        assertEquals(125_000L, GraveFees.total(player(clears = 4), units, archive, merged = false))
        assertEquals(25_500L, GraveFees.total(player(clears = 4), listOf(20_000_000L to 1, 150_000L to 2), archive, merged = false))
        // Half, then a quarter of that.
        assertEquals(12_750L, GraveFees.total(player(ironman = 3, clears = 0), listOf(20_000_000L to 1, 150_000L to 2), archive, merged = false))
        // No quarter from the fifth clear on, for a merged grave, or for a death in the lobby.
        assertEquals(102_000L, GraveFees.total(player(clears = 5), listOf(20_000_000L to 1, 150_000L to 2), archive, merged = false))
        assertEquals(102_000L, GraveFees.total(player(), listOf(20_000_000L to 1, 150_000L to 2), archive, merged = true))
        assertEquals(102_000L, GraveFees.total(player(), listOf(20_000_000L to 1, 150_000L to 2), lobby, merged = false))
    }

    @Test
    fun `the raid's squares are script10182's`() {
        for ((x, z, level) in listOf(Triple(63, 86, 0), Triple(65, 88, 3), Triple(64, 87, 3), Triple(64, 88, 2))) {
            assertTrue(GraveFees.inArchive(GraveFees.coord(x shl 6, z shl 6, level)), "$x,$z level $level")
        }
        for ((x, z, level) in listOf(Triple(64, 86, 0), Triple(64, 88, 3), Triple(64, 90, 2), Triple(62, 87, 0), Triple(63, 89, 0))) {
            assertFalse(GraveFees.inArchive(GraveFees.coord(x shl 6, z shl 6, level)), "$x,$z level $level")
        }
        assertFalse(GraveFees.inArchive(-1))
    }
}
