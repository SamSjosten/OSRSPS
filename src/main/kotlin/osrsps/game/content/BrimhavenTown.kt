package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Davon's words, the Wiki's Transcript:Davon (read 2026-11-18). */
object DavonLines : Lines("davon", "Transcript:Davon") {
    val PSSST = line("pssst", "his greeting")
    val SELLING = line("selling", "option 1 (the shop opens, with no line from the player)")
    val WHAT_PSSST = line("what_pssst", "option 2, which the player then says")
    val THROAT = line("throat", "option 2: he was clearing his throat")
    val RESTOCK = line("restock", "option 3, which the player then says")
    val HARD_TO_GET = line("hard_to_get", "option 3: he waits for adventurers to supply him")
}

/** The Dead Man's Chest's bartender's words, the Wiki's Transcript:Bartender (Dead Man's Chest) (read 2026-11-18). */
object DeadMansBartenderLines : Lines("deadmans_bartender", "Transcript:Bartender (Dead Man's Chest)") {
    val GREETING = line("greeting", "his greeting: what would the player like to drink?")
    val NOTHING = line("nothing", "option 1, which the player then says")
    val GROG = line("grog", "option 2, which the player then says")
    val THREE_COINS = line("three_coins", "option 2: one grog, three coins")
    val RUM = line("rum", "option 3, which the player then says")
    val TWENTY_SEVEN = line("twenty_seven", "option 3: 27 coins")
    val NO_MONEY = line("no_money", "options 2 and 3, short of the coins: the player can't pay")
}

/** Alfonse the waiter's words, the Wiki's Transcript:Alfonse the waiter (read 2026-11-18). */
object AlfonseLines : Lines("alfonse", "Transcript:Alfonse the waiter") {
    val WELCOME = line("welcome", "his greeting: would the player like to order? (live says sir or madam by the player's look; ours always the one form until appearance is built)")
    val YES = line("yes", "option 1, which the player then says (the shop opens)")
    val NO = line("no", "option 2, which the player then says")
    val KARAMBWAN = line("karambwan", "option 3, which the player then says")
    val LUBUFU = line("lubufu", "option 3: they buy off Lubufu, who has a monopoly")
    val WHERE_LUBUFU = line("where_lubufu", "its option 1, which the player then says")
    val SOUTH = line("south", "its option 1: south of the town")
    val MONOPOLY = line("monopoly", "its option 2, which the player then says")
    val SECRET = line("secret", "its option 2: nobody else knows how")
    val THANKS = line("thanks", "after either answer, the player's thanks")
}

/**
 * Brimhaven's town (Karamja step K3.2), as the Wiki's Brimhaven, shop and NPC pages have it (read 2026-11-18):
 *
 * - **Davon** (8680) on the south shore talks and runs Davon's Amulet Store (shops.json): it starts with none of its
 *   five amulets and buys them back at 90%.
 * - **The Dead Man's Chest**'s bartender (1314) sells grog for 3 coins and Karamjan rum for 27 through his talk.
 * - **The Shrimp and Parrot**'s Alfonse the waiter (4920) talks and runs its food shop (shops.json); Charlie the cook
 *   (4921) stands in its kitchen.
 * - The ropeswings to and from Moss Giant Island are an Agility shortcut (skills/agility.json); the range house and the
 *   Shrimp and Parrot's kitchen have the ordinary range (Cooking's RANGE).
 *
 * Guessed: the chathead faces; that a full backpack gets the drink at the player's feet; that the bartender takes the
 * coins after his price line, as the transcript ends there. Left out: Karamja gloves' better prices at Davon's (no
 * Karamja Diary yet), Alfred Grimhand's Barcrawl, Heroes' Quest and Tribal Totem, Charlie's talk (only the quest's), the
 * kitchen door, the item spawns, and the fighting townsfolk (step K3.2b).
 */
object BrimhavenTown {
    const val DAVON = 8680
    const val BARTENDER = 1314
    const val ALFONSE = 4920
    const val CHARLIE = 4921
    const val TALK_TO = 1

    const val GROG = 1915
    const val GROG_PRICE = 3
    const val RUM = 431
    const val RUM_PRICE = 27

    /** Davon's talk; true when the player asked what he sells. */
    internal suspend fun Dialogue.davon(): Boolean = with(DavonLines) {
        chatNpc(ChatAnim.NEUTRAL, PSSST)
        when (choice(SELLING, WHAT_PSSST, RESTOCK)) {
            1 -> return@with true
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_PSSST)
                chatNpc(ChatAnim.NEUTRAL, THROAT)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, RESTOCK)
                chatNpc(ChatAnim.NEUTRAL, HARD_TO_GET)
            }
        }
        false
    }

    /** The bartender's talk; [give] hands over a drink once paid. */
    internal suspend fun Dialogue.bartender(give: (Int) -> Unit) = with(DeadMansBartenderLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        val (drink, price) = when (choice(NOTHING, GROG, RUM)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, NOTHING)
                return@with
            }
            2 -> {
                chatPlayer(ChatAnim.HAPPY, GROG)
                chatNpc(ChatAnim.HAPPY, THREE_COINS)
                BrimhavenTown.GROG to GROG_PRICE
            }
            else -> {
                chatPlayer(ChatAnim.HAPPY, RUM)
                chatNpc(ChatAnim.HAPPY, TWENTY_SEVEN)
                BrimhavenTown.RUM to RUM_PRICE
            }
        }
        if (AlKharidTraders.pay(player, price)) give(drink) else chatPlayer(ChatAnim.SAD, NO_MONEY)
    }

    /** Alfonse's talk; true when the player asked to order. */
    internal suspend fun Dialogue.alfonse(): Boolean = with(AlfonseLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        when (choice(YES, NO, KARAMBWAN)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, YES)
                return@with true
            }
            2 -> chatPlayer(ChatAnim.NEUTRAL, NO)
            else -> {
                chatPlayer(ChatAnim.QUIZ, KARAMBWAN)
                chatNpc(ChatAnim.NEUTRAL, LUBUFU)
                if (choice(WHERE_LUBUFU, MONOPOLY) == 1) {
                    chatPlayer(ChatAnim.QUIZ, WHERE_LUBUFU)
                    chatNpc(ChatAnim.NEUTRAL, SOUTH)
                } else {
                    chatPlayer(ChatAnim.QUIZ, MONOPOLY)
                    chatNpc(ChatAnim.NEUTRAL, SECRET)
                }
                chatPlayer(ChatAnim.HAPPY, THANKS)
            }
        }
        false
    }
}

fun registerBrimhavenTown(world: World) {
    with(BrimhavenTown) {
        world.handlers.onNpc(DAVON, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = davon() }
            if (trade) world.shopTypes.ofNpc(DAVON)?.let { world.shopScreen.open(player, it) }
        }
        world.handlers.onNpc(BARTENDER, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) {
                bartender { obj -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } }
            }
        }
        world.handlers.onNpc(ALFONSE, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = alfonse() }
            if (trade) world.shopTypes.ofNpc(ALFONSE)?.let { world.shopScreen.open(player, it) }
        }
    }
}
