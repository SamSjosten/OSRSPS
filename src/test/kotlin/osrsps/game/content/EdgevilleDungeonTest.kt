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
    fun `the wall carries a player across from either side, wherever the route reaches it, and nobody from further off`() {
        assertEquals(Tile(3093, 9895), EdgevilleDungeon.acrossWall(wall, 3094, 9895))
        assertEquals(Tile(3094, 9895), EdgevilleDungeon.acrossWall(wall, 3093, 9895))
        // Beside the other half or just north or south of this one, the route has reached the wall too.
        assertEquals(Tile(3093, 9895), EdgevilleDungeon.acrossWall(wall, 3094, 9896))
        assertEquals(Tile(3093, 9895), EdgevilleDungeon.acrossWall(wall, 3094, 9894))
        assertEquals(Tile(3094, 9895), EdgevilleDungeon.acrossWall(wall, 3093, 9896))
        assertNull(EdgevilleDungeon.acrossWall(wall, 3095, 9895))
        assertNull(EdgevilleDungeon.acrossWall(wall, 3094, 9897))
    }

    @Test
    fun `the brass key takes a player through the shed's door from either side, and nobody from further off`() {
        val door = ZoneLoc(EdgevilleDungeon.SHED_DOOR, LocShape.WALL_STRAIGHT, 3, 3115, 3450, 0)
        assertTrue(EdgevilleDungeon.isShedDoor(door))
        assertEquals(Tile(3115, 3450), EdgevilleDungeon.throughShedDoor(door, 3115, 3449))
        assertEquals(Tile(3115, 3450), EdgevilleDungeon.throughShedDoor(door, 3114, 3449))
        assertEquals(Tile(3115, 3449), EdgevilleDungeon.throughShedDoor(door, 3115, 3450))
        assertEquals(Tile(3115, 3449), EdgevilleDungeon.throughShedDoor(door, 3116, 3450))
        assertNull(EdgevilleDungeon.throughShedDoor(door, 3115, 3451))
        assertNull(EdgevilleDungeon.throughShedDoor(door, 3117, 3449))
        assertFalse(EdgevilleDungeon.isShedLadder(ZoneLoc(EdgevilleDungeon.SHED_LADDER, LocShape.CENTREPIECE_STRAIGHT, 3, 2566, 3227, 0)))
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

    @Test
    fun `the trapdoors that go somewhere are Edgeville's and Paterdomus's, on the ground only`() {
        fun trapdoor(x: Int, z: Int, level: Int = 0) = ZoneLoc(EdgevilleDungeon.TRAPDOOR_OPEN, LocShape.GROUND_DECOR, 2, x, z, level)
        assertEquals(EdgevilleDungeon.LANDING, EdgevilleDungeon.landing(trapdoor(3097, 3468)))
        assertEquals(Paterdomus.TRAPDOOR_LANDING, EdgevilleDungeon.landing(trapdoor(3405, 3507)))
        assertEquals(null, EdgevilleDungeon.landing(trapdoor(3405, 3507, 1)))
        assertEquals(null, EdgevilleDungeon.landing(trapdoor(3200, 3200)))
    }

    @Test
    fun `a door with no open type takes the player across its wall, whichever side they stand`() {
        val west = ZoneLoc(Paterdomus.DOOR_LEFT, LocShape.WALL_STRAIGHT, 2, 3408, 3489, 0)
        assertEquals(Tile(3409, 3489), Paterdomus.across(west, 3408, 3489))
        assertEquals(Tile(3408, 3489), Paterdomus.across(west, 3409, 3489))
        val south = ZoneLoc(Paterdomus.GATE_WEST, LocShape.WALL_STRAIGHT, 3, 3405, 9895, 0)
        assertEquals(Tile(3405, 9894), Paterdomus.across(south, 3405, 9895))
        assertEquals(Tile(3405, 9895), Paterdomus.across(south, 3405, 9894))
    }

    @Test
    fun `a door clicked from a tile beside its doorway still takes the player through the doorway`() {
        val west = ZoneLoc(Paterdomus.DOOR_LEFT, LocShape.WALL_STRAIGHT, 2, 3408, 3489, 0)
        assertEquals(Tile(3409, 3489), Paterdomus.across(west, 3408, 3490))
        assertEquals(Tile(3408, 3489), Paterdomus.across(west, 3409, 3488))
        val south = ZoneLoc(Paterdomus.GATE_WEST, LocShape.WALL_STRAIGHT, 3, 3405, 9895, 0)
        assertEquals(Tile(3405, 9894), Paterdomus.across(south, 3406, 9895))
        assertEquals(Tile(3405, 9895), Paterdomus.across(south, 3404, 9894))
    }
}
