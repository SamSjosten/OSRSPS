package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.loc.LocInteraction
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

class NpcInteractionsTest {
    private var tick = 0L
    private val hans = NpcType(HANS, "Hans", ops = listOf("Talk-to", null, null, null, null))
    private val cow = NpcType(COW, "Cow", size = 2, ops = listOf(null, "Attack", null, null, null))
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { tick }
    private val movement = Movement(map)
    private val messages = mutableListOf<String>()
    private val faced = mutableListOf<Npc?>()
    private val interactions = NpcInteractions(
        npcs, NpcTypes.of(hans, cow), map, movement, handlers, scripts, { tick },
        message = { _, text -> messages += text },
        faceNpc = { _, npc -> faced += npc },
        clearFlag = {},
    )
    private val player = Player(1, "test", 3212, 3210, 0)

    private fun spawn(type: NpcType = hans, x: Int = 3216, z: Int = 3210, wander: Int = 0) =
        npcs.add(NpcSpawn(type, x, z, wander = wander, source = "test"))!!

    /** One tick in World's order: NPCs (after letting go of finished ones), scripts, then the interaction and walk. */
    private fun tick() {
        tick++
        interactions.releaseNpcs()
        npcs.process()
        scripts.process(player)
        interactions.process(player) { movement.process(player) }
    }

    @Test
    fun `talk-to walks the player beside the NPC, then runs the handler and the NPC faces them`() {
        val npc = spawn()
        val used = mutableListOf<Long>()
        handlers.onNpc(HANS, 1) { op ->
            used += tick
            assertSame(npc, op.npc)
        }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        // Turned to on the click.
        assertEquals(listOf<Npc?>(npc), faced)
        repeat(3) { tick() }
        // 3212 to 3215, beside Hans at 3216: three steps, used on the third tick.
        assertEquals(listOf(3L), used)
        assertEquals(3215 to 3210, player.x to player.z)
        assertSame(player, npc.engagedWith)
        assertSame(player, npc.facing)
        assertNull(player.interaction)
        // The handler is done, so Hans is let go the next tick.
        tick()
        assertNull(npc.engagedWith)
    }

    @Test
    fun `an NPC beside the player is used on the tick the click is read`() {
        val npc = spawn(x = 3213)
        val used = mutableListOf<Long>()
        handlers.onNpc(HANS, 1) { used += tick }
        tick = 10
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        assertEquals(listOf(11L), used)
    }

    @Test
    fun `NPCs are not reached from a corner or from under them`() {
        val npc = spawn(x = 3213, z = 3211)
        handlers.onNpc(HANS, 1) {}
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        assertNull(npc.engagedWith)
        // From the corner at 3212, 3210 the player steps to a side first, and talks on arriving there.
        tick()
        assertSame(player, npc.engagedWith)
        assertTrue((player.x to player.z) in setOf(3212 to 3211, 3213 to 3210), "at ${player.x}, ${player.z}")

        val under = Player(2, "under", 3220, 3220, 0)
        val other = spawn(x = 3220, z = 3220)
        interactions.click(under, other.index, 1, -1, invertRun = false)
        interactions.process(under) { movement.process(under) }
        assertTrue(!other.covers(under.x, under.z), "stepped out from under it")
    }

    @Test
    fun `a big NPC is reached along any of its sides`() {
        val npc = spawn(cow, x = 3216, z = 3210)
        handlers.onNpc(COW, 2) {}
        interactions.click(player, npc.index, 2, -1, invertRun = false)
        repeat(3) { tick() }
        assertSame(player, npc.engagedWith)
        assertEquals(3215 to 3210, player.x to player.z)
    }

    @Test
    fun `the player chases an NPC that walks away`() {
        val npc = spawn(x = 3216)
        val used = mutableListOf<Long>()
        handlers.onNpc(HANS, 1) { used += tick }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        // Hans walks east a tile a tick for three ticks, as fast as the player, then stops at 3219.
        npc.destination = Tile(3219, 3210)
        repeat(8) { tick() }
        // The player follows him to 3218: six steps.
        assertEquals(listOf(6L), used)
        assertEquals(3218 to 3210, player.x to player.z)
    }

    @Test
    fun `an option with no handler does nothing interesting`() {
        val npc = spawn(x = 3213)
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        assertEquals(listOf(NpcInteractions.NOTHING_INTERESTING), messages)
        // It still turns to the player, and goes back to wandering the next tick.
        assertSame(player, npc.engagedWith)
        tick()
        assertNull(npc.engagedWith)
        assertNull(npc.facing)
    }

    @Test
    fun `an option the NPC doesn't have is ignored`() {
        val npc = spawn(x = 3213)
        interactions.click(player, npc.index, 2, -1, invertRun = false)
        assertNull(player.interaction)
        assertEquals(emptyList(), faced)
    }

    @Test
    fun `an NPC out of view can't be clicked`() {
        val npc = spawn(x = 3212 + NpcInteractions.VIEW_RANGE + 1)
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        assertNull(player.interaction)
    }

    @Test
    fun `the NPC stays with the player while they are busy`() {
        val npc = spawn(x = 3213, wander = 5)
        handlers.onNpc(HANS, 1) { delay(3) }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        repeat(3) {
            tick()
            assertSame(player, npc.engagedWith)
            assertEquals(3213 to 3210, npc.x to npc.z)
        }
        tick()
        assertNull(npc.engagedWith)
    }

    @Test
    fun `an NPC behind a wall can't be reached`() {
        // A wall along the west edge of x 3214 the whole height of the loaded map square; Hans on the far side.
        val walls = LocCollision(map)
        for (z in 3200..3263) walls.addLoc(LocCollisionType(), 3214, z, 0, LocShape.WALL_STRAIGHT, 0)
        val npc = spawn(x = 3216)
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        repeat(5) { tick() }
        assertEquals(listOf(NpcInteractions.CANT_REACH), messages)
        assertNull(player.interaction)
        assertNull(npc.engagedWith)
    }

    @Test
    fun `the NPC going away ends the interaction quietly`() {
        val npc = spawn()
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        npcs.remove(npc)
        tick()
        assertNull(player.interaction)
        assertEquals(emptyList(), messages)
        assertNull(faced.last())
    }

    @Test
    fun `a new click replaces the interaction`() {
        val npc = spawn()
        handlers.onNpc(HANS, 1) {}
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        // Walking off: World clears the interaction and the facing.
        player.interaction = null
        interactions.stopFacing(player)
        movement.request(player, 3205, 3210, invertRun = false)
        repeat(6) { tick() }
        assertNull(npc.engagedWith)
        assertNull(faced.last())
        assertNull(player.facingNpc)
    }

    @Test
    fun `a loc interaction is left to the loc code`() {
        val loc = LocInteraction(ZoneLoc(1, LocShape.CENTREPIECE_STRAIGHT, 0, 3215, 3215, 0), 1)
        player.interaction = loc
        var moved = false
        interactions.process(player) { moved = true }
        assertTrue(moved)
        assertSame(loc, player.interaction)
    }

    @Test
    fun `an item used on an NPC reaches its handler with the item and slot`() {
        val npc = spawn(x = 3213)
        player.inventory[4] = InvObj(BUCKET, 1)
        val seen = mutableListOf<Op>()
        handlers.onObjOnNpc(HANS, BUCKET) { op -> seen += op }
        interactions.useObj(player, npc.index, 4, BUCKET, invertRun = false)
        tick()
        val op = seen.single()
        assertEquals(OpTarget.OBJ_ON_NPC, op.target)
        assertEquals(BUCKET to 4, op.obj to op.sub)
        assertSame(npc, op.npc)
    }

    @Test
    fun `an item the player doesn't hold is ignored, and an unhandled one does nothing interesting`() {
        val npc = spawn(x = 3213)
        interactions.useObj(player, npc.index, 4, BUCKET, invertRun = false)
        assertNull(player.interaction)
        player.inventory[4] = InvObj(BUCKET, 1)
        interactions.useObj(player, npc.index, 4, BUCKET, invertRun = false)
        tick()
        assertEquals(listOf(NpcInteractions.NOTHING_INTERESTING), messages)
    }

    @Test
    fun `an item that leaves its slot on the way is not used`() {
        val npc = spawn()
        player.inventory[4] = InvObj(BUCKET, 1)
        var used = false
        handlers.onObjOnNpc(HANS, BUCKET) { used = true }
        interactions.useObj(player, npc.index, 4, BUCKET, invertRun = false)
        tick()
        player.inventory[4] = null
        repeat(4) { tick() }
        assertTrue(!used)
        assertNull(player.interaction)
        assertNull(npc.engagedWith)
    }

    @Test
    fun `a multi-NPC uses the type the player sees`() {
        val shown = NpcType(SHOWN, "Hans", ops = listOf("Talk-to", null, null, null, null))
        val multi = NpcType(MULTI, "null", ops = NpcType.DEFAULT_OPS, multiVarp = 0, multiNpcs = intArrayOf(-1, SHOWN, -1))
        val types = NpcTypes.of(hans, multi, shown)
        val interactions = NpcInteractions(npcs, types, map, movement, handlers, scripts, { tick }, { _, _ -> }, { _, _ -> }, {})
        val npc = npcs.add(NpcSpawn(multi, 3213, 3210, source = "test"))!!
        // No var types here, so its varp reads as unknown: it shows its default, nothing, and has no options.
        assertNull(interactions.shownType(npc, player))
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        assertNull(player.interaction)
    }

    private companion object {
        const val HANS = 3105
        const val COW = 2790
        const val BUCKET = 1925
        const val MULTI = 50
        const val SHOWN = 51
    }
}
