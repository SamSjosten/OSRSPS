package osrsps.game.loc

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.Zones
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/** Double doors and closed types that share an open type. Single doors are in [LocInteractionsTest]. */
class DoorsTest {
    private val types = LocTypes(
        listOf(
            LocType(DOOR, "Door", ops = listOf("Open", null, null, null, null), internalName = "elfdoor"),
            LocType(DOOR_OPEN, "Door", ops = listOf("Close", null, null, null, null), internalName = "elfdooropen"),
            LocType(OTHER_DOOR, "Door", ops = listOf("Open", null, null, null, null), internalName = "otherdoor"),
            LocType(DOOR_M, "Door", ops = listOf("Open", null, null, null, null), internalName = "poordoor_m"),
            LocType(DOOR_M_OPEN, "Door", ops = listOf("Close", null, null, null, null), internalName = "poordooropen_m"),
            LocType(TRAPDOOR, "Trapdoor", ops = listOf("Open", null, null, null, null), internalName = "trapdoor_level1"),
            LocType(TRAPDOOR_OPEN, "Trapdoor", ops = listOf("Close", null, null, null, null), internalName = "trapdoor_open_level1"),
            LocType(LEFT, "Large door", ops = listOf("Open", null, null, null, null)),
            LocType(LEFT_OPEN, "Large door", ops = listOf("Close", null, null, null, null)),
            LocType(RIGHT, "Large door", ops = listOf("Open", null, null, null, null)),
            LocType(RIGHT_OPEN, "Large door", ops = listOf("Close", null, null, null, null)),
        ).associateBy { it.id },
    )
    private val static = StaticLocs()
    private val zones = Zones()
    private val locs = Locs(types, static, CollisionMap().apply { markLoaded(50, 50) }, zones) { 0 }
    private val doors = Doors(locs).apply { registerDouble(LEFT, LEFT_OPEN, RIGHT, RIGHT_OPEN) }

    // Lumbridge Castle's large doors as the cache has them: the left leaf on the west edge of 3213, 3221, the right one north of it.
    private val left = ZoneLoc(LEFT, LocShape.WALL_STRAIGHT, 0, 3213, 3221, 0)
    private val right = ZoneLoc(RIGHT, LocShape.WALL_STRAIGHT, 0, 3213, 3222, 0)

    // Opened, both leaves move onto the tiles to the west and turn apart: the left onto its south edge, the right its north.
    private val leftOpened = ZoneLoc(LEFT_OPEN, LocShape.WALL_STRAIGHT, 3, 3212, 3221, 0)
    private val rightOpened = ZoneLoc(RIGHT_OPEN, LocShape.WALL_STRAIGHT, 1, 3212, 3222, 0)

    @AfterTest
    fun release() = zones.endCycle()

    @Test
    fun `opening either leaf of double doors opens both`() {
        static.add(left)
        static.add(right)
        assertEquals(leftOpened, doors.toggle(left, 1))
        assertEquals(listOf(leftOpened), locs.at(3212, 3221, 0))
        assertEquals(listOf(rightOpened), locs.at(3212, 3222, 0))
        assertTrue(locs.at(3213, 3221, 0).isEmpty() && locs.at(3213, 3222, 0).isEmpty())

        assertEquals(right, doors.toggle(rightOpened, 1))
        assertEquals(listOf(left), locs.at(3213, 3221, 0))
        assertEquals(listOf(right), locs.at(3213, 3222, 0))
        assertEquals(0, locs.changed, "back as the cache has them")

        assertEquals(rightOpened, doors.toggle(right, 1))
        assertEquals(listOf(leftOpened), locs.at(3212, 3221, 0))
        assertEquals(left, doors.toggle(leftOpened, 1))
        assertEquals(0, locs.changed)
    }

    @Test
    fun `a leaf on its own opens and closes by itself`() {
        static.add(right)
        assertEquals(rightOpened, doors.toggle(right, 1))
        assertEquals(1, locs.at(3212, 3222, 0).size)
        assertTrue(locs.at(3212, 3221, 0).isEmpty())
        assertEquals(right, doors.toggle(rightOpened, 1))
        assertEquals(0, locs.changed)
    }

    @Test
    fun `an option that isn't Open or Close leaves double doors alone`() {
        static.add(left)
        static.add(right)
        assertNull(doors.toggle(left, 2))
        assertEquals(0, locs.changed)
    }

    @Test
    fun `a door sharing an open type closes back into the cache's type`() {
        doors.addClosedType(OTHER_DOOR, DOOR_OPEN)
        val other = ZoneLoc(OTHER_DOOR, LocShape.WALL_STRAIGHT, 1, 3207, 3217, 0)
        static.add(other)
        val opened = doors.toggle(other, 1)!!
        assertEquals(ZoneLoc(DOOR_OPEN, LocShape.WALL_STRAIGHT, 2, 3207, 3218, 0), opened)
        assertEquals(other, doors.toggle(opened, 1))
        assertEquals(0, locs.changed)
        // The open type still closes into its own partner where the cache has no door.
        assertEquals(DOOR, doors.closedType(DOOR_OPEN))
        assertEquals(DOOR_OPEN, doors.openType(DOOR))
    }

    @Test
    fun `a door with something after door in its name pairs with open put after door`() {
        assertEquals(DOOR_M_OPEN, doors.openType(DOOR_M))
        assertEquals(DOOR_M, doors.closedType(DOOR_M_OPEN))
        // Trapdoors aren't wall doors.
        assertNull(doors.openType(TRAPDOOR))
    }

    private companion object {
        const val DOOR = 1543
        const val DOOR_OPEN = 1544
        const val OTHER_DOOR = 12348
        const val LEFT = 12349
        const val LEFT_OPEN = 1522
        const val RIGHT = 12350
        const val RIGHT_OPEN = 1525
        const val DOOR_M = 56376
        const val DOOR_M_OPEN = 56377
        const val TRAPDOOR = 1580
        const val TRAPDOOR_OPEN = 1582
    }
}
