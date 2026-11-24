package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The balloon transport system's words. */
object BalloonLines : Lines("balloon", "Balloon_transport_system") {
    val GREETING = line("greeting", "every assistant and Auguste, before the options (the Wiki's assistant transcripts)")
    val YES = line("yes", "the player's option (the transcripts)")
    val NO = line("no", "the player's option and line (the transcripts)")
    val WHO = line("who", "the player's option and line (the transcripts)")
    val AGAIN = line("again", "the assistant's question after who they are (the transcripts)")
    val WHO_STAN = line("who_stan", "Assistant Stan, Taverley (Transcript:Assistant Stan)")
    val WHO_BROCK = line("who_brock", "Assistant Brock, the Crafting Guild (Transcript:Assistant Brock)")
    val WHO_SERF = line("who_serf", "Assistant Serf, Varrock (Transcript:Assistant Serf)")
    val WHO_MARROW = line("who_marrow", "Assistant Marrow, Castle Wars (Transcript:Assistant Marrow)")
    val WHO_LE_SMITH = line("who_le_smith", "Assistant Le Smith, the Grand Tree (Transcript:Assistant Le Smith)")
    val WHY = line("why", "the player, to Le Smith (Transcript:Assistant Le Smith)")
    val HOT_AIR = line("hot_air", "Le Smith's answer (Transcript:Assistant Le Smith)")
    val NEED_LOGS = line("need_logs", "the message for a trip without its log (the assistant transcripts give it with the log's type)", "logs")
    val LOGS = line("logs", "the log's name in need_logs: normal logs (guessed, the transcripts say [type])")
    val OAK_LOGS = line("oak_logs", "the log's name in need_logs: oak logs (guessed, as logs)")
    val WILLOW_LOGS = line("willow_logs", "the log's name in need_logs: willow logs (guessed, as logs)")
    val YEW_LOGS = line("yew_logs", "the log's name in need_logs: yew logs (guessed, as logs)")
    val MAGIC_LOGS = line("magic_logs", "the log's name in need_logs: magic logs (guessed, as logs)")
    val LEVEL = line("level", "the message for a trip above the player's Firemaking level; not on the Wiki", "level")
    val TOO_HEAVY = line("too_heavy", "the message for more than 40 kg; the Wiki has only Auguste's line in the quest", "limit")
    val ENTRANA_SEARCH = line("entrana_search", "the assistant, on a trip to Entrana (Transcript:Assistant Stan, Assistant Marrow)")
    val SEARCHED = line("searched", "the box after it (the same transcripts)")
    val ENTRANA_REFUSE = line("entrana_refuse", "the assistant, to a player with weapons or armour (the same transcripts)")
}

/**
 * One balloon station: its button on the map (interface `zep_balloon_map`), its basket (a multiloc on the station's
 * varbit, south-west tile as the 242 map places it), where a trip there lands, the log a trip there burns, the Firemaking
 * level it needs, and the assistant at it (the NPC forms the station's multi-NPC shows).
 */
enum class BalloonStation(
    val button: Int,
    val basket: Int,
    val basketTile: Destination,
    val landing: Destination,
    val log: Int,
    val logName: Line,
    val level: Int,
    val assistants: List<Int>,
    val who: Line?,
) {
    ENTRANA(16, 19133, Destination(2806, 3355, 0), Destination(2806, 3354, 0), 1511, BalloonLines.LOGS, 20, listOf(4715), null),
    TAVERLEY(17, 19135, Destination(2939, 3421, 0), Destination(2939, 3420, 0), 1511, BalloonLines.LOGS, 20, listOf(4723), BalloonLines.WHO_STAN),
    CRAFTING_GUILD(15, 19141, Destination(2924, 3301, 0), Destination(2924, 3300, 0), 1521, BalloonLines.OAK_LOGS, 30, listOf(4720), BalloonLines.WHO_BROCK),
    VARROCK(18, 19143, Destination(3296, 3481, 0), Destination(3296, 3480, 0), 1519, BalloonLines.WILLOW_LOGS, 40, listOf(4719), BalloonLines.WHO_SERF),
    CASTLE_WARS(13, 19137, Destination(2461, 3109, 0), Destination(2461, 3108, 0), 1515, BalloonLines.YEW_LOGS, 50, listOf(4721), BalloonLines.WHO_MARROW),
    GRAND_TREE(14, 19139, Destination(2481, 3457, 0), Destination(2481, 3456, 0), 1513, BalloonLines.MAGIC_LOGS, 60, listOf(4722, 7154), BalloonLines.WHO_LE_SMITH),
}

/**
 * The balloon transport system (travel networks step T5), with every route open (Sam, 19 Nov 2026): each account counts
 * as having finished Enlightened Journey and flown every route, so every basket stands ready
 * ([osrsps.game.Quests.AFTER_QUESTS]) and no route needs the flying minigame.
 *
 * - A basket's Use, an assistant's Fly (or Talk-to, then Yes) opens the map; a destination's Travel flies there.
 * - A trip needs the destination's Firemaking level (boosts count), one of its logs in the backpack, and no more than
 *   40 kg carried without that log; a trip to Entrana also needs no weapons or armour, which the assistant searches for.
 * - The log is burnt, the balloon journey jingle plays, and the player lands beside the destination's basket.
 *
 * Sources: the Wiki's Balloon transport system page (read 23 Nov 2026) for the routes, logs, levels, weight limit and
 * the Use change of 9 August 2018; its assistant transcripts for the talk, the log message and the Entrana search; the
 * 242 cache for the baskets, their varbits, the map interface and its buttons, the NPCs and the jingle; mejrs's live NPC
 * list for where the assistants stand.
 *
 * Inferred: a basket's varbit at its value with the basket ready (2 for Entrana's and Taverley's, which the quest
 * builds in two steps, 1 for the others). Guessed: where a trip lands (the tile south of the basket's south-west corner,
 * walkable on the 242 map); the trip itself (the map closing, the jingle and a move [FLIGHT_TICKS] later, with no flying
 * screen or fade); the Firemaking and weight messages' words (keys with no words until a recording); a click on the
 * station the player is at doing nothing; Entrana's rule (any carried or worn item worn in the head, weapon, body,
 * shield, legs, hands or feet slot with an attack or defence bonus); the player having to stay within [REACH] of the
 * basket while the map is open.
 * Not built yet: log storage and paying from it (step T6); Auguste's other options (origami balloons, replacing the
 * bomber jacket and cap); the bail-out crash.
 */
class Balloons(private val world: World) {
    /** The station whose map each player has open. */
    private val flying = HashMap<Player, BalloonStation>()

    fun register() {
        for (station in BalloonStation.entries) {
            world.handlers.onLoc(station.basket, USE) { arriveDelay(); openMap(station) }
            for (assistant in station.assistants) {
                world.handlers.onNpc(assistant, TALK_TO) { talk(station, assistant) }
                world.handlers.onNpc(assistant, FLY) { openMap(station) }
            }
            world.handlers.onButton(MAP, station.button, 1) { travel(station) }
        }
        world.handlers.onButton(MAP, CLOSE) { world.interfaces.closeModals(player) }
        world.interfaces.onClose(MAP) { flying.remove(it) }
    }

    /** Drops what is kept for [player], who has logged out. */
    fun forget(player: Player) {
        flying.remove(player)
    }

    private suspend fun ScriptContext.talk(station: BalloonStation, assistant: Int) {
        var fly = false
        world.dialogues.start(this) {
            val type = world.npcTypes[assistant] ?: return@start
            val name = type.name
            chatNpc(type, name, ChatAnim.NEUTRAL, BalloonLines.GREETING)
            // Lori, the Grand Tree's other assistant, has only Yes and No (Transcript:Assistant Lori).
            val who = station.who.takeIf { assistant != LORI }
            val picked = if (who == null) {
                if (choice(BalloonLines.YES, BalloonLines.NO) == 1) YES_PICKED else NO_PICKED
            } else {
                when (choice(BalloonLines.YES, BalloonLines.NO, BalloonLines.WHO)) {
                    1 -> YES_PICKED
                    2 -> NO_PICKED
                    else -> WHO_PICKED
                }
            }
            when (picked) {
                YES_PICKED -> fly = true
                NO_PICKED -> chatPlayer(ChatAnim.NEUTRAL, BalloonLines.NO)
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, BalloonLines.WHO)
                    chatNpc(type, name, ChatAnim.NEUTRAL, who!!)
                    if (station == BalloonStation.GRAND_TREE) {
                        chatPlayer(ChatAnim.NEUTRAL, BalloonLines.WHY)
                        chatNpc(type, name, ChatAnim.LAUGH, BalloonLines.HOT_AIR)
                    }
                    chatNpc(type, name, ChatAnim.NEUTRAL, BalloonLines.AGAIN)
                    if (choice(BalloonLines.YES, BalloonLines.NO) == 1) fly = true else chatPlayer(ChatAnim.NEUTRAL, BalloonLines.NO)
                }
            }
        }
        if (fly) openMap(station)
    }

    private fun ScriptContext.openMap(station: BalloonStation) {
        flying[player] = station
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAP)
        for (each in BalloonStation.entries) world.interfaces.setEvents(player, MAP, each.button, -1, -1, Interfaces.IF_EVENT_OP1)
    }

    private suspend fun ScriptContext.travel(to: BalloonStation) {
        val from = flying[player] ?: return
        // The map left open by a move that doesn't close it (Home Teleport, ::tele) would fly from wherever the player
        // went, as with the canoes (exploit review R32C-1).
        if (player.modals[FrameSlot.MAINMODAL] != MAP || !near(from)) return
        if (to == from) return
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.FIREMAKING) < to.level) {
            player.message(text[BalloonLines.LEVEL, "level" to to.level])
            return
        }
        val slot = player.inventory.contents().firstOrNull { (_, obj) -> obj.id == to.log }?.first
        if (slot == null) {
            player.message(text[BalloonLines.NEED_LOGS, "logs" to text[to.logName]])
            return
        }
        if (tooHeavy(to)) {
            player.message(text[BalloonLines.TOO_HEAVY, "limit" to WEIGHT_LIMIT_GRAMS / 1000])
            return
        }
        world.interfaces.closeModals(player)
        if (to == BalloonStation.ENTRANA && !searched(from)) return
        // The search's boxes waited: the log must still be there, and the player still at the basket.
        if (player.inventory[slot]?.id != to.log || !near(from) || tooHeavy(to)) return
        player.inventory.remove(slot, 1)
        player.jingle(JOURNEY_JINGLE)
        delay(FLIGHT_TICKS)
        world.teleport(player, to.landing.x, to.landing.z, to.landing.level)
    }

    /**
     * Whether [player] carries more than the limit, not counting one of [to]'s logs: weighed now, not at the last tick's
     * update, as a worn item can come off in the same tick as Travel (Codex review on #905).
     */
    private fun ScriptContext.tooHeavy(to: BalloonStation): Boolean =
        world.weightNow(player) - (world.objTypes[to.log]?.weight ?: 0) > WEIGHT_LIMIT_GRAMS

    /** The assistant's search before a trip to Entrana: true when the player may go. */
    private suspend fun ScriptContext.searched(from: BalloonStation): Boolean {
        val type = world.npcTypes[from.assistants.first()] ?: return false
        val armed = armed(player)
        world.dialogues.start(this) {
            chatNpc(type, type.name, ChatAnim.NEUTRAL, BalloonLines.ENTRANA_SEARCH)
            mesbox(BalloonLines.SEARCHED)
            if (armed) chatNpc(type, type.name, ChatAnim.NEUTRAL, BalloonLines.ENTRANA_REFUSE)
        }
        return !armed
    }

    /** Guessed: anything worn or carried that is worn as armour or a weapon and has an attack or defence bonus. */
    private fun armed(player: Player): Boolean {
        val items = player.inventory.contents().map { it.second.id } + player.equipment.contents().map { it.second.id }
        return items.any { id ->
            val type = world.objTypes[id] ?: return@any false
            type.wearpos1 in ARMED_SLOTS && COMBAT_BONUS_PARAMS.any { (type.params[it] as? Int ?: 0) != 0 }
        }
    }

    private fun ScriptContext.near(station: BalloonStation): Boolean =
        player.level == station.basketTile.level &&
            maxOf(kotlin.math.abs(player.x - station.basketTile.x), kotlin.math.abs(player.z - station.basketTile.z)) <= REACH

    companion object {
        /** Interface `zep_balloon_map` and its close button. */
        const val MAP = 469
        const val CLOSE = 26

        const val USE = 1
        const val TALK_TO = 1
        const val FLY = 4

        /** Jingle `balloon journey jingle`. */
        const val JOURNEY_JINGLE = 118

        /** The Wiki: no more than 40 kg, not counting the log. */
        const val WEIGHT_LIMIT_GRAMS = 40_000

        /** Guessed: the ticks between the map closing and the landing. */
        const val FLIGHT_TICKS = 4

        /** Guessed: how far from the basket's south-west tile the player may stand with the map open (the assistants stand 2 away). */
        const val REACH = 4

        private val ARMED_SLOTS = setOf(WearPos.HEAD, WearPos.WEAPON, WearPos.BODY, WearPos.SHIELD, WearPos.LEGS, WearPos.HANDS, WearPos.FEET)

        /** Params 0 to 9: the attack and defence bonuses (see Equipment). */
        private val COMBAT_BONUS_PARAMS = 0..9

        /** `zep_assist_gno_replacement_vis`: Assistant Lori. */
        private const val LORI = 7154

        private const val YES_PICKED = 1
        private const val NO_PICKED = 2
        private const val WHO_PICKED = 3
    }
}
