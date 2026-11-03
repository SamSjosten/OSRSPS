package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjType
import osrsps.game.PlayerEffects
import osrsps.game.Skill
import osrsps.game.Stance
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile
import kotlin.math.abs
import kotlin.math.sign

/** Agility's words, in the dialogue file. */
object AgilityLines : Lines("agility", "Agility") {
    val LEVEL = line("level", "the message when an obstacle with no level line of its own needs a higher Agility level", "level")
    val DRAYNOR_LAPS = line("draynor_laps", "Draynor Village Rooftop Course: the lap count message after each lap", "count")
    val ALKHARID_LAPS = line("alkharid_laps", "Al Kharid Rooftop Course: the lap count message after each lap", "count")
    val ALKHARID_LEVEL = line("alkharid_level", "Al Kharid Rooftop Course: an obstacle clicked below level 20", "level")
    val VARROCK_LAPS = line("varrock_laps", "Varrock Rooftop Course: the lap count message after each lap", "count")
    val VARROCK_LEVEL = line("varrock_level", "Varrock Rooftop Course: an obstacle clicked below level 30", "level")
    val GNOME_LAPS = line("gnome_laps", "Gnome Stronghold Agility Course: the lap count message after each lap", "count")

    /** A line by its key, for agility.json to name one. */
    fun byKey(key: String): Line = lines.firstOrNull { it.key == key } ?: error("No agility line $key")
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
 * One way across an obstacle: the tile the player starts from, what crossing does, for an obstacle that can be failed
 * what a failure does instead ([fail], empty for one that can't), and where the obstacle is one of several locs, the
 * tiles of the locs this route is for ([at], empty for every loc).
 */
class AgilityRoute(val from: AgilityTile, val moves: List<AgilityMove>, val fail: List<AgilityMove>, val at: List<AgilityTile> = emptyList())

/**
 * An obstacle: the loc and its option (by the start of the cache's option text), any other loc types that are the same
 * obstacle ([alsoLocs], as the Gnome Stronghold's two pipes), the Agility level and XP in tenths, where it stands when
 * several locs of the same type are different obstacles ([at], null when there is one), its ways across ([routes]: of
 * those for the loc clicked, the one starting nearest the player is taken), and for one that can fail its success pair ([success], out of 256 as
 * [SuccessRoll]'s) and the damage a failure deals ([damage]).
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
    /** What the player is told below [level]; [AgilityLines.LEVEL] for an obstacle with no line of its own. */
    val levelLine: Line = AgilityLines.LEVEL,
    val alsoLocs: List<Int> = emptyList(),
) {
    /** Every loc type that is this obstacle. */
    val locs: List<Int> get() = listOf(loc) + alsoLocs

    init {
        require(routes.isNotEmpty()) { "Obstacle $key has no route" }
        require(loc !in alsoLocs && alsoLocs.toSet().size == alsoLocs.size) { "Obstacle $key lists a loc twice" }
        require((success == null) == routes.all { it.fail.isEmpty() }) { "Obstacle $key needs both a success pair and fail moves, or neither" }
    }
}

/**
 * A course: its [obstacles] in order, the XP for a whole lap on top of theirs ([lapXpTenths], 0 where the last obstacle's
 * XP already holds it, as on the rooftops) and the message with the lap count ([lapLine]). Courses with marks of grace
 * give their Agility [level], the tiles a mark can land on ([markSpots]) and the chance a lap gives one ([markChance],
 * a numerator and denominator); see [Agility.rollMark].
 */
class AgilityCourse(
    val key: String,
    val obstacles: List<Obstacle>,
    val lapXpTenths: Int,
    val lapLine: Line,
    val level: Int = 1,
    val markSpots: List<AgilityTile> = emptyList(),
    val markChance: Pair<Int, Int> = 1 to 3,
) {
    init {
        require(obstacles.size in 1..MAX_OBSTACLES) { "Course $key has ${obstacles.size} obstacles, 1 to $MAX_OBSTACLES" }
        require(markChance.second >= 1 && markChance.first in 0..markChance.second) { "Course $key has a mark chance of $markChance" }
    }

    /** The [Charges] counters: laps done, and which obstacles of this lap are crossed (a bit each). */
    val lapsCounter: String get() = "agility_laps_$key"
    val progressCounter: String get() = "agility_lap_$key"

    companion object {
        /** The progress bits fit in a count. */
        const val MAX_OBSTACLES = 30
    }
}

/** agility.json: every obstacle and course. */
class AgilityData(val obstacles: List<Obstacle>, val courses: List<AgilityCourse> = emptyList()) {
    /** By loc type: those that share one are told apart by [Obstacle.at]. */
    val byLoc: Map<Int, List<Obstacle>> = obstacles.flatMap { obstacle -> obstacle.locs.map { it to obstacle } }.groupBy({ it.first }, { it.second })

    /** The course each course obstacle is on, and where in it. */
    val courseOf: Map<Obstacle, Pair<AgilityCourse, Int>> =
        courses.flatMap { course -> course.obstacles.mapIndexed { index, obstacle -> obstacle to (course to index) } }.toMap()

    init {
        require(obstacles.map { it.key }.toSet().size == obstacles.size) { "An obstacle key is listed twice" }
        for ((loc, list) in byLoc) {
            require(list.size == 1 || list.all { it.at != null }) { "Loc $loc is several obstacles, so each needs \"at\"" }
        }
        require(courses.map { it.key }.toSet().size == courses.size) { "A course key is listed twice" }
        require(courseOf.size == courses.sumOf { it.obstacles.size }) { "An obstacle is on two courses, or twice on one" }
    }

    companion object {
        const val RESOURCE = "/skills/agility.json"

        fun load(): AgilityData {
            val text = AgilityData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): AgilityData {
            val root = Json.parseToJsonElement(text).jsonObject
            val obstacles = root.getValue("obstacles").jsonArray.map { obstacle(it.jsonObject) }
            val byKey = obstacles.associateBy { it.key }
            val courses = root["courses"]?.jsonArray.orEmpty().map { it.jsonObject }.map { course ->
                val key = course.getValue("key").jsonPrimitive.content
                AgilityCourse(
                    key,
                    course.getValue("obstacles").jsonArray.map {
                        val name = it.jsonPrimitive.content
                        byKey[name] ?: error("Course $key names obstacle $name, which isn't listed")
                    },
                    course.int("lapXpTenths"),
                    AgilityLines.byKey(course.getValue("lapLine").jsonPrimitive.content),
                    level = course["level"]?.jsonPrimitive?.int ?: 1,
                    markSpots = course["marks"]?.jsonArray.orEmpty().map { tile(it.jsonArray) },
                    markChance = course["markChance"]?.jsonArray?.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int } ?: (1 to 3),
                )
            }
            return AgilityData(obstacles, courses)
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
                    route["at"]?.jsonArray.orEmpty().map { tile(it.jsonArray) },
                )
            }
            return Obstacle(
                key, json.int("loc"), json.getValue("option").jsonPrimitive.content, json.int("level"), json.int("xpTenths"),
                json["at"]?.jsonArray?.let(::tile), routes, success, damage,
                json["levelLine"]?.jsonPrimitive?.content?.let(AgilityLines::byKey) ?: AgilityLines.LEVEL,
                json["alsoLocs"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int },
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
    /** The clock's minute now, which the marks' timer counts in; tests move it. */
    var nowMinutes: () -> Long = { System.currentTimeMillis() / 60_000 }

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
            player.message(world.textFor(player)[obstacle.levelLine, "level" to obstacle.level])
            return
        }
        val route = routeFor(obstacle, loc) ?: return
        if (!walkTo(route.from)) return
        val success = obstacle.success?.let { (low, high) ->
            SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.AGILITY))
        } ?: true
        for (move in if (success) route.moves else route.fail) play(move)
        if (success) {
            player.skills.addTenths(Skill.AGILITY, obstacle.xpTenths)
            crossed(obstacle)
        } else if (!obstacle.damage.isEmpty()) {
            val damage = world.random.between(obstacle.damage.first, obstacle.damage.last)
            world.meleeCombat.statusHit(player, damage, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
        }
    }

    /**
     * Marks [obstacle] crossed on its course's lap. The first obstacle starts a new lap; the last one ends it, and with
     * every obstacle crossed since the start the lap counts: the lap count goes up and is shown, and the lap XP comes.
     * Inferred: a lap needs every obstacle in it, as the Gnome Stronghold's lap bonus does (OSRS Wiki); on a rooftop
     * there is no other way round. Guessed: the progress is kept across a logout.
     */
    private fun ScriptContext.crossed(obstacle: Obstacle) {
        val (course, index) = data.courseOf[obstacle] ?: return
        val progress = if (index == 0) 1 else player.charges[course.progressCounter] or (1 shl index)
        if (index < course.obstacles.lastIndex) {
            player.charges[course.progressCounter] = progress
            return
        }
        player.charges[course.progressCounter] = 0
        if (progress != (1 shl course.obstacles.size) - 1) return
        val laps = (player.charges[course.lapsCounter].toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        player.charges[course.lapsCounter] = laps
        if (course.lapXpTenths > 0) player.skills.addTenths(Skill.AGILITY, course.lapXpTenths)
        player.message(world.textFor(player)[course.lapLine, "count" to laps])
        rollMark(course)
    }

    /**
     * A lap done: a chance at a mark of grace, by the OSRS Wiki's Mark of Grace page (read 2026-11-02). Once three
     * minutes of the clock have gone since the player's last mark (counted in whole minutes, from the minute it came, and
     * kept across a logout), each lap has the course's chance, 2/6 on most courses, cut to a fifth when their unboosted
     * Agility is 20 or more over the course's level. A mark lands on one of the course's spots, for this player alone,
     * and goes after ten minutes; one landing on a mark already there stacks and the stack's ten minutes start again.
     *
     * Inferred: the spot is picked at random, as the Wiki gives the spots but no order; a failed roll leaves the timer
     * alone, as the Wiki times it from the last mark. Not built: the Wiki's marks staying on the ground with their
     * timer paused while the player is logged out (ours keep counting down), and the Achievement Diary rerolls.
     */
    internal fun ScriptContext.rollMark(course: AgilityCourse) {
        if (course.markSpots.isEmpty()) return
        val now = nowMinutes()
        if (!markDue(player.charges[MARK_MINUTE], now)) return
        val (numerator, denominator) = markOdds(course, player.skills.baseLevel(Skill.AGILITY))
        if (!world.random.chance(numerator, denominator)) return
        val spot = world.random.pick(course.markSpots)
        val mark = world.objTypes[MARK_OF_GRACE] ?: ObjType(MARK_OF_GRACE, "Mark of grace", stackable = true)
        val obj = world.groundObjs.addPrivate(
            mark, 1, spot.x, spot.z, spot.level, player.saveKey,
            privateTicks = MARK_TICKS, despawnTicks = MARK_TICKS, neverPublic = true,
        )
        obj.ticksUntilDespawn = MARK_TICKS
        player.charges[MARK_MINUTE] = now.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }

    /** The obstacle [loc] is: the only one of its type, or the one standing on its tile. */
    private fun obstacleAt(obstacles: List<Obstacle>, loc: ZoneLoc): Obstacle? =
        obstacles.singleOrNull()?.takeIf { it.at == null } ?: obstacles.firstOrNull { it.at == AgilityTile(loc.x, loc.z, loc.level) }

    /**
     * The way across from where the player is: of the routes on their level, those that name the loc clicked (one net or
     * pipe of several), or if none does those that name no loc, then the one starting nearest the player (a fence's
     * side).
     */
    private fun ScriptContext.routeFor(obstacle: Obstacle, loc: ZoneLoc): AgilityRoute? {
        val here = obstacle.routes.filter { it.from.level == player.level }
        val clicked = AgilityTile(loc.x, loc.z, loc.level)
        val named = here.filter { clicked in it.at }
        return (named.ifEmpty { here.filter { it.at.isEmpty() } }).minByOrNull { distance(it.from) }
    }

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
        /** The mark of grace (`grace`). */
        const val MARK_OF_GRACE = 11849

        /** Ten minutes of ticks, a mark's time on the ground. */
        const val MARK_TICKS = 1_000

        /** The minutes between marks, and the [osrsps.game.Charges] counter holding the minute of the last one. */
        const val MARK_TIMER_MINUTES = 3
        const val MARK_MINUTE = "agility_mark_minute"

        /** 20 levels over a course cut its chance of a mark to a fifth. */
        const val OVER_LEVEL = 20
        const val OVER_LEVEL_DIVISOR = 5

        /** A lap's chance of a mark on [course] for a player whose unboosted Agility is [baseLevel]. */
        fun markOdds(course: AgilityCourse, baseLevel: Int): Pair<Int, Int> {
            val (numerator, denominator) = course.markChance
            return if (baseLevel >= course.level + OVER_LEVEL) numerator to denominator * OVER_LEVEL_DIVISOR else numerator to denominator
        }

        /** Whether a mark last seen at [lastMinute] (0 for never) lets another come at [nowMinute]. */
        fun markDue(lastMinute: Int, nowMinute: Long): Boolean = lastMinute == 0 || nowMinute >= lastMinute + MARK_TIMER_MINUTES

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
