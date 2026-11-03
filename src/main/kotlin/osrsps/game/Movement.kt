package osrsps.game

import osrsps.map.CollisionMap
import osrsps.map.Route
import osrsps.map.RouteFinder
import osrsps.map.RouteTarget
import osrsps.map.Steps
import osrsps.map.Tile
import kotlin.math.sign

/** What a player's movement did this tick. */
enum class MoveResult { NONE, WALKED, RAN }

/**
 * Routes players to clicked tiles and moves them along the route: one tile a tick walking, two running.
 *
 * Run energy follows the OSRS Wiki "Energy" article: running drains ⌊(60 + 67 × weight / 64) × (1 − agility / 300)⌋
 * units a tick, and any tick spent not running restores ⌊agility / 10⌋ + 15. Weight is [Player.weightGrams] in
 * kilograms, counted from 0 to 64 (lighter or heavier has no more effect). Inferred: the weight's fractions count, as
 * the article tells 64.1 kg from 64; the client is only shown whole kilograms. Inferred: the agility is the current
 * (boosted) level, as the article says "Agility level" and a boost counts for Agility's other checks. Graceful pieces make
 * the restore faster ([Graceful], [Player.restoreBonusPercent]).
 *
 * An obstacle's [Player.forcedSteps] come first: one tile a tick, through anything in the way and while the player is
 * delayed, which the obstacle is for ([osrsps.game.skill.Agility]). They count as walking for energy.
 */
class Movement(private val collision: CollisionMap) {
    private val routeFinder = RouteFinder(collision)

    /** Replaces [player]'s route with one to [destX], [destZ]. [invertRun] is the ctrl key. */
    fun request(player: Player, destX: Int, destZ: Int, invertRun: Boolean): Route =
        request(player, RouteTarget.tile(destX, destZ), invertRun)

    /** Replaces [player]'s route with one to wherever reaches [target]. */
    fun request(player: Player, target: RouteTarget, invertRun: Boolean): Route =
        find(player, target).also { follow(player, it, invertRun) }

    /** A route from where [player] stands to wherever reaches [target], without walking it. */
    fun find(player: Player, target: RouteTarget): Route = routeFinder.find(player.level, player.x, player.z, target)

    /** Replaces [player]'s route with [route]. */
    fun follow(player: Player, route: Route, invertRun: Boolean) {
        player.waypoints.clear()
        player.waypoints.addAll(route.waypoints)
        player.invertRun = invertRun
    }

    fun process(player: Player): MoveResult {
        // A dead player goes nowhere, even partway along an obstacle.
        if (player.dead) player.forcedSteps.clear()
        // Counted down by the world at the tick's start, so the walk and the attack delay end on the same tick.
        val weary = player.wearyTicks > 0
        if (player.forcedSteps.isNotEmpty()) {
            // A teleport this tick (a climb onto a roof) lands first; the walk starts on the next tick.
            if (player.teleported) {
                updateEnergy(player, MoveResult.NONE)
                return MoveResult.NONE
            }
            val to = player.forcedSteps.removeFirst()
            player.lastStepFrom = Tile(player.x, player.z)
            // One tile at most, so a step can't carry the player off along a gap in the list.
            player.x += (to.x - player.x).sign
            player.z += (to.z - player.z).sign
            updateEnergy(player, MoveResult.WALKED)
            return MoveResult.WALKED
        }
        val wantsRun = player.runEnabled != (player.invertRun && ctrlInverts(player))
        // Inferred: a delayed player keeps their route but doesn't walk it until the delay ends. Energy still changes.
        // Weary, they walk whatever the run setting says.
        val steps = if (player.scripts.delayed) 0 else if (wantsRun && player.runEnergy > 0 && !weary) 2 else 1
        var taken = 0
        while (taken < steps && step(player)) taken++
        val result = when (taken) {
            0 -> MoveResult.NONE
            1 -> MoveResult.WALKED
            else -> MoveResult.RAN
        }
        updateEnergy(player, result)
        return result
    }

    /** Takes one step toward the next waypoint. False when there is nowhere to go or the way is blocked. */
    private fun step(player: Player): Boolean {
        val target = player.waypoints.firstOrNull() ?: return false
        val dx = (target.x - player.x).sign
        val dz = (target.z - player.z).sign
        if (!canStep(player.level, player.x, player.z, dx, dz)) {
            // The map changed under the route (a door closed); stop like the server does.
            player.waypoints.clear()
            return false
        }
        player.lastStepFrom = Tile(player.x, player.z)
        player.x += dx
        player.z += dz
        if (player.x == target.x && player.z == target.z) player.waypoints.removeFirst()
        return true
    }

    /** The same checks the route finder makes for one step. */
    fun canStep(level: Int, x: Int, z: Int, dx: Int, dz: Int): Boolean = Steps.canStep(collision, level, x, z, dx, dz)

    /**
     * Whether ctrl flips the run mode the player has now, by the All Settings dropdown "Ctrl+click to invert run mode"
     * (varbit runinvert_mode, its options from enum 4074): Never, Walk -> Run only, Run -> Walk only, Always. Without
     * the varbit (tests with no cache) ctrl always flips, as before the setting.
     */
    private fun ctrlInverts(player: Player): Boolean {
        if (player.vars.types.varbit(VARBIT_RUN_INVERT_MODE) == null) return true
        return when (player.vars.getVarbit(VARBIT_RUN_INVERT_MODE)) {
            RUN_INVERT_NEVER -> false
            RUN_INVERT_WALK_TO_RUN -> !player.runEnabled
            RUN_INVERT_RUN_TO_WALK -> player.runEnabled
            else -> true
        }
    }

    /**
     * The energy, in hundredths of a percent, at which run comes back on after running out, from the All Settings
     * number "Energy threshold to re-enable running" (varbit runenergy_autoenable, a percentage; 0 turns it off). Its
     * description in the cache: after energy has "naturally depleted, you will automatically start running again once
     * you have this much energy". Guessed: values over 100 count as 100, as the orb can't go higher.
     */
    private fun autoEnableAt(player: Player): Int {
        if (player.vars.types.varbit(VARBIT_RUN_AUTOENABLE) == null) return 0
        return player.vars.getVarbit(VARBIT_RUN_AUTOENABLE).coerceAtMost(100) * (MAX_ENERGY / 100)
    }

    private fun updateEnergy(player: Player, result: MoveResult) {
        // Run turned back on by any other way (the orb, the side panel) isn't waiting on the threshold any more.
        if (player.runEnabled) player.runRanOut = false
        // Guessed: only a two-tile tick counts as running, and a single tile at the end of a run drains nothing.
        val agility = player.skills.currentLevel(Skill.AGILITY)
        if (result == MoveResult.RAN) {
            val grams = player.weightGrams.coerceIn(0, MAX_WEIGHT_GRAMS).toLong()
            val drain = ((60L * MAX_WEIGHT_GRAMS + 67 * grams) * (300 - agility) / (MAX_WEIGHT_GRAMS * 300L)).toInt()
            player.runEnergy = (player.runEnergy - drain).coerceAtLeast(0)
        }
        // Guessed: run switches itself off when energy runs out, as the orb does on live; also when it is turned on
        // with none left. The setter clears runRanOut, so it is set after.
        if (player.runEnabled && player.runEnergy == 0) {
            player.runEnabled = false
            player.runRanOut = true
        }
        // Every tick that isn't a run restores energy, the one that turned run off too.
        if (result != MoveResult.RAN) {
            val restore = Graceful.boosted(agility / 10 + 15, player.restoreBonusPercent)
            player.runEnergy = (player.runEnergy + restore).coerceAtMost(MAX_ENERGY)
            val threshold = autoEnableAt(player)
            if (player.runRanOut && threshold > 0 && player.runEnergy >= threshold) {
                player.runEnabled = true
            }
        }
    }

    companion object {
        const val MAX_ENERGY = 10_000

        /** runinvert_mode and its values, enum 4074's order. */
        const val VARBIT_RUN_INVERT_MODE = 13132
        const val RUN_INVERT_NEVER = 0
        const val RUN_INVERT_WALK_TO_RUN = 1
        const val RUN_INVERT_RUN_TO_WALK = 2
        const val RUN_INVERT_ALWAYS = 3

        /** runenergy_autoenable, the percentage of energy that turns run back on after it ran out. */
        const val VARBIT_RUN_AUTOENABLE = 11031

        /** 64 kg, past which weight drains no faster. */
        private const val MAX_WEIGHT_GRAMS = 64_000
    }
}
