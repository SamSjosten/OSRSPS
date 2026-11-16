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
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.EdgevilleDungeon
import osrsps.game.content.LavaMaze
import osrsps.game.content.MemberGates
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
 * Wilderness step W3.4b through the headless client: the Lava Maze's two mirror ladders down into its dungeon and back
 * up, the fence gate into the lesser demons' pen, the metal gates north of the middle, the muddy chest locked and then
 * opened with a muddy key, the King Black Dragon's lever doing nothing yet, the dungeon's spawn file, and one of its black
 * dragons fought with the real combat and drop files, breathing fire and dropping its bones, hide and only what its table
 * names. The locs are
 * placed as the 242 map has them, on open ground. The rl-client script test-runner/examples/lava-maze-dungeon.json does
 * the same on the real map.
 */
class HeadlessLavaMazeTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(LavaMaze.WEST_LADDER.top, "Ladder", "Climb-down"),
            type(LavaMaze.WEST_LADDER.bottom, "Ladder", "Climb-up"),
            type(LavaMaze.MIDDLE_LADDER.top, "Ladder", "Climb-down"),
            type(LavaMaze.MIDDLE_LADDER.bottom, "Ladder", "Climb-up"),
            type(MemberGates.LEFT, "Gate", "Open"),
            type(MemberGates.RIGHT, "Gate", "Open"),
            type(EdgevilleDungeon.GATE_LEFT, "Gate", "Open"),
            type(EdgevilleDungeon.GATE_RIGHT, "Gate", "Open"),
            type(EdgevilleDungeon.GATE_LEFT_OPEN, "Gate", "Close"),
            type(EdgevilleDungeon.GATE_RIGHT_OPEN, "Gate", "Close"),
            type(LavaMaze.CHEST, "Muddy chest", "Open"),
            type(LavaMaze.CHEST_OPEN, "Muddy chest"),
            type(KBD_LEVER, "Lever", "Pull"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(LavaMaze.WEST_LADDER.top, LocShape.CENTREPIECE_STRAIGHT, 0, 3017, 3849, 0),
        ZoneLoc(LavaMaze.WEST_LADDER.bottom, LocShape.CENTREPIECE_STRAIGHT, 0, 3069, 10256, 0),
        ZoneLoc(LavaMaze.MIDDLE_LADDER.top, LocShape.CENTREPIECE_STRAIGHT, 0, 3069, 3856, 0),
        ZoneLoc(LavaMaze.MIDDLE_LADDER.bottom, LocShape.CENTREPIECE_STRAIGHT, 0, 3017, 10249, 0),
        ZoneLoc(MemberGates.LEFT, LocShape.WALL_STRAIGHT, 0, 3008, 3849, 0),
        ZoneLoc(MemberGates.RIGHT, LocShape.WALL_STRAIGHT, 0, 3008, 3850, 0),
        ZoneLoc(EdgevilleDungeon.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3075, 3867, 0),
        ZoneLoc(EdgevilleDungeon.GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3076, 3867, 0),
        ZoneLoc(LavaMaze.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3089, 3859, 0),
        ZoneLoc(KBD_LEVER, LocShape.CENTREPIECE_STRAIGHT, 0, 3067, 10253, 0),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3007 shr 6, 3849 shr 6)
            markLoaded(3008 shr 6, 3849 shr 6)
            markLoaded(3069 shr 6, 3856 shr 6)
            markLoaded(3089 shr 6, 3859 shr 6)
            markLoaded(3017 shr 6, 10249 shr 6)
            markLoaded(3069 shr 6, 10256 shr 6)
        }
        val objTypes = DropTestItems.objTypes(listOf(ObjType(LavaMaze.MUDDY_KEY, "Muddy key")))
        World(
            crcs, map, random = GameRandom(5), objTypes = objTypes, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(), npcDrops = NpcDrops.load(NpcTypes.of(), objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int = 0) =
        await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    @Test
    fun `the mirror ladders lead to the far end of the dungeon and back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("rhea").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(42, Wilderness.level(3017, 3849))
                // The pen's ladder, from the tile south of it, comes out south of the ladder by the King Black Dragon's lever.
                client.tele(3017, 3848)
                client.locOp(LavaMaze.WEST_LADDER.top, 3017, 3849, LavaMaze.CLIMB)
                client.awaitAt(3069, 10255)
                client.locOp(LavaMaze.WEST_LADDER.bottom, 3069, 10256, LavaMaze.CLIMB)
                client.awaitAt(3017, 3848)
                // The middle's ladder, from the east, comes out east of the west ladder, by the greater demons.
                client.tele(3070, 3856)
                client.locOp(LavaMaze.MIDDLE_LADDER.top, 3069, 3856, LavaMaze.CLIMB)
                client.awaitAt(3018, 10249)
                client.locOp(LavaMaze.MIDDLE_LADDER.bottom, 3017, 10249, LavaMaze.CLIMB)
                client.awaitAt(3070, 3856)
                // The lever is nothing interesting until the King Black Dragon's lair is built.
                client.tele(3067, 10252)
                client.locOp(KBD_LEVER, 3067, 10253, 1)
                client.awaitMessage("nothing interesting") { it == "[game.nothing_interesting]" }
            }
        }
    }

    @Test
    fun `the pen's fence gate passes the player through and the metal gates swing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("sola").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                // On the west edge of 3008,3849: from outside (3007) in, then back out through the other leaf.
                client.tele(3007, 3849)
                client.locOp(MemberGates.LEFT, 3008, 3849, 1)
                client.awaitAt(3008, 3849)
                client.await("the gate shut again") { server.world.locs.find(MemberGates.LEFT, 3008, 3849, 0) }
                client.tele(3008, 3850)
                client.locOp(MemberGates.RIGHT, 3008, 3850, 1)
                client.awaitAt(3007, 3850)

                client.tele(3075, 3866)
                client.locOp(EdgevilleDungeon.GATE_LEFT, 3075, 3867, EdgevilleDungeon.GATE_OPTION)
                client.await("the metal gates open") { Unit.takeIf { server.world.locs.find(EdgevilleDungeon.GATE_LEFT, 3075, 3867, 0) == null } }
                // Swung open, the leaves stand a tile north, and Close there shuts them again.
                val open = client.await("the left leaf open") { server.world.locs.find(EdgevilleDungeon.GATE_LEFT_OPEN, 3075, 3868, 0) }
                client.locOp(EdgevilleDungeon.GATE_LEFT_OPEN, open.x, open.z, EdgevilleDungeon.GATE_OPTION)
                client.await("the metal gates shut") { server.world.locs.find(EdgevilleDungeon.GATE_LEFT, 3075, 3867, 0) }
                assertTrue(client.messages().none { it.text == "[game.nothing_interesting]" })
            }
        }
    }

    @Test
    fun `the muddy chest wants a muddy key and gives its loot for one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("tamm").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3089, 3858)
                client.locOp(LavaMaze.CHEST, 3089, 3859, LavaMaze.OPEN)
                client.awaitMessage("locked") { it == "[lava_maze.chest_locked]" }

                client.command("item ${LavaMaze.MUDDY_KEY}")
                client.await("the key") { Unit.takeIf { client.count(LavaMaze.MUDDY_KEY) == 1L } }
                client.locOp(LavaMaze.CHEST, 3089, 3859, LavaMaze.OPEN)
                client.awaitMessage("the treasure") { it == "[lava_maze.chest_treasure]" }
                assertTrue(client.messages().any { it.text == "[lava_maze.chest_unlock]" })
                assertEquals(0L, client.count(LavaMaze.MUDDY_KEY))
                // The always part of the loot, as the Wiki lists it.
                assertEquals(1L, client.count(1619), "uncut ruby")
                assertEquals(2L, client.count(2360), "noted mithril bars")
                assertEquals(5L, client.count(563), "law runes")
                assertEquals(5L, client.count(560), "death runes")
                assertEquals(15L, client.count(562), "chaos runes")
                assertNotNull(server.world.locs.find(LavaMaze.CHEST_OPEN, 3089, 3859, 0))
                client.await("the chest shut again") { server.world.locs.find(LavaMaze.CHEST, 3089, 3859, 0) }
            }
        }
    }

    @Test
    fun `a muddy key gone in the tick before the chest opens opens nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("orrin").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("item ${LavaMaze.MUDDY_KEY}")
                client.await("the key") { Unit.takeIf { client.count(LavaMaze.MUDDY_KEY) == 1L } }
                client.tele(3089, 3857)
                // One step from the chest, with the clock stopped: the Open arrives, the player steps beside the chest
                // in the next tick and the chest waits a tick more; the key leaves the backpack in between.
                client.commandReply("pause") { it.startsWith("Paused") }
                client.locOp(LavaMaze.CHEST, 3089, 3859, LavaMaze.OPEN)
                val paused = server.world.tick
                client.commandReply("step 1") { it.startsWith("Stepping") }
                client.await("the stepped tick") { Unit.takeIf { server.world.tick > paused } }
                val player = server.world.playerNamed("orrin")!!
                assertEquals(3858, player.z, "beside the chest")
                player.inventory.remove(player.inventory.contents().first { it.second.id == LavaMaze.MUDDY_KEY }.first, 1)
                client.commandReply("resume") { true }
                client.awaitMessage("locked") { it == "[lava_maze.chest_locked]" }
                assertEquals(0L, client.count(1619), "no loot")
                assertTrue(client.messages().none { it.text == "[lava_maze.chest_treasure]" })
                assertNotNull(server.world.locs.find(LavaMaze.CHEST, 3089, 3859, 0), "still shut")
            }
        }
    }

    @Test
    fun `the dungeon's spawn file has its demons, spiders and dragons`() {
        val names = mapOf(
            "greater_demon3" to 2027, "greater_demon4" to 2028, "greater_demon5" to 2029, "poisonspider" to 3023,
            "black_dragon4" to 255, "black_dragon5" to 256,
        )
        val npcTypes = NpcTypes.of(*names.map { (name, id) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/lava-maze-dungeon.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "lava-maze-dungeon.json")
        assertEquals(emptyList(), parsed.unknown)
        assertEquals(15, parsed.spawns.size)
        assertEquals(3, parsed.spawns.count { it.type.internalName.orEmpty().startsWith("greater_demon") })
        assertEquals(10, parsed.spawns.count { it.type.internalName == "poisonspider" })
        assertEquals(2, parsed.spawns.count { it.type.internalName.orEmpty().startsWith("black_dragon") })
        assertTrue(parsed.spawns.all { it.x in 3008..3071 && it.z in 10240..10303 && it.level == 0 })
        assertTrue("lava-maze-dungeon.json" in NpcSpawnFile.FILES)
    }

    private val blackDragon = NpcType(
        BLACK_DRAGON, "Black dragon", size = 4, ops = listOf(null, "Attack", null, null, null), combatLevel = 227,
        internalName = "black_dragon4", stats = NpcStats(attack = 200, defence = 200, strength = 200, hitpoints = 190, magic = 100),
    )

    private fun dragonServer(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val npcTypes = NpcTypes.of(blackDragon)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(DRAGON_X shr 6, DRAGON_Z shr 6) }, random = GameRandom(7), objTypes = objTypes,
            npcTypes = npcTypes, npcSpawns = listOf(NpcSpawn(blackDragon, DRAGON_X, DRAGON_Z, source = "test")),
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
        lines(entry["always"]?.jsonArray?.map { it.jsonObject }.orEmpty())
        lines(entry["table"]?.jsonObject?.getValue("lines")?.jsonArray?.map { it.jsonObject }.orEmpty())
        lines(entry["tertiary"]?.jsonArray?.map { it.jsonObject }.orEmpty())
        return items
    }

    @Test
    fun `a black dragon breathes fire, and killed drops its bones, hide and only what its table names`(@TempDir dir: Path) {
        dragonServer(dir).use { server ->
            server.login("sol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(DRAGON_X - 1, DRAGON_Z)
                val npc = client.await("the dragon in view") { client.npcs().firstOrNull { it.id == BLACK_DRAGON } }
                client.npcOp(npc.index, 2)
                client.awaitMessage("a breath", timeoutMillis = 60_000) { it == "[dragonfire.burnt]" || it == "[dragonfire.resisted]" }
                client.commandReply("sure max") { true }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                client.await("the dragon's death", timeoutMillis = 120_000) { if (client.npcs().none { it.id == BLACK_DRAGON }) Unit else null }
                client.await("the dragon bones") { client.groundObjs().firstOrNull { it.id == DRAGON_BONES } }
                client.await("the black dragonhide") { client.groundObjs().firstOrNull { it.id == BLACK_DRAGONHIDE } }
                val table = dropItems("black_dragon4")
                for (obj in client.groundObjs()) assertTrue(obj.id in table, "${obj.id} is on the black dragon's table")
            }
        }
    }

    private companion object {
        /** `dragonkinginlever`, by the east ladder. */
        const val KBD_LEVER = 1816
        const val BACKPACK = 93

        /** A black dragon's spawn in the dungeon, from its spawn file. */
        const val BLACK_DRAGON = 255
        const val DRAGON_X = 3048
        const val DRAGON_Z = 10266
        const val DRAGON_BONES = 536
        const val BLACK_DRAGONHIDE = 1747
    }
}
