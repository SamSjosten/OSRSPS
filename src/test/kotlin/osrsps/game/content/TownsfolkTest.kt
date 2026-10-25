package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.GameRandom
import osrsps.game.Interfaces
import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.Townsfolk.talk
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

class TownsfolkTest {
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val send: (Player, OutgoingGameMessage) -> Unit = { _, message -> sent += message }
    private val interfaces = Interfaces(send)
    private val scripts = ScriptRunner(Handlers(), interfaces::closeModals) { 0 }
    private val dialogues = Dialogues(interfaces, scripts, ChatFont.APPROXIMATE, { npc, _ -> npc.type }, send)
    private val world = World(
        IntArray(0), CollisionMap(),
        objTypes = ObjTypes(arrayOfNulls<ObjType>(Townsfolk.FLIER + 1).also { it[Townsfolk.FLIER] = ObjType(Townsfolk.FLIER, "Flier") }),
    )
    private val npc = Npcs(CollisionMap(), GameRandom(1)).add(NpcSpawn(NpcType(3106, "Man"), 3221, 3219, source = "test"))!!
    private val player = Player(1, "Zezima", 3222, 3219, 0)

    /** The chat pages shown since last asked: the speaker and text of each. */
    private fun shown(): List<String> {
        val pages = sent.filterIsInstance<IfSetText>().filter { it.componentId == Chatbox.CHAT_TEXT && it.interfaceId != Chatbox.CHATMENU }.map { it.text }
        sent.clear()
        return pages
    }

    private fun next(interfaceId: Int) = assertTrue(dialogues.resume(player, interfaceId, Chatbox.CHAT_CONTINUE, -1))

    private fun talk(dialogue: Int, lines: TownsfolkLines = ManLines) =
        scripts.start(player) { dialogues.start(this, npc) { talk(world, lines, dialogue) } }

    @Test
    fun `one talk in about 128 offers a flier and every other conversation comes up`() {
        val random = GameRandom(7)
        val counts = IntArray(24)
        repeat(128 * 200) { counts[Townsfolk.pick(random)]++ }
        assertTrue(counts[22] in 150..250, "flier ${counts[22]} times in ${128 * 200}")
        assertTrue((1..23).all { counts[it] > 0 })
        assertEquals(0, counts[0])
    }

    @Test
    fun `asked how they can help, the player can ask about quests`() {
        talk(2)
        assertEquals(listOf("[man.hello]"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("[man.help]"), shown())
        next(Chatbox.CHAT_LEFT)
        val menu = sent.filterIsInstance<RunClientScript>().last { it.id == Chatbox.MULTI_INIT_SCRIPT }.values[1].toString()
        assertEquals("[man.trade]|[man.quest]|[man.enemies]", menu)
        assertTrue(dialogues.resume(player, Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, 2))
        assertEquals(listOf("[man.quest]"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("[man.no_quest]"), shown())
        next(Chatbox.CHAT_LEFT)
        assertEquals(-1, player.chatModal)
    }

    @Test
    fun `a woman's lines are her own`() {
        talk(16, WomanLines)
        assertEquals(listOf("[woman.hello]"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("[woman.quest]"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("[woman.no_quest]"), shown())
    }

    @Test
    fun `wanting a fight is only words while there is no combat`() {
        talk(11)
        assertEquals(listOf("[man.hello]"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("[man.fight]"), shown())
        next(Chatbox.CHAT_LEFT)
        assertEquals(-1, player.chatModal)
    }

    @Test
    fun `the flier goes into the inventory`() {
        talk(22)
        assertEquals(listOf("[man.hello]"), shown())
        next(Chatbox.CHAT_RIGHT)
        assertEquals(listOf("[man.flier]"), shown())
        assertEquals(listOf(0 to InvObj(Townsfolk.FLIER, 1)), player.inventory.contents())
    }

    @Test
    fun `men and women in the castle have Talk-to`() {
        for (id in Townsfolk.MEN + Townsfolk.WOMEN) assertTrue(world.handlers.find(Op(OpTarget.NPC, id, Townsfolk.TALK_TO)) != null)
    }
}
