package osrsps.game.content

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/**
 * The lines of an Al Kharid General Store keeper's Talk-to, from that keeper's transcript (read 2026-10-30). The shop
 * keeper's and the assistant's pages are separate, so each has its own keys. The Ali Morrisane topic is there after
 * Rogue Trader, which every account has done.
 */
abstract class AlKharidStoreLines(prefix: String, page: String, who: String) : Lines(prefix, page) {
    val HELP = line("help", "the $who's first line")
    val SELLING = line("selling", "option 1: what they sell (the shop opens, with no line from the player)")
    val NO_THANKS = line("no_thanks", "option 2, which the player then says")
    val ALI = line("ali", "option 3 (after Rogue Trader): what they think of Ali Morrisane, which the player then says")
    val ALI_1 = line("ali_1", "the Ali Morrisane topic: the $who's first line")
    val ALI_2 = line("ali_2", "the Ali Morrisane topic: second line")
    val ALI_3 = line("ali_3", "the Ali Morrisane topic: third line")
    val WHAT_DO = line("what_do", "the Ali Morrisane topic: the player asks what they'll do about it")
    val USUAL = line("usual", "the Ali Morrisane topic: business as usual")
    val ANYTHING = line("anything", "the Ali Morrisane topic: the $who asks if they can help with anything, before the menu again")
}

object AlKharidShopKeeperLines : AlKharidStoreLines("al_kharid_shop_keeper", "Transcript:Shop keeper (Al Kharid)", "shop keeper")

object AlKharidShopAssistantLines : AlKharidStoreLines("al_kharid_shop_assistant", "Transcript:Shop assistant (Al Kharid)", "shop assistant")

/** Zeke's lines; the words are in the dialogue file, from the Wiki's Transcript:Zeke, read 2026-10-30. */
object ZekeLines : Lines("zeke", "Transcript:Zeke") {
    val GREETING = line("greeting", "his first line")
    val TRADE = line("trade", "option 1, which the player then says")
    val SCIMITARS = line("scimitars", "option 1: his answer, which ends the talk (the Wiki opens no shop here)")
    val CLOAK = line("cloak", "option 2, which the player then says")
    val THANK_YOU = line("thank_you", "option 2: his answer")
    val DRAGON = line("dragon", "option 3: a dragon scimitar, which the player then says")
    val A_DRAGON = line("a_dragon", "the dragon scimitar topic: his first line")
    val ONLY_PLACE = line("only_place", "the dragon scimitar topic: second line")
    val HMMM = line("hmmm", "the dragon scimitar topic, after Monkey Madness I: the player's line")
    val PERHAPS = line("perhaps", "his offer of his own stock, at the end of either topic")
    val YES_PLEASE = line("yes_please", "that offer's menu: yes, and the shop opens")
    val NOT_TODAY = line("not_today", "that offer's menu: no")
    val ALI = line("ali", "option 4 (after Rogue Trader): Ali Morrisane, which the player then says")
    val ALI_1 = line("ali_1", "the Ali Morrisane topic: his first line")
    val ALI_2 = line("ali_2", "the Ali Morrisane topic: second line")
    val ALI_3 = line("ali_3", "the Ali Morrisane topic: third line")
    val THREATS = line("threats", "the Ali Morrisane topic: the player asks what threats")
    val WARES = line("wares", "the Ali Morrisane topic: his answer, before his offer")
}

/** Dommik's lines; the words are in the dialogue file, from the Wiki's Transcript:Dommik, read 2026-10-30. */
object DommikLines : Lines("dommik", "Transcript:Dommik") {
    val GREETING = line("greeting", "his first line")
    val NO_THANKS = line("no_thanks", "option 1, which the player then says")
    val FARE_WELL = line("fare_well", "option 1: his goodbye")
    val SEE = line("see", "option 2, which the player then says before the shop opens")
    val ALI = line("ali", "option 3 (after Rogue Trader): Ali Morrisane, which the player then says")
    val ALI_1 = line("ali_1", "the Ali Morrisane topic: his first line")
    val ALI_2 = line("ali_2", "the Ali Morrisane topic: second line")
    val WHAT_THEN = line("what_then", "the Ali Morrisane topic: the player's question")
    val AS_ANY = line("as_any", "the Ali Morrisane topic: his answer, before the menu again")
}

/** Louie Legs's lines; the words are in the dialogue file, from the Wiki's Transcript:Louie Legs, read 2026-10-30. */
object LouieLegsLines : Lines("louie_legs", "Transcript:Louie Legs") {
    val GREETING = line("greeting", "his first line")
    val WHAT_GOT = line("what_got", "option 1, which the player then says")
    val LEGS = line("legs", "option 1: his answer, before the shop opens")
    val NO_THANK_YOU = line("no_thank_you", "option 2, which the player then says")
}

/** Ranael's lines; the words are in the dialogue file, from the Wiki's Transcript:Ranael, read 2026-10-30. */
object RanaelLines : Lines("ranael", "Transcript:Ranael") {
    val GREETING = line("greeting", "her first line")
    val YES_PLEASE = line("yes_please", "option 1: the shop opens, with no line from the player")
    val NOT_MY_SCENE = line("not_my_scene", "option 2, which the player then says")
}

/** The Gem trader's lines; the words are in the dialogue file, from the Wiki's Transcript:Gem trader, read 2026-10-30. */
object GemTraderLines : Lines("gem_trader", "Transcript:Gem trader") {
    val GREETING = line("greeting", "his first line")
    val YES_PLEASE = line("yes_please", "option 1: the shop opens, with no line from the player")
    val NO_THANK_YOU = line("no_thank_you", "option 2, which the player then says")
    val SUIT_YOURSELF = line("suit_yourself", "option 2: his answer")
}

/** Ali Morrisane's game message when he sells junk; the words are in the dialogue file, from the Wiki's Ali's Discount Wares. */
object AliMorrisaneLines : Lines("ali_morrisane", "Ali's Discount Wares") {
    val JUNK = line("junk", "game message: he has sold the player a piece of junk after they viewed his basic stock")
}

/**
 * Al Kharid's shop keepers' Talk-to (Al Kharid step 3): the General Store's Shop keeper (2817) and Shop assistant
 * (2818), Zeke (2875), Dommik (2879), Louie Legs (2876), Ranael (2878) and the Gem trader (2874). Trade is
 * [registerShops]'s, as is Ali Morrisane's (3533).
 *
 * From the Wiki's transcripts: each conversation, as after Monkey Madness I and Rogue Trader (every account has done
 * every quest), and where the shop opens. The Wiki has Zeke's "Do you want to trade?" end with his answer, opening no
 * shop; that is kept. Guessed: every chathead expression (2004Scape's where it has the line, else neutral), and that a
 * topic which "returns to the main options" shows the menu again without the greeting. Left out: the Al Kharid flyer
 * used on a keeper and the treasure trail steps (no flyer or clues yet), and Ali Morrisane's Talk-to, whose
 * conversations follow The Feud and Rogue Trader at length (its own step).
 */
object AlKharidShopkeepers {
    /** Ali's basic stock (shops.json). */
    const val ALIS_WARES = "alis_discount_wares"

    /** The junk Ali sells, each 1 in 5 (the Wiki's Junk table): bronze pickaxe, bucket, knife, raw chicken, tinderbox. */
    val JUNK = listOf(1265, 1925, 946, 2138, 590)

    /** What the junk costs. Guessed: the Wiki says only that the player needs 10 coins. */
    const val JUNK_PRICE = 10

    /**
     * After the player has viewed Ali's basic stock: with [JUNK_PRICE] coins and a free slot, he sells them one of
     * [JUNK] at random for those coins. Guessed: that it happens as the shop closes.
     */
    fun aliJunk(player: Player, random: GameRandom, give: (Player, Int) -> Unit, message: (Player) -> Unit) {
        val inv = player.inventory
        if (inv.count(ShopScreen.COINS) < JUNK_PRICE || inv.freeSlots == 0) return
        var left = JUNK_PRICE
        for ((slot, held) in inv.contents()) {
            if (left == 0) break
            if (held.id == ShopScreen.COINS) left -= inv.remove(slot, left)
        }
        give(player, random.pick(JUNK))
        message(player)
    }

    const val SHOP_KEEPER = 2817
    const val SHOP_ASSISTANT = 2818
    const val ZEKE = 2875
    const val LOUIE_LEGS = 2876
    const val RANAEL = 2878
    const val DOMMIK = 2879
    const val GEM_TRADER = 2874
    const val ALI_MORRISANE = 3533
    const val TALK_TO = 1

    /** A General Store keeper's talk; true when the player asked to see the shop. */
    internal suspend fun Dialogue.keeper(lines: AlKharidStoreLines): Boolean = with(lines) {
        chatNpc(ChatAnim.HAPPY, HELP)
        var trade: Boolean? = null
        while (trade == null) {
            when (choice(SELLING, NO_THANKS, ALI)) {
                1 -> trade = true
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    trade = false
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, ALI)
                    chatNpc(ChatAnim.ANGRY, ALI_1)
                    chatNpc(ChatAnim.ANGRY, ALI_2)
                    chatNpc(ChatAnim.ANGRY, ALI_3)
                    chatPlayer(ChatAnim.QUIZ, WHAT_DO)
                    chatNpc(ChatAnim.NEUTRAL, USUAL)
                    chatNpc(ChatAnim.HAPPY, ANYTHING)
                }
            }
        }
        trade == true
    }

    /** Zeke's talk; true when the player took his offer of his stock. */
    internal suspend fun Dialogue.zeke(): Boolean = with(ZekeLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(TRADE, CLOAK, DRAGON, ALI)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, TRADE)
                chatNpc(ChatAnim.HAPPY, SCIMITARS)
                return@with false
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, CLOAK)
                chatNpc(ChatAnim.NEUTRAL, THANK_YOU)
                return@with false
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, DRAGON)
                chatNpc(ChatAnim.CONFUSED, A_DRAGON)
                chatNpc(ChatAnim.NEUTRAL, ONLY_PLACE)
                chatPlayer(ChatAnim.NEUTRAL, HMMM)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, ALI)
                chatNpc(ChatAnim.NEUTRAL, ALI_1)
                chatNpc(ChatAnim.NEUTRAL, ALI_2)
                chatNpc(ChatAnim.NEUTRAL, ALI_3)
                chatPlayer(ChatAnim.QUIZ, THREATS)
                chatNpc(ChatAnim.NEUTRAL, WARES)
            }
        }
        chatNpc(ChatAnim.HAPPY, PERHAPS)
        choice(YES_PLEASE, NOT_TODAY) == 1
    }

    /** Dommik's talk; true when the player asked to see the shop. */
    internal suspend fun Dialogue.dommik(): Boolean = with(DommikLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        var trade: Boolean? = null
        while (trade == null) {
            when (choice(NO_THANKS, SEE, ALI)) {
                1 -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    chatNpc(ChatAnim.HAPPY, FARE_WELL)
                    trade = false
                }
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, SEE)
                    trade = true
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, ALI)
                    chatNpc(ChatAnim.NEUTRAL, ALI_1)
                    chatNpc(ChatAnim.NEUTRAL, ALI_2)
                    chatPlayer(ChatAnim.QUIZ, WHAT_THEN)
                    chatNpc(ChatAnim.NEUTRAL, AS_ANY)
                }
            }
        }
        trade == true
    }

    /** Louie Legs's talk; true when the player asked what he has. */
    internal suspend fun Dialogue.louieLegs(): Boolean = with(LouieLegsLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        if (choice(WHAT_GOT, NO_THANK_YOU) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, WHAT_GOT)
            chatNpc(ChatAnim.HAPPY, LEGS)
            return@with true
        }
        chatPlayer(ChatAnim.NEUTRAL, NO_THANK_YOU)
        false
    }

    /** Ranael's talk; true when the player said yes. */
    internal suspend fun Dialogue.ranael(): Boolean = with(RanaelLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        if (choice(YES_PLEASE, NOT_MY_SCENE) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, NOT_MY_SCENE)
        false
    }

    /** The Gem trader's talk; true when the player said yes. */
    internal suspend fun Dialogue.gemTrader(): Boolean = with(GemTraderLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        if (choice(YES_PLEASE, NO_THANK_YOU) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, NO_THANK_YOU)
        chatNpc(ChatAnim.NEUTRAL, SUIT_YOURSELF)
        false
    }
}

/** Al Kharid's shop keepers' Talk-to. Trade is [registerShops]'s. */
fun registerAlKharidShopkeepers(world: World) {
    fun openShop(player: Player, npc: Int) {
        world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
    }
    with(AlKharidShopkeepers) {
        val talks: List<Pair<Int, suspend Dialogue.() -> Boolean>> = listOf(
            SHOP_KEEPER to { keeper(AlKharidShopKeeperLines) },
            SHOP_ASSISTANT to { keeper(AlKharidShopAssistantLines) },
            ZEKE to { zeke() },
            DOMMIK to { dommik() },
            LOUIE_LEGS to { louieLegs() },
            RANAEL to { ranael() },
            GEM_TRADER to { gemTrader() },
        )
        for ((id, talk) in talks) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = talk() }
                if (trade) openShop(player, id)
            }
        }
        world.shopScreen.onClose(ALIS_WARES) { player ->
            aliJunk(
                player, world.random,
                give = { p, obj -> world.objTypes[obj]?.let { p.inventory.add(it, 1) } },
                message = { p -> world.message(p, AliMorrisaneLines.JUNK) },
            )
        }
    }
}
