package osrsps.game.content

import osrsps.game.World

/**
 * Lumbridge Castle (Sam's first content piece, 2026-10-21). Loc ids and places are from the live 241 cache (map squares
 * 50,50 and 50,150); see /mnt/project-files/lumbridge-castle/castle-locs-rev241.tsv.
 *
 * Getting about: the spiral stairs in the south-west and north-west towers go between the ground floor, the first floor
 * (the Duke's) and the top floor; the ladders in the east wall towers do the same; the kitchen trapdoor goes down to the
 * cellar and the cellar ladder back up. The handlers are keyed by loc type, so the same types elsewhere work too.
 *
 * Doors: the castle's other doors pair with their open types by name (see [osrsps.game.loc.Doors]). The kitchen door
 * and the large double doors from the courtyard are Recipe for Disaster's copies (`hundred_lumbridge_*`), which have
 * no open types of their own. Each has exactly the models, recolours and retextures of an ordinary door (`elfdoor`,
 * `castledoubledoorl`, `castledoubledoorr`, checked in the 241 cache), so they are taken to open into that door's open
 * type. Inferred, not seen on live.
 *
 * NPCs: Hans (see [Hans]), the Cook ([Cook]), Duke Horacio ([DukeHoracio]), the men and women ([Townsfolk]), Perdu
 * ([Perdu]), the Crafting tutor ([CraftingTutor]), the bankers ([Bankers]), and outside the gate the Doomsayer
 * ([Doomsayer]), Hatius Cosaintus ([Hatius]) and the Lumbridge Guide ([LumbridgeGuide]) talk, as they do once every
 * quest is done.
 *
 * Searching: the crates, sacks, chest and drawers (see [Searching]). Not done yet, for want of a source: the Lost Tribe
 * chest (`lost_tribe_chest` 6910, next to the Duke's room) after the quest, and the winch on the top floor.
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

    /** hundred_lumbridge_door, the kitchen's door: opens into [DOOR_OPEN]. */
    const val KITCHEN_DOOR = 12348

    /** elfdooropen. */
    const val DOOR_OPEN = 1544

    /** hundred_lumbridge_doubledoorl and _r, the large doors from the courtyard into the castle. */
    const val LARGE_DOOR_LEFT = 12349
    const val LARGE_DOOR_RIGHT = 12350

    /** opencastledoubledoorl and r. */
    const val LARGE_DOOR_LEFT_OPEN = 1522
    const val LARGE_DOOR_RIGHT_OPEN = 1525

    /** crate, crate2_old and crate3_old: Search. */
    const val CRATE = 366
    const val CRATE_2 = 357
    const val CRATE_3 = 358

    /** sacks: Search. */
    const val SACKS = 365

    /** chestclosed (the Duke's floor) and chestopen: Open, then Search and Shut. */
    const val CHEST = 375
    const val CHEST_OPEN = 378

    /** drawers4 (the Duke's floor) and drawers4open: Open, then Search and Shut. */
    const val DRAWERS = 5618
    const val DRAWERS_OPEN = 5619

    const val CLIMB = 1
    const val CLIMB_UP = 2
    const val CLIMB_DOWN = 3

    /** Top-floor on [STAIRS_BOTTOM] and Bottom-floor on [STAIRS_TOP]. */
    const val SKIP_FLOOR = 2
}

/** Lumbridge Castle's stairs, ladders, trapdoor, cellar ladder, doors, searchable furniture and NPCs' options. */
fun registerLumbridgeCastle(world: World) {
    registerCook(world)
    registerDukeHoracio(world)
    registerTownsfolk(world)
    registerPerdu(world)
    registerCraftingTutor(world)
    registerBankers(world)
    registerDoomsayer(world)
    registerHatius(world)
    registerLumbridgeGuide(world)
    with(LumbridgeCastle) {
        for (crate in listOf(CRATE, CRATE_2, CRATE_3)) registerSearch(world, crate, 1, SearchLines.CRATE_EMPTY)
        registerSearch(world, SACKS, 1, SearchLines.SACKS_EMPTY)
        registerOpenable(world, CHEST, CHEST_OPEN, SearchLines.CHEST_EMPTY)
        registerOpenable(world, DRAWERS, DRAWERS_OPEN, SearchLines.DRAWERS_EMPTY)

        world.doors.addClosedType(KITCHEN_DOOR, DOOR_OPEN)
        world.doors.registerDouble(LARGE_DOOR_LEFT, LARGE_DOOR_LEFT_OPEN, LARGE_DOOR_RIGHT, LARGE_DOOR_RIGHT_OPEN)

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

        world.handlers.onLoc(LADDER_BOTTOM, 1) { op -> climbLadder(world, op.loc!!, 1, Climbing.ANIM_REACH_FOR_LADDER) }
        world.handlers.onLoc(LADDER_MIDDLE, CLIMB) { op -> climbLadderMenu(world, op.loc!!) }
        world.handlers.onLoc(LADDER_MIDDLE, CLIMB_UP) { op -> climbLadder(world, op.loc!!, 1, Climbing.ANIM_REACH_FOR_LADDER) }
        world.handlers.onLoc(LADDER_MIDDLE, CLIMB_DOWN) { op -> climbLadder(world, op.loc!!, -1, Climbing.ANIM_REACH_FOR_LADDER) }
        world.handlers.onLoc(LADDER_TOP, 1) { op -> climbLadder(world, op.loc!!, -1, Climbing.ANIM_PICKUP_FLOOR) }

        world.handlers.onLoc(KITCHEN_TRAPDOOR, 1) { op -> climbDungeonLadder(world, op.loc!!, down = true, anim = Climbing.ANIM_PICKUP_FLOOR) }
        world.handlers.onLoc(CELLAR_LADDER, 1) { op -> climbDungeonLadder(world, op.loc!!, down = false, anim = Climbing.ANIM_REACH_FOR_LADDER) }
    }
}
