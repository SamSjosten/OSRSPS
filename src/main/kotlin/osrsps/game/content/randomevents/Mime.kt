package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.Tile

/** The Mime show's lines; the words are in the dialogue file, from the Mysterious Old Man's transcript (read 2026-11-19). */
object MimeLines : Lines("mime", "Transcript:Mysterious Old Man, the Mime Show version") {
    val INVITE = line("invite", "Standard dialogue - Mime Show version: his question", "name")
    val YES = line("yes", "Mime Show version: the first option, and the player's words")
    val BUSY = line("busy", "Mime Show version: the second option, and the player's words")
    val COPY = line("copy", "Mime Show version: after the teleport")
}

/**
 * The Mime (OSRS Wiki, Mime (Random Event) and Mime pages and the Mysterious Old Man's transcript, read 2026-11-19).
 * The Mysterious Old Man comes as NPC 6753 (macro_mime_invitation; Talk-to, Dismiss) and asks his player to perform in
 * a mime show; yes takes them to the stage (map square 31,74, [EventAreas]), where the mime (321, macro_mime) does one
 * of eight emotes and the player copies it from the chatbox window macro_mime_emotes (188; Think, Laugh, Climb rope,
 * Glass box, Cry, Dance, Lean, Glass wall, buttons 2 to 9). Right, the player cheers; wrong, they cry; too slow, the mime
 * does another. Four right send the player back with one or two mime outfit pieces not yet had, else a mime emote not
 * yet had (Glass box, Climb rope, Lean, Glass wall), else a lamp. The animations are the emote tab's
 * ([osrsps.game.content.Emotes]).
 *
 * Guessed: where the mime and player stand (under the two spotlights and ropes); the stage as a private copy (the Wiki
 * says live's room is shared); how long the mime's emote plays before the window opens ([PERFORM_TICKS]) and how long
 * the player has ([ANSWER_TICKS]); that the show starts by itself [COPY_TICKS] after the mime's
 * opening line if that line isn't continued; that the mime's emotes are the player's animations; one piece or two at random, and
 * which; that a wrong copy doesn't start the four again. Left out: the teleport refusal during the show and
 * the spell message.
 */
object Mime : RandomEvent("mime", 6753) {
    const val MIME_NPC = 321
    const val INTERFACE = 188
    const val FIRST_BUTTON = 2
    const val ROUNDS = 4
    const val PERFORM_TICKS = 4
    const val ANSWER_TICKS = 20
    const val REACT_TICKS = 3
    const val COPY_TICKS = 10
    const val CHEER = 862
    const val CRY = 860

    /** The window's emotes in button order, with their animations. */
    enum class Emote(val anim: Int) {
        THINK(857), LAUGH(861), CLIMB_ROPE(1130), GLASS_BOX(1131), CRY(860), DANCE(866), LEAN(1129), GLASS_WALL(1128),
    }

    const val MASK = 3057
    const val TOP = 3058
    const val LEGS = 3059
    const val GLOVES = 3060
    const val BOOTS = 3061
    val OUTFIT = listOf(MASK, TOP, LEGS, GLOVES, BOOTS)

    /** emote_glassbox, emote_climbrope, emote_lean, emote_glasswall. */
    val EMOTE_VARBITS = listOf(1368, 1369, 1370, 1367)

    /** The zones copied: the theatre of map square 31,74. */
    const val FROM_X = 2000
    const val FROM_Z = 4744
    const val WIDTH_ZONES = 3
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(2012, 4762)
    val MIME_AT = Tile(2007, 4762)

    /** A show: the mime, the emote to copy (null between rounds), and the copies right so far. */
    class Show(val mime: Npc?) {
        var wanted: Emote? = null
        var right = 0

        /** Counts each emote the mime does, so a timeout set for one doesn't fire for the next. */
        var turn = 0
    }

    override val teleports = true
    override val nags = with(MysteriousOldManLines) { listOf(NAME, HELLO, TALK_TO_ME, ARE_YOU_THERE, LIKE_TO_TALK) }
    override val leaving = MysteriousOldManLines.MAYBE_LATER
    override val notYours = MysteriousOldManLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(MimeLines) {
        chatNpc(ChatAnim.HAPPY, INVITE, "name" to player.username)
        if (choice(YES, BUSY) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, BUSY)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, YES)
        val type = world.npcTypes[npcId]
        val away = events.areas.takeAway(player, this@Mime, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val show = Show(spawnMime(world, away))
        away.state = show
        // The show also starts on its own, so walking off or shutting the line below can't leave the player stuck on stage.
        world.npcScripts.later(2 + COPY_TICKS) {
            if (showOf(world, player)?.second !== show || show.turn != 0) return@later
            world.scripts.interrupt(player)
            perform(world, player, show)
        }
        delay(2)
        if (type != null) chatNpc(type, type.name, ChatAnim.NEUTRAL, COPY)
        if (showOf(world, player)?.second === show && show.turn == 0) perform(world, player, show)
        true
    }

    private fun spawnMime(world: World, away: EventAreas.Away): Npc? {
        val type = world.npcTypes[MIME_NPC] ?: return null
        val tile = away.inside(MIME_AT.x, MIME_AT.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "mime show for ${away.owner}", respawns = false))
    }

    private fun showOf(world: World, player: Player): Pair<EventAreas.Away, Show>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val show = away.state as? Show ?: return null
        return away to show
    }

    /** The mime does an emote; the window opens once it has played, and he does another if the player is too slow. */
    private fun perform(world: World, player: Player, show: Show) {
        val emote = Emote.entries[world.random.nextInt(Emote.entries.size)]
        val turn = ++show.turn
        show.wanted = null
        show.mime?.effects?.anim(emote.anim)
        world.npcScripts.later(PERFORM_TICKS) {
            if (showOf(world, player)?.second !== show || show.turn != turn) return@later
            show.wanted = emote
            for (i in Emote.entries.indices) player.session.queue(IfSetEventsV2(INTERFACE, FIRST_BUTTON + i, -1, -1, 0, OP1))
            world.interfaces.openChatModal(player, INTERFACE, unclamp = null)
        }
        world.npcScripts.later(PERFORM_TICKS + ANSWER_TICKS) {
            if (showOf(world, player)?.second !== show || show.turn != turn || show.wanted == null) return@later
            world.interfaces.closeChatModal(player)
            perform(world, player, show)
        }
    }

    /** A button in the window: the player does that emote, then cheers or cries. */
    fun copy(world: World, player: Player, component: Int) {
        val (away, show) = showOf(world, player) ?: return
        val wanted = show.wanted ?: return
        val picked = Emote.entries.getOrNull(component - FIRST_BUTTON) ?: return
        show.wanted = null
        world.interfaces.closeChatModal(player)
        player.anim(picked.anim)
        val right = picked == wanted
        world.npcScripts.later(REACT_TICKS) {
            if (showOf(world, player)?.second !== show) return@later
            player.anim(if (right) CHEER else CRY)
            if (right) show.right++
            world.npcScripts.later(REACT_TICKS) {
                if (showOf(world, player)?.second !== show) return@later
                if (show.right >= ROUNDS) finish(world, player, away) else perform(world, player, show)
            }
        }
    }

    private fun finish(world: World, player: Player, away: EventAreas.Away) {
        // Back first, so what doesn't fit goes on the ground there, not in the stage that is about to end.
        world.randomEvents.areas.bringBack(player, away)
        val missing = OUTFIT.filter { !owns(player, it) }.toMutableList()
        if (missing.isNotEmpty()) {
            repeat(minOf(missing.size, 1 + world.random.nextInt(2))) {
                world.randomEvents.reward(player, missing.removeAt(world.random.nextInt(missing.size)))
            }
        } else {
            val emotes = EMOTE_VARBITS.filter { player.vars.types.varbit(it) != null && player.vars.getVarbit(it) == 0 }
            if (emotes.isNotEmpty()) player.vars.setVarbit(emotes[world.random.nextInt(emotes.size)], 1)
            else world.randomEvents.reward(player, XpLamp.LAMP)
        }
    }

    private fun owns(player: Player, id: Int) = player.inventory.count(id) > 0 || player.equipment.count(id) > 0 || player.bank.count(id) > 0

    /** IF_SETEVENTS events2 bit for op 1. */
    private const val OP1 = 1 shl 0

    fun register(world: World) {
        for (i in Emote.entries.indices) world.handlers.onButton(INTERFACE, FIRST_BUTTON + i) { copy(world, player, FIRST_BUTTON + i) }
    }
}
