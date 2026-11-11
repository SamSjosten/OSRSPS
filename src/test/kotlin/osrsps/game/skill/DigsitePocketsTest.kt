package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.combat.StatusEffects
import osrsps.game.content.Digsite
import osrsps.game.content.DigsitePeopleLines

/** Misthalin piece 7 step 9a's pockets against the OSRS Wiki (read 2026-11-11): the Digsite workmen and the teddy student. */
class DigsitePocketsTest {
    private fun pocketOf(npc: Int) = Pickpocketing.POCKETS.single { npc in it.npcs }

    @Test
    fun `a Digsite workman is level 25, 10,4 XP, 1 damage, a 4-second stun, on Steal-from, with the Wiki's table out of 11`() {
        val workman = pocketOf(Digsite.WORKMEN.first())
        assertEquals(Digsite.WORKMEN, workman.npcs.toList())
        assertEquals(listOf(25, 104, 1, 100, 240), listOf(workman.level, workman.xpTenths, workman.damage, workman.low, workman.high))
        assertEquals(Pickpocketing.STUN_4_SECONDS, workman.stunTicks)
        assertEquals(DigsitePeopleLines.CAUGHT, workman.caught)
        assertEquals(3, workman.op)
        assertEquals(null, workman.anyPouch)
        // Coins 4/11 (10 each), the specimen brush 3/11, rope, bucket, leather gloves and spade 1/11 each.
        assertEquals(11, workman.loot.sumOf { it.weight(1) })
        val coins = workman.loot.single { it.obj == Pickpocketing.COINS }
        assertEquals(listOf(4, 10, 10), listOf(coins.weight(1), coins.min, coins.max))
        assertEquals(3, workman.loot.single { it.obj == 670 }.weight(1))
        assertEquals(listOf(954, 1925, 1059, 952), workman.loot.filter { it.weight(1) == 1 }.map { it.obj })
    }

    @Test
    fun `the teddy student is level 25, no XP, 100 to 240, and always gives her teddy`() {
        val student = pocketOf(Digsite.STUDENT_TEDDY)
        assertEquals(listOf(25, 0, 100, 240), listOf(student.level, student.xpTenths, student.low, student.high))
        assertEquals(listOf(673), student.loot.map { it.obj })
        assertEquals(StatusEffects.PICKPOCKET_TICKS, student.stunTicks)
        // The Wiki's chart: 101/256 at 1, 241/256 at 99.
        assertEquals(listOf(101, 241), listOf(1, 99).map { SuccessRoll.chance(student.low, student.high, it) })
    }
}
