package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines

/** Edgeville's lines (keys only; the words are in data/dialogue.txt). */
object EdgevilleLines : Lines("edgeville", "Edgeville") {
    val WARDROBE_EMPTY = line("wardrobe_empty", "Search on an open wardrobe in the house east of the willows")

    /** Our own words, not live's: the Wilderness isn't built, so the ditch can't be crossed (as Shantay Pass refuses). */
    val DITCH_CLOSED = line("ditch_closed", "Cross on the Wilderness Ditch: ours, the Wilderness isn't built yet")
}

/**
 * Edgeville (Edgeville piece step 1): the bank, the grange's double doors, the houses' furniture and the Wilderness
 * Ditch along Misthalin's northern edge. Loc ids and places are from the live 241 cache (map square 48,54, and 48,55 for
 * the ditch). The bank booths and deposit box work by name ([registerBanking]), the bankers through [Bankers], Evil
 * Dave's door and the others by name ([osrsps.game.loc.Doors]), Doris's yard gate through [osrsps.game.loc.Gates], the
 * ladders, crates and bookcases as the castle's and Draynor's types, and the grange's haystack and hay bales through
 * [Haystacks].
 *
 * The ditch's cover (`ditch_wilderness_cover`, Cross) runs the whole length of the Wilderness's southern border, so
 * refusing it here closes the Wilderness everywhere until a Wilderness piece builds it.
 */
object Edgeville {
    /** castledoubledoorl/r and their open types: the grange's front doors (3101, 3509 and 3510). */
    const val LARGE_DOOR_LEFT = 1521
    const val LARGE_DOOR_LEFT_OPEN = 1522
    const val LARGE_DOOR_RIGHT = 1524
    const val LARGE_DOOR_RIGHT_OPEN = 1525

    /** spookywardrobe and its open type: the house east of the willows (3120 to 3124, 3481 to 3487). */
    const val WARDROBE = 388
    const val WARDROBE_OPEN = 389

    /** coffin (Open) and coffin_open (Close): the ruined mausoleum south of the bank. */
    const val COFFIN = 398
    const val COFFIN_OPEN = 3577

    /** ditch_wilderness_cover, Cross. */
    const val DITCH = 23271
    const val CROSS = 1
}

fun registerEdgeville(world: World) {
    with(Edgeville) {
        world.doors.registerDouble(LARGE_DOOR_LEFT, LARGE_DOOR_LEFT_OPEN, LARGE_DOOR_RIGHT, LARGE_DOOR_RIGHT_OPEN)
        registerOpenable(world, WARDROBE, WARDROBE_OPEN, EdgevilleLines.WARDROBE_EMPTY)
        // The coffins open and close as other furniture does; open, they have nothing to search (241 cache: Close only).
        world.handlers.onLoc(COFFIN, Searching.OPEN) { op -> swapFurniture(world, op.loc!!, COFFIN_OPEN, Searching.ANIM_OPEN) }
        world.handlers.onLoc(COFFIN_OPEN, 1) { op -> swapFurniture(world, op.loc!!, COFFIN, Searching.ANIM_SHUT) }
        world.handlers.onLoc(DITCH, CROSS) { world.message(player, EdgevilleLines.DITCH_CLOSED) }
    }
}
