package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.InvObj
import osrsps.game.Player

/** The bottomless milk bucket's uses are the player's, so every bucket they own, banked ones too, shows them. */
class BottomlessMilkBucketTest {
    @Test
    fun `a banked bucket turns filled and back with the uses`() {
        val alice = Player(1, "alice", 3222, 3218, 0)
        alice.inventory[0] = InvObj(BottomlessMilkBucket.EMPTY, 1)
        alice.bank[0] = InvObj(BottomlessMilkBucket.EMPTY, 1)

        BottomlessMilkBucket.set(alice, BottomlessMilkBucket.TYPES[2], 5)
        assertEquals(BottomlessMilkBucket.FILLED, alice.inventory[0]?.id)
        assertEquals(BottomlessMilkBucket.FILLED, alice.bank[0]?.id)
        assertEquals(1, alice.bank[0]?.count)

        BottomlessMilkBucket.set(alice, null, 0)
        assertEquals(BottomlessMilkBucket.EMPTY, alice.inventory[0]?.id)
        assertEquals(BottomlessMilkBucket.EMPTY, alice.bank[0]?.id)
    }

    @Test
    fun `without the bank to close the gap a banked empty bucket still turns filled`() {
        val alice = Player(1, "alice", 3222, 3218, 0)
        alice.bank[0] = InvObj(BottomlessMilkBucket.FILLED, 1)
        alice.bank[1] = InvObj(BottomlessMilkBucket.EMPTY, 1)
        BottomlessMilkBucket.set(alice, BottomlessMilkBucket.TYPES[0], 3)
        assertEquals(listOf(BottomlessMilkBucket.FILLED, BottomlessMilkBucket.FILLED), listOf(alice.bank[0]?.id, alice.bank[1]?.id))
    }
}
