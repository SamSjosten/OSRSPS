package osrsps.game.dialogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetAnim
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetPlayerHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.TriggerOnDialogAbort
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.GameRandom
import osrsps.game.Interfaces
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap

class DialoguesTest {
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val send: (Player, OutgoingGameMessage) -> Unit = { _, message -> sent += message }
    private val interfaces = Interfaces(send)
    private val scripts = ScriptRunner(Handlers(), interfaces::closeModals) { 0 }

    /** Every character 1 pixel wide, so a line is [Dialogues.CHAT_WIDTH] characters. */
    private val font = ChatFont(IntArray(256) { 1 })
    private val dialogues = Dialogues(interfaces, scripts, font, { npc, _ -> npc.type }, send)
    private val hans = NpcType(3105, "Hans", ops = listOf("Talk-to", "Age", null, null, null))
    private val npc = Npcs(CollisionMap(), GameRandom(1)).add(NpcSpawn(hans, 3221, 3219, source = "test"))!!
    private val player = Player(1, "Zezima", 3222, 3218, 0)
    private val log = mutableListOf<String>()

    private fun talk(block: suspend Dialogue.() -> Unit) {
        scripts.start(player) {
            dialogues.start(this, npc) { block() }
            log += "done"
        }
    }

    private fun continueChat(interfaceId: Int) = dialogues.resume(player, interfaceId, Chatbox.CHAT_CONTINUE, -1)

    @Test
    fun `an NPC's line opens chat_left with its head, name, text and continue button, then waits`() {
        talk { chatNpc(ChatAnim.NEUTRAL, "Hello there, adventurer.") }
        assertEquals(
            listOf(
                "MessageGame 114 Hans|Hello there, adventurer.",
                "RunClientScript 2379 []",
                "IfOpenSub 162:568 231 modal",
                "IfSetNpcHead 231:2 3105",
                "IfSetAnim 231:2 588",
                "IfSetText 231:4 Hans",
                "IfSetText 231:6 Hello there, adventurer.",
                "RunClientScript 600 [1, 1, 16, ${(231 shl 16) or 6}]",
                "IfSetEventsV2 231:5 65535..65535 1",
                "IfSetText 231:5 Click here to continue",
            ),
            sent.map(::describe),
        )
        assertEquals(Chatbox.CHAT_LEFT, player.chatModal)
        assertTrue(scripts.busy(player))
        assertTrue(player.scripts.waitingForDialogue)
        assertEquals(emptyList(), log)
    }

    @Test
    fun `continuing the last page ends the script and closes the dialogue`() {
        talk { chatNpc(ChatAnim.NEUTRAL, "Hello.") }
        sent.clear()
        assertTrue(continueChat(Chatbox.CHAT_LEFT))
        assertEquals(listOf("done"), log)
        assertEquals(listOf("IfCloseSub 162:568"), sent.map(::describe))
        assertEquals(-1, player.chatModal)
        assertFalse(scripts.busy(player))
    }

    @Test
    fun `continuing into another page replaces the dialogue without closing it`() {
        talk {
            chatNpc(ChatAnim.NEUTRAL, "Hello.")
            chatPlayer(ChatAnim.HAPPY, "Hi!")
        }
        sent.clear()
        assertTrue(continueChat(Chatbox.CHAT_LEFT))
        val described = sent.map(::describe)
        assertTrue(described.none { it.startsWith("IfCloseSub") })
        assertTrue("IfOpenSub 162:568 217 modal" in described)
        assertTrue("IfSetPlayerHead 217:2" in described)
        assertTrue("IfSetText 217:4 Zezima" in described)
        assertTrue("IfSetAnim 217:2 567" in described)
        assertTrue("MessageGame 114 Zezima|Hi!" in described)
        assertEquals(Chatbox.CHAT_RIGHT, player.chatModal)
        assertTrue(continueChat(Chatbox.CHAT_RIGHT))
        assertEquals(listOf("done"), log)
    }

    @Test
    fun `a click on the wrong component or slot doesn't continue`() {
        talk { chatNpc(ChatAnim.NEUTRAL, "Hello.") }
        assertFalse(continueChat(Chatbox.CHAT_RIGHT))
        assertFalse(dialogues.resume(player, Chatbox.CHAT_LEFT, Chatbox.CHAT_CONTINUE, 0))
        assertTrue(player.scripts.waitingForDialogue)
        assertEquals(Chatbox.CHAT_LEFT, player.chatModal)
    }

    @Test
    fun `a chat menu returns the option picked`() {
        talk {
            val picked = choice("Yes.", "No.", "Maybe.")
            log += "picked $picked"
        }
        assertEquals(
            listOf(
                "RunClientScript 2379 []",
                "IfOpenSub 162:568 219 modal",
                "RunClientScript 58 [Select an option, Yes.|No.|Maybe.]",
                "IfSetEventsV2 219:1 1..3 1",
            ),
            sent.map(::describe),
        )
        assertFalse(dialogues.resume(player, Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, 4))
        assertTrue(dialogues.resume(player, Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, 3))
        assertEquals(listOf("picked 3", "done"), log)
        assertEquals(-1, player.chatModal)
    }

    @Test
    fun `closing the dialogue drops the script, which never carries on`() {
        talk {
            chatNpc(ChatAnim.NEUTRAL, "Hello.")
            log += "after"
        }
        sent.clear()
        // Walking, Esc or another click: ScriptRunner.interrupt closes every modal.
        scripts.interrupt(player)
        assertEquals(listOf("IfCloseSub 162:568", "TriggerOnDialogAbort"), sent.map(::describe))
        assertFalse(player.scripts.waitingForDialogue)
        assertFalse(continueChat(Chatbox.CHAT_LEFT))
        assertEquals(emptyList(), log)
    }

    @Test
    fun `long text goes onto pages of four lines, each with the animation for its line count`() {
        val words = List(5) { "x".repeat(Dialogues.CHAT_WIDTH - 1) }
        talk { chatNpc(ChatAnim.NEUTRAL, words.joinToString(" ")) }
        val firstPage = sent.filterIsInstance<IfSetText>().first { it.componentId == Chatbox.CHAT_TEXT }.text
        assertEquals(words.take(4).joinToString("<br>"), firstPage)
        assertEquals(591, sent.filterIsInstance<IfSetAnim>().single().anim)
        sent.clear()
        assertTrue(continueChat(Chatbox.CHAT_LEFT))
        assertEquals(588, sent.filterIsInstance<IfSetAnim>().single().anim)
        assertEquals(words[4], sent.filterIsInstance<IfSetText>().first { it.componentId == Chatbox.CHAT_TEXT }.text)
        assertTrue(continueChat(Chatbox.CHAT_LEFT))
        assertEquals(listOf("done"), log)
    }

    @Test
    fun `a message box has no head and goes to the chat history as a mesbox`() {
        talk { mesbox("You've been given another clue!") }
        assertEquals(
            listOf(
                "MessageGame 115 You've been given another clue!",
                "IfOpenSub 162:568 229 modal",
                "IfSetText 229:3 You've been given another clue!",
                "RunClientScript 600 [1, 1, 0, ${(229 shl 16) or 3}]",
                "IfSetEventsV2 229:4 65535..65535 1",
                "IfSetText 229:4 Click here to continue",
                "RunClientScript 1508 [0]",
            ),
            sent.map(::describe),
        )
        assertTrue(dialogues.resume(player, Chatbox.MESSAGEBOX, Chatbox.MESSAGEBOX_CONTINUE, -1))
        assertEquals(listOf("done"), log)
    }

    @Test
    fun `an item box shows the item and continues from its built button`() {
        talk { objbox(2677, "You've obtained a casket!") }
        val described = sent.map(::describe)
        assertTrue("IfOpenSub 162:568 193 modal" in described)
        assertTrue("RunClientScript 2868 [Click here to continue]" in described)
        assertTrue("IfSetEventsV2 193:0 0..1 1" in described)
        assertTrue("IfSetObject 193:1 2677 400" in described)
        assertTrue(dialogues.resume(player, Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0))
        assertEquals(listOf("done"), log)
    }

    @Test
    fun `the unclamp varbit goes out before the dialogue opens when the cache has it`() {
        val types = VarTypes(6_000, mapOf(Chatbox.VARBIT_CHATMODAL_UNCLAMP to VarbitType(4_000, 3, 3)))
        val player = Player(2, "test", 3222, 3218, 0, types)
        player.vars.setVarbit(Chatbox.VARBIT_CHATMODAL_UNCLAMP, 1)
        interfaces.openChatModal(player, Chatbox.CHAT_LEFT, Chatbox.UNCLAMP_FIXED)
        assertEquals(listOf("VarpSmall 4000 0", "RunClientScript 2379 []", "IfOpenSub 162:568 231 modal"), sent.map(::describe))
    }

    /** RSProt keeps an event range's ends as unsigned shorts, so -1 (no slot) shows as 65535; the same on the wire. */
    private fun describe(message: OutgoingGameMessage): String = when (message) {
        is MessageGame -> "MessageGame ${message.type} ${message.message}"
        is RunClientScript -> "RunClientScript ${message.id} ${message.values}"
        is IfOpenSub -> "IfOpenSub ${message.destinationInterfaceId}:${message.destinationComponentId} ${message.interfaceId} " +
            if (message.type == Interfaces.MODAL) "modal" else "overlay"
        is IfCloseSub -> "IfCloseSub ${message.interfaceId}:${message.componentId}"
        is IfSetNpcHead -> "IfSetNpcHead ${message.interfaceId}:${message.componentId} ${message.npc}"
        is IfSetPlayerHead -> "IfSetPlayerHead ${message.interfaceId}:${message.componentId}"
        is IfSetAnim -> "IfSetAnim ${message.interfaceId}:${message.componentId} ${message.anim}"
        is IfSetText -> "IfSetText ${message.interfaceId}:${message.componentId} ${message.text}"
        is IfSetObject -> "IfSetObject ${message.interfaceId}:${message.componentId} ${message.obj} ${message.count}"
        is IfSetEventsV2 ->
            "IfSetEventsV2 ${message.interfaceId}:${message.componentId} ${message.start}..${message.end} ${message.events1}"
        is VarpSmall -> "VarpSmall ${message.id} ${message.value}"
        TriggerOnDialogAbort -> "TriggerOnDialogAbort"
        else -> message.toString()
    }
}
