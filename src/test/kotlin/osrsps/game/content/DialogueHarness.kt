package osrsps.game.content

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.GameRandom
import osrsps.game.Interfaces
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.dialogue.ChatFont
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Dialogues
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap

/**
 * A content conversation with no dialogue file, so every line shows its key, and what the chatbox shows read back:
 * [expect] the pages since the last check, then [menu] or [pick] for an option menu.
 */
class DialogueHarness(npcName: String = "Someone") {
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val send: (Player, OutgoingGameMessage) -> Unit = { _, message -> sent += message }
    private val interfaces = Interfaces(send)
    private val scripts = ScriptRunner(Handlers(), interfaces::closeModals) { 0 }
    val dialogues = Dialogues(interfaces, scripts, ChatFont.APPROXIMATE, { npc, _ -> npc.type }, send)
    val npc = Npcs(CollisionMap(), GameRandom(1)).add(NpcSpawn(NpcType(1, npcName), 3221, 3219, source = "test"))!!
    val player = Player(1, "Zezima", 3222, 3219, 0)

    /** The interface of the page showing now, to continue it. */
    private var page = -1

    /** Whether an option was just picked, so a menu can follow with no page to continue. */
    private var picked = false

    fun talk(block: suspend Dialogue.() -> Unit) {
        scripts.start(player) { dialogues.start(this, npc) { block() } }
    }

    /** Checks the next pages are [keys] (`[key]`, as with no words), continuing each but the last. */
    fun expect(vararg keys: String) {
        for ((i, key) in keys.withIndex()) {
            if (i > 0) next()
            val texts = sent.filterIsInstance<IfSetText>().filter { it.componentId == Chatbox.CHAT_TEXT || (it.interfaceId == Chatbox.MESSAGEBOX && it.componentId == Chatbox.MESSAGEBOX_TEXT) }
            assertEquals(listOf("[$key]"), texts.filter { it.interfaceId != Chatbox.CHATMENU }.map { it.text }, "page ${i + 1}")
            page = texts.last().interfaceId
            sent.clear()
        }
        picked = false
    }

    /** Continues the page showing. */
    fun next() = assertTrue(dialogues.resume(player, page, if (page == Chatbox.MESSAGEBOX) Chatbox.MESSAGEBOX_CONTINUE else Chatbox.CHAT_CONTINUE, -1))

    /** Continues the page showing (unless an option was just picked) and checks the menu that follows has [keys]. */
    fun menu(vararg keys: String) {
        if (!picked) next()
        val menu = sent.filterIsInstance<RunClientScript>().last { it.id == Chatbox.MULTI_INIT_SCRIPT }.values[1].toString()
        assertEquals(keys.joinToString("|") { "[$it]" }, menu)
        sent.clear()
    }

    /** Picks [option], from 1, in the menu showing. */
    fun pick(option: Int) {
        assertTrue(dialogues.resume(player, Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, option))
        picked = true
    }

    /** Continues the last page and checks the conversation is over. */
    fun ends() {
        next()
        assertEquals(-1, player.chatModal)
    }
}
