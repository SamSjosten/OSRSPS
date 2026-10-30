package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** The candle seller's lines; the words are in the dialogue file, from the Wiki's Transcript:Candle seller, read 2026-10-30. */
object CandleSellerLines : Lines("candle_seller", "Transcript:Candle seller") {
    val OFFER = line("offer", "the seller's first line, the candle and its price")
    val YES = line("yes", "first menu, option 1, which the player then says")
    val THOUSAND = line("thousand", "first menu, option 2, which the player then says")
    val CURSE = line("curse", "first menu, option 3 (and the lantern lesson's option 2), which the player then says")
    val LANTERNS = line("lanterns", "how lanterns are made: an option in three menus, which the player then says")
    val CANT_AFFORD = line("cant_afford", "buying with under 1000 coins: the player says so")
    val NO_CANDLE = line("no_candle", "buying with under 1000 coins: the seller's answer")
    val HERE = line("here", "the candle bought: the seller hands it over")
    val CAREFUL = line("careful", "the candle bought: the seller's warning, and a lantern")
    val NAKED_FLAME = line("naked_flame", "the menu after buying, option 1, which the player then says")
    val HEH = line("heh", "after option 1: the seller's answer")
    val THANKS_BYE = line("thanks_bye", "the menu after buying, option 3, which the player then says")
    val LOOK = line("look", "after the price: the seller's first line, why it costs so much")
    val SO = line("so", "after the price: the seller's second line")
    val PRIVILEGE = line("privilege", "after the price: the seller's third line")
    val YOU_WIN = line("you_win", "the menus after the price and the lesson, option 1: buying after all")
    val NO_WAY = line("no_way", "the menu after the price, option 2, which the player then says")
    val LESSON_OUT = line("lesson_out", "the lantern lesson, line 1")
    val LESSON_FIRSTLY = line("lesson_firstly", "the lantern lesson, line 2")
    val LESSON_THEN = line("lesson_then", "the lantern lesson, line 3")
    val LESSON_FINALLY = line("lesson_finally", "the lantern lesson, line 4")
    val LESSON_ONCE = line("lesson_once", "the lantern lesson, line 5")
    val LESSON_FOR = line("lesson_for", "the lantern lesson, line 6")
    val LESSON_GOLD = line("lesson_gold", "the lantern lesson, line 7, back to the candle's price")
}

/**
 * The candle seller at the entrance to the Lumbridge Swamp Caves (NPC 1896), who sells a lit candle for 1000 coins and
 * tells how lanterns are made; once a player has heard that, the first menu offers it again.
 *
 * From the Wiki's transcript: every branch, the price and the coin check. Guessed: every chathead expression; that the
 * candle goes in as the seller hands it over, landing at the player's feet with no free slot (the transcript doesn't
 * say); that having heard about lanterns is kept with the account by name, as [osrsps.game.Charges] are, from the
 * lesson's first line; and that the player says the lanterns option out loud from the first menu too.
 */
object CandleSeller {
    const val ID = 1896
    const val TALK_TO = 1

    const val COINS = 995
    const val PRICE = 1000

    /** lit_candle. */
    const val LIT_CANDLE = 33

    /** The name having heard how lanterns are made is kept under. */
    const val HEARD_LANTERNS = "candle_seller_lanterns"

    fun heardLanterns(player: Player): Boolean = player.charges[HEARD_LANTERNS] > 0

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit): Unit = with(CandleSellerLines) {
        chatNpc(ChatAnim.NEUTRAL, OFFER)
        val options = listOfNotNull(YES, THOUSAND, CURSE, LANTERNS.takeIf { heardLanterns(player) })
        when (options[choice(*options.toTypedArray()) - 1]) {
            YES -> {
                chatPlayer(ChatAnim.HAPPY, YES)
                buy(give)
            }
            THOUSAND -> {
                chatPlayer(ChatAnim.SHOCKED, THOUSAND)
                chatNpc(ChatAnim.NEUTRAL, LOOK)
                chatNpc(ChatAnim.NEUTRAL, SO)
                chatNpc(ChatAnim.HAPPY, PRIVILEGE)
                when (choice(YOU_WIN, NO_WAY, LANTERNS)) {
                    1 -> {
                        chatPlayer(ChatAnim.SAD, YOU_WIN)
                        buy(give)
                    }
                    2 -> chatPlayer(ChatAnim.ANGRY, NO_WAY)
                    else -> lanterns(give)
                }
            }
            CURSE -> chatPlayer(ChatAnim.NEUTRAL, CURSE)
            else -> lanterns(give)
        }
    }

    private suspend fun Dialogue.buy(give: (Player, Int) -> Unit): Unit = with(CandleSellerLines) {
        if (player.inventory.count(COINS) < PRICE) {
            chatPlayer(ChatAnim.SAD, CANT_AFFORD)
            chatNpc(ChatAnim.ANGRY, NO_CANDLE)
            return@with
        }
        takeCoins(player, PRICE)
        give(player, LIT_CANDLE)
        chatNpc(ChatAnim.HAPPY, HERE)
        chatNpc(ChatAnim.NEUTRAL, CAREFUL)
        when (choice(NAKED_FLAME, LANTERNS, THANKS_BYE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, NAKED_FLAME)
                chatNpc(ChatAnim.LAUGH, HEH)
            }
            2 -> lanterns(give)
            else -> chatPlayer(ChatAnim.HAPPY, THANKS_BYE)
        }
    }

    private suspend fun Dialogue.lanterns(give: (Player, Int) -> Unit): Unit = with(CandleSellerLines) {
        chatPlayer(ChatAnim.QUIZ, LANTERNS)
        player.charges[HEARD_LANTERNS] = 1
        chatNpc(ChatAnim.NEUTRAL, LESSON_OUT)
        chatNpc(ChatAnim.NEUTRAL, LESSON_FIRSTLY)
        chatNpc(ChatAnim.NEUTRAL, LESSON_THEN)
        chatNpc(ChatAnim.NEUTRAL, LESSON_FINALLY)
        chatNpc(ChatAnim.NEUTRAL, LESSON_ONCE)
        chatNpc(ChatAnim.NEUTRAL, LESSON_FOR)
        chatNpc(ChatAnim.HAPPY, LESSON_GOLD)
        if (choice(YOU_WIN, CURSE) == 1) {
            chatPlayer(ChatAnim.SAD, YOU_WIN)
            buy(give)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, CURSE)
        }
    }

    /** Takes [count] coins from the inventory, from its first coin slots on. */
    private fun takeCoins(player: Player, count: Int) {
        var left = count
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == COINS) left -= player.inventory.remove(slot, left)
        }
    }
}

/** The candle seller's Talk-to. */
fun registerCandleSeller(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(CandleSeller.ID, CandleSeller.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(CandleSeller) { talk(give) } }
    }
}
