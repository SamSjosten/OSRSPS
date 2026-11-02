package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/** Edgeville step 10: which tiles the dungeon's trapdoor, wall and north gate work at, and where the wall puts a player. */
class EdgevilleDungeonTest {
    private val wall = ZoneLoc(EdgevilleDungeon.WALL_LEFT, LocShape.WALL_STRAIGHT, 0, 3094, 9895, 0)

    @Test
    fun `the wall carries a player across from either side and nobody from anywhere else`() {
        assertEquals(Tile(3093, 9895), EdgevilleDungeon.acrossWall(wall, 3094, 9895))
        assertEquals(Tile(3094, 9895), EdgevilleDungeon.acrossWall(wall, 3093, 9895))
        assertNull(EdgevilleDungeon.acrossWall(wall, 3095, 9895))
        assertNull(EdgevilleDungeon.acrossWall(wall, 3093, 9896))
    }

    @Test
    fun `the trapdoor, the wall and the north gate work only where the map has them`() {
        assertTrue(EdgevilleDungeon.isTrapdoor(ZoneLoc(EdgevilleDungeon.TRAPDOOR_OPEN, LocShape.GROUND_DECOR, 2, 3097, 3468, 0)))
        assertFalse(EdgevilleDungeon.isTrapdoor(ZoneLoc(EdgevilleDungeon.TRAPDOOR_OPEN, LocShape.GROUND_DECOR, 2, 3097, 3468, 1)))
        assertTrue(EdgevilleDungeon.isWall(wall))
        assertFalse(EdgevilleDungeon.isWall(wall.copy(x = 3000)))
        assertTrue(EdgevilleDungeon.isNorthGate(ZoneLoc(EdgevilleDungeon.NORTH_GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3132, 9917, 0)))
        assertFalse(EdgevilleDungeon.isNorthGate(ZoneLoc(EdgevilleDungeon.NORTH_GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3132, 9944, 0)))
        // The metal gates swing in the middle, not in the Wilderness's part (3105, 9944) or the Taverley Dungeon.
        assertTrue(EdgevilleDungeon.inMiddle(ZoneLoc(EdgevilleDungeon.GATE_LEFT, LocShape.WALL_STRAIGHT, 2, 3145, 9871, 0)))
        assertFalse(EdgevilleDungeon.inMiddle(ZoneLoc(EdgevilleDungeon.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3105, 9944, 0)))
        assertFalse(EdgevilleDungeon.inMiddle(ZoneLoc(EdgevilleDungeon.GATE_LEFT, LocShape.WALL_STRAIGHT, 0, 2910, 9910, 0)))
    }
}
