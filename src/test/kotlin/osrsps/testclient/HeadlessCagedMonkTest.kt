package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The Varrock Palace's caged Monk of Zamorak (level 17) through the headless client: attacked, he fights back, and
 * killed he leaves his bones, from the real npc-combat.json and npc-drops.json. The cage itself is the cache's walls,
 * which the runner script test-runner/examples/caged-monk.json meets in the real client.
 */
class HeadlessCagedMonkTest {
    private val monk = NpcType(
        528, "Monk of Zamorak", ops = listOf(null, "Attack", null, null, null), combatLevel = 17, internalName = "chaosmonk2",
        stats = NpcStats(attack = 8, defence = 12, strength = 8, hitpoints = 10, magic = 25),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(monk)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(7), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(monk, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `the caged monk fights back and leaves his bones`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("the monk in view") { client.npcs().firstOrNull { it.id == monk.id } }.index
                client.npcOp(npc, 2)
                client.await("his punch back", timeoutMillis = 30_000) {
                    client.actorEvents().firstOrNull { !it.npc && it.index == client.index && it.kind == ActorEventKind.HITSPLAT }
                }
                client.commandReply("kill $npc") { it.startsWith("Killed") }
                client.await("his bones", timeoutMillis = 20_000) { client.groundObjs().firstOrNull { it.id == BONES } }
            }
        }
    }

    private companion object {
        const val BONES = 526
    }
}
