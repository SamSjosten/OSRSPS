package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Cook
import osrsps.game.content.DukeHoracio
import osrsps.game.content.LumbridgeGuide
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** The castle NPCs' conversations through the headless client, with no dialogue file: every line shows its key. */
class HeadlessCastleNpcTest {
    private val cook = NpcSpawn(NpcType(Cook.ID, "Cook", ops = listOf("Talk-to", null, null, null, null)), 3221, 3220, source = "test")
    private val duke = NpcSpawn(NpcType(DukeHoracio.ID, "Duke Horacio", ops = listOf("Talk-to", null, null, null, null)), 3221, 3220, source = "test")
    private val guide = NpcSpawn(NpcType(LumbridgeGuide.ID, "Lumbridge Guide", ops = listOf("Talk-to", null, null, null, null)), 3221, 3220, source = "test")

    private fun server(dir: Path, npc: NpcSpawn) = TestServer(dir) { crcs, millis ->
        val types = arrayOfNulls<ObjType>(LumbridgeGuide.SECURITY_BOOK_OBJ + 1)
        types[LumbridgeGuide.SECURITY_BOOK_OBJ] = ObjType(LumbridgeGuide.SECURITY_BOOK_OBJ, "Security book")
        types[DukeHoracio.ANTI_DRAGON_SHIELD] = ObjType(DukeHoracio.ANTI_DRAGON_SHIELD, "Anti-dragon shield")
        types[BRONZE_AXE] = ObjType(BRONZE_AXE, "Bronze axe")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(types), npcSpawns = listOf(npc),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("the NPC in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, 1)
    }

    /** Continues each page that follows, checking it is [expected] (an NPC's or the player's line). */
    private fun HeadlessClient.says(vararg expected: Pair<String, String>) {
        for ((name, text) in expected) {
            val page = awaitDialogue("$name saying $text") { it is DialogueView.Chat && it.text == text } as DialogueView.Chat
            assertEquals(name, page.name)
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg options: String) =
        assertEquals(options.toList(), (awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu).options)

    @Test
    fun `the Cook talks about his range`(@TempDir dir: Path) {
        server(dir, cook).use { server ->
            server.login("alice").use { client ->
                client.talkTo(Cook.ID)
                client.says("Cook" to "[cook.greeting]")
                client.menu("[cook.other_quests]", "[cook.strong]", "[cook.dying]", "[cook.range]")
                client.chooseOption(4)
                client.says(
                    "alice" to "[cook.range]",
                    "Cook" to "[cook.range_yes]",
                    "Cook" to "[cook.range_name]",
                    "alice" to "[cook.burn_less]",
                    "Cook" to "[cook.salesman]",
                    "alice" to "[cook.thanks]",
                )
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `the Cook has no other quests and answers straight after the option`(@TempDir dir: Path) {
        server(dir, cook).use { server ->
            server.login("bob").use { client ->
                client.talkTo(Cook.ID)
                client.says("Cook" to "[cook.greeting]")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.says("Cook" to "[cook.nothing_to_do]")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `the Duke gives a shield once, then stops offering it`(@TempDir dir: Path) {
        server(dir, duke).use { server ->
            server.login("carol").use { client ->
                client.talkTo(DukeHoracio.ID)
                client.says("Duke Horacio" to "[duke.greeting]")
                client.menu("[duke.shield]", "[duke.quests]", "[duke.money]")
                client.chooseOption(1)
                client.says(
                    "carol" to "[duke.shield]",
                    "Duke Horacio" to "[duke.which_dragon]",
                    "carol" to "[duke.no_dragon]",
                    "Duke Horacio" to "[duke.earned]",
                )
                assertEquals(DialogueView.Other(Chatbox.OBJECTBOX), client.awaitDialogue("the shield") { it is DialogueView.Other })
                assertEquals(listOf(InvObj(DukeHoracio.ANTI_DRAGON_SHIELD, 1)), client.inventory(BACKPACK).filterNotNull())

                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                client.talkTo(DukeHoracio.ID)
                client.says("Duke Horacio" to "[duke.greeting]")
                client.menu("[duke.quests]", "[duke.money]")
                client.chooseOption(1)
                client.says("carol" to "[duke.quests]", "Duke Horacio" to "[duke.all_well]")
            }
        }
    }

    @Test
    fun `with a full inventory the shield lands at the player's feet`(@TempDir dir: Path) {
        server(dir, duke).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BRONZE_AXE 28")
                client.await("a full inventory") { client.inventory(BACKPACK).filterNotNull().takeIf { it.size == 28 } }
                client.talkTo(DukeHoracio.ID)
                client.says("Duke Horacio" to "[duke.greeting]")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.says(
                    "dave" to "[duke.shield]",
                    "Duke Horacio" to "[duke.which_dragon]",
                    "dave" to "[duke.no_dragon]",
                    "Duke Horacio" to "[duke.earned]",
                )
                val obj = client.await("the shield on the ground") { client.groundObjs().firstOrNull { it.id == DukeHoracio.ANTI_DRAGON_SHIELD } }
                assertEquals(client.position, obj.coord)
            }
        }
    }

    @Test
    fun `the Lumbridge Guide hands over a security book from his security menu`(@TempDir dir: Path) {
        server(dir, guide).use { server ->
            server.login("erin").use { client ->
                client.talkTo(LumbridgeGuide.ID)
                client.says("Lumbridge Guide" to "[lumbridge_guide.greeting]")
                client.menu(
                    "[lumbridge_guide.quest]", "[lumbridge_guide.monsters]", "[lumbridge_guide.money]", "[lumbridge_guide.info]",
                    "[lumbridge_guide.more_options]",
                )
                client.chooseOption(5)
                client.menu("[lumbridge_guide.security]", "[lumbridge_guide.bank]", "[lumbridge_guide.no_help]", "[lumbridge_guide.previous]")
                client.chooseOption(1)
                client.says(
                    "erin" to "[lumbridge_guide.security]",
                    "Lumbridge Guide" to "[lumbridge_guide.security_1]",
                    "Lumbridge Guide" to "[lumbridge_guide.security_topics_1]",
                    "Lumbridge Guide" to "[lumbridge_guide.security_topics_2]",
                )
                client.awaitDialogue("the security menu") { it is DialogueView.Menu }
                client.chooseOption(4)
                client.says(
                    "erin" to "[lumbridge_guide.stronghold]",
                    "Lumbridge Guide" to "[lumbridge_guide.stronghold_1]",
                    "erin" to "[lumbridge_guide.fell_on_head]",
                    "Lumbridge Guide" to "[lumbridge_guide.stronghold_2]",
                    "erin" to "[lumbridge_guide.how_so]",
                    "Lumbridge Guide" to "[lumbridge_guide.stronghold_3]",
                    "erin" to "[lumbridge_guide.where_explorer]",
                    "Lumbridge Guide" to "[lumbridge_guide.stronghold_4]",
                )
                assertEquals(DialogueView.Other(Chatbox.OBJECTBOX), client.awaitDialogue("the book") { it is DialogueView.Other })
                assertEquals(listOf(InvObj(LumbridgeGuide.SECURITY_BOOK_OBJ, 1)), client.inventory(BACKPACK).filterNotNull())
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.says("Lumbridge Guide" to "[lumbridge_guide.anything_else]")
                client.menu("[lumbridge_guide.more_security]", "[lumbridge_guide.something_else]", "[lumbridge_guide.no_thanks]")
                client.chooseOption(3)
                client.says("erin" to "[lumbridge_guide.no_thanks]")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    private companion object {
        const val BRONZE_AXE = 1351
        const val BACKPACK = 93
    }
}
