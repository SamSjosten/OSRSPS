package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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
import osrsps.game.skill.hunter.Deadfalls
import osrsps.game.skill.hunter.Traps
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Hunter step 4 through the headless client: a deadfall set on a boulder east of the player with a knife and logs, a
 * barb-tailed kebbit caught coming in from the north and checked, and the boulder back; then no knife, two deadfalls at
 * most, none below level 23, and a barb-tailed kebbit above the player's level getting away, which ends the trap. The
 * player logs in at 3222, 3218.
 */
class HeadlessDeadfallTest {
    private val barbed = NpcType(Deadfalls.BARB_TAILED_KEBBIT, "Barb-tailed kebbit", internalName = "huntingbeast_barbedtail")
    private val wild = NpcType(Deadfalls.WILD_KEBBIT, "Wild kebbit", internalName = "huntingbeast_claws")
    private val kind = Deadfalls.KIND

    private fun server(dir: Path, kebbit: NpcType, kebbitX: Int, kebbitZ: Int, boulders: List<Pair<Int, Int>>) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(35_000)
        objs[KNIFE] = ObjType(KNIFE, "Knife", stackable = false, tradeable = true)
        objs[LOGS] = ObjType(LOGS, "Logs", stackable = false, tradeable = true)
        for (id in listOf(Deadfalls.BONES, Deadfalls.BARB_TAIL_HARPOON, Deadfalls.RAW_BARB_TAILED_KEBBIT, Deadfalls.KEBBIT_CLAWS)) {
            objs[id] = ObjType(id, "Drop $id", stackable = false, tradeable = true)
        }
        val ids = listOf(kind.boulder, kind.setting, kind.set, kind.failing) + kind.mirrored.values + kind.creatures.flatMap { listOf(it.catching, it.full) }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            locTypes = LocTypes(ids.associateWith { LocType(it, "Boulder", opsOf(it), LocCollisionType(width = 2, length = 1, blocksProjectile = false)) }),
            npcTypes = NpcTypes.of(barbed, wild),
            npcSpawns = listOf(NpcSpawn(kebbit, kebbitX, kebbitZ, source = "test")),
            staticLocs = StaticLocs().apply {
                for ((x, z) in boulders) add(ZoneLoc(kind.boulder, LocShape.CENTREPIECE_STRAIGHT, 0, x, z, 0))
            },
            cycleMillis = millis,
        )
    }

    /** The cache's ops on the boulder in each state. */
    private fun opsOf(loc: Int): List<String?> = when (loc) {
        kind.boulder -> listOf("Set-trap", null, null, null, null)
        kind.set -> listOf("Dismantle", "Investigate", null, null, null)
        in kind.creatures.map { it.full } -> listOf("Check", null, null, null, null)
        else -> List(5) { null }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.awaitLoc(id: Int, x: Int, z: Int, timeoutMillis: Long = 10_000) =
        await("loc $id at $x, $z", timeoutMillis) { if (locAt(x, z) == id) Unit else null }

    private fun TestServer.groundLoc(x: Int, z: Int) = world.locs[x, z, 0, GROUND]?.id

    @Test
    fun `a deadfall on a boulder catches a barb-tailed kebbit from the north, and Check gives its drops and the boulder back`(@TempDir dir: Path) {
        server(dir, barbed, 3223, 3220, listOf(3223 to 3218)).use { server ->
            server.login("jo").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item $KNIFE 1")
                client.command("item $LOGS 1")
                client.await("the logs") { if (client.count(LOGS) == 1) Unit else null }
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                client.locOp(kind.boulder, 3223, 3218, Traps.SET_TRAP)
                client.seen("[hunter.laying]")
                client.awaitLoc(kind.setting, 3223, 3218)
                client.awaitLoc(kind.set, 3223, 3218)
                client.locOp(kind.set, 3223, 3218, Traps.INVESTIGATE)
                client.seen("[hunter.investigate_deadfall]")
                // From 8 ticks on it looks; the kebbit two tiles north comes to the north side, and the mirrored loc shows.
                client.awaitLoc(kind.mirrored.getValue(Deadfalls.BARB_TAILED_KEBBIT), 3223, 3218, timeoutMillis = 20_000)
                val full = kind.creatures.first { it.npc == Deadfalls.BARB_TAILED_KEBBIT }.full
                client.awaitLoc(full, 3223, 3218)
                client.locOp(full, 3223, 3218, Traps.CHECK)
                client.seen("[hunter.caught creature=barb-tailed kebbit]")
                client.await("134 XP") { if (client.stat(HUNTER)!!.xp - before == 134) Unit else null }
                client.await("the drops") { if (client.count(Deadfalls.RAW_BARB_TAILED_KEBBIT) == 1) Unit else null }
                assertEquals(1, client.count(Deadfalls.BONES))
                assertEquals(1, client.count(Deadfalls.BARB_TAIL_HARPOON))
                assertEquals(kind.boulder, server.groundLoc(3223, 3218))
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    @Test
    fun `a knife is needed, two deadfalls at most, level 23 to set one, and a kebbit above the player's level gets away and ends the trap`(@TempDir dir: Path) {
        server(dir, barbed, 3225, 3219, listOf(3223 to 3218, 3223 to 3224, 3223 to 3212)).use { server ->
            server.login("kai").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOGS 3")
                client.await("the logs") { if (client.count(LOGS) == 3) Unit else null }
                client.locOp(kind.boulder, 3223, 3224, Traps.SET_TRAP)
                client.seen("[hunter.no_knife]")
                client.command("item $KNIFE 1")
                client.await("the knife") { if (client.count(KNIFE) == 1) Unit else null }
                // At 99 five traps, but two deadfalls at most.
                client.command("setlevel hunter 99")
                client.locOp(kind.boulder, 3223, 3224, Traps.SET_TRAP)
                client.awaitLoc(kind.set, 3223, 3224)
                client.locOp(kind.boulder, 3223, 3212, Traps.SET_TRAP)
                client.awaitLoc(kind.set, 3223, 3212)
                client.locOp(kind.boulder, 3223, 3218, Traps.SET_TRAP)
                client.seen("[hunter.too_many count=2]")
                client.locOp(kind.set, 3223, 3224, Traps.DISMANTLE)
                client.seen("[hunter.dismantled]")
                client.locOp(kind.set, 3223, 3212, Traps.DISMANTLE)
                client.await("both gone") { if (server.world.hunter.traps.all.isEmpty()) Unit else null }
                // Below 23 no deadfall goes up at all.
                client.command("setlevel hunter 1")
                client.await("level 1") { client.stat(HUNTER)?.takeIf { it.level == 1 } }
                client.locOp(kind.boulder, 3223, 3218, Traps.SET_TRAP)
                client.seen("[hunter.trap_level level=23]")
                // At 23 against the barb-tailed kebbit's 33: the one by the kebbit fails and is gone, the boulder back.
                client.command("setlevel hunter 23")
                client.await("level 23") { client.stat(HUNTER)?.takeIf { it.level == 23 } }
                client.locOp(kind.boulder, 3223, 3218, Traps.SET_TRAP)
                client.awaitLoc(kind.set, 3223, 3218)
                client.awaitLoc(kind.failing, 3223, 3218, timeoutMillis = 20_000)
                client.awaitLoc(kind.boulder, 3223, 3218)
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val HUNTER = 21
        const val GROUND = 2
        const val KNIFE = 946
        const val LOGS = 1511
    }
}
