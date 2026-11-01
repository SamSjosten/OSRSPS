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
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** Edgeville step 3b: an outlaw from the camp, fought through the headless client with the real combat file. */
class HeadlessEdgevilleOutlawsTest {
    private val outlaw = NpcType(
        4171, "Outlaw", ops = listOf(null, "Attack", null, null, null), combatLevel = 32, internalName = "surok_outlaw5",
        stats = NpcStats(attack = 35, strength = 30, defence = 25, hitpoints = 20),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(outlaw)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(7), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(outlaw, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `killing an outlaw gives a fortieth of the XP for its 20 hitpoints, and it is back two ticks on`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("olive").use { client ->
                // Beside the login tile, which the test map has loaded; the camp's own tiles are the spawn file's test.
                val npc = client.await("the outlaw in view") { client.npcs().firstOrNull { it.id == outlaw.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure") { it.startsWith("Your attacks can't miss") }
                client.commandReply("setlevel strength 99") { it.contains("99") }
                val hitpoints = client.stat(HITPOINTS)!!.xp
                client.npcOp(npc.index, 2)
                // Back two ticks after its death, too soon to see it gone: its bones show it died.
                client.await("the bones", timeoutMillis = 10_000) { client.groundObjs().map { it.id }.takeIf { BONES in it } }
                // Punching (Attack): 4 XP a point is 80 for 20 hitpoints, and -97.5% leaves 2.0 however the hits fell.
                assertEquals(2, client.stat(ATTACK)!!.xp)
                // Hitpoints' 1.33 a point (26.6) is a fortieth too, rounded down hit by hit: at most 0.6, so 0 whole XP.
                assertEquals(hitpoints, client.stat(HITPOINTS)!!.xp)
                client.await("the outlaw back") { client.npcs().firstOrNull { it.id == outlaw.id } }
                assertTrue(client.commandReply("fight") { it.startsWith("You are") }.startsWith("You are not fighting"))
            }
        }
    }

    companion object {
        const val BONES = 526
        const val ATTACK = 0
        const val HITPOINTS = 3
    }
}
