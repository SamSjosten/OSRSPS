package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Varrock General Store keeper's lines, from the Wiki's Transcript:Shop keeper (Varrock), read 2026-10-30. */
object VarrockShopKeeperLines : GeneralStoreLines("varrock_shop_keeper", "Transcript:Shop keeper (Varrock)", "shop keeper")

/** Aubury's lines, from the Wiki's Transcript:Aubury (Standard dialogue), read 2026-10-30. */
object AuburyLines : Lines("aubury", "Transcript:Aubury") {
    val GREETING = line("greeting", "his first line: buy some runes?")
    val CAPE = line("cape", "option 1: ask about his cape (no line from the player)")
    val CAPE_1 = line("cape_1", "option 1: what skillcapes are")
    val CAPE_2 = line("cape_2", "option 1: what the Cape of Runecrafting does")
    val VIEW = line("view", "after the cape: option 1, the shop opens, with no line from the player")
    val NO_THANKS = line("no_thanks", "after the cape: option 2, which the player then says")
    val YES = line("yes", "option 2: the shop opens, with no line from the player")
    val DECLINE = line("decline", "option 3: it's a rune shop, which the player then says")
    val BYE = line("bye", "his parting line, after either no")
}

/** Thessalia's lines, from the Wiki's Transcript:Thessalia (Standard dialogue), read 2026-10-30. */
object ThessaliaLines : Lines("thessalia", "Transcript:Thessalia") {
    val GREETING = line("greeting", "her first line: buy some clothes?")
    val WHAT_GOT = line("what_got", "option 1, which the player then says")
    val OFFER = line("offer", "option 1: her clothes and her makeovers")
    val MORE = line("more", "after the offer: the makeover, which the player then says")
    val MORE_1 = line("more_1", "the makeover: her first line")
    val MORE_2 = line("more_2", "the makeover: second line")
    val MORE_3 = line("more_3", "the makeover: third line, it's free")
    val MORE_4 = line("more_4", "the makeover: what does the player say?")
    val BUY = line("buy", "the menus' buy option: the shop opens, with no line from the player")
    val NO_THANKS = line("no_thanks", "the menus' no option, which the player then says")
    val RETURN = line("return", "her parting line")
}

/** Asyff's lines, from the Wiki's Transcript:Asyff (Standard dialogue), read 2026-10-30. */
object AsyffLines : Lines("asyff", "Transcript:Asyff") {
    val GREETING = line("greeting", "his first line, on the player's looks")
    val WHAT = line("what", "the player's answer")
    val JUST_SAYING = line("just_saying", "his second line: browse his garments")
    val MATERIALS_TOO = line("materials_too", "his third line: he wants unusual materials")
    val STOCK = line("stock", "option 1: see his stock, which the player then says before the shop opens")
    val LEAVE = line("leave", "option 2: leave the perusing, which the player then says")
    val MATERIALS = line("materials", "option 3: what materials, which the player then says")
    val MATERIALS_1 = line("materials_1", "option 3: colourful feathers")
    val MATERIALS_2 = line("materials_2", "option 3: odd local demand")
    val SEE = line("see", "option 3: the player asks to see the stock, before the shop opens")
}

/** The Tea Seller's lines, from the Wiki's Transcript:Tea Seller, read 2026-10-30. */
object TeaSellerLines : Lines("tea_seller", "Transcript:Tea Seller") {
    val GREETING = line("greeting", "his first line: some refreshment?")
    val YES = line("yes", "option 1, which the player then says before the shop opens")
    val WHAT = line("what", "option 2: what he sells, which the player then says")
    val WARES = line("wares", "option 2: his answer")
    val NO = line("no", "option 3, which the player then says")
    val COME_BACK = line("come_back", "option 3: his answer")
}

/**
 * Varrock's other shops' Talk-to (the Varrock piece's step 5): the General Store's Shop keeper (2815) and Shop assistant
 * (2816), Aubury (aubury_3op, 11435, as every account has done Rune Mysteries; aubury_2op, 11434, talks the same),
 * Thessalia (thessalia_normal, 10477), Asyff (2887) and the Tea Seller (8687). Trade is [registerShops]'s. From the
 * Wiki's transcripts. Guessed: every expression; that the General Store's assistant, who has no transcript, says what
 * the keeper says; that Aubury's two partings are one line. Left out: Aubury's Teleport and its talk option (there is
 * no rune essence mine yet), Thessalia's Makeover and its change top and legwear options (there is no makeover screen
 * yet), her scythe, bunny ears and frog token options, and Asyff's Fur Clothing, bird costume, hunting clothing and
 * priest gown options.
 */
object VarrockStores {
    const val SHOP_KEEPER = 2815
    const val SHOP_ASSISTANT = 2816
    val AUBURY = listOf(11434, 11435)
    const val THESSALIA = 10477
    const val ASYFF = 2887
    const val TEA_SELLER = 8687
    const val TALK_TO = 1

    /** Aubury's talk; true when the player asked to see the shop. */
    internal suspend fun Dialogue.aubury(): Boolean = with(AuburyLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(CAPE, YES, DECLINE)) {
            1 -> {
                chatNpc(ChatAnim.HAPPY, CAPE_1)
                chatNpc(ChatAnim.HAPPY, CAPE_2)
                if (choice(VIEW, NO_THANKS) == 1) return@with true
                chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            }
            2 -> return@with true
            else -> chatPlayer(ChatAnim.NEUTRAL, DECLINE)
        }
        chatNpc(ChatAnim.NEUTRAL, BYE)
        false
    }

    internal suspend fun Dialogue.thessalia(): Boolean = with(ThessaliaLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        var picked = choice(WHAT_GOT, BUY, NO_THANKS)
        if (picked == 1) {
            chatPlayer(ChatAnim.QUIZ, WHAT_GOT)
            chatNpc(ChatAnim.HAPPY, OFFER)
            picked = choice(MORE, BUY, NO_THANKS)
            if (picked == 1) {
                chatPlayer(ChatAnim.QUIZ, MORE)
                for (line in listOf(MORE_1, MORE_2, MORE_3, MORE_4)) chatNpc(ChatAnim.HAPPY, line)
                // The change top and legwear options are left out, so buying and no are the menu's first two.
                picked = choice(BUY, NO_THANKS) + 1
            }
        }
        if (picked == 2) return@with true
        chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        chatNpc(ChatAnim.HAPPY, RETURN)
        false
    }

    internal suspend fun Dialogue.asyff(): Boolean = with(AsyffLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        chatPlayer(ChatAnim.CONFUSED, WHAT)
        chatNpc(ChatAnim.HAPPY, JUST_SAYING)
        chatNpc(ChatAnim.HAPPY, MATERIALS_TOO)
        when (choice(STOCK, LEAVE, MATERIALS)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, STOCK)
                true
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                false
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, MATERIALS)
                chatNpc(ChatAnim.NEUTRAL, MATERIALS_1)
                chatNpc(ChatAnim.CONFUSED, MATERIALS_2)
                chatPlayer(ChatAnim.HAPPY, SEE)
                true
            }
        }
    }

    internal suspend fun Dialogue.teaSeller(): Boolean = with(TeaSellerLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(YES, WHAT, NO)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, YES)
                true
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT)
                chatNpc(ChatAnim.HAPPY, WARES)
                false
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, NO)
                chatNpc(ChatAnim.HAPPY, COME_BACK)
                false
            }
        }
    }
}

/** Varrock's other shop keepers' Talk-to. Trade is [registerShops]'s. */
fun registerVarrockStores(world: World) {
    with(VarrockStores) {
        val talks: List<Pair<Int, suspend Dialogue.() -> Boolean>> = listOf(
            SHOP_KEEPER to { with(LumbridgeShopkeepers) { keeper(VarrockShopKeeperLines) } },
            SHOP_ASSISTANT to { with(LumbridgeShopkeepers) { keeper(VarrockShopKeeperLines) } },
            THESSALIA to { thessalia() },
            ASYFF to { asyff() },
            TEA_SELLER to { teaSeller() },
            AUBURY[0] to { aubury() },
            AUBURY[1] to { aubury() },
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
