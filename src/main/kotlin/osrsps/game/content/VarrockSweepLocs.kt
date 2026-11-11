package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines

/** The pillory's message, from the Wiki's Pillory page (Trivia), read 2026-11-11. */
object PilloryLines : Lines("pillory", "Pillory") {
    val UNLOCK = line("unlock", "Unlock on the Varrock pillory's cage: the player won't let the criminals out")
}

/**
 * Varrock locs the Misthalin sweep found with no handler (loc ids and places are the 241 cache's):
 *
 * - **The West Bank's cellar**: `fai_varrock_bank_stairs` (11800, 3187,3433) down and `fai_varrock_stairs_from_cellar`
 *   (11805, 3187,9833, the dungeon's 6400 below) up. Like the town's straight stairs, no wait and no animation. Where
 *   each puts the player is guessed: the free tile at the foot of the other staircase (3190,9833 below, 3186,3433
 *   above), from the map's collision.
 * - **The cellar south of the square**: `fai_varrock_ladder_deep` (11803, 3232,3402) and `fai_varrock_ladder_from_cellar`
 *   (11804, 3232,9802), a dungeon ladder pair that keeps the player's tile ([climbDungeonLadder]). The crates and
 *   cabinets down there are the town's, already searchable.
 * - **`fairy_crate` and `fairy_crate_2`** (12105, 12104) in the south-west houses: Search finds nothing, with the
 *   crates' usual message (guessed: the Wiki has no page for them).
 * - **The pillory**: Unlock on the cage (20873) says the Wiki's line; Buy on the rotten tomato crate (20885) opens
 *   the Pelters' Veg Stall (`pillory_rottenfruitshop`, shops.json), the Wiki's Rotten tomato page's Varrock stall.
 *
 * Left out: the Dragon Slayer II door (32464, quest-only), the open manhole to the Rat Pits (10321, not built), the
 * garden patches (Farming) and the spirit tree.
 */
object VarrockSweepLocs {
    const val BANK_STAIRS_DOWN = 11800
    const val CELLAR_STAIRS_UP = 11805
    const val LADDER_DOWN = 11803
    const val LADDER_UP = 11804
    val FAIRY_CRATES = listOf(12105, 12104)
    const val PILLORY_CAGE = 20873
    const val TOMATO_CRATE = 20885

    /** Where the stairs put the player: guessed, the free tile at the other staircase's foot. */
    val CELLAR_LANDING = Destination(3190, 9833, 0)
    val BANK_LANDING = Destination(3186, 3433, 0)

    const val PELTERS_STALL = "varrock_pelters_veg_stall"
}

fun registerVarrockSweepLocs(world: World) {
    with(VarrockSweepLocs) {
        world.handlers.onLoc(BANK_STAIRS_DOWN, 1) { world.teleport(player, CELLAR_LANDING.x, CELLAR_LANDING.z, CELLAR_LANDING.level) }
        world.handlers.onLoc(CELLAR_STAIRS_UP, 1) { world.teleport(player, BANK_LANDING.x, BANK_LANDING.z, BANK_LANDING.level) }
        world.handlers.onLoc(LADDER_DOWN, 1) { op -> climbDungeonLadder(world, op.loc!!, down = true, anim = Climbing.ANIM_PICKUP_FLOOR) }
        world.handlers.onLoc(LADDER_UP, 1) { op -> climbDungeonLadder(world, op.loc!!, down = false, anim = Climbing.ANIM_REACH_FOR_LADDER) }
        for (crate in FAIRY_CRATES) registerSearch(world, crate, 1, SearchLines.CRATE_EMPTY)
        registerSearch(world, PILLORY_CAGE, 1, PilloryLines.UNLOCK)
        world.handlers.onLoc(TOMATO_CRATE, 1) { world.shopTypes[PELTERS_STALL]?.let { world.shopScreen.open(player, it) } }
    }
}
