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
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Poison on NPCs through the headless client, with the real NPC combat file: `::poison npc` poisons a man at 30, who
 * takes 6 thirty ticks on and dies of the second hit, giving no XP; a cow can't be poisoned. The rl-client script
 * test-runner/examples/poison-npc.json does the same in the real client.
 */
class HeadlessNpcPoisonTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val cow = NpcType(2790, "Cow", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "cow", stats = NpcStats(hitpoints = 8))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man, cow)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test"), NpcSpawn(cow, 3220, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `a poisoned man takes 6 every 30 ticks and dies of it, with no XP, and a cow can't be poisoned`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val manView = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                val cowView = client.await("the cow in view") { client.npcs().firstOrNull { it.id == cow.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("poison npc 30 cow") { it.startsWith("Cow #") }.let {
                    assertEquals("Cow #${cowView.index} can't be: it is immune, envenomed or more strongly poisoned already, or can't be fought.", it)
                }
                assertEquals(
                    "Man #${manView.index} is poisoned, hitting 6 in 30 ticks.",
                    client.commandReply("poison npc 30 ${manView.index}") { it.startsWith("Man #") },
                )
                client.awaitTicks(31)
                client.command("npcs 5")
                client.await("the man on 1 hitpoint, still poisoned") {
                    client.messages().firstOrNull { it.text.startsWith("#${manView.index} Man") && it.text.contains("; 1/7 hp; poisoned, hitting 6 in ") }
                }
                client.await("the man dead and gone", timeoutMillis = 40_000) { Unit.takeIf { client.npcs().none { it.index == manView.index } } }
                assertEquals(0, client.stat(Skill.ATTACK.id)?.xp)
                assertEquals(0, client.stat(Skill.STRENGTH.id)?.xp)
            }
        }
    }
}
