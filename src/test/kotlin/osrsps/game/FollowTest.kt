package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.Tile

class FollowTest {
    private val map = CollisionMap().apply { markLoaded(3200 shr 6, 3200 shr 6) }
    private val movement = Movement(map)
    private val online = mutableSetOf<Player>()
    private val sent = mutableListOf<Pair<Player, OutgoingGameMessage>>()
    private val follows = Follows(movement, online::contains) { player, message -> sent += player to message }

    private val alice = Player(1, "alice", 3210, 3210, 0).also(online::add)
    private val bob = Player(2, "bob", 3215, 3210, 0).also(online::add)

    /** One tick's movement phase, as World runs it, with the players moving in [order]. */
    private fun tick(order: List<Player> = listOf(alice, bob)) {
        follows.startMovement(order)
        for (player in order) follows.process(player, move = { movement.process(player) }) { movement.process(player) }
    }

    private fun Player.tile() = Tile(x, z)

    @Test
    fun `a target who hasn't moved is followed to the tile west of them`() {
        follows.click(alice, bob, ctrl = false)
        assertIs<FollowInteraction>(alice.interaction)
        repeat(6) { tick() }
        assertEquals(Tile(3214, 3210), alice.tile())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `the follower walks onto each tile the target steps off and ends one behind`() {
        follows.click(alice, bob, ctrl = false)
        repeat(4) { tick() }
        assertEquals(Tile(3214, 3210), alice.tile())
        movement.request(bob, 3215, 3216, invertRun = false)
        val trail = (1..8).map { tick(); alice.tile() }
        // Bob walks north a tile a tick; Alice takes the tile he left at the start of each tick.
        assertEquals(Tile(3215, 3210), trail[1])
        assertEquals(Tile(3215, 3211), trail[2])
        assertEquals(Tile(3215, 3215), trail.last())
        assertEquals(Tile(3215, 3216), bob.tile())
        assertIs<FollowInteraction>(alice.interaction)
    }

    @Test
    fun `who moves first in the tick doesn't change the follow`() {
        val other = FollowTest()
        follows.click(alice, bob, ctrl = false)
        other.follows.click(other.alice, other.bob, ctrl = false)
        movement.request(bob, 3225, 3214, invertRun = false)
        other.movement.request(other.bob, 3225, 3214, invertRun = false)
        repeat(12) {
            tick(listOf(alice, bob))
            other.tick(listOf(other.bob, other.alice))
            assertEquals(alice.tile(), other.alice.tile(), "tick $it")
        }
    }

    @Test
    fun `a running target is followed to the tile between their two steps`() {
        bob.runEnabled = true
        follows.click(alice, bob, ctrl = false)
        repeat(4) { tick() }
        movement.request(bob, 3215, 3220, invertRun = false)
        tick()
        tick()
        // Bob ran 3210 -> 3212 -> 3214. Alice walks, so she falls behind, heading for 3211 and then 3213.
        assertEquals(Tile(3215, 3214), bob.tile())
        assertEquals(Tile(3215, 3211), alice.tile())
        repeat(10) { tick() }
        // His last run went 3218 -> 3220, past 3219.
        assertEquals(Tile(3215, 3220), bob.tile())
        assertEquals(Tile(3215, 3219), alice.tile())
    }

    @Test
    fun `the follower takes a route around a wall instead of stopping at it`() {
        // A wall along the east side of x 3212 from z 3208 to 3212, between Alice and Bob's west tile.
        for (z in 3208..3212) {
            map.add(3212, z, 0, CollisionFlag.WALL_EAST)
            map.add(3213, z, 0, CollisionFlag.WALL_WEST)
        }
        follows.click(alice, bob, ctrl = false)
        repeat(12) { tick() }
        assertEquals(Tile(3214, 3210), alice.tile())
    }

    @Test
    fun `the follow stops when the target logs out, and the follower stops walking`() {
        alice.x = 3200
        follows.click(alice, bob, ctrl = false)
        tick()
        online -= bob
        tick()
        assertNull(alice.interaction)
        assertTrue(alice.waypoints.isEmpty())
        assertEquals(listOf<Pair<Player, OutgoingGameMessage>>(alice to SetMapFlagV2.RESET), sent)
        val stoppedAt = alice.tile()
        repeat(3) { tick() }
        assertEquals(stoppedAt, alice.tile())
    }

    @Test
    fun `the follow stops when the target changes level or goes out of sight`() {
        follows.click(alice, bob, ctrl = false)
        bob.level = 1
        tick()
        assertNull(alice.interaction)

        bob.level = 0
        follows.click(alice, bob, ctrl = false)
        bob.x = alice.x + Follows.RANGE + 1
        tick()
        assertNull(alice.interaction)
    }

    @Test
    fun `following yourself or someone out of sight does nothing`() {
        follows.click(alice, alice, ctrl = false)
        assertNull(alice.interaction)
        bob.x = alice.x - Follows.RANGE - 1
        follows.click(alice, bob, ctrl = false)
        assertNull(alice.interaction)
    }

    @Test
    fun `a follow with nowhere to walk clears the flag`() {
        alice.x = bob.x - 1
        follows.click(alice, bob, ctrl = false)
        assertEquals(listOf<Pair<Player, OutgoingGameMessage>>(alice to SetMapFlagV2.RESET), sent)
        assertIs<FollowInteraction>(alice.interaction)
    }

    @Test
    fun `ctrl-click flips run for the walk to the target only`() {
        alice.x = 3200
        follows.click(alice, bob, ctrl = true)
        assertTrue(alice.invertRun)
        repeat(20) { tick() }
        movement.request(bob, 3220, 3210, invertRun = false)
        tick()
        tick()
        assertEquals(false, alice.invertRun)
    }

    @Test
    fun `steps remember the tile they left`() {
        assertNull(bob.lastStepFrom)
        movement.request(bob, 3217, 3210, invertRun = false)
        movement.process(bob)
        assertEquals(Tile(3215, 3210), bob.lastStepFrom)
    }

    @Test
    fun `face angles are the client's`() {
        // The client's sum lands a unit off the exact compass points: 325.949 is a hair under 2048 / 2π.
        assertEquals(0, Follows.angle(0, 1, 0, 0), "target south")
        assertEquals(511, Follows.angle(0, 0, -1, 0), "target west")
        assertEquals(1023, Follows.angle(0, 0, 0, 1), "target north")
        assertEquals(1537, Follows.angle(0, 0, 1, 0), "target east")
    }

    @Test
    fun `players get Follow, Trade with and Report, none above Walk here`() {
        val sent = mutableListOf<OutgoingGameMessage>()
        PlayerOps.send(sent::add)
        val ops = sent.map { it as net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp }
        assertEquals(listOf(2 to null, 3 to "Follow", 4 to "Trade with", 5 to null, 8 to "Report"), ops.map { it.id to it.op })
        assertTrue(ops.none { it.priority })
    }
}
