package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/** The Apothecary's lines, from the Wiki's Transcript:Apothecary (after Romeo & Juliet), read 2026-10-30. */
object ApothecaryLines : Lines("apothecary", "Transcript:Apothecary") {
    val GREETING = line("greeting", "his first line")
    val SOMETHING_ELSE = line("something_else", "option: talk about something else (no line from the player)")
    val NO_THANKS = line("no_thanks", "option: no thanks, which the player then says")
    val GOSSIP = line("gossip", "something else, option 1: any gossip, which the player then says")
    val GOSSIP_1 = line("gossip_1", "the gossip: his first line, after Romeo & Juliet")
    val GOSSIP_2 = line("gossip_2", "the gossip: second line")
    val GOSSIP_3 = line("gossip_3", "the gossip: third line")
    val GOSSIP_4 = line("gossip_4", "the gossip: fourth line")
    val HAIR = line("hair", "something else, option 2: a potion for hair loss, which the player then says")
    val HAIR_1 = line("hair_1", "the hair potion: his first line")
    val HAIR_2 = line("hair_2", "the hair potion: second line")
    val FREE = line("free", "something else, option 3: any free potions, which the player then says")
    val CHARITY = line("charity", "the free potions: his answer")
}

/** Baraek's lines, from the Wiki's Transcript:Baraek, read 2026-10-30. */
object BaraekLines : Lines("baraek", "Transcript:Baraek") {
    val SELL_ME = line("sell_me", "option 1: can he sell some furs, which the player then says")
    val TWENTY = line("twenty", "his price, 20 coins")
    val YES = line("yes", "the price menu, option 1, which the player says when they hold 20 coins")
    val OUTRAGE = line("outrage", "the price menu, option 2, which the player then says")
    val CHEAPER = line("cheaper", "the haggle: his answer")
    val NOT_ENOUGH = line("not_enough", "the price menu, option 1, which the player says instead with fewer than 20 coins")
    val BEST_PRICE = line("best_price", "his lower price, 18 coins")
    val OK_HERE = line("ok_here", "the lower price menu, option 1, which the player then says")
    val NO_LEAVE = line("no_leave", "the lower price menu, option 2, which the player then says")
    val LOSS = line("loss", "his answer to that")
    val NOT_EITHER = line("not_either", "with fewer than 18 coins: the player's line")
    val NO_CHEAPER = line("no_cheaper", "with fewer than 18 coins: his answer")
    val NEVER_MIND = line("never_mind", "with fewer than 18 coins: the player's last line")
    val SOLD = line("sold", "the item box when he sells a fur")
    val QUEST = line("quest", "option 2: in search of a quest, which the player then says")
    val KIDDO = line("kiddo", "his answer")
    val SELL_GREY = line("sell_grey", "option 3 with grey wolf fur: buy it, which the player then says")
    val GREY_1 = line("grey_1", "the grey wolf fur: his first line")
    val GREY_2 = line("grey_2", "the grey wolf fur: second line")
    val GREY_3 = line("grey_3", "the grey wolf fur: his offer, 120 a fur")
    val YEP = line("yep", "the grey wolf fur menu, option 1, which the player then says")
    val THROAT = line("throat", "the grey wolf fur menu, option 2, which the player then says")
    val SELL_FUR = line("sell_fur", "option 3 with fur or bear fur and no grey wolf fur: buy it, which the player then says")
    val LOOK = line("look", "the fur: his first line")
    val HAND_FUR = line("hand_fur", "the item box: the player hands him a fur")
    val HAND_BEAR_FUR = line("hand_bear_fur", "the item box: the player hands him a bear fur")
    val CONDITION = line("condition", "the fur: his offer, 12 a fur")
    val THATLL_DO = line("thatll_do", "the fur menu, option 1, which the player then says")
    val KEEP = line("keep", "the fur menu, option 2, which the player then says")
    val DIDNT_WANT = line("didnt_want", "his answer to that")
    val THANKS = line("thanks", "the player's last line after he buys")
}

/** Benny's lines, from the Wiki's Transcript:Benny, read 2026-10-30. */
object BennyLines : Lines("benny", "Transcript:Benny") {
    val CAN_I = line("can_i", "option 1: a newspaper, which the player then says")
    val CERTAINLY = line("certainly", "option 1: his answer, 50 coins")
    val HOW_MUCH = line("how_much", "option 2: the price, which the player then says")
    val JUST_50 = line("just_50", "option 2: his answer")
    val HERALD = line("herald", "option 3: what the Varrock Herald is, which the player then says")
    val ILLITERATE = line("illiterate", "option 3: his first line")
    val CAPTIVATING = line("captivating", "option 3: second line")
    val ANYTHING = line("anything", "option 4: anything interesting in it, which the player then says")
    val OF_COURSE = line("of_course", "option 4: his answer")
    val SURE = line("sure", "the buy menu, option 1, which the player then says")
    val NO_THANKS = line("no_thanks", "the buy menu, option 2, which the player then says")
    val SUIT_YOURSELF = line("suit_yourself", "his answer to that")
    val MAYBE = line("maybe", "with fewer than 50 coins: the player's line")
    val NO_CASH = line("no_cash", "with fewer than 50 coins: his answer")
}

/**
 * The Varrock traders who sell through their talk (the Varrock piece's step 5b): the Apothecary (5036), Baraek (2881),
 * who sells a fur for 20 coins (18 to a player who can't pay 20) and buys grey wolf fur at 120 and fur and bear fur at
 * 12, and Benny (5216), who sells the Varrock Herald for 50. From the Wiki's transcripts. Guessed: every expression;
 * that a player with 18 or 19 coins gets the lower price; that the hand-over box shows a fur when the player holds both
 * kinds; that a full backpack drops what was bought at the player's feet. Left out: the Apothecary's Potions and its
 * talk option (his potions screen, the cache's interface 556 `apothecary_potions`, is a step of its own), his quest
 * topics; Baraek's Ring of Charos(a) option
 * and treasure trails; Benny's overhead shouts.
 */
object VarrockTraders {
    const val APOTHECARY = 5036
    const val BARAEK = 2881
    const val BENNY = 5216
    const val TALK_TO = 1
    const val FUR = 6814
    const val BEAR_FUR = 948
    const val GREY_WOLF_FUR = 958
    const val NEWSPAPER = 11169
    const val FUR_PRICE = 20
    const val FUR_BEST_PRICE = 18
    const val GREY_WOLF_FUR_PAYS = 120
    const val FUR_PAYS = 12
    const val NEWSPAPER_PRICE = 50

    internal suspend fun Dialogue.apothecary() = with(ApothecaryLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        // Each topic brings the first menu back; no thanks, in either menu, ends the talk.
        while (choice(SOMETHING_ELSE, NO_THANKS) == 1) {
            when (choice(GOSSIP, HAIR, FREE, NO_THANKS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, GOSSIP)
                    chatNpc(ChatAnim.SAD, GOSSIP_1)
                    chatNpc(ChatAnim.NEUTRAL, GOSSIP_2)
                    chatNpc(ChatAnim.NEUTRAL, GOSSIP_3)
                    chatNpc(ChatAnim.HAPPY, GOSSIP_4)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, HAIR)
                    chatNpc(ChatAnim.HAPPY, HAIR_1)
                    chatNpc(ChatAnim.NEUTRAL, HAIR_2)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, FREE)
                    chatNpc(ChatAnim.ANGRY, CHARITY)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    return@with
                }
            }
        }
        chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
    }

    /** Baraek's talk; [give] hands over a bought item or coins (id, count). */
    internal suspend fun Dialogue.baraek(give: (Int, Int) -> Unit) = with(BaraekLines) {
        val inv = player.inventory
        val grey = inv.count(GREY_WOLF_FUR) > 0
        val furs = inv.count(FUR) > 0 || inv.count(BEAR_FUR) > 0
        val options = listOfNotNull(SELL_ME, QUEST, if (grey) SELL_GREY else if (furs) SELL_FUR else null)
        when (choice(*options.toTypedArray())) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, SELL_ME)
                chatNpc(ChatAnim.HAPPY, TWENTY)
                if (choice(YES, OUTRAGE) == 2) {
                    chatPlayer(ChatAnim.ANGRY, OUTRAGE)
                    chatNpc(ChatAnim.NEUTRAL, CHEAPER)
                    return@with
                }
                if (pay(player, FUR_PRICE)) {
                    chatPlayer(ChatAnim.HAPPY, YES)
                    sellFur(give)
                    return@with
                }
                chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
                chatNpc(ChatAnim.NEUTRAL, BEST_PRICE)
                if (choice(OK_HERE, NO_LEAVE) == 2) {
                    chatPlayer(ChatAnim.NEUTRAL, NO_LEAVE)
                    chatNpc(ChatAnim.NEUTRAL, LOSS)
                    return@with
                }
                chatPlayer(ChatAnim.HAPPY, OK_HERE)
                if (pay(player, FUR_BEST_PRICE)) {
                    sellFur(give)
                    return@with
                }
                chatPlayer(ChatAnim.SAD, NOT_EITHER)
                chatNpc(ChatAnim.NEUTRAL, NO_CHEAPER)
                chatPlayer(ChatAnim.SAD, NEVER_MIND)
            }
            2 -> {
                chatPlayer(ChatAnim.HAPPY, QUEST)
                chatNpc(ChatAnim.NEUTRAL, KIDDO)
            }
            else -> if (grey) {
                chatPlayer(ChatAnim.QUIZ, SELL_GREY)
                chatNpc(ChatAnim.SHOCKED, GREY_1)
                chatNpc(ChatAnim.HAPPY, GREY_2)
                chatNpc(ChatAnim.HAPPY, GREY_3)
                if (choice(YEP, THROAT) == 2) {
                    chatPlayer(ChatAnim.ANGRY, THROAT)
                    return@with
                }
                chatPlayer(ChatAnim.HAPPY, YEP)
                buy(listOf(GREY_WOLF_FUR), GREY_WOLF_FUR_PAYS, give)
                chatPlayer(ChatAnim.HAPPY, THANKS)
            } else {
                chatPlayer(ChatAnim.QUIZ, SELL_FUR)
                chatNpc(ChatAnim.NEUTRAL, LOOK)
                if (inv.count(FUR) > 0) objbox(FUR, HAND_FUR) else objbox(BEAR_FUR, HAND_BEAR_FUR)
                chatNpc(ChatAnim.NEUTRAL, CONDITION)
                if (choice(THATLL_DO, KEEP) == 2) {
                    chatPlayer(ChatAnim.NEUTRAL, KEEP)
                    chatNpc(ChatAnim.ANGRY, DIDNT_WANT)
                    return@with
                }
                chatPlayer(ChatAnim.HAPPY, THATLL_DO)
                buy(listOf(FUR, BEAR_FUR), FUR_PAYS, give)
                chatPlayer(ChatAnim.HAPPY, THANKS)
            }
        }
    }

    private suspend fun Dialogue.sellFur(give: (Int, Int) -> Unit) {
        give(FUR, 1)
        objbox(FUR, BaraekLines.SOLD)
    }

    /** Takes every one of [objs] from the backpack, [each] coins apiece. */
    private fun Dialogue.buy(objs: List<Int>, each: Int, give: (Int, Int) -> Unit) {
        var sold = 0
        for ((slot, held) in player.inventory.contents()) {
            if (held.id in objs) sold += player.inventory.remove(slot, held.count)
        }
        if (sold > 0) give(ShopScreen.COINS, sold * each)
    }

    /** Benny's talk, which opens with the player's menu; [give] hands over the newspaper. */
    internal suspend fun Dialogue.benny(give: (Int, Int) -> Unit) = with(BennyLines) {
        when (choice(CAN_I, HOW_MUCH, HERALD, ANYTHING)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, CAN_I)
                chatNpc(ChatAnim.HAPPY, CERTAINLY)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, HOW_MUCH)
                chatNpc(ChatAnim.HAPPY, JUST_50)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, HERALD)
                chatNpc(ChatAnim.HAPPY, ILLITERATE)
                chatNpc(ChatAnim.HAPPY, CAPTIVATING)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, ANYTHING)
                chatNpc(ChatAnim.HAPPY, OF_COURSE)
            }
        }
        if (player.inventory.count(ShopScreen.COINS) < NEWSPAPER_PRICE) {
            chatPlayer(ChatAnim.SAD, MAYBE)
            chatNpc(ChatAnim.NEUTRAL, NO_CASH)
            return@with
        }
        if (choice(SURE, NO_THANKS) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            chatNpc(ChatAnim.NEUTRAL, SUIT_YOURSELF)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, SURE)
        if (pay(player, NEWSPAPER_PRICE)) give(NEWSPAPER, 1)
    }

    private fun pay(player: Player, price: Int) = AlKharidTraders.pay(player, price)
}

fun registerVarrockTraders(world: World) {
    with(VarrockTraders) {
        world.handlers.onNpc(APOTHECARY, TALK_TO) { op -> world.dialogues.start(this, op.npc) { apothecary() } }
        world.handlers.onNpc(BARAEK, TALK_TO) { op ->
            val give = { obj: Int, count: Int -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, count) } ?: Unit }
            world.dialogues.start(this, op.npc) { baraek(give) }
        }
        world.handlers.onNpc(BENNY, TALK_TO) { op ->
            val give = { obj: Int, count: Int -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, count) } ?: Unit }
            world.dialogues.start(this, op.npc) { benny(give) }
        }
    }
}
