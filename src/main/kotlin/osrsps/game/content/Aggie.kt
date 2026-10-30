package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** Aggie's lines (the Wiki's Transcript:Aggie, read 2026-10-30). */
object AggieLines : Lines("aggie", "Transcript:Aggie") {
    val HELP = line("help", "Aggie's first line")
    val MAKE = line("make", "first menu, option 1, which the player then says")
    val POTIONS = line("potions", "option 1: Aggie's answer, before the dye menu")
    val FROGS = line("frogs", "first menu, option 2, which the player then says")
    val FROGS_NO = line("frogs_no", "option 2: Aggie's answer")
    val WITCH = line("witch", "first menu, option 3, which the player then says")
    val OH = line("oh", "option 3 with 21 coins or more, or a pot of flour: Aggie's first answer")
    val FIVE_COINS = line("five_coins", "option 3 with 21 to 100 coins: the item box, 5 coins gone")
    val TWENTY_COINS = line("twenty_coins", "option 3 with 101 coins or more: the item box, 20 coins gone")
    val RESPECT = line("respect", "option 3, after the coins go: Aggie's last line")
    val FLOUR = line("flour", "option 3 with a pot of flour: the item box, the flour gone")
    val FLOUR_THANKS = line("flour_thanks", "option 3, after the flour goes: Aggie's last line")
    val NOTHING = line("nothing", "option 3 with nothing to take: Aggie's answer")
    val DYES = line("dyes", "first menu, option 4, which the player then says")
    val WHICH = line("which", "option 4 (and Dyes): Aggie asks which colour")
    val RED = line("red", "the dye menu, option 1, which the player then says")
    val RED_COST = line("red_cost", "option 1: Aggie's answer, 3 redberries and 5 coins")
    val YELLOW = line("yellow", "the dye menu, option 2, which the player then says")
    val YELLOW_COST = line("yellow_cost", "option 2: Aggie's answer, 2 onions and 5 coins")
    val BLUE = line("blue", "the dye menu, option 3, which the player then says")
    val BLUE_COST = line("blue_cost", "option 3: Aggie's answer, 2 woad leaves and 5 coins")
    val HAPPY = line("happy", "the dye menu, option 4, which the player then says")
    val HAPPY_ANSWER = line("happy_answer", "option 4: Aggie's answer")
    val MAKE_RED = line("make_red", "the red dye menu, option 1, which the player then says")
    val MAKE_YELLOW = line("make_yellow", "the yellow dye menu, option 1, which the player then says")
    val MAKE_BLUE = line("make_blue", "the blue dye menu, option 1, which the player then says")
    val NO_REDBERRIES = line("no_redberries", "making red dye with under 3 redberries: the message box")
    val NO_ONIONS = line("no_onions", "making yellow dye with under 2 onions: the message box")
    val NO_WOAD = line("no_woad", "making blue dye with under 2 woad leaves: the message box")
    val NO_COINS = line("no_coins", "making a dye with under 5 coins: the message box (2004Scape's; the Wiki shows no coin check)")
    val GOT_RED = line("got_red", "the red dye made: the item box")
    val GOT_YELLOW = line("got_yellow", "the yellow dye made: the item box")
    val GOT_BLUE = line("got_blue", "the blue dye made: the item box")
    val NOT_YET = line("not_yet", "a colour's menu, option 2, which the player then says")
    val NOT_YET_ANSWER = line("not_yet_answer", "option 2: Aggie's answer")
    val PRICE = line("price", "a colour's menu, option 3, which the player then says")
    val PRICE_ANSWER = line("price_answer", "option 3: Aggie's answer")
    val WHERE_REDBERRIES = line("where_redberries", "the red dye menu, option 4, which the player then says")
    val WHERE_ONIONS = line("where_onions", "the yellow dye menu, option 4, which the player then says")
    val WHERE_WOAD = line("where_woad", "the blue dye menu, option 4 (the player says nothing)")
    val REDBERRIES_ANSWER = line("redberries_answer", "where to get redberries: Aggie's answer")
    val ONIONS_ANSWER = line("onions_answer", "where to get onions: Aggie's answer")
    val WOAD_ANSWER = line("woad_answer", "where to get woad leaves: Aggie's answer")
    val OTHER = line("other", "a colour's menu, option 5, and the where-to-get menu's option 1, which the player then says")
    val OTHER_ANSWER = line("other_answer", "that option: Aggie names the colours, before the dye menu")
    val THANKS = line("thanks", "the where-to-get menu, option 2, which the player then says")
    val WELCOME = line("welcome", "that option: Aggie's answer")
}

/**
 * Aggie the witch in her house west of the market (Draynor piece step 4): she makes red, yellow and blue dye for its
 * ingredients and 5 coins, and takes coins or flour from players who insult her. NPC 4284 is a multi-NPC on varbit 13597
 * (gobdip_met_aggie): `aggie_1op` (120, Talk-to) before Goblin Diplomacy has the player meet her, `aggie_2ops` (121,
 * Talk-to and Dyes) after, which every account is ([osrsps.game.Quests.AFTER_QUESTS]).
 *
 * From the Wiki's transcript: every branch, the ingredient checks and the insult's coin and flour checks, in its order.
 * From 2004Scape's aggie script (a structural reference): taking the coins after the ingredients are checked, the coin
 * check itself (the Wiki shows none), and using an ingredient on her to make its dye. Guessed: every chathead
 * expression; that Dyes goes straight to her question which colour, as option 4 does after the player's line; that the
 * dye lands at the player's feet with no free slot (it can't: the ingredients free one); the coin item boxes' icon (the
 * cache's coins_250, as 2004Scape has it).
 */
object Aggie {
    const val ID = 4284
    const val BEFORE_MET = 120
    const val AFTER_MET = 121
    const val TALK_TO = 1
    const val DYES = 3

    const val COINS = 995
    const val COINS_250 = 1002
    const val POT_OF_FLOUR = 1933
    const val DYE_PRICE = 5

    enum class Dye(
        val ingredient: Int,
        val needed: Int,
        val dye: Int,
        val ask: Line,
        val cost: Line,
        val make: Line,
        val notEnough: Line,
        val got: Line,
        val where: Line,
        val whereAnswer: Line,
        val playerAsksWhere: Boolean,
    ) {
        RED(1951, 3, 1763, AggieLines.RED, AggieLines.RED_COST, AggieLines.MAKE_RED, AggieLines.NO_REDBERRIES, AggieLines.GOT_RED,
            AggieLines.WHERE_REDBERRIES, AggieLines.REDBERRIES_ANSWER, true),
        YELLOW(1957, 2, 1765, AggieLines.YELLOW, AggieLines.YELLOW_COST, AggieLines.MAKE_YELLOW, AggieLines.NO_ONIONS, AggieLines.GOT_YELLOW,
            AggieLines.WHERE_ONIONS, AggieLines.ONIONS_ANSWER, true),
        BLUE(1793, 2, 1767, AggieLines.BLUE, AggieLines.BLUE_COST, AggieLines.MAKE_BLUE, AggieLines.NO_WOAD, AggieLines.GOT_BLUE,
            AggieLines.WHERE_WOAD, AggieLines.WOAD_ANSWER, false),
    }

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit): Unit = with(AggieLines) {
        chatNpc(ChatAnim.NEUTRAL, HELP)
        when (choice(MAKE, FROGS, WITCH, DYES)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, MAKE)
                chatNpc(ChatAnim.NEUTRAL, POTIONS)
                dyeMenu(give)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, FROGS)
                chatNpc(ChatAnim.LAUGH, FROGS_NO)
            }
            3 -> {
                chatPlayer(ChatAnim.ANGRY, WITCH)
                insulted()
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, DYES)
                dyes(give)
            }
        }
    }

    /** Aggie's question which colour and the dye menu: option 4 after the player's line, and the Dyes option. */
    internal suspend fun Dialogue.dyes(give: (Player, Int) -> Unit) {
        chatNpc(ChatAnim.QUIZ, AggieLines.WHICH)
        dyeMenu(give)
    }

    private suspend fun Dialogue.dyeMenu(give: (Player, Int) -> Unit): Unit = with(AggieLines) {
        val picked = choice(RED, YELLOW, BLUE, HAPPY)
        if (picked == 4) {
            chatPlayer(ChatAnim.HAPPY, HAPPY)
            chatNpc(ChatAnim.NEUTRAL, HAPPY_ANSWER)
            return@with
        }
        val dye = Dye.entries[picked - 1]
        chatPlayer(ChatAnim.QUIZ, dye.ask)
        chatNpc(ChatAnim.NEUTRAL, dye.cost)
        when (choice(dye.make, NOT_YET, PRICE, dye.where, OTHER)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, dye.make)
                make(dye, give)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, NOT_YET)
                chatNpc(ChatAnim.NEUTRAL, NOT_YET_ANSWER)
            }
            3 -> {
                chatPlayer(ChatAnim.NEUTRAL, PRICE)
                chatNpc(ChatAnim.ANGRY, PRICE_ANSWER)
            }
            4 -> {
                if (dye.playerAsksWhere) chatPlayer(ChatAnim.QUIZ, dye.where)
                chatNpc(ChatAnim.NEUTRAL, dye.whereAnswer)
                if (choice(OTHER, THANKS) == 1) {
                    otherColours(give)
                } else {
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                    chatNpc(ChatAnim.HAPPY, WELCOME)
                }
            }
            else -> otherColours(give)
        }
    }

    private suspend fun Dialogue.otherColours(give: (Player, Int) -> Unit) {
        chatPlayer(ChatAnim.QUIZ, AggieLines.OTHER)
        chatNpc(ChatAnim.NEUTRAL, AggieLines.OTHER_ANSWER)
        dyeMenu(give)
    }

    /** Makes [dye] if the player has its ingredients and 5 coins: also what using an ingredient on Aggie does. */
    internal suspend fun Dialogue.make(dye: Dye, give: (Player, Int) -> Unit) {
        when {
            player.inventory.count(dye.ingredient) < dye.needed -> mesbox(dye.notEnough)
            player.inventory.count(COINS) < DYE_PRICE -> mesbox(AggieLines.NO_COINS)
            else -> {
                Ned.take(player, COINS, DYE_PRICE)
                Ned.take(player, dye.ingredient, dye.needed)
                give(player, dye.dye)
                objbox(dye.dye, dye.got)
            }
        }
    }

    /** What Aggie takes for the insult, checked in the transcript's order. */
    private suspend fun Dialogue.insulted(): Unit = with(AggieLines) {
        val coins = player.inventory.count(COINS)
        val fine = when {
            coins in 21..100 -> 5
            coins >= 101 -> 20
            else -> 0
        }
        when {
            fine > 0 -> {
                chatNpc(ChatAnim.ANGRY, OH)
                Ned.take(player, COINS, fine)
                objbox(COINS_250, if (fine == 5) FIVE_COINS else TWENTY_COINS)
                chatNpc(ChatAnim.LAUGH, RESPECT)
            }
            player.inventory.count(POT_OF_FLOUR) > 0 -> {
                chatNpc(ChatAnim.ANGRY, OH)
                Ned.take(player, POT_OF_FLOUR, 1)
                objbox(POT_OF_FLOUR, FLOUR)
                chatNpc(ChatAnim.LAUGH, FLOUR_THANKS)
            }
            else -> chatNpc(ChatAnim.ANGRY, NOTHING)
        }
    }
}

/** Aggie's Talk-to and Dyes, on both of the types she shows, and her ingredients used on her. */
fun registerAggie(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    for (type in listOf(Aggie.BEFORE_MET, Aggie.AFTER_MET)) {
        world.handlers.onNpc(type, Aggie.TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { with(Aggie) { talk(give) } }
        }
        for (dye in Aggie.Dye.entries) {
            world.handlers.onObjOnNpc(type, dye.ingredient) { op ->
                world.dialogues.start(this, op.npc) { with(Aggie) { make(dye, give) } }
            }
        }
    }
    world.handlers.onNpc(Aggie.AFTER_MET, Aggie.DYES) { op ->
        world.dialogues.start(this, op.npc) { with(Aggie) { dyes(give) } }
    }
}
