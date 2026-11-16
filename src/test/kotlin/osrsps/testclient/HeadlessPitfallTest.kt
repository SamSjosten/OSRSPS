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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.Deadfalls
import osrsps.game.skill.hunter.Pitfalls
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Hunter step 5 through the headless client: a spined larupia pit north of the player (2 by 2 from 3222, 3220), trapped
 * with a knife and logs; the larupia south of the player teased with a teasing stick, chasing; the player jumping the
 * spiked pit northwards, the larupia following and falling in; Dismantle for its drops and XP. Then the level and
 * teasing stick checks. The pit's look is the player's own varbit, read off their varps. The player logs in at 3222, 3218.
 */
class HeadlessPitfallTest {
    private val larupia = NpcType(Pitfalls.SPINED_LARUPIA, "Spined larupia", size = 2, ops = listOf("Tease", null, null, null, null), internalName = "hunting_jaguar")
    private val pit = Pitfalls.PITS.getValue(PIT)

    private val vars = VarTypes(6_000, mapOf(pit.varbit to VarbitType(PIT_VARP, 0, 2)))

    private fun server(
        dir: Path, larupiaX: Int, larupiaZ: Int, others: List<Pair<Int, Int>> = emptyList(), blocked: List<Pair<Int, Int>> = emptyList(),
    ) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(35_000)
        for (id in listOf(KNIFE, LOGS, Pitfalls.TEASING_STICK, Pitfalls.BIG_BONES, RAW_LARUPIA, LARUPIA_FUR, TATTY_LARUPIA_FUR)) {
            objs[id] = ObjType(id, "Obj $id", stackable = false, tradeable = true)
        }
        objs[Deadfalls.FLETCHING_KNIFE] = ObjType(
            Deadfalls.FLETCHING_KNIFE, "Fletching knife", stackable = false, tradeable = false,
            iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = osrsps.game.WearPos.SHIELD,
        )
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                for ((x, z) in blocked) add(x, z, 0, osrsps.map.CollisionFlag.LOC)
            },
            objTypes = ObjTypes(objs),
            random = GameRandom(7),
            locTypes = LocTypes(mapOf(PIT to LocType(PIT, "null", List(5) { null }, LocCollisionType(width = 2, length = 2, blocksProjectile = false), multiloc = true, multiVarbit = pit.varbit))),
            npcTypes = NpcTypes.of(larupia),
            npcSpawns = (listOf(larupiaX to larupiaZ) + others).map { (x, z) -> NpcSpawn(larupia, x, z, wander = 0, source = "test") },
            staticLocs = StaticLocs().apply { add(ZoneLoc(PIT, LocShape.CENTREPIECE_STRAIGHT, 0, 3222, 3220, 0)) },
            varTypes = vars,
            scenarios = osrsps.game.Scenarios(listOf(osrsps.game.Scenario.parse("here", """{ "position": { "x": 3222, "z": 3218 } }"""))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.awaitPit(value: Int, timeoutMillis: Long = 10_000) =
        await("the pit at $value", timeoutMillis) { if (varp(PIT_VARP) and 7 == value) Unit else null }

    @Test
    fun `a teased larupia follows the player over the spiked pit and falls in, and Dismantle gives its drops`(@TempDir dir: Path) {
        server(dir, 3222, 3213).use { server ->
            server.login("lu").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item $KNIFE 1")
                client.command("item $LOGS 1")
                client.command("item ${Pitfalls.TEASING_STICK} 1")
                client.await("the stick") { if (client.count(Pitfalls.TEASING_STICK) == 1) Unit else null }
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                client.locOp(PIT, 3222, 3220, Pitfalls.TRAP)
                client.seen("[hunter.laying]")
                client.awaitPit(Pitfalls.SET)
                // The backpack's update may come a packet after the varbit's.
                client.await("the log used") { if (client.count(LOGS) == 0) Unit else null }
                val cat = client.await("the larupia in view") { client.npcs().firstOrNull { it.id == Pitfalls.SPINED_LARUPIA } }
                client.npcOp(cat.index, Pitfalls.TEASE)
                client.await("the tease") { server.world.hunter.pitfalls.teased.firstOrNull() }
                // From the south edge over to the north: the larupia, chasing, comes to the south edge and falls in.
                client.locOp(PIT, 3222, 3220, Pitfalls.JUMP)
                client.await("over the pit", timeoutMillis = 15_000) { client.position?.takeIf { it.z == 3222 } }
                client.awaitPit(Pitfalls.CATCHING, timeoutMillis = 20_000)
                // The client sees it fall in before it goes.
                client.await("the fall") {
                    client.actorEvents().firstOrNull { it.npc && it.index == cat.index && it.kind == ActorEventKind.ANIMATION && it.id == Pitfalls.FALL_ANIM }
                }
                client.awaitPit(Pitfalls.FULL)
                assertTrue(server.world.hunter.pitfalls.teased.isEmpty())
                client.locOp(PIT, 3222, 3220, Pitfalls.DISMANTLE)
                client.seen("[hunter.caught creature=spined larupia]")
                client.await("180 XP") { if (client.stat(HUNTER)!!.xp - before == 180) Unit else null }
                client.await("the drops") { if (client.count(RAW_LARUPIA) == 1) Unit else null }
                assertEquals(1, client.count(Pitfalls.BIG_BONES))
                assertEquals(1, client.count(LARUPIA_FUR) + client.count(TATTY_LARUPIA_FUR))
                client.awaitPit(Pitfalls.EMPTY)
                assertTrue(server.world.hunter.pitfalls.all.isEmpty())
            }
        }
    }

    @Test
    fun `Jump refuses a pit whose far side is blocked, and the player stays put`(@TempDir dir: Path) {
        // Exploit review round 36, B-1: no landing on a loc's tile.
        server(dir, 3230, 3230, blocked = listOf(3222 to 3222)).use { server ->
            server.login("lb").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item $KNIFE 1")
                client.command("item $LOGS 1")
                client.await("the logs") { if (client.count(LOGS) == 1) Unit else null }
                client.locOp(PIT, 3222, 3220, Pitfalls.TRAP)
                client.awaitPit(Pitfalls.SET)
                client.locOp(PIT, 3222, 3220, Pitfalls.JUMP)
                client.seen("[game.cant_reach]")
                val here = client.await("standing") { client.position }
                assertTrue(here.z < 3220, "still south of the pit: $here")
            }
        }
    }

    @Test
    fun `the creature's level is needed to trap and tease, and a teasing stick to tease`(@TempDir dir: Path) {
        server(dir, 3226, 3213).use { server ->
            server.login("mo").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 30")
                client.command("item $KNIFE 1")
                client.command("item $LOGS 1")
                client.await("the logs") { if (client.count(LOGS) == 1) Unit else null }
                client.locOp(PIT, 3222, 3220, Pitfalls.TRAP)
                client.seen("[hunter.pit_level level=31]")
                val cat = client.await("the larupia in view") { client.npcs().firstOrNull { it.id == Pitfalls.SPINED_LARUPIA } }
                client.npcOp(cat.index, Pitfalls.TEASE)
                client.seen("[hunter.no_teasing_stick]")
                client.command("item ${Pitfalls.TEASING_STICK} 1")
                client.await("the stick") { if (client.count(Pitfalls.TEASING_STICK) == 1) Unit else null }
                client.npcOp(cat.index, Pitfalls.TEASE)
                client.seen("[hunter.tease_level level=31]")
                client.command("setlevel hunter 31")
                client.await("level 31") { client.stat(HUNTER)?.takeIf { it.level == 31 } }
                client.locOp(PIT, 3222, 3220, Pitfalls.TRAP)
                client.awaitPit(Pitfalls.SET)
                client.locOp(PIT, 3222, 3220, Pitfalls.DISMANTLE)
                client.seen("[hunter.dismantled]")
                client.awaitPit(Pitfalls.EMPTY)
                // Nothing back for a spiked pit taken down.
                assertEquals(0, client.count(LOGS))
                assertTrue(server.world.hunter.pitfalls.all.isEmpty())
            }
        }
    }

    @Test
    fun `teasing a second creature ends the first tease, and the first stops following`(@TempDir dir: Path) {
        server(dir, 3220, 3214, others = listOf(3224 to 3214)).use { server ->
            server.login("vi").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item ${Pitfalls.TEASING_STICK} 1")
                client.await("the stick") { if (client.count(Pitfalls.TEASING_STICK) == 1) Unit else null }
                val cats = client.await("both larupias in view") { client.npcs().filter { it.id == Pitfalls.SPINED_LARUPIA }.takeIf { it.size == 2 } }
                client.npcOp(cats[0].index, Pitfalls.TEASE)
                val first = client.await("the first tease") { server.world.hunter.pitfalls.teased.firstOrNull()?.npc }
                client.npcOp(cats[1].index, Pitfalls.TEASE)
                client.await("the second tease") { server.world.hunter.pitfalls.teased.singleOrNull()?.takeIf { it.npc !== first } }
                client.await("the first stops following") { if (first.following == null) Unit else null }
            }
        }
    }

    @Test
    fun `a scenario takes down the player's spiked pit, as it does their other traps`(@TempDir dir: Path) {
        server(dir, 3226, 3213).use { server ->
            server.login("lu").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 31")
                client.command("item $KNIFE 1")
                client.command("item $LOGS 1")
                client.await("the logs") { if (client.count(LOGS) == 1) Unit else null }
                client.locOp(PIT, 3222, 3220, Pitfalls.TRAP)
                client.awaitPit(Pitfalls.SET)
                // The scenario puts the player back on the same tile, in range of the pit: only the takedown clears it.
                client.command("scenario here")
                client.await("the scenario") { client.messages().firstOrNull { it.text.startsWith("Started scenario here.") } }
                client.awaitPit(Pitfalls.EMPTY)
            }
        }
    }

    @Test
    fun `a fletching knife worn in the off-hand sets a pit, as one in the backpack does`(@TempDir dir: Path) {
        server(dir, 3226, 3213).use { server ->
            server.login("fi").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 31")
                client.command("item ${Deadfalls.FLETCHING_KNIFE} 1")
                client.command("item $LOGS 1")
                client.await("the logs") { if (client.count(LOGS) == 1) Unit else null }
                val slot = client.await("the knife") { client.inventory(BACKPACK).indexOfFirst { it?.id == Deadfalls.FLETCHING_KNIFE }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WIELD_BUTTON, slot, Deadfalls.FLETCHING_KNIFE)
                client.await("the knife worn") { client.inventory(WORN).getOrNull(osrsps.game.WearPos.SHIELD)?.takeIf { it.id == Deadfalls.FLETCHING_KNIFE } }
                client.locOp(PIT, 3222, 3220, Pitfalls.TRAP)
                client.awaitPit(Pitfalls.SET)
            }
        }
    }

    private companion object {
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WIELD_BUTTON = 3
        const val WORN = 94
        const val BACKPACK = 93
        const val HUNTER = 21
        const val PIT = 19259 // hunting_pitfall_7, a larupia pit
        const val PIT_VARP = 5_000
        const val KNIFE = 946
        const val LOGS = 1511
        const val RAW_LARUPIA = 29122
        const val LARUPIA_FUR = 10095
        const val TATTY_LARUPIA_FUR = 10093
    }
}
