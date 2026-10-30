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
import osrsps.game.combat.NpcDrains
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** Combat through the headless client, with the real NPC combat file. */
class HeadlessCombatTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    /** Waits for [text] among every message so far: a command's later lines can arrive in the same tick as its first. */
    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `combat gives a new account's numbers against a man, and follows the style button`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                assertEquals("Punch (accurate, crush), speed 4: attack roll 768, max hit 1", client.commandReply("combat") { it.startsWith("Punch") })
                client.seen("vs Man #1 (level 2, 7 hp): defence roll 430, you hit 71.9%")
                client.seen("It: attack roll 640 vs your 576, hits 54.9%, max hit 1")
                client.command("varp 43 3")
                assertEquals("Block (defensive, crush), speed 4: attack roll 576, max hit 1", client.commandReply("combat man") { it.startsWith("Block") })
                client.seen("It: attack roll 640 vs your 768, hits 41.6%, max hit 1")
            }
        }
    }

    @Test
    fun `a drained man's defence roll falls with his Defence, and the NPC list shows it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                assertEquals("Drained Man #${npc.index}'s defence by 1 to 0/1.", client.commandReply("drain defence 1 man") { it.startsWith("Drained") })
                client.command("combat man")
                client.await("the drained defence roll") { client.messages().firstOrNull { it.text.startsWith("vs Man #${npc.index} (level 2, 7 hp): defence roll 387,") } }
                client.command("npcs 5")
                client.await("the drain in ::npcs") { client.messages().firstOrNull { it.text.contains("; drained defence 0/1, a level back in ") } }
            }
        }
    }

    /** The man's line in `::npcs`. */
    private fun manLine(client: HeadlessClient, index: Int): String {
        val from = client.messages().size
        client.command("npcs 5")
        return client.await("the man's line") { client.messages().drop(from).firstOrNull { it.text.startsWith("#$index Man") } }.text
    }

    @Test
    fun `a drained level comes back a full minute of ticks after the drain`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                // Paused and stepped, so the minute is counted in game ticks. A command typed while paused after tick n
                // drains him on tick n + 1, the next one run, which starts the timer without counting it down.
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.commandReply("drain defence 1 man") { it.startsWith("Drained") }
                client.stepTicks(NpcDrains.RESTORE_TICKS)
                manLine(client, npc.index).let { assertTrue(it.endsWith("; drained defence 0/1, a level back in 1 ticks"), "not back yet: $it") }
                client.stepTicks(1)
                manLine(client, npc.index).let { assertTrue("drained" !in it, "back a minute on: $it") }
                client.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `attacking a man punches him to death, with XP for every point, and he comes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                // He hits back now, and the dice depend on when the clicks land: ::god keeps the fight hers to win.
                assertEquals("You can't be hurt now (::god again to turn it off).", client.commandReply("god") { it.startsWith("You can") })
                client.npcOp(npc.index, 2)
                // Two tiles away: a step, then the first swing.
                client.awaitTicks(3)
                val fighting = client.commandReply("fight", timeoutMillis = 5_000) { it.startsWith("You are fighting") }
                assertTrue(fighting.startsWith("You are fighting Man #${npc.index} ("), fighting)
                // Every hit is 0 or 1, so the man takes seven of them however the dice fall: about 20 swings.
                client.await("the man's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == man.id }) Unit else null }
                assertEquals(7 * 4, client.stat(ATTACK)!!.xp)
                assertEquals(1_154 + 9, client.stat(HITPOINTS)!!.xp)
                client.await("the man's return", timeoutMillis = 10_000) { client.npcs().firstOrNull { it.id == man.id } }
                assertEquals("You are not fighting anything.", client.commandReply("fight") { it.startsWith("You are") })
            }
        }
    }

    @Test
    fun `a man hits back, with auto retaliate off you take it, with it on you fight, and god and heal shrug it off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                assertEquals("Varp 172: 0 -> 1", client.commandReply("varp 172 1") { it.startsWith("Varp 172") })
                client.npcOp(npc.index, 2)
                client.awaitTicks(3)
                // One punch, then she walks off west; the man comes after her and she does nothing about it.
                client.walk(3220, 3218)
                client.await("the man's first hit on her", timeoutMillis = 60_000) {
                    client.commandReply("hp") { it.startsWith("You have") }.takeIf { it.contains("under attack by Man #${npc.index}") }
                }
                assertEquals("You are not fighting anything.", client.commandReply("fight") { it.startsWith("You are") })
                // Every hit is 0 or 1 and about half land, so a point goes within a minute however the dice fall.
                client.await("a hitpoint lost", timeoutMillis = 60_000) { client.stat(HITPOINTS)?.takeIf { it.level < 10 } }
                assertEquals("You are not fighting anything.", client.commandReply("fight") { it.startsWith("You are") })
                // Auto retaliate back on: his next hit starts her fight.
                assertEquals("Varp 172: 1 -> 0", client.commandReply("varp 172 0") { it.startsWith("Varp 172") })
                val fighting = client.await("her retaliation", timeoutMillis = 15_000) {
                    client.commandReply("fight") { it.startsWith("You are") }.takeIf { it.startsWith("You are fighting") }
                }
                assertTrue(fighting.startsWith("You are fighting Man #${npc.index} ("), fighting)
                // God: no more damage; heal: back to full.
                assertEquals("You can't be hurt now (::god again to turn it off).", client.commandReply("god") { it.startsWith("You can") })
                assertEquals("Healed to 10 hitpoints.", client.commandReply("heal") { it.startsWith("Healed") })
                client.await("full hitpoints", timeoutMillis = 5_000) { client.stat(HITPOINTS)?.takeIf { it.level == 10 } }
                client.awaitTicks(12)
                assertEquals(10, client.stat(HITPOINTS)!!.level)
                val hp = client.commandReply("hp") { it.startsWith("You have") }
                assertTrue(hp.startsWith("You have 10/10 hitpoints; under attack by Man #${npc.index}, hit ") && hp.endsWith("; ::god is on."), hp)
            }
        }
    }

    @Test
    fun `a killed man drops his bones for his killer alone, who takes them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                assertEquals("Man always drops: Bones", client.commandReply("drops man") { it.startsWith("Man always") })
                client.seen("Also: Clue scroll (beginner) 1/90, Clue scroll (easy) 1/128")
                assertEquals("Killed Man #${npc.index}.", client.commandReply("kill man") { it.startsWith("Killed") })
                // The drops show as the body goes, four ticks on: the bones always, the rest as the dice fall.
                val bones = client.await("the bones on the floor", timeoutMillis = 10_000) { client.groundObjs().firstOrNull { it.id == BONES } }
                assertEquals(1, bones.count)
                server.login("erin").use { other ->
                    other.await("the login rebuild") { other.position }
                    other.awaitTicks(2)
                    assertTrue(other.groundObjs().none { it.id == BONES }, "the bones are the killer's for a minute")
                }
                client.objOp(BONES, bones.coord.x, bones.coord.z, TAKE)
                client.await("the bones in the backpack", timeoutMillis = 10_000) { client.inventory(BACKPACK).firstOrNull { it?.id == BONES } }
                client.await("the bones off the floor") { if (client.groundObjs().none { it.id == BONES }) Unit else null }
            }
        }
    }

    companion object {
        /** Skill ids as the stat packet carries them. */
        private const val ATTACK = 0
        private const val HITPOINTS = 3
        private const val BONES = 526
        private const val BACKPACK = 93
        private const val TAKE = 3
    }
}
