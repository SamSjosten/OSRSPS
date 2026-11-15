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
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.3 through the headless client: the spawn file for levels 21 to 35 loads every NPC in it and a
 * client visiting each map square sees that square's NPCs; the Bandit Camp mine's mithril and adamantite rocks mine as
 * any others do; and the Wilderness God Wars Dungeon's mouth stands at level 28 and leads nowhere until step W6.3.
 */
class HeadlessWilderness21To35Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        517 to "aggressive_black_knight", 301 to "black_heather", 2849 to "black_unicorn", 1026 to "brawling_bandit",
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
        // The bosses wait for piece 4.
        assertTrue(parsed.spawns.none { it.type.internalName in setOf("crazy_archaeologist", "venenatis", "vetion") })
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
    }
}
