package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.map.Tile

/** The lesser Wilderness bosses' lairs' words (keys only; the words are in data/dialogue.txt). */
object WildernessLairLines : Lines("wildy_lair", "Wilderness boss lairs") {
    val PEEK = line("peek", "Peek on a Wilderness boss lair's way in: how many players are inside (no source yet)", "count")
    val PEEK_KILLS = line("peek_kills", "Peek with fewer than 20 kills of the lair's boss (the Wiki's Hunter's End: 20 kills to peek; no words yet)")
    val FEE_PAID = line("fee_paid", "Check-Fee on a lair's way in with the fee paid (no source yet)")
    val FEE_DUE = line("fee_due", "Check-Fee on a lair's way in with the fee still to pay (no source yet)", "fee")
}

/**
 * Artio and her lair, Hunter's End (Wilderness step W4.8; the Wiki's Artio, its Strategies page and Hunter's End, read
 * 2026-11-15). `callisto_singles` 11992, size 3, combat 320, 450 hitpoints, aggressive, every 5 ticks.
 *
 * - **The way in** (`wild_callisto_singles_entrance01` 47141, 3 by 3 at 3115,3676 at level 20): Enter takes the lairs'
 *   fee ([WildernessLairFee]) and puts the player in the north of the den; Peek says how many players are inside, once
 *   the player has 20 kills of her; Check-Fee says whether the fee is paid or what it is. The way out
 *   (`wild_callisto_exit01` 47122 at 1758,11531, the den's south side) puts them outside the way in. The den is map
 *   square 27_180, at level 21. Live asks for the hard Wilderness Diary or a Callisto task; ours is open (Sam, 9 Nov).
 * - **Her fight** is [WildernessBear]'s: her swipe 15 to 35, her crescents up to 17, the knockback and the traps.
 *
 * Her drops are in npc-drops.json and her kill count in `total_artio_kills`; she is a lair boss, so her top damage
 * dealer gets 10,000 off the fee.
 *
 * GUESSED until recorded (the fight's own guesses are [WildernessBear]'s): where Enter and Exit put the player and
 * where she spawns. Left out: singles-plus combat (the den is single-way, so one player fights her at a time), the 3-tick
 * teleport delay on entering, the lair's Slayer and diary checks.
 */
object Artio {
    const val BOSS = 11992
    const val KILL_COUNT_VARP = 3761

    const val ENTRANCE = 47141
    const val EXIT = 47122
    const val ENTER = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20

    /** Where Enter puts the player (the den's north), where Exit does (outside the way in), and where she spawns. */
    val ARRIVAL = Tile(1759, 11549)
    val OUTSIDE = Tile(3116, 3675)
    val SPAWN = Tile(1757, 11535)

    /** The den: its map square. */
    fun inLair(x: Int, z: Int): Boolean = x shr 6 == 27 && z shr 6 == 180

    val FIGHT = WildernessBear(WildernessBear.Config("artio", BOSS, meleeMin = 15, meleeMax = 35, rangedMax = 17, speed = 5, inArena = ::inLair))

    /** The tiles her traps cover now, for tests. */
    fun traps(npc: Npc): Set<Tile> = FIGHT.traps(npc)

    /** Lays a trap on [at] in [npc]'s fight, for tests. */
    internal fun layTrap(npc: Npc, at: Tile) = FIGHT.layTrap(npc, at)
}

fun registerArtio(world: World) {
    with(Artio) {
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, 0)
        }
        world.handlers.onLoc(ENTRANCE, PEEK) {
            arriveDelay()
            if (KILL_COUNT_VARP < player.vars.types.varpCount && player.vars[KILL_COUNT_VARP] < PEEK_KILLS) {
                return@onLoc world.message(player, WildernessLairLines.PEEK_KILLS)
            }
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inLair(it.x, it.z) && it.level == 0 })
        }
        world.handlers.onLoc(ENTRANCE, CHECK_FEE) {
            arriveDelay()
            if (WildernessLairFee.paid(player) || WildernessLairFee.fee(player) == 0) world.message(player, WildernessLairLines.FEE_PAID)
            else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
        }
        // Callisto's den has the same way out, into the Escape Caves.
        world.handlers.onLoc(EXIT, 1) { op ->
            val loc = op.loc!!
            when {
                inLair(loc.x, loc.z) -> {
                    arriveDelay()
                    world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
                }
                Callisto.inDen(loc.x, loc.z) -> {
                    arriveDelay()
                    world.escapeCaves.leaveLair(world, player, Callisto.NAME)
                }
                else -> world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            }
        }
        FIGHT.register(world, WildernessBoss("artio", setOf(BOSS), KILL_COUNT_VARP, lair = true))
    }
}
