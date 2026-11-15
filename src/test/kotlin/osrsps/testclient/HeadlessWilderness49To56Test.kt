package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawnFile
import osrsps.map.CollisionMap

/**
 * Wilderness step W3.5 (its spawns) through the headless client: the spawn file for levels 49 to 56 loads every NPC in
 * it and a client visiting each map square sees that square's NPCs; and the far north's monsters have their Wiki
 * numbers and drops in the combat and drop files.
 */
class HeadlessWilderness49To56Test {
    /** The file's NPCs by their 242 cache ids, read by name as the server reads them. */
    private val names = mapOf(
        2841 to "icewarrior", 526 to "rogue", 3022 to "ice_spider", 2844 to "magicaxe", 108 to "whitewolf", 6603 to "wilderness_rogue",
        2834 to "bat", 3024 to "scorpion", 104 to "hellhound", 106 to "wolf", 2085 to "icegiant", 2086 to "icegiant2", 2087 to "icegiant3",
        77 to "skeleton_armed", 79 to "skeleton_armed3", 81 to "skeleton_armed5",
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
        // The Chaos Elemental waits for piece 4, the Resource Area for step W3.6, the Pirates' Hideout for W3.7.
        assertTrue(parsed.spawns.none { it.x in 3174..3196 && it.z in 3924..3944 }, "nothing in the Resource Area yet")
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
        assertEquals(11, combat[104]!!.maxHit, "hellhound")
        assertEquals(89, combat[104]!!.respawnTicks)
        val drops = NpcDrops.load(npcTypes, DropTestItems.objTypes())
        assertEquals(listOf(532), drops[2085]!!.always.map { it.type.id }, "ice giants' big bones")
        assertEquals(listOf(526, 995), drops[6603]!!.always.map { it.type.id }, "the rogue's bones and coins")
        assertEquals(listOf(25769), drops[104]!!.always.map { it.type.id }, "hellhounds' vile ashes")
        assertNotNull(drops[2841]!!.table, "ice warriors roll a table")
        assertEquals(128, drops[2841]!!.table!!.slots)
    }
}
