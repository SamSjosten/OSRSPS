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
 * Varrock's and Edgeville's guards through the headless client, with the real combat and drop files: each attacked,
 * fought to its death and leaving its bones. The rl-client script test-runner/examples/varrock-guards.json kills one of
 * each in the real client.
 */
class HeadlessGuardsTest {
    private fun guard(id: Int, internal: String) = NpcType(
        id, "Guard", ops = listOf(null, "Attack", "Pickpocket", null, null), combatLevel = 21, internalName = internal,
        stats = NpcStats(hitpoints = 22, attack = 19, strength = 18, defence = 14),
    )

    private val varrock = guard(VARROCK_GUARD, "fai_varrock_guard02")
    private val edgeville = guard(EDGEVILLE_GUARD, "guard1")

    private fun server(dir: Path, type: NpcType) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(varrock, edgeville)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(21), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(type, 3224, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun killForBones(dir: Path, type: NpcType) {
        server(dir, type).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the guard in view") { client.npcs().firstOrNull { it.id == type.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("setlevel all 99") { it.contains("99") }
                client.npcOp(npc.index, 2)
                client.await("the guard's death", timeoutMillis = 90_000) { if (client.npcs().none { it.id == type.id }) Unit else null }
                val drops = client.await("the bones") { client.groundObjs().map { it.id }.takeIf { BONES in it } }
                assertEquals(1, drops.count { it == BONES })
                // Attack xp is 4 per damage dealt, and a guard has 22 hitpoints.
                assertEquals(22 * 4, client.stat(ATTACK)!!.xp - ATTACK_99_XP)
            }
        }
    }

    @Test
    fun `a Varrock guard fought to its death drops its bones`(@TempDir dir: Path) = killForBones(dir, varrock)

    @Test
    fun `an Edgeville guard fought to its death drops its bones`(@TempDir dir: Path) = killForBones(dir, edgeville)

    private companion object {
        const val VARROCK_GUARD = 11911
        const val EDGEVILLE_GUARD = 3254
        const val BONES = 526
        const val ATTACK = 0

        /** Experience for level 99. */
        const val ATTACK_99_XP = 13_034_431
    }
}
