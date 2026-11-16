package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
import osrsps.game.SeqNames
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.NpcSpawnFile
import osrsps.map.CollisionMap

/**
 * Wilderness step W3.5 (its spawns) through the headless client: the spawn file for levels 49 to 56 loads every NPC in
 * it and a client visiting each map square sees that square's NPCs; the far north's monsters have their Wiki numbers
 * and drops in the combat and drop files; and a hellhound, an ice giant and a Pirates' Hideout pirate (step W3.7)
 * fought with those files leave their vile ashes, big bones and bones and only what their tables name.
 */
class HeadlessWilderness49To56Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        2841 to "icewarrior", 526 to "rogue", 3022 to "ice_spider", 2844 to "magicaxe", 108 to "whitewolf", 6603 to "wilderness_rogue",
        2834 to "bat", 3024 to "scorpion", 104 to "hellhound", 106 to "wolf", 2085 to "icegiant", 2086 to "icegiant2", 2087 to "icegiant3",
        77 to "skeleton_armed", 79 to "skeleton_armed3", 81 to "skeleton_armed5", 6599 to "wilderness_resource_guardian", 13 to "wilderness_resource_certer",
        6600 to "wilderness_rune_golem", 1536 to "49_61_crabs", 523 to "pirate_aggressive",
    )

    private val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    @Test
    fun `every map square of levels 49 to 56 has the spawn file's NPCs`(@TempDir dir: Path) {
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-49-56.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-49-56.json")
        assertEquals(emptyList(), parsed.unknown)
        assertTrue(parsed.spawns.all { Wilderness.level(it.x, it.z) in 49..56 && it.level == 0 }, "all on the surface in levels 49 to 56")
        assertEquals(8, parsed.spawns.count { it.type.internalName == "wilderness_rogue" }, "Rogues' Castle's level 135 rogues")
        assertEquals(9, parsed.spawns.count { it.type.internalName == "magicaxe" }, "the axe hut's magic axes")
        assertEquals(12, parsed.spawns.count { it.type.internalName == "pirate_aggressive" }, "the Pirates' Hideout's pirates")
        // The Chaos Elemental waits for piece 4.
        assertEquals(
            mapOf<String?, Int>("wilderness_resource_certer" to 1, "wilderness_rune_golem" to 2, "49_61_crabs" to 4),
            parsed.spawns.filter { it.x in 3174..3196 && it.z in 3924..3944 }.groupingBy { it.type.internalName }.eachCount(),
            "Piles, the runite golems and the dark crab spots in the Resource Area",
        )
        val squares = parsed.spawns.groupBy { Triple(it.x shr 6, it.z shr 6, it.level) }
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { for ((mx, mz) in squares.keys.map { it.first to it.second }.toSet()) markLoaded(mx, mz) },
                random = GameRandom(3), npcTypes = npcTypes, npcSpawns = parsed.spawns, cycleMillis = millis,
            )
        }.use { server ->
            server.login("ysolde").use { client ->
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

    @Test
    fun `the far north's monsters fight and drop as the Wiki has them`() {
        val combat = NpcCombat.load(npcTypes, SeqNames.NONE)
        assertEquals(6, combat[2841]!!.maxHit, "ice warrior")
        assertEquals(7, combat[2086]!!.maxHit, "ice giant")
        assertEquals(15, combat[6603]!!.maxHit, "the level 135 rogue")
        assertEquals(4, combat[523]!!.maxHit, "the hideout's pirates")
        assertEquals(11, combat[104]!!.maxHit, "hellhound")
        assertEquals(89, combat[104]!!.respawnTicks)
        val drops = NpcDrops.load(npcTypes, DropTestItems.objTypes())
        assertEquals(listOf(532), drops[2085]!!.always.map { it.type.id }, "ice giants' big bones")
        assertEquals(listOf(526, 995), drops[6603]!!.always.map { it.type.id }, "the rogue's bones and coins")
        assertEquals(listOf(25769), drops[104]!!.always.map { it.type.id }, "hellhounds' vile ashes")
        assertNotNull(drops[2841]!!.table, "ice warriors roll a table")
        assertEquals(128, drops[2841]!!.table!!.slots)
    }

    private val hellhound = NpcType(
        HELLHOUND, "Hellhound", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 122,
        internalName = "hellhound", stats = NpcStats(attack = 105, defence = 102, strength = 104, hitpoints = 116),
    )

    private val iceGiant = NpcType(
        ICE_GIANT, "Ice giant", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 53,
        internalName = "icegiant", stats = NpcStats(attack = 40, defence = 40, strength = 40, hitpoints = 70),
    )

    private val pirate = NpcType(
        PIRATE, "Pirate", ops = listOf(null, "Attack", null, null, null), combatLevel = 26,
        internalName = "pirate_aggressive", stats = NpcStats(attack = 20, defence = 20, strength = 20, hitpoints = 25), // test stats, only for the fight's length
    )

    private fun monsterServer(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val types = NpcTypes.of(hellhound, iceGiant, pirate)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(HOUND_X shr 6, HOUND_Z shr 6); markLoaded(GIANT_X shr 6, GIANT_Z shr 6); markLoaded(PIRATE_X shr 6, PIRATE_Z shr 6) },
            random = GameRandom(11), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(hellhound, HOUND_X, HOUND_Z, source = "test"), NpcSpawn(iceGiant, GIANT_X, GIANT_Z, source = "test"),
                NpcSpawn(pirate, PIRATE_X, PIRATE_Z, wander = 0, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
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
    fun `a hellhound, an ice giant and a pirate killed leave their ashes and bones and only what their tables name`(@TempDir dir: Path) {
        monsterServer(dir).use { server ->
            server.login("ymir").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(55, Wilderness.level(HOUND_X, HOUND_Z))
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure max") { true }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                for ((id, at, name, always) in listOf(Kill(HELLHOUND, HOUND_X to HOUND_Z, "hellhound", VILE_ASHES), Kill(ICE_GIANT, GIANT_X to GIANT_Z, "icegiant", BIG_BONES), Kill(PIRATE, PIRATE_X to PIRATE_Z, "pirate_aggressive", BONES))) {
                    client.tele(at.first - 1, at.second)
                    val npc = client.await("the $name in view") { client.npcs().firstOrNull { it.id == id } }
                    client.npcOp(npc.index, 2)
                    client.await("the $name's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == id }) Unit else null }
                    client.await("the $name's $always") { client.groundObjs().firstOrNull { it.id == always } }
                    val table = dropItems(name)
                    val near = client.groundObjs().filter { kotlin.math.abs(it.coord.x - at.first) <= 8 && kotlin.math.abs(it.coord.z - at.second) <= 8 }
                    for (obj in near) assertTrue(obj.id in table, "${obj.id} is on the $name's table")
                }
            }
        }
    }

    private data class Kill(val id: Int, val at: Pair<Int, Int>, val name: String, val always: Int)

    private companion object {
        /** A hellhound's spawn by the Resource Area (level 55) and an ice giant's on the Frozen Waste Plateau (level 51). */
        const val HELLHOUND = 104
        const val HOUND_X = 3169
        const val HOUND_Z = 3956
        const val ICE_GIANT = 2085
        const val GIANT_X = 2947
        const val GIANT_Z = 3921
        const val VILE_ASHES = 25769
        const val BIG_BONES = 532

        /** A pirate's spawn in the Pirates' Hideout (level 55, step W3.7). */
        const val PIRATE = 523
        const val PIRATE_X = 3041
        const val PIRATE_Z = 3953
        const val BONES = 526
    }
}
