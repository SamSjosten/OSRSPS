package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.GameRandom
import osrsps.game.Interfaces
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.Hans.age
import osrsps.game.content.Hans.talk
import osrsps.game.dialogue.ChatFont
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Dialogues
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap
import osrsps.map.Tile

class HansTest {
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val send: (Player, OutgoingGameMessage) -> Unit = { _, message -> sent += message }
    private val interfaces = Interfaces(send)
    private val scripts = ScriptRunner(Handlers(), interfaces::closeModals) { 0 }
    private val dialogues = Dialogues(interfaces, scripts, ChatFont.APPROXIMATE, { npc, _ -> npc.type }, send)
    private val npc = Npcs(CollisionMap(), GameRandom(1))
        .add(NpcSpawn(NpcType(Hans.ID, "Hans"), 3221, 3219, source = "test"))!!
    private val player = Player(1, "Zezima", 3222, 3219, 0)

    /** What the chatbox shows: the name and text of each chat page, or the options of a menu. */
    private fun shown(): List<String> {
        val texts = sent.filterIsInstance<IfSetText>()
            .filter { it.componentId == Chatbox.CHAT_TEXT && it.interfaceId != Chatbox.CHATMENU }
            .map { it.text }
        sent.clear()
        return texts
    }

    private fun next(interfaceId: Int) = assertTrue(dialogues.resume(player, interfaceId, Chatbox.CHAT_CONTINUE, -1))

    private fun pick(option: Int) = assertTrue(dialogues.resume(player, Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, option))

    private fun startTalk(now: Long = 0) = scripts.start(player) { dialogues.start(this, npc) { talk(now) } }.also { sent.clear() }

    @Test
    fun `asking where you are gets the castle's directions`() {
        scripts.start(player) { dialogues.start(this, npc) { talk(0) } }
        assertEquals(listOf("Hello. What are you doing here?"), shown())
        next(Chatbox.CHAT_LEFT)
        pick(3)
        assertEquals(listOf("I don't know. I'm lost. Where am I?"), shown())
        next(Chatbox.CHAT_RIGHT)
        val answer = shown().single()
        assertTrue(answer.startsWith("You are in Lumbridge Castle, in the Kingdom of Misthalin."), answer)
        next(Chatbox.CHAT_LEFT)
        assertEquals(-1, player.chatModal)
    }

    @Test
    fun `threatening him sends him running from the player`() {
        startTalk()
        next(Chatbox.CHAT_LEFT)
        pick(2)
        assertEquals(listOf("I have come to kill everyone in this castle!"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("Help! Help!"), shown())
        // The player is east of Hans, so he heads west.
        assertEquals(Tile(3221 - Hans.FLEE_DISTANCE, 3219), npc.destination)
    }

    @Test
    fun `nothing ends it after the player's line`() {
        startTalk()
        next(Chatbox.CHAT_LEFT)
        pick(5)
        assertEquals(listOf("Nothing."), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(-1, player.chatModal)
    }

    @Test
    fun `age tells the time played and the account's age`() {
        player.playedTicks = (2L * 24 * 60 + 3 * 60 + 7) * 100 // 2 days, 3 hours, 7 minutes of 0.6 s ticks
        player.createdAt = 0
        val now = 10L * 24 * 60 * 60 * 1000 + 5
        scripts.start(player) { dialogues.start(this, npc) { age(now) } }
        assertTrue(shown().single().startsWith("Ahh, I see all the newcomers"))
        next(Chatbox.CHAT_LEFT)
        assertEquals(
            listOf("You've spent 2 days, 3 hours, 7 minutes in the world since you arrived 10 days ago."),
            shown().map { it.replace("<br>", " ") },
        )
    }

    @Test
    fun `talk-to and age are registered on the world`() {
        val world = World(IntArray(0), CollisionMap())
        assertTrue(world.handlers.find(Op(OpTarget.NPC, Hans.ID, Hans.TALK_TO)) != null)
        assertTrue(world.handlers.find(Op(OpTarget.NPC, Hans.ID, Hans.AGE)) != null)
    }
}
