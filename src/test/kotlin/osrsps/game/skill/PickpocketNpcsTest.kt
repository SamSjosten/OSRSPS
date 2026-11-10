package osrsps.game.skill

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/**
 * Thieving 2 step T1a against the OSRS Wiki (read 2026-11-10): the farmer's, Al Kharid warrior's and guard's numbers
 * and success charts, the farmer's table, the NPCs that joined existing pockets, and the gloves of silence's 5%; and step
 * T1b's rogues, Knights of Ardougne, watchmen, paladins, gnomes and heroes.
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

    @Test
    fun `Kandarin's and the Wilderness's pockets have the Wiki's levels, XP, charts, damage, pouches and stuns`() {
        // npc, level, XP tenths, low, high, damage, pouch, coins, stun ticks
        val rows = listOf(
            listOf(Pickpocketing.ROGUES.first(), 32, 365, 75, 240, 2, Pickpocketing.ROGUE_POUCH, 25, 40, 9),
            listOf(Pickpocketing.KNIGHTS_OF_ARDOUGNE.first(), 55, 843, 50, 240, 3, Pickpocketing.KNIGHT_POUCH, 50, 50, 8),
            listOf(Pickpocketing.WATCHMEN.first(), 65, 1375, 15, 160, 3, Pickpocketing.WATCHMAN_POUCH, 60, 60, 9),
            listOf(Pickpocketing.PALADINS.first(), 70, 1318, 40, 170, 3, Pickpocketing.PALADIN_POUCH, 80, 80, 8),
            listOf(Pickpocketing.GNOMES.first(), 75, 1333, 43, 175, 1, Pickpocketing.GNOME_POUCH, 300, 300, 9),
            listOf(Pickpocketing.HEROES.first(), 80, 1633, 39, 160, 3, Pickpocketing.HERO_POUCH, 200, 300, 10),
        )
        for (row in rows) {
            val pocket = pocketOf(row[0])
            assertEquals(row.subList(1, 7), listOf(pocket.level, pocket.xpTenths, pocket.low, pocket.high, pocket.damage, pocket.anyPouch), pocket.who)
            assertEquals(row[7]..row[8], Pickpocketing.POUCH_COINS[pocket.anyPouch], pocket.who)
            assertEquals(row[9], pocket.stunTicks, pocket.who)
            assertEquals(Pickpocketing.PICKPOCKET, pocket.op)
        }
        // Every version on the Wiki's pages shares its pocket: the knights, paladins and heroes, the gnome men, women and
        // children, and the 2022 warriors with the warrior women.
        for (group in listOf(Pickpocketing.KNIGHTS_OF_ARDOUGNE, Pickpocketing.PALADINS, Pickpocketing.HEROES, Pickpocketing.GNOMES)) {
            assertEquals(1, group.map { pocketOf(it) }.toSet().size)
        }
        assertTrue(Pickpocketing.WARRIORS_2022.all { pocketOf(it) == pocketOf(Pickpocketing.WARRIOR_WOMAN) })
        val all = Pickpocketing.POCKETS.flatMap { it.npcs }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `the rogue's, gnome's and hero's tables add up to the Wiki's, and paladins and watchmen give their extra`() {
        fun weights(npc: Int) = pocketOf(npc).loot.associate { it.obj to it.weight(1) }
        assertEquals(144, weights(Pickpocketing.ROGUES.first()).values.sum())
        assertEquals(123, weights(Pickpocketing.ROGUES.first())[Pickpocketing.ROGUE_POUCH])
        assertEquals(128, weights(Pickpocketing.GNOMES.first()).values.sum())
        assertEquals(30, weights(Pickpocketing.GNOMES.first())[Pickpocketing.GNOME_POUCH])
        assertEquals(128, weights(Pickpocketing.HEROES.first()).values.sum())
        assertEquals(105, weights(Pickpocketing.HEROES.first())[Pickpocketing.HERO_POUCH])
        // Air runes come 8 at a time, death runes 2, arrow shafts 2 to 4.
        val rogueAir = pocketOf(Pickpocketing.ROGUES.first()).loot.single { it.obj == AIR_RUNE }
        assertEquals(8..8, rogueAir.min..rogueAir.max)
        val shafts = pocketOf(Pickpocketing.GNOMES.first()).loot.single { it.obj == ARROW_SHAFT }
        assertEquals(2..4, shafts.min..shafts.max)
        val paladin = pocketOf(Pickpocketing.PALADINS.first()).extra.single()
        assertEquals(listOf(CHAOS_RUNE, 2), listOf(paladin.obj, paladin.min))
        val watchman = pocketOf(Pickpocketing.WATCHMEN.first()).extra.single()
        assertEquals(listOf(BREAD, 1), listOf(watchman.obj, watchman.min))
        assertTrue(pocketOf(Pickpocketing.HEROES.first()).extra.isEmpty())
    }

    private companion object {
        const val TRIES = 10_000
        const val AIR_RUNE = 556
        const val ARROW_SHAFT = 52
        const val CHAOS_RUNE = 562
        const val BREAD = 2309
    }
}
