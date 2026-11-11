package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Thieving 2 step T3: East Ardougne's six stalls against the OSRS Wiki's stall pages (read 2026-11-10). */
class ArdougneStallsTest {
    private fun stall(loc: Int) = Thieving.STALLS.getValue(loc)

    @Test
    fun `each stall has the Wiki's level, XP and respawn`() {
        fun row(s: Stall) = listOf(s.level, s.xpTenths, s.respawnTicks)
        with(Thieving) {
            assertEquals(listOf(5, 160, 4), row(stall(BAKERY_STALL)))
            assertEquals(listOf(20, 240, 8), row(stall(SILK_STALL)))
            assertEquals(listOf(35, 450, 12), row(stall(FUR_STALL)))
            assertEquals(listOf(50, 2050, 32), row(stall(SILVER_STALL)))
            assertEquals(listOf(65, 920, 10), row(stall(SPICE_STALL)))
            assertEquals(listOf(75, 4080, 100), row(stall(GEM_STALL)))
            assertTrue(ARDOUGNE_STALLS.all { it.empty == EMPTY_STALL && it.refuseTicks == STALL_REFUSE_TICKS })
        }
    }

    @Test
    fun `the tables are the Wiki's`() {
        with(Thieving) {
            assertEquals(listOf(13, 5, 2), BAKERY_LOOT.map { it.weight })
            assertEquals(listOf(16, 3, 1), SILVER_LOOT.map { it.weight })
            assertEquals(listOf(105, 17, 5, 1), GEM_LOOT.map { it.weight })
            assertEquals(128, GEM_LOOT.sumOf { it.weight })
            assertEquals(listOf(950, 958, 2007), listOf(SILK_LOOT, FUR_LOOT, SPICE_LOOT).map { it.single().obj })
        }
    }

    @Test
    fun `both bakers keep the bakery stall, and the dearer stalls have more watchers`() {
        with(Thieving) {
            assertEquals(setOf(ARDOUGNE_BAKER, ARDOUGNE_BAKER_2), stall(BAKERY_STALL).owners)
            assertEquals(setOf(Pickpocketing.ARDOUGNE_GUARD), stall(BAKERY_STALL).guards)
            assertTrue(Pickpocketing.KNIGHTS_OF_ARDOUGNE.all { it in stall(SILK_STALL).guards && it in stall(FUR_STALL).guards })
            for (dear in listOf(SILVER_STALL, SPICE_STALL, GEM_STALL)) assertTrue(Pickpocketing.PALADINS.all { it in stall(dear).guards })
            assertTrue(Pickpocketing.PALADINS.none { it in stall(FUR_STALL).guards })
        }
    }

    @Test
    fun `the market's guards are pickpocketed as guards`() {
        // The H.A.M. storerooms' guards are a "guard" pocket of their own; the market's join Varrock's and Edgeville's.
        val pocket = Pickpocketing.POCKETS.single { Pickpocketing.EDGEVILLE_GUARD in it.npcs }
        assertTrue(Pickpocketing.ARDOUGNE_GUARD in pocket.npcs)
    }
}
