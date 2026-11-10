package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.map.CollisionMap

/** Bob Barter's decanting worked out on a backpack, with the families found from item names as the cache has them. */
class DecantingTest {
    private val drink = listOf("Drink", null, null, null, "Drop")
    private val types = buildList {
        for (dose in 1..4) {
            val id = ENERGY[dose - 1]
            add(ObjType(id, "Super energy($dose)", iops = drink, tradeable = true, certlink = id + 1))
            add(ObjType(id + 1, "Super energy($dose)", stackable = true, tradeable = true, certlink = id, certtemplate = 799))
            add(ObjType(RESTORE[dose - 1], "Super restore($dose)", iops = drink, tradeable = true))
        }
        add(ObjType(Decanting.VIAL, "Vial", tradeable = true, certlink = Decanting.VIAL_NOTE))
        add(ObjType(Decanting.VIAL_NOTE, "Vial", stackable = true, tradeable = true, certlink = Decanting.VIAL, certtemplate = 799))
        add(ObjType(Decanting.COINS, "Coins", stackable = true, tradeable = true))
        // Named like a potion but no Drink option, and a family missing a dose: neither decants.
        add(ObjType(RING_4, "Ring of dueling(4)", tradeable = true))
        add(ObjType(BARB_2, "Attack mix(2)", iops = drink, tradeable = true))
    }
    private val decanting = Decanting(World(IntArray(0), CollisionMap(), objTypes = ObjTypes.of(*types.toTypedArray())))

    private fun backpack(vararg objs: InvObj) = List(28) { objs.getOrNull(it) }

    private fun counts(slots: List<InvObj?>) = slots.filterNotNull().groupBy { it.id }.mapValues { (_, it) -> it.sumOf { obj -> obj.count } }

    @Test
    fun `families are the drinkable items with all four doses, notes included`() {
        assertEquals(PotionFamily("super energy", ENERGY, ENERGY.map { it + 1 }), decanting.families.getValue(ENERGY[0]).first)
        assertEquals(2, decanting.families.getValue(ENERGY[2] + 1).second)
        assertEquals(listOf(-1, -1, -1, -1), decanting.families.getValue(RESTORE[0]).first.notes)
        assertNull(decanting.families[RING_4])
        assertNull(decanting.families[BARB_2])
    }

    @Test
    fun `four 3-dose potions decant into three 4-dose, giving a vial back`() {
        val result = decanting.decant(backpack(*Array(4) { InvObj(ENERGY[2], 1) }), 4, 3)
        assertEquals(DecantOutcome.ALL_DONE, result.outcome)
        assertEquals(mapOf(ENERGY[3] to 3, Decanting.VIAL to 1), counts(result.inventory))
    }

    @Test
    fun `a 4-dose into 1-dose buys three vials at the price, and odd doses leave one short potion`() {
        val result = decanting.decant(backpack(InvObj(ENERGY[3], 1), InvObj(Decanting.COINS, 100)), 1, 3)
        assertEquals(DecantOutcome.ALL_DONE, result.outcome)
        assertEquals(3, result.bought)
        assertEquals(mapOf(ENERGY[0] to 4, Decanting.COINS to 91), counts(result.inventory))
        val odd = decanting.decant(backpack(InvObj(ENERGY[2], 1), InvObj(ENERGY[1], 1), InvObj(Decanting.VIAL, 1)), 2, 3)
        // 5 doses: two 2-dose and a 1-dose, the three vials used.
        assertEquals(mapOf(ENERGY[1] to 2, ENERGY[0] to 1), counts(odd.inventory))
    }

    @Test
    fun `noted potions come back as notes, with the spare vials`() {
        val result = decanting.decant(backpack(InvObj(ENERGY[1] + 1, 5)), 4, 3)
        assertEquals(DecantOutcome.ALL_DONE, result.outcome)
        assertEquals(mapOf(ENERGY[3] + 1 to 2, ENERGY[1] + 1 to 1, Decanting.VIAL_NOTE to 2), counts(result.inventory))
    }

    @Test
    fun `results that won't fit unnoted come back noted`() {
        // 27 1-dose and a 4-dose in a full backpack: 31 doses into 1-dose is 31 potions.
        val full = List(28) { if (it < 27) InvObj(ENERGY[0], 1) else InvObj(ENERGY[3], 1) }
        val result = decanting.decant(full, 1, 3)
        // No coins for the three vials: nothing is done.
        assertEquals(DecantOutcome.SHORT, result.outcome)
        assertEquals(full, result.inventory)
        val toTwo = decanting.decant(List(28) { InvObj(ENERGY[0], 1) }, 2, 3)
        assertEquals(mapOf(ENERGY[1] to 14, Decanting.VIAL to 14), counts(toTwo.inventory))
        val overflow = decanting.decant(List(27) { InvObj(ENERGY[3], 1) } + InvObj(Decanting.COINS, 1000), 1, 3)
        assertEquals(DecantOutcome.ALL_DONE, overflow.outcome)
        assertEquals(mapOf(ENERGY[0] + 1 to 108, Decanting.COINS to 1000 - 81 * 3), counts(overflow.inventory))
    }

    @Test
    fun `short of cash part way decants what the vials cover, and nothing decantable says so`() {
        val result = decanting.decant(
            backpack(InvObj(ENERGY[3], 1), InvObj(RESTORE[3], 1), InvObj(RESTORE[3], 1), InvObj(Decanting.COINS, 9)), 2, 3,
        )
        // Super energy takes 1 more vial (3 coins); super restore 2 more: 6 coins left buys both.
        assertEquals(DecantOutcome.ALL_DONE, result.outcome)
        val tight = decanting.decant(backpack(InvObj(ENERGY[3], 1), InvObj(RESTORE[3], 1), InvObj(Decanting.COINS, 3)), 2, 3)
        assertEquals(DecantOutcome.DONE_WHAT_I_CAN, tight.outcome)
        assertEquals(mapOf(ENERGY[1] to 2, RESTORE[3] to 1), counts(tight.inventory))
        assertEquals(DecantOutcome.NOTHING, decanting.decant(backpack(InvObj(RING_4, 1)), 4, 3).outcome)
    }

    private companion object {
        val ENERGY = listOf(3022, 3020, 3018, 3016)
        val RESTORE = listOf(3030, 3028, 3026, 3024)
        const val RING_4 = 2552
        const val BARB_2 = 11429
    }
}
