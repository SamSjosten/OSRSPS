package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.WearPos

/** Varrock armour's second ore and bar: only worn, only up to its tier's level, only at the Edgeville furnace, about 1 in 10. */
class VarrockArmourTest {
    private fun wearing(piece: Int?) = Player(1, "alice", 3222, 3218, 0, VarTypes.NONE).apply {
        if (piece != null) equipment[WearPos.BODY] = InvObj(piece, 1)
    }

    private fun count(tries: Int, roll: (GameRandom) -> Boolean): Int {
        val random = GameRandom(5)
        return (1..tries).count { roll(random) }
    }

    @Test
    fun `no armour, no second ore`() {
        assertEquals(0, count(1_000) { VarrockArmour.doubleOre(wearing(null), it, 15) })
    }

    @Test
    fun `each tier doubles ore up to its level`() {
        val (one, two, three, four) = VarrockArmour.PIECES
        assertEquals(0, count(1_000) { VarrockArmour.doubleOre(wearing(one), it, 55) }, "mithril on armour 1")
        assertTrue(count(1_000) { VarrockArmour.doubleOre(wearing(one), it, 40) } in 60..140, "gold on armour 1, about 1 in 10")
        assertTrue(count(1_000) { VarrockArmour.doubleOre(wearing(two), it, 65) } > 0, "lovakite on armour 2")
        assertEquals(0, count(1_000) { VarrockArmour.doubleOre(wearing(two), it, 70) }, "adamantite on armour 2")
        assertTrue(count(1_000) { VarrockArmour.doubleOre(wearing(three), it, 70) } > 0)
        assertEquals(0, count(1_000) { VarrockArmour.doubleOre(wearing(three), it, 85) }, "runite on armour 3")
        assertTrue(count(1_000) { VarrockArmour.doubleOre(wearing(four), it, 85) } > 0)
    }

    @Test
    fun `bars double at the Edgeville furnace only, up to the tier's bar`() {
        val (one, _, _, four) = VarrockArmour.PIECES
        assertTrue(count(1_000) { VarrockArmour.doubleBar(wearing(one), it, 30, 3109, 3498) } > 0, "steel at Edgeville")
        assertEquals(0, count(1_000) { VarrockArmour.doubleBar(wearing(one), it, 40, 3109, 3498) }, "gold on armour 1")
        assertEquals(0, count(1_000) { VarrockArmour.doubleBar(wearing(four), it, 85, 3226, 3256) }, "Lumbridge's furnace")
        assertTrue(count(1_000) { VarrockArmour.doubleBar(wearing(four), it, 85, 3109, 3498) } > 0, "runite on armour 4")
    }
}
