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
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W2.2 through the headless client: the spawn file for levels 1 to 10 loads every NPC in it, a client
 * visiting each map square sees that square's NPCs, and the South Wilderness and South-west mines' rocks mine on the
 * existing Mining. The rocks are placed where the 242 map has them, with their cache ids.
 */
class HeadlessWildernessSouthTest {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        510 to "bearded_dark_wizard", 2838 to "brownbear", 2862 to "giantrat1", 2863 to "giantrat1_2", 2864 to "giantrat1_3",
        3024 to "scorpion", 77 to "skeleton_armed", 78 to "skeleton_armed2", 79 to "skeleton_armed3", 80 to "skeleton_armed4",
        81 to "skeleton_armed5", 70 to "skeleton_unarmed", 71 to "skeleton_unarmed2", 72 to "skeleton_unarmed3",
        73 to "skeleton_unarmed4", 525 to "thug", 6604 to "wilderness_mammoth", 512 to "young_dark_wizard",
    )

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `every map square of levels 1 to 10 has the spawn file's NPCs`(@TempDir dir: Path) {
        val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-south.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-south.json")
        assertEquals(emptyList(), parsed.unknown)
        assertTrue(parsed.spawns.all { Wilderness.level(it.x, it.z) in 1..10 && Wilderness.inside(it.x, it.z) }, "all in levels 1 to 10")
        val squares = parsed.spawns.groupBy { (it.x shr 6) to (it.z shr 6) }
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { for ((mx, mz) in squares.keys) markLoaded(mx, mz) },
                random = GameRandom(3), npcTypes = npcTypes, npcSpawns = parsed.spawns, cycleMillis = millis,
            )
        }.use { server ->
            server.login("ivan").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                client.await("the spawns loaded") { Unit.takeIf { world.npcs.all.count() == parsed.spawns.size } }
                for ((square, spawns) in squares) {
                    val loaded = world.npcs.all.count { (it.spawn.x shr 6) to (it.spawn.z shr 6) == square }
                    assertEquals(spawns.size, loaded, "map square $square")
                }
                client.commandReply("god") { it.startsWith("You can") }
                for ((square, spawns) in squares) {
                    val first = spawns.first()
                    client.tele(first.x, first.z)
                    client.await("${first.type.internalName} in view in map square $square") {
                        client.npcs().firstOrNull { it.id == first.type.id }
                    }
                }
            }
        }
    }

    @Test
    fun `the South Wilderness mine's iron and the South-west mine's coal mine`(@TempDir dir: Path) {
        val mine = listOf("Mine", null, null, null, null)
        fun rock(id: Int, name: String) = LocType(id, name, mine, LocCollisionType(width = 1, length = 1))
        TestServer(dir, cycleMillis = 40) { crcs, millis ->
            val gems = Mining.GEMS.map { it.obj }.filter { it >= 0 }
            val objs = arrayOfNulls<ObjType>(maxOf(RUNE_PICKAXE, gems.max()) + 1)
            objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
            objs[IRON_ORE] = ObjType(IRON_ORE, "Iron ore")
            objs[COAL] = ObjType(COAL, "Coal")
            for (gem in gems) objs[gem] = ObjType(gem, "Gem $gem")
            World(
                crcs, CollisionMap().apply { markLoaded(IRON_X shr 6, IRON_Z shr 6); markLoaded(COAL_X shr 6, COAL_Z shr 6) },
                random = GameRandom(3), objTypes = ObjTypes(objs),
                locTypes = LocTypes(listOf(rock(IRON_ROCK, "Iron rocks"), rock(COAL_ROCK, "Coal rocks"), rock(Mining.ROCKS1, "Rocks")).associateBy { it.id }),
                staticLocs = StaticLocs().apply {
                    add(ZoneLoc(IRON_ROCK, LocShape.CENTREPIECE_STRAIGHT, 0, IRON_X, IRON_Z, 0))
                    add(ZoneLoc(COAL_ROCK, LocShape.CENTREPIECE_STRAIGHT, 0, COAL_X, COAL_Z, 0))
                },
                cycleMillis = millis,
            )
        }.use { server ->
            server.login("jade").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $RUNE_PICKAXE")
                client.command("setlevel mining 99")
                client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }
                assertEquals(7, Wilderness.level(IRON_X, IRON_Z))
                assertEquals(10, Wilderness.level(COAL_X, COAL_Z))
                client.tele(IRON_X - 1, IRON_Z)
                client.locOp(IRON_ROCK, IRON_X, IRON_Z, 1)
                client.await("iron ore", timeoutMillis = 30_000) { Unit.takeIf { client.inventory(BACKPACK).any { it?.id == IRON_ORE } } }
                client.tele(COAL_X - 1, COAL_Z)
                client.locOp(COAL_ROCK, COAL_X, COAL_Z, 1)
                client.await("coal", timeoutMillis = 30_000) { Unit.takeIf { client.inventory(BACKPACK).any { it?.id == COAL } } }
            }
        }
    }

    private companion object {
        /** ironrock1 at the South Wilderness mine and coalrock1 at the South-west mine, as the 242 map places them. */
        const val IRON_ROCK = 11364
        const val IRON_X = 3104
        const val IRON_Z = 3570
        const val COAL_ROCK = 11366
        const val COAL_X = 3016
        const val COAL_Z = 3592
        const val IRON_ORE = 440
        const val COAL = 453
        const val RUNE_PICKAXE = 1275
        const val BACKPACK = 93
        const val MINING = 14
    }
}
