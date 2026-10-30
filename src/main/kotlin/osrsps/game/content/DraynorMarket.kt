package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Fortunato's lines once Rag and Bone Man I is done (the Wiki's Transcript:Fortunato, read 2026-10-30). */
object FortunatoLines : Lines("fortunato", "Transcript:Fortunato") {
    val HELP = line("help", "After Rag and Bone Man I: Fortunato's first line")
    val BACK = line("back", "his second line")
    val PLEASE = line("please", "his third line")
    val JUST_LOOKING = line("just_looking", "the player's answer")
    val WONDERFUL = line("wonderful", "Fortunato's reply")
    val STOCK = line("stock", "his next line")
    val SOMETHING = line("something", "his last line before the menu")
    val YES = line("yes", "option 1, which the player then says; the shop opens")
    val NO = line("no", "option 2, which the player then says")
    val WELL = line("well", "option 2: Fortunato's answer")
    val THANKS = line("thanks", "option 2: the player's last line")
}

/** Olivia's lines (the Wiki's Transcript:Olivia, read 2026-10-30). */
object OliviaLines : Lines("olivia", "Transcript:Olivia") {
    val SEEDS = line("seeds", "Olivia's first line")
    val YES = line("yes", "option 1; the shop opens, with no line from the player")
    val NO = line("no", "option 2")
    val NO_THANKS = line("no_thanks", "option 2: what the player says")
    val WHERE = line("where", "option 3, which the player then says")
    val HONEST = line("honest", "option 3: Olivia's answer")
}

/** Diango's lines with no holiday event on (the Wiki's Transcript:Diango, read 2026-10-30). */
object DiangoLines : Lines("diango", "Transcript:Diango") {
    val HOWDY = line("howdy", "Diango's first line")
    val PLATES = line("plates", "option 1, which the player then says")
    val THIEVES = line("thieves", "option 1: Diango's first answer")
    val PLATES_2 = line("plates_2", "option 1: his second, before the shop opens")
    val HOLIDAY = line("holiday", "option 2, which the player then says")
    val MISSING = line("missing", "option 2: Diango's answer, before the holiday item retrieval would open")
    val SELLING = line("selling", "option 3, which the player then says; the shop opens")
    val NO_THANKS = line("no_thanks", "option 4, which the player then says")
}

/**
 * Draynor's market traders (Draynor piece step 3): Fortunato (`rag_wine_merchant` 1260) and his Wine Shop, Olivia
 * (`seed_merchant` 500) and the Draynor Seed Market, Diango (`aprilfoolshorsesalesman` 8693) and his Toy Store. The shops
 * are in shops.json and open on Trade ([registerShops]) and from the talk. Fortunato talks as he does once Rag and Bone
 * Man I is done, which every account counts as.
 *
 * Left out: Diango's Holiday Item Retrieval (no account has holiday items to reclaim, so his option 2 ends after his
 * answer, and Holiday-items does nothing yet) and Redeem-code. Olivia's refusal for 30 minutes after a theft from her
 * stalls comes with thieving. Guessed: every chathead expression, and Diango's spinning-plate lines leading into the
 * shop as the Wiki's transcript has it.
 */
object DraynorMarket {
    const val FORTUNATO = 1260
    const val OLIVIA = 500
    const val DIANGO = 8693
    const val TALK_TO = 1

    internal suspend fun Dialogue.fortunato(): Boolean = with(FortunatoLines) {
        chatNpc(ChatAnim.HAPPY, HELP)
        chatNpc(ChatAnim.HAPPY, BACK)
        chatNpc(ChatAnim.NEUTRAL, PLEASE)
        chatPlayer(ChatAnim.NEUTRAL, JUST_LOOKING)
        chatNpc(ChatAnim.HAPPY, WONDERFUL)
        chatNpc(ChatAnim.NEUTRAL, STOCK)
        chatNpc(ChatAnim.QUIZ, SOMETHING)
        if (choice(YES, NO) == 1) {
            chatPlayer(ChatAnim.QUIZ, YES)
            return@with true
        }
        chatPlayer(ChatAnim.NEUTRAL, NO)
        chatNpc(ChatAnim.NEUTRAL, WELL)
        chatPlayer(ChatAnim.NEUTRAL, THANKS)
        false
    }

    internal suspend fun Dialogue.olivia(): Boolean = with(OliviaLines) {
        chatNpc(ChatAnim.QUIZ, SEEDS)
        when (choice(YES, NO, WHERE)) {
            1 -> return@with true
            2 -> chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            else -> {
                chatPlayer(ChatAnim.QUIZ, WHERE)
                chatNpc(ChatAnim.NEUTRAL, HONEST)
            }
        }
        false
    }

    internal suspend fun Dialogue.diango(): Boolean = with(DiangoLines) {
        chatNpc(ChatAnim.HAPPY, HOWDY)
        when (choice(PLATES, HOLIDAY, SELLING, NO_THANKS)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, PLATES)
                chatNpc(ChatAnim.HAPPY, THIEVES)
                chatNpc(ChatAnim.HAPPY, PLATES_2)
                true
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, HOLIDAY)
                chatNpc(ChatAnim.NEUTRAL, MISSING)
                false
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, SELLING)
                true
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                false
            }
        }
    }
}

/** The market traders' Talk-to. Trade is [registerShops]'s. */
fun registerDraynorMarket(world: World) {
    fun openShop(player: Player, npc: Int) {
        world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
    }
    val talks: List<Pair<Int, suspend Dialogue.() -> Boolean>> = listOf(
        DraynorMarket.FORTUNATO to { with(DraynorMarket) { fortunato() } },
        DraynorMarket.OLIVIA to { with(DraynorMarket) { olivia() } },
        DraynorMarket.DIANGO to { with(DraynorMarket) { diango() } },
    )
    for ((id, talk) in talks) {
        world.handlers.onNpc(id, DraynorMarket.TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = talk() }
            if (trade) openShop(player, id)
        }
    }
}
