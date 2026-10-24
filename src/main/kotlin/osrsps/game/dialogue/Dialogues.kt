package osrsps.game.dialogue

import net.rsprot.protocol.game.outgoing.interfaces.IfSetAnim
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetPlayerHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Interfaces
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.npc.Npc
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.script.ScriptRunner

/**
 * Chat dialogues: an NPC or the player talking, plain message boxes, item boxes and option menus, shown in the chatbox
 * while the content script waits for "Click here to continue" or an option. Start one from a handler:
 *
 * ```
 * world.handlers.onNpc(HANS, 1) { op -> world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.NEUTRAL, "Hello.") } }
 * ```
 *
 * Each page shows until the player continues; the script then carries on straight away, and if it shows nothing else
 * the dialogue closes (as RSMod's captures of live have it). Walking, clicking something else, Esc, a strong script, `::tele`
 * or ctrl+shift-click closes the dialogue and drops the script. [osrsps.game.World.teleport] itself doesn't, so content can
 * move the player in the middle of a conversation. Text longer than four lines goes onto more pages.
 *
 * The packets for each kind of box, their order, and the line spacings follow RSMod's captures of live (rev 233), checked
 * against the 241 cache's interfaces and scripts by name and argument count. Not yet compared with our own recording.
 */
class Dialogues(
    private val interfaces: Interfaces,
    private val scripts: ScriptRunner,
    private val font: ChatFont,
    /** The NPC type [Player] sees for an NPC (a multi-NPC shows one by their vars), null for none. */
    private val shownType: (Npc, Player) -> NpcType?,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    /** Runs [block] as a dialogue in [context]'s script, about [npc] if it has one. */
    suspend fun start(context: ScriptContext, npc: Npc? = null, block: suspend Dialogue.() -> Unit) {
        Dialogue(context, this, npc).block()
    }

    /**
     * The client sent resume_pausebutton for [componentId] of [interfaceId], slot [sub] (-1 for a continue, the option
     * from 1 in a chat menu). The script waiting for it carries on now; if it opens no new dialogue, the old one closes.
     * Inferred from RSMod, which closes the chat modal once the script has run unless the script replaced it. Returns
     * false, doing nothing, when no script waits for that click (an old or forged packet).
     */
    fun resume(player: Player, interfaceId: Int, componentId: Int, sub: Int): Boolean {
        val opens = player.chatModalOpens
        if (!scripts.resumePauseButton(player, Op.packButton(interfaceId, componentId), sub)) return false
        if (player.chatModalOpens == opens) interfaces.closeChatModal(player)
        return true
    }

    /** [npc] says [text], its head on the left with expression [anim] (null for a still head). */
    internal suspend fun chatNpc(context: ScriptContext, npc: Npc, anim: ChatAnim?, text: String) {
        val player = context.player
        val type = shownType(npc, player) ?: npc.type
        for (lines in pages(text, CHAT_WIDTH)) {
            showChat(player, Chatbox.CHAT_LEFT, type.name, lines, anim, IfSetNpcHead(Chatbox.CHAT_LEFT, Chatbox.CHAT_HEAD, type.id))
            context.pauseButton(Chatbox.CHAT_LEFT, Chatbox.CHAT_CONTINUE)
        }
    }

    /** The player says [text], their own head on the right. */
    internal suspend fun chatPlayer(context: ScriptContext, anim: ChatAnim?, text: String) {
        val player = context.player
        for (lines in pages(text, CHAT_WIDTH)) {
            showChat(player, Chatbox.CHAT_RIGHT, player.username, lines, anim, IfSetPlayerHead(Chatbox.CHAT_RIGHT, Chatbox.CHAT_HEAD))
            context.pauseButton(Chatbox.CHAT_RIGHT, Chatbox.CHAT_CONTINUE)
        }
    }

    private fun showChat(player: Player, interfaceId: Int, title: String, lines: List<String>, anim: ChatAnim?, head: OutgoingGameMessage) {
        val text = lines.joinToString(LINE_BREAK)
        send(player, MessageGame(Chatbox.CHATTYPE_DIALOG, "$title|$text"))
        interfaces.openChatModal(player, interfaceId, Chatbox.UNCLAMP_FIXED)
        send(player, head)
        send(player, IfSetAnim(interfaceId, Chatbox.CHAT_HEAD, anim?.seq(lines.size) ?: -1))
        send(player, IfSetText(interfaceId, Chatbox.CHAT_NAME, title))
        send(player, IfSetText(interfaceId, Chatbox.CHAT_TEXT, text))
        alignText(player, interfaceId, Chatbox.CHAT_TEXT, CHAT_LINE_HEIGHTS[lines.size - 1])
        continueButton(player, interfaceId, Chatbox.CHAT_CONTINUE)
    }

    /** [text] in a plain box, with no head. */
    internal suspend fun mesbox(context: ScriptContext, text: String) {
        val player = context.player
        for (lines in pages(text, MESBOX_WIDTH)) {
            val page = lines.joinToString(LINE_BREAK)
            send(player, MessageGame(Chatbox.CHATTYPE_MESBOX, page))
            // RSMod opens a messagebox without the unclamp varbit or background reset that the other boxes get.
            interfaces.openChatModal(player, Chatbox.MESSAGEBOX, unclamp = null)
            send(player, IfSetText(Chatbox.MESSAGEBOX, Chatbox.MESSAGEBOX_TEXT, page))
            alignText(player, Chatbox.MESSAGEBOX, Chatbox.MESSAGEBOX_TEXT, MESBOX_LINE_HEIGHTS[lines.size - 1])
            continueButton(player, Chatbox.MESSAGEBOX, Chatbox.MESSAGEBOX_CONTINUE)
            send(player, RunClientScript(Chatbox.MESSAGEBOX_SCRIPT, listOf("0")))
            context.pauseButton(Chatbox.MESSAGEBOX, Chatbox.MESSAGEBOX_CONTINUE)
        }
    }

    /** Item [obj] beside [text]; [zoom] is the model's zoom (RSMod's default 400). */
    internal suspend fun objbox(context: ScriptContext, obj: Int, text: String, zoom: Int) {
        val player = context.player
        for (lines in pages(text, CHAT_WIDTH)) {
            val page = lines.joinToString(LINE_BREAK)
            send(player, MessageGame(Chatbox.CHATTYPE_MESBOX, page))
            interfaces.openChatModal(player, Chatbox.OBJECTBOX, Chatbox.UNCLAMP_INFINITE)
            send(player, RunClientScript(Chatbox.OBJBOX_SET_BUTTONS, listOf(Chatbox.CONTINUE_TEXT)))
            // The script builds the button as slots of universe; RSMod enables slots 0 and 1.
            send(player, IfSetEventsV2(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0, 1, Chatbox.EVENT_PAUSE_BUTTON, 0))
            send(player, IfSetObject(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_ITEM, obj, zoom))
            send(player, IfSetText(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT, page))
            context.pauseButton(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0..1)
        }
    }

    /** Items [obj1] and [obj2] beside [text]. */
    internal suspend fun doubleobjbox(context: ScriptContext, obj1: Int, obj2: Int, text: String, zoom1: Int, zoom2: Int) {
        val player = context.player
        for (lines in pages(text, CHAT_WIDTH)) {
            val page = lines.joinToString(LINE_BREAK)
            send(player, MessageGame(Chatbox.CHATTYPE_MESBOX, page))
            interfaces.openChatModal(player, Chatbox.OBJECTBOX_DOUBLE, Chatbox.UNCLAMP_INFINITE)
            continueButton(player, Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_PAUSEBUTTON)
            send(player, IfSetObject(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_MODEL1, obj1, zoom1))
            send(player, IfSetObject(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_MODEL2, obj2, zoom2))
            send(player, IfSetText(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_TEXT, page))
            context.pauseButton(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_PAUSEBUTTON)
        }
    }

    /** A chat menu of [options] under [title]; returns the one picked, from 1. */
    internal suspend fun choice(context: ScriptContext, title: String, options: List<String>): Int {
        require(options.size in 1..MAX_OPTIONS) { "A chat menu has 1 to $MAX_OPTIONS options, not ${options.size}" }
        require(options.none { '|' in it }) { "Options can't hold '|', which separates them" }
        val player = context.player
        interfaces.openChatModal(player, Chatbox.CHATMENU, Chatbox.UNCLAMP_INFINITE)
        send(player, RunClientScript(Chatbox.MULTI_INIT_SCRIPT, listOf(title, options.joinToString("|"))))
        send(player, IfSetEventsV2(Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, 1, options.size, Chatbox.EVENT_PAUSE_BUTTON, 0))
        return context.pauseButton(Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, 1..options.size)
    }

    /** [text]'s lines in a component [width] pixels wide, four to a page. */
    internal fun pages(text: String, width: Int): List<List<String>> = font.splitLines(text, width).chunked(ChatAnim.MAX_LINES)

    private fun alignText(player: Player, interfaceId: Int, componentId: Int, lineHeight: Int) {
        // Centred both ways, as RSMod sends it.
        val args = listOf<Any>(1, 1, lineHeight, Op.packButton(interfaceId, componentId))
        send(player, RunClientScript(Chatbox.TEXT_ALIGN_SCRIPT, args))
    }

    /** Lets the client send the continue click, and labels the button. */
    private fun continueButton(player: Player, interfaceId: Int, componentId: Int) {
        send(player, IfSetEventsV2(interfaceId, componentId, -1, -1, Chatbox.EVENT_PAUSE_BUTTON, 0))
        send(player, IfSetText(interfaceId, componentId, Chatbox.CONTINUE_TEXT))
    }

    companion object {
        /** The text components' widths in the 241 cache: chat_left:text and the objectboxes' are 380; messagebox:text fills its 479-wide parent. */
        const val CHAT_WIDTH = 380
        const val MESBOX_WIDTH = 479

        const val MAX_OPTIONS = 5

        /** How the lines of a page are joined in what is sent: as `<br>`, so the client breaks where we counted. */
        const val LINE_BREAK = "<br>"

        /** Line spacing for a page of one to four lines, from RSMod's captures of live; not measured by us. */
        private val CHAT_LINE_HEIGHTS = intArrayOf(16, 28, 20, 16)
        private val MESBOX_LINE_HEIGHTS = intArrayOf(0, 31, 24, 17)
    }
}

/**
 * A dialogue being shown in a content script: what [Dialogues.start] runs. [npc] is who the player talks to, for
 * [chatNpc]; null when the dialogue isn't with an NPC.
 */
class Dialogue internal constructor(val context: ScriptContext, private val dialogues: Dialogues, val npc: Npc?) {
    val player: Player get() = context.player

    /** The NPC says [text]. Needs [npc]. */
    suspend fun chatNpc(anim: ChatAnim?, text: String) =
        dialogues.chatNpc(context, npc ?: error("chatNpc needs a dialogue started with an NPC"), anim, text)

    /** [npc], which needn't be the one the dialogue is with, says [text]. */
    suspend fun chatNpc(npc: Npc, anim: ChatAnim?, text: String) = dialogues.chatNpc(context, npc, anim, text)

    suspend fun chatPlayer(anim: ChatAnim?, text: String) = dialogues.chatPlayer(context, anim, text)

    suspend fun mesbox(text: String) = dialogues.mesbox(context, text)

    suspend fun objbox(obj: Int, text: String, zoom: Int = DEFAULT_ZOOM) = dialogues.objbox(context, obj, text, zoom)

    suspend fun doubleobjbox(obj1: Int, obj2: Int, text: String, zoom1: Int = DEFAULT_ZOOM, zoom2: Int = DEFAULT_ZOOM) =
        dialogues.doubleobjbox(context, obj1, obj2, text, zoom1, zoom2)

    /** Shows [options] under [title] and returns the one picked, from 1. */
    suspend fun choice(vararg options: String, title: String = Chatbox.SELECT_AN_OPTION): Int =
        dialogues.choice(context, title, options.toList())

    suspend fun delay(ticks: Int = 1) = context.delay(ticks)

    companion object {
        /** RSMod's default item zoom in an objectbox. */
        const val DEFAULT_ZOOM = 400
    }
}
