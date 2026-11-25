package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

private val logger = KotlinLogging.logger {}

/** Cap'n Izzy No-Beard's and his parrot's words, the Wiki's Transcript:Cap'n Izzy No-Beard (read 2026-11-18). */
object IzzyLines : Lines("izzy", "Transcript:Cap'n Izzy No-Beard") {
    val AHOY_CAPN = line("ahoy_capn", "the player's greeting")
    val AHOY_THERE = line("ahoy_there", "his answer")
    val PARROT_AVAST = line("parrot_avast", "the parrot: avast")
    val HUH = line("huh", "the player's puzzlement")
    val CRACKED = line("cracked", "his parrot is Cracked Jenny's Tea Cup")
    val WHAT_PLACE = line("what_place", "menu option 1, which the player then says")
    val PLACE_1 = line("place_1", "option 1: the entrance to the arena")
    val PLACE_2 = line("place_2", "option 1: digging for buried treasure")
    val PARROT_TREASURE = line("parrot_treasure", "option 1, the parrot: buried treasure")
    val PLACE_3 = line("place_3", "option 1: a league from side to side")
    val PLACE_4 = line("place_4", "option 1: it made him list")
    val PARROT_GET_ON = line("parrot_get_on", "option 1, the parrot: get on with it")
    val PLACE_5 = line("place_5", "option 1: a huge cave with platforms and traps")
    val PARROT_DANGER = line("parrot_danger", "option 1, the parrot: danger")
    val ONLY_200 = line("only_200", "option 1: entrance is only 200 coins")
    val WHAT_DO = line("what_do", "menu option 2, which the player then says")
    val DO_1 = line("do_1", "option 2: cross between the platforms to the arrowed pillar")
    val DO_2 = line("do_2", "option 2: tickets for tagging pillars in a row")
    val DO_3 = line("do_3", "option 2: take the tickets to Jackie")
    val PARROT_GREEN = line("parrot_green", "option 2, the parrot: green light, red light")
    val THANKS = line("thanks", "option 2, the player's thanks")
    val USE_ARENA = line("use_arena", "menu option 3, which the player then says")
    val ENTRANCE_200 = line("entrance_200", "option 3: entrance be 200 coins")
    val PARROT_EIGHT = line("parrot_eight", "option 3, the parrot: pieces of eight")
    val WARNING = line("warning", "option 3: a word of warning about the traps")
    val NO_MONEY = line("no_money", "option 3 without 200 coins, which the player says")
    val PARROT_SQUAWK = line("parrot_squawk", "then the parrot's squawk")
    val NO_COINS = line("no_coins", "then: no coins, no entrance (also his Pay option's without them)")
    val HERES_200 = line("heres_200", "option 3 with 200 coins: its menu's option 1, which the player then says")
    val HAND_200 = line("hand_200", "the box as the 200 coins go")
    val WIND = line("wind", "then: may the wind be in ye sails")
    val NEVER_MIND = line("never_mind", "that menu's option 2, which the player then says")
    val CAPE_ABOUT = line("cape_about", "menu option 4 below 99 Agility, which the player then says")
    val CAPE_SYMBOL = line("cape_symbol", "option 4: the cape is the symbol of the master of dexterity")
    val ANYTHING_ELSE = line("anything_else", "option 4: anything else")
    val CAPE_SELL = line("cape_sell", "menu option 4 at 99 Agility")
    val BUY_CAPE = line("buy_cape", "buying a cape, which the player says")
    val PRICE = line("price", "buying: 99,000 coins")
    val CANT_AFFORD = line("cant_afford", "the price menu's option 1, which the player then says")
    val SO_SAD = line("so_sad", "then: go and pickpocket some wealthy people")
    val HERE_YOU_GO = line("here_you_go", "the price menu's option 2, which the player then says")
    val NOT_ENOUGH = line("not_enough", "then, without 99,000 coins, the player says")
    val COME_BACK = line("come_back", "then: come back when you do")
    val NO_SPACE = line("no_space", "then, without room for the cape and hood")
    val SUITS_YOU = line("suits_you", "then, sold: that cape really does suit you")
    val SKILLCAPE = line("skillcape", "with an Agility cape: the menu's option 1")
    val HOOD = line("hood", "its option 2")
    val ANOTHER_HOOD = line("another_hood", "the hood, which the player then says")
    val FREE = line("free", "the hood: free of charge")
    val HOOD_GIVEN = line("hood_given", "the box with the hood")
    val SEE_YOU = line("see_you", "menu option 5, which the player then says")
    val FEE_PAID = line("fee_paid", "his Pay option: the message as the 200 coins go (the Wiki's Cap'n Izzy No-Beard page)")
    val NOT_PAID = line("not_paid", "the ladder down without the fee (keys only: the Wiki gives no words)")
}

/** Pirate Jackie the Fruit's words, the Wiki's Transcript:Pirate Jackie the Fruit (read 2026-11-18). */
object JackieLines : Lines("pirate_jackie", "Transcript:Pirate Jackie the Fruit") {
    val AHOY = line("ahoy", "her greeting")
    val WHAT_PLACE = line("what_place", "menu option 1, which the player then says")
    val WELCOME = line("welcome", "option 1: welcome to the arena")
    val ASK_IZZY = line("ask_izzy", "option 1: talk to Cap'n Izzy")
    val WHAT_DO = line("what_do", "menu option 2, which the player then says")
    val JACK_O_TICKETS = line("jack_o_tickets", "option 2: she exchanges the tickets")
    val SOUNDS_GOOD = line("sounds_good", "option 2: the player's answer")
    val TRADE_IN = line("trade_in", "menu option 3, which the player then says")
    val RIGHT_TACK = line("right_tack", "option 3: ye be on the right tack")
}

/** The arena's messages, the Wiki's Ticket dispenser page (read 2026-11-18). */
object AgilityArenaLines : Lines("agility_arena", "Ticket dispenser") {
    val TICKET = line("ticket", "a tag of the arrowed dispenser: a ticket and a voucher")
    val ONE_AT_A_TIME = line("one_at_a_time", "the arrowed dispenser again before the arrow moves")
    val NOT_ARROWED = line("not_arrowed", "a dispenser without the arrow")
}

/**
 * The Brimhaven Agility Arena's way in and its ticket dispensers (Karamja step K3.4), as the Wiki's Brimhaven Agility
 * Arena, Cap'n Izzy No-Beard, Pirate Jackie the Fruit and Ticket dispenser pages have them (read 2026-11-18):
 *
 * - **Cap'n Izzy No-Beard** (5789) in the hut takes the 200-coin fee through his talk or his Pay option, and sells the
 *   Agility cape at 99 Agility for 99,000 coins (trimmed with another 99), with a free hood for one who has the cape.
 * - **The ladder** (3617) down takes a player who has paid into the arena, using up the fee; the ladder (3618) on the
 *   north-east pillar leads back up. Logging out in the arena puts the player back in the hut ([savedAt]).
 * - **The ticket dispensers** (3608, and 3581 with darts): one of the 24 has the arrow, and it moves every minute (100
 *   ticks) to another at random. A tag of the arrowed one gives 30 Agility XP for every 10 Agility levels (boosts and
 *   drains count, at most 300), an Agility arena ticket and a Brimhaven voucher, once until the arrow moves.
 * - **Pirate Jackie the Fruit** (5516) talks.
 *
 * Inferred: the arrow is the client's tile hint arrow; the ladders' landing tiles are the sides the other ladder faces, and their climbs the usual ladder anims and wait;
 * his Pay option takes the fee with the Wiki's message and no talk, and refuses with his talk's line; "initial" in the
 * transcript is back to the menu. Guessed: the hint arrow's height; that the arrow only draws its next dispenser once
 * someone is in the arena to see it; that a ticket and voucher with no room drop at the player's feet; the parrot's chathead (the
 * Eadgar parrot, 4768) and the chathead faces. Left out: the obstacles (step K3.4b); the Agility Arena Store behind
 * Jackie's trade-in and her Trade option (step K3.4c); the diary talk and the elite diary's double tickets (no diaries
 * yet); the "Tagged a Ticket!" jingle (its id isn't known here); the minute's wait to re-enter after logging out; the
 * Brimhaven Agility worlds' first-tag shout; the clue step.
 */
object BrimhavenAgilityArena {
    const val IZZY = 5789
    const val JACKIE = 5516
    const val PARROT = 4768
    const val TALK_TO = 1
    const val PAY = 3

    const val LADDER_DOWN = 3617
    const val LADDER_UP = 3618
    const val CLIMB = 1
    const val DISPENSER = 3608
    const val DART_DISPENSER = 3581
    const val TAG = 1

    const val FEE = 200
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99
    const val AGILITY_CAPE = 9771
    const val AGILITY_CAPE_TRIMMED = 9772
    const val AGILITY_HOOD = 9773
    const val TICKET = 29480
    const val VOUCHER = 29482

    /** The arrow moves every minute. */
    const val ROTATE_TICKS = 100

    /** Guessed: the tile hint arrow's height above the pillar. */
    const val ARROW_HEIGHT = 120

    /** Where the ladders put the player: below the hut's ladder on the north-east pillar, and beside the hut's ladder. */
    val ARENA_ARRIVAL = Destination(2805, 9589, 3)
    val HUT = Destination(2810, 3194, 0)

    /** The arena's platforms (level 3) and the pit below them (level 0). */
    fun inArena(x: Int, z: Int): Boolean = x in 2752..2815 && z in 9536..9599

    /** The 24 dispensers' tiles, west to east and south to north, as the 242 map has them (all on level 3). */
    val DISPENSERS: List<Destination> = listOf(
        2761 to 9546, 2761 to 9557, 2761 to 9568, 2761 to 9579, 2761 to 9590,
        2772 to 9546, 2772 to 9557, 2772 to 9568, 2772 to 9579, 2772 to 9590,
        2783 to 9546, 2783 to 9557, 2783 to 9568, 2783 to 9579, 2783 to 9590,
        2794 to 9546, 2794 to 9557, 2794 to 9568, 2794 to 9579, 2794 to 9590,
        2805 to 9546, 2805 to 9557, 2805 to 9568, 2805 to 9579,
    ).map { (x, z) -> Destination(x, z, 3) }

    /** Agility XP in tenths for a tag at [level]: 30 for every 10 levels, at most 300. */
    fun tagTenths(level: Int): Int = minOf(level, 100) / 10 * 300

    /** Where a save puts [player]: the hut when they are in the arena, else null for where they stand. */
    fun savedAt(player: Player): Destination? = if (inArena(player.x, player.z)) HUT else null
}

/** The arena's shared state: which dispenser has the arrow, who tagged it, who has paid and what each player is shown. */
class AgilityArena(private val world: World) {
    /** The index into [BrimhavenAgilityArena.DISPENSERS] of the arrowed dispenser. */
    var active: Int = 0
        private set

    /**
     * True when the arrow is due to move but nobody was in the arena to see it: the draw waits for the next player in,
     * so an empty arena takes nothing from [World.random] and seeded runs elsewhere replay as before.
     */
    private var due = true

    /** How often the arrow moves; tests hold it still or hurry it. */
    internal var rotateTicks = BrimhavenAgilityArena.ROTATE_TICKS
    private var movedAt = 0L
    private val tagged = HashSet<String>()
    private val paid = HashSet<String>()
    private val shown = HashMap<String, Int>()

    fun pay(player: Player) { paid += player.saveKey }
    fun usePaid(player: Player): Boolean = paid.remove(player.saveKey)

    /** Moves the arrow every [rotateTicks] and keeps each player's hint arrow in step. */
    fun process(tick: Long) {
        if (tick - movedAt >= rotateTicks) {
            movedAt = tick
            due = true
        }
        val players = world.onlinePlayers
        val inside = players.filter { BrimhavenAgilityArena.inArena(it.x, it.z) }
        if (due && inside.isNotEmpty()) {
            due = false
            // Another dispenser than the one it leaves.
            val count = BrimhavenAgilityArena.DISPENSERS.size
            active = (active + 1 + world.random.nextInt(count - 1)) % count
            tagged.clear()
        }
        for (player in players) {
            val was = shown[player.saveKey]
            if (player in inside) {
                if (was == active) continue
                val tile = BrimhavenAgilityArena.DISPENSERS[active]
                player.session.queue(HintArrow(HintArrow.TileHintArrow(tile.x, tile.z, BrimhavenAgilityArena.ARROW_HEIGHT, HintArrow.TileHintArrow.HintArrowTilePosition.CENTER)))
                shown[player.saveKey] = active
            } else if (was != null) {
                player.session.queue(HintArrow(HintArrow.ResetHintArrow))
                shown.remove(player.saveKey)
            }
        }
        if (shown.size > players.size) shown.keys.retainAll(players.mapTo(HashSet()) { it.saveKey })
    }

    /** A tag of the dispenser at [x], [z]. */
    fun tag(player: Player, x: Int, z: Int) {
        val text = world.textFor(player)
        val tile = BrimhavenAgilityArena.DISPENSERS[active]
        when {
            tile.x != x || tile.z != z -> player.message(text[AgilityArenaLines.NOT_ARROWED])
            !tagged.add(player.saveKey) -> player.message(text[AgilityArenaLines.ONE_AT_A_TIME])
            else -> {
                player.skills.addTenths(Skill.AGILITY, BrimhavenAgilityArena.tagTenths(player.skills.currentLevel(Skill.AGILITY)))
                for (obj in listOf(BrimhavenAgilityArena.TICKET, BrimhavenAgilityArena.VOUCHER)) {
                    world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
                }
                player.message(text[AgilityArenaLines.TICKET])
            }
        }
    }
}

internal object AgilityArenaTalk {
    private suspend fun Dialogue.parrot(world: World, line: osrsps.game.dialogue.Line) {
        val type = world.npcTypes[BrimhavenAgilityArena.PARROT]
        if (type != null) chatNpc(type, type.name, ChatAnim.NEUTRAL, line) else chatNpc(ChatAnim.NEUTRAL, line)
    }

    private fun coins(player: Player) = player.inventory.count(ShopScreen.COINS)

    /** Izzy's talk; [give] hands over an item. */
    suspend fun Dialogue.izzy(world: World, arena: AgilityArena, give: (Int) -> Unit) = with(IzzyLines) {
        chatPlayer(ChatAnim.HAPPY, AHOY_CAPN)
        chatNpc(ChatAnim.HAPPY, AHOY_THERE)
        parrot(world, PARROT_AVAST)
        chatPlayer(ChatAnim.QUIZ, HUH)
        chatNpc(ChatAnim.HAPPY, CRACKED)
        val master = player.skills.baseLevel(Skill.AGILITY) >= BrimhavenAgilityArena.MAX_LEVEL
        while (true) {
            when (choice(WHAT_PLACE, WHAT_DO, USE_ARENA, if (master) CAPE_SELL else CAPE_ABOUT, SEE_YOU)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
                    chatNpc(ChatAnim.HAPPY, PLACE_1)
                    chatNpc(ChatAnim.HAPPY, PLACE_2)
                    parrot(world, PARROT_TREASURE)
                    chatNpc(ChatAnim.HAPPY, PLACE_3)
                    chatNpc(ChatAnim.HAPPY, PLACE_4)
                    parrot(world, PARROT_GET_ON)
                    chatNpc(ChatAnim.NEUTRAL, PLACE_5)
                    parrot(world, PARROT_DANGER)
                    chatNpc(ChatAnim.HAPPY, ONLY_200)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_DO)
                    chatNpc(ChatAnim.NEUTRAL, DO_1)
                    chatNpc(ChatAnim.NEUTRAL, DO_2)
                    chatNpc(ChatAnim.NEUTRAL, DO_3)
                    parrot(world, PARROT_GREEN)
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                }
                3 -> {
                    useArena(world, arena)
                    return@with
                }
                4 -> {
                    if (!master) {
                        chatPlayer(ChatAnim.QUIZ, CAPE_ABOUT)
                        chatNpc(ChatAnim.HAPPY, CAPE_SYMBOL)
                        chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
                    } else {
                        cape(give)
                        return@with
                    }
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, SEE_YOU)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.useArena(world: World, arena: AgilityArena) = with(IzzyLines) {
        chatPlayer(ChatAnim.QUIZ, USE_ARENA)
        chatNpc(ChatAnim.HAPPY, ENTRANCE_200)
        parrot(world, PARROT_EIGHT)
        chatNpc(ChatAnim.NEUTRAL, WARNING)
        if (coins(player) < BrimhavenAgilityArena.FEE) {
            chatPlayer(ChatAnim.SAD, NO_MONEY)
            parrot(world, PARROT_SQUAWK)
            chatNpc(ChatAnim.ANGRY, NO_COINS)
            return@with
        }
        if (choice(HERES_200, NEVER_MIND) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, HERES_200)
        if (!AlKharidTraders.pay(player, BrimhavenAgilityArena.FEE)) return@with
        arena.pay(player)
        mesbox(HAND_200)
        chatNpc(ChatAnim.HAPPY, WIND)
    }

    private fun owns(player: Player, obj: Int) =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0

    private suspend fun Dialogue.cape(give: (Int) -> Unit) = with(IzzyLines) {
        val hasCape = owns(player, BrimhavenAgilityArena.AGILITY_CAPE) || owns(player, BrimhavenAgilityArena.AGILITY_CAPE_TRIMMED)
        if (hasCape && choice(SKILLCAPE, HOOD) == 2) {
            chatPlayer(ChatAnim.QUIZ, ANOTHER_HOOD)
            chatNpc(ChatAnim.HAPPY, FREE)
            give(BrimhavenAgilityArena.AGILITY_HOOD)
            mesbox(HOOD_GIVEN)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, BUY_CAPE)
        chatNpc(ChatAnim.HAPPY, PRICE)
        if (choice(CANT_AFFORD, HERE_YOU_GO) == 1) {
            chatPlayer(ChatAnim.SAD, CANT_AFFORD)
            chatNpc(ChatAnim.NEUTRAL, SO_SAD)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, HERE_YOU_GO)
        val coins = coins(player)
        when {
            coins < BrimhavenAgilityArena.CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
                chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            }
            player.inventory.freeSlots + (if (coins == BrimhavenAgilityArena.CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            else -> {
                if (!AlKharidTraders.pay(player, BrimhavenAgilityArena.CAPE_PRICE)) return@with
                val trimmed = Skill.entries.any { it != Skill.AGILITY && player.skills.baseLevel(it) >= BrimhavenAgilityArena.MAX_LEVEL }
                give(if (trimmed) BrimhavenAgilityArena.AGILITY_CAPE_TRIMMED else BrimhavenAgilityArena.AGILITY_CAPE)
                give(BrimhavenAgilityArena.AGILITY_HOOD)
                chatNpc(ChatAnim.HAPPY, SUITS_YOU)
            }
        }
    }

    /** Jackie's talk. */
    suspend fun Dialogue.jackie() = with(JackieLines) {
        chatNpc(ChatAnim.HAPPY, AHOY)
        while (true) {
            when (choice(WHAT_PLACE, WHAT_DO, TRADE_IN)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
                    chatNpc(ChatAnim.HAPPY, WELCOME)
                    chatNpc(ChatAnim.HAPPY, ASK_IZZY)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_DO)
                    chatNpc(ChatAnim.HAPPY, JACK_O_TICKETS)
                    chatPlayer(ChatAnim.HAPPY, SOUNDS_GOOD)
                }
                else -> {
                    chatPlayer(ChatAnim.HAPPY, TRADE_IN)
                    chatNpc(ChatAnim.HAPPY, RIGHT_TACK)
                    return@with
                }
            }
        }
    }
}

fun registerBrimhavenAgilityArena(world: World) {
    val arena = world.agilityArena
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    with(BrimhavenAgilityArena) {
        world.handlers.onNpc(IZZY, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { with(AgilityArenaTalk) { izzy(world, arena) { give(player, it) } } }
        }
        world.handlers.onNpc(IZZY, PAY) { op ->
            if (AlKharidTraders.pay(player, FEE)) {
                arena.pay(player)
                player.message(world.textFor(player)[IzzyLines.FEE_PAID])
            } else {
                world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.ANGRY, IzzyLines.NO_COINS) }
            }
        }
        world.handlers.onNpc(JACKIE, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { with(AgilityArenaTalk) { jackie() } }
        }
        world.handlers.onLoc(LADDER_DOWN, CLIMB) {
            arriveDelay()
            if (!arena.usePaid(player)) {
                player.message(world.textFor(player)[IzzyLines.NOT_PAID])
                return@onLoc
            }
            player.anim(Climbing.ANIM_PICKUP_FLOOR)
            delay(1)
            world.teleport(player, ARENA_ARRIVAL.x, ARENA_ARRIVAL.z, ARENA_ARRIVAL.level)
        }
        world.handlers.onLoc(LADDER_UP, CLIMB) {
            arriveDelay()
            player.anim(Climbing.ANIM_REACH_FOR_LADDER)
            delay(1)
            world.teleport(player, HUT.x, HUT.z, HUT.level)
        }
        for (dispenser in listOf(DISPENSER, DART_DISPENSER)) {
            world.handlers.onLoc(dispenser, TAG) { op ->
                arriveDelay()
                val loc = op.loc!!
                arena.tag(player, loc.x, loc.z)
            }
        }
    }
}
