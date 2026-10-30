package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Where a climb puts the player. */
data class Destination(val x: Int, val z: Int, val level: Int)

/** The Climb menus' title and the stairs' options, RSMod's (rev 233). A ladder's options are its own ops' names. */
object ClimbLines : Lines("climb", "Staircase") {
    val STAIRS_TITLE = line("stairs_title", "Climb on stairs with a floor above and below: the menu's title")
    val STAIRS_UP = line("stairs_up", "that menu's first option")
    val STAIRS_DOWN = line("stairs_down", "that menu's second option")
    val LADDER_TITLE = line("ladder_title", "Climb on a ladder with a floor above and below: the menu's title (the Ladder page)")
}

/**
 * Stairs and ladders. Spiral stairs move the player at once to the tile beside the stairs on the next level; ladders
 * play an animation first and keep the player's tile. Where the stairs put the player, the animations, the waits and
 * the menu texts follow RSMod (rev 233), which models them on captures of live. Not yet checked against our own
 * recording, so all of it counts as guessed until then.
 */
object Climbing {
    /** human_reachforladder: going up a ladder, and either way on a ladder with a Climb menu. */
    const val ANIM_REACH_FOR_LADDER = 828

    /** human_pickupfloor: going down from the top of a ladder, or down a trapdoor. */
    const val ANIM_PICKUP_FLOOR = 827

    /** How far apart in z a dungeon is from the ground above it. */
    const val DUNGEON_OFFSET = 6400

    const val LADDER_UP = "Climb-up"
    const val LADDER_DOWN = "Climb-down"

    /**
     * Where spiral stairs [loc], [width] by [length] tiles before rotation, take a player going up [levels] levels. The
     * player comes out beside the stairs, on the side the loc's angle (0 west to 3 south) turns to.
     */
    fun stairsUp(loc: ZoneLoc, width: Int, length: Int, levels: Int = 1): Destination {
        val (w, l) = rotated(loc, width, length)
        return when (loc.angle and 3) {
            0 -> loc.offset(w, 0, levels)
            1 -> loc.offset(0, -(l - 1), levels)
            2 -> loc.offset(-(w - 1), l - 1, levels)
            else -> loc.offset(w - 1, l, levels)
        }
    }

    /** As [stairsUp], going down [levels] levels. */
    fun stairsDown(loc: ZoneLoc, width: Int, length: Int, levels: Int = 1): Destination {
        val (w, l) = rotated(loc, width, length)
        return when (loc.angle and 3) {
            0 -> loc.offset(w - 1, -1, -levels)
            1 -> loc.offset(-w, 0, -levels)
            2 -> loc.offset(0, l, -levels)
            else -> loc.offset(w, l - 1, -levels)
        }
    }

    private fun rotated(loc: ZoneLoc, width: Int, length: Int) = if (loc.angle and 1 == 1) length to width else width to length

    private fun ZoneLoc.offset(dx: Int, dz: Int, dLevel: Int) = Destination(x + dx, z + dz, level + dLevel)
}

/**
 * RSMod's arriveDelay: a player who took a step this tick to reach what they clicked waits one more tick before using
 * it. One who was already beside it uses it at once.
 */
suspend fun ScriptContext.arriveDelay() {
    if (player.lastMoveTick == tick) delay(1)
}

/** Spiral stairs [loc] up or down [levels] levels (negative for down), no wait and no animation. */
fun ScriptContext.climbStairs(world: World, loc: ZoneLoc, levels: Int) {
    val type = world.locs.types[loc.id]
    val width = type?.width ?: 1
    val length = type?.length ?: 1
    val to = if (levels > 0) Climbing.stairsUp(loc, width, length, levels) else Climbing.stairsDown(loc, width, length, -levels)
    world.teleport(player, to.x, to.z, to.level)
}

/** The Climb option on stairs with a floor above and below: a menu, then up or down. */
suspend fun ScriptContext.climbStairsMenu(world: World, loc: ZoneLoc) {
    var levels = 0
    world.dialogues.start(this) {
        levels = if (choice(ClimbLines.STAIRS_UP, ClimbLines.STAIRS_DOWN, title = text(ClimbLines.STAIRS_TITLE)) == 1) 1 else -1
    }
    climbStairs(world, loc, levels)
}

/**
 * Ladder [loc] up or down [levels] levels on the player's own tile, with [anim]. The tile and level are taken before
 * any wait, from where the player stands beside the ladder and the ladder's own level, so nothing that moves the player
 * during the wait can carry the climb off the map (the exploit review found a teleport during the wait made level 4).
 */
suspend fun ScriptContext.climbLadder(world: World, loc: ZoneLoc, levels: Int, anim: Int) {
    val to = Destination(player.x, player.z, loc.level + levels)
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, to.level)
}

/** The Climb option on ladder [loc] with a floor above and below: a menu, then up or down after a longer wait. */
suspend fun ScriptContext.climbLadderMenu(world: World, loc: ZoneLoc) {
    val x = player.x
    val z = player.z
    arriveDelay()
    var levels = 0
    world.dialogues.start(this) {
        levels = if (choice(Climbing.LADDER_UP, Climbing.LADDER_DOWN, title = text(ClimbLines.LADDER_TITLE)) == 1) 1 else -1
    }
    player.anim(Climbing.ANIM_REACH_FOR_LADDER)
    delay(2)
    world.teleport(player, x, z, loc.level + levels)
}

/**
 * Ladder or trapdoor [loc] between the ground and the dungeon below it: [down] adds [Climbing.DUNGEON_OFFSET] to z. As
 * for [climbLadder], the tile and level are taken before the wait.
 */
suspend fun ScriptContext.climbDungeonLadder(world: World, loc: ZoneLoc, down: Boolean, anim: Int) {
    val to = Destination(player.x, player.z + if (down) Climbing.DUNGEON_OFFSET else -Climbing.DUNGEON_OFFSET, loc.level)
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, to.level)
}
