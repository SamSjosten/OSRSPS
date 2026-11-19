package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MisthalinOptionPackTest {
    @Test
    fun `the berry picked first keeps its deadline when the last one is picked`() {
        assertEquals(1, MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1199))
        assertEquals(150, MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1050))
    }

    @Test
    fun `a berry due back on the pick's own tick is back already`() {
        assertNull(MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1200))
        assertNull(MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1201))
    }

    @Test
    fun `with no timer running the first berry takes the full time`() {
        assertEquals(MisthalinOptionPack.BERRY_TICKS, MisthalinOptionPack.ticksUntilFirstBack(due = null, now = 1200))
    }
}
