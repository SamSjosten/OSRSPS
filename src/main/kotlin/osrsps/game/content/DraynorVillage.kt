package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** What Draynor's furniture says (keys only; the words are in data/dialogue.txt). */
object DraynorSearchLines : Lines("draynor_search", "Draynor Village") {
    val WARDROBE_EMPTY = line("wardrobe_empty", "Search on an open wardrobe in a Draynor house")
    val BOOKCASE_EMPTY = line("bookcase_empty", "Search on the bookcase in the north-east house")
    val GARLIC = line("garlic", "Search on Morgan's open cupboard upstairs: a clove of garlic is found (the Wiki, Garlic)")
    val GARLIC_NO_ROOM = line("garlic_no_room", "the same with a full inventory; words guessed")
}

/**
 * Draynor Village (Draynor piece step 1): the bank, the houses' stairs and furniture, and Morgan's garlic cupboard. Loc
 * ids and places are from the live 241 cache (map squares 48,50 and 48,51). The bank booths and deposit box work by
 * name ([registerBanking]), the bankers through [Bankers], the doors by name ([osrsps.game.loc.Doors]) and the pig pen's
 * and farm's gates through [osrsps.game.loc.Gates]; the wheat is [Crops]'s, the crates `crate2_old`/`crate3_old` and
 * the chests the castle's ([LumbridgeCastle]).
 *
 * Stairs: the two straight staircases (Morgan's house, the Wise Old Man's) aren't spiral, so [Climbing.stairsUp] doesn't
 * fit them. Each takes the player off its far end: up onto the floor tile past the top piece, down onto the tile before
 * the bottom piece, read off the 241 map's walls (both run east, whatever their angle). Inferred, not seen on live.
 */
object DraynorVillage {
    /** stairs (2x3) and stairstop (2x2): Morgan's house. */
    const val MORGAN_STAIRS = 15645
    const val MORGAN_STAIRS_TOP = 15648

    /** narrowstairs_wooden_bottom (1x3) and _top (1x2): the Wise Old Man's house. */
    const val NARROW_STAIRS = 16670
    const val NARROW_STAIRS_TOP = 16669

    /** garliccupboardshut and garliccupboardopen, upstairs in Morgan's house. */
    const val GARLIC_CUPBOARD = 2612
    const val GARLIC_CUPBOARD_OPEN = 2613

    /** draynor_wardrobe and its open type: Aggie's house. */
    const val WARDROBE = 5622
    const val WARDROBE_OPEN = 5623

    /** drawers2 and drawers2open: Spria's house and the north-east house. */
    const val DRAWERS = 350
    const val DRAWERS_OPEN = 351

    /** crate_old (Search): Ned's house. */
    const val CRATE_OLD = 356

    /** aggie_courtyard_crate and aggie_courtyard_crates_stacked: behind Aggie's house. */
    const val AGGIE_CRATE = 16564
    const val AGGIE_CRATES = 16565

    /** bookcase2: the north-east house. */
    const val BOOKCASE = 381

    /** Garlic. */
    const val GARLIC = 1550

    /** Where each straight staircase, by the tile of the loc clicked, puts the player. */
    val STAIRS: Map<Triple<Int, Int, Int>, Destination> = mapOf(
        Triple(3099, 3266, 0) to Destination(3102, 3266, 1),
        Triple(3100, 3266, 1) to Destination(3098, 3266, 0),
        Triple(3090, 3251, 0) to Destination(3093, 3251, 1),
        Triple(3091, 3251, 1) to Destination(3089, 3251, 0),
    )

    fun stairs(loc: ZoneLoc): Destination? = STAIRS[Triple(loc.x, loc.z, loc.level)]
}

fun registerDraynorVillage(world: World) {
    with(DraynorVillage) {
        for (id in listOf(MORGAN_STAIRS, MORGAN_STAIRS_TOP, NARROW_STAIRS, NARROW_STAIRS_TOP)) {
            world.handlers.onLoc(id, 1) { op ->
                // The same types elsewhere in the world aren't placed yet.
                val to = stairs(op.loc!!) ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                world.teleport(player, to.x, to.z, to.level)
            }
        }
        registerOpenable(world, WARDROBE, WARDROBE_OPEN, DraynorSearchLines.WARDROBE_EMPTY)
        registerOpenable(world, DRAWERS, DRAWERS_OPEN, SearchLines.DRAWERS_EMPTY)
        for (crate in listOf(CRATE_OLD, AGGIE_CRATE, AGGIE_CRATES)) registerSearch(world, crate, 1, SearchLines.CRATE_EMPTY)
        registerSearch(world, BOOKCASE, 1, DraynorSearchLines.BOOKCASE_EMPTY)

        // The garlic cupboard opens and shuts as other furniture does, but its open type has Search first and Close second.
        world.handlers.onLoc(GARLIC_CUPBOARD, 1) { op -> swapFurniture(world, op.loc!!, GARLIC_CUPBOARD_OPEN, Searching.ANIM_OPEN) }
        world.handlers.onLoc(GARLIC_CUPBOARD_OPEN, 2) { op -> swapFurniture(world, op.loc!!, GARLIC_CUPBOARD, Searching.ANIM_SHUT) }
        world.handlers.onLoc(GARLIC_CUPBOARD_OPEN, 1) { searchGarlic(world) }
    }
}

/**
 * Searching Morgan's open cupboard gives a clove of garlic every time (the Wiki's Garlic page: "an unlimited supply").
 * Guessed: no animation or wait, and nothing given with a full inventory.
 */
private fun ScriptContext.searchGarlic(world: World) {
    if (player.inventory.freeSlots == 0) {
        world.message(player, DraynorSearchLines.GARLIC_NO_ROOM)
        return
    }
    world.objTypes[DraynorVillage.GARLIC]?.let { player.inventory.add(it, 1) }
    world.message(player, DraynorSearchLines.GARLIC)
}
