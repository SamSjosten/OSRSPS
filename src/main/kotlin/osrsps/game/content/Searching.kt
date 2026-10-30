package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * What searching furniture says. RSMod and 2004Scape agree on the crate's; the sacks' is RSMod's (2004Scape's differs
 * a little), the chest's a constant RSMod defines but never uses (2004Scape guesses another), the drawers' 2004Scape's.
 */
object SearchLines : Lines("search", "Crate") {
    val CRATE_EMPTY = line("crate_empty", "Search on a crate")
    val SACKS_EMPTY = line("sacks_empty", "Search on sacks")
    val CHEST_EMPTY = line("chest_empty", "Search on an open chest")
    val DRAWERS_EMPTY = line("drawers_empty", "Search on open drawers")
    val BOXES_EMPTY = line("boxes_empty", "Search on boxes (2004Scape's crates.rs2)")
}

/**
 * Crates, sacks, chests and drawers that a player searches and finds nothing in. RS2 once gave a random item now and
 * then; the OSRS Wiki says that went in 2006, before OSRS's 2007 base, so nothing is ever found here.
 *
 * Sources, none checked against live yet: RSMod (rev 233) for the crate and sacks messages, the 2004Scape project (RS2
 * as of 2004) for the drawers' message and for how chests and drawers open and shut: the animation, a tick's wait,
 * and the change lasting 300 ticks before the furniture goes back as it was. The chest's message is a constant RSMod
 * defines but never uses. All guessed until a recording settles them.
 */
object Searching {
    /** human_openchest, for drawers too. */
    const val ANIM_OPEN = 536

    /** human_closechest. */
    const val ANIM_SHUT = 535

    /** How long opened (or shut) furniture stays that way before it goes back as the map has it. */
    const val CHANGE_TICKS = 300

    const val OPEN = 1
    const val SEARCH = 2
    const val SHUT = 3
}

/** Option [option] on loc [id] says [message]. */
fun registerSearch(world: World, id: Int, option: Int, message: Line) {
    world.handlers.onLoc(id, option) { world.message(player, message) }
}

/**
 * Furniture whose [closed] type opens into [open], which has Search (option [search]), saying [searchMessage], and Shut
 * or Close (option [shut]). Most open types have them second and third; Varrock's have them first and second.
 */
fun registerOpenable(world: World, closed: Int, open: Int, searchMessage: Line, search: Int = Searching.SEARCH, shut: Int = Searching.SHUT) {
    world.handlers.onLoc(closed, Searching.OPEN) { op -> swapFurniture(world, op.loc!!, open, Searching.ANIM_OPEN) }
    registerSearch(world, open, search, searchMessage)
    world.handlers.onLoc(open, shut) { op -> swapFurniture(world, op.loc!!, closed, Searching.ANIM_SHUT) }
}

/** Opens or shuts furniture [loc] into [to] with [anim], for [Searching.CHANGE_TICKS] unless that is the map's own. */
suspend fun ScriptContext.swapFurniture(world: World, loc: ZoneLoc, to: Int, anim: Int) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    val locs = world.locs
    // Someone else may have opened or shut it meanwhile.
    if (!locs.exists(loc)) return
    if (locs.cached(loc.x, loc.z, loc.level, loc.layer) == loc.copy(id = to)) {
        locs.restore(loc.x, loc.z, loc.level, loc.layer)
    } else {
        locs.replace(loc, to, revertAfter = Searching.CHANGE_TICKS)
    }
}
