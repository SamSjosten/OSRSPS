package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals

class WildernessTest {
    @Test
    fun `level 1 starts past the ditch and rises every 8 tiles, to 56 in the far north`() {
        assertEquals(0, Wilderness.level(3100, 3519), "south of the ditch")
        assertEquals(1, Wilderness.level(3100, 3520))
        assertEquals(1, Wilderness.level(3100, 3527))
        assertEquals(2, Wilderness.level(3100, 3528))
        assertEquals(20, Wilderness.level(3100, 3679))
        assertEquals(21, Wilderness.level(3100, 3680))
        assertEquals(23, Wilderness.level(3100, 3700))
        assertEquals(56, Wilderness.level(3100, 3967))
        assertEquals(0, Wilderness.level(3100, 3968))
    }

    @Test
    fun `only between the Wilderness's west and east edges, and the same band underground`() {
        assertEquals(0, Wilderness.level(2943, 3600))
        assertEquals(11, Wilderness.level(2944, 3600))
        assertEquals(11, Wilderness.level(3391, 3600))
        assertEquals(0, Wilderness.level(3392, 3600))
        assertEquals(11, Wilderness.level(3100, 10000), "the dungeons, 6,400 tiles up")
        assertEquals(0, Wilderness.level(3100, 9919))
        assertEquals(0, Wilderness.level(3222, 3218), "Lumbridge")
    }
}
