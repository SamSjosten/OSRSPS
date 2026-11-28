package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.MultiCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.WildernessSlayerCave
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.skill.AgilityData
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerTask
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W6.2 through the headless client (test-runner/examples/wilderness-slayer-cave.json does the ways in, a
 * crevice, Peeve and a fight on the real map): the Wilderness Slayer Cave's spawn file loads every NPC in it, in
 * levels 17 to 32 and multi-way; its monsters fight and drop as the Wiki has them, with the cave's own table; both
 * stairs lead down and both openings back up; a crevice crosses at Agility 77; and Peeve stops an attack on an abyssal
 * demon off task but not on one.
 */
class HeadlessWildernessSlayerCaveTest {
    private val names = mapOf(
        11239 to "wild_cave_abyssal", 7864 to "wild_cave_ankou", 6605 to "wilderness_bandit",
        7874 to "wild_cave_black_demon", 7875 to "wild_cave_black_demon2", 7876 to "wild_cave_black_demon3",
        7861 to "wild_cave_black_dragon", 7862 to "wild_cave_black_dragon2", 7863 to "wild_cave_black_dragon3",
        11238 to "wild_cave_dustdevil", 11240 to "wild_cave_nechryael",
        7871 to "wild_cave_greater_demon", 7872 to "wild_cave_greater_demon2", 7873 to "wild_cave_greater_demon3",
        7868 to "wild_cave_green_dragon", 7869 to "wild_cave_green_dragon2", 7870 to "wild_cave_green_dragon3",
        7877 to "wild_cave_hell_hound", 7878 to "wild_cave_icegiant", 7879 to "wild_cave_icegiant2", 7880 to "wild_cave_icegiant3",
        11241 to "wild_cave_jelly_1", 11242 to "wild_cave_jelly_2", 11243 to "wild_cave_jelly_3", 11244 to "wild_cave_jelly_4", 11245 to "wild_cave_jelly_5",
        7865 to "wild_cave_lesser_demon", 7866 to "wild_cave_lesser_demon2", 7867 to "wild_cave_lesser_demon3",
        WildernessSlayerCave.PEEVE to "wild_cave_inactive_ghost_small_roam2",
    )
    private val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())

    @Test
    fun `the spawn file has the Wiki's monsters and Peeve, all in the cave, which is multi-way`() {
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-slayer-cave.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-slayer-cave.json")
        assertEquals(emptyList(), parsed.unknown)
        val multi = MultiCombat.load()
        for (spawn in parsed.spawns) {
            val at = "${spawn.type.name} at ${spawn.x},${spawn.z}"
            assertTrue(WildernessSlayerCave.inCave(spawn.x, spawn.z) && spawn.level == 0, at)
            assertTrue(Wilderness.level(spawn.x, spawn.z) in 17..32, "$at: level ${Wilderness.level(spawn.x, spawn.z)}")
            assertTrue(multi.at(0, spawn.x, spawn.z), "$at is multi-way")
        }
        val counts = parsed.spawns.groupingBy { it.type.id }.eachCount()
        assertEquals(1, counts[WildernessSlayerCave.PEEVE], "one Peeve")
        assertEquals(8, counts[11239], "abyssal demons")
        assertEquals(9, counts[11238], "dust devils")
        assertEquals(8, counts[11240], "Greater Nechryaels")
        assertEquals(14, (11241..11245).sumOf { counts[it] ?: 0 }, "jellies")
        assertEquals(110, parsed.spawns.size)
    }

    @Test
    fun `its monsters fight and drop as the Wiki has them, with the cave's table beside`() {
        val combat = NpcCombat.load(npcTypes, SeqNames.NONE)
        assertEquals(8, combat[11239]!!.maxHit, "abyssal demon")
        for ((id, level) in mapOf(11239 to 85, 11238 to 65, 11240 to 80, 11241 to 52)) assertEquals(level, combat[id]!!.slayerLevel, "NPC $id")
        val drops = NpcDrops.load(npcTypes, DropTestItems.objTypes())
        val abyssal = drops[11239]!!
        assertEquals(listOf(4151 to 512, 13265 to 32000), abyssal.preroll.map { it.item.type.id to it.denominator }, "the whip and dagger")
        assertEquals(80, abyssal.minor!!.lines.first { it.table != null }.weight, "the cave's table 80 kills in 100")
        for (id in names.keys - WildernessSlayerCave.PEEVE - 6605) assertTrue(drops[id]?.minor != null, "NPC $id rolls the cave's table")
    }

    private val agility = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private val abyssal = NpcType(
        11239, "Abyssal demon", ops = listOf(null, "Attack", null, null, null), combatLevel = 124, internalName = "wild_cave_abyssal",
        params = mapOf(Slayer.PARAM_CATEGORY to ABYSSAL_TASK), stats = NpcStats(attack = 97, strength = 67, defence = 135, hitpoints = 150),
    )
    private val peeve = NpcType(WildernessSlayerCave.PEEVE, "null", internalName = "wild_cave_inactive_ghost_small_roam2")
    private val abyssals = SlayerTask(0, ABYSSAL_TASK, "abyssal demons", "Abyssal demons")

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList()) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val types = NpcTypes.of(abyssal, peeve)
        val loc = { id: Int, name: String, op: String -> LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) }
        World(
            crcs, CollisionMap().apply { markLoaded(50, 57); markLoaded(51, 58); for (sx in 52..53) for (sz in 157..158) markLoaded(sx, sz) },
            npcTypes = types, random = GameRandom(3), npcSpawns = spawns, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            varTypes = VarTypes(6_000, mapOf(Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22), Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13))),
            slayerData = SlayerData(listOf(abyssals), emptyList()),
            locTypes = LocTypes(
                mapOf(
                    WildernessSlayerCave.SOUTH_STAIRS to loc(WildernessSlayerCave.SOUTH_STAIRS, "Stairs", "Walk-down"),
                    WildernessSlayerCave.NORTH_STAIRS to loc(WildernessSlayerCave.NORTH_STAIRS, "Stairs", "Walk-down"),
                    WildernessSlayerCave.SOUTH_EXIT to loc(WildernessSlayerCave.SOUTH_EXIT, "Opening", "Exit"),
                    WildernessSlayerCave.NORTH_EXIT to loc(WildernessSlayerCave.NORTH_EXIT, "Opening", "Exit"),
                    CREVICE to loc(CREVICE, "Crevice", "Squeeze-Through"),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(WildernessSlayerCave.SOUTH_STAIRS, 10, 0, 3259, 3664, 0))
                add(ZoneLoc(WildernessSlayerCave.SOUTH_EXIT, 10, 0, 3384, 10050, 0))
                add(ZoneLoc(WildernessSlayerCave.NORTH_STAIRS, 10, 0, 3293, 3746, 0))
                add(ZoneLoc(WildernessSlayerCave.NORTH_EXIT, 10, 0, 3405, 10146, 0))
                add(ZoneLoc(CREVICE, 0, 3, 3434, 10091, 0))
                add(ZoneLoc(CREVICE, 0, 1, 3434, 10093, 0))
            },
            agilityData = AgilityData.parse(agility), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.arrive(x: Int, z: Int, what: String) =
        await(what, timeoutMillis = 15_000) { position?.takeIf { it == Coord(x, z, 0) } }

    @Test
    fun `both stairs lead down and both openings up, and a crevice crosses at Agility 77`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(3259, 3663)
                client.locOp(WildernessSlayerCave.SOUTH_STAIRS, 3259, 3664, 1)
                client.arrive(WildernessSlayerCave.SOUTH_ARRIVAL.x, WildernessSlayerCave.SOUTH_ARRIVAL.z, "in by the south stairs")
                client.awaitTicks(2)
                client.locOp(WildernessSlayerCave.SOUTH_EXIT, 3384, 10050, 1)
                client.arrive(WildernessSlayerCave.SOUTH_OUT.x, WildernessSlayerCave.SOUTH_OUT.z, "out by the south opening")
                client.awaitTicks(2)
                client.tele(3294, 3747)
                client.locOp(WildernessSlayerCave.NORTH_STAIRS, 3293, 3746, 1)
                client.arrive(WildernessSlayerCave.NORTH_ARRIVAL.x, WildernessSlayerCave.NORTH_ARRIVAL.z, "in by the north stairs")
                client.awaitTicks(2)
                client.locOp(WildernessSlayerCave.NORTH_EXIT, 3405, 10146, 1)
                client.arrive(WildernessSlayerCave.NORTH_OUT.x, WildernessSlayerCave.NORTH_OUT.z, "out by the north opening")
                client.awaitTicks(2)

                client.command("setlevel agility 76")
                client.await("level 76") { client.stat(AGILITY)?.takeIf { it.level == 76 } }
                client.tele(3434, 10090)
                client.locOp(CREVICE, 3434, 10091, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.level level=77]" } }
                assertEquals(Coord(3434, 10090, 0), client.position)
                client.command("setlevel agility 77")
                client.await("level 77") { client.stat(AGILITY)?.takeIf { it.level == 77 } }
                val xp = client.stat(AGILITY)!!.xp
                client.locOp(CREVICE, 3434, 10091, 1)
                client.arrive(3434, 10094, "through to the north")
                client.await("10 XP") { client.stat(AGILITY)?.takeIf { it.xp == xp + 10 } }
                client.awaitTicks(2)
                client.locOp(CREVICE, 3434, 10093, 1)
                client.arrive(3434, 10090, "back to the south")
            }
        }
    }

    @Test
    fun `Peeve stops an attack on an abyssal demon off task, but not on one`(@TempDir dir: Path) {
        val spawns = listOf(NpcSpawn(abyssal, 3400, 10100, wander = 0, source = "test"), NpcSpawn(peeve, 3404, 10100, wander = 0, source = "test"))
        server(dir, spawns).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel slayer 99") { true }
                client.commandReply("setlevel hitpoints 99") { true }
                client.tele(3401, 10101)
                val demon = client.await("the demon in view") { client.npcs().firstOrNull { it.id == abyssal.id } }
                client.npcOp(demon.index, 2)
                val chat = client.awaitDialogue("Peeve's woo") { it is DialogueView.Chat && it.text == "[peeve.woo]" }
                assertTrue(chat is DialogueView.Chat)
                client.continueDialogue()
                client.awaitTicks(3)
                assertTrue(client.npcHits().none { it.index == demon.index }, "no hit off task")

                client.commandReply("slayer task abyssal demons 10") { it.startsWith("Task") }
                client.npcOp(demon.index, 2)
                client.await("a hit on the demon", timeoutMillis = 15_000) { client.npcHits().firstOrNull { it.index == demon.index } }
                assertNull(client.dialogueView)
            }
        }
    }

    private companion object {
        const val CREVICE = 53259
        const val AGILITY = 16
        const val ABYSSAL_TASK = 42
    }
}
