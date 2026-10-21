package osrsps.game.loc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import osrsps.game.zone.ZoneLoc

class StaticLocsTest {
    @Test
    fun `packs and finds locs by tile and layer`() {
        val locs = StaticLocs()
        val wall = ZoneLoc(62_533, 0, 3, 3215, 3223, 2)
        val decoration = ZoneLoc(5, 4, 1, 3215, 3223, 2)
        val ground = ZoneLoc(StaticLocs.MAX_ID, 22, 2, 3215, 3223, 2)
        val neighbour = ZoneLoc(7, 10, 0, 3216, 3223, 2)
        listOf(wall, decoration, ground, neighbour).forEach(locs::add)
        // Lots more in the same zone, so its array grows.
        repeat(20) { locs.add(ZoneLoc(9, 10, 0, 3208 + it % 8, 3216 + it / 8, 2)) }
        locs.trim()
        assertEquals(listOf(wall, decoration, ground), locs.at(3215, 3223, 2))
        assertEquals(decoration, locs.at(3215, 3223, 2, 1))
        assertNull(locs.at(3215, 3223, 2, 2))
        assertEquals(listOf(neighbour), locs.at(3216, 3223, 2))
        assertEquals(emptyList(), locs.at(3215, 3223, 1))
        assertEquals(24, locs.size)
    }

    @Test
    fun `a later loc on the same layer is the one the tile shows`() {
        val locs = StaticLocs()
        locs.add(ZoneLoc(1, 10, 0, 3200, 3200, 0))
        locs.add(ZoneLoc(2, 11, 0, 3200, 3200, 0))
        assertEquals(2, locs.at(3200, 3200, 0, 2)?.id)
    }

    @Test
    fun `refuses ids too big to pack`() {
        assertFailsWith<IllegalArgumentException> { StaticLocs().add(ZoneLoc(StaticLocs.MAX_ID + 1, 10, 0, 0, 0, 0)) }
    }
}
