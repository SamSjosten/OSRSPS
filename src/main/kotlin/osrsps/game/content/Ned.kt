package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** Ned's lines once Dragon Slayer I and The Fremennik Isles are done (the Wiki's Transcript:Ned, read 2026-10-30). */
object NedLines : Lines("ned", "Transcript:Ned") {
    val GREETING = line("greeting", "Ned's first line, offering rope")
    val CRANDOR = line("crandor", "first menu, option 1, which the player then says: how he got back from Crandor")
    val CRANDOR_HOW = line("crandor_how", "option 1: Ned's answer")
    val CRANDOR_ROPE = line("crandor_rope", "option 1: Ned's second line, offering rope again")
    val AGAIN = line("again", "the Crandor menu, option 1: going back to Crandor")
    val AGAIN_SAY = line("again_say", "the Crandor menu, option 1: what the player says")
    val AGAIN_DOCKS = line("again_docks", "the Crandor menu, option 1: Ned's answer, meeting at Port Sarim's docks")
    val ROPE = line("rope", "first menu, option 2 (and the Crandor menu's), which the player then says")
    val PRICE = line("price", "the rope talk: Ned's price, 15 coins or 4 balls of wool")
    val ROPE_2 = line("rope_2", "the rope talk: the player's second line")
    val ROPE_3 = line("rope_3", "the rope talk: Ned's third line")
    val ROPE_4 = line("rope_4", "the rope talk: the player's third line")
    val ROPE_5 = line("rope_5", "the rope talk: Ned's last line before the menu")
    val SELL = line("sell", "the rope menu, option 1, which the player then says: buying for coins")
    val CANT_AFFORD = line("cant_afford", "option 1 with under 15 coins: the message box")
    val HERE = line("here", "option 1 with 15 coins: Ned hands the rope over")
    val WOOL = line("wool", "the rope menu, option 3 with 4 balls of wool, which the player then says")
    val WOOL_THANKS = line("wool_thanks", "option 3: Ned's answer, with the rope")
    val EXPENSIVE = line("expensive", "the rope menu, option 2: the price is too high")
    val RUM = line("rum", "option 2: Ned's answer, the price and his rum")
    val GET_WOOL = line("get_wool", "the rope menu, option 3 with under 4 balls of wool: going to get some")
    val FOUR_BALLS = line("four_balls", "that option: Ned's reminder that it takes 4")
    val DECLINE = line("decline", "first menu, option 3 (and the Crandor menu's), which the player then says")
    val COME_BACK = line("come_back", "option 3: Ned's answer")
    val YAK_SMELL = line("yak_smell", "after buying, The Fremennik Isles done: the player on the rope's smell")
    val YAK_WHAT = line("yak_what", "Ned's one-word answer")
    val YAK_ISLES = line("yak_isles", "the player on the Isles and yak hair")
    val YAK_WELL = line("yak_well", "Ned's three-word answer")
    val YAK_TRADER = line("yak_trader", "the player on the wool trader who sold rope")
    val YAK_SO = line("yak_so", "Ned's six-word answer")
    val YAK_ACCUSE = line("yak_accuse", "the player: Ned trades wool for yak-hair rope")
    val YAK_CONFESS = line("yak_confess", "Ned confesses: his fingers and the demand")
    val YAK_BRAND = line("yak_brand", "Ned goes on: the shop's name")
    val YAK_ACCEPT = line("yak_accept", "the player accepts it")
    val YAK_SAMPLE = line("yak_sample", "with a free slot: Ned offers a free rope")
    val YAK_SECRET = line("yak_secret", "the player promises to keep it quiet")
    val YAK_ROPE = line("yak_rope", "with a free slot: the item box, the free rope")
    val YAK_JOFRIDR = line("yak_jofridr", "Ned asks the player to pass a message to Jofridr")
    val YAK_BYE = line("yak_bye", "the player's last line")
}

/**
 * Ned (NPC 4280) in his house in the north-east of Draynor (Draynor piece step 4), after Dragon Slayer I and The
 * Fremennik Isles, which every account counts as done. He sells a rope for 15 coins or makes one from 4 balls of wool;
 * Trade opens Ned's Handmade Rope (shops.json, through [registerShops]).
 *
 * From the Wiki's transcript: every branch, the coin and wool checks, and the Isles talk that follows buying, or trying
 * to buy, for coins, with a free rope when there's a slot (the Wiki: "he gives an extra rope free with each purchase").
 * Guessed: every chathead expression; that the bought rope lands at the player's feet with no free slot, as
 * [CandleSeller]'s candle does; that the wool menu option shows with 4 or more balls.
 */
object Ned {
    const val ID = 4280
    const val TALK_TO = 1

    const val COINS = 995
    const val ROPE_COINS = 15

    const val ROPE_ITEM = 954
    const val BALL_OF_WOOL = 1759
    const val WOOL_PER_ROPE = 4

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit): Unit = with(NedLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(CRANDOR, ROPE, DECLINE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, CRANDOR)
                chatNpc(ChatAnim.NEUTRAL, CRANDOR_HOW)
                chatNpc(ChatAnim.HAPPY, CRANDOR_ROPE)
                when (choice(AGAIN, ROPE, DECLINE)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, AGAIN_SAY)
                        chatNpc(ChatAnim.HAPPY, AGAIN_DOCKS)
                    }
                    2 -> rope(give)
                    else -> decline()
                }
            }
            2 -> rope(give)
            else -> decline()
        }
    }

    private suspend fun Dialogue.decline(): Unit = with(NedLines) {
        chatPlayer(ChatAnim.NEUTRAL, DECLINE)
        chatNpc(ChatAnim.NEUTRAL, COME_BACK)
    }

    private suspend fun Dialogue.rope(give: (Player, Int) -> Unit): Unit = with(NedLines) {
        chatPlayer(ChatAnim.QUIZ, ROPE)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        chatPlayer(ChatAnim.QUIZ, ROPE_2)
        chatNpc(ChatAnim.NEUTRAL, ROPE_3)
        chatPlayer(ChatAnim.QUIZ, ROPE_4)
        chatNpc(ChatAnim.NEUTRAL, ROPE_5)
        val enoughWool = player.inventory.count(BALL_OF_WOOL) >= WOOL_PER_ROPE
        when (choice(SELL, EXPENSIVE, if (enoughWool) WOOL else GET_WOOL)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, SELL)
                if (player.inventory.count(COINS) < ROPE_COINS) {
                    mesbox(CANT_AFFORD)
                } else {
                    take(player, COINS, ROPE_COINS)
                    give(player, ROPE_ITEM)
                    chatNpc(ChatAnim.HAPPY, HERE)
                }
                yakHair(give)
            }
            2 -> {
                chatPlayer(ChatAnim.ANGRY, EXPENSIVE)
                chatNpc(ChatAnim.NEUTRAL, RUM)
            }
            else -> if (enoughWool) {
                chatPlayer(ChatAnim.NEUTRAL, WOOL)
                take(player, BALL_OF_WOOL, WOOL_PER_ROPE)
                give(player, ROPE_ITEM)
                chatNpc(ChatAnim.HAPPY, WOOL_THANKS)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, GET_WOOL)
                chatNpc(ChatAnim.NEUTRAL, FOUR_BALLS)
            }
        }
    }

    /** What follows buying for coins once The Fremennik Isles is done: the yak hair, and a free rope with a slot for it. */
    private suspend fun Dialogue.yakHair(give: (Player, Int) -> Unit): Unit = with(NedLines) {
        chatPlayer(ChatAnim.QUIZ, YAK_SMELL)
        chatNpc(ChatAnim.NEUTRAL, YAK_WHAT)
        chatPlayer(ChatAnim.NEUTRAL, YAK_ISLES)
        chatNpc(ChatAnim.NEUTRAL, YAK_WELL)
        chatPlayer(ChatAnim.NEUTRAL, YAK_TRADER)
        chatNpc(ChatAnim.NEUTRAL, YAK_SO)
        chatPlayer(ChatAnim.ANGRY, YAK_ACCUSE)
        chatNpc(ChatAnim.SAD, YAK_CONFESS)
        chatNpc(ChatAnim.SAD, YAK_BRAND)
        chatPlayer(ChatAnim.NEUTRAL, YAK_ACCEPT)
        val room = player.inventory.freeSlots > 0
        if (room) chatNpc(ChatAnim.HAPPY, YAK_SAMPLE)
        chatPlayer(ChatAnim.HAPPY, YAK_SECRET)
        if (room) {
            give(player, ROPE_ITEM)
            objbox(ROPE_ITEM, YAK_ROPE)
        }
        chatNpc(ChatAnim.NEUTRAL, YAK_JOFRIDR)
        chatPlayer(ChatAnim.HAPPY, YAK_BYE)
    }

    /** Takes [count] of item [id] from the inventory, from its first slots holding it on. */
    internal fun take(player: Player, id: Int, count: Int) {
        var left = count
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == id) left -= player.inventory.remove(slot, left)
        }
    }
}

/** Ned's Talk-to. Trade is [registerShops]'s. */
fun registerNed(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(Ned.ID, Ned.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Ned) { talk(give) } }
    }
}
