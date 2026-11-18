package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
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
 * Karamja step K3.3b through the headless client, with the real combat and drop files: a Brimhaven Dungeon wild dog
 * killed leaves its bones, and a baby red dragon its babydragon bones. The hitpoints are cut down here and the player
 * given 99 Attack so the fights end quickly; test-runner/examples/brimhaven-dungeon-monsters.json fights in the dungeon
 * on the real map.
 */
class HeadlessBrimhavenDungeonMonstersTest {
    private val dog = NpcType(
        WILD_DOG, "Wild dog", ops = listOf(null, "Attack", null, null, null), combatLevel = 63,
        internalName = "dog_wild", stats = NpcStats(hitpoints = 4),
    )
    private val dragon = NpcType(
        BABY_RED, "Baby red dragon", ops = listOf(null, "Attack", null, null, null), combatLevel = 65,
        internalName = "babyreddragon", stats = NpcStats(hitpoints = 4),
    )

    private fun server(dir: Path, type: NpcType) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(dog, dragon)
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
                // The dungeon's monsters have high defences, so the player gets the levels to land hits.
                client.commandReply("setlevel attack 99") { it.contains("Attack") }
                client.npcOp(npc.index, 2)
                client.await("its death", timeoutMillis = 90_000) { if (client.npcs().none { it.id == type.id }) Unit else null }
                val drops = client.await("the drop") { client.groundObjs().map { it.id }.takeIf { drop in it } }
                assertTrue(drop in drops)
            }
        }
    }

    @Test
    fun `a wild dog killed drops its bones`(@TempDir dir: Path) = killLeaves(dir, dog, BONES)

    @Test
    fun `a baby red dragon killed drops babydragon bones`(@TempDir dir: Path) = killLeaves(dir, dragon, BABYDRAGON_BONES)

    private companion object {
        const val WILD_DOG = 112
        const val BABY_RED = 244
        const val BONES = 526
        const val BABYDRAGON_BONES = 534
    }
}
