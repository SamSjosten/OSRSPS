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
import osrsps.game.EquipmentTab
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
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
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.3 through the headless client: the spawn file for levels 21 to 35 loads every NPC in it and a
 * client visiting each map square sees that square's NPCs; the Bandit Camp mine's mithril and adamantite rocks mine as
 * any others do; a chaos dwarf goes for a level-3 player unprovoked, and a chaos dwarf and a Forgotten Cemetery Ankou
 * fought with the real combat and drop files drop their bones and only what their tables name; and the Wilderness God
 * Wars Dungeon's mouth stands at level 28 and leads nowhere until step W6.3.
 */
class HeadlessWilderness21To35Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        6618 to "crazy_archaeologist", 517 to "aggressive_black_knight", 301 to "black_heather", 2849 to "black_unicorn", 1026 to "brawling_bandit",
        2838 to "brownbear", 3021 to "deadly_red_spider", 302 to "donny_the_lad", 291 to "dwarf_chaos", 85 to "ghost",
        86 to "ghost2", 87 to "ghost3", 90 to "ghost6", 92 to "ghost8", 2856 to "giantrat", 2863 to "giantrat1_2",
        2864 to "giantrat1_3", 3017 to "giantspider1", 260 to "green_dragon", 261 to "green_dragon2", 262 to "green_dragon3",
        263 to "green_dragon4", 264 to "green_dragon5", 3049 to "hobgoblin_unarmed", 3106 to "man", 3023 to "poisonspider",
        2854 to "rat", 77 to "skeleton_armed", 78 to "skeleton_armed2", 79 to "skeleton_armed3", 80 to "skeleton_armed4",
        81 to "skeleton_armed5", 303 to "speedy_keith", 3019 to "spider", 6608 to "wilderness_ankou", 6605 to "wilderness_bandit",
        6594 to "wilderness_ent", 6596 to "wilderness_zombie", 6597 to "wilderness_zombie2", 6598 to "wilderness_zombie3",
        45 to "zombie2_rural1", 46 to "zombie2_rural2", 47 to "zombie2_rural3", 48 to "zombie2_rural4", 59 to "zombie_armed_rural1",
        61 to "zombie_armed_rural3",
    )

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    @Test
    fun `every map square of levels 21 to 35 has the spawn file's NPCs`(@TempDir dir: Path) {
        val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-21-35.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-21-35.json")
        assertEquals(emptyList(), parsed.unknown)
        assertTrue(parsed.spawns.all { Wilderness.level(it.x, it.z) in 21..35 && it.level == 0 }, "all on the surface in levels 21 to 35")
        assertEquals(21, parsed.spawns.count { it.type.internalName == "wilderness_ankou" }, "the Forgotten Cemetery's Ankou")
        assertEquals(26, parsed.spawns.count { it.type.internalName == "hobgoblin_unarmed" }, "the Bandit Camp mine's hobgoblins")
        assertEquals(4, parsed.spawns.count { it.type.internalName == "wilderness_ent" })
        // The crazy archaeologist came with W4.2; the lairs' bosses wait for theirs.
        assertEquals(1, parsed.spawns.count { it.type.internalName == "crazy_archaeologist" }, "the crazy archaeologist in the Ruins")
        assertTrue(parsed.spawns.none { it.type.internalName in setOf("venenatis", "vetion") })
        val squares = parsed.spawns.groupBy { Triple(it.x shr 6, it.z shr 6, it.level) }
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { for ((mx, mz) in squares.keys.map { it.first to it.second }.toSet()) markLoaded(mx, mz) },
                random = GameRandom(3), npcTypes = npcTypes, npcSpawns = parsed.spawns, cycleMillis = millis,
            )
        }.use { server ->
            server.login("ora").use { client ->
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

    private val chaosDwarf = NpcType(
        CHAOS_DWARF, "Chaos dwarf", ops = listOf(null, "Attack", null, null, null), combatLevel = 48,
        internalName = "dwarf_chaos", stats = NpcStats(attack = 38, defence = 28, strength = 42, hitpoints = 61),
    )

    private val ankou = NpcType(
        ANKOU, "Ankou", ops = listOf(null, "Attack", null, null, null), combatLevel = 86,
        internalName = "wilderness_ankou", stats = NpcStats(attack = 75, defence = 80, strength = 75, hitpoints = 70),
    )

    private fun monsterServer(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val npcTypes = NpcTypes.of(chaosDwarf, ankou)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(DWARF_X shr 6, DWARF_Z shr 6); markLoaded(ANKOU_X shr 6, ANKOU_Z shr 6) },
            random = GameRandom(9), objTypes = objTypes, npcTypes = npcTypes,
            npcSpawns = listOf(NpcSpawn(chaosDwarf, DWARF_X, DWARF_Z, source = "test"), NpcSpawn(ankou, ANKOU_X, ANKOU_Z, source = "test")),
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes), cycleMillis = millis,
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
        lines(entry.getValue("always").jsonArray.map { it.jsonObject })
        lines(entry.getValue("table").jsonObject.getValue("lines").jsonArray.map { it.jsonObject })
        lines(entry["tertiary"]?.jsonArray?.map { it.jsonObject }.orEmpty())
        return items
    }

    @Test
    fun `a chaos dwarf goes for a level-3 player unprovoked`(@TempDir dir: Path) {
        monsterServer(dir).use { server ->
            server.login("quin").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(30, Wilderness.level(DWARF_X, DWARF_Z))
                // Auto retaliate off, so the player stands still and only the dwarf can start the fight.
                client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                client.tele(DWARF_X - 1, DWARF_Z)
                val hitpoints = client.await("hitpoints") { client.stat(Skill.HITPOINTS.id) }.level
                client.await("the dwarf's hit", timeoutMillis = 60_000) { client.stat(Skill.HITPOINTS.id)?.takeIf { it.level < hitpoints } }
            }
        }
    }

    @Test
    fun `a chaos dwarf and a Forgotten Cemetery Ankou killed drop their bones and only what their tables name`(@TempDir dir: Path) {
        monsterServer(dir).use { server ->
            server.login("rue").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(28, Wilderness.level(ANKOU_X, ANKOU_Z))
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure max") { true }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                for ((id, x, z, name) in listOf(Spot(CHAOS_DWARF, DWARF_X, DWARF_Z, "dwarf_chaos"), Spot(ANKOU, ANKOU_X, ANKOU_Z, "wilderness_ankou"))) {
                    client.tele(x - 1, z)
                    val npc = client.await("$name in view") { client.npcs().firstOrNull { it.id == id } }
                    client.npcOp(npc.index, 2)
                    client.await("$name's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == id }) Unit else null }
                    val bones = client.await("$name's bones") { client.groundObjs().firstOrNull { it.id == BONES } }
                    assertEquals(1, bones.count)
                    val table = dropItems(name)
                    for (obj in client.groundObjs()) assertTrue(obj.id in table, "${obj.id} is on $name's table")
                }
            }
        }
    }

    private data class Spot(val id: Int, val x: Int, val z: Int, val name: String)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(RUNE_PICKAXE + 1)
        objs[MITHRIL_ORE] = ObjType(MITHRIL_ORE, "Mithril ore")
        objs[ADAMANTITE_ORE] = ObjType(ADAMANTITE_ORE, "Adamantite ore")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        val mine = listOf("Mine", null, null, null, null)
        fun rock(id: Int, name: String) = LocType(id, name, mine, LocCollisionType(width = 1, length = 1))
        val entrance = LocType(GWD_ENTRANCE, "Cave", listOf("Enter", null, null, null, null), LocCollisionType(width = 3, length = 2))
        World(
            crcs, CollisionMap().apply { markLoaded(MITHRIL_X shr 6, MITHRIL_Z shr 6); markLoaded(GWD_X shr 6, GWD_Z shr 6) },
            objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(listOf(rock(MITHRIL, "Mithril rocks"), rock(ADAMANTITE, "Adamantite rocks"), entrance).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(MITHRIL, LocShape.CENTREPIECE_STRAIGHT, 2, MITHRIL_X, MITHRIL_Z, 0))
                add(ZoneLoc(ADAMANTITE, LocShape.CENTREPIECE_STRAIGHT, 3, ADAMANTITE_X, ADAMANTITE_Z, 0))
                add(ZoneLoc(GWD_ENTRANCE, LocShape.CENTREPIECE_STRAIGHT, 2, GWD_X, GWD_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    @Test
    fun `the Bandit Camp mine's mithril and adamantite rocks give their ores`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("pell").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                assertEquals(31, Wilderness.level(MITHRIL_X, MITHRIL_Z))
                client.command("item $RUNE_PICKAXE")
                client.command("setlevel mining 99")
                client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }
                client.tele(MITHRIL_X, MITHRIL_Z - 1)
                client.locOp(MITHRIL, MITHRIL_X, MITHRIL_Z, 1)
                client.await("mithril ore", timeoutMillis = 60_000) { if (client.count(MITHRIL_ORE) >= 1) Unit else null }
                client.tele(ADAMANTITE_X + 1, ADAMANTITE_Z)
                client.locOp(ADAMANTITE, ADAMANTITE_X, ADAMANTITE_Z, 1)
                client.await("adamantite ore", timeoutMillis = 60_000) { if (client.count(ADAMANTITE_ORE) >= 1) Unit else null }
                assertTrue(client.messages().any { it.text == "[mining.get_ore ore=adamantite]" })
            }
        }
    }

    @Test
    fun `the Wilderness God Wars Dungeon's mouth is at level 28 and leads nowhere yet`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("quil").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                assertEquals(28, Wilderness.level(GWD_X, GWD_Z))
                client.tele(GWD_X + 1, GWD_Z - 1)
                client.locOp(GWD_ENTRANCE, GWD_X, GWD_Z, 1)
                client.awaitMessage("nothing interesting") { it == "[game.nothing_interesting]" }
                assertEquals(Coord(GWD_X + 1, GWD_Z - 1, 0), client.position)
            }
        }
    }

    private companion object {
        /** mithrilrock1 and adamantiterock1, as the 242 map places them in the Bandit Camp mine. */
        const val MITHRIL = 11372
        const val MITHRIL_X = 3083
        const val MITHRIL_Z = 3760
        const val ADAMANTITE = 11374
        const val ADAMANTITE_X = 3078
        const val ADAMANTITE_Z = 3756
        const val MITHRIL_ORE = 447
        const val ADAMANTITE_ORE = 449
        const val RUNE_PICKAXE = 1275
        const val BACKPACK = 93
        const val MINING = 14

        /** wilderness_gwd_entrance (3 by 2), as the 242 map places it east of the Forgotten Cemetery. */
        const val GWD_ENTRANCE = 26766
        const val GWD_X = 3016
        const val GWD_Z = 3738

        /** A chaos dwarf's and an Ankou's spawns from the file, at levels 30 and 28. */
        const val CHAOS_DWARF = 291
        const val DWARF_X = 3021
        const val DWARF_Z = 3755
        const val ANKOU = 6608
        const val ANKOU_X = 2981
        const val ANKOU_Z = 3739
        const val BONES = 526
    }
}
