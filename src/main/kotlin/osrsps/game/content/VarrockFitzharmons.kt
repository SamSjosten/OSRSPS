package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/** Dimintheis's lines, from the Wiki's Transcript:Dimintheis (after Family Crest), read 2026-11-01. */
object DimintheisLines : Lines("dimintheis", "Transcript:Dimintheis") {
    val QUESTS = line("quests", "the first menu's option 1, which the player then says")
    val OTHER = line("other", "the first menu's option 2")
    val MURMUR = line("murmur", "quests: the box after the player asks, murmuring about rewards")
    val NO_TROUBLE = line("no_trouble", "quests: the family won't trouble the player again")
    val INSIST = line("insist", "quests: the player insists, boasting")
    val CONTENT = line("content", "quests: he needs nothing, content as he is")
    val MONEY = line("money", "quests' menu, option 1, which the player then says")
    val PARDON = line("pardon", "money: he didn't catch that")
    val NOTHING = line("nothing", "money: the player waves it off")
    val HELP = line("help", "quests' menu, option 2, which the player then says")
    val SONS = line("sons", "help: ask his sons for work")
    val STRAIGHT_THERE = line("straight_there", "help: the player will go at once")
    val REWARDS = line("rewards", "help: the closing box about rewards to come")
    val GREETING = line("greeting", "other: his greeting after Defender of Varrock")
    val GAUNTLETS = line("gauntlets", "other: the player asks for gauntlets")
    val RETURNED_CHAOS = line("returned_chaos", "other, chaos gauntlets lost: they were returned to him; does the player want them?")
    val RETURNED_COOKING = line("returned_cooking", "other, cooking gauntlets lost: as returned_steel")
    val RETURNED_GOLDSMITH = line("returned_goldsmith", "other, goldsmith gauntlets lost: as returned_steel")
    val RETURNED_STEEL = line("returned_steel", "other, the plain family gauntlets lost: as returned_chaos")
    val YES_PLEASE = line("yes_please", "the returned gauntlets' menu, option 1, which the player then says")
    val HANDS_OVER = line("hands_over", "yes please: the box as he hands them over")
    val HERE_YOU_GO = line("here_you_go", "yes please: his line before the box")
    val NOT_NOW = line("not_now", "the returned gauntlets' menu, option 2, which the player then says")
    val ANY_TIME = line("any_time", "not now: come back any time")
    val HAVE_THEM = line("have_them", "other, gauntlets owned: he hasn't got them, the player has")
}

/** Johnathon's lines, from the Wiki's Transcript:Johnathon (after Family Crest), read 2026-11-01. */
object JohnathonLines : Lines("johnathon", "Transcript:Johnathon") {
    val HERO = line("hero", "his first line: the family sees the player as a hero")
    val AGREE = line("agree", "the player agrees")
    val ANYTHING = line("anything", "can he help with anything?")
    val CHANGE = line("change", "the menu's option 1, which the player then says")
    val PRICE = line("price", "change: he will, for {price} coins", "price")
    val ACCEPT = line("accept", "the price menu's option 1, which the player then says")
    val CHANTS = line("chants", "accept: the box as he chants over the gauntlets")
    val DECLINE = line("decline", "the price menu's option 2, which the player then says")
    val NO_THANKS = line("no_thanks", "the menu's option 2, which the player then says")
    val NO_GAUNTLETS = line("no_gauntlets", "change, without family gauntlets to change (guessed: the Wiki has no line for it)")
    val NO_COINS = line("no_coins", "accept, without the coins (guessed: the Wiki has no line for it)")
}

/** Old Man Yarlo's lines, from the Wiki's Transcript:Old Man Yarlo (after Daddy's Home), read 2026-11-01. */
object YarloLines : Lines("yarlo", "Transcript:Old Man Yarlo") {
    val HAPPIER = line("happier", "his first line: happier in his home")
    val GOOD_NEWS = line("good_news", "the menu's option 1, which the player then says")
    val CONSTRUCTION = line("construction", "the menu's option 2, which the player then says")
    val THANKS = line("thanks", "construction: the player's thanks")
}

/**
 * The Fitzharmons and Old Man Yarlo (the Varrock piece's step 8b), talking as once every quest is done.
 * Dimintheis (4984) in his house east of the palace hands back a lost pair of family gauntlets for free; his son
 * Johnathon (multi-NPC 4988, spawned as `johnathon_fitzharmon_2ops` 5445, the type varp 148 shows once Family Crest is
 * done) upstairs in the Jolly Boar Inn turns a plain pair into chaos gauntlets for free and another son's pair for 25,000 coins, through his talk or his
 * Gauntlets option; Old Man Yarlo (10425) south of the square.
 *
 * Johnathon enchants a plain pair for free, skipping his price (Wiki: only another son's enchantment costs), and
 * Dimintheis hands back the pair the player last had enchanted ([lastPair]).
 *
 * Guessed: every expression; that a plain pair goes straight to his chant with no menu; which items count as owned (any of the four pairs in the backpack,
 * worn or banked); that Johnathon changes the pair worn before one carried; his two lines without gauntlets or coins.
 * Left out: Yarlo's Construction cutscene (the player only thanks him), and Avan's and Caleb's own changes.
 */
object VarrockFitzharmons {
    const val DIMINTHEIS = 4984
    const val JOHNATHON = 5445
    const val YARLO = 10425
    const val TALK_TO = 1
    const val GAUNTLETS_OP = 3

    const val COOKING_GAUNTLETS = 775
    const val GOLDSMITH_GAUNTLETS = 776
    const val CHAOS_GAUNTLETS = 777
    const val FAMILY_GAUNTLETS = 778
    val ALL_GAUNTLETS = listOf(COOKING_GAUNTLETS, GOLDSMITH_GAUNTLETS, CHAOS_GAUNTLETS, FAMILY_GAUNTLETS)

    /** The pairs Johnathon can turn into his, every pair but his own. */
    val CHANGEABLE = listOf(COOKING_GAUNTLETS, GOLDSMITH_GAUNTLETS, FAMILY_GAUNTLETS)
    const val CHANGE_PRICE = 25_000

    /**
     * Where the pair the player last had enchanted is kept ([osrsps.game.Charges], saved with the account), as its obj
     * id. Guessed: live keeps it in a var the cache doesn't name.
     */
    const val LAST_PAIR = "family_gauntlets"

    fun ownsGauntlets(player: Player): Boolean = ALL_GAUNTLETS.any {
        player.inventory.count(it) > 0 || player.equipment.count(it) > 0 || player.bank.count(it) > 0
    }

    internal suspend fun Dialogue.dimintheis(give: (Int) -> Unit) = with(DimintheisLines) {
        if (choice(QUESTS, OTHER) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, QUESTS)
            mesbox(MURMUR)
            chatNpc(ChatAnim.HAPPY, NO_TROUBLE)
            chatPlayer(ChatAnim.NEUTRAL, INSIST)
            chatNpc(ChatAnim.NEUTRAL, CONTENT)
            if (choice(MONEY, HELP) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, MONEY)
                chatNpc(ChatAnim.CONFUSED, PARDON)
                chatPlayer(ChatAnim.NEUTRAL, NOTHING)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, HELP)
                chatNpc(ChatAnim.NEUTRAL, SONS)
                chatPlayer(ChatAnim.HAPPY, STRAIGHT_THERE)
                mesbox(REWARDS)
            }
            return@with
        }
        chatNpc(ChatAnim.HAPPY, GREETING)
        chatPlayer(ChatAnim.QUIZ, GAUNTLETS)
        if (ownsGauntlets(player)) {
            chatNpc(ChatAnim.NEUTRAL, HAVE_THEM)
            return@with
        }
        val pair = lastPair(player)
        chatNpc(ChatAnim.NEUTRAL, returned(pair))
        if (choice(YES_PLEASE, NOT_NOW) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
            chatNpc(ChatAnim.HAPPY, HERE_YOU_GO)
            // Checked again: the player may have got a pair while the boxes were open.
            if (!ownsGauntlets(player)) give(pair)
            objbox(pair, HANDS_OVER)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NOT_NOW)
            chatNpc(ChatAnim.NEUTRAL, ANY_TIME)
        }
    }

    internal suspend fun Dialogue.johnathon() = with(JohnathonLines) {
        chatNpc(ChatAnim.HAPPY, HERO)
        chatPlayer(ChatAnim.HAPPY, AGREE)
        chatNpc(ChatAnim.QUIZ, ANYTHING)
        if (choice(CHANGE, NO_THANKS) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, CHANGE)
        johnathonChange()
    }

    /** His price, and the change if the player takes it. Also his Gauntlets option. */
    internal suspend fun Dialogue.johnathonChange() = with(JohnathonLines) {
        val first = changeable(player)
        if (first == null) {
            chatNpc(ChatAnim.NEUTRAL, NO_GAUNTLETS)
            return@with
        }
        // A plain pair is enchanted for free (Wiki); only another son's enchantment costs his price.
        if (first.id != FAMILY_GAUNTLETS) {
            chatNpc(ChatAnim.NEUTRAL, PRICE, "price" to "%,d".format(CHANGE_PRICE))
            if (choice(ACCEPT, DECLINE) == 2) {
                chatPlayer(ChatAnim.NEUTRAL, DECLINE)
                return@with
            }
            chatPlayer(ChatAnim.HAPPY, ACCEPT)
        }
        // The pair is found again after the menu, and paid for and changed with nothing suspending in between.
        val pair = changeable(player)
        if (pair == null) {
            chatNpc(ChatAnim.NEUTRAL, NO_GAUNTLETS)
            return@with
        }
        val price = if (pair.id == FAMILY_GAUNTLETS) 0 else CHANGE_PRICE
        if (price > 0 && (player.inventory.count(ShopScreen.COINS) < price || !AlKharidTraders.pay(player, price))) {
            chatNpc(ChatAnim.NEUTRAL, NO_COINS)
            return@with
        }
        pair.change()
        player.charges[LAST_PAIR] = CHAOS_GAUNTLETS
        objbox(CHAOS_GAUNTLETS, CHANTS)
    }

    /** The player's pair Johnathon would change, and what changes it. */
    private class Changeable(val id: Int, val change: () -> Unit)

    /** The player's pair to change, worn before carried, or null without one. */
    private fun changeable(player: Player): Changeable? {
        val worn = player.equipment[WearPos.HANDS]
        if (worn != null && worn.id in CHANGEABLE) {
            return Changeable(worn.id) { player.equipment[WearPos.HANDS] = InvObj(CHAOS_GAUNTLETS, 1) }
        }
        val (slot, obj) = player.inventory.contents().firstOrNull { it.second.id in CHANGEABLE } ?: return null
        return Changeable(obj.id) { player.inventory[slot] = InvObj(CHAOS_GAUNTLETS, 1) }
    }

    /** Dimintheis's line naming the returned [pair]. */
    private fun returned(pair: Int) = when (pair) {
        CHAOS_GAUNTLETS -> DimintheisLines.RETURNED_CHAOS
        COOKING_GAUNTLETS -> DimintheisLines.RETURNED_COOKING
        GOLDSMITH_GAUNTLETS -> DimintheisLines.RETURNED_GOLDSMITH
        else -> DimintheisLines.RETURNED_STEEL
    }

    /** The pair the player last had enchanted, which Dimintheis hands back; the plain pair before any. */
    fun lastPair(player: Player): Int = player.charges[LAST_PAIR].takeIf { it in ALL_GAUNTLETS } ?: FAMILY_GAUNTLETS

    internal suspend fun Dialogue.yarlo() = with(YarloLines) {
        chatNpc(ChatAnim.HAPPY, HAPPIER)
        if (choice(GOOD_NEWS, CONSTRUCTION) == 1) {
            chatPlayer(ChatAnim.HAPPY, GOOD_NEWS)
        } else {
            chatPlayer(ChatAnim.QUIZ, CONSTRUCTION)
            chatPlayer(ChatAnim.HAPPY, THANKS)
        }
    }
}

/** Dimintheis's, Johnathon's and Old Man Yarlo's options. */
fun registerVarrockFitzharmons(world: World) {
    with(VarrockFitzharmons) {
        val give: Dialogue.(Int) -> Unit = { obj -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } }
        world.handlers.onNpc(DIMINTHEIS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { dimintheis { give(it) } } }
        world.handlers.onNpc(JOHNATHON, TALK_TO) { op -> world.dialogues.start(this, op.npc) { johnathon() } }
        world.handlers.onNpc(JOHNATHON, GAUNTLETS_OP) { op -> world.dialogues.start(this, op.npc) { johnathonChange() } }
        world.handlers.onNpc(YARLO, TALK_TO) { op -> world.dialogues.start(this, op.npc) { yarlo() } }
    }
}
