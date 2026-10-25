package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.ConditionalOp
import osrsps.game.FrameSlot
import osrsps.game.GameRandom
import osrsps.game.Interactions
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.SubOp
import osrsps.game.VarTypes
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
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
    private val shared = Interactions { _, text -> messages += text }
    private val player = Player(1, "test", 3212, 3210, 0)

    private fun spawn(type: NpcType = hans, x: Int = 3216, z: Int = 3210, wander: Int = 0) =
        npcs.add(NpcSpawn(type, x, z, wander = wander, source = "test"))!!

    /** One tick in World's order: NPCs (after letting go of finished ones), scripts, then the interaction and walk. */
    private fun tick() {
        tick++
        interactions.releaseNpcs()
        npcs.process()
        scripts.process(player)
        shared.process(player) { movement.process(player) }
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
        shared.process(under) { movement.process(under) }
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
        assertEquals(listOf(Interactions.CANT_REACH), messages)
        assertNull(player.interaction)
        assertNull(npc.engagedWith)
        // The player stops turning to the NPC they gave up on.
        assertNull(player.facingNpc)
        assertNull(faced.last())
    }

    @Test
    fun `an NPC registered for it is used from across a counter, others not`() {
        // A row of bank booths (block walking, not projectiles) along z 3211 the width of the map square; the NPC behind
        // the one at 3212, 3211, on a blocked floor as Lumbridge's banker stands.
        for (x in 3200..3263) LocCollision(map).addLoc(LocCollisionType(blocksProjectile = false), x, 3211, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        map.add(3212, 3212, 0, osrsps.map.CollisionFlag.FLOOR)
        val npc = spawn(x = 3212, z = 3212)
        val used = mutableListOf<Long>()
        handlers.onNpc(HANS, 1) { used += tick }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        repeat(3) { tick() }
        assertEquals(emptyList(), used)
        assertEquals(listOf(Interactions.CANT_REACH), messages)

        interactions.reachOverCounter(HANS)
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        assertEquals(listOf(1L + 3), used)
        assertEquals(3212 to 3210, player.x to player.z)
    }

    @Test
    fun `a counter that blocks projectiles stops the reach across it`() {
        interactions.reachOverCounter(HANS)
        for (x in 3200..3263) LocCollision(map).addLoc(LocCollisionType(), x, 3211, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        map.add(3212, 3212, 0, osrsps.map.CollisionFlag.FLOOR)
        val npc = spawn(x = 3212, z = 3212)
        handlers.onNpc(HANS, 1) { error("used") }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        repeat(3) { tick() }
        assertEquals(listOf(Interactions.CANT_REACH), messages)
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

    @Test
    fun `a forged slot out of range is ignored`() {
        val npc = spawn(x = 3213)
        interactions.useObj(player, npc.index, 99, BUCKET, invertRun = false)
        interactions.useObj(player, npc.index, -1, BUCKET, invertRun = false)
        assertNull(player.interaction)
    }

    @Test
    fun `a multi-NPC is clickable by the type it shows`() {
        val vars = VarTypes(varpCount = 200, varbits = emptyMap())
        val shown = NpcType(SHOWN, "Hans", ops = listOf("Talk-to", null, null, null, null))
        // The base type has no menu of its own; varp 0 = 1 shows the one that has.
        val multi = NpcType(MULTI, "null", interactable = false, multiVarp = 0, multiNpcs = intArrayOf(-1, SHOWN, -1))
        val interactions = NpcInteractions(npcs, NpcTypes.of(multi, shown), map, movement, handlers, scripts, { tick }, { _, _ -> }, { _, _ -> }, {})
        val player = Player(2, "multi", 3212, 3210, 0, vars)
        val npc = npcs.add(NpcSpawn(multi, 3216, 3210, source = "test"))!!
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        assertNull(player.interaction)
        player.vars[0] = 1
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        assertTrue(player.interaction is NpcInteraction)

        // It changes back while the player walks: on arrival there is nothing to use.
        val used = mutableListOf<Op>()
        handlers.onNpc(SHOWN, 1) { op -> used += op }
        handlers.onNpc(MULTI, 1) { op -> used += op }
        shared.process(player) { movement.process(player) }
        player.vars[0] = 0
        repeat(4) { shared.process(player) { movement.process(player) } }
        assertEquals(emptyList(), used)
        assertNull(player.interaction)
    }

    @Test
    fun `an NPC busy with one player stays with them when another arrives`() {
        val npc = spawn(x = 3213)
        handlers.onNpc(HANS, 1) { delay(5) }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        assertSame(player, npc.engagedWith)
        tick()
        val other = Player(2, "other", 3213, 3211, 0)
        interactions.click(other, npc.index, 1, -1, invertRun = false)
        shared.process(other) { movement.process(other) }
        assertSame(player, npc.engagedWith)
        // The first player finishes first; the NPC turns to the second, who is still busy with it, then lets go.
        fun both() {
            tick()
            scripts.process(other)
        }
        while (scripts.busy(player)) both()
        both()
        assertSame(other, npc.engagedWith)
        repeat(3) { both() }
        assertNull(npc.engagedWith)
    }

    @Test
    fun `an NPC is let go when its player clicks another NPC or walks off`() {
        val first = spawn(x = 3213)
        val second = spawn(x = 3212, z = 3212)
        // Each NPC's option opens a dialogue, as task 18's will.
        handlers.onNpc(HANS, 1) { this@NpcInteractionsTest.player.modals[FrameSlot.CHAT_CONTAINER] = DIALOGUE }
        interactions.click(player, first.index, 1, -1, invertRun = false)
        tick()
        assertSame(player, first.engagedWith)
        // The click closes the first dialogue and the second NPC opens its own, which says nothing about the first.
        interactions.click(player, second.index, 1, -1, invertRun = false)
        assertNull(first.engagedWith)
        repeat(3) { tick() }
        assertSame(player, second.engagedWith)
        interactions.leave(player)
        assertNull(second.engagedWith)
        assertNull(player.facingNpc)
    }

    @Test
    fun `an NPC is not handed to a waiting player who is already done with it`() {
        val npc = spawn(x = 3213)
        // Only the first player's option opens a dialogue; the other's is over at once.
        handlers.onNpc(HANS, 1) { this@NpcInteractionsTest.player.modals[FrameSlot.CHAT_CONTAINER] = DIALOGUE }
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        val other = Player(2, "other", 3213, 3211, 0)
        interactions.click(other, npc.index, 1, -1, invertRun = false)
        shared.process(other) { movement.process(other) }
        assertSame(player, npc.engagedWith)
        interactions.leave(player)
        assertNull(npc.engagedWith)
    }

    @Test
    fun `an option shown by a conditional option can be clicked while its var is in range`() {
        val vars = VarTypes(varpCount = 200, varbits = emptyMap())
        val guard = NpcType(GUARD, "Guard", ops = listOf("Talk-to", null, null, null, null), conditionalOps = listOf(ConditionalOp(2, 7, -1, 1, 1, "Pay")))
        val interactions = NpcInteractions(npcs, NpcTypes.of(guard), map, movement, handlers, scripts, { tick }, { _, _ -> }, { _, _ -> }, {})
        val player = Player(2, "cond", 3212, 3210, 0, vars)
        val npc = npcs.add(NpcSpawn(guard, 3216, 3210, source = "test"))!!
        interactions.click(player, npc.index, 3, -1, invertRun = false)
        assertNull(player.interaction)
        player.vars[7] = 1
        interactions.click(player, npc.index, 3, -1, invertRun = false)
        assertTrue(player.interaction is NpcInteraction)
        // The var changes on the way: the option is gone on arrival, so nothing runs.
        var used = false
        handlers.onNpc(GUARD, 3) { used = true }
        player.vars[7] = 0
        repeat(5) { shared.process(player) { movement.process(player) } }
        assertTrue(!used)
        assertNull(player.interaction)
    }

    @Test
    fun `only sub-options the NPC shows can be clicked`() {
        val vars = VarTypes(varpCount = 200, varbits = emptyMap())
        val guard = NpcType(
            GUARD, "Guard", ops = listOf("Talk-to", null, null, null, null),
            subOps = listOf(SubOp(0, 1, "Ask"), SubOp(0, 2, "Pay", ConditionalOp(0, 7, -1, 1, 1, "Pay"))),
        )
        val interactions = NpcInteractions(npcs, NpcTypes.of(guard), map, movement, handlers, scripts, { tick }, { _, _ -> }, { _, _ -> }, {})
        val player = Player(2, "sub", 3212, 3210, 0, vars)
        val npc = npcs.add(NpcSpawn(guard, 3216, 3210, source = "test"))!!
        // A forged sub-option the NPC doesn't have, and one hidden by its var, are ignored.
        interactions.click(player, npc.index, 1, 9, invertRun = false)
        assertNull(player.interaction)
        interactions.click(player, npc.index, 1, 2, invertRun = false)
        assertNull(player.interaction)
        interactions.click(player, npc.index, 1, 1, invertRun = false)
        assertTrue(player.interaction is NpcInteraction)
        player.vars[7] = 1
        interactions.click(player, npc.index, 1, 2, invertRun = false)
        assertEquals(2, (player.interaction as NpcInteraction).sub)
        // The var changes on the way: the sub-option is gone on arrival, so nothing runs.
        var used = false
        handlers.onNpc(GUARD, 1) { used = true }
        player.vars[7] = 0
        repeat(5) { shared.process(player) { movement.process(player) } }
        assertTrue(!used)
        assertNull(player.interaction)
    }

    @Test
    fun `a player still turned to an NPC after talking stops when it is removed`() {
        val npc = spawn(x = 3213)
        interactions.click(player, npc.index, 1, -1, invertRun = false)
        tick()
        assertSame(npc, player.facingNpc)
        npcs.remove(npc)
        interactions.removed(npc, listOf(null, player))
        assertNull(player.facingNpc)
        assertNull(faced.last())
    }

    @Test
    fun `the player stops facing the NPC when the used item goes`() {
        val npc = spawn()
        player.inventory[4] = InvObj(BUCKET, 1)
        interactions.useObj(player, npc.index, 4, BUCKET, invertRun = false)
        player.inventory[4] = null
        tick()
        assertNull(player.facingNpc)
        assertNull(faced.last())
    }

    private companion object {
        const val HANS = 3105
        const val COW = 2790
        const val BUCKET = 1925
        const val MULTI = 50
        const val SHOWN = 51
        const val GUARD = 52
        const val DIALOGUE = 231
    }
}
