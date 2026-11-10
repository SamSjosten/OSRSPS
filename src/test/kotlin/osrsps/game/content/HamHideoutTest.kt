package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.skill.Pickpocketing
import osrsps.game.skill.SuccessRoll

class HamHideoutTest {
    @Test
    fun `the trapdoor's pick runs 4 to 98 percent, or 24 to 99 with a lockpick`() = with(HamHideout) {
        assertEquals(4, pickChance(1, withLockpick = false))
        assertEquals(98, pickChance(99, withLockpick = false))
        assertEquals(24, pickChance(1, withLockpick = true))
        assertEquals(99, pickChance(99, withLockpick = true))
        assertEquals(51, pickChance(50, withLockpick = false))
        // A drained level 0 is read as 1.
        assertEquals(4, pickChance(0, withLockpick = false))
    }

    @Test
    fun `each H,A,M, piece makes being thrown out 4 percent less likely`() = with(HamHideout) {
        assertEquals(100, ejectChance(0))
        assertEquals(96, ejectChance(1))
        assertEquals(72, ejectChance(7))
        assertEquals(72, ejectChance(9))
    }

    @Test
    fun `a member's pocket runs 53 to 94 percent, and the table fills its 1122 slots`() {
        val ham = Pickpocketing.POCKETS.single { Pickpocketing.HAM_MEMBERS.containsAll(it.npcs) }
        assertEquals(15, ham.level)
        assertEquals(222, ham.xpTenths)
        assertEquals(136, SuccessRoll.chance(ham.low, ham.high, 1), "53.1%")
        assertEquals(241, SuccessRoll.chance(ham.low, ham.high, 99), "94.1%")
        assertEquals(1 to 3, ham.damageMin to ham.damage)
        assertEquals(Pickpocketing.HAM_SLOTS, Pickpocketing.HAM_LOOT.sumOf { it.weight(1) })
        // The coin pouch, 17/102.
        assertEquals(17 * 11, Pickpocketing.HAM_LOOT.single { it.obj == Pickpocketing.HAM_POUCH }.weight(1))
        assertEquals(1..21, Pickpocketing.POUCH_COINS[Pickpocketing.HAM_POUCH])
    }
}
