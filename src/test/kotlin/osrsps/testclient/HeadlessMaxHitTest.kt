package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.HitsplatType
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Max hit hitsplats over the wire: a new account punches for at most 1, so every hit that lands is a max hit. Its
 * puncher is sent damage_max_me while the All Settings "Max hit hitsplats threshold" is 1 or under, an ordinary hit
 * above it; an onlooker always gets the ordinary one. The "Max hit hitsplats" toggle itself is the client's (hitmark
 * 43's multimark), so the server only has to send 43.
 */
class HeadlessMaxHitTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 99))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcDrops = NpcDrops.load(types, objTypes), varTypes = VarTypes(6_000, AllSettings.VARBITS), cycleMillis = millis,
        )
    }

    /** Waits for a hit on [npc] that did damage, seen after the first [skip], and returns it. */
    private fun HeadlessClient.damage(npc: Int, skip: Int): NpcHit =
        await("a hit that lands", timeoutMillis = 20_000) { npcHits().drop(skip).firstOrNull { it.index == npc && it.value > 0 } }

    @Test
    fun `the puncher sees their max hits at or over the threshold, and no one else does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val npc = alice.await("the man in view") { alice.npcs().firstOrNull { it.id == man.id } }.index
                    alice.await("the login rebuild") { alice.position }
                    alice.commandReply("god") { it.startsWith("You can") }
                    alice.commandReply("varbit ${MeleeCombat.VARBIT_MAX_HIT_THRESHOLD} 1") { it.startsWith("Varbit") }
                    alice.npcOp(npc, 2)

                    val hit = alice.damage(npc, 0)
                    assertEquals(1, hit.value, "a new account's max hit")
                    assertEquals(HitsplatType.DAMAGE_MAX_ME, hit.type)
                    val seen = bob.damage(npc, 0)
                    assertEquals(HitsplatType.DAMAGE_OTHER, seen.type, "an onlooker sees an ordinary hit")

                    alice.commandReply("varbit ${MeleeCombat.VARBIT_MAX_HIT_THRESHOLD} 2") { it.startsWith("Varbit") }
                    // A hit already rolled may still land in this tick; skip what came before the change and one tick on.
                    alice.awaitTicks(2)
                    val after = alice.damage(npc, alice.npcHits().size)
                    assertEquals(HitsplatType.DAMAGE_ME, after.type, "a max of 1 is under a threshold of 2")
                    assertTrue(alice.npcHits().none { it.index == npc && it.type == HitsplatType.DAMAGE_OTHER }, "the puncher never gets the onlookers' splat")
                }
            }
        }
    }
}
