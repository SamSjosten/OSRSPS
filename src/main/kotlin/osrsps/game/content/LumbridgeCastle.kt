package osrsps.game.content

import osrsps.game.World

/**
 * Lumbridge Castle (Sam's first content piece, 2026-10-21). Loc ids and places are from the live 241 cache (map squares
 * 50,50 and 50,150); see /mnt/project-files/lumbridge-castle/castle-locs-rev241.tsv.
 *
 * Getting about: the spiral stairs in the south-west and north-west towers go between the ground floor, the first floor
 * (the Duke's) and the top floor; the ladders in the east wall towers do the same; the kitchen trapdoor goes down to the
 * cellar and the cellar ladder back up. The handlers are keyed by loc type, so the same types elsewhere work too.
 */
object LumbridgeCastle {
    /** spiralstairsbottom_3, 2x2: Climb-up, and Top-floor straight to the top. */
    const val STAIRS_BOTTOM = 56230

    /** spiralstairsmiddle, 2x2: Climb (a menu), Climb-up, Climb-down. */
    const val STAIRS_MIDDLE = 16672

    /** spiralstairstop_3, 1x1: Climb-down, and Bottom-floor straight to the ground. */
    const val STAIRS_TOP = 56231

    /** ladder: Climb-up. */
    const val LADDER_BOTTOM = 16683

    /** laddermiddle: Climb (a menu), Climb-up, Climb-down. */
    const val LADDER_MIDDLE = 16684

    /** laddertop: Climb-down. */
    const val LADDER_TOP = 16679

    /** qip_cook_trapdoor_open, in the kitchen: Climb-down to the cellar. */
    const val KITCHEN_TRAPDOOR = 14880

    /** ladder_from_cellar: Climb-up to the kitchen. */
    const val CELLAR_LADDER = 17385

    const val CLIMB = 1
    const val CLIMB_UP = 2
    const val CLIMB_DOWN = 3

    /** Top-floor on [STAIRS_BOTTOM] and Bottom-floor on [STAIRS_TOP]. */
    const val SKIP_FLOOR = 2
}

/** Lumbridge Castle's stairs, ladders, trapdoor and cellar ladder. */
fun registerLumbridgeCastle(world: World) {
    with(LumbridgeCastle) {
        world.handlers.onLoc(STAIRS_BOTTOM, 1) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(STAIRS_BOTTOM, SKIP_FLOOR) { op -> climbStairs(world, op.loc!!, 2) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB) { op -> climbStairsMenu(world, op.loc!!) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB_UP) { op ->
            arriveDelay()
            climbStairs(world, op.loc!!, 1)
        }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB_DOWN) { op ->
            arriveDelay()
            climbStairs(world, op.loc!!, -1)
        }
        world.handlers.onLoc(STAIRS_TOP, 1) { op -> climbStairs(world, op.loc!!, -1) }
        world.handlers.onLoc(STAIRS_TOP, SKIP_FLOOR) { op -> climbStairs(world, op.loc!!, -2) }

        world.handlers.onLoc(LADDER_BOTTOM, 1) { climbLadder(world, 1, Climbing.ANIM_REACH_FOR_LADDER) }
        world.handlers.onLoc(LADDER_MIDDLE, CLIMB) { climbLadderMenu(world) }
        world.handlers.onLoc(LADDER_MIDDLE, CLIMB_UP) { climbLadder(world, 1, Climbing.ANIM_REACH_FOR_LADDER) }
        world.handlers.onLoc(LADDER_MIDDLE, CLIMB_DOWN) { climbLadder(world, -1, Climbing.ANIM_REACH_FOR_LADDER) }
        world.handlers.onLoc(LADDER_TOP, 1) { climbLadder(world, -1, Climbing.ANIM_PICKUP_FLOOR) }

        world.handlers.onLoc(KITCHEN_TRAPDOOR, 1) { climbDungeonLadder(world, down = true, anim = Climbing.ANIM_PICKUP_FLOOR) }
        world.handlers.onLoc(CELLAR_LADDER, 1) { climbDungeonLadder(world, down = false, anim = Climbing.ANIM_REACH_FOR_LADDER) }
    }
}
