package osrsps.game.content

import osrsps.game.World
import osrsps.map.Tile

/**
 * Callisto and Callisto's Den (Wilderness step W4.12; the Wiki's Callisto, its Strategies page and Callisto's Den, read
 * 2026-11-15): `callisto` 6609, size 5, combat 470, 1,000 hitpoints, aggressive, every 4 ticks. His fight is
 * [WildernessBear]'s at his numbers: his swipe 35 to 55, splashing onto those beside his target; his crescents up to 31;
 * the knockback up to 50 and the traps at 66% and 33%.
 *
 * - **The way in** (`wild_callisto_entrance01` 47140, 5 by 4 at 3291,3849, level 40): Enter takes the lairs' fee
 *   ([WildernessLairFee]) and puts the player in the den, map square 52_161; Peek and Check-Fee as at every lair
 *   ([WildernessLairLines]). Live asks for the medium Wilderness Diary or a Callisto task; ours is open (Sam, 9 Nov).
 * - **The way out** (`wild_callisto_exit01` 47122 at 3358,10315, the den's south side, the same loc as Artio's) leads
 *   into the Escape Caves ([EscapeCaves.leaveLair]); Artio's step registers it for both.
 * - The den is multi-way (multi-combat.tsv). His drops are in npc-drops.json, split by damage
 *   among up to 10 players ([WildernessBosses]), and his kill count in
 *   `total_callisto_kills`; he is a lair boss, so his top damage dealer gets 10,000 off the fee.
 *
 * GUESSED: where Enter puts the player (the den's north; the Wiki says only that players come in there at the other
 * lairs) and where he spawns (in front of the way out, as Artio does). Left out: the lair's Slayer and diary checks,
 * the cannon immunity.
 */
object Callisto {
    const val NAME = "callisto"
    const val BOSS = 6609
    const val KILL_COUNT_VARP = 1510

    /** The uniques, at most one a kill ([WildernessBosses]' group loot): claws of Callisto, dragon 2h sword, dragon pickaxe, voidwaker hilt, tyrannical ring. */
    val UNIQUES = setOf(27667, 7158, 11920, 27681, 12603)

    const val ENTRANCE = 47140
    const val ENTER = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20

    val ARRIVAL = Tile(3359, 10333)
    val SPAWN = Tile(3357, 10322)

    /** The den: its map square, on the ground level. */
    fun inDen(x: Int, z: Int): Boolean = x shr 6 == 52 && z shr 6 == 161

    val FIGHT = WildernessBear(WildernessBear.Config(NAME, BOSS, meleeMin = 35, meleeMax = 55, rangedMax = 31, speed = 4, inArena = ::inDen))
}

fun registerCallisto(world: World) {
    with(Callisto) {
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
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inDen(it.x, it.z) && it.level == 0 })
        }
        world.handlers.onLoc(ENTRANCE, CHECK_FEE) {
            arriveDelay()
            // A fee discounted to nothing lets them in unpaid, so it reads as paid, as at the other lairs (Codex review on #997).
            if (WildernessLairFee.paid(player) || WildernessLairFee.fee(player) == 0) world.message(player, WildernessLairLines.FEE_PAID)
            else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
        }
        FIGHT.register(world, WildernessBoss(NAME, setOf(BOSS), KILL_COUNT_VARP, group = true, uniques = UNIQUES, lair = true))
    }
}
