package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GroundObjs
import osrsps.game.ObjSpawn
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * Ground items can't be piled high enough to break anyone's updates. The exploit review of 29 Oct 2026 found no limit
 * per tile, and a zone's objs all went in one packet that RSProt refuses past 40 KB, which failed the tick's updates
 * for every player after the one who saw the zone.
 */
class HeadlessGroundLimitsTest {
    private val sword = ObjType(1277, "Bronze sword", cost = 26, tradeable = true, iops = DROPPABLE)
    private val coins = ObjType(995, "Coins", cost = 1, stackable = true, tradeable = true, iops = DROPPABLE)

    private fun world() = World(IntArray(0), CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(sword, coins))

    @Test
    fun `a drop on a full tile is refused and the item stays in the bag, but a stack it joins still goes down`(@TempDir dir: Path) {
        TestServer(dir) { crcs, millis ->
            World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(sword, coins), cycleMillis = millis).apply {
                groundObjs.addPublic(sword, GroundObjs.MAX_PER_TILE - 1, World.SPAWN_X, World.SPAWN_Z, 0)
            }
        }.use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.command("item ${coins.id} 10")
                alice.command("item ${sword.id}")
                alice.await("the bag") { alice.inventory(BACKPACK).takeIf { it.size >= 2 && it[1] != null } }
                // The coins take the last place on the tile; the sword then has none.
                alice.ifButton(INVENTORY, ITEMS, DROP, sub = 0, obj = coins.id)
                alice.await("the coins down") { if (alice.inventory(BACKPACK)[0] == null) Unit else null }
                alice.ifButton(INVENTORY, ITEMS, DROP, sub = 1, obj = sword.id)
                alice.awaitMessage("refused") { it == GroundObjs.TILE_FULL }
                assertEquals(InvObj(sword.id, 1), alice.inventory(BACKPACK)[1])
                assertEquals(GroundObjs.MAX_PER_TILE, server.world.zones.objsAt(World.SPAWN_X, World.SPAWN_Z, 0).size)
                // More coins join her stack, which takes no new place.
                alice.command("item ${coins.id} 5")
                alice.await("more coins") { alice.inventory(BACKPACK)[0] }
                alice.ifButton(INVENTORY, ITEMS, DROP, sub = 0, obj = coins.id)
                alice.await("the stack of 15") {
                    server.world.zones.objsAt(World.SPAWN_X, World.SPAWN_Z, 0).singleOrNull { it.id == coins.id }?.takeIf { it.count == 15 }
                }
            }
        }
    }

    @Test
    fun `loot on a full tile pushes out the oldest objs there`() {
        val world = world()
        val first = world.groundObjs.addPublic(sword, 1, 3222, 3218, 0).single()
        world.groundObjs.addPublic(sword, GroundObjs.MAX_PER_TILE - 1, 3222, 3218, 0)
        world.groundObjs.addPrivate(coins, 100, 3222, 3218, 0, "alice", GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
        val objs = world.zones.objsAt(3222, 3218, 0)
        assertEquals(GroundObjs.MAX_PER_TILE, objs.size)
        assert(first !in objs) { "the oldest obj went" }
        assertEquals(coins.id, objs.last().id)
    }

    @Test
    fun `a spawn pushed off a full tile waits for room before it comes back`() {
        val world = world()
        world.groundObjs.addSpawn(ObjSpawn(sword.id, 1, 3222, 3218, 0, respawnTicks = 5))
        world.groundObjs.addPublic(sword, GroundObjs.MAX_PER_TILE - 1, 3222, 3218, 0)
        // The loot pushes out the spawn, the oldest obj there.
        world.groundObjs.addPrivate(coins, 100, 3222, 3218, 0, "alice", GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
        assertEquals(1, world.groundObjs.pendingRespawns().size)
        repeat(20) { world.groundObjs.process() }
        assertEquals(GroundObjs.MAX_PER_TILE, world.zones.objsAt(3222, 3218, 0).size)
        assertEquals(1, world.groundObjs.pendingRespawns().size, "still waiting")

        world.groundObjs.clear(3222, 3218, 0)
        world.groundObjs.process()
        assertEquals(listOf(sword.id), world.zones.objsAt(3222, 3218, 0).map { it.id })
        assertEquals(emptyList(), world.groundObjs.pendingRespawns())
    }

    @Test
    fun `a zone holding thousands of objs comes into view in parts, and nobody's tick is lost`(@TempDir dir: Path) {
        // 70 objs on each of a zone's 64 tiles, 4,480 in all: well past what one packet holds. Five zones from the spawn.
        val zoneX = 3264
        val zoneZ = 3264
        TestServer(dir) { crcs, millis ->
            World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(zoneX shr 6, zoneZ shr 6) }, objTypes = ObjTypes.of(sword), cycleMillis = millis).apply {
                for (dx in 0 until 8) for (dz in 0 until 8) groundObjs.addPublic(sword, PER_TILE, zoneX + dx, zoneZ + dz, 0)
            }
        }.use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.command("tele ${zoneX - 2} ${zoneZ - 2}")
                    val seen = alice.await("the zone's objs", timeoutMillis = 10_000) {
                        alice.groundObjs().count { it.coord.x in zoneX until zoneX + 8 && it.coord.z in zoneZ until zoneZ + 8 }.takeIf { it == 64 * PER_TILE }
                    }
                    assertEquals(64 * PER_TILE, seen)
                    val ticks = bob.ticksSeen
                    bob.await("bob still ticking") { if (bob.ticksSeen >= ticks + 5) Unit else null }
                }
            }
        }
    }

    private companion object {
        const val PER_TILE = 70
        const val BACKPACK = 93
        const val INVENTORY = 149
        const val ITEMS = 0
        const val DROP = 7
        val DROPPABLE = listOf(null, null, null, null, "Drop")
    }
}
