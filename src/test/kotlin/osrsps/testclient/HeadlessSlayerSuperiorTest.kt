package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerSuperiors
import osrsps.game.skill.SlayerTask
import osrsps.map.CollisionMap

/**
 * Slayer step 6 through the headless client: a chasm crawler put out for a player on a cave crawler task with the
 * superior's message, another player's attack on it refused, its kill giving its 600 Slayer XP and one off the task,
 * one left alone going away after 2 minutes, and one attacked only with misses staying.
 */
class HeadlessSlayerSuperiorTest {
    // The 242 cache's types: the cave crawler and its superior, both naming the cave crawlers task (37) in param 50.
    private val crawler = NpcType(406, "Cave crawler", size = 2, ops = OPS, combatLevel = 23, internalName = "slayer_cave_crawler_1", params = mapOf(Slayer.PARAM_CATEGORY to 37), stats = NpcStats(hitpoints = 22))
    private val chasm = NpcType(7389, "Chasm Crawler", size = 3, ops = OPS, combatLevel = 68, internalName = "superior_cave_crawler", params = mapOf(Slayer.PARAM_CATEGORY to 37), stats = NpcStats(attack = 64, defence = 55, strength = 59, hitpoints = 60))

    /** The chasm crawler with a Defence no level-40 swing can get past: every attack on it misses. */
    private val armouredChasm = NpcType(chasm.id, chasm.name, size = 3, ops = OPS, combatLevel = 68, internalName = chasm.internalName, params = chasm.params, stats = NpcStats(attack = 1, defence = 100_000, strength = 1, hitpoints = 60))

    private val crawlers = SlayerTask(6226, 37, "cave crawlers", "Cave crawlers")
    private val data = SlayerData(listOf(crawlers), listOf(SlayerMasterTask(3, crawlers, 8, 40, 80)))

    // A stand-in tertiary on the cave crawler's drops that always lands, to count the superior's rolls of it.
    private val gloves = ObjType(GLOVES, "Mystic gloves (light)")
    private val objTypes = ObjTypes.of(gloves)
    private val drops = """{ "npcs": [ { "npcs": ["slayer_cave_crawler_1"], "tertiary": [ { "item": $GLOVES, "name": "Mystic gloves (light)", "chance": "1/1" } ], "source": "test" } ] }"""

    private fun server(dir: Path, superior: NpcType = chasm) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(crawler, superior)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            varTypes = VarTypes(6_000, mapOf(Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22), Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13))),
            npcTypes = types, npcCombat = NpcCombat.load(types, SeqNames.NONE), slayerData = data, cycleMillis = millis,
            objTypes = objTypes, npcDrops = NpcDrops.parse(drops, types, objTypes),
        )
    }

    private fun HeadlessClient.superior(): Int {
        val reply = commandReply("slayer superior") { it.startsWith("Put out") }
        // The message comes before the command's reply.
        await("the superior's message") { messages().firstOrNull { it.text == "[slayer_superior.appeared]" } }
        return reply.substringAfter("#").substringBefore(".").toInt()
    }

    @Test
    fun `a superior is its player's alone, and its kill gives its own XP and one off the task`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("setlevel all 40") { it.startsWith("Every skill") }
                    }
                    alice.commandReply("slayer task cave crawlers 5 vannaka") { it.startsWith("Task") }
                    val index = alice.superior()

                    bob.await("the chasm crawler in view") { bob.npcs().firstOrNull { it.index == index } }
                    bob.npcOp(index, ATTACK)
                    bob.awaitMessage("[slayer_superior.not_yours]") { it == "[slayer_superior.not_yours]" }

                    val before = alice.stat(SLAYER)?.xp ?: 0
                    alice.commandReply("kill $index") { it.startsWith("Killed") }
                    alice.await("600 Slayer XP") { if ((alice.stat(SLAYER)?.xp ?: 0) - before == 600) Unit else null }
                    alice.await("one off the task") { if (alice.varp(Slayer.VARP_COUNT) == 4) Unit else null }
                    // Each of the three rolls on the cave crawler's drops brings its tertiaries (the Wiki's rolls=3).
                    alice.await("three pairs of gloves") { if (alice.groundObjs().count { it.id == GLOVES } == 3) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a superior left alone goes after 2 minutes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("slayer task cave crawlers 5 vannaka") { it.startsWith("Task") }
                val index = client.superior()
                client.await("the chasm crawler in view") { client.npcs().firstOrNull { it.index == index } }
                // In steps, with a command between, as a connection that sends nothing for 15 seconds is dropped.
                repeat((SlayerSuperiors.IGNORED_TICKS - 10) / 38) {
                    client.awaitTicks(38)
                    client.commandReply("slayer") { it.contains("points") }
                }
                assertEquals(true, client.npcs().any { it.index == index })
                client.await("the chasm crawler gone", timeoutMillis = 10_000) { if (client.npcs().none { it.index == index }) Unit else null }
            }
        }
    }

    @Test
    fun `a superior attacked only with misses is not left alone`(@TempDir dir: Path) {
        server(dir, armouredChasm).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel all 40") { it.startsWith("Every skill") }
                client.command("god")
                client.commandReply("slayer task cave crawlers 5 vannaka") { it.startsWith("Task") }
                val index = client.superior()
                client.await("the chasm crawler in view") { client.npcs().firstOrNull { it.index == index } }
                client.npcOp(index, ATTACK)
                client.await("a blocked swing") { if (server.world.playerNamed("dave")?.attackedNpc?.index == index) Unit else null }
                repeat(SlayerSuperiors.IGNORED_TICKS / 38 + 2) {
                    client.awaitTicks(38)
                    client.commandReply("slayer") { it.contains("points") }
                }
                val npc = server.world.npcs.all.first { it.index == index }
                assertEquals(npc.maxHitpoints, npc.hitpoints)
                assertEquals(true, client.npcs().any { it.index == index })
            }
        }
    }

    private companion object {
        val OPS = listOf(null, "Attack", null, null, null)
        const val ATTACK = 2
        const val SLAYER = 18
        const val GLOVES = 20000
    }
}
