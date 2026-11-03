package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import osrsps.game.content.VarrockFighters
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Varrock step 12 through the headless client, with the real combat and drop files: a mugger goes for a player of
 * twice its level or less and leaves a stronger one alone, and dies leaving its bones; the Blue Moon Inn's barbarian
 * talks every way her talk goes. The rl-client script test-runner/examples/varrock-fighters.json
 * does the same in the real city, and kills a thief and the Black Knight.
 */
class HeadlessVarrockFightersTest {
    private val mugger = NpcType(
        MUGGER, "Mugger", ops = listOf(null, "Attack", null, null, null), combatLevel = 6, internalName = "mugger",
        stats = NpcStats(attack = 5, strength = 5, defence = 5, hitpoints = 8),
    )
    private val barbarian = NpcType(
        VarrockFighters.BLUE_MOON_BARBARIAN, "Barbarian", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 8,
        internalName = "barbarianwoman_varrock", stats = NpcStats(attack = 6, strength = 5, defence = 5, hitpoints = 14),
    )

    private fun server(dir: Path, vararg spawns: NpcSpawn) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(mugger, barbarian)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(11), objTypes = objTypes, npcTypes = types,
            npcSpawns = spawns.toList(), npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            cycleMillis = millis,
        )
    }

    @Test
    fun `a mugger goes for a level 3 player and dies leaving its bones, but leaves a stronger one alone`(@TempDir dir: Path) {
        // Out of its hunt range of the login tile, so it looks only once a player is put beside it.
        server(dir, NpcSpawn(mugger, 3240, 3218, wander = 0, source = "test")).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                val npc = world.npcs.all.first { it.type.id == MUGGER }
                val alice = assertNotNull(world.playerNamed("alice"))
                // Combat level 13, over twice its 6: it lets her be.
                for (skill in listOf("attack", "strength", "defence", "hitpoints")) client.commandReply("setlevel $skill 13") { it.contains("13") }
                assertTrue(alice.skills.combatLevel > 12, "combat level ${alice.skills.combatLevel}")
                client.commandReply("tele 3238 3218") { it.startsWith("Teleported") }
                client.awaitTicks(8)
                assertNull(npc.target, "it left a level ${alice.skills.combatLevel} alone")
                // Back to level 3: now it goes for her.
                for (skill in listOf("attack", "strength", "defence")) client.commandReply("setlevel $skill 1") { it.contains("level 1") }
                client.commandReply("setlevel hitpoints 10") { it.contains("10") }
                assertTrue(alice.skills.combatLevel <= 12, "combat level ${alice.skills.combatLevel}")
                client.await("the mugger after her", timeoutMillis = 10_000) { if (npc.target === alice) Unit else null }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure") { it.startsWith("Your attacks can't miss") }
                client.commandReply("setlevel strength 99") { it.contains("99") }
                val view = client.await("the mugger in view") { client.npcs().firstOrNull { it.id == MUGGER } }
                client.npcOp(view.index, 2)
                client.await("its bones", timeoutMillis = 15_000) { client.groundObjs().map { it.id }.takeIf { BONES in it } }
            }
        }
    }

    @Test
    fun `the Blue Moon Inn's barbarian talks every way, and doesn't attack`(@TempDir dir: Path) {
        server(dir, NpcSpawn(barbarian, 3224, 3218, wander = 0, source = "test")).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                val npc = world.npcs.all.first { it.type.id == VarrockFighters.BLUE_MOON_BARBARIAN }
                val view = client.await("the barbarian in view") { client.npcs().firstOrNull { it.id == VarrockFighters.BLUE_MOON_BARBARIAN } }
                fun chat(key: String) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[blue_moon_barbarian.$key]" }
                    client.continueDialogue()
                }
                fun talk(option: Int, vararg after: String) {
                    client.npcOp(view.index, VarrockFighters.TALK_TO)
                    chat("hello")
                    chat("what_want")
                    val options = listOf("drink", "fight", "um").map { "[blue_moon_barbarian.$it]" }
                    client.awaitDialogue("the menu") { it is DialogueView.Menu && it.options == options }
                    client.chooseOption(option)
                    for (key in after) chat(key)
                    client.await("the talk over") { if (client.dialogueView == null) Unit else null }
                }
                talk(
                    1, "drink_1 name=bob", "drink_2", "drink_3", "drink_4", "drink_5",
                    "yell", "player_yell", "yell", "player_yell", "drink_end name=bob",
                )
                // Her scream shows over her head too, once for each time she screams.
                assertEquals(2, client.npcSaid().count { it.text == "[blue_moon_barbarian.yell]" })
                talk(2, "fight", "fight_answer")
                talk(3, "um", "um_answer")
                client.awaitTicks(5)
                assertNull(npc.target, "she talks, and leaves the fighting to the player")
            }
        }
    }

    private companion object {
        const val MUGGER = 513
        const val BONES = 526
    }
}
