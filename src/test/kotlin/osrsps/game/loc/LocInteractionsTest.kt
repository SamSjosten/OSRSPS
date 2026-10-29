package osrsps.game.loc

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.Interactions
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.Zones
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocShape
import osrsps.map.RouteTarget

class LocInteractionsTest {
    private var tick = 0L
    private val types = LocTypes(
        listOf(
            LocType(WALL, "Wall"),
            LocType(DOOR, "Door", ops = listOf("Open", null, null, null, null), internalName = "poordoor"),
            LocType(DOOR_OPEN, "Door", ops = listOf("Close", null, null, null, null), internalName = "poordooropen"),
            LocType(LADDER, "Ladder", ops = listOf("Climb-up", null, null, null, null)),
            LocType(CRATE, "Crate", ops = listOf(null, "Search", null, null, null)),
        ).associateBy { it.id },
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val static = StaticLocs()
    private val zones = Zones()
    private val locs = Locs(types, static, map, zones) { tick }
    private val doors = Doors(locs)
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { tick }
    private val movement = Movement(map)
    private val messages = mutableListOf<String>()
    private val faced = mutableListOf<RouteTarget>()
    private val interactions = LocInteractions(
        locs, map, movement, handlers, scripts, doors,
        message = { _, text -> messages += text },
        faceLoc = { _, target -> faced += target },
        clearFlag = {},
    )
    private val steps = Interactions(message = { _, text -> messages += text })
    private val player = Player(1, "test", 3212, 3210, 0)

    // A wall running north and south along the east edge of x 3215, with a closed door in it at z 3210.
    private val door = ZoneLoc(DOOR, LocShape.WALL_STRAIGHT, 2, 3215, 3210, 0)
    private val ladder = ZoneLoc(LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3210, 3214, 0)

    init {
        place(door)
        for (z in 3205..3215) if (z != 3210) place(ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 2, 3215, z, 0))
        place(ladder)
    }

    @AfterTest
    fun release() = zones.endCycle()

    private fun place(loc: ZoneLoc) {
        static.add(loc)
        LocCollision(map).addLoc(types[loc.id]!!.collision, loc.x, loc.z, loc.level, loc.shape, loc.angle)
    }

    /** One tick of the parts that matter here, in World's order: scripts, timed loc changes, then the interaction and walk. */
    private fun tick() {
        tick++
        scripts.process(player)
        locs.process()
        steps.process(player) { movement.process(player) }
    }

    private fun walkTo(x: Int, z: Int) {
        player.interaction = null
        movement.request(player, x, z, invertRun = false)
        repeat(20) { tick() }
    }

    @Test
    fun `a loc further away is used on the tick the player arrives`() {
        val used = mutableListOf<Long>()
        handlers.onLoc(LADDER, 1) { op -> used += tick; assertEquals(ladder, op.loc) }
        // 3212, 3210 to beside the ladder at 3210, 3214: three steps to 3210, 3213 (diagonals first need two straight).
        interactions.click(player, LADDER, 3210, 3214, 1, invertRun = false)
        repeat(6) { tick() }
        assertEquals(1, used.size)
        val arrived = used.single()
        assertTrue(arrived in 3L..4L, "used on arrival, tick $arrived")
        assertNull(player.interaction)
        assertEquals(RouteTarget.loc(3210, 3214, LocShape.CENTREPIECE_STRAIGHT, 0, 1, 1, 0), faced.single())
    }

    @Test
    fun `a loc next to the player is used on the tick the click is read`() {
        val used = mutableListOf<Long>()
        handlers.onLoc(LADDER, 1) { used += tick }
        player.x = 3210
        player.z = 3213
        tick = 10
        // Read in tick 11's input; used in tick 11's movement phase, before the player would step.
        interactions.click(player, LADDER, 3210, 3214, 1, invertRun = false)
        tick()
        assertEquals(listOf(11L), used)
    }

    @Test
    fun `a handler can move the player to another level`() {
        handlers.onLoc(LADDER, 1) { player.level = 1 }
        interactions.click(player, LADDER, 3210, 3214, 1, invertRun = false)
        repeat(6) { tick() }
        assertEquals(1, player.level)
    }

    @Test
    fun `an option with no handler does nothing interesting`() {
        player.x = 3210
        player.z = 3213
        // The ladder has only op1; a click on op2 is dropped as one the client can't send.
        interactions.click(player, LADDER, 3210, 3214, 2, invertRun = false)
        assertNull(player.interaction)
        interactions.click(player, LADDER, 3210, 3214, 1, invertRun = false)
        tick()
        assertEquals(listOf("[game.nothing_interesting]"), messages)
    }

    @Test
    fun `an item used on a loc walks there and runs the handler for that item`() {
        val used = mutableListOf<Op>()
        handlers.onObjOnLoc(LADDER, BUCKET) { op -> used += op }
        player.inventory[3] = InvObj(BUCKET, 1)
        interactions.useObj(player, LADDER, 3210, 3214, slot = 3, obj = BUCKET, invertRun = false)
        repeat(6) { tick() }
        assertEquals(listOf(Op.objOnLoc(LADDER, BUCKET, 3, ladder)), used)
        assertNull(player.interaction)
        assertEquals(1, faced.size)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `an item with no handler for the loc does nothing interesting`() {
        player.inventory[0] = InvObj(BUCKET, 1)
        interactions.useObj(player, CRATE_HELD_NOWHERE, 3210, 3214, slot = 0, obj = BUCKET, invertRun = false)
        assertNull(player.interaction, "no such loc there")
        interactions.useObj(player, LADDER, 3210, 3214, slot = 0, obj = BUCKET, invertRun = false)
        repeat(6) { tick() }
        assertEquals(listOf("[game.nothing_interesting]"), messages)
    }

    @Test
    fun `an item the slot doesn't hold, or that leaves it on the way, is used on nothing`() {
        val used = mutableListOf<Op>()
        handlers.onObjOnLoc(LADDER) { op -> used += op }
        player.inventory[0] = InvObj(BUCKET, 1)
        interactions.useObj(player, LADDER, 3210, 3214, slot = 1, obj = BUCKET, invertRun = false)
        assertNull(player.interaction)
        interactions.useObj(player, LADDER, 3210, 3214, slot = 0, obj = BUCKET, invertRun = false)
        tick()
        player.inventory[0] = null
        repeat(6) { tick() }
        assertTrue(used.isEmpty())
        assertTrue(messages.isEmpty())
        assertNull(player.interaction)
    }

    @Test
    fun `a click on a loc that isn't there is ignored`() {
        interactions.click(player, LADDER, 3211, 3214, 1, invertRun = false)
        assertNull(player.interaction)
        assertTrue(player.waypoints.isEmpty())
    }

    @Test
    fun `a loc the player can't get to says so once they stop`() {
        // Boxed in by walls on every side of their tile.
        player.x = 3220
        player.z = 3220
        val walls = LocCollision(map)
        for (angle in 0..3) walls.addLoc(types[WALL]!!.collision, 3220, 3220, 0, LocShape.WALL_STRAIGHT, angle)
        interactions.click(player, LADDER, 3210, 3214, 1, invertRun = false)
        tick()
        assertEquals(listOf("[game.cant_reach]"), messages)
        assertNull(player.interaction)
    }

    @Test
    fun `the interaction waits while the player is delayed`() {
        val used = mutableListOf<Long>()
        handlers.onLoc(LADDER, 1) { used += tick }
        player.x = 3210
        player.z = 3213
        scripts.start(player) { delay(3) }
        interactions.click(player, LADDER, 3210, 3214, 1, invertRun = false)
        repeat(4) { tick() }
        // The delay ends in tick 3's script phase, before its movement phase.
        assertEquals(listOf(3L), used)
    }

    @Test
    fun `open a door, walk through it, close it, and the tile blocks again`() {
        // The plan's check for this task, end to end.
        assertFalse(movement.canStep(0, 3215, 3210, 1, 0), "closed")
        interactions.click(player, DOOR, 3215, 3210, 1, invertRun = false)
        repeat(4) { tick() }
        val opened = ZoneLoc(DOOR_OPEN, LocShape.WALL_STRAIGHT, 3, 3216, 3210, 0)
        assertEquals(listOf(opened), locs.at(3216, 3210, 0), "swung onto the next tile, along its south edge")
        assertTrue(locs.at(3215, 3210, 0).isEmpty())
        assertTrue(movement.canStep(0, 3215, 3210, 1, 0), "open")

        walkTo(3217, 3210)
        assertEquals(3217, player.x)

        interactions.click(player, DOOR_OPEN, 3216, 3210, 1, invertRun = false)
        tick()
        assertEquals(listOf(door), locs.at(3215, 3210, 0))
        assertTrue(locs.at(3216, 3210, 0).isEmpty())
        assertFalse(movement.canStep(0, 3216, 3210, -1, 0), "closed again")
        assertEquals(0, locs.changed, "back as the cache has it")
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a door the cache places open closes onto its wall and opens again`() {
        // An open door on the north edge of 3220, 3220, from a closed door on the west edge of 3221, 3220.
        val open = ZoneLoc(DOOR_OPEN, LocShape.WALL_STRAIGHT, 1, 3220, 3220, 0)
        place(open)
        player.x = 3220
        player.z = 3219
        interactions.click(player, DOOR_OPEN, 3220, 3220, 1, invertRun = false)
        tick()
        val closed = ZoneLoc(DOOR, LocShape.WALL_STRAIGHT, 0, 3221, 3220, 0)
        assertEquals(listOf(closed), locs.at(3221, 3220, 0))
        assertFalse(movement.canStep(0, 3220, 3220, 1, 0))
        interactions.click(player, DOOR, 3221, 3220, 1, invertRun = false)
        repeat(3) { tick() }
        assertEquals(listOf(open), locs.at(3220, 3220, 0))
        assertEquals(0, locs.changed)
    }

    @Test
    fun `a handler takes over from the generic door`() {
        val ops = mutableListOf<Op>()
        handlers.onLoc(DOOR, 1) { ops += it }
        player.x = 3215
        interactions.click(player, DOOR, 3215, 3210, 1, invertRun = false)
        tick()
        assertEquals(1, ops.size)
        assertEquals(listOf(door), locs.at(3215, 3210, 0), "still closed")
    }

    @Test
    fun `double doors are left to content`() {
        // A second closed door beside the first, in the same wall.
        locs.add(ZoneLoc(DOOR, LocShape.WALL_STRAIGHT, 2, 3215, 3211, 0))
        player.x = 3215
        interactions.click(player, DOOR, 3215, 3210, 1, invertRun = false)
        tick()
        assertEquals(listOf(door), locs.at(3215, 3210, 0))
        assertEquals(listOf("[game.nothing_interesting]"), messages)
    }

    @Test
    fun `a door someone else opens first ends the interaction`() {
        interactions.click(player, DOOR, 3215, 3210, 1, invertRun = false)
        // Someone else opens it before the player gets there.
        doors.toggle(door, 1)
        repeat(5) { tick() }
        assertNull(player.interaction)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a re-running action whose loc goes stops the player's animation`() {
        // The player stands by the ladder, mid-action, when someone else changes it (another player felling a tree).
        player.x = 3210
        player.z = 3213
        interactions.repeat(player, ladder, 1)
        locs.remove(ladder)
        tick()
        assertNull(player.interaction)
        assertEquals(-1, player.effects.anim?.id)
    }

    @Test
    fun `doors pair by their gameval names`() {
        assertEquals(DOOR_OPEN, doors.openType(DOOR))
        assertEquals(DOOR, doors.closedType(DOOR_OPEN))
        assertNull(doors.openType(CRATE))
    }

    @Test
    fun `registering a door again drops its old partners`() {
        doors.register(DOOR, CRATE)
        assertNull(doors.closedType(DOOR_OPEN), "the old open type no longer closes into the door")
        assertEquals(DOOR, doors.closedType(CRATE))
        doors.register(LADDER, CRATE)
        assertNull(doors.openType(DOOR), "the door no longer opens into the reused open type")
        assertEquals(CRATE, doors.openType(LADDER))
    }

    private companion object {
        const val WALL = 1
        const val DOOR = 1535
        const val DOOR_OPEN = 1536
        const val LADDER = 16683
        const val CRATE = 5
        const val CRATE_HELD_NOWHERE = 6
        const val BUCKET = 1925
    }
}
