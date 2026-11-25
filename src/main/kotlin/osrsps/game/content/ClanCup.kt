package osrsps.game.content

import osrsps.game.World

/**
 * Misthalin's game-wide systems: the Clan Cup portal upstairs in the building north of Edgeville's bank, and the
 * Challenge area it leads to. Sources: the OSRS Wiki's Clan Cup portal page, read 2026-11-19, and the 242 cache:
 * `clancup_portal_challengearea_enter` (27094) at 3097, 3507 on level 1, a multiloc on deadman_mode that shows
 * `clancup_portal_challengearea_exit` (27095, Use) off Deadman worlds, and the Challenge area's own 27095 at 3157, 9817.
 *
 * Use on the Edgeville portal goes into the Challenge area, and on the Challenge area's portal back to Edgeville (the
 * Wiki: "Connects the Challenge area to Edgeville"). The Wiki says it works only on members worlds; this server's one
 * world counts as one. The Challenge area is only a lobby now (a small cave with blood splatters); the Clan Cup arenas it
 * once led to aren't built.
 *
 * Guessed: the tiles each way (beside each portal) and that the portal acts at once, with no animation.
 */
object ClanCup {
    const val EDGEVILLE_PORTAL = 27094
    const val PORTAL = 27095
    const val USE = 1

    /** Where Use puts the player: in the Challenge area, north of its portal; and in Edgeville, north of the upstairs one. */
    const val AREA_X = 3157
    const val AREA_Z = 9818
    const val EDGEVILLE_X = 3097
    const val EDGEVILLE_Z = 3508
    const val EDGEVILLE_LEVEL = 1

    /** The Challenge area is underground: below this z every portal leads out to Edgeville. */
    const val UNDERGROUND_Z = 6400
}

/** The two Clan Cup portals. */
fun registerClanCup(world: World) {
    with(ClanCup) {
        for (portal in listOf(EDGEVILLE_PORTAL, PORTAL)) {
            world.handlers.onLoc(portal, USE) {
                arriveDelay()
                if (player.z >= UNDERGROUND_Z) {
                    world.teleport(player, EDGEVILLE_X, EDGEVILLE_Z, EDGEVILLE_LEVEL)
                } else {
                    world.teleport(player, AREA_X, AREA_Z, 0)
                }
            }
        }
    }
}
