package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.WearPos
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.CorporealBeast
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W2.4 through the headless client: the spawn file for levels 11 to 20 loads every NPC in it and a
 * client visiting each map square (both floors of the Dark Warriors' Fortress) sees that square's NPCs; a level 145
 * dark warrior fought with the real combat and drop files drops its bones and only what its table names; a green dragon
 * breathes dragonfire, burning or resisted bare and held to the shield's line behind an anti-dragon shield; and the
 * Corporeal Beast's cave mouth stands at level 20 and, since W4.15, leads into its lair.
 */
class HeadlessWilderness11To20Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        2849 to "black_unicorn", 2838 to "brownbear", 531 to "dark_warrior", 85 to "ghost", 86 to "ghost2", 87 to "ghost3",
        90 to "ghost6", 91 to "ghost7", 92 to "ghost8", 2098 to "giant", 2100 to "giant3", 3017 to "giantspider1",
        260 to "green_dragon", 263 to "green_dragon4", 264 to "green_dragon5", 1027 to "guard_bandit", 2854 to "rat",
        72 to "skeleton_unarmed3", 3019 to "spider", 6605 to "wilderness_bandit", 6607 to "wilderness_chaos_druid",
        6606 to "wilderness_dark_warrior", 6594 to "wilderness_ent", 45 to "zombie2_rural1", 46 to "zombie2_rural2",
        47 to "zombie2_rural3", 48 to "zombie2_rural4", 60 to "zombie_armed_rural2", 61 to "zombie_armed_rural3",
        62 to "zombie_armed_rural4", 63 to "zombie_armed_rural5",
    )

    private val darkWarrior = NpcType(
        DARK_WARRIOR, "Dark warrior", ops = listOf(null, "Attack", null, null, null), combatLevel = 145,
        internalName = "wilderness_dark_warrior", stats = NpcStats(attack = 75, defence = 55, strength = 75, hitpoints = 165),
    )

    private val dragon = NpcType(
        GREEN_DRAGON, "Green dragon", size = 4, ops = listOf(null, "Attack", null, null, null), combatLevel = 79,
        internalName = "green_dragon", stats = NpcStats(attack = 68, defence = 68, strength = 68, hitpoints = 75, magic = 68),
    )

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    @Test
    fun `every map square of levels 11 to 20 has the spawn file's NPCs`(@TempDir dir: Path) {
        val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-11-20.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-11-20.json")
        assertEquals(emptyList(), parsed.unknown)
        assertTrue(parsed.spawns.none { Wilderness.inFerox(it.x, it.z) }, "none in Ferox Enclave")
        // The ents run from level 9 up; every other NPC is in 11 to 20.
        val (ents, rest) = parsed.spawns.partition { it.type.internalName == "wilderness_ent" }
        assertTrue(rest.all { Wilderness.level(it.x, it.z) in 11..20 }, "all but the ents in levels 11 to 20")
        assertTrue(ents.all { Wilderness.level(it.x, it.z) in 1..20 }, "the ents below 21")
        assertEquals(6, ents.size)
        assertEquals(17, parsed.spawns.count { it.type.internalName == "wilderness_dark_warrior" })
        assertEquals(8, parsed.spawns.count { it.type.internalName == "dark_warrior" && it.level == 1 }, "the fortress's upstairs")
        val squares = parsed.spawns.groupBy { Triple(it.x shr 6, it.z shr 6, it.level) }
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { for ((mx, mz) in squares.keys.map { it.first to it.second }.toSet()) markLoaded(mx, mz) },
                random = GameRandom(3), npcTypes = npcTypes, npcSpawns = parsed.spawns, cycleMillis = millis,
            )
        }.use { server ->
            server.login("ivo").use { client ->
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
        val npcTypes = NpcTypes.of(darkWarrior, dragon)
        val objTypes = DropTestItems.objTypes()
        val cave = LocType(CAVE, "Cave", listOf("Enter", null, null, null, null), LocCollisionType(width = 5, length = 5))
        World(
            crcs, CollisionMap().apply { markLoaded(WARRIOR_X shr 6, WARRIOR_Z shr 6); markLoaded(DRAGON_X shr 6, DRAGON_Z shr 6); markLoaded(CAVE_X shr 6, CAVE_Z shr 6); markLoaded(46, 66) },
            random = GameRandom(7), objTypes = objTypes,
            locTypes = LocTypes(mapOf(CAVE to cave)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(CAVE, LocShape.CENTREPIECE_STRAIGHT, 3, CAVE_X, CAVE_Z, 0)) },
            npcTypes = npcTypes, npcSpawns = listOf(
                NpcSpawn(darkWarrior, WARRIOR_X, WARRIOR_Z, source = "test"),
                NpcSpawn(dragon, DRAGON_X, DRAGON_Z, source = "test"),
            ),
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes), cycleMillis = millis,
        )
    }

    /** The items the drop file's level 145 dark warrior line can give: its own, and the herb table's. */
    private fun darkWarriorItems(): Set<Int> {
        val root = Json.parseToJsonElement(NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()).jsonObject
        val entry = root.getValue("npcs").jsonArray.map { it.jsonObject }
            .single { e -> e.getValue("npcs").jsonArray.any { it.jsonPrimitive.content == "wilderness_dark_warrior" } }
        val herbs = root.getValue("tables").jsonObject.getValue("herbs").jsonObject
        fun ids(lines: List<kotlinx.serialization.json.JsonObject>) = lines.mapNotNull { it["item"]?.jsonPrimitive?.int }
        return (
            ids(entry.getValue("always").jsonArray.map { it.jsonObject }) +
                ids(entry.getValue("table").jsonObject.getValue("lines").jsonArray.map { it.jsonObject }) +
                ids(entry["tertiary"]?.jsonArray?.map { it.jsonObject }.orEmpty()) +
                ids(herbs.getValue("lines").jsonArray.map { it.jsonObject })
            ).toSet()
    }

    @Test
    fun `a level 145 dark warrior killed drops its bones and only what its table names`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jon").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(16, Wilderness.level(WARRIOR_X, WARRIOR_Z))
                client.tele(WARRIOR_X - 1, WARRIOR_Z)
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure max") { true }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                val npc = client.await("the dark warrior in view") { client.npcs().firstOrNull { it.id == DARK_WARRIOR } }
                client.npcOp(npc.index, 2)
                client.await("the dark warrior's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == DARK_WARRIOR }) Unit else null }
                val bones = client.await("the bones") { client.groundObjs().firstOrNull { it.id == BONES } }
                assertEquals(1, bones.count)
                val table = darkWarriorItems()
                for (obj in client.groundObjs()) assertTrue(obj.id in table, "${obj.id} is on the dark warrior's table")
            }
        }
    }

    @Test
    fun `a green dragon breathes dragonfire, and an anti-dragon shield takes the shield's line`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kai").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val kai = server.world.playerNamed("kai")!!
                client.tele(DRAGON_X - 1, DRAGON_Z)
                client.commandReply("god") { it.startsWith("You can") }
                val npc = client.await("the dragon in view") { client.npcs().firstOrNull { it.id == GREEN_DRAGON } }
                client.npcOp(npc.index, 2)
                client.awaitMessage("a bare breath", timeoutMillis = 60_000) { it == "[dragonfire.burnt]" || it == "[dragonfire.resisted]" }
                kai.equipment[WearPos.SHIELD] = InvObj(ANTI_DRAGON_SHIELD, 1)
                val before = client.messages().size
                client.await("a shielded breath", timeoutMillis = 60_000) {
                    client.messages().drop(before).firstOrNull { it.text.startsWith("[dragonfire.") }
                }.also { assertEquals("[dragonfire.shield]", it.text) }
            }
        }
    }

    @Test
    fun `the Corporeal Beast's cave mouth is at level 20 and leads into its lair`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("lia").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(20, Wilderness.level(CAVE_X, CAVE_Z))
                client.tele(CAVE_X + 2, CAVE_Z - 1)
                client.locOp(CAVE, CAVE_X, CAVE_Z, 1)
                val arrival = Coord(CorporealBeast.ARRIVAL.x, CorporealBeast.ARRIVAL.z, CorporealBeast.LAIR_LEVEL)
                client.await("in the lair", timeoutMillis = 15_000) { client.position?.takeIf { it == arrival } }
            }
        }
    }

    private companion object {
        const val DARK_WARRIOR = 6606
        const val WARRIOR_X = 3035
        const val WARRIOR_Z = 3644
        const val GREEN_DRAGON = 260
        const val DRAGON_X = 3330
        const val DRAGON_Z = 3660
        const val ANTI_DRAGON_SHIELD = 1540
        const val BONES = 526

        /** corp_cave_entrance, as the 242 map places it north-east of Ferox Enclave. */
        const val CAVE = 678
        const val CAVE_X = 3201
        const val CAVE_Z = 3679
    }
}
