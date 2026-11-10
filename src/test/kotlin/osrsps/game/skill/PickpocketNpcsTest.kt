package osrsps.game.skill

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/**
 * Thieving 2 step T1a against the OSRS Wiki (read 2026-11-10): the farmer's, Al Kharid warrior's and guard's numbers
 * and success charts, the farmer's table, the NPCs that joined existing pockets, and the gloves of silence's 5%.
 */
class PickpocketNpcsTest {
    private fun pocketOf(npc: Int) = Pickpocketing.POCKETS.single { npc in it.npcs }

    @Test
    fun `a farmer is level 10, 14,5 XP, 1 damage, 150 to 240, and gives a farmer's pouch of 9 coins or a potato seed`() {
        val farmer = pocketOf(Pickpocketing.FARMERS.first())
        assertTrue(Pickpocketing.FARMERS.all { it in farmer.npcs })
        assertEquals(listOf(10, 145, 1, 150, 240), listOf(farmer.level, farmer.xpTenths, farmer.damage, farmer.low, farmer.high))
        assertEquals("farmer", farmer.who)
        assertEquals(Pickpocketing.FARMER_POUCH, farmer.anyPouch)
        assertEquals(9..9, Pickpocketing.POUCH_COINS[Pickpocketing.FARMER_POUCH])
        // 123/128 the pouch, 5/128 a potato seed.
        assertEquals(128, farmer.loot.sumOf { it.weight(1) })
        assertEquals(123, farmer.loot.single { it.obj == Pickpocketing.FARMER_POUCH }.weight(1))
        assertEquals(5, farmer.loot.single { it.obj == Pickpocketing.POTATO_SEED }.weight(99))
        assertEquals(Pickpocketing.PICKPOCKET, farmer.op)
    }

    @Test
    fun `Al Kharid's warriors pickpocket as warrior women do, its man as a man, and Edgeville's guards as guards`() {
        val alKharid = pocketOf(Pickpocketing.AL_KHARID_WARRIOR)
        val woman = pocketOf(Pickpocketing.WARRIOR_WOMAN)
        assertEquals(
            listOf(woman.level, woman.xpTenths, woman.damage, woman.low, woman.high, woman.pouch, woman.coins),
            listOf(alKharid.level, alKharid.xpTenths, alKharid.damage, alKharid.low, alKharid.high, alKharid.pouch, alKharid.coins),
        )
        assertEquals(Pickpocketing.WARRIOR_POUCH, alKharid.pouch)
        assertEquals(pocketOf(3106), pocketOf(Pickpocketing.AL_KHARID_MAN))
        assertEquals(pocketOf(Pickpocketing.VARROCK_GUARDS.first()), pocketOf(Pickpocketing.EDGEVILLE_GUARD))
        // No NPC is in two pockets.
        val all = Pickpocketing.POCKETS.flatMap { it.npcs }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `the gloves of silence raise each chart to the Wiki's gloves line`() {
        fun gloves(value: Int) = value * PickpocketGear.GLOVES_PERCENT / 100
        // The Wiki's charts: farmer 150/240 to 157/252, Al Kharid warrior 100/240 to 105/252, guard 50/240 to 52/252.
        assertEquals(listOf(157, 252), listOf(gloves(150), gloves(240)))
        assertEquals(105, gloves(100))
        assertEquals(52, gloves(50))
        assertEquals(62, PickpocketGear.GLOVES_FAILURES)
        assertEquals(10, PickpocketGear.NECKLACE_CHARGES)
    }

    @Test
    fun `ten thousand seeded tries land on each new pocket's chart`() {
        val random = GameRandom(2026)
        for ((npc, level) in listOf(Pickpocketing.FARMERS.first() to 10, Pickpocketing.FARMERS.first() to 60, Pickpocketing.AL_KHARID_WARRIOR to 25, Pickpocketing.EDGEVILLE_GUARD to 40)) {
            val pocket = pocketOf(npc)
            for (withGloves in listOf(false, true)) {
                val low = if (withGloves) pocket.low * PickpocketGear.GLOVES_PERCENT / 100 else pocket.low
                val high = if (withGloves) pocket.high * PickpocketGear.GLOVES_PERCENT / 100 else pocket.high
                val p = SuccessRoll.chance(low, high, level) / 256.0
                val wins = (1..TRIES).count { SuccessRoll.roll(random, low, high, level) }
                assertTrue(abs(wins - TRIES * p) <= 5 * sqrt(TRIES * p * (1 - p)), "$npc at $level (gloves $withGloves): $wins of $TRIES, expected ${TRIES * p}")
            }
        }
        // The farmer's chart: (1 + 150)/256 at 1, 241/256 at 99.
        val farmer = pocketOf(Pickpocketing.FARMERS.first())
        assertEquals(151, SuccessRoll.chance(farmer.low, farmer.high, 1))
        assertEquals(241, SuccessRoll.chance(farmer.low, farmer.high, 99))
    }

    private companion object {
        const val TRIES = 10_000
    }
}
