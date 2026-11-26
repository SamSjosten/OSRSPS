package osrsps.game.content

import osrsps.game.World
import osrsps.map.Tile

/**
 * Vet'ion and Vet'ion's Rest (Wilderness step W4.13; the Wiki's Vet'ion, its Strategies page, its transcript and Vet'ion's
 * Rest, read 2026-11-15): `vetion` 6611 and his enraged form `vetion_2` 6612, size 3, combat 454, 255 hitpoints a form,
 * every 6 ticks (5 enraged), max hit 44; his hellhounds `vetion_hellhound_jnr` 6613 and, enraged, `vetion_hellhound_snr`
 * 6614. His fight is [SkeletalLord]'s, as Calvar'ion's is, at his numbers. His shouts are Calvar'ion's: the two
 * transcripts have the same words in the same order, so both use [CalvarionLines].
 *
 * - **The way in** (`wild_vetion_entrance01` 46995, the crevice at 3221,3787, level 35): Jump-Down takes the lairs' fee
 *   ([WildernessLairFee]) and puts the player in the rest, map square 51_159 on level 1; Peek and Check-Fee as at every
 *   lair ([WildernessLairLines]). Live asks for the medium Wilderness Diary or a Vet'ion task; ours is open (Sam, 9 Nov).
 * - **The way out** (`wild_vetion_exit01` 46925 at 3294,10190, the rest's south side, the same loc as Calvar'ion's) leads
 *   into the Escape Caves ([EscapeCaves.leaveLair]); Calvar'ion's step registers it for both.
 * - The rest is multi-way (multi-combat.tsv). His drops are in npc-drops.json, split by damage
 *   among up to 10 players ([WildernessBosses]), and his kill count in
 *   `total_vetion_kills`; he is a lair boss, so his top damage dealer gets 10,000 off the fee.
 *
 * GUESSED: where Jump-Down puts the player (the rest's north) and where he spawns (its middle); his enraged max hit
 * [ENRAGED_MAX_HIT] (the Wiki: "his attacks will deal more damage"). Left out: his 90% damage resistance between half
 * his hitpoints and his hounds coming (they come at once here), the lair's Slayer and diary checks.
 */
object Vetion {
    const val NAME = "vetion"
    const val BOSS = 6611
    const val ENRAGED = 6612
    const val HOUND = 6613
    const val GREATER_HOUND = 6614
    const val KILL_COUNT_VARP = 1512

    /** The uniques, at most one a kill ([WildernessBosses]' group loot): skull of Vet'ion, dragon 2h sword, dragon pickaxe, voidwaker blade, ring of the gods. */
    val UNIQUES = setOf(27673, 7158, 11920, 27684, 12601)

    const val ENTRANCE = 46995
    const val JUMP_DOWN = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20
    const val REST_LEVEL = 1

    val ARRIVAL = Tile(3295, 10208)
    val SPAWN = Tile(3294, 10201)

    const val MAX_HIT = 44
    const val ENRAGED_MAX_HIT = 50

    /** The rest: its map square, on level 1. */
    fun inRest(x: Int, z: Int): Boolean = x shr 6 == 51 && z shr 6 == 159

    val FIGHT = SkeletalLord(
        SkeletalLord.Config(
            NAME, BOSS, ENRAGED, HOUND, GREATER_HOUND, MAX_HIT, ENRAGED_MAX_HIT, bashMax = MAX_HIT, speed = 6, enragedSpeed = 5,
            inArena = ::inRest,
            shouts = CalvarionLines.SHOUTS, bash = CalvarionLines.BASH, hounds = CalvarionLines.HOUNDS,
            houndsDead = CalvarionLines.HOUNDS_DEAD, again = CalvarionLines.AGAIN,
            enragedShouts = CalvarionLines.ENRAGED_SHOUTS, enragedBash = CalvarionLines.ENRAGED_BASH,
            enragedHounds = CalvarionLines.ENRAGED_HOUNDS, enragedHoundsDead = CalvarionLines.ENRAGED_HOUNDS_DEAD,
            dying = CalvarionLines.DYING,
        ),
    )
}

fun registerVetion(world: World) {
    with(Vetion) {
        world.handlers.onLoc(ENTRANCE, JUMP_DOWN) {
            arriveDelay()
            with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, REST_LEVEL)
        }
        world.handlers.onLoc(ENTRANCE, PEEK) {
            arriveDelay()
            if (KILL_COUNT_VARP < player.vars.types.varpCount && player.vars[KILL_COUNT_VARP] < PEEK_KILLS) {
                return@onLoc world.message(player, WildernessLairLines.PEEK_KILLS)
            }
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inRest(it.x, it.z) && it.level == REST_LEVEL })
        }
        world.handlers.onLoc(ENTRANCE, CHECK_FEE) {
            arriveDelay()
            // A fee discounted to nothing lets them in unpaid, so it reads as paid, as Calvar'ion's does (Codex review on #997).
            if (WildernessLairFee.paid(player) || WildernessLairFee.fee(player) == 0) world.message(player, WildernessLairLines.FEE_PAID)
            else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
        }
        FIGHT.register(world, WildernessBoss(NAME, setOf(BOSS, ENRAGED), KILL_COUNT_VARP, group = true, uniques = UNIQUES, lair = true))
    }
}
