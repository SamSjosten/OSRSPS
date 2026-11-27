package osrsps.game.content

import osrsps.game.World
import osrsps.map.Tile

/**
 * Venenatis and the Silk Chasm (Wilderness step W4.14; the Wiki's Venenatis, its Strategies page, Venenatis' Spiderling
 * and the Silk Chasm, read 2026-11-15): `venenatis` 6610, size 4, combat 464, 850 hitpoints, aggressive, every 4 ticks.
 * Her fight is [SpiderQueen]'s at her numbers: melee up to 21 for anyone beside her, ranged up to 35 and magic up to 30,
 * two spiderlings (`venenatis_spiderling` 12000) and the sticky web.
 *
 * - **The way in** (`wild_venanatis_entrance01` 47077, 4 by 2 at 3321,3794, level 35): Enter takes the lairs' fee
 *   ([WildernessLairFee]) and puts the player in the chasm, map square 53_159 on level 2; Peek and Check-Fee as at every
 *   lair ([WildernessLairLines]). Live asks for the medium Wilderness Diary or a Venenatis task; ours is open (Sam, 9 Nov).
 * - **The way out** (`wild_venanatis_exit` 47000 at 3422,10183, the end of the chasm's south passage, the
 *   same loc as Spindel's) leads into the Escape Caves ([EscapeCaves.leaveLair]); Spindel's step registers it for both.
 * - The chasm is multi-way (multi-combat.tsv). Her drops are in npc-drops.json, split by damage
 *   among up to 10 players ([WildernessBosses]), and her kill count in
 *   `total_venenatis_kills`; she is a lair boss, so her top damage dealer gets 10,000 off the fee.
 *
 * GUESSED: where Enter puts the player (the chasm's north, which the Strategies page gives, at the middle) and where
 * she spawns (in front of the passage out, as Callisto does). Left out: the lair's Slayer and diary checks, the cannon
 * refusal, the teleport delay on entering.
 */
object Venenatis {
    const val NAME = "venenatis"
    const val BOSS = 6610
    const val SPIDERLING = 12000
    const val KILL_COUNT_VARP = 1511

    /** The uniques, at most one a kill ([WildernessBosses]' group loot): fangs of Venenatis, dragon 2h sword, dragon pickaxe, voidwaker gem, treasonous ring. */
    val UNIQUES = setOf(27670, 7158, 11920, 27687, 12605)

    const val ENTRANCE = 47077
    const val ENTER = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20
    const val CHASM_LEVEL = 2

    val ARRIVAL = Tile(3423, 10212)
    val SPAWN = Tile(3421, 10196)

    /** The chasm: its map square. */
    fun inChasm(x: Int, z: Int): Boolean = x shr 6 == 53 && z shr 6 == 159

    val FIGHT = SpiderQueen(
        SpiderQueen.Config(NAME, BOSS, SPIDERLING, meleeMax = 21, rangedMax = 35, magicMax = 30, speed = 4, spiderlings = 2, inArena = ::inChasm),
    )
}

fun registerVenenatis(world: World) {
    with(Venenatis) {
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, CHASM_LEVEL)
        }
        world.handlers.onLoc(ENTRANCE, PEEK) {
            arriveDelay()
            if (KILL_COUNT_VARP < player.vars.types.varpCount && player.vars[KILL_COUNT_VARP] < PEEK_KILLS) {
                return@onLoc world.message(player, WildernessLairLines.PEEK_KILLS)
            }
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inChasm(it.x, it.z) && it.level == CHASM_LEVEL })
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
