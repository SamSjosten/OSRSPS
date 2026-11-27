package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.CorporealBeast
import osrsps.game.content.CorporealBeastLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W4.15 through the headless client: the cave in the Wilderness down to the lair, the Passage both
 * ways and its Peek, and the Cave exit back out; the Corporeal Beast's swipe, its three spells and its stomp; its
 * healing with no one in its room; its armour halving all but magic and corpbane stabs; and its loot and kill count
 * going to its top damage dealer, not whoever struck last. The rl-client script test-runner/examples/corporeal-beast.json
 * does the way in and the fight on the real map.
 */
class HeadlessCorporealBeastTest {
    private val boss = NpcType(
        CorporealBeast.BOSS, "Corporeal Beast", size = 5, ops = listOf(null, "Attack", null, null, null), combatLevel = 785, internalName = "corp_beast",
        stats = NpcStats(attack = 320, strength = 320, defence = 310, hitpoints = 2000, magic = 350, ranged = 150),
    )
    private val types = NpcTypes.of(boss)

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        val cave = LocType(CorporealBeast.ENTRANCE, "Cave", listOf("Enter", null, null, null, null), LocCollisionType(width = 5, length = 5))
        val exit = LocType(CorporealBeast.EXIT, "Cave exit", listOf("Exit", null, null, null, null), LocCollisionType())
        val passage = LocType(CorporealBeast.PASSAGE, "Passage", listOf("Go-through", "Peek", null, null, null), LocCollisionType(width = 5, length = 3))
        World(
            crcs, CollisionMap().apply { markLoaded(46, 66); markLoaded(50, 57) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed),
            npcSpawns = if (spawn) listOf(NpcSpawn(boss, CorporealBeast.SPAWN.x, CorporealBeast.SPAWN.z, CorporealBeast.LAIR_LEVEL, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(CorporealBeast.ENTRANCE to cave, CorporealBeast.EXIT to exit, CorporealBeast.PASSAGE to passage)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(CorporealBeast.ENTRANCE, 10, 3, 3201, 3679, 0))
                add(ZoneLoc(CorporealBeast.EXIT, 0, 2, 2963, 4254, CorporealBeast.LAIR_LEVEL))
                add(ZoneLoc(CorporealBeast.PASSAGE, 10, 1, 2971, 4254, CorporealBeast.LAIR_LEVEL))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    @Test
    fun `the cave leads to the lair, the Passage goes both ways, and the exit leads back out`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3202, 3678, 0)
                client.locOp(CorporealBeast.ENTRANCE, 3201, 3679, CorporealBeast.ENTER)
                val level = CorporealBeast.LAIR_LEVEL
                client.await("in the lair", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(CorporealBeast.ARRIVAL.x, CorporealBeast.ARRIVAL.z, level) } }

                client.tele(2969, 4256, level)
                client.locOp(CorporealBeast.PASSAGE, 2971, 4254, CorporealBeast.PEEK)
                client.await("the peek") { client.messages().firstOrNull { it.text == "[${CorporealBeastLines.PEEK.key} count=0]" } }
                client.locOp(CorporealBeast.PASSAGE, 2971, 4254, CorporealBeast.GO_THROUGH)
                client.await("through the Passage", timeoutMillis = 15_000) { client.position?.takeIf { it.x == CorporealBeast.PASSAGE_EAST_X && it.z in CorporealBeast.PASSAGE_ROWS } }
                client.locOp(CorporealBeast.PASSAGE, 2971, 4254, CorporealBeast.GO_THROUGH)
                client.await("back through", timeoutMillis = 15_000) { client.position?.takeIf { it.x == CorporealBeast.PASSAGE_WEST_X && it.z in CorporealBeast.PASSAGE_ROWS } }

                client.tele(2965, 4254, level)
                client.locOp(CorporealBeast.EXIT, 2963, 4254, 1)
                client.await("outside", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(CorporealBeast.OUTSIDE.x, CorporealBeast.OUTSIDE.z, 0) } }
            }
        }
    }

    @Test
    fun `it swipes, casts its three spells and stomps on whoever stands under it`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                val level = CorporealBeast.LAIR_LEVEL
                client.tele(CorporealBeast.SPAWN.x + 2, CorporealBeast.SPAWN.z + 5, level)
                val npc = server.world.npcs.all.first { it.type.id == CorporealBeast.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Corporeal Beast") }
                val fight = client.await("its fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                for (said in listOf(" swipes at bo", " casts at bo", " drains at bo", " splash on bo for ")) {
                    client.await(said.trim(), timeoutMillis = 60_000) { fight.lines.firstOrNull { said in it } }
                }
                // It follows bo about, so bo goes back under it until it stomps.
                var stomp: String? = null
                repeat(10) {
                    if (stomp != null) return@repeat
                    client.command("tele ${npc.x + 2} ${npc.z + 2} $level")
                    client.keepAlive()
                    stomp = runCatching { client.await("the stomp", timeoutMillis = 3_000) { fight.lines.firstOrNull { " stomps on bo for " in it } } }.getOrNull()
                }
                assertTrue(stomp != null, "no stomp: ${fight.lines.toList().takeLast(20)}")
            }
        }
    }

    @Test
    fun `with no one in its room it heals, more each time`(@TempDir dir: Path) {
        server(dir, seed = 2).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                val level = CorporealBeast.LAIR_LEVEL
                client.tele(CorporealBeast.SPAWN.x + 2, CorporealBeast.SPAWN.z + 5, level)
                val npc = server.world.npcs.all.first { it.type.id == CorporealBeast.BOSS }
                client.commandReply("hit 500 ${npc.index}") { it.startsWith("Hit Corporeal Beast") }
                val fight = client.await("its fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.tele(CorporealBeast.ARRIVAL.x, CorporealBeast.ARRIVAL.z, level)
                client.keepAlive()
                val heals = client.await("two heals", timeoutMillis = 30_000) {
                    fight.lines.toList().mapNotNull { Regex("heals (\\d+)$").find(it)?.groupValues?.get(1)?.toInt() }.takeIf { it.size >= 2 }
                }
                assertTrue(heals[0] >= CorporealBeast.EMPTY_HEAL, "$heals")
                assertEquals(heals[0] + CorporealBeast.EMPTY_HEAL_STEP, heals[1], "$heals")
                assertTrue(npc.hitpoints > 1500)
            }
        }
    }

    @Test
    fun `its armour halves all but magic and a corpbane weapon's stab`() {
        val spear = ObjType(1249, "Dragon spear", params = mapOf(CorporealBeast.PARAM_CORPBANE to 1))
        val whip = ObjType(4151, "Abyssal whip")
        assertEquals(40, CorporealBeast.cut(spear, 40, AttackType.STAB))
        assertEquals(20, CorporealBeast.cut(spear, 40, AttackType.SLASH))
        assertEquals(20, CorporealBeast.cut(whip, 40, AttackType.STAB))
        assertEquals(20, CorporealBeast.cut(null, 40, AttackType.RANGED))
        assertEquals(40, CorporealBeast.cut(whip, 40, AttackType.MAGIC))
    }

    @Test
    fun `its armour holds for a fresh spawn, before any fight tick`(@TempDir dir: Path) {
        server(dir, seed = 5, spawn = false).use { server ->
            server.login("fi").use { client ->
                client.await("the login rebuild") { client.position }
                val fi = server.world.playerNamed("fi")!!
                // A respawn is a new Npc, nothing set on it yet.
                val fresh = osrsps.game.npc.Npc(1, NpcSpawn(boss, CorporealBeast.SPAWN.x, CorporealBeast.SPAWN.z, CorporealBeast.LAIR_LEVEL, wander = 0, source = "test"))
                assertEquals(20, server.world.meleeCombat.armoured(fresh, 40, magic = false, player = fi, type = AttackType.SLASH))
                assertEquals(40, server.world.meleeCombat.armoured(fresh, 40, magic = true, player = fi, type = AttackType.MAGIC))
            }
        }
    }

    @Test
    fun `its kill count and loot go to its top damage dealer, not whoever struck last`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("di").use { di ->
                server.login("ed").use { ed ->
                    di.await("di's login") { di.position }
                    ed.await("ed's login") { ed.position }
                    val level = CorporealBeast.LAIR_LEVEL
                    di.commandReply("god") { it.startsWith("You can") }
                    ed.commandReply("god") { it.startsWith("You can") }
                    di.tele(CorporealBeast.SPAWN.x + 2, CorporealBeast.SPAWN.z + 5, level)
                    ed.tele(CorporealBeast.SPAWN.x + 3, CorporealBeast.SPAWN.z + 5, level)
                    val npc = server.world.npcs.all.first { it.type.id == CorporealBeast.BOSS }
                    di.commandReply("hit 1200 ${npc.index}") { it.startsWith("Hit Corporeal Beast") }
                    ed.commandReply("hit 800 ${npc.index}") { it.startsWith("Hit Corporeal Beast") }
                    val diPlayer = server.world.playerNamed("di")!!
                    val edPlayer = server.world.playerNamed("ed")!!
                    di.await("di's kill count") { Unit.takeIf { diPlayer.vars[CorporealBeast.KILL_COUNT_VARP] == 1 } }
                    assertEquals(0, edPlayer.vars[CorporealBeast.KILL_COUNT_VARP], "nothing for the last hit")
                    val ashes = di.await("its ashes, di's") { server.world.groundObjs.visibleAt(diPlayer, npc.x, npc.z, npc.level).firstOrNull { it.id == ASHES } }
                    assertEquals(1, ashes.count, "always ashes")
                }
            }
        }
    }

    private companion object {
        const val ASHES = 592
    }
}
