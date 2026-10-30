package osrsps.game.content

import osrsps.game.Inventory
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/** The Blue Moon Inn's bartender's lines, from the Wiki's Transcript:Bartender (Blue Moon Inn), read 2026-10-30. */
object BlueMoonBartenderLines : Lines("bluemoon_bartender", "Transcript:Bartender (Blue Moon Inn)") {
    val GREETING = line("greeting", "his first line: what can he do?")
    val ALE = line("ale", "option 1, which the player then says")
    val TWO_COINS = line("two_coins", "option 1: it's 2 coins")
    val NO_MONEY = line("no_money", "option 1, fewer than 2 coins: the player can't pay")
    val FORTUNE = line("fortune", "option 2, which the player then says")
    val TOO_EASY = line("too_easy", "option 2: that would make the game too easy")
    val AH_WELL = line("ah_well", "option 2's menu, option 1, which the player then says")
    val GAME = line("game", "option 2's menu, option 2, which the player then says")
    val ONLINE = line("online", "game: the world is an online game")
    val WHAT_ONLINE = line("what_online", "game: the player asks what online means")
    val MAGIC_BOXES = line("magic_boxes", "game: magic boxes join people together")
    val MAD = line("mad", "game: the player calls him mad")
    val CLUE = line("clue", "option 2's menu, option 3, which the player then says")
    val ASK_JOLLY_BOAR = line("ask_jolly_boar", "clue: ask the Jolly Boar's bartender")
    val EQUIPMENT = line("equipment", "option 3, which the player then says")
    val SWORD_SHOP = line("sword_shop", "option 3: the sword shop and the market")
}

/** The Dancing Donkey Inn's bartender's lines, from the Wiki's Transcript:Bartender (Dancing Donkey Inn), read 2026-10-30. */
object DancingDonkeyBartenderLines : Lines("donkey_bartender", "Transcript:Bartender (Dancing Donkey Inn)") {
    val HELLO = line("hello", "the player's greeting")
    val BEER = line("beer", "his answer: a beer?")
    val YES = line("yes", "option 1, which the player then says")
    val TWO_COINS = line("two_coins", "option 1: two gold coins")
    val CHEERS = line("cheers", "option 1, paid: his toast")
    val CHEERS_BACK = line("cheers_back", "option 1, paid: the player's toast")
    val NO = line("no", "option 2, which the player then says")
    val CHANGE_MIND = line("change_mind", "option 2: come back if the player changes their mind")
    val HOW_MUCH = line("how_much", "option 3, which the player then says")
    val PRICE = line("price", "option 3: two gold a pint, then the menu again")
}

/** The Jolly Boar Inn's bartender's lines, from the Wiki's Transcript:Bartender (Jolly Boar Inn), read 2026-10-30. */
object JollyBoarBartenderLines : Lines("jollyboar_bartender", "Transcript:Bartender (Jolly Boar Inn)") {
    val GREETING = line("greeting", "his first line: does the player need help?")
    val BEER = line("beer", "option 1, which the player then says")
    val TWO_COINS = line("two_coins", "option 1: two coins")
    val NO_MONEY = line("no_money", "option 1, fewer than 2 coins: the player can't pay")
    val HINTS = line("hints", "option 2, which the player then says")
    val THINK = line("think", "option 2: he thinks")
    val SEWERS = line("sewers", "option 2: the sewers' monsters")
    val WHERE = line("where", "option 2: the player asks where the entrance is")
    val EAST_OF_PALACE = line("east_of_palace", "option 2: east of the palace")
    val GOSSIP = line("gossip", "option 3, which the player then says")
    val MAD = line("mad", "option 3: the Blue Moon's bartender is mad")
    val OLD_NEWS = line("old_news", "option 3: probably old news")
}

/** The Blue Moon Inn's cook's lines, from the Wiki's Transcript:Cook (Blue Moon Inn), read 2026-10-30. */
object BlueMoonCookLines : Lines("bluemoon_cook", "Transcript:Cook (Blue Moon Inn)") {
    val GREETING = line("greeting", "his first line: what does the player want, quickly")
    val SELL = line("sell", "option 1, which the player then says")
    val CABBAGE = line("cabbage", "option 1: a cabbage, it's healthy")
    val NO_MONEY = line("no_money", "option 1, no coins: the player has no money")
    val NO_SPACE = line("no_space", "option 1, a full backpack: the player has no room")
    val WASTE = line("waste", "after no money or no room: he scolds the player away")
    val BUY = line("buy", "the cabbage menu's option 1, which the player then says")
    val EAT_IT = line("eat_it", "bought: eat it")
    val DONT_LIKE = line("dont_like", "the cabbage menu's option 2, which the player then says")
    val JUNK = line("junk", "not bought: people only want junk food")
    val FREE = line("free", "option 2, which the player then says")
    val FREE_MONEY = line("free_money", "option 2: free money instead?")
    val WHY_PAY = line("why_pay", "option 2: the player asks why")
    val WHY_FREE = line("why_free", "option 2: why give free food?")
    val FORGET_IT = line("forget_it", "option 2: the player gives up")
    val HORRIBLE = line("horrible", "option 3, which the player then says")
    val CLEANING = line("cleaning", "option 3: he cleans hard")
    val DISGUST = line("disgust", "option 3: the player's disgust")
    val LEAVE_ME = line("leave_me", "option 3: leave him alone")
}

/** The Jolly Boar Inn's cook's lines, from the Wiki's Transcript:Cook (Jolly Boar Inn), read 2026-10-30. */
object JollyBoarCookLines : Lines("jollyboar_cook", "Transcript:Cook (Jolly Boar Inn)") {
    val GREETING = line("greeting", "his first line: he's busy")
    val COOK = line("cook", "option 1, which the player then says")
    val COOK_LINES = (1..7).map { line("cook_$it", "option 1: line $it of the Wiki's seven after the ask, his and the player's in turn") }
    val WHY = line("why", "option 2, which the player then says")
    val WHY_LINES = (1..8).map { line("why_$it", "option 2: line $it of the Wiki's eight after the ask, his and the player's in turn") }
    val JOKE = line("joke", "option 3, which the player then says")
    val JOKE_LINES = (1..5).map { line("joke_$it", "option 3: line $it of the Wiki's five after the ask (his three, the player's, his punchline and the player's)") }
}

/**
 * Varrock's inns (the Varrock piece's step 7): the Blue Moon Inn (bartender 1312, cook 2895), the Dancing Donkey Inn
 * (bartender 1311) and the Jolly Boar Inn north-east of the city (bartender 1310, cook 2896). Each bartender sells a
 * beer for 2 coins through his talk, and the Blue Moon's cook a cabbage for 1. From the Wiki's transcripts. Guessed:
 * every expression; that a full backpack gets the beer at the player's feet (the transcripts don't say); that the
 * Dancing Donkey's bartender, short of 2 coins, says nothing more (as the transcript ends); that the Jolly Boar's cook
 * always opens with his busy line (the transcript's other opening, after being asked to cook, needs a memory of it).
 */
object VarrockInns {
    const val JOLLY_BOAR_BARTENDER = 1310
    const val DANCING_DONKEY_BARTENDER = 1311
    const val BLUE_MOON_BARTENDER = 1312
    const val BLUE_MOON_COOK = 2895
    const val JOLLY_BOAR_COOK = 2896
    const val TALK_TO = 1

    const val BEER = 1917
    const val BEER_PRICE = 2
    const val CABBAGE = 1965
    const val CABBAGE_PRICE = 1

    /** Which of [JollyBoarCookLines.COOK_LINES], [JollyBoarCookLines.WHY_LINES] and [JollyBoarCookLines.JOKE_LINES] the player says. */
    val COOK_PLAYER = setOf(2, 4, 6)
    val WHY_PLAYER = setOf(2, 4, 6, 8)
    val JOKE_PLAYER = setOf(3, 5)

    private fun Dialogue.coins() = player.inventory.count(ShopScreen.COINS)

    /** Takes [price] coins and gives [obj], with nothing suspending between; false when the coins aren't there. */
    private fun Dialogue.sell(obj: Int, price: Int, give: (Int) -> Unit): Boolean {
        if (!AlKharidTraders.pay(player, price)) return false
        give(obj)
        return true
    }

    internal suspend fun Dialogue.blueMoonBartender(give: (Int) -> Unit) = with(BlueMoonBartenderLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(ALE, FORTUNE, EQUIPMENT)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, ALE)
                chatNpc(ChatAnim.HAPPY, TWO_COINS)
                if (!sell(BEER, BEER_PRICE, give)) chatPlayer(ChatAnim.SAD, NO_MONEY)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, FORTUNE)
                chatNpc(ChatAnim.LAUGH, TOO_EASY)
                when (choice(AH_WELL, GAME, CLUE)) {
                    1 -> chatPlayer(ChatAnim.SAD, AH_WELL)
                    2 -> {
                        chatPlayer(ChatAnim.CONFUSED, GAME)
                        chatNpc(ChatAnim.HAPPY, ONLINE)
                        chatPlayer(ChatAnim.CONFUSED, WHAT_ONLINE)
                        chatNpc(ChatAnim.HAPPY, MAGIC_BOXES)
                        chatPlayer(ChatAnim.CONFUSED, MAD)
                    }
                    else -> {
                        chatPlayer(ChatAnim.QUIZ, CLUE)
                        chatNpc(ChatAnim.NEUTRAL, ASK_JOLLY_BOAR)
                    }
                }
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, EQUIPMENT)
                chatNpc(ChatAnim.NEUTRAL, SWORD_SHOP)
            }
        }
    }

    internal suspend fun Dialogue.dancingDonkeyBartender(give: (Int) -> Unit) = with(DancingDonkeyBartenderLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, BEER)
        while (true) {
            when (choice(YES, NO, HOW_MUCH)) {
                1 -> {
                    chatPlayer(ChatAnim.HAPPY, YES)
                    chatNpc(ChatAnim.HAPPY, TWO_COINS)
                    if (sell(VarrockInns.BEER, BEER_PRICE, give)) {
                        chatNpc(ChatAnim.HAPPY, CHEERS)
                        chatPlayer(ChatAnim.HAPPY, CHEERS_BACK)
                    }
                    return@with
                }
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO)
                    chatNpc(ChatAnim.NEUTRAL, CHANGE_MIND)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, HOW_MUCH)
                    chatNpc(ChatAnim.NEUTRAL, PRICE)
                }
            }
        }
    }

    internal suspend fun Dialogue.jollyBoarBartender(give: (Int) -> Unit) = with(JollyBoarBartenderLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        when (choice(BEER, HINTS, GOSSIP)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, BEER)
                chatNpc(ChatAnim.NEUTRAL, TWO_COINS)
                if (!sell(VarrockInns.BEER, BEER_PRICE, give)) chatPlayer(ChatAnim.SAD, NO_MONEY)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, HINTS)
                chatNpc(ChatAnim.QUIZ, THINK)
                chatNpc(ChatAnim.NEUTRAL, SEWERS)
                chatPlayer(ChatAnim.HAPPY, WHERE)
                chatNpc(ChatAnim.NEUTRAL, EAST_OF_PALACE)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, GOSSIP)
                chatNpc(ChatAnim.LAUGH, MAD)
                chatNpc(ChatAnim.NEUTRAL, OLD_NEWS)
            }
        }
    }

    internal suspend fun Dialogue.blueMoonCook(give: (Int) -> Unit) = with(BlueMoonCookLines) {
        chatNpc(ChatAnim.ANGRY, GREETING)
        when (choice(SELL, FREE, HORRIBLE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, SELL)
                chatNpc(ChatAnim.NEUTRAL, CABBAGE)
                when {
                    coins() < CABBAGE_PRICE -> {
                        chatPlayer(ChatAnim.SAD, NO_MONEY)
                        chatNpc(ChatAnim.ANGRY, WASTE)
                    }
                    player.inventory.contents().size >= Inventory.INV_CAPACITY -> {
                        chatPlayer(ChatAnim.SAD, NO_SPACE)
                        chatNpc(ChatAnim.ANGRY, WASTE)
                    }
                    choice(BUY, DONT_LIKE) == 1 -> {
                        chatPlayer(ChatAnim.HAPPY, BUY)
                        sell(VarrockInns.CABBAGE, CABBAGE_PRICE, give)
                        chatNpc(ChatAnim.HAPPY, EAT_IT)
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, DONT_LIKE)
                        chatNpc(ChatAnim.ANGRY, JUNK)
                    }
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, FREE)
                chatNpc(ChatAnim.ANGRY, FREE_MONEY)
                chatPlayer(ChatAnim.QUIZ, WHY_PAY)
                chatNpc(ChatAnim.ANGRY, WHY_FREE)
                chatPlayer(ChatAnim.NEUTRAL, FORGET_IT)
            }
            else -> {
                chatPlayer(ChatAnim.ANGRY, HORRIBLE)
                chatNpc(ChatAnim.ANGRY, CLEANING)
                chatPlayer(ChatAnim.ANGRY, DISGUST)
                chatNpc(ChatAnim.ANGRY, LEAVE_ME)
            }
        }
    }

    internal suspend fun Dialogue.jollyBoarCook() = with(JollyBoarCookLines) {
        chatNpc(ChatAnim.ANGRY, GREETING)
        val (ask, lines, playerSays) = when (choice(COOK, WHY, JOKE)) {
            1 -> Triple(COOK, COOK_LINES, COOK_PLAYER)
            2 -> Triple(WHY, WHY_LINES, WHY_PLAYER)
            else -> Triple(JOKE, JOKE_LINES, JOKE_PLAYER)
        }
        chatPlayer(ChatAnim.QUIZ, ask)
        for ((i, line) in lines.withIndex()) {
            if (i + 1 in playerSays) chatPlayer(ChatAnim.NEUTRAL, line) else chatNpc(ChatAnim.ANGRY, line)
        }
    }
}

/** The inns' bartenders and cooks. */
fun registerVarrockInns(world: World) {
    with(VarrockInns) {
        val talks: List<Pair<Int, suspend Dialogue.((Int) -> Unit) -> Unit>> = listOf(
            BLUE_MOON_BARTENDER to { give -> blueMoonBartender(give) },
            DANCING_DONKEY_BARTENDER to { give -> dancingDonkeyBartender(give) },
            JOLLY_BOAR_BARTENDER to { give -> jollyBoarBartender(give) },
            BLUE_MOON_COOK to { give -> blueMoonCook(give) },
            JOLLY_BOAR_COOK to { _ -> jollyBoarCook() },
        )
        for ((id, talk) in talks) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                world.dialogues.start(this, op.npc) {
                    talk { obj -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } }
                }
            }
        }
    }
}
