package osrsps.game.skill.hunter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.skill.SuccessRoll

/** Hunter step 3's data against the Wiki: levels, XP, the catch chances its pages give, and where the net goes. */
class NetTrapsTest {
    private fun creature(kind: NetTrapKind) = kind.creatures.single()

    @Test
    fun `levels and XP are each creature page's`() {
        assertEquals(listOf(29 to 1520, 47 to 2240, 59 to 2720), NetTraps.KINDS.map { creature(it).level to creature(it).xpTenths })
        assertEquals(listOf(29, 47, 59), NetTraps.KINDS.map { it.level })
    }

    @Test
    fun `salamanders are caught at the chances their pages give`() {
        // "Approximately 55% beginning at level 29, and 100% after level 66."
        val swamp = creature(NetTraps.SWAMP)
        assertTrue(SuccessRoll.chance(swamp.low, swamp.high, 29) * 100 / 256 in 54..56)
        assertEquals(256, SuccessRoll.chance(swamp.low, swamp.high, 67))
        // "Approximately 56% beginning at level 47, and 100% after level 88."
        val orange = creature(NetTraps.ORANGE)
        assertTrue(SuccessRoll.chance(orange.low, orange.high, 47) * 100 / 256 in 55..57)
        assertEquals(256, SuccessRoll.chance(orange.low, orange.high, 89))
        // "Approximately 56% beginning at level 59, and 94% at level 99."
        val red = creature(NetTraps.RED)
        assertTrue(SuccessRoll.chance(red.low, red.high, 59) * 100 / 256 in 55..57)
        assertTrue(SuccessRoll.chance(red.low, red.high, 99) * 100 / 256 in 93..94)
    }

    @Test
    fun `the net goes square to a side of its tree, and every colour owns its trees and nets`() {
        assertEquals(listOf(0, 1, 2, 3), listOf(NetTrapKind.sideOf(0, 1), NetTrapKind.sideOf(1, 0), NetTrapKind.sideOf(0, -1), NetTrapKind.sideOf(-1, 0)))
        assertNull(NetTrapKind.sideOf(1, 1))
        assertNull(NetTrapKind.sideOf(0, 2))
        for (kind in NetTraps.KINDS) {
            val c = creature(kind)
            assertTrue(listOf(kind.treeSet, kind.set, kind.treeReset, kind.broken, kind.failing, c.catching, c.full).all(kind::owns))
            assertTrue(!kind.laidFromItem)
        }
        assertEquals(21, NetTraps.KINDS.flatMap { k -> listOf(k.treeSet, k.set, k.treeReset, k.broken, k.failing, creature(k).catching, creature(k).full) }.toSet().size)
    }
}
