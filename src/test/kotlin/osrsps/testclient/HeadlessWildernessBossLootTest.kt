package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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
import osrsps.map.CollisionMap

/**
 * Wilderness step W4.1 through the headless client: a test boss with group loot, spawned with `::wboss`, hit for 30 by
 * one player and finished for 70 by another; each gets their own pile under it (the bones whole, the coins at their
 * share: 30 and 70), theirs alone at first, and each is told their kill count, read off its varp.
 */
class HeadlessWildernessBossLootTest {
    private val boss = NpcType(BOSS, "Ice giant", ops = listOf(null, "Attack", null, null, null), combatLevel = 53, internalName = "icegiant", stats = NpcStats(hitpoints = 100))
    private val types = NpcTypes.of(boss)

    private val drops = """
        {"npcs": [{"npcs": ["icegiant"], "always": [{"item": 532, "name": "Big bones"}],
          "table": {"slots": 1, "lines": [{"item": 995, "name": "Coins", "count": 100, "weight": 1}]}, "source": "test"}]}
    """.trimIndent()

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = ObjTypes.of(ObjType(532, "Big bones"), ObjType(995, "Coins", stackable = true))
        World(
            crcs, CollisionMap().apply { markLoaded(3200 shr 6, 3900 shr 6) }, objTypes = objs, npcTypes = types, random = GameRandom(4),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.parse(drops, types, objs), cycleMillis = millis,
        ).also { it.wildernessBosses.register(osrsps.game.content.WildernessBoss("test boss", setOf(BOSS), KILL_COUNT_VARP, group = true)) }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `two players' damage splits the boss's loot into two piles, and both count the kill`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss") { it == "Wilderness bosses: test boss" }
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 30 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    for (client in listOf(a, b)) {
                        client.await("the kill count") { client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Ice giant count=1]" } }
                    }
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    val bea = world.playerNamed("bea")!!
                    assertEquals(1, ada.vars[KILL_COUNT_VARP])
                    assertEquals(1, bea.vars[KILL_COUNT_VARP])
                    fun pile(player: osrsps.game.Player) = world.groundObjs.visibleAt(player, npc.x, npc.z, npc.level).map { it.id to it.count }.sortedBy { it.first }
                    a.await("the piles") { Unit.takeIf { pile(ada).isNotEmpty() && pile(bea).isNotEmpty() } }
                    assertEquals(listOf(532 to 1, 995 to 30), pile(ada), "ada's 30%")
                    assertEquals(listOf(532 to 1, 995 to 70), pile(bea), "bea's 70%")
                }
            }
        }
    }

    private companion object {
        const val BOSS = 2085
        const val KILL_COUNT_VARP = 1510
    }
}
