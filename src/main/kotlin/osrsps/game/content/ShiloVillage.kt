package osrsps.game.content

import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility

/** Mosol Rei's words once Shilo Village is done, the Wiki's Transcript:Mosol Rei (read 2026-11-19). */
object MosolReiLines : Lines("mosol_rei", "Transcript:Mosol Rei") {
    val GREETINGS = line("greetings", "the player's greeting, after Shilo Village")
    val THREAT_REMOVED = line("threat_removed", "his answer: Rashiliyia's threat is gone, some undead still about")
    val SHOW_THE_WAY = line("show_the_way", "his offer to show the player into the village")
    val ENTER_TITLE = line("enter_title", "the menu's title")
    val YES = line("yes", "option 1 (no line from the player)")
    val OTHER_TIME = line("other_time", "option 2 (no line from the player)")
    val LEADS = line("leads", "option 1's message box")
    val LEAVES = line("leaves", "option 1's game message, once inside (and Follow's)")
    val DECIDE_NOT = line("decide_not", "option 2's game message")
}

/** The broken cart's words, the Wiki's Transcript:Broken cart (Shilo Village) (read 2026-11-19). */
object ShiloCartLines : Lines("shilo_cart", "Transcript:Broken cart (Shilo Village)") {
    val FROM_EAST = line("from_east", "Climb over from the east side (outside the village)")
    val FROM_WEST_1 = line("from_west_1", "Climb over from the west side, the first message")
    val FROM_WEST_2 = line("from_west_2", "Climb over from the west side, the second message")
}

/** Obli's words, the Wiki's Transcript:Obli (read 2026-11-19). */
object ObliLines : Lines("obli", "Transcript:Obli") {
    val WELCOME = line("welcome", "his greeting")
    val YES_PLEASE = line("yes_please", "option 1, which the player then says (the shop opens)")
    val NO_THANKS = line("no_thanks", "option 2, which the player then says")
}

/** Fernahei's words, the Wiki's Transcript:Fernahei (read 2026-11-19). */
object FernaheiLines : Lines("fernahei", "Transcript:Fernahei") {
    val WELCOME = line("welcome", "his greeting")
    val YES_PLEASE = line("yes_please", "option 1, which the player then says (the shop opens)")
    val NO_THANKS = line("no_thanks", "option 2, which the player then says")
    val THATS_FINE = line("thats_fine", "option 2: his answer")
}

/**
 * Shilo Village (Karamja step K4.3), open as after the Shilo Village quest, as every quest is here (OSRS Wiki: Shilo
 * Village (location), Mosol Rei, Broken cart (Shilo Village), Obli, Fernahei, Shilo Village bank, read 2026-11-19):
 *
 * - **Mosol Rei** (`mosol_rei_multi` 8695, shown as `mosol_rei_complete` 8696) outside the gate: Follow takes the player
 *   into the village; Talk-to offers the same.
 * - **The broken cart** (`shilo_brokencart` 2216, shown as `shilo_brokencart_postquest`) is climbed over either way, with
 *   the Wiki's messages for each side.
 * - **The metal and wooden gates** (2259/2260 and 2261/2262) have no open types in the cache: Open takes the player
 *   through to the other side.
 * - **The bankers** (`shilobanker` 3093) are [Bankers.STANDARD]'s; the bank deposit box is [registerBanking]'s.
 * - **Obli** (5362) and **Fernahei** (5363) talk and run their shops (shops.json).
 * - **The undead ones** at the gate fight (npc-combat.json); the fishing spots are [osrsps.game.skill.Fishing]'s; the
 *   stepping stone over the river is an Agility shortcut (skills/agility.json); the gem rocks are Mining's.
 *
 * Guessed: where Mosol Rei leaves the player (just inside the wooden gate); the climb's animation and timing (as the
 * Agility fences'); the gates' walk through (one tick, no animation, no message); the chathead faces. Left out: the
 * pre-quest cart and Mosol Rei (no quests), the cart's warning toggle, the jungle foresters (Legends' Quest only).
 */
object ShiloVillage {
    const val MOSOL_REI = 8696
    const val FOLLOW = 1
    const val MOSOL_TALK_TO = 3
    const val TALK_TO = 1

    const val BROKEN_CART = 2216
    const val CLIMB_OVER = 2

    /** The cart's tiles: 3 by 3 from 2877, 2951. */
    const val CART_WEST_X = 2876
    const val CART_EAST_X = 2880
    const val CART_SOUTH_Z = 2951
    const val CART_NORTH_Z = 2953

    const val OPEN = 1

    /** The metal gate's wall on the west edge of x 2875, the wooden gate's on the east edge of 2867. */
    val METAL_GATE = listOf(2259, 2260)
    const val METAL_GATE_EAST_X = 2875
    val WOODEN_GATE = listOf(2261, 2262)
    const val WOODEN_GATE_EAST_X = 2868

    /** Where Mosol Rei leaves the player: just inside the wooden gate (guessed). */
    const val INSIDE_X = 2866
    const val INSIDE_Z = 2952

    const val OBLI = 5362
    const val FERNAHEI = 5363

    /** Climbing over: human_jump_hurdle, as the Agility fences' (guessed). */
    const val CLIMB_ANIM = 1603
    const val CLIMB_TICKS = 2

    internal suspend fun Dialogue.mosolRei(): Boolean = with(MosolReiLines) {
        chatPlayer(ChatAnim.HAPPY, GREETINGS)
        chatNpc(ChatAnim.HAPPY, THREAT_REMOVED)
        chatNpc(ChatAnim.QUIZ, SHOW_THE_WAY)
        if (choice(YES, OTHER_TIME, title = text(ENTER_TITLE)) == 1) {
            mesbox(LEADS)
            true
        } else {
            false
        }
    }

    internal suspend fun Dialogue.obli(): Boolean = with(ObliLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        if (choice(YES_PLEASE, NO_THANKS) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
            true
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            false
        }
    }

    internal suspend fun Dialogue.fernahei(): Boolean = with(FernaheiLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        if (choice(YES_PLEASE, NO_THANKS) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
            true
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            chatNpc(ChatAnim.HAPPY, THATS_FINE)
            false
        }
    }

    /** Mosol Rei shows the player in. */
    internal fun ScriptContext.enter(world: World) {
        world.teleport(player, INSIDE_X, INSIDE_Z, 0)
        world.message(player, MosolReiLines.LEAVES)
    }

    /** Over the cart to its other side, from either side. */
    internal suspend fun ScriptContext.climbCart(world: World) {
        val fromEast = player.x >= CART_EAST_X
        val toX = if (fromEast) CART_WEST_X else CART_EAST_X
        val toZ = player.z.coerceIn(CART_SOUTH_Z, CART_NORTH_Z)
        if (fromEast) {
            world.message(player, ShiloCartLines.FROM_EAST)
        } else {
            world.message(player, ShiloCartLines.FROM_WEST_1)
        }
        slide(world, toX, toZ, CLIMB_ANIM, CLIMB_TICKS)
        if (!fromEast) world.message(player, ShiloCartLines.FROM_WEST_2)
    }

    /** Through a gate whose wall runs north to south, with [eastX] the first tile east of it. */
    internal suspend fun ScriptContext.passGate(world: World, eastX: Int) {
        val toX = if (player.x >= eastX) eastX - 1 else eastX
        slide(world, toX, player.z, -1, 1)
    }

    private suspend fun ScriptContext.slide(world: World, toX: Int, toZ: Int, anim: Int, ticks: Int) {
        val angle = Agility.angle(toX - player.x, toZ - player.z)
        player.effects.exactMove(PlayerEffects.ExactMove(player.x, player.z, toX, toZ, 0, ticks * 30, angle))
        if (anim >= 0) player.anim(anim)
        delay(ticks)
        world.teleport(player, toX, toZ, player.level)
    }
}

fun registerShiloVillage(world: World) {
    with(ShiloVillage) {
        world.handlers.onNpc(MOSOL_REI, FOLLOW) { enter(world) }
        world.handlers.onNpc(MOSOL_REI, MOSOL_TALK_TO) { op ->
            var enter = false
            world.dialogues.start(this, op.npc) { enter = mosolRei() }
            if (enter) enter(world) else world.message(player, MosolReiLines.DECIDE_NOT)
        }
        world.handlers.onLoc(BROKEN_CART, CLIMB_OVER) { climbCart(world) }
        for (gate in METAL_GATE) world.handlers.onLoc(gate, OPEN) { passGate(world, METAL_GATE_EAST_X) }
        for (gate in WOODEN_GATE) world.handlers.onLoc(gate, OPEN) { passGate(world, WOODEN_GATE_EAST_X) }
        world.handlers.onNpc(OBLI, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = obli() }
            if (trade) world.shopTypes.ofNpc(OBLI)?.let { world.shopScreen.open(player, it) }
        }
        world.handlers.onNpc(FERNAHEI, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = fernahei() }
            if (trade) world.shopTypes.ofNpc(FERNAHEI)?.let { world.shopScreen.open(player, it) }
        }
    }
}

/** Kaleb Paramaya's words, the Wiki's Transcript:Kaleb Paramaya (read 2026-11-19). */
object KalebLines : Lines("kaleb", "Transcript:Kaleb Paramaya") {
    val HELLO = line("hello", "his greeting")
    val ABOUT = line("about", "option: the player asks about the place, and says it")
    val OF_COURSE = line("of_course", "his answer: the player looks like a traveller")
    val TRAVELLER = line("traveller", "the player: yes, they are")
    val HOSTEL = line("hostel", "his hostel for weary travellers")
    val DORMITORY = line("dormitory", "the dormitory upstairs, 35 coins")
    val FOR_SALE = line("for_sale", "option: anything for sale, which the player then says")
    val DIARY = line("diary", "option: the Achievement Diary, which the player then says")
    val NOT_ANYMORE = line("not_anymore", "the diary: Pirate Jackie in Brimhaven does that now")
    val FINE = line("fine", "option: the player's fine, which they then say (also the sale menu's Cancel)")
    val BUY_WINE = line("buy_wine", "sale menu option (struck through without the coins)")
    val BUY_BEER = line("buy_beer", "sale menu option (struck through without the coins)")
    val BUY_REST = line("buy_rest", "sale menu option (struck through without the coins)")
    val BUY_PACK = line("buy_pack", "sale menu option (struck through without the coins)")
    val CANCEL = line("cancel", "sale menu option")
    val BOUGHT_WINE = line("bought_wine", "message box: a jug of wine bought")
    val BOUGHT_BEER = line("bought_beer", "message box: a beer bought")
    val BOUGHT_REST = line("bought_rest", "message box: a Paramaya ticket bought")
    val BOUGHT_PACK = line("bought_pack", "message box: five Paramaya tickets bought")
    val NOT_ENOUGH = line("not_enough", "a struck-through option picked: not enough money")
}

/** Yanni Salika's words, the Wiki's Transcript:Yanni Salika (read 2026-11-19). */
object YanniLines : Lines("yanni", "Transcript:Yanni Salika") {
    val HELLO = line("hello", "the player's greeting")
    val GREETINGS = line("greetings", "his: he buys and sells antiques")
    val ANTIQUES = line("antiques", "option 1, which the player then says")
    val BUY_ANTIQUES = line("buy_antiques", "his answer: he buys antiques and interesting items")
    val QUOTE = line("quote", "his offer to look at the player's items")
    val YES_PLEASE = line("yes_please", "its option 1, which the player then says")
    val NOTHING = line("nothing", "its option 1: the player has nothing he wants")
    val OTHER_TIME = line("other_time", "its option 2, which the player then says")
    val NICE_DAY = line("nice_day", "its option 2: his goodbye")
    val FOR_SALE = line("for_sale", "option 2, which the player then says")
    val NOTHING_YOU_NEED = line("nothing_you_need", "option 2, with the Beads of the Dead held or banked (the male form)")
    val NOT_INTERESTED = line("not_interested", "option 3, which the player then says")
    val FAIR_ENOUGH = line("fair_enough", "option 3: his answer")
}

/** Yohnus's words, the Wiki's Transcript:Yohnus (read 2026-11-19). */
object YohnusLines : Lines("yohnus", "Transcript:Yohnus") {
    val HELLO = line("hello", "the player's greeting")
    val CLOSED = line("closed", "his: the smithery is closed, the furnace for a fee, a coffer")
    val COFFER_TITLE = line("coffer_title", "the coffer menu's title", "coins")
    val DEPOSIT = line("deposit", "the coffer menu's option 1")
    val EMPTY = line("empty", "the coffer menu's option 2")
    val CANCEL = line("cancel", "the coffer menu's option 3")
    val ALREADY_EMPTY = line("already_empty", "message box: Empty coffer with nothing in it")
    val WITHDRAW = line("withdraw", "message box (with coins): the coffer emptied", "coins")
    val FEE = line("fee", "his: the furnace used with too few coins")
}

/** Seravel's words, the Wiki's Transcript:Seravel (read 2026-11-19). */
object SeravelLines : Lines("seravel", "Transcript:Seravel") {
    val HELLO = line("hello", "the player's greeting (live has no full stop)")
    val TICKET = line("ticket", "his: a ticket for the Lady of the Waves?")
    val YES = line("yes", "option 1, which the player then says")
    val NO = line("no", "option 2, which the player then says")
    val MORE = line("more", "option 3 (no line from the player)")
    val SHIP = line("ship", "option 3: the ship, west of the village, 25 coins")
    val NOT_ENOUGH = line("not_enough", "yes without 25 coins")
    val GREAT = line("great", "yes, with the ticket bought")
    val FAIR_ENOUGH = line("fair_enough", "no: his answer")
}

/**
 * Shilo Village's people (Karamja step K4.3b), from the Wiki's transcripts and pages for each (read 2026-11-19):
 *
 * - **Kaleb Paramaya** (5358, upstairs in the Paramaya Inn) sells a jug of wine for 1 coin, a beer for 2, a Paramaya
 *   ticket for 35 and five for 175 through his talk; an option the player can't pay for shows struck through.
 * - **Yanni Salika** (5361) talks; nothing the player can hold is an antique he buys here.
 * - **Yohnus** (5359) keeps the furnace's coffer: 20 coins a bar at the furnace by his smithery, out of the coffer
 *   first and then the backpack (the Wiki gives the fee per smelt, and the coffer as the way to smelt a full backpack),
 *   up to 40,000 coins in it, deposited in twenties.
 * - **Seravel** (5360, upstairs in the fishing shop) sells a ship ticket for 25 coins.
 *
 * Guessed: that the fee is taken from the backpack when the coffer is empty (inferred from the Wiki's Shilo Village
 * page); that Coffer opens the coffer menu straight away; that one beer is given (the message box speaks of a single glass,
 * though the Wiki's note says two); that a full backpack gets the item at the player's feet; the chathead faces.
 * Left out: Yanni's antiques (quest items no player holds here) and his black prism, and the Lady of the Waves itself
 * (step K4.3c, with the carts).
 */
object ShiloPeople {
    const val KALEB = 5358
    const val YANNI = 5361
    const val YOHNUS = 5359
    const val SERAVEL = 5360
    const val TALK_TO = 1
    const val COFFER = 3

    const val JUG_OF_WINE = 1993
    const val BEER = 1917
    const val PARAMAYA_TICKET = 619
    const val SHIP_TICKET = 621

    const val WINE_PRICE = 1
    const val BEER_PRICE = 2
    const val REST_PRICE = 35
    const val PACK_PRICE = 175
    const val PACK_TICKETS = 5
    const val TICKET_PRICE = 25

    const val FEE = 20
    const val COFFER_MAX = 40_000
    const val COFFER_CHARGE = "shilo_furnace_coffer"

    /** A menu option the player can't pay for, struck through as the client draws it. */
    private fun struck(text: String) = "<str>$text</str>"

    internal suspend fun Dialogue.kaleb(give: (Int, Int) -> Unit) = with(KalebLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        var pick = choice(ABOUT, FOR_SALE, DIARY, FINE)
        while (pick == 3) {
            chatPlayer(ChatAnim.QUIZ, DIARY)
            chatNpc(ChatAnim.NEUTRAL, NOT_ANYMORE)
            pick = choice(ABOUT, FOR_SALE, DIARY, FINE)
        }
        if (pick == 1) {
            chatPlayer(ChatAnim.QUIZ, ABOUT)
            chatNpc(ChatAnim.HAPPY, OF_COURSE)
            chatPlayer(ChatAnim.HAPPY, TRAVELLER)
            chatNpc(ChatAnim.HAPPY, HOSTEL)
            chatNpc(ChatAnim.HAPPY, DORMITORY)
            pick = if (choice(FOR_SALE, FINE) == 1) 2 else 4
        }
        if (pick == 4) return@with chatPlayer(ChatAnim.NEUTRAL, FINE)
        chatPlayer(ChatAnim.QUIZ, FOR_SALE)
        val sales = listOf(
            Triple(BUY_WINE, WINE_PRICE, BOUGHT_WINE) to (JUG_OF_WINE to 1),
            Triple(BUY_BEER, BEER_PRICE, BOUGHT_BEER) to (BEER to 1),
            Triple(BUY_REST, REST_PRICE, BOUGHT_REST) to (PARAMAYA_TICKET to 1),
            Triple(BUY_PACK, PACK_PRICE, BOUGHT_PACK) to (PARAMAYA_TICKET to PACK_TICKETS),
        )
        while (true) {
            val coins = player.inventory.count(Ned.COINS)
            val options = sales.map { (sale, _) -> text(sale.first).let { if (coins >= sale.second) it else struck(it) } } + text(CANCEL)
            val picked = choice(*options.toTypedArray())
            if (picked > sales.size) return@with chatPlayer(ChatAnim.NEUTRAL, FINE)
            val (sale, item) = sales[picked - 1]
            if (!AlKharidTraders.pay(player, sale.second)) {
                chatNpc(ChatAnim.SAD, NOT_ENOUGH)
                continue
            }
            give(item.first, item.second)
            mesbox(sale.third)
        }
    }

    internal suspend fun Dialogue.yanni() = with(YanniLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, GREETINGS)
        when (choice(ANTIQUES, FOR_SALE, NOT_INTERESTED)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, ANTIQUES)
                chatNpc(ChatAnim.HAPPY, BUY_ANTIQUES)
                chatNpc(ChatAnim.QUIZ, QUOTE)
                if (choice(YES_PLEASE, OTHER_TIME) == 1) {
                    chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
                    chatNpc(ChatAnim.NEUTRAL, NOTHING)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, OTHER_TIME)
                    chatNpc(ChatAnim.HAPPY, NICE_DAY)
                }
            }
            // Every quest is done here, so the Beads of the Dead count as had (inferred).
            2 -> {
                chatPlayer(ChatAnim.QUIZ, FOR_SALE)
                chatNpc(ChatAnim.NEUTRAL, NOTHING_YOU_NEED)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, NOT_INTERESTED)
                chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
            }
        }
    }

    internal suspend fun Dialogue.seravel(give: (Int) -> Unit) = with(SeravelLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.QUIZ, TICKET)
        var pick = choice(YES, NO, MORE)
        if (pick == 3) {
            chatNpc(ChatAnim.NEUTRAL, SHIP)
            pick = choice(YES, NO)
        }
        if (pick == 1) {
            chatPlayer(ChatAnim.HAPPY, YES)
            if (!AlKharidTraders.pay(player, TICKET_PRICE)) return@with chatNpc(ChatAnim.SAD, NOT_ENOUGH)
            give(SHIP_TICKET)
            chatNpc(ChatAnim.HAPPY, GREAT)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO)
            chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
        }
    }

    /** The coffer menu, until Cancel. */
    internal suspend fun Dialogue.coffer(world: World) = with(YohnusLines) {
        while (true) {
            val held = player.charges[COFFER_CHARGE]
            when (choice(DEPOSIT, EMPTY, CANCEL, title = text(COFFER_TITLE, "coins" to held))) {
                1 -> {
                    val wanted = context.countDialog()
                    val coins = minOf(player.inventory.count(Ned.COINS), Int.MAX_VALUE.toLong()).toInt()
                    val amount = minOf(minOf(wanted, coins), COFFER_MAX - held) / ShiloPeople.FEE * ShiloPeople.FEE
                    if (amount > 0) {
                        Ned.take(player, Ned.COINS, amount)
                        player.charges[COFFER_CHARGE] = held + amount
                    }
                }
                2 -> if (held == 0) {
                    mesbox(ALREADY_EMPTY)
                } else {
                    player.charges[COFFER_CHARGE] = 0
                    player.inventory.add(world.objTypes[Ned.COINS] ?: return@with, held)
                    objbox(Ned.COINS, text(WITHDRAW, "coins" to held))
                }
                else -> return@with
            }
        }
    }

    internal suspend fun Dialogue.yohnus(world: World) = with(YohnusLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.NEUTRAL, CLOSED)
        coffer(world)
    }
}

/** The fee at Shilo Village's furnace: out of Yohnus's coffer first, then the backpack. */
class ShiloFurnace(private val world: World) : osrsps.game.skill.Smithing.FurnaceFee {
    override fun canPay(player: osrsps.game.Player): Boolean =
        player.charges[ShiloPeople.COFFER_CHARGE] >= ShiloPeople.FEE || player.inventory.count(Ned.COINS) >= ShiloPeople.FEE

    override fun pay(player: osrsps.game.Player): Boolean {
        val coffer = player.charges[ShiloPeople.COFFER_CHARGE]
        if (coffer >= ShiloPeople.FEE) {
            player.charges[ShiloPeople.COFFER_CHARGE] = coffer - ShiloPeople.FEE
            return true
        }
        return AlKharidTraders.pay(player, ShiloPeople.FEE)
    }

    override suspend fun refuse(context: ScriptContext) {
        val yohnus = world.npcTypes[ShiloPeople.YOHNUS] ?: return
        world.dialogues.start(context) { chatNpc(yohnus, yohnus.name, ChatAnim.NEUTRAL, YohnusLines.FEE) }
    }
}

fun registerShiloPeople(world: World) {
    with(ShiloPeople) {
        world.handlers.onNpc(KALEB, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) {
                kaleb { obj, count -> world.objTypes[obj]?.let { type -> repeat(count) { world.groundObjs.give(player, type, 1) } } }
            }
        }
        world.handlers.onNpc(YANNI, TALK_TO) { op -> world.dialogues.start(this, op.npc) { yanni() } }
        world.handlers.onNpc(SERAVEL, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { seravel { obj -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } } }
        }
        world.handlers.onNpc(YOHNUS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { yohnus(world) } }
        world.handlers.onNpc(YOHNUS, COFFER) { op -> world.dialogues.start(this, op.npc) { coffer(world) } }
        world.handlers.onLoc(osrsps.game.skill.Smithing.SHILO_FURNACE, COFFER) { world.dialogues.start(this) { coffer(world) } }
        world.smithing.fees[osrsps.game.skill.Smithing.SHILO_FURNACE] = ShiloFurnace(world)
    }
}
