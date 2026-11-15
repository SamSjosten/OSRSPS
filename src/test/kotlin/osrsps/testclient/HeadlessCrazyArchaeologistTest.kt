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
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.CrazyArchaeologist
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W4.2 through the headless client: the crazy archaeologist, seeded, throwing books at a player out of
 * his reach, then his Rain of knowledge: three books, each exploding over a 3x3 [CrazyArchaeologist.BOOK_TICKS] later,
 * and one splitting into two more; then a kill counting in his kill count with his bones on the floor. The rl-client
 * script test-runner/examples/crazy-archaeologist.json fights him on the real map.
 */
class HeadlessCrazyArchaeologistTest {
    private val boss = NpcType(
        CrazyArchaeologist.BOSS, "Crazy archaeologist", ops = listOf(null, "Attack", null, null, null), combatLevel = 204,
        internalName = "crazy_archaeologist", stats = NpcStats(attack = 160, strength = 90, defence = 240, hitpoints = 225, ranged = 180),
    )
    private val types = NpcTypes.of(boss)

    private fun server(dir: Path, seed: Long) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = objs, npcTypes = types, random = GameRandom(seed),
            npcSpawns = listOf(NpcSpawn(boss, X, Z, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objs), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `he throws books from afar, and his rain of knowledge explodes three books and two from a split`(@TempDir dir: Path) {
        server(dir, seed = 11).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(X + 4, Z)
                val npc = server.world.npcs.all.first { it.type.id == CrazyArchaeologist.BOSS }
                // Hit once so he takes the player on, aggressive or not.
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Crazy archaeologist") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("a book thrown", timeoutMillis = 30_000) { fight.lines.firstOrNull { "throws a book at ivy" in it } }
                assertTrue(fight.lines.none { "punches" in it }, "no melee from 4 tiles off")
                val rain = client.await("the rain of knowledge", timeoutMillis = 60_000) { fight.lines.firstOrNull { "rain of knowledge" in it } }
                val landings = rain.substringAfter("rain of knowledge on ").trim().split(" ")
                assertEquals(3, landings.size, rain)
                client.await("his shout") { client.npcSaid().firstOrNull { it.index == npc.index && it.text == "[crazy_archaeologist.rain]" } }
                // Three 3x3s from the throw, then two more from the split.
                client.await("five explosions' hazards") { Unit.takeIf { fight.lines.count { it.contains("hazard on") } >= 5 } }
                val hazards = fight.lines.filter { it.contains("hazard on") }.take(5)
                assertTrue(hazards.all { it.substringAfter("hazard on ").substringBefore(" from").split(" ").size == 9 }, hazards.joinToString("\n"))
                assertTrue(hazards.take(3).all { it.endsWith("from tick ${CrazyArchaeologist.BOOK_TICKS} for 1") }, hazards.joinToString("\n"))
                assertTrue(hazards.drop(3).all { it.endsWith("from tick ${CrazyArchaeologist.SPLIT_TICKS} for 1") }, hazards.joinToString("\n"))
            }
        }
    }

    @Test
    fun `a kill counts in his kill count and leaves his bones`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("jon").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(X + 1, Z)
                val npc = server.world.npcs.all.first { it.type.id == CrazyArchaeologist.BOSS }
                client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the kill count") {
                    client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Crazy archaeologist count=1]" }
                }
                assertEquals(1, server.world.playerNamed("jon")!!.vars[CrazyArchaeologist.KILL_COUNT_VARP])
                val jon = server.world.playerNamed("jon")!!
                client.await("his bones") { server.world.groundObjs.visibleAt(jon, npc.x, npc.z, 0).firstOrNull { it.id == BONES } }
            }
        }
    }

    private companion object {
        const val X = 2977
        const val Z = 3702
        const val BONES = 526
    }
}
