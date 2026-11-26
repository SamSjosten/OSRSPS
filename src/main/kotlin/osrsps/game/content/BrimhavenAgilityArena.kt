package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen
import osrsps.game.skill.Agility
import osrsps.game.skill.SuccessRoll
import osrsps.map.Tile
import kotlin.math.sign

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
object AgilityArenaLines : Lines("agility_arena", "Ticket dispenser; Spinning blades (Brimhaven Agility Arena)") {
    val SPINNING_BLADES = line("spinning_blades", "the spinning blades hitting the player (the Wiki's Spinning blades page)")
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
 * - **Pirate Jackie the Fruit** (5516, shown as 7650 or 7651) talks.
 * - **The obstacles** (step K3.4b) are agility.json's `arena_` entries; a fall lands in the pit, whose climbing ropes
 *   (3610) climb back up to the same tile on the platforms. **The planks** are rigged: of each set of three, the smooth
 *   one holds and the broken ones break under anyone below 99 Agility (the Wiki's Plank page), and the smooth one moves
 *   with the arrow, the planks' middle pieces laid to match ([AgilityArena.plankHolds]).
 * - **The floor traps** (step K3.4d): walking onto the floor spikes (3582, level 20, 24 XP, safe from 50 Agility),
 *   a pressure pad (3585, level 20, 26 XP, none for a third in a row within 8 ticks) or the spinning blades (3580,
 *   level 40, 28 XP, the Wiki's 153/256 to 255/256) jumps it ([AgilityArena.stepped]); a failure deals HP * 5 / 100 + 2.
 *
 * Inferred: the arrow is the client's tile hint arrow; the ladders' landing tiles are the sides the other ladder faces, and their climbs the usual ladder anims and wait;
 * his Pay option takes the fee with the Wiki's message and no talk, and refuses with his talk's line; "initial" in the
 * transcript is back to the menu. Guessed: the hint arrow's height; that the arrow only draws its next dispenser once
 * someone is in the arena to see it; that a ticket and voucher with no room drop at the player's feet; the parrot's chathead (the
 * Eadgar parrot, 4768) and the chathead faces. Guessed: the smooth plank drawn afresh each time the arrow moves
 * (the Wiki: every minute), the climbing ropes' anim and wait. Guessed: the traps' anims and jump, the spikes' and pads' success pairs, a failure putting the player back where
 * they came from, and a trap below its level always failed. Left out: the blades (3567-3569, timed) and the darts
 * (their Agility drain); a broken plank's extra tick at 99; the Agility Arena Store behind
 * Jackie's trade-in and her Trade option (step K3.4c); the diary talk and the elite diary's double tickets (no diaries
 * yet); the "Tagged a Ticket!" jingle (its id isn't known here); the minute's wait to re-enter after logging out; the
 * Brimhaven Agility worlds' first-tag shout; the clue step.
 */
object BrimhavenAgilityArena {
    const val IZZY = 5789
    const val JACKIE = 5516

    /**
     * The types Jackie (a multi-NPC by varbit 4567) shows, the cache's agilityarena_tickettrader_2ops and _3ops: her
     * talk is on what the player sees, so it is registered on both.
     */
    val JACKIE_SHOWN = listOf(7650, 7651)
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

    /** The pit's climbing ropes (level 0), each back up to its own tile on level 3. */
    const val CLIMBING_ROPE = 3610

    /** The agility.json obstacle the planks are, rigged by [AgilityArena.plankHolds]. */
    const val PLANK = "arena_plank"

    /**
     * The two sets of three planks, as the 242 map has them: west of the middle pillar of the south row but one (x 2764
     * to 2769) and in the north-east (x 2797 to 2802), each a plank per row ([PLANK_ROWS], south to north).
     */
    val PLANK_SETS: List<IntRange> = listOf(2764..2769, 2797..2802)
    val PLANK_ROWS: List<List<Int>> = listOf(listOf(9556, 9557, 9558), listOf(9589, 9590, 9591))

    /** The middle pieces of a plank: smooth (3573, 3574) or broken (3576). */
    val PLANK_MIDDLES = 3573..3576
    const val PLANK_BROKEN = 3576

    /**
     * The floor traps (step K3.4d) by kind, as the 242 map has them on the platforms (level 3): two tiles each for the
     * floor spikes (3582) and pressure pads (3585), one for the spinning blades (3580). Walking onto one jumps it.
     * [ticks] is a crossing from the step onto it to the landing, the Wiki's obstacle table: 4 for the spikes and pads,
     * 5 for the blades. Below [level] a jump always fails (guessed: the Wiki gives the level, not what happens under it).
     */
    enum class Trap(val level: Int, val xpTenths: Int, val success: Pair<Int, Int>?, val ticks: Int, val anim: Int, val failAnim: Int, val tiles: List<Pair<Int, Int>>) {
        // Never fails from 50 Agility (the Wiki); the pair below that is guessed.
        FLOOR_SPIKES(20, 240, 150 to 255, 4, 1603, 1113, listOf(2761 to 9573, 2761 to 9574, 2772 to 9551, 2772 to 9552, 2799 to 9568, 2800 to 9568)),
        // Guessed pair.
        PRESSURE_PAD(20, 260, 150 to 255, 4, 1115, 1113, listOf(2772 to 9584, 2772 to 9585, 2799 to 9557, 2800 to 9557, 2799 to 9579, 2800 to 9579)),
        // The Wiki's success chart, 153/256 at 40 to 255/256 at 99, as SuccessRoll's low/high: (1 + 85) at 1 Agility,
        // (1 + 254) at 99, which gives 1 + round((85 * 59 + 254 * 39) / 98) = 153 at 40.
        SPINNING_BLADES(40, 280, 85 to 254, 5, 1115, 1113, listOf(2777 to 9556, 2777 to 9580, 2782 to 9573)),
    }

    /** The floor spikes never fail from this level. */
    const val SPIKES_SAFE_LEVEL = 50

    /** Two pressure pads in a row, and the next gives no XP for 8 ticks (the Wiki, 17 July 2024). */
    const val PAD_STREAK = 2
    const val PAD_COOLDOWN_TICKS = 8

    /** The trap at [x], [z] on the platforms, or null. */
    fun trapAt(x: Int, z: Int): Trap? = Trap.entries.firstOrNull { (x to z) in it.tiles }

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
    @Volatile
    private var due = true

    /** How often the arrow moves; tests hold it still or hurry it. */
    @Volatile
    internal var rotateTicks = BrimhavenAgilityArena.ROTATE_TICKS
    private var movedAt = 0L
    private val tagged = HashSet<String>()
    private val paid = HashSet<String>()
    /** The dispenser each player's client has the arrow over, and the session it went to: a reconnect gets it again. */
    private val shown = HashMap<String, Pair<Int, Any>>()

    /** Tests: moves the arrow once on the next tick a player is in the arena, whatever [rotateTicks] is. */
    internal fun moveArrow() { due = true }

    /** For each of [BrimhavenAgilityArena.PLANK_SETS], the row of its smooth plank; the map's own until the first draw. */
    private val smooth = IntArray(BrimhavenAgilityArena.PLANK_SETS.size) { 1 }

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
            for (set in smooth.indices) {
                smooth[set] = world.random.nextInt(BrimhavenAgilityArena.PLANK_ROWS[set].size)
                layPlanks(set)
            }
        }
        for (player in players) {
            val was = shown[player.saveKey]
            if (player in inside) {
                if (was != null && was.first == active && was.second === player.session) continue
                val tile = BrimhavenAgilityArena.DISPENSERS[active]
                player.session.queue(HintArrow(HintArrow.TileHintArrow(tile.x, tile.z, BrimhavenAgilityArena.ARROW_HEIGHT, HintArrow.TileHintArrow.HintArrowTilePosition.CENTER)))
                shown[player.saveKey] = active to player.session
            } else if (was != null) {
                player.session.queue(HintArrow(HintArrow.ResetHintArrow))
                shown.remove(player.saveKey)
            }
        }
        if (shown.size > players.size) shown.keys.retainAll(players.mapTo(HashSet()) { it.saveKey })
    }

    /** [tenthsAfter]: the player's Agility XP (tenths) after the streak's last pad, so XP from anything else ends it. */
    private class PadStreak(var count: Int, var lastTick: Long, var tenthsAfter: Int = -1)

    private val pads = HashMap<String, PadStreak>()

    /**
     * A player's movement in the arena, [trail] being the tiles they stood on this tick: onto a floor trap (either step
     * of a run, in the way that step went), the player jumps it, landing on the far side. A failure deals the arena's
     * HP * 5 / 100 + 2 and puts them back on the tile before the trap.
     */
    fun stepped(player: Player, trail: List<Tile>) {
        if (player.level != 3 || player.scripts.delayed) return
        for (i in 1 until trail.size) {
            val to = trail[i]
            if (!BrimhavenAgilityArena.inArena(to.x, to.z)) continue
            val trap = BrimhavenAgilityArena.trapAt(to.x, to.z) ?: continue
            val dx = (to.x - trail[i - 1].x).sign
            val dz = (to.z - trail[i - 1].z).sign
            if (dx == 0 && dz == 0) continue
            var end = to.x to to.z
            while (BrimhavenAgilityArena.trapAt(end.first + dx, end.second + dz) == trap) end = end.first + dx to end.second + dz
            val landing = end.first + dx to end.second + dz
            val before = to.x - dx to to.z - dz
            world.scripts.start(player) { jump(this, trap, landing, before) }
            return
        }
    }

    private suspend fun jump(context: ScriptContext, trap: BrimhavenAgilityArena.Trap, landing: Pair<Int, Int>, before: Pair<Int, Int>) = with(context) {
        player.waypoints.clear()
        player.forcedSteps.clear()
        val level = player.skills.currentLevel(Skill.AGILITY)
        val success = when {
            level < trap.level -> false
            trap == BrimhavenAgilityArena.Trap.FLOOR_SPIKES && level >= BrimhavenAgilityArena.SPIKES_SAFE_LEVEL -> true
            else -> trap.success?.let { (low, high) -> SuccessRoll.roll(world.random, low, high, level) } ?: true
        }
        if (!success) {
            player.anim(trap.failAnim)
            val damage = player.skills.currentLevel(Skill.HITPOINTS) * 5 / 100 + 2
            world.meleeCombat.statusHit(player, damage, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
            if (trap == BrimhavenAgilityArena.Trap.SPINNING_BLADES) player.message(world.textFor(player)[AgilityArenaLines.SPINNING_BLADES])
            // A hit that kills leaves the player to the death script, not put back by this one.
            if (player.dead) return@with
            delay(1)
            world.teleport(player, before.first, before.second, player.level)
            return@with
        }
        val angle = Agility.angle(landing.first - player.x, landing.second - player.z)
        // The slide lasts until the landing teleport, so the client doesn't walk back to the trap tile once it ends.
        player.effects.exactMove(PlayerEffects.ExactMove(player.x, player.z, landing.first, landing.second, 0, (trap.ticks - 1) * CYCLES_PER_TICK, angle))
        player.anim(trap.anim)
        // The step onto the trap was the crossing's first tick.
        delay(trap.ticks - 1)
        world.teleport(player, landing.first, landing.second, player.level)
        if (trap == BrimhavenAgilityArena.Trap.PRESSURE_PAD && !padPays(player)) return@with
        player.skills.addTenths(Skill.AGILITY, trap.xpTenths)
        if (trap == BrimhavenAgilityArena.Trap.PRESSURE_PAD) pads[player.saveKey]?.tenthsAfter = player.skills.tenths(Skill.AGILITY)
    }

    /**
     * Whether a pressure pad crossed now gives its XP: not a third in a row within 8 ticks of the second. Another
     * obstacle or trap crossed in between (Agility XP gained since the last pad) starts the streak again.
     */
    private fun padPays(player: Player): Boolean {
        val streak = pads.getOrPut(player.saveKey) { PadStreak(0, Long.MIN_VALUE / 2) }
        if (player.skills.tenths(Skill.AGILITY) != streak.tenthsAfter) streak.count = 0
        val now = world.tick
        if (streak.count >= BrimhavenAgilityArena.PAD_STREAK && now - streak.lastTick < BrimhavenAgilityArena.PAD_COOLDOWN_TICKS) return false
        if (streak.count >= BrimhavenAgilityArena.PAD_STREAK) streak.count = 0
        streak.count++
        streak.lastTick = now
        return true
    }

    /**
     * Whether the plank at [x], [z] holds [player]: the smooth one always, a broken one at 99 Agility (boosts count, Mod
     * Ash, on the Wiki's Plank page), and otherwise it breaks.
     */
    fun plankHolds(player: Player, x: Int, z: Int): Boolean {
        if (player.skills.currentLevel(Skill.AGILITY) >= BrimhavenAgilityArena.MAX_LEVEL) return true
        val set = BrimhavenAgilityArena.PLANK_SETS.indexOfFirst { x in it }
        if (set < 0) return true
        return BrimhavenAgilityArena.PLANK_ROWS[set].indexOf(z) == smooth[set]
    }

    /** The smooth row of [set] now, for tests. */
    internal fun smoothRow(set: Int): Int = smooth[set]

    /**
     * Lays [set]'s planks with the smooth one on its drawn row: each row's middle pieces become those of a row of the map
     * (the smooth one's on the drawn row, the broken ones' on the others, in order), so the broken planks show their
     * missing chunks. A map without the planks (tests) is left alone.
     */
    private fun layPlanks(set: Int) {
        val rows = BrimhavenAgilityArena.PLANK_ROWS[set]
        val xs = BrimhavenAgilityArena.PLANK_SETS[set].let { (it.first + 1) until it.last }
        fun middle(x: Int, z: Int) = world.locs.at(x, z, 3).firstOrNull { it.id in BrimhavenAgilityArena.PLANK_MIDDLES }
        fun cached(x: Int, z: Int) = (0 until 4).firstNotNullOfOrNull { layer ->
            world.locs.cached(x, z, 3, layer)?.takeIf { it.id in BrimhavenAgilityArena.PLANK_MIDDLES }
        }
        val patterns = rows.map { z -> xs.map { x -> cached(x, z) ?: return } }
        val smoothPattern = patterns.firstOrNull { row -> row.none { it.id == BrimhavenAgilityArena.PLANK_BROKEN } } ?: return
        val broken = (patterns - setOf(smoothPattern)).iterator()
        for ((row, z) in rows.withIndex()) {
            val pattern = if (row == smooth[set]) smoothPattern else if (broken.hasNext()) broken.next() else return
            for ((i, x) in xs.withIndex()) {
                val now = middle(x, z) ?: continue
                val want = pattern[i]
                if (now.id != want.id || now.angle != want.angle) world.locs.replace(now, want.id, angle = want.angle)
            }
        }
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

/** Client cycles (20 ms) in a 600 ms tick. */
private const val CYCLES_PER_TICK = 30

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
        for (jackie in JACKIE_SHOWN) {
            world.handlers.onNpc(jackie, TALK_TO) { op ->
                world.dialogues.start(this, op.npc) { with(AgilityArenaTalk) { jackie() } }
            }
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
        world.stepHooks += arena::stepped
        // A run stops on a trap, so the jump starts from it and not from the tile past it.
        world.stepStops += { player -> player.level == 3 && !player.scripts.delayed && BrimhavenAgilityArena.inArena(player.x, player.z) && BrimhavenAgilityArena.trapAt(player.x, player.z) != null }
        world.agility.rigs[PLANK] = { player, loc -> arena.plankHolds(player, loc.x, loc.z) }
        world.handlers.onLoc(CLIMBING_ROPE, CLIMB) { op ->
            // Only the pit's ropes (the 242 map has 25, all there): a copy of the type anywhere else does nothing.
            val loc = op.loc ?: return@onLoc
            if (loc.level != 0 || !inArena(loc.x, loc.z)) return@onLoc
            arriveDelay()
            player.anim(Climbing.ANIM_REACH_FOR_LADDER)
            delay(1)
            world.teleport(player, loc.x, loc.z, ARENA_ARRIVAL.level)
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
