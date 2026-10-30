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

/** Fence gates open and close both leaves together, pairing types by name. */
class GatesTest {
    private val types = LocTypes(
        listOf(
            gate(LEFT, "Open", "farming_fencegate_l"),
            gate(RIGHT, "Open", "farming_fencegate_r"),
            gate(LEFT_OPEN, "Close", "farming_openfencegate_l"),
            gate(RIGHT_OPEN, "Close", "farming_openfencegate_r"),
            gate(LONE, "Open", "lone_fencegate_l"),
            gate(LONE_OPEN, "Close", "lone_openfencegate_l"),
            gate(UNPAIRED, "Open", "nothing_fencegate_l"),
            gate(FIELD_L, "Open", "fai_varrock_gate_l"),
            gate(FIELD_L_OPEN, "Close", "fai_varrock_gate_lc"),
            gate(FIELD_R, "Open", "fai_varrock_gate_r"),
            gate(FIELD_R_OPEN, "Close", "fai_varrock_gate_rc"),
        ).associateBy { it.id },
    )
    private val static = StaticLocs()
    private val zones = Zones()
    private val locs = Locs(types, static, CollisionMap().apply { markLoaded(48, 50) }, zones) { 0 }
    private val gates = Gates(locs)

    // Draynor's pig pen gate as the 241 cache has it: both leaves in a wall along the north edge (angle 1), left at x 3077.
    private val left = ZoneLoc(LEFT, LocShape.WALL_STRAIGHT, 1, 3077, 3258, 0)
    private val right = ZoneLoc(RIGHT, LocShape.WALL_STRAIGHT, 1, 3078, 3258, 0)

    // Open, both turn a quarter anticlockwise to angle 0 and stand north of the wall, against the left leaf's hinge.
    private val leftOpened = ZoneLoc(LEFT_OPEN, LocShape.WALL_STRAIGHT, 0, 3077, 3259, 0)
    private val rightOpened = ZoneLoc(RIGHT_OPEN, LocShape.WALL_STRAIGHT, 0, 3077, 3260, 0)

    @AfterTest
    fun release() = zones.endCycle()

    @Test
    fun `opening either leaf opens both, and closing puts back the cache's gate`() {
        static.add(left)
        static.add(right)
        assertEquals(rightOpened, gates.toggle(right, 1))
        assertEquals(listOf(leftOpened), locs.at(3077, 3259, 0))
        assertEquals(listOf(rightOpened), locs.at(3077, 3260, 0))
        assertTrue(locs.at(3077, 3258, 0).isEmpty() && locs.at(3078, 3258, 0).isEmpty())

        assertEquals(left, gates.toggle(leftOpened, 1))
        assertEquals(listOf(left), locs.at(3077, 3258, 0))
        assertEquals(listOf(right), locs.at(3078, 3258, 0))
        assertEquals(0, locs.changed, "back as the cache has them")

        gates.toggle(left, 1)
        assertEquals(right, gates.toggle(rightOpened, 1))
        assertEquals(0, locs.changed)
    }

    @Test
    fun `a gate facing each way keeps its leaves together`() {
        for (angle in 0..3) {
            val (px, pz) = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)[angle]
            val l = ZoneLoc(LEFT, LocShape.WALL_STRAIGHT, angle, 3090, 3240 + angle * 5, 0)
            val r = ZoneLoc(RIGHT, LocShape.WALL_STRAIGHT, angle, l.x + px, l.z + pz, 0)
            static.add(l)
            static.add(r)
            val opened = gates.toggle(l, 1)!!
            assertEquals((angle + 3) and 3, opened.angle)
            val gate = locs.at(opened.x, opened.z, 0) + locs.at(opened.x + listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)[opened.angle].first,
                opened.z + listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)[opened.angle].second, 0)
            assertEquals(listOf(LEFT_OPEN, RIGHT_OPEN), gate.map { it.id }, "angle $angle")
            assertEquals(l, gates.toggle(opened, 1))
            assertEquals(listOf(r), locs.at(r.x, r.z, 0), "angle $angle")
        }
        assertEquals(0, locs.changed)
    }

    @Test
    fun `a left leaf with no right type swings on its own`() {
        val lone = ZoneLoc(LONE, LocShape.WALL_STRAIGHT, 0, 3100, 3260, 0)
        static.add(lone)
        val opened = gates.toggle(lone, 1)!!
        assertEquals(ZoneLoc(LONE_OPEN, LocShape.WALL_STRAIGHT, 3, 3099, 3260, 0), opened)
        assertEquals(lone, gates.toggle(opened, 1))
    }

    @Test
    fun `other options and unpaired gates are left alone`() {
        static.add(left)
        static.add(right)
        assertNull(gates.toggle(left, 2))
        val unpaired = ZoneLoc(UNPAIRED, LocShape.WALL_STRAIGHT, 0, 3100, 3270, 0)
        static.add(unpaired)
        assertNull(gates.toggle(unpaired, 1))
        assertEquals(0, locs.changed)
    }

    @Test
    fun `the field gates north of Lumbridge open into their _lc and _rc types`() {
        // As the cache has the one at 3254, 3347: both leaves along a north wall (angle 1), the left one west.
        val l = ZoneLoc(FIELD_L, LocShape.WALL_STRAIGHT, 1, 3100, 3250, 0)
        val r = ZoneLoc(FIELD_R, LocShape.WALL_STRAIGHT, 1, 3101, 3250, 0)
        static.add(l)
        static.add(r)
        assertEquals(ZoneLoc(FIELD_L_OPEN, LocShape.WALL_STRAIGHT, 0, 3100, 3251, 0), gates.toggle(l, 1))
        assertEquals(listOf(ZoneLoc(FIELD_R_OPEN, LocShape.WALL_STRAIGHT, 0, 3100, 3252, 0)), locs.at(3100, 3252, 0))
        assertEquals(r, gates.toggle(locs.at(3100, 3252, 0).single(), 1))
        assertEquals(0, locs.changed)
    }

    private fun gate(id: Int, op: String, name: String) =
        LocType(id, "Gate", ops = listOf(op, null, null, null, null), internalName = name)

    companion object {
        const val LEFT = 1561
        const val RIGHT = 1562
        const val LEFT_OPEN = 1563
        const val RIGHT_OPEN = 1564
        const val LONE = 1
        const val LONE_OPEN = 2
        const val UNPAIRED = 3
        const val FIELD_L = 15514
        const val FIELD_L_OPEN = 15515
        const val FIELD_R = 15516
        const val FIELD_R_OPEN = 15517
    }
}
