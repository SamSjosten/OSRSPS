package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * The Karamja Volcano and Crandor (Karamja step K1.4): the ways in and out of the Crandor and Karamja Dungeon, as the
 * Wiki's Karamja Volcano, Crandor, Crandor and Karamja Dungeon, Rocks (Karamja Volcano), Wall (Crandor and Karamja
 * Dungeon), Hole (Crandor) and Climbing rope (Crandor Dungeon) pages have them (read 2026-11-18), with the ids and tiles
 * from the 242 cache and map:
 *
 * - **The volcano's rocks** (`volcano_entrance` 11441, at 2856,3168) climb down into the Karamja side of the dungeon,
 *   and its climbing rope (18969, at 2856,9569) climbs back up.
 * - **The wall** (2606, at 2836,9600) between the Karamja side and the Crandor side opens from either side. Every
 *   account has finished Dragon Slayer I, so it is open to all (the Wiki: unlocked for good once the player has sailed
 *   to Crandor).
 * - **Crandor's hole** (25154, at the top of the island's volcano) goes down into the Crandor side, and its climbing
 *   rope (25213, at 2833,9657) climbs back up.
 *
 * The monsters on the volcano, in the dungeon and on Crandor are spawn files (`karamja-volcano.json`,
 * `crandor.json`); their mining rocks are Mining's own. Inferred: the climbs' animations, as any ladder down and up.
 * Guessed: the tiles each climb lands on (beside the rope or rocks, checked as walkable on the 242 map); the wall moving
 * the player one tile through with no message or animation. Left out: Elvarg and her lair's wall (25161), whom the
 * quest kills for good; the Crandor rock-climb shortcut (53234, 53236, 84 Agility); Dallas Jones and the lab (Dragon
 * Slayer II); the unreachable fire giants; the monkeys (no combat data yet). The cave entrance to Mor Ul Rek (11835) is
 * step K2.1.
 *
 * Each climb works only on its own tile ([PLACED]): the 242 map has one of each, and a copy of the type anywhere else
 * says "Nothing interesting happens." rather than teleporting (exploit review R50B-1).
 */
object KaramjaVolcano {
    const val VOLCANO_ROCKS = 11441
    const val VOLCANO_ROPE = 18969
    const val WALL = 2606
    const val CRANDOR_HOLE = 25154
    const val CRANDOR_ROPE = 25213

    const val CLIMB_DOWN = 1
    const val CLIMB = 1
    const val OPEN = 1
    const val ENTER = 1

    /** Where the volcano's rocks put the player below (beside the rope), and the rope above (south of the rocks). */
    val VOLCANO_BELOW = Destination(2856, 9570, 0)
    val VOLCANO_ABOVE = Destination(2856, 3167, 0)

    /** Where Crandor's hole puts the player below (beside the rope), and the rope above (south of the hole). */
    val CRANDOR_BELOW = Destination(2834, 9656, 0)
    val CRANDOR_ABOVE = Destination(2834, 3254, 0)

    /** The wall's tile; it stands on that tile's south edge, so the Crandor side is that tile and north of it. */
    const val WALL_X = 2836
    const val WALL_Z = 9600

    /** The one tile each climb stands on, on the 242 map (KaramjaClimbsAuditTest checks there are no others). */
    val PLACED = mapOf(
        VOLCANO_ROCKS to Destination(2856, 3168, 0),
        VOLCANO_ROPE to Destination(2856, 9569, 0),
        WALL to Destination(WALL_X, WALL_Z, 0),
        CRANDOR_HOLE to Destination(2833, 3255, 0),
        CRANDOR_ROPE to Destination(2833, 9657, 0),
    )

    /** The tile through the wall from a player at [z]: north of the wall to its south side, and the other way. */
    fun throughWall(z: Int): Destination = if (z >= WALL_Z) Destination(WALL_X, WALL_Z - 1, 0) else Destination(WALL_X, WALL_Z, 0)
}

/** A climb with [anim] to [to], after the wait a ladder has. */
private suspend fun ScriptContext.climbTo(world: World, loc: ZoneLoc?, at: Destination?, to: Destination, anim: Int) {
    arriveDelay()
    if (!placedAt(world, loc, at)) return
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, to.level)
}

/** True when [loc] stands on [at]; anywhere else the player is told nothing happens. */
internal fun ScriptContext.placedAt(world: World, loc: ZoneLoc?, at: Destination?): Boolean {
    if (loc != null && at != null && loc.x == at.x && loc.z == at.z && loc.level == at.level) return true
    world.message(player, GameLines.NOTHING_INTERESTING)
    return false
}

fun registerKaramjaVolcano(world: World) {
    with(KaramjaVolcano) {
        fun climb(id: Int, option: Int, to: Destination, anim: Int) =
            world.handlers.onLoc(id, option) { op -> climbTo(world, op.loc, PLACED[id], to, anim) }
        climb(VOLCANO_ROCKS, CLIMB_DOWN, VOLCANO_BELOW, Climbing.ANIM_PICKUP_FLOOR)
        climb(VOLCANO_ROPE, CLIMB, VOLCANO_ABOVE, Climbing.ANIM_REACH_FOR_LADDER)
        climb(CRANDOR_HOLE, ENTER, CRANDOR_BELOW, Climbing.ANIM_PICKUP_FLOOR)
        climb(CRANDOR_ROPE, CLIMB, CRANDOR_ABOVE, Climbing.ANIM_REACH_FOR_LADDER)
        world.handlers.onLoc(WALL, OPEN) { op ->
            arriveDelay()
            if (!placedAt(world, op.loc, PLACED[WALL])) return@onLoc
            val to = throughWall(player.z)
            world.teleport(player, to.x, to.z, to.level)
        }
    }
}
