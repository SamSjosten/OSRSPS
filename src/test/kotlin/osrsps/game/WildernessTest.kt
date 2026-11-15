package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The level sums follow the 242 client's proc `wilderness_level` (osrs-dumps 2026-11-11-rev242), case by case. */
class WildernessTest {
    @Test
    fun `level 1 starts at the ditch and rises every 8 tiles, to 56 in the far north`() {
        assertEquals(0, Wilderness.level(3100, 3519), "south of the ditch")
        assertEquals(1, Wilderness.level(3100, 3520))
        assertEquals(1, Wilderness.level(3100, 3527))
        assertEquals(2, Wilderness.level(3100, 3528))
        assertEquals(20, Wilderness.level(3100, 3679))
        assertEquals(21, Wilderness.level(3100, 3680))
        assertEquals(23, Wilderness.level(3100, 3700))
        assertEquals(56, Wilderness.level(3100, 3967))
        // The client's box runs to the top of map square 67, where nothing walkable is, so its sum carries on.
        assertEquals(57, Wilderness.level(3100, 3968))
        assertEquals(0, Wilderness.level(3100, 4352))
    }

    @Test
    fun `only between the Wilderness's west and east edges, and the caves 6,400 tiles up`() {
        assertEquals(0, Wilderness.level(2943, 3600))
        assertEquals(11, Wilderness.level(2944, 3600))
        assertEquals(11, Wilderness.level(3391, 3600))
        assertEquals(0, Wilderness.level(3392, 3600))
        assertEquals(11, Wilderness.level(3100, 10000), "the caves")
        assertEquals(11, Wilderness.level(3440, 10000), "the caves reach a map square further east")
        assertEquals(0, Wilderness.level(3100, 9919))
        assertEquals(0, Wilderness.level(3222, 3218), "Lumbridge")
    }

    @Test
    fun `the client's special cases`() {
        assertEquals(5, Wilderness.level(3400, 4000), "the far north-east box")
        assertEquals(0, Wilderness.level(3420, 4060), "the hole in it")
        assertEquals(24, Wilderness.level(3040, 10120), "one square shifted two levels down")
        assertEquals(35, Wilderness.level(3300, 10200))
        assertEquals(35, Wilderness.level(3420, 10200))
        assertEquals(40, Wilderness.level(3350, 10330))
        assertEquals(21, Wilderness.level(1750, 11550))
        assertEquals(21, Wilderness.level(1880, 11550))
        assertEquals(29, Wilderness.level(1630, 11550))
        assertEquals(33, Wilderness.level(3350, 10240 + 6), "the slope's south end")
        assertEquals(40, Wilderness.level(3350, 10240 + 56), "and its north end")
        assertEquals(33, Wilderness.level(3350, 10240), "south of 6 the script's division rounds to nothing")
    }

    @Test
    fun `inside starts north of the ditch, though the level starts on it`() {
        assertFalse(Wilderness.inside(3090, 3520), "the ditch's south side")
        assertFalse(Wilderness.inside(3090, 3522), "the ditch itself")
        assertTrue(Wilderness.inside(3090, 3523))
        assertTrue(Wilderness.inside(3090, 3700))
        assertTrue(Wilderness.inside(3100, 10000))
        assertFalse(Wilderness.inside(3222, 3218))
    }
}
