package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals

class RickTest {
    @Test
    fun `he sells no more than the coins pay for or the backpack holds`() {
        assertEquals(2, Rick.sellable(5, 160_000, 20))
        assertEquals(3, Rick.sellable(3, 300_000, 20))
        assertEquals(0, Rick.sellable(1, 74_999, 20))
        // Coins left over keep their slot.
        assertEquals(1, Rick.sellable(5, 300_000, 1))
    }

    @Test
    fun `paying with every coin frees the coins' slot for one more crystal`() {
        assertEquals(1, Rick.sellable(1, 75_000, 0))
        assertEquals(2, Rick.sellable(2, 150_000, 1))
        assertEquals(0, Rick.sellable(1, 150_000, 0))
    }
}
