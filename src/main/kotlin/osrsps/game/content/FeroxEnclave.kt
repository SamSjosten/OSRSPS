package osrsps.game.content

import kotlin.math.abs
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Movement
import osrsps.game.Skill
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Ferox Enclave's words (keys only; the words are in data/dialogue.txt). */
object FeroxEnclaveLines : Lines("ferox", "Ferox Enclave") {
    val POOL = line("pool", "the message on drinking from a Pool of Refreshment")
    val BARRIER_TELEBLOCKED = line("barrier_teleblocked", "Pass-Through on a barrier into the Enclave while Tele Blocked")
}

/**
 * Ferox Enclave (Wilderness step W2.3), the safe town at levels 13 to 16. Inside its walls ([Wilderness.inFerox]) no
 * player can attack another ([osrsps.game.combat.PvpRules] asks [Wilderness.pvpLevel]), and the Wilderness overlay reads
 * "Level: --" (varbit `wildy_hub_buffer`, set with the overlay in [World]).
 *
 * Its four barriers (`wildy_hub_entry_barrier` 39652 and its mirror 39653, two at each entrance) are walls on the safe area's
 * edges in the 242 map. Pass-Through puts the player on the other side, either way; a Tele Blocked player can't come
 * in, but anyone in combat can (the Wiki's Ferox Enclave and Barrier pages). Its two Pools of Refreshment
 * (`wildy_hub_pool` 39651, Drink) restore Hitpoints, Prayer and run energy, set every boosted or drained skill back,
 * cure poison and venom and turn every prayer off, but not special attack energy (the Wiki's Pool of Refreshment page,
 * with its message). The bank chest and the banker's Bank and Collect use Banking.
 *
 * Guessed: the barrier's pass takes a tick with no animation, the pool's drink has none either. Not built: the
 * barrier's warning screen about Tele Block (with its don't-ask-again varbit `wildy_hub_warning`), the safe tiles
 * outside each barrier, disease (not in the game yet), the banker's talk, and every other Ferox NPC's talk, shop and
 * minigame (Clan Wars, Last Man Standing, the loot chest, the respawn point).
 */
object FeroxEnclave {
    const val BARRIER = 39652
    const val BARRIER_MIRROR = 39653
    const val PASS_THROUGH = 1
    const val POOL = 39651
    const val DRINK = 1

    /** wildy_hub_banker; Bank is op3 and Collect op4, as the other bankers. */
    const val BANKER = 10389

    /**
     * Where Pass-Through on [barrier] takes a player standing at [x], [z]: out of the box (to the tile just past the
     * barrier's edge) from anywhere inside it beside the barrier, and into it (onto the barrier's own tile) from anywhere
     * outside beside that outer tile, as wall routing may finish a click on either side's neighbouring tiles; null
     * from anywhere else.
     */
    fun across(barrier: ZoneLoc, x: Int, z: Int): Tile? {
        val (dx, dz) = when (barrier.angle) {
            0 -> -1 to 0
            1 -> 0 to 1
            2 -> 1 to 0
            else -> 0 to -1
        }
        val outside = Tile(barrier.x + dx, barrier.z + dz)
        fun beside(tile: Tile) = abs(x - tile.x) <= 1 && abs(z - tile.z) <= 1
        val inside = Wilderness.inFerox(x, z)
        return when {
            inside && beside(Tile(barrier.x, barrier.z)) -> outside
            !inside && beside(outside) -> Tile(barrier.x, barrier.z)
            else -> null
        }
    }

    /** Whether a Tele Block keeps [player] from crossing to [to]: only inward, into the Enclave. */
    fun blocked(player: Player, to: Tile) = Wilderness.inFerox(to.x, to.z) && player.teleblockTicks > 0

    fun isBarrier(loc: ZoneLoc) = loc.level == 0 && Wilderness.inFerox(loc.x, loc.z)
}

fun registerFeroxEnclave(world: World) {
    for (barrier in listOf(FeroxEnclave.BARRIER, FeroxEnclave.BARRIER_MIRROR)) {
        world.handlers.onLoc(barrier, FeroxEnclave.PASS_THROUGH) { op ->
            val loc = op.loc!!
            val to = if (FeroxEnclave.isBarrier(loc)) FeroxEnclave.across(loc, player.x, player.z) else null
            if (to == null) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            if (FeroxEnclave.blocked(player, to)) return@onLoc world.message(player, FeroxEnclaveLines.BARRIER_TELEBLOCKED)
            arriveDelay()
            delay(1)
            // A Tele Block that landed during the two ticks above still keeps the player out.
            if (FeroxEnclave.blocked(player, to)) return@onLoc world.message(player, FeroxEnclaveLines.BARRIER_TELEBLOCKED)
            world.teleport(player, to.x, to.z, loc.level)
        }
    }

    world.handlers.onLoc(FeroxEnclave.POOL, FeroxEnclave.DRINK) {
        arriveDelay()
        for (skill in Skill.entries) player.skills.setCurrentLevel(skill, player.skills.baseLevel(skill))
        player.runEnergy = Movement.MAX_ENERGY
        world.poison.clear(player)
        world.prayers.deactivateAll(player)
        world.message(player, FeroxEnclaveLines.POOL)
    }

    world.handlers.onNpc(FeroxEnclave.BANKER, Bankers.BANK) { world.bankScreen.open(player) }
    world.handlers.onNpc(FeroxEnclave.BANKER, Bankers.COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }
}
