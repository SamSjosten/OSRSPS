package osrsps.game.skill.construction

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Ned
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The estate agents' lines; the words are in the dialogue file, from the Wiki's Transcript:Estate agent. */
object EstateAgentLines : Lines("estate_agent", "Transcript:Estate agent") {
    val GREETING = line("greeting", "Both menus: his greeting")
    val HOW_GET = line("how_get", "Before buying a house: How can I get a house?, which the player then says")
    val HOW_GET_REPLY = line("how_get_reply", "How can I get a house?: a starting house in Rimmington for 1000 coins")
    val WANT_BUY = line("want_buy", "How can I get a house?: do you want to buy a starter house?")
    val YES_PLEASE = line("yes_please", "Buying: Yes please!, which the player then says")
    val NO_COINS = line("no_coins", "Buying without 1,000 coins: the player's line")
    val NO_COINS_REPLY = line("no_coins_reply", "Buying without 1,000 coins: come back when you have it")
    val SOLD = line("sold", "Bought: go through the Rimmington house portal")
    val SOLD_BOOK = line("sold_book", "Bought: this book will help you, as he hands over a Construction guide")
    val NO_THANKS = line("no_thanks", "Buying: No thanks, which the player then says")
    val NO_THANKS_REPLY = line("no_thanks_reply", "Buying, no thanks: enjoy your player-owned cardboard box")
    val ABOUT = line("about", "Both menus: Tell me about houses!, which the player then says (standard dialogue only)")
    val ABOUT_1 = line("about_1", "Tell me about houses: the wizards' experiments folding space")
    val ABOUT_2 = line("about_2", "Tell me about houses: folded-space regions of plots")
    val ABOUT_PLAYER = line("about_player", "Tell me about houses: the player's 'so that's how everyone can have a house'")
    val ABOUT_3 = line("about_3", "Tell me about houses: the wizards hired him to sell them")
    val ABOUT_4 = line("about_4", "Tell me about houses: others help you furnish it")
    val ABOUT_5 = line("about_5", "Tell me about houses: start by buying planks from a sawmill operator")
    val GUIDE = line("guide", "Standard dialogue: Could I have a Construction guide book?, which the player then says")
    val GUIDE_HAS = line("guide_has", "A guide book with one in the inventory: you've already got one")
    val GUIDE_NO_SPACE = line("guide_no_space", "A guide book with a full inventory: free up some space")
    val GUIDE_GIVEN = line("guide_given", "A guide book: certainly")
    val CAPE = line("cape", "Standard dialogue under 99 Construction: Tell me about that skillcape you're wearing., which the player then says")
    val CAPE_1 = line("cape_1", "The skillcape: only masters, his life building houses")
    val CAPE_2 = line("cape_2", "The skillcape: come back when you can build a demonic throne")
    val CAPE_3 = line("cape_3", "The skillcape: its teleports to the house portals")
}

/**
 * The estate agent (`poh_estate_agent` 3097) in Varrock: Talk-to sells the starter house in Rimmington for 1,000
 * coins, with a Construction guide (`poh_guide_book` 8463), and gives another guide later. From the Wiki's
 * Transcript:Estate agent (read 2026-11-18). GUESSED: every chathead expression; the guide dropped at the player's
 * feet when the backpack is full after buying (the transcript gives no line for it); the coins counted from the
 * backpack only ("I haven't got 1,000 coins on me"). Left out until step CON11: his Relocate and Redecorate, the
 * standard menu's moving and redecorating, and selling the Construction cape; until then the standard menu offers the
 * guide book, houses and the skillcape talk.
 */
object EstateAgent {
    const val ID = 3097
    const val TALK_TO = 1
    const val COINS = 995
    const val HOUSE_PRICE = 1_000
    const val GUIDE_BOOK = 8463
    const val MAX_LEVEL = 99

    internal suspend fun Dialogue.talk(houses: Houses, give: (Player, Int) -> Unit) = with(EstateAgentLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (player.house == null) {
            when (choice(HOW_GET, ABOUT)) {
                1 -> buy(houses, give)
                else -> about(playerAsks = false)
            }
            return@with
        }
        val cape = player.skills.baseLevel(Skill.CONSTRUCTION) < MAX_LEVEL
        val pick = if (cape) choice(GUIDE, ABOUT, CAPE) else choice(GUIDE, ABOUT)
        when (pick) {
            1 -> guide(give)
            2 -> about(playerAsks = true)
            else -> {
                chatPlayer(ChatAnim.QUIZ, CAPE)
                chatNpc(ChatAnim.NEUTRAL, CAPE_1)
                chatNpc(ChatAnim.NEUTRAL, CAPE_2)
                chatNpc(ChatAnim.HAPPY, CAPE_3)
            }
        }
    }

    private suspend fun Dialogue.buy(houses: Houses, give: (Player, Int) -> Unit) = with(EstateAgentLines) {
        chatPlayer(ChatAnim.QUIZ, HOW_GET)
        chatNpc(ChatAnim.HAPPY, HOW_GET_REPLY)
        chatNpc(ChatAnim.QUIZ, WANT_BUY)
        if (choice(YES_PLEASE, NO_THANKS) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            chatNpc(ChatAnim.LAUGH, NO_THANKS_REPLY)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
        // Checked again after the dialogue's waits: the coins may have gone, or a house come, since the menu.
        if (player.house != null) return@with
        if (player.inventory.count(COINS) < HOUSE_PRICE) {
            chatPlayer(ChatAnim.SAD, NO_COINS)
            chatNpc(ChatAnim.NEUTRAL, NO_COINS_REPLY)
            return@with
        }
        Ned.take(player.inventory, COINS, HOUSE_PRICE)
        houses.sell(player)
        chatNpc(ChatAnim.HAPPY, SOLD)
        chatNpc(ChatAnim.HAPPY, SOLD_BOOK)
        give(player, GUIDE_BOOK)
    }

    private suspend fun Dialogue.guide(give: (Player, Int) -> Unit) = with(EstateAgentLines) {
        chatPlayer(ChatAnim.QUIZ, GUIDE)
        when {
            player.inventory.count(GUIDE_BOOK) > 0 -> chatNpc(ChatAnim.NEUTRAL, GUIDE_HAS)
            player.inventory.freeSlots == 0 -> chatNpc(ChatAnim.NEUTRAL, GUIDE_NO_SPACE)
            else -> {
                chatNpc(ChatAnim.HAPPY, GUIDE_GIVEN)
                give(player, GUIDE_BOOK)
            }
        }
    }

    private suspend fun Dialogue.about(playerAsks: Boolean) = with(EstateAgentLines) {
        // The transcript's first version, before a house is bought, has no line for the player asking.
        if (playerAsks) chatPlayer(ChatAnim.QUIZ, ABOUT)
        chatNpc(ChatAnim.NEUTRAL, ABOUT_1)
        chatNpc(ChatAnim.NEUTRAL, ABOUT_2)
        chatPlayer(ChatAnim.HAPPY, ABOUT_PLAYER)
        chatNpc(ChatAnim.NEUTRAL, ABOUT_3)
        chatNpc(ChatAnim.NEUTRAL, ABOUT_4)
        chatNpc(ChatAnim.NEUTRAL, ABOUT_5)
    }

    fun register(world: World, houses: Houses) {
        val give: (Player, Int) -> Unit = { player, obj -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } }
        world.handlers.onNpc(ID, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { talk(houses, give) }
        }
    }
}
