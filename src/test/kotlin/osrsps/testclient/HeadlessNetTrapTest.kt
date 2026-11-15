package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.NetTraps
import osrsps.game.skill.hunter.Traps
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Hunter step 3 through the headless client: a net trap set on a swamp young tree from the tile south of it, a swamp
 * lizard caught on the net and checked, and the tree back as the cache has it; then one set from the west, a lizard
 * that gets away once the player's level is below its own, the tree back up beside the fallen net, Reset and
 * Dismantle. The player logs in at 3222, 3218.
 */
class HeadlessNetTrapTest {
    private val lizard = NpcType(NetTraps.SWAMP_LIZARD, "Swamp lizard", internalName = "salamander_green")
    private val kind = NetTraps.SWAMP

    private fun server(dir: Path, treeX: Int, treeZ: Int, lizardX: Int, lizardZ: Int) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(35_000)
        objs[NetTraps.NET_TRAP] = ObjType(NetTraps.NET_TRAP, "Net trap", stackable = false, tradeable = true, iops = listOf(null, null, null, null, "Drop"))
        objs[NetTraps.SWAMP_LIZARD_ITEM] = ObjType(NetTraps.SWAMP_LIZARD_ITEM, "Swamp lizard", stackable = false, tradeable = true)
        val trees = listOf(kind.tree, kind.setting, kind.treeSet, kind.treeReset)
        val nets = listOf(kind.set, kind.broken, kind.failing) + kind.creatures.flatMap { listOf(it.catching, it.full) }
        val types = trees.associateWith { LocType(it, "Young tree", opsOf(it), LocCollisionType(width = 1, length = 1)) } +
            nets.associateWith {
                val long = it != kind.set && it != kind.broken
                LocType(it, "Net trap", opsOf(it), LocCollisionType(width = 1, length = if (long) 2 else 1, interactType = 0, blocksProjectile = false))
            }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            locTypes = LocTypes(types),
            npcTypes = NpcTypes.of(lizard),
            npcSpawns = listOf(NpcSpawn(lizard, lizardX, lizardZ, source = "test")),
            staticLocs = StaticLocs().apply { add(ZoneLoc(kind.tree, LocShape.CENTREPIECE_STRAIGHT, 0, treeX, treeZ, 0)) },
            cycleMillis = millis,
        )
    }

    /** The cache's ops on each swamp tree and net. */
    private fun opsOf(loc: Int): List<String?> = when (loc) {
        kind.tree -> listOf("Set-trap", null, null, null, null)
        kind.treeSet, kind.set -> listOf("Dismantle", "Investigate", null, null, null)
        kind.treeReset, kind.broken -> listOf("Dismantle", "Reset", null, null, null)
        kind.creatures.single().full -> listOf("Check", "Reset", null, null, null)
        else -> List(5) { null }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.awaitLoc(id: Int, x: Int, z: Int, timeoutMillis: Long = 10_000) =
        await("loc $id at $x, $z", timeoutMillis) { if (locAt(x, z) == id) Unit else null }

    /** The ground loc on a tile, as the server has it. */
    private fun TestServer.groundLoc(x: Int, z: Int) = world.locs[x, z, 0, GROUND]?.id

    @Test
    fun `a net set south of a young tree catches a swamp lizard, and Check gives it and puts the tree back`(@TempDir dir: Path) {
        server(dir, treeX = 3222, treeZ = 3219, lizardX = 3224, lizardZ = 3218).use { server ->
            server.login("hana").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item ${NetTraps.NET_TRAP} 1")
                client.await("the net trap") { if (client.count(NetTraps.NET_TRAP) == 1) Unit else null }
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                client.locOp(kind.tree, 3222, 3219, Traps.SET_TRAP)
                client.seen("[hunter.laying]")
                // The tree bends, then stands bent over the net on the player's tile; the player steps west off it.
                client.awaitLoc(kind.setting, 3222, 3219)
                client.awaitLoc(kind.set, at.x, at.z)
                client.awaitLoc(kind.treeSet, 3222, 3219)
                client.await("a step west") { client.position?.takeIf { it.x == at.x - 1 && it.z == at.z } }
                assertEquals(0, client.count(NetTraps.NET_TRAP))
                client.locOp(kind.treeSet, 3222, 3219, Traps.INVESTIGATE)
                client.seen("[hunter.investigate_net]")
                // Investigate walked the player back onto the net, beside the tree: nothing is caught while they stand there.
                client.command("tele ${at.x - 1} ${at.z}")
                client.await("off the net") { client.position?.takeIf { it.x == at.x - 1 && it.z == at.z } }
                // At 99 the catch is sure: the lizard walks onto the net, and the 1 by 2 net starts on the net's tile (the
                // net is south of the tree), the tree gone while it stands.
                val creature = kind.creatures.single()
                client.awaitLoc(creature.catching, at.x, at.z, timeoutMillis = 20_000)
                client.awaitLoc(creature.full, at.x, at.z)
                assertNull(server.groundLoc(3222, 3219))
                val trap = server.world.hunter.traps.all.single()
                assertEquals(2, trap.rotation)
                client.locOp(creature.full, at.x, at.z, Traps.CHECK)
                client.seen("[hunter.caught creature=swamp lizard]")
                client.await("152 XP") { if (client.stat(HUNTER)!!.xp - before == 152) Unit else null }
                client.await("the lizard") { if (client.count(NetTraps.SWAMP_LIZARD_ITEM) == 1) Unit else null }
                assertEquals(1, client.count(NetTraps.NET_TRAP))
                assertEquals(kind.tree, server.groundLoc(3222, 3219))
                assertNull(server.groundLoc(at.x, at.z))
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    @Test
    fun `a lizard above the player's level gets away, the tree springs up beside the net, and Reset and Dismantle work`(@TempDir dir: Path) {
        server(dir, treeX = 3223, treeZ = 3218, lizardX = 3220, lizardZ = 3218).use { server ->
            server.login("ivan").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${NetTraps.NET_TRAP} 1")
                // Below the lizard's 29 the tree refuses.
                client.locOp(kind.tree, 3223, 3218, Traps.SET_TRAP)
                client.seen("[hunter.net_level level=29]")
                client.command("setlevel hunter 29")
                client.await("level 29") { client.stat(HUNTER)?.takeIf { it.level == 29 } }
                client.locOp(kind.tree, 3223, 3218, Traps.SET_TRAP)
                client.awaitLoc(kind.set, at.x, at.z)
                assertEquals(3, server.world.hunter.traps.all.single().rotation)
                // Down to 1: the lizard gets away every time. The net is west of the tree, so the 1 by 2 starts on its tile.
                client.command("setlevel hunter 1")
                client.awaitLoc(kind.failing, at.x, at.z, timeoutMillis = 20_000)
                assertNull(server.groundLoc(3223, 3218))
                client.awaitLoc(kind.broken, at.x, at.z)
                client.awaitLoc(kind.treeReset, 3223, 3218)
                // Out of the way, so it doesn't walk into the trap laid again.
                val index = server.world.npcs.all.single().index
                client.command("hidenpc $index")
                client.await("the lizard hidden") { if (server.world.npcs.all.single().hidden) Unit else null }
                // Laying it again needs the level again.
                client.command("setlevel hunter 29")
                client.await("level 29 again") { client.stat(HUNTER)?.takeIf { it.level == 29 } }
                client.locOp(kind.treeReset, 3223, 3218, Traps.RESET)
                client.seen("[hunter.dismantled]")
                client.awaitLoc(kind.treeSet, 3223, 3218)
                client.awaitLoc(kind.set, at.x, at.z)
                assertEquals(0, client.count(NetTraps.NET_TRAP))
                client.locOp(kind.set, at.x, at.z, Traps.DISMANTLE)
                client.await("the net trap back") { if (client.count(NetTraps.NET_TRAP) == 1) Unit else null }
                assertEquals(kind.tree, server.groundLoc(3223, 3218))
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val HUNTER = 21
        const val GROUND = 2
    }
}
