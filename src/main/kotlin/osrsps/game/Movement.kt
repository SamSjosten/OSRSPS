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
 * the article tells 64.1 kg from 64; the client is only shown whole kilograms. Agility is 1 until the skill is built.
 */
class Movement(private val collision: CollisionMap) {
    private val routeFinder = RouteFinder(collision)

    /** Replaces [player]'s route with one to [destX], [destZ]. [invertRun] is the ctrl key. */
    fun request(player: Player, destX: Int, destZ: Int, invertRun: Boolean): Route =
        request(player, RouteTarget.tile(destX, destZ), invertRun)

    /** Replaces [player]'s route with one to wherever reaches [target]. */
    fun request(player: Player, target: RouteTarget, invertRun: Boolean): Route {
        val route = routeFinder.find(player.level, player.x, player.z, target)
        player.waypoints.clear()
        player.waypoints.addAll(route.waypoints)
        player.invertRun = invertRun
        return route
    }

    fun process(player: Player): MoveResult {
        val wantsRun = player.runEnabled != player.invertRun
        // Inferred: a delayed player keeps their route but doesn't walk it until the delay ends. Energy still changes.
        val steps = if (player.scripts.delayed) 0 else if (wantsRun && player.runEnergy > 0) 2 else 1
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

    private fun updateEnergy(player: Player, result: MoveResult) {
        // Guessed: only a two-tile tick counts as running, and a single tile at the end of a run drains nothing.
        if (result == MoveResult.RAN) {
            val grams = player.weightGrams.coerceIn(0, MAX_WEIGHT_GRAMS).toLong()
            val drain = ((60L * MAX_WEIGHT_GRAMS + 67 * grams) * (300 - AGILITY) / (MAX_WEIGHT_GRAMS * 300L)).toInt()
            player.runEnergy = (player.runEnergy - drain).coerceAtLeast(0)
            // Guessed: run switches itself off when energy runs out, as the orb does on live.
            if (player.runEnergy == 0) player.runEnabled = false
        } else {
            player.runEnergy = (player.runEnergy + AGILITY / 10 + 15).coerceAtMost(MAX_ENERGY)
        }
    }

    companion object {
        const val MAX_ENERGY = 10_000
        private const val AGILITY = 1

        /** 64 kg, past which weight drains no faster. */
        private const val MAX_WEIGHT_GRAMS = 64_000
    }
}
