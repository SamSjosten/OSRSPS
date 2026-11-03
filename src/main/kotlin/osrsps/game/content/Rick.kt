package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Rick's lines on a members' world, for a player who hasn't done the 2020 Halloween event (the Wiki's Transcript:Rick (Wizards' Tower)). */
object RickLines : Lines("rick", "Transcript:Rick (Wizards' Tower)") {
    val HEY = line("hey", "the player's opening line")
    val HELLO = line("hello", "his greeting")
    val WHO = line("who", "the option asking who he is, which the player then says")
    val NAME = line("name", "his answer: his name and his old study")
    val WHY_NOT = line("why_not", "the player's question about why")
    val PUMPKIN = line("pumpkin", "his answer: the pumpkin")
    val LUCKILY = line("luckily", "his next box: an adventurer helped")
    val LUCKY = line("lucky", "the player's reply")
    val BUY = line("buy", "the option asking to buy a crystal, which the player then says")
    val OF_COURSE = line("of_course", "his answer to it")
    val WHICH = line("which", "the option asking where the crystal works, which the player then says")
    val PLACES = line("places", "his answer: the places the crystals work")
    val GAUNTLET = line("gauntlet", "his next box: the Gauntlet")
    val DOING = line("doing", "the option asking what he does here, which the player then says")
    val STUDENT = line("student", "his answer's first box")
    val ARDOUGNE = line("ardougne", "his answer's second box")
    val FIELDS = line("fields", "the player's question about the fields")
    val PUMPKIN_FIELDS = line("pumpkin_fields", "his last line")
    val SELL = line("sell", "the option asking what he sells, which the player then says")
    val CABBAGES = line("cabbages", "his answer: the cabbage research")
    val SALESMAN = line("salesman", "the player cutting him off")
    val SHARDS = line("shards", "his answer: the crystal shards")
    val CALL_THEM = line("call_them", "his next box: their name")
    val WHY_WANT = line("why_want", "the player's question")
    val CAUGHT = line("caught", "his answer's first box")
    val ENHANCED = line("enhanced", "his answer's second box")
    val ZONING = line("zoning", "his box about zoning out")
    val COMBAT = line("combat", "his box about triggering in combat")
    val RUN_THROUGH = line("run_through", "his box about running through a dangerous area")
    val RING_OF_LIFE = line("ring_of_life", "his box about where a ring of life works")
    val LIKE_ONE = line("like_one", "his box about acting like a ring of life")
    val BUY_ONE = line("buy_one", "his offer: 75000 gold each")
    val SURE = line("sure", "the offer's option to buy, which the player then says")
    val WONDERFUL = line("wonderful", "his answer to it")
    val TOO_EXPENSIVE = line("too_expensive", "the offer's option to refuse, which the player then says")
    val LOT_GOES_INTO = line("lot_goes_into", "his answer to it")
    val GOODBYE = line("goodbye", "the option to leave, which the player then says")
    val BYE = line("bye", "his farewell")
    val BUY_PROMPT = line("buy_prompt", "the yes or no menu's title before buying")
    val YES = line("yes", "the yes or no menu's option 1")
    val NO = line("no", "the yes or no menu's option 2")
    val CANT_AFFORD = line("cant_afford", "his line when the player has fewer than 75000 coins")
}

/**
 * Rick (`telecrystal_wizard_shop` 11410) on the Wizards' Tower's first floor (Draynor piece step 15d), who sells escape
 * crystals (`tob_teleport` 22517) for 75,000 coins each, as the Wiki's transcript has him on a members' world for a
 * player who hasn't done the 2020 Halloween event (no events here). Buying asks yes or no, then how many.
 *
 * Left out: the crystal's own Teleport, Auto-afk and Configure (not built). Guessed: that he is spawned as the type that
 * shows Buy-crystals from the start (live shows `telecrystal_wizard_no_shop` 11409 until the first talk), that
 * Buy-crystals goes straight to the yes or no menu, the count prompt's words, and every chathead face.
 */
object Rick {
    const val RICK = 11410
    const val TALK_TO = 1
    const val BUY_CRYSTALS = 3
    const val ESCAPE_CRYSTAL = 22517
    const val PRICE = 75_000

    /** Asks yes or no and how many, then sells as many as the coins and the backpack allow. */
    internal suspend fun Dialogue.purchase(give: (Player, Int) -> Int) = with(RickLines) {
        if (player.inventory.count(Ned.COINS) < PRICE) return chatNpc(ChatAnim.SAD, CANT_AFFORD)
        if (choice(YES, NO, title = text(BUY_PROMPT)) != 1) return
        val wanted = context.countDialog()
        val afford = (player.inventory.count(Ned.COINS) / PRICE).toInt()
        val count = minOf(wanted, afford)
        if (count <= 0) return
        val bought = give(player, count)
        if (bought > 0) Ned.take(player, Ned.COINS, bought * PRICE)
    }

    private suspend fun Dialogue.offer(menu: Boolean, give: (Player, Int) -> Int): Unit = with(RickLines) {
        chatNpc(ChatAnim.HAPPY, BUY_ONE)
        val options = if (menu) arrayOf(SURE, WHICH, TOO_EXPENSIVE) else arrayOf(SURE, TOO_EXPENSIVE)
        when (options[choice(*options) - 1]) {
            SURE -> {
                chatPlayer(ChatAnim.HAPPY, SURE)
                chatNpc(ChatAnim.HAPPY, WONDERFUL)
                purchase(give)
            }
            WHICH -> which(give)
            else -> {
                chatPlayer(ChatAnim.ANGRY, TOO_EXPENSIVE)
                chatNpc(ChatAnim.NEUTRAL, LOT_GOES_INTO)
            }
        }
    }

    private suspend fun Dialogue.which(give: (Player, Int) -> Int) = with(RickLines) {
        chatPlayer(ChatAnim.QUIZ, WHICH)
        chatNpc(ChatAnim.NEUTRAL, PLACES)
        chatNpc(ChatAnim.NEUTRAL, GAUNTLET)
        offer(menu = false, give)
    }

    private suspend fun Dialogue.sell(give: (Player, Int) -> Int) = with(RickLines) {
        chatPlayer(ChatAnim.QUIZ, SELL)
        chatNpc(ChatAnim.HAPPY, CABBAGES)
        chatPlayer(ChatAnim.ANGRY, SALESMAN)
        chatNpc(ChatAnim.HAPPY, SHARDS)
        chatNpc(ChatAnim.NEUTRAL, CALL_THEM)
        chatPlayer(ChatAnim.QUIZ, WHY_WANT)
        for (line in listOf(CAUGHT, ENHANCED, ZONING, COMBAT, RUN_THROUGH, RING_OF_LIFE, LIKE_ONE)) chatNpc(ChatAnim.NEUTRAL, line)
        offer(menu = true, give)
    }

    /** His talk: the top menu, and after his answers about himself the shorter one, until the player leaves or buys. */
    internal suspend fun Dialogue.rick(give: (Player, Int) -> Int): Unit = with(RickLines) {
        chatPlayer(ChatAnim.NEUTRAL, HEY)
        chatNpc(ChatAnim.NEUTRAL, HELLO)
        var options = arrayOf(WHO, BUY, WHICH, DOING, GOODBYE)
        while (true) {
            when (options[choice(*options) - 1]) {
                WHO -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.NEUTRAL, NAME)
                    chatPlayer(ChatAnim.QUIZ, WHY_NOT)
                    chatNpc(ChatAnim.SAD, PUMPKIN)
                    chatNpc(ChatAnim.HAPPY, LUCKILY)
                    chatPlayer(ChatAnim.NEUTRAL, LUCKY)
                    options = arrayOf(DOING, SELL, GOODBYE)
                }
                DOING -> {
                    chatPlayer(ChatAnim.QUIZ, DOING)
                    chatNpc(ChatAnim.NEUTRAL, STUDENT)
                    chatNpc(ChatAnim.NEUTRAL, ARDOUGNE)
                    chatPlayer(ChatAnim.QUIZ, FIELDS)
                    chatNpc(ChatAnim.LAUGH, PUMPKIN_FIELDS)
                    options = arrayOf(WHO, SELL, GOODBYE)
                }
                BUY -> {
                    chatPlayer(ChatAnim.QUIZ, BUY)
                    chatNpc(ChatAnim.HAPPY, OF_COURSE)
                    return purchase(give)
                }
                WHICH -> return which(give)
                SELL -> return sell(give)
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    chatNpc(ChatAnim.NEUTRAL, BYE)
                    return
                }
            }
        }
    }
}

fun registerRick(world: World) {
    // Escape crystals don't stack: one a free slot, and the coins for those that fit.
    val give: (Player, Int) -> Int = { player, count ->
        val type = world.objTypes[Rick.ESCAPE_CRYSTAL] ?: error("No obj type ${Rick.ESCAPE_CRYSTAL} for the escape crystal")
        player.inventory.add(type, count)
    }
    with(Rick) {
        world.handlers.onNpc(RICK, TALK_TO) { op -> world.dialogues.start(this, op.npc) { rick(give) } }
        world.handlers.onNpc(RICK, BUY_CRYSTALS) { op -> world.dialogues.start(this, op.npc) { purchase(give) } }
    }
}
