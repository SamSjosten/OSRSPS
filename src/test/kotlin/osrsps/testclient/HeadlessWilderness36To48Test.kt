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
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.4 (the surface) through the headless client: the spawn file for levels 36 to 48 loads every NPC in
 * it and a client visiting each map square sees that square's NPCs; the Lava Maze's runite rock north of the maze
 * mines and empties; and a lava dragon breathes dragonfire.
 */
class HeadlessWilderness36To48Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        517 to "aggressive_black_knight", 3021 to "deadly_red_spider", 2100 to "giant3", 2101 to "giant4", 2102 to "giant5",
        2103 to "giant6", 3018 to "giantspider2", 2026 to "greater_demon2", 2027 to "greater_demon3", 2028 to "greater_demon4",
        2029 to "greater_demon5", 260 to "green_dragon", 261 to "green_dragon2", 262 to "green_dragon3", 263 to "green_dragon4",
        264 to "green_dragon5", 3027 to "kingscorpion", 6593 to "lava_dragon", 2005 to "lesser_demon", 2006 to "lesser_demon2",
        2007 to "lesser_demon3", 2008 to "lesser_demon4", 2018 to "lesser_demon5", 2090 to "mossgiant", 2091 to "mossgiant2",
        2093 to "mossgiant4", 3023 to "poisonspider", 77 to "skeleton_armed", 3019 to "spider",
    )

    private val lavaDragon = NpcType(
        LAVA_DRAGON, "Lava dragon", size = 4, ops = listOf(null, "Attack", null, null, null), combatLevel = 252,
        internalName = "lava_dragon", stats = NpcStats(attack = 240, defence = 220, strength = 220, hitpoints = 230, magic = 1),
    )

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    @Test
    fun `every map square of levels 36 to 48 has the spawn file's NPCs`(@TempDir dir: Path) {
        val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-36-48.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-36-48.json")
        assertEquals(emptyList(), parsed.unknown)
        assertTrue(parsed.spawns.all { Wilderness.level(it.x, it.z) in 36..48 && it.level == 0 }, "all on the surface in levels 36 to 48")
        assertEquals(8, parsed.spawns.count { it.type.internalName == "lava_dragon" }, "Lava Dragon Isle's eight (the Wiki's Lava Dragon Isle)")
        assertEquals(4, parsed.spawns.count { it.type.internalName.orEmpty().startsWith("greater_demon") }, "the Demonic Ruins' greater demons")
        assertEquals(4, parsed.spawns.count { it.type.internalName == "kingscorpion" })
        val squares = parsed.spawns.groupBy { Triple(it.x shr 6, it.z shr 6, it.level) }
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { for ((mx, mz) in squares.keys.map { it.first to it.second }.toSet()) markLoaded(mx, mz) },
                random = GameRandom(3), npcTypes = npcTypes, npcSpawns = parsed.spawns, cycleMillis = millis,
            )
        }.use { server ->
            server.login("rue").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                client.await("the spawns loaded") { Unit.takeIf { world.npcs.all.count() == parsed.spawns.size } }
                client.commandReply("god") { it.startsWith("You can") }
                for ((square, spawns) in squares) {
                    val first = spawns.first()
                    client.tele(first.x, first.z, first.level)
                    client.await("${first.type.internalName} in view in map square $square") {
                        client.npcs().firstOrNull { it.id == first.type.id }
                    }
                }
            }
        }
    }

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(RUNE_PICKAXE + 1)
        objs[RUNITE_ORE] = ObjType(RUNITE_ORE, "Runite ore")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        val mine = listOf("Mine", null, null, null, null)
        fun rock(id: Int, name: String) = LocType(id, name, mine, LocCollisionType(width = 1, length = 1))
        val npcTypes = NpcTypes.of(lavaDragon)
        World(
            crcs, CollisionMap().apply { markLoaded(RUNITE_X shr 6, RUNITE_Z shr 6); markLoaded(DRAGON_X shr 6, DRAGON_Z shr 6) },
            objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(listOf(rock(RUNITE, "Runite rocks"), rock(Mining.ROCKS1, "Rocks")).associateBy { it.id }),
            staticLocs = StaticLocs().apply { add(ZoneLoc(RUNITE, LocShape.CENTREPIECE_STRAIGHT, 3, RUNITE_X, RUNITE_Z, 0)) },
            npcTypes = npcTypes, npcSpawns = listOf(NpcSpawn(lavaDragon, DRAGON_X, DRAGON_Z, source = "test")),
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    @Test
    fun `the Lava Maze's runite rock gives runite ore and empties`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("sef").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                assertEquals(46, Wilderness.level(RUNITE_X, RUNITE_Z))
                client.command("item $RUNE_PICKAXE")
                client.command("setlevel mining 99")
                client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }
                client.tele(RUNITE_X, RUNITE_Z - 1)
                client.locOp(RUNITE, RUNITE_X, RUNITE_Z, 1)
                client.await("runite ore", timeoutMillis = 60_000) { if (client.count(RUNITE_ORE) == 1) Unit else null }
                client.await("the empty rock") {
                    client.locChanges().lastOrNull { it.coord == Coord(RUNITE_X, RUNITE_Z, 0) }?.takeIf { it.id == Mining.ROCKS1 }
                }
            }
        }
    }

    @Test
    fun `a lava dragon breathes dragonfire`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("tam").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                // God first: a lava dragon's first breath can kill a new account on arrival.
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(DRAGON_X - 1, DRAGON_Z)
                val npc = client.await("the dragon in view") { client.npcs().firstOrNull { it.id == LAVA_DRAGON } }
                client.npcOp(npc.index, 2)
                client.awaitMessage("a breath", timeoutMillis = 60_000) { it == "[dragonfire.burnt]" || it == "[dragonfire.resisted]" }
            }
        }
    }

    private companion object {
        /** runiterock1, as the 242 map places it north of the Lava Maze. */
        const val RUNITE = 11376
        const val RUNITE_X = 3059
        const val RUNITE_Z = 3885
        const val RUNITE_ORE = 451
        const val RUNE_PICKAXE = 1275
        const val BACKPACK = 93
        const val MINING = 14
        const val LAVA_DRAGON = 6593
        const val DRAGON_X = 3210
        const val DRAGON_Z = 3830
    }
}
