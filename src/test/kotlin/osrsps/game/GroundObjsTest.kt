package osrsps.game

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import net.rsprot.protocol.game.outgoing.zone.payload.ObjCount
import net.rsprot.protocol.game.outgoing.zone.payload.ObjDel
import net.rsprot.protocol.message.OutgoingGameMessage
import net.rsprot.protocol.message.ZoneProt
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.ZoneObj
import osrsps.game.zone.Zones
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

class GroundObjsTest {
    private val axe = ObjType(1351, "Bronze axe", tradeable = true)
    private val coins = ObjType(995, "Coins", stackable = true, tradeable = true)
    private val bond = ObjType(13190, "Old school bond (untradeable)", tradeable = false)
    private val logs = ObjType(1511, "Logs", tradeable = true, ops = listOf(null, null, "Take", "Light", null))

    private var tick = 0L
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, {}) { tick }
    private val zones = Zones()
    private val collision = CollisionMap().apply { markLoaded(50, 50) }
    private val online = mutableSetOf("alice", "bob")
    private var population = 2
    private val sent = mutableListOf<Pair<Player, OutgoingGameMessage>>()
    private val ground = GroundObjs(zones, ObjTypes.of(axe, coins, bond, logs), scripts, collision, online::contains, { population }) { player, message ->
        sent += player to message
    }

    // Both in Lumbridge castle's courtyard zone, so each sees the other's zone.
    private val alice = Player(1, "alice", 3222, 3218, 0)
    private val bob = Player(2, "bob", 3220, 3220, 0)

    @AfterTest
    fun release() = zones.endCycle()

    /** What [observer]'s client is told about the zones this cycle, unencoded. */
    private fun prots(observer: Player): List<ZoneProt> = zones.collect(observer).flatMap { it.shared + it.own }

    /** Ends the cycle for both players, so each is watching its zones. */
    private fun watch() {
        prots(alice)
        prots(bob)
        zones.endCycle()
    }

    /** Runs [n] ticks: the timers, then the players' script phase. */
    private fun ticks(n: Int) = repeat(n) {
        tick++
        ground.process()
        scripts.process(alice)
        scripts.process(bob)
    }

    private fun messages(player: Player) = sent.filter { it.first === player }.mapNotNull { (it.second as? MessageGame)?.message }

    private fun dropAxe(): ZoneObj {
        alice.inventory.add(axe, 1)
        return assertNotNull(ground.drop(alice, 0))
    }

    @Test
    fun `a drop is the dropper's for 100 ticks, everyone's after, and gone 300 ticks after the drop`() {
        watch()
        val obj = dropAxe()
        assertTrue(alice.inventory.isEmpty())
        assertEquals(listOf(GroundObjs.SOUND_PUT_DOWN), alice.effects.pendingSounds.map { it.id })
        val add = prots(alice).single() as ObjAdd
        assertEquals(listOf(1351, 1, 100, 300, Zones.OWNERSHIP_SELF), listOf(add.id, add.quantity, add.timeUntilPublic, add.timeUntilDespawn, add.ownershipType))
        assertFalse(add.neverBecomesPublic)
        assertTrue(prots(bob).isEmpty())
        zones.endCycle()

        ticks(99)
        assertTrue(obj.isPrivate)
        zones.endCycle()
        ticks(1)
        assertFalse(obj.isPrivate)
        assertTrue(prots(alice).isEmpty(), "alice's client counted down itself")
        val reveal = prots(bob).single() as ObjAdd
        assertEquals(listOf(0, 200, Zones.OWNERSHIP_OTHER), listOf(reveal.timeUntilPublic, reveal.timeUntilDespawn, reveal.ownershipType))
        zones.endCycle()

        ticks(199)
        assertTrue(zones.hasObj(obj))
        zones.endCycle()
        ticks(1)
        assertFalse(zones.hasObj(obj))
        assertTrue(prots(bob).single() is ObjDel)
    }

    @Test
    fun `a player coming by later is told the time left`() {
        val obj = dropAxe()
        ticks(40)
        val add = prots(alice).single() as ObjAdd
        assertEquals(listOf(60, 260), listOf(add.timeUntilPublic, add.timeUntilDespawn))
        assertSame(obj, zones.objsAt(3222, 3218, 0).single())
    }

    @Test
    fun `an untradeable drop never turns public, and its timer stops while its owner is logged out`() {
        watch()
        alice.inventory.add(bond, 1)
        val obj = assertNotNull(ground.drop(alice, 0))
        val add = prots(alice).single() as ObjAdd
        assertTrue(add.neverBecomesPublic)
        assertEquals(listOf(0, 300), listOf(add.timeUntilPublic, add.timeUntilDespawn))
        zones.endCycle()

        ticks(150)
        online -= "alice"
        ticks(500)
        assertTrue(obj.isPrivate)
        assertEquals(150, obj.ticksUntilDespawn)
        online += "alice"
        ticks(149)
        assertTrue(zones.hasObj(obj))
        ticks(1)
        assertFalse(zones.hasObj(obj))
        assertTrue(prots(bob).isEmpty(), "bob never knew about it")
    }

    @Test
    fun `a stackable drop lands on the dropper's own stack, which keeps its timers`() {
        watch()
        alice.inventory.add(coins, 50)
        val first = assertNotNull(ground.drop(alice, 0))
        zones.endCycle()
        ticks(30)
        alice.inventory.add(coins, 25)
        val second = assertNotNull(ground.drop(alice, 0))
        assertSame(first, second)
        assertEquals(75, first.count)
        assertEquals(listOf(70, 270), listOf(first.ticksUntilPublic, first.ticksUntilDespawn))
        val count = prots(alice).single() as ObjCount
        assertEquals(listOf(50, 75), listOf(count.oldQuantity, count.newQuantity))
        assertTrue(prots(bob).isEmpty())
    }

    @Test
    fun `stacks of different owners stay apart, as do objs that don't stack`() {
        alice.inventory.add(coins, 50)
        ground.drop(alice, 0)
        ground.addPrivate(coins, 10, alice.x, alice.z, 0, "bob", 100, 300)
        ground.addPrivate(axe, 3, alice.x, alice.z, 0, "alice", 100, 300)
        assertEquals(listOf(50, 10, 1, 1, 1), zones.objsAt(alice.x, alice.z, 0).map { it.count })
    }

    @Test
    fun `taking an obj puts it in the inventory, if there's room`() {
        val obj = dropAxe()
        assertTrue(ground.take(alice, obj))
        assertEquals(InvObj(1351, 1), alice.inventory[0])
        assertFalse(zones.hasObj(obj))
        assertFalse(ground.take(alice, obj))
        assertEquals(listOf("[game.too_late]"), messages(alice))

        val again = dropAxe()
        alice.inventory.add(axe, 28)
        assertFalse(ground.take(alice, again))
        assertTrue(zones.hasObj(again))
        assertEquals("[game.no_space]", messages(alice).last())
    }

    @Test
    fun `coins go onto the inventory's stack even when it is full`() {
        alice.inventory.add(axe, 27)
        alice.inventory.add(coins, 5)
        val obj = ground.addPublic(coins, 100, alice.x, alice.z, 0).single()
        assertTrue(ground.take(alice, obj))
        assertEquals(105, alice.inventory.count(995))
    }

    @Test
    fun `nobody can take another player's private drop`() {
        val obj = dropAxe()
        assertNull(ground.click(bob, 1351, obj.x, obj.z, GroundObjs.TAKE_OPTION))
        assertFalse(ground.take(bob, obj))
        assertTrue(zones.hasObj(obj))
    }

    @Test
    fun `a spawn comes back after its respawn time, shorter on a busy world`() {
        val spawn = ObjSpawn(1351, 1, 3223, 3218, 0, respawnTicks = 50)
        ground.addSpawn(spawn)
        watch()
        val obj = zones.objsAt(3223, 3218, 0).single()
        assertEquals(listOf(null, null), listOf(obj.owner, obj.droppedBy))
        assertSame(spawn, ground.spawnOf(obj))

        population = 0
        assertTrue(ground.take(alice, obj))
        zones.endCycle()
        ticks(49)
        assertTrue(zones.objsAt(3223, 3218, 0).isEmpty())
        ticks(1)
        val back = zones.objsAt(3223, 3218, 0).single()
        val add = prots(bob).single() as ObjAdd
        assertEquals(listOf(0, 0, Zones.OWNERSHIP_NONE), listOf(add.timeUntilPublic, add.timeUntilDespawn, add.ownershipType))
        assertSame(spawn, ground.spawnOf(back))

        // Wiki: ⌊BaseRate × (4000 − Population) / 4000⌋.
        population = 2000
        assertEquals(25, ground.respawnDelay(spawn))
        population = 1
        assertEquals(49, ground.respawnDelay(spawn))
        population = 5000
        assertEquals(1, ground.respawnDelay(spawn))
    }

    @Test
    fun `cleartile removes everything and spawns come back`() {
        ground.addSpawn(ObjSpawn(995, 10, alice.x, alice.z, 0, respawnTicks = 5))
        dropAxe()
        assertEquals(2, ground.clear(alice.x, alice.z, 0))
        assertTrue(zones.objsAt(alice.x, alice.z, 0).isEmpty())
        assertEquals(1, ground.pendingRespawns().size)
    }

    @Test
    fun `walking onto the obj takes it in the tick the player arrives`() {
        val obj = ground.addPublic(axe, 1, 3224, 3218, 0).single()
        val interaction = assertNotNull(ground.click(alice, 1351, 3224, 3218, GroundObjs.TAKE_OPTION))
        alice.waypoints.add(osrsps.map.Tile(3224, 3218))
        assertFalse(interaction.tryInteract(alice), "still walking")
        alice.x = 3224
        alice.waypoints.clear()
        assertTrue(interaction.tryInteract(alice))
        assertFalse(zones.hasObj(obj))
        assertEquals(1, alice.inventory.count(1351))
    }

    @Test
    fun `an obj on a table is taken from beside it a tick later`() {
        collision.add(3223, 3218, 0, CollisionFlag.LOC)
        val obj = ground.addPublic(axe, 1, 3223, 3218, 0).single()
        val interaction = assertNotNull(ground.click(alice, 1351, 3223, 3218, GroundObjs.TAKE_OPTION))
        assertTrue(interaction.tryInteract(alice))
        assertTrue(zones.hasObj(obj), "the take waits a tick")
        assertTrue(alice.scripts.delayed)
        ticks(1)
        assertFalse(zones.hasObj(obj))
        assertEquals(1, alice.inventory.count(1351))
        assertEquals(GroundObjs.ANIM_PICKUP_TABLE, alice.effects.anim?.id)
        assertEquals(listOf(GroundObjs.SOUND_PICK), alice.effects.pendingSounds.map { it.id })
    }

    @Test
    fun `a wall between the player and the table stops the take`() {
        // A wall flags the tiles on both its sides, as the map loader does.
        collision.add(3223, 3218, 0, CollisionFlag.LOC or CollisionFlag.WALL_WEST)
        collision.add(3222, 3218, 0, CollisionFlag.WALL_EAST)
        ground.addPublic(axe, 1, 3223, 3218, 0)
        val interaction = assertNotNull(ground.click(alice, 1351, 3223, 3218, GroundObjs.TAKE_OPTION))
        assertFalse(interaction.tryInteract(alice))
        val told = mutableListOf<String>()
        alice.interaction = interaction
        Interactions { _, text -> told += text }.process(alice) {}
        assertEquals(listOf("[game.cant_reach]"), told)
        assertNull(alice.interaction)
        assertEquals(0L, alice.inventory.count(1351))
    }

    @Test
    fun `an obj that vanished on the way ends the walk quietly`() {
        val obj = ground.addPublic(axe, 1, 3224, 3218, 0).single()
        val interaction = assertNotNull(ground.click(alice, 1351, 3224, 3218, GroundObjs.TAKE_OPTION))
        ground.take(bob, obj)
        alice.x = 3224
        assertTrue(interaction.tryInteract(alice))
        assertTrue(messages(alice).isEmpty())
    }

    @Test
    fun `other options run their handler, and do nothing interesting without one`() {
        val ran = mutableListOf<Op>()
        handlers.onGroundObj(1511, 4) { ran += it }
        val obj = ground.addPublic(logs, 1, alice.x, alice.z, 0).single()
        assertNotNull(ground.click(alice, 1511, alice.x, alice.z, 4)).tryInteract(alice)
        assertEquals(listOf(Op(OpTarget.GROUND_OBJ, 1511, 4, groundObj = obj)), ran)
        assertTrue(zones.hasObj(obj))

        assertNull(ground.click(alice, 1511, alice.x, alice.z, 5), "logs have no op5")
        handlers.onGroundObj(1351, 3) { ran += it }
        ground.addPublic(axe, 1, alice.x, alice.z, 0)
        assertNotNull(ground.click(alice, 1351, alice.x, alice.z, 3)).tryInteract(alice)
        assertEquals(2, ran.size)
        assertEquals(0L, alice.inventory.count(1351), "the handler replaced Take")
    }
}
