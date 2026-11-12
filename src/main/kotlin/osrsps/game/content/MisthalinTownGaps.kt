package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * The small Draynor, Edgeville and Lumbridge lists from the Misthalin sweep (the Varrock thread's follow-up): loc
 * options there with no handler. Ids and places are the 241 cache's.
 *
 * - Searching finds nothing in the Wizards' Tower's crates (`fai_wiztower_crate2`/`_crate3`, 12547 and 12548),
 *   Edgeville's crates (`crate2`/`crate3`, 354 and 355), the Wise Old Man's old bookshelves in Draynor (the 7058 to
 *   7089 family and 9146/9147) and the bookcase in Lumbridge's south house (`bookcase` 380), with the shared lines
 *   (guessed: the Wiki pages for them couldn't be read).
 * - Draynor Manor's cabbages (`draynor_magic_cabbage` 11494) pick as the fields' do and give the manor's own cabbage
 *   (1967), coming back after 100 ticks (the Wiki's Cabbage (Draynor Manor) infobox; the message is the fields'
 *   cabbage line, guessed).
 * - The stairs in Draynor Manor down to Count Draynor's crypt (`cryptstairsdown` 2616 at 3115,3357) and back up
 *   (`cryptstairsup` 2617 at 3077,9768). Where each lands is guessed: the open tile at the north end of the other
 *   staircase, from the map's collision.
 *
 * Left out: poll booths, the loot chests, the Wilderness statistics and streak boards, the Clan Cup portal and the
 * Keldagrim trapdoor (systems not built); the Edgeville Dungeon pipes (the Agility thread's shortcuts); Obor's rocks
 * and chest; the Wise Old Man's tree, trapdoor and bed; Lucien's door; the Lost Tribe chest; the crypt's coffin; the
 * Lumbridge winch and the church organ (the Wiki doesn't say what they do).
 */
object MisthalinTownGaps {
    val EMPTY_CRATES = listOf(12547, 12548, 354, 355)
    val WISE_OLD_MAN_BOOKSHELVES = listOf(7058, 7065, 7066, 7068, 7071, 7072, 7073, 7074, 7076, 7077, 7078, 7079, 7081, 7082, 7089, 9146, 9147)
    const val LUMBRIDGE_BOOKCASE = 380
    const val SEARCH = 1

    const val MAGIC_CABBAGE_LOC = 11494
    const val MANOR_CABBAGE = 1967
    const val PICK = 1
    val MANOR_CABBAGES = Crops.Crop(listOf(MAGIC_CABBAGE_LOC), MANOR_CABBAGE, 100, CropLines.CABBAGE_PICKED, CropLines.CABBAGE_NO_ROOM)

    const val CRYPT_STAIRS_DOWN = 2616
    const val CRYPT_STAIRS_UP = 2617
    const val CLIMB = 1
    val CRYPT_LANDING = Destination(3077, 9771, 0)
    val GRAVEYARD_LANDING = Destination(3115, 3360, 0)
    val CRYPT_STAIRS_DOWN_TILE = 3115 to 3357
    val CRYPT_STAIRS_UP_TILE = 3077 to 9768
}

fun registerMisthalinTownGaps(world: World) = with(MisthalinTownGaps) {
    for (crate in EMPTY_CRATES) registerSearch(world, crate, SEARCH, SearchLines.CRATE_EMPTY)
    for (shelf in WISE_OLD_MAN_BOOKSHELVES) registerSearch(world, shelf, SEARCH, DraynorSearchLines.BOOKCASE_EMPTY)
    registerSearch(world, LUMBRIDGE_BOOKCASE, SEARCH, VarrockSearchLines.BOOKCASE_EMPTY)
    world.handlers.onLoc(MAGIC_CABBAGE_LOC, PICK) { op -> pick(world, op.loc!!, MANOR_CABBAGES) }
    // The stairs by tile, as Cooks' Guild's are: a copy of either type elsewhere in the cache mustn't lead to Draynor
    // (exploit review round 29).
    fun ScriptContext.ours(loc: ZoneLoc, tile: Pair<Int, Int>): Boolean {
        if (loc.level == 0 && loc.x to loc.z == tile) return true
        world.message(player, GameLines.NOTHING_INTERESTING)
        return false
    }
    world.handlers.onLoc(CRYPT_STAIRS_DOWN, CLIMB) { op ->
        if (ours(op.loc!!, CRYPT_STAIRS_DOWN_TILE)) world.teleport(player, CRYPT_LANDING.x, CRYPT_LANDING.z, CRYPT_LANDING.level)
    }
    world.handlers.onLoc(CRYPT_STAIRS_UP, CLIMB) { op ->
        if (ours(op.loc!!, CRYPT_STAIRS_UP_TILE)) world.teleport(player, GRAVEYARD_LANDING.x, GRAVEYARD_LANDING.z, GRAVEYARD_LANDING.level)
    }
}
