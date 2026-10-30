package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** Varrock's straight stairs, each way round, on staircases as the 241 cache places them. */
class VarrockTest {
    private fun stairs(id: Int, angle: Int, x: Int, z: Int, level: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, angle, x, z, level)

    private val top = Varrock.STRAIGHT_STAIRS_TOP

    @Test
    fun `stairs climbing north (angle 0) go up past the north end and down before the south end`() {
        // 3188, 3389 with its top a tile north, as in the house south-west of the square.
        assertEquals(Destination(3188, 3392, 1), Varrock.straightStairs(stairs(11796, 0, 3188, 3389, 0), up = true, 3188, 3388))
        assertEquals(Destination(3189, 3388, 0), Varrock.straightStairs(stairs(top, 0, 3188, 3390, 1), up = false, 3189, 3392))
    }

    @Test
    fun `stairs climbing east (angle 1) go up past the east end and down before the west end`() {
        // 3227, 3393 with its top a tile east; Draynor's Morgan's stairs work the same way.
        assertEquals(Destination(3230, 3394, 1), Varrock.straightStairs(stairs(11796, 1, 3227, 3393, 0), up = true, 3226, 3394))
        assertEquals(Destination(3226, 3393, 0), Varrock.straightStairs(stairs(top, 1, 3228, 3393, 1), up = false, 3230, 3393))
        assertEquals(Destination(3102, 3266, 1), Varrock.straightStairs(stairs(11796, 1, 3099, 3266, 0), up = true, 3098, 3266))
    }

    @Test
    fun `stairs climbing south (angle 2) go up past the south end and down before the north end`() {
        // 3230, 3383 by the south gate, its top on the same corner.
        assertEquals(Destination(3230, 3382, 1), Varrock.straightStairs(stairs(11797, 2, 3230, 3383, 0), up = true, 3230, 3386))
        assertEquals(Destination(3231, 3386, 0), Varrock.straightStairs(stairs(top, 2, 3230, 3383, 1), up = false, 3231, 3382))
    }

    @Test
    fun `stairs climbing west (angle 3) go up past the west end and down before the east end`() {
        // 3156, 3435, its top on the same corner.
        assertEquals(Destination(3155, 3435, 1), Varrock.straightStairs(stairs(11797, 3, 3156, 3435, 0), up = true, 3159, 3435))
        assertEquals(Destination(3159, 3436, 0), Varrock.straightStairs(stairs(top, 3, 3156, 3435, 1), up = false, 3155, 3436))
    }

    @Test
    fun `a player beside the stairs keeps to the stairs' two columns`() {
        assertEquals(Destination(3189, 3392, 1), Varrock.straightStairs(stairs(11796, 0, 3188, 3389, 0), up = true, 3195, 3388))
        assertEquals(Destination(3188, 3392, 1), Varrock.straightStairs(stairs(11796, 0, 3188, 3389, 0), up = true, 3180, 3388))
    }
}
