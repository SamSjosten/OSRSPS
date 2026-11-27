package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** The Revenant Caves' words (keys only; the words are in data/dialogue.txt). */
object RevenantCavesLines : Lines("revenant_caves", "Revenant Caves") {
    val TITLE = line("title", "the menu before a way into the Revenant Caves takes the fee (no source yet)", "fee")
    val YES = line("yes", "the fee menu's option that pays and goes in (no source yet)")
    val NO = line("no", "the fee menu's option that doesn't (no source yet)")
    val NO_COINS = line("no_coins", "a way in with fewer coins than the fee in the backpack and in the bank (no source yet)", "fee")
}

/**
 * Where combat is singles-plus (OSRS Wiki, Singles-plus combat, read 2026-11-15): single-way, "with the exception that
 * if a player is in combat with a monster, another player attacking will take priority over fighting the monster", so
 * a monster's fight bars no PvP attack ([osrsps.game.combat.PvpCombat.singleWayRefusal]). The client's single sword and
 * plus icon is `singleway_plus_indicator`, which the world sets each tick. The Wiki's list: the Revenant Caves, the
 * Wilderness Agility Course, Hunter's End, the Skeletal Tomb and the Web Chasm; only the caves are in so far (the
 * course comes with step W6.4; the three lairs were built single-way).
 *
 * INFERRED: a monster still fights only one player at a time there, as in single-way combat.
 */
object SinglesPlus {
    const val VARBIT_INDICATOR = 5961

    fun at(level: Int, x: Int, z: Int): Boolean = level == 0 && RevenantCaves.inCaves(x, z)
}

/**
 * The Revenant Caves, levels 17 to 40 (Wilderness step W5.1; the Wiki's Revenant Caves, Cavern (Revenant Caves),
 * Crevice (Revenant Caves), Stairs (Revenant Caves), Trapdoor (Revenant Caves) and Singles-plus combat, read
 * 2026-11-15). Members only, all singles-plus ([SinglesPlus]).
 *
 * - **Three one-way ways in**, each taking the fee first ([payToEnter]): the level 17 cavern (`wild_cave_entrance_low`
 *   31555, 4 by 2 at 3073,3654, Enter), the level 26 crevice (`wild_cave_entrance_mid` 40386 at 3067,3740, Jump-Down)
 *   and the level 40 cavern (`wild_cave_entrance_high` 31556 at 3124,3831, Enter).
 * - **Two one-way ways out**: the southern stairs (`wild_cave_exit_low` 31558 at 3218,10058) to the trapdoor in level
 *   17 and the northern ones (`wild_cave_exit_high` 43868 at 3244,10215) to the trapdoor in level 36; the trapdoors
 *   (31557) have no option. Leaving by the stairs costs no second fee (the Wiki).
 * - **The fee** (the Wiki's Revenant Caves page): 100,000 coins before going in, taken from the bank if not carried,
 *   paid once until the player dies in the caves or is killed by a player anywhere in the Wilderness; a player killer
 *   gets the coins as loot. A PvP death [forfeit]s it, coins to the killer, and any other death in the caves [lose]s it.
 *
 * Where each way in and out puts the player is from RuneLite's Shortest Path plugin's transports (its data, read
 * 2026-11-15): 3197,10056, 3187,10127 and 3241,10233 in; 3102,3655 and 3124,3805 out.
 *
 * GUESSED: the menu before paying and its words, as at the boss lairs ([WildernessLairFee]); the fee from the backpack
 * when it holds it all, else from the bank; whether it is paid kept in a varp of our own ([CustomVars.revenantFeeVarp]).
 * Not built: the members check, the 2-tick teleport delay in combat with a revenant, a death to a revenant counting as
 * a PvP death (items seen by everyone), the cannon refusal, the crevice's one-way warning. The caves' Agility pillars
 * are obstacles in skills/agility.json (step W5.1b).
 */
object RevenantCaves {
    const val FEE = 100_000
    const val COINS = WildernessLairFee.COINS

    const val CAVERN_LOW = 31555
    const val CREVICE = 40386
    const val CAVERN_HIGH = 31556
    const val STAIRS_LOW = 31558
    const val STAIRS_HIGH = 43868
    const val ENTER = 1
    const val CLIMB_UP = 1

    val CAVERN_LOW_ARRIVAL = Tile(3197, 10056)
    val CREVICE_ARRIVAL = Tile(3187, 10127)
    val CAVERN_HIGH_ARRIVAL = Tile(3241, 10233)
    val STAIRS_LOW_EXIT = Tile(3102, 3655)
    val STAIRS_HIGH_EXIT = Tile(3124, 3805)

    /** The caves: map squares 49,157 to 50,159 on the ground level, which hold nothing else (the 242 cache's map). */
    fun inCaves(x: Int, z: Int): Boolean = x in 3136..3263 && z in 10048..10239

    private fun varp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.REVENANT_FEE]

    fun paid(player: Player): Boolean = varp(player)?.let { player.vars[it] != 0 } ?: false

    private fun setPaid(player: Player, paid: Boolean) {
        val varp = varp(player) ?: return
        player.vars[varp] = if (paid) 1 else 0
    }

    /**
     * A way in: true when [player] may go in, the fee paid already or paid now, after a menu, from the backpack or the
     * bank; false (nothing taken) when they say no or can't pay.
     */
    suspend fun ScriptContext.payToEnter(world: World): Boolean {
        if (paid(player)) return true
        if (!WildernessLairFee.canPay(player, FEE)) {
            world.message(player, RevenantCavesLines.NO_COINS, "fee" to FEE)
            return false
        }
        var yes = false
        world.dialogues.start(this) {
            yes = choice(RevenantCavesLines.YES, RevenantCavesLines.NO, title = text(RevenantCavesLines.TITLE, "fee" to FEE)) == 1
        }
        if (!yes) return false
        if (!WildernessLairFee.pay(world, player, FEE)) {
            world.message(player, RevenantCavesLines.NO_COINS, "fee" to FEE)
            return false
        }
        setPaid(player, true)
        return true
    }

    /** [player] died to another player, anywhere: the fee, if paid, is lost and its coins go to the killer; returns how many (0 for none). */
    fun forfeit(player: Player): Int {
        if (!paid(player)) return 0
        setPaid(player, false)
        return FEE
    }

    /** [player] died with no player killer: in the caves, the fee is lost. */
    fun lose(player: Player) {
        if (player.level == 0 && inCaves(player.x, player.z)) setPaid(player, false)
    }
}

fun registerRevenantCaves(world: World) {
    with(RevenantCaves) {
        for ((loc, arrival) in listOf(CAVERN_LOW to CAVERN_LOW_ARRIVAL, CREVICE to CREVICE_ARRIVAL, CAVERN_HIGH to CAVERN_HIGH_ARRIVAL)) {
            world.handlers.onLoc(loc, ENTER) {
                arriveDelay()
                if (!payToEnter(world)) return@onLoc
                world.teleport(player, arrival.x, arrival.z, 0)
            }
        }
        for ((loc, exit) in listOf(STAIRS_LOW to STAIRS_LOW_EXIT, STAIRS_HIGH to STAIRS_HIGH_EXIT)) {
            world.handlers.onLoc(loc, CLIMB_UP) {
                arriveDelay()
                world.teleport(player, exit.x, exit.z, 0)
            }
        }
    }
}
