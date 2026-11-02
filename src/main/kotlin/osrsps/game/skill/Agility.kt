package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.PlayerEffects
import osrsps.game.Skill
import osrsps.game.Stance
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile
import kotlin.math.abs
import kotlin.math.sign

/** Agility's words, in the dialogue file. */
object AgilityLines : Lines("agility", "Agility") {
    val LEVEL = line("level", "the message when an obstacle needs a higher Agility level", "level")
}

/** A tile on a level, as obstacles name them. */
data class AgilityTile(val x: Int, val z: Int, val level: Int)

/** One part of crossing an obstacle, done in order; see agility.json's `about`. */
sealed interface AgilityMove {
    /** Plays [anim] (-1 for none) and waits [ticks] ticks where the player stands. */
    data class Anim(val anim: Int, val ticks: Int) : AgilityMove

    /** Puts the player straight on [to], another level too (a climb's end): no walking and no wait. */
    data class Teleport(val to: AgilityTile) : AgilityMove

    /**
     * Walks the player along [path] one tile a tick, through whatever is in the way, with [anim] as every walking and
     * standing animation of their stance meanwhile (a log balance's, a tightrope's).
     */
    data class Walk(val path: List<Tile>, val anim: Int) : AgilityMove

    /**
     * Slides the player from where they stand to [to] (the client's exact move), starting [startCycles] client cycles
     * (20 ms) after the update and arriving at [endCycles], with [anim]; [ticks] ticks later they stand on [to].
     */
    data class Slide(val to: AgilityTile, val anim: Int, val startCycles: Int, val endCycles: Int, val ticks: Int) : AgilityMove
}

/**
 * One way across an obstacle: the tile the player starts from, what crossing does, and, for an obstacle that can be
 * failed, what a failure does instead ([fail], empty for one that can't).
 */
class AgilityRoute(val from: AgilityTile, val moves: List<AgilityMove>, val fail: List<AgilityMove>)

/**
 * An obstacle: the loc and its option (by the start of the cache's option text), the Agility level and XP in tenths,
 * where it stands when several locs of the same type are different obstacles ([at], null when there is one), its ways
 * across ([routes], the nearest to the player is taken), and for one that can fail its success pair ([success], out of
 * 256 as [SuccessRoll]'s) and the damage a failure deals ([damage]).
 */
class Obstacle(
    val key: String,
    val loc: Int,
    val option: String,
    val level: Int,
    val xpTenths: Int,
    val at: AgilityTile?,
    val routes: List<AgilityRoute>,
    val success: Pair<Int, Int>?,
    val damage: IntRange,
) {
    init {
        require(routes.isNotEmpty()) { "Obstacle $key has no route" }
        require((success == null) == routes.all { it.fail.isEmpty() }) { "Obstacle $key needs both a success pair and fail moves, or neither" }
    }
}

/** agility.json: every obstacle. */
class AgilityData(val obstacles: List<Obstacle>) {
    /** By loc type: those that share one are told apart by [Obstacle.at]. */
    val byLoc: Map<Int, List<Obstacle>> = obstacles.groupBy { it.loc }

    init {
        require(obstacles.map { it.key }.toSet().size == obstacles.size) { "An obstacle key is listed twice" }
        for ((loc, list) in byLoc) {
            require(list.size == 1 || list.all { it.at != null }) { "Loc $loc is several obstacles, so each needs \"at\"" }
        }
    }

    companion object {
        const val RESOURCE = "/skills/agility.json"

        fun load(): AgilityData {
            val text = AgilityData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): AgilityData {
            val root = Json.parseToJsonElement(text).jsonObject
            return AgilityData(root.getValue("obstacles").jsonArray.map { obstacle(it.jsonObject) })
        }

        private fun obstacle(json: JsonObject): Obstacle {
            val key = json.getValue("key").jsonPrimitive.content
            val success = json["success"]?.jsonArray?.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int }
            val damage = json["damage"]?.jsonArray?.let { it[0].jsonPrimitive.int..it[1].jsonPrimitive.int } ?: IntRange.EMPTY
            val routes = json.getValue("routes").jsonArray.map { it.jsonObject }.map { route ->
                AgilityRoute(
                    tile(route.getValue("from").jsonArray),
                    route.getValue("moves").jsonArray.map { move(key, it.jsonObject) },
                    route["fail"]?.jsonArray?.map { move(key, it.jsonObject) } ?: emptyList(),
                )
            }
            return Obstacle(
                key, json.int("loc"), json.getValue("option").jsonPrimitive.content, json.int("level"), json.int("xpTenths"),
                json["at"]?.jsonArray?.let(::tile), routes, success, damage,
            )
        }

        private fun move(key: String, json: JsonObject): AgilityMove = when (json.getValue("type").jsonPrimitive.content) {
            "anim" -> AgilityMove.Anim(json.int("anim"), json.int("ticks"))
            "teleport" -> AgilityMove.Teleport(tile(json.getValue("to").jsonArray))
            "walk" -> AgilityMove.Walk(
                json.getValue("path").jsonArray.map { it.jsonArray.let { tile -> Tile(tile[0].jsonPrimitive.int, tile[1].jsonPrimitive.int) } },
                json.int("anim"),
            )
            "slide" -> AgilityMove.Slide(
                tile(json.getValue("to").jsonArray), json.int("anim"), json.int("startCycles"), json.int("endCycles"), json.int("ticks"),
            )
            else -> error("Obstacle $key has a move of unknown type ${json["type"]}")
        }

        private fun tile(array: JsonArray) = AgilityTile(array[0].jsonPrimitive.int, array[1].jsonPrimitive.int, array[2].jsonPrimitive.int)

        private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    }
}

/**
 * Agility obstacles: the option on an obstacle's loc walks the player to where it starts, checks the level, then plays
 * its moves while the player is delayed (clicks do nothing until it ends, see [osrsps.game.script.ScriptRunner]) and
 * gives the XP when they are across. One that can fail rolls its success pair against the current Agility level
 * first; a failure plays the fail moves instead (a fall) and deals a hit of its damage, with no XP.
 *
 * Sources: the OSRS Wiki's Agility page (read 2026-11-02) for XP coming on completing an obstacle, nothing for a
 * failure, and some obstacles failing with damage while others can't; each course's page gives the levels, XP and
 * damage, which agility.json holds with the courses. Inferred: the player walks to the obstacle's start by themselves
 * once they click it and can't be turned back on the way, as on live; the level is checked on arrival. Guessed: the
 * level message is a game message, not a box; the fail chance (the Wiki gives none, so each pair is guessed in the data
 * file); the hit coming after the fail moves; and when the real tile moves on a slide (see [slide]).
 */
class Agility(private val world: World, val data: AgilityData) {
    fun register() {
        for ((locId, obstacles) in data.byLoc) {
            world.handlers.onLoc(locId, option(locId, obstacles.first().option)) { op -> cross(op, obstacles) }
        }
    }

    /** The loc's option that starts with [name] (Climb, Walk-on, Jump), 1 to 5; 1 when the cache doesn't say (tests). */
    private fun option(locId: Int, name: String): Int {
        val ops = world.locs.types[locId]?.ops ?: return 1
        val index = ops.indexOfFirst { it != null && it.startsWith(name, ignoreCase = true) }
        return if (index >= 0) index + 1 else 1
    }

    private suspend fun ScriptContext.cross(op: Op, obstacles: List<Obstacle>) {
        val loc = op.loc ?: return
        val obstacle = obstacleAt(obstacles, loc) ?: return
        arriveDelay()
        if (player.skills.currentLevel(Skill.AGILITY) < obstacle.level) {
            player.message(world.textFor(player)[AgilityLines.LEVEL, "level" to obstacle.level])
            return
        }
        val route = obstacle.routes.filter { it.from.level == player.level }.minByOrNull { distance(it.from) } ?: return
        if (!walkTo(route.from)) return
        val success = obstacle.success?.let { (low, high) ->
            SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.AGILITY))
        } ?: true
        for (move in if (success) route.moves else route.fail) play(move)
        if (success) {
            player.skills.addTenths(Skill.AGILITY, obstacle.xpTenths)
        } else if (!obstacle.damage.isEmpty()) {
            val damage = world.random.between(obstacle.damage.first, obstacle.damage.last)
            world.meleeCombat.statusHit(player, damage, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
        }
    }

    /** The obstacle [loc] is: the only one of its type, or the one standing on its tile. */
    private fun obstacleAt(obstacles: List<Obstacle>, loc: ZoneLoc): Obstacle? =
        obstacles.singleOrNull()?.takeIf { it.at == null } ?: obstacles.firstOrNull { it.at == AgilityTile(loc.x, loc.z, loc.level) }

    private fun ScriptContext.distance(tile: AgilityTile) = maxOf(abs(tile.x - player.x), abs(tile.z - player.z))

    /**
     * Walks the player to [tile] along the route finder's path, delayed so a click can't turn them back. False, and
     * they stay put, when there is no way there.
     */
    private suspend fun ScriptContext.walkTo(tile: AgilityTile): Boolean {
        if (player.x == tile.x && player.z == tile.z) return true
        val route = world.findRoute(player, tile.x, tile.z)
        if (route.alternative || route.destination != Tile(tile.x, tile.z)) return false
        val steps = steps(Tile(player.x, player.z), route.waypoints)
        forcedWalk(steps)
        return true
    }

    /**
     * Hands [steps] to the movement phase and waits until they are walked. A loop rather than a count of ticks: a script
     * started in the movement phase (an obstacle clicked from beside it) sets them after that tick's step.
     */
    private suspend fun ScriptContext.forcedWalk(steps: List<Tile>) {
        player.waypoints.clear()
        player.forcedSteps.clear()
        player.forcedSteps.addAll(steps)
        while (player.forcedSteps.isNotEmpty()) delay(1)
    }

    private suspend fun ScriptContext.play(move: AgilityMove) {
        when (move) {
            is AgilityMove.Anim -> {
                player.anim(move.anim)
                if (move.ticks > 0) delay(move.ticks)
            }
            is AgilityMove.Teleport -> world.teleport(player, move.to.x, move.to.z, move.to.level)
            is AgilityMove.Walk -> walk(move)
            is AgilityMove.Slide -> slide(move)
        }
    }

    private suspend fun ScriptContext.walk(move: AgilityMove.Walk) {
        player.stanceOverride = Stance(move.anim, move.anim, move.anim, move.anim, move.anim, move.anim, move.anim)
        try {
            forcedWalk(steps(Tile(player.x, player.z), move.path))
        } finally {
            // The stance goes back even if the walk is cut short (a logout).
            player.stanceOverride = null
        }
    }

    /**
     * The client's exact move from where the player stands to [move]'s tile. Guessed: the player's real tile changes
     * when the slide's ticks are up, so the client keeps showing them where the slide ends until then; RSProt notes the
     * client goes back to the real tile once the move is over.
     */
    private suspend fun ScriptContext.slide(move: AgilityMove.Slide) {
        val angle = angle(move.to.x - player.x, move.to.z - player.z)
        player.effects.exactMove(PlayerEffects.ExactMove(player.x, player.z, move.to.x, move.to.z, move.startCycles, move.endCycles, angle))
        player.anim(move.anim)
        delay(move.ticks)
        world.teleport(player, move.to.x, move.to.z, move.to.level)
    }

    companion object {
        /**
         * The tiles from [start] through each of [waypoints] in turn, one step at a time (straight or diagonal), not
         * counting [start].
         */
        fun steps(start: Tile, waypoints: List<Tile>): List<Tile> {
            val steps = ArrayList<Tile>()
            var x = start.x
            var z = start.z
            for (waypoint in waypoints) {
                while (x != waypoint.x || z != waypoint.z) {
                    x += (waypoint.x - x).sign
                    z += (waypoint.z - z).sign
                    steps += Tile(x, z)
                }
            }
            return steps
        }

        /**
         * The client's angle for facing along [dx], [dz]: 0 south, 512 west, 1024 north, 1536 east, the diagonals between.
         * A slide that goes nowhere faces south.
         */
        fun angle(dx: Int, dz: Int): Int = when {
            dx == 0 && dz == 0 -> 0
            else -> {
                // atan2 with south as 0 and west as a quarter turn, in 2048ths of a turn.
                val turns = Math.atan2(-dx.toDouble(), -dz.toDouble()) / (2 * Math.PI)
                Math.floorMod(Math.round(turns * 2048).toInt(), 2048)
            }
        }
    }
}
