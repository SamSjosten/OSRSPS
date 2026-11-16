package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.SeqNames
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
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
 * mines and empties; a lava dragon breathes dragonfire; and a greater demon and a King Scorpion fought with the real
 * combat and drop files leave only what their tables name: the demon its ashes always, the scorpion nothing but its
 * rare tertiaries.
 */
class HeadlessWilderness36To48Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        517 to "aggressive_black_knight", 3021 to "deadly_red_spider", 2100 to "giant3", 2101 to "giant4", 2102 to "giant5",
        2103 to "giant6", 3018 to "giantspider2", 2026 to "greater_demon2", 2027 to "greater_demon3", 2028 to "greater_demon4",
        2029 to "greater_demon5", 260 to "green_dragon", 261 to "green_dragon2", 262 to "green_dragon3", 263 to "green_dragon4",
        264 to "green_dragon5", 3027 to "kingscorpion", 6593 to "lava_dragon", 2005 to "lesser_demon", 2006 to "lesser_demon2",
        2007 to "lesser_demon3", 2008 to "lesser_demon4", 2018 to "lesser_demon5", 2090 to "mossgiant", 2091 to "mossgiant2",
        2093 to "mossgiant4", 3023 to "poisonspider", 77 to "skeleton_armed", 3019 to "spider", 7995 to "wild_chaos_uncerter", 2841 to "icewarrior", 2085 to "icegiant", 2086 to "icegiant2", 2087 to "icegiant3",
    )

    private val lavaDragon = NpcType(
        LAVA_DRAGON, "Lava dragon", size = 4, ops = listOf(null, "Attack", null, null, null), combatLevel = 252,
        internalName = "lava_dragon", stats = NpcStats(attack = 240, defence = 220, strength = 220, hitpoints = 230, magic = 1),
    )

    private val greaterDemon = NpcType(
        GREATER_DEMON, "Greater demon", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 92,
        internalName = "greater_demon2", stats = NpcStats(attack = 76, defence = 82, strength = 78, hitpoints = 87),
    )

    private val kingScorpion = NpcType(
        KING_SCORPION, "King Scorpion", ops = listOf(null, "Attack", null, null, null), combatLevel = 32,
        internalName = "kingscorpion", stats = NpcStats(attack = 30, defence = 23, strength = 29, hitpoints = 30),
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
        val npcTypes = NpcTypes.of(lavaDragon, greaterDemon, kingScorpion)
        val objTypes = DropTestItems.objTypes(listOfNotNull(*objs))
        World(
            crcs, CollisionMap().apply {
                markLoaded(RUNITE_X shr 6, RUNITE_Z shr 6); markLoaded(DRAGON_X shr 6, DRAGON_Z shr 6)
                markLoaded(DEMON_X shr 6, DEMON_Z shr 6); markLoaded(SCORPION_X shr 6, SCORPION_Z shr 6)
            },
            objTypes = objTypes, random = GameRandom(5),
            locTypes = LocTypes(listOf(rock(RUNITE, "Runite rocks"), rock(Mining.ROCKS1, "Rocks")).associateBy { it.id }),
            staticLocs = StaticLocs().apply { add(ZoneLoc(RUNITE, LocShape.CENTREPIECE_STRAIGHT, 3, RUNITE_X, RUNITE_Z, 0)) },
            npcTypes = npcTypes, npcSpawns = listOf(
                NpcSpawn(lavaDragon, DRAGON_X, DRAGON_Z, source = "test"),
                NpcSpawn(greaterDemon, DEMON_X, DEMON_Z, source = "test"),
                NpcSpawn(kingScorpion, SCORPION_X, SCORPION_Z, source = "test"),
            ),
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes), cycleMillis = millis,
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

    /** The items the drop file's line for [name] can give: its own, and those of every shared table it rolls on. */
    private fun dropItems(name: String): Set<Int> {
        val root = Json.parseToJsonElement(NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()).jsonObject
        val entry = root.getValue("npcs").jsonArray.map { it.jsonObject }
            .single { e -> e.getValue("npcs").jsonArray.any { it.jsonPrimitive.content == name } }
        val tables = root.getValue("tables").jsonObject
        val items = HashSet<Int>()
        fun lines(lines: List<JsonObject>) {
            for (line in lines) {
                line["item"]?.let { items += it.jsonPrimitive.int }
                line["table"]?.let { lines(tables.getValue(it.jsonPrimitive.content).jsonObject.getValue("lines").jsonArray.map { l -> l.jsonObject }) }
            }
        }
        lines(entry["always"]?.jsonArray?.map { it.jsonObject }.orEmpty())
        lines(entry["table"]?.jsonObject?.getValue("lines")?.jsonArray?.map { it.jsonObject }.orEmpty())
        lines(entry["tertiary"]?.jsonArray?.map { it.jsonObject }.orEmpty())
        return items
    }

    @Test
    fun `a greater demon killed leaves its ashes and only what its table names, a King Scorpion only its tertiaries`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("uma").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(46, Wilderness.level(DEMON_X, DEMON_Z))
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure max") { true }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                client.tele(DEMON_X - 1, DEMON_Z)
                val demon = client.await("the greater demon in view") { client.npcs().firstOrNull { it.id == GREATER_DEMON } }
                client.npcOp(demon.index, 2)
                client.await("the greater demon's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == GREATER_DEMON }) Unit else null }
                val ashes = client.await("the vile ashes") { client.groundObjs().firstOrNull { it.id == VILE_ASHES } }
                assertEquals(1, ashes.count)
                val demonTable = dropItems("greater_demon2")
                for (obj in client.groundObjs()) assertTrue(obj.id in demonTable, "${obj.id} is on the greater demon's table")

                client.tele(SCORPION_X - 1, SCORPION_Z)
                val scorpion = client.await("the King Scorpion in view") { client.npcs().firstOrNull { it.id == KING_SCORPION } }
                client.npcOp(scorpion.index, 2)
                client.await("the King Scorpion's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == KING_SCORPION }) Unit else null }
                // Its respawn (25 ticks) comes after its drops land.
                client.await("the King Scorpion's respawn", timeoutMillis = 60_000) { client.npcs().firstOrNull { it.id == KING_SCORPION } }
                val scorpionTable = dropItems("kingscorpion")
                val near = client.groundObjs().filter { kotlin.math.abs(it.coord.x - SCORPION_X) <= 8 && kotlin.math.abs(it.coord.z - SCORPION_Z) <= 8 }
                for (obj in near) assertTrue(obj.id in scorpionTable, "${obj.id} is one of the King Scorpion's tertiaries")
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

        /** A greater demon's spawn in the Demonic Ruins (level 46) and a King Scorpion's in the Lava Maze (level 39). */
        const val GREATER_DEMON = 2026
        const val DEMON_X = 3304
        const val DEMON_Z = 3886
        const val KING_SCORPION = 3027
        const val SCORPION_X = 3070
        const val SCORPION_Z = 3829
        const val VILE_ASHES = 25769
    }
}
