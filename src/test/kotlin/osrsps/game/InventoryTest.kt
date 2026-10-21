package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryTest {
    private val axe = ObjType(1351, "Bronze axe")
    private val coins = ObjType(995, "Coins", stackable = true)
    private val inv = Inventory(Inventory.INV, Inventory.INV_CAPACITY)

    @Test
    fun `items that don't stack take a slot each`() {
        assertEquals(3, inv.add(axe, 3))
        assertEquals(listOf(0 to InvObj(1351, 1), 1 to InvObj(1351, 1), 2 to InvObj(1351, 1)), inv.contents())
        assertEquals(25, inv.freeSlots)
        assertEquals(25, inv.add(axe, 30), "only 25 slots were left")
        assertEquals(0, inv.freeSlots)
        assertEquals(28L, inv.count(1351))
    }

    @Test
    fun `stackable items go on one stack, up to the largest the client shows`() {
        inv.add(axe, 1)
        assertEquals(100, inv.add(coins, 100))
        assertEquals(Int.MAX_VALUE - 100, inv.add(coins, Int.MAX_VALUE))
        assertEquals(0, inv.add(coins, 1))
        assertEquals(InvObj(995, Int.MAX_VALUE), inv[1])
        assertEquals(2, 28 - inv.freeSlots)
    }

    @Test
    fun `a full inventory still takes more of a stack it holds`() {
        inv.add(coins, 1)
        inv.add(axe, 27)
        assertEquals(0, inv.add(ObjType(1, "Other coins", stackable = true), 1))
        assertEquals(5, inv.add(coins, 5))
        assertEquals(InvObj(995, 6), inv[0])
    }

    @Test
    fun `remove, swap and clear`() {
        inv.add(coins, 10)
        inv.add(axe, 1)
        assertEquals(4, inv.remove(0, 4))
        assertEquals(InvObj(995, 6), inv[0])
        inv.swap(0, 5)
        assertNull(inv[0])
        assertEquals(InvObj(995, 6), inv[5])
        assertEquals(6, inv.remove(5))
        assertNull(inv[5])
        inv.clear()
        assertTrue(inv.isEmpty())
    }

    @Test
    fun `the first flush sends everything, then only changes`() {
        val fulls = mutableListOf<List<InvObj?>>()
        val partials = mutableListOf<List<Pair<Int, InvObj?>>>()
        fun flush() {
            inv.flush({ fulls.add(it) }, { partials.add(it) })
        }
        inv.add(axe, 1)
        flush()
        assertEquals(1, fulls.size)
        assertEquals(InvObj(1351, 1), fulls[0][0])
        assertEquals(28, fulls[0].size)
        flush()
        assertTrue(partials.isEmpty(), "nothing changed")
        inv.swap(0, 3)
        inv.add(coins, 2)
        flush()
        assertEquals(listOf(listOf<Pair<Int, InvObj?>>(0 to InvObj(995, 2), 3 to InvObj(1351, 1))), partials)
        inv.resend(7)
        flush()
        assertEquals<Pair<Int, InvObj?>>(7 to null, partials[1].single())
    }
}
