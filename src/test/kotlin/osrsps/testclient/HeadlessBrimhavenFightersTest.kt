package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * Karamja step K3.2b through the headless client, with the real combat and drop files: a Brimhaven pirate guard killed
 * leaves only his bones, and a jungle spider its carcass. The hitpoints are cut down here so a level 3 player's hits
 * finish them quickly; test-runner/examples/brimhaven-fighters.json fights them on the real map.
 */
class HeadlessBrimhavenFightersTest {
    private val guard = NpcType(
        PIRATE_GUARD, "Pirate Guard", ops = listOf(null, "Attack", null, null, null), combatLevel = 26,
        internalName = "pirate_guard", stats = NpcStats(hitpoints = 4),
    )
    private val spider = NpcType(
        JUNGLE_SPIDER, "Jungle spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 44,
        internalName = "jungle_spider", stats = NpcStats(hitpoints = 4),
    )

    private fun server(dir: Path, type: NpcType) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(guard, spider)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(9), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(type, 3224, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun killLeaves(dir: Path, type: NpcType, drop: Int) {
        server(dir, type).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the ${type.name} in view") { client.npcs().firstOrNull { it.id == type.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                client.await("its death", timeoutMillis = 90_000) { if (client.npcs().none { it.id == type.id }) Unit else null }
                val drops = client.await("the drop") { client.groundObjs().map { it.id }.takeIf { drop in it } }
                assertEquals(listOf(drop), drops)
            }
        }
    }

    @Test
    fun `a pirate guard killed drops only his bones`(@TempDir dir: Path) = killLeaves(dir, guard, BONES)

    @Test
    fun `a jungle spider killed drops its carcass`(@TempDir dir: Path) = killLeaves(dir, spider, SPIDER_CARCASS)

    private companion object {
        const val PIRATE_GUARD = 4926
        const val JUNGLE_SPIDER = 3020
        const val BONES = 526
        const val SPIDER_CARCASS = 6291
    }
}
