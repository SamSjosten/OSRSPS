package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Varrock Swordshop keeper's lines, from the Wiki's Transcript:Shop keeper (Varrock Swordshop), read 2026-10-30. */
object VarrockSwordshopLines : Lines("varrock_swordshop", "Transcript:Shop keeper (Varrock Swordshop)") {
    val GREETING = line("greeting", "the keeper's first line: a look at the swords?")
    val YES = line("yes", "option 1: the shop opens, with no line from the player")
    val NO = line("no", "option 2: the player is fine for swords, which they then say")
    val BYE = line("bye", "option 2: the keeper's parting line")
}

/** Horvik's lines, from the Wiki's Transcript:Horvik (Standard dialogue), read 2026-10-30. */
object HorvikLines : Lines("horvik", "Transcript:Horvik") {
    val GREETING = line("greeting", "his first line")
    val BROWSING = line("browsing", "option 1: just browsing, which the player then says")
    val COME_BACK = line("come_back", "option 1: his answer")
    val TRADE = line("trade", "option 2: the shop opens, with no line from the player")
}

/** Lowe's lines, from the Wiki's Transcript:Lowe (Standard dialogue), read 2026-10-30. */
object LoweLines : Lines("lowe", "Transcript:Lowe") {
    val GREETING = line("greeting", "his first line: see his wares?")
    val YES = line("yes", "option 1: the shop opens, with no line from the player")
    val NO = line("no", "option 2, which the player then says")
    val SUIT_YOURSELF = line("suit_yourself", "option 2: his answer")
}

/** Zaff's lines, from the Wiki's Transcript:Zaff (Standard dialogue), read 2026-10-30. */
object ZaffLines : Lines("zaff", "Transcript:Zaff") {
    val GREETING = line("greeting", "his first line: buy or sell staffs?")
    val YES = line("yes", "option 1: the shop opens, with no line from the player")
    val NO = line("no", "option 2, which the player then says")
    val PUN = line("pun", "option 2: his answer")
    val JOKE = line("joke", "option 2: the player's last line")
}

/**
 * Varrock's weapon and armour shops' Talk-to (the Varrock piece's step 4): the Swordshop's Shop keeper (2884) and Shop
 * assistant (2885), Horvik (2882), Lowe (2883) and Zaff (2880). Trade is [registerShops]'s. From the Wiki's
 * transcripts. Guessed: every chathead expression; that the Swordshop's assistant, who has no transcript, says what the
 * keeper says. Left out: Horvik's corrupted platebody, Lowe's and Zaff's combat path voucher, treasure trails, and
 * Zaff's Varrock diary battlestaves, What Lies Below ring and Bryophyta's essence options.
 */
object VarrockShopkeepers {
    const val SWORDSHOP_KEEPER = 2884
    const val SWORDSHOP_ASSISTANT = 2885
    const val HORVIK = 2882
    const val LOWE = 2883
    const val ZAFF = 2880
    const val TALK_TO = 1

    /** The Swordshop's talk; true when the player asked to see the swords. */
    internal suspend fun Dialogue.swordshop(): Boolean = with(VarrockSwordshopLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (choice(YES, NO) == 1) return true
        chatPlayer(ChatAnim.NEUTRAL, NO)
        chatNpc(ChatAnim.NEUTRAL, BYE)
        false
    }

    internal suspend fun Dialogue.horvik(): Boolean = with(HorvikLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (choice(BROWSING, TRADE) == 2) return true
        chatPlayer(ChatAnim.NEUTRAL, BROWSING)
        chatNpc(ChatAnim.HAPPY, COME_BACK)
        false
    }

    internal suspend fun Dialogue.lowe(): Boolean = with(LoweLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (choice(YES, NO) == 1) return true
        chatPlayer(ChatAnim.NEUTRAL, NO)
        chatNpc(ChatAnim.NEUTRAL, SUIT_YOURSELF)
        false
    }

    internal suspend fun Dialogue.zaff(): Boolean = with(ZaffLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (choice(YES, NO) == 1) return true
        chatPlayer(ChatAnim.NEUTRAL, NO)
        chatNpc(ChatAnim.LAUGH, PUN)
        chatPlayer(ChatAnim.LAUGH, JOKE)
        false
    }
}

/** Varrock's shop keepers' Talk-to. Trade is [registerShops]'s. */
fun registerVarrockShopkeepers(world: World) {
    with(VarrockShopkeepers) {
        val talks: List<Pair<Int, suspend Dialogue.() -> Boolean>> = listOf(
            SWORDSHOP_KEEPER to { swordshop() },
            SWORDSHOP_ASSISTANT to { swordshop() },
            HORVIK to { horvik() },
            LOWE to { lowe() },
            ZAFF to { zaff() },
        )
        for ((id, talk) in talks) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = talk() }
                if (trade) world.shopTypes.ofNpc(id)?.let { world.shopScreen.open(player, it) }
            }
        }
    }
}
