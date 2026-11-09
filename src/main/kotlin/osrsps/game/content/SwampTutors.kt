package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** Monlum's lines; the words are in the dialogue file, from the Wiki's Transcript:Monlum, read 2026-11-08. */
object MonlumLines : Lines("monlum", "Transcript:Monlum") {
    val GREETING = line("greeting", "Standard dialogue: his greeting")
    val ADVICE = line("advice", "first menu: mining advice, which the player then says")
    val ADVICE_1 = line("advice_1", "mining advice: a pickaxe and rocks, tin and copper here")
    val MINE_HINT = line("mine_hint", "mining advice: the mining site icon's box (item 5082)")
    val ADVICE_2 = line("advice_2", "mining advice: rarer rocks take more skill")
    val ADVICE_3 = line("advice_3", "mining advice: not much else to it")
    val GUIDE_HINT = line("guide_hint", "mining advice: the Skills panel box")
    val THANKS = line("thanks", "the player's thanks (after the advice and after a pickaxe)")
    val PICKAXE = line("pickaxe", "first menu: a pickaxe, which the player then says")
    val SPARE = line("spare", "a pickaxe, owning none: his line")
    val GIVES_PICKAXE = line("gives_pickaxe", "a pickaxe, owning none: the box with the bronze pickaxe")
    val NO_PROBLEM = line("no_problem", "a pickaxe, after the thanks: his answer")
    val HAS_ONE = line("has_one", "a pickaxe, owning one: his line")
    val SO_I_DO = line("so_i_do", "a pickaxe, owning one: the player's answer")
    val NO_ROOM = line("no_room", "pickaxe, owning none, a full backpack: his line (missing)")
    val LEAVE = line("leave", "first menu: better be off, which the player then says (end of dialogue)")
}

/** Finlay's lines; the words are in the dialogue file, from the Wiki's Transcript:Finlay, read 2026-11-08. */
object FinlayLines : Lines("finlay", "Transcript:Finlay") {
    val GREETING = line("greeting", "Standard dialogue: his greeting")
    val ADVICE = line("advice", "first menu: advice on catching fish, which the player then says")
    val ADVICE_1 = line("advice_1", "fishing advice: a fine hobby, his parrot")
    val WHAT_PARROT = line("what_parrot", "fishing advice: the player asking what parrot")
    val PERCY = line("percy", "fishing advice: Percy on his shoulder")
    val SURE = line("sure", "fishing advice: the player humouring him")
    val ADVICE_2 = line("advice_2", "fishing advice: gear and water, spots in seas and rivers")
    val ADVICE_3 = line("advice_3", "fishing advice: these spots, a small net")
    val SPOT_HINT = line("spot_hint", "fishing advice: the fishing spot icon's box (item 5088)")
    val ADVICE_4 = line("advice_4", "fishing advice: some fish need more skill")
    val ADVICE_5 = line("advice_5", "fishing advice: get out there and start fishing")
    val GUIDE_HINT = line("guide_hint", "fishing advice: the Skills panel box")
    val THANKS = line("thanks", "the player's thanks (after the advice and after a net)")
    val DONT_MENTION_IT = line("dont_mention_it", "fishing advice, after the thanks: his answer")
    val TOOLS = line("tools", "first menu: fishing tools, which the player then says")
    val ONLY_A_NET = line("only_a_net", "tools, owning no small net: his line")
    val GIVES_NET = line("gives_net", "tools, owning no small net: the box with the net")
    val HAPPY_TO_HELP = line("happy_to_help", "tools, after the thanks: his answer")
    val HAS_NET = line("has_net", "tools, owning a small net: his line")
    val I_DO = line("i_do", "tools, owning a small net: the player's answer")
    val NO_ROOM = line("no_room", "tools, owning no small net, a full backpack: his line (missing)")
    val LEAVE = line("leave", "first menu: better get going, which the player then says (end of dialogue)")
}

/**
 * Lumbridge Swamp's two tutors, renamed in 242.1: Monlum (NPC 16809, the Mining tutor until then) by the east mine and
 * Finlay (16810, the Fishing tutor) by the fishing spots.
 *
 * From the Wiki: each menu, the advice with its boxes, and a bronze pickaxe or small fishing net for a player without
 * one. Guessed: every chathead expression; that the boxes show the old tutors' icon items (5082, 5088) and the Skills
 * panel ones are plain message boxes; that "previous"/"other" is the first menu again; that any pickaxe counts as one
 * (Monlum says "on you", the old tutor counted the bank too: we count carried, worn and banked so a free pickaxe can't
 * be sold for coins from nothing, as the second exploit review asked); and that with a full backpack he gives nothing
 * and says so, in a line of his own (the Wiki gives no no-room line; a gift dropped at the player's feet could be
 * asked for again and again).
 */
object SwampTutors {
    const val MONLUM = 16809
    const val FINLAY = 16810
    const val TALK_TO = 1

    const val MINING_SITE_ICON = 5082
    const val FISHING_SPOT_ICON = 5088

    const val BRONZE_PICKAXE = 1265
    const val SMALL_FISHING_NET = 303

    /** Pickaxes (bronze to dragon, 3rd age, infernal and crystal); guessed as the ones that count. */
    val PICKAXES = listOf(1265, 1267, 1269, 12297, 1273, 1271, 1275, 11920, 12797, 20014, 13243, 23680)

    internal suspend fun Dialogue.monlum(give: (Player, Int) -> Unit) = with(MonlumLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(ADVICE, PICKAXE, LEAVE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, ADVICE)
                    chatNpc(ChatAnim.HAPPY, ADVICE_1)
                    objbox(MINING_SITE_ICON, MINE_HINT)
                    chatNpc(ChatAnim.NEUTRAL, ADVICE_2)
                    chatNpc(ChatAnim.NEUTRAL, ADVICE_3)
                    mesbox(GUIDE_HINT)
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, PICKAXE)
                    if (PICKAXES.any { owns(player, it) }) {
                        chatNpc(ChatAnim.NEUTRAL, HAS_ONE)
                        chatPlayer(ChatAnim.NEUTRAL, SO_I_DO)
                    } else if (player.inventory.freeSlots == 0) {
                        chatNpc(ChatAnim.NEUTRAL, NO_ROOM)
                    } else {
                        chatNpc(ChatAnim.HAPPY, SPARE)
                        give(player, BRONZE_PICKAXE)
                        objbox(BRONZE_PICKAXE, GIVES_PICKAXE)
                        chatPlayer(ChatAnim.HAPPY, THANKS)
                        chatNpc(ChatAnim.HAPPY, NO_PROBLEM)
                    }
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.finlay(give: (Player, Int) -> Unit) = with(FinlayLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(ADVICE, TOOLS, LEAVE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, ADVICE)
                    chatNpc(ChatAnim.HAPPY, ADVICE_1)
                    chatPlayer(ChatAnim.QUIZ, WHAT_PARROT)
                    chatNpc(ChatAnim.HAPPY, PERCY)
                    chatPlayer(ChatAnim.CONFUSED, SURE)
                    chatNpc(ChatAnim.NEUTRAL, ADVICE_2)
                    chatNpc(ChatAnim.NEUTRAL, ADVICE_3)
                    objbox(FISHING_SPOT_ICON, SPOT_HINT)
                    chatNpc(ChatAnim.NEUTRAL, ADVICE_4)
                    chatNpc(ChatAnim.HAPPY, ADVICE_5)
                    mesbox(GUIDE_HINT)
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                    chatNpc(ChatAnim.HAPPY, DONT_MENTION_IT)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, TOOLS)
                    if (owns(player, SMALL_FISHING_NET)) {
                        chatNpc(ChatAnim.NEUTRAL, HAS_NET)
                        chatPlayer(ChatAnim.NEUTRAL, I_DO)
                    } else if (player.inventory.freeSlots == 0) {
                        chatNpc(ChatAnim.NEUTRAL, NO_ROOM)
                    } else {
                        chatNpc(ChatAnim.HAPPY, ONLY_A_NET)
                        give(player, SMALL_FISHING_NET)
                        objbox(SMALL_FISHING_NET, GIVES_NET)
                        chatPlayer(ChatAnim.HAPPY, THANKS)
                        chatNpc(ChatAnim.HAPPY, HAPPY_TO_HELP)
                    }
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                    return@with
                }
            }
        }
    }

    /** Carried, worn or banked. */
    fun owns(player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0
}

/** Monlum's and Finlay's Talk-to. */
fun registerSwampTutors(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(SwampTutors.MONLUM, SwampTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(SwampTutors) { monlum(give) } }
    }
    world.handlers.onNpc(SwampTutors.FINLAY, SwampTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(SwampTutors) { finlay(give) } }
    }
}
