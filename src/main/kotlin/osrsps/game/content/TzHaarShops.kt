package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The words of Mor Ul Rek's shopkeepers, the Wiki's Transcript:TzHaar-Hur-Tel, Transcript:TzHaar-Hur-Lek and
 * Transcript:TzHaar-Mej-Roh (read 2026-11-18): one conversation, but for what each says it is. [name] is the player's
 * TzHaar name, [caste] JalYt and the caste ([TzHaarNames]).
 */
object TzHaarShopLines : Lines("tzhaar_shop", "Transcript:TzHaar-Hur-Tel; Transcript:TzHaar-Hur-Lek; Transcript:TzHaar-Mej-Roh") {
    val GREETING = line("greeting", "the shopkeeper's first line, naming the player", "name")
    val TRADE = line("trade", "option 1: the shop opens, with no line from the player")
    val WHAT_CALL = line("what_call", "option 2 (no line said)")
    val FINE = line("fine", "option 3, which the player then says")
    val ARE_YOU_NOT = line("are_you_not", "the shopkeeper's answer, naming the player", "name")
    val WHATS_CASTE = line("whats_caste", "the player then asks", "caste")
    val KET_IS = line("ket_is", "the answer for a JalYt-Ket")
    val MEJ_IS = line("mej_is", "the answer for a JalYt-Mej")
    val XIL_IS = line("xil_is", "the answer for a JalYt-Xil")
    val HUR_IS = line("hur_is", "the answer for a JalYt-Hur")
    val SUPPOSE = line("suppose", "the player: they suppose they are")
    val THEN_YOU = line("then_you", "the reply, naming the caste", "caste")
    val WHAT_ARE_YOU = line("what_are_you", "the menu's option 1, which the player then says")
    val THANKS = line("thanks", "option 2, which the player then says")
    val GUESS_SO = line("guess_so", "option 3, which the player then says")
    val NOT = line("not", "option 4, which the player then says")
    val NO_PROBLEMS = line("no_problems", "the answer to 'I guess so'")
    val CRAZY = line("crazy", "the answer to 'No I'm not!'")
    val I_AM_HUR = line("i_am_hur", "TzHaar-Hur-Tel and -Lek: they are TzHaar-Hur")
    val CASTES_HUR = line("castes_hur", "then: the other three castes")
    val I_AM_MEJ = line("i_am_mej", "TzHaar-Mej-Roh: he is a TzHaar-Mej")
    val OTHER_TYPES = line("other_types", "his menu's option 1, which the player then says")
    val AH_OK = line("ah_ok", "its option 2, which the player then says")
    val CASTES_MEJ = line("castes_mej", "his answer: the other three castes")
}

/**
 * Mor Ul Rek's outer shops (Karamja step K2.2), as the Wiki's shop pages have them (read 2026-11-18): TzHaar-Hur-Tel's
 * Equipment Store (2183), TzHaar-Hur-Lek's Ore and Gem Store (2184) and TzHaar-Mej-Roh's Rune Store (2185), all paid in
 * Tokkul (shops.json), selling at 150% of an item's value and buying at 15% with a change of 2% an item (the stock
 * tables' sellmultiplier 1500, buymultiplier 150, delta 20). Their talk opens the shop or explains the player's TzHaar
 * name. Left out: Karamja gloves' better prices (no Karamja Diary yet), the ironman stocks. Guessed: the chathead faces.
 */
object TzHaarShops {
    const val HUR_TEL = 2183
    const val HUR_LEK = 2184
    const val MEJ_ROH = 2185
    const val TALK_TO = 1

    /** The talk; true when the player asked to trade. [mej] for TzHaar-Mej-Roh, the rest are TzHaar-Hur. */
    internal suspend fun Dialogue.shopkeeper(player: Player, mej: Boolean): Boolean = with(TzHaarShopLines) {
        val name = TzHaarNames.name(player)
        val caste = TzHaarNames.casteTitle(player)
        chatNpc(ChatAnim.NEUTRAL, GREETING, "name" to name)
        when (choice(TRADE, WHAT_CALL, FINE)) {
            1 -> return true
            2 -> {
                chatNpc(ChatAnim.NEUTRAL, ARE_YOU_NOT, "name" to name)
                chatPlayer(ChatAnim.QUIZ, WHATS_CASTE, "caste" to caste)
                chatNpc(ChatAnim.NEUTRAL, when (TzHaarNames.caste(player)) {
                    TzHaarNames.Caste.KET -> KET_IS
                    TzHaarNames.Caste.MEJ -> MEJ_IS
                    TzHaarNames.Caste.XIL -> XIL_IS
                    TzHaarNames.Caste.HUR -> HUR_IS
                })
                chatPlayer(ChatAnim.NEUTRAL, SUPPOSE)
                chatNpc(ChatAnim.NEUTRAL, THEN_YOU, "caste" to caste)
                when (choice(WHAT_ARE_YOU, THANKS, GUESS_SO, NOT)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, WHAT_ARE_YOU)
                        if (!mej) {
                            chatNpc(ChatAnim.NEUTRAL, I_AM_HUR)
                            chatNpc(ChatAnim.NEUTRAL, CASTES_HUR)
                        } else {
                            chatNpc(ChatAnim.NEUTRAL, I_AM_MEJ)
                            if (choice(OTHER_TYPES, AH_OK) == 1) {
                                chatPlayer(ChatAnim.QUIZ, OTHER_TYPES)
                                chatNpc(ChatAnim.NEUTRAL, CASTES_MEJ)
                            } else {
                                chatPlayer(ChatAnim.NEUTRAL, AH_OK)
                            }
                        }
                    }
                    2 -> chatPlayer(ChatAnim.NEUTRAL, THANKS)
                    3 -> {
                        chatPlayer(ChatAnim.NEUTRAL, GUESS_SO)
                        chatNpc(ChatAnim.NEUTRAL, NO_PROBLEMS)
                    }
                    else -> {
                        chatPlayer(ChatAnim.ANGRY, NOT)
                        chatNpc(ChatAnim.NEUTRAL, CRAZY)
                    }
                }
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
        }
        false
    }
}

fun registerTzHaarShops(world: World) {
    with(TzHaarShops) {
        for (keeper in listOf(HUR_TEL, HUR_LEK, MEJ_ROH)) {
            world.handlers.onNpc(keeper, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = shopkeeper(player, mej = keeper == MEJ_ROH) }
                if (trade) world.shopTypes.ofNpc(keeper)?.let { world.shopScreen.open(player, it) }
            }
        }
    }
}
