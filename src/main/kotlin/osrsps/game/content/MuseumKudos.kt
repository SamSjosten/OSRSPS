package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World

/**
 * The Varrock Museum's Kudos, kept as live keeps it: in the cache's own museum varbits, so the client's Kudos List (the
 * information booth's Check Kudos, `vm_kudos_info` 600) and anything else reading them sees what live shows.
 *
 * Each way of earning Kudos sets its own varbit (a display filled, a quiz passed, a story told), and [total] adds up
 * what those are worth, by the Wiki's Kudos page and the client's own list (enums 1740 and 1741, procs
 * `vm_kudos_get_status` and `vm_info_draw`). The total is written to `vm_kudos` (3637, the count the client shows) each
 * time a source changes and at login. Inferred: live keeps `vm_kudos` as a running count it adds to; working it out from
 * the sources gives the same number, can't count one source twice, and puts right a save made before a source existed.
 *
 * Built so far: the Dig Site exhibit's five display cases (10 each, [MuseumFinds]). Not built, so worth nothing here
 * yet: the natural history quiz, Historian Minas's timeline, Fossil Island's displays, Camdozaal's and Sailing's relics.
 */
object Kudos {
    /** `vm_kudos`: varp `vm` (1010) bits 0 to 10. */
    const val VARBIT_KUDOS = 3637

    /** `vm_necklace`: the clean necklace shown to an archaeologist, who then teaches the Digsite pendant's enchantment. */
    const val VARBIT_NECKLACE = 3639

    /** `vm_digsite_symbol_anc`, `_symbol_old`, `_coin_senn` (the ancient coin), `_coin_sara` (the old coin), `_pottery`: varp `vm_displays` (1011) bits 2 to 6. */
    const val VARBIT_ANCIENT_SYMBOL = 3645
    const val VARBIT_OLD_SYMBOL = 3646
    const val VARBIT_ANCIENT_COIN = 3647
    const val VARBIT_OLD_COIN = 3648
    const val VARBIT_POTTERY = 3649

    /** A source: worth [kudos] once [varbit] is at least [atLeast]. */
    class Source(val varbit: Int, val kudos: Int, val atLeast: Int = 1)

    /** The Dig Site exhibit's displays, in the client's list order (enum 1741 rows 0 to 4), 10 Kudos each (enum 1740). */
    val DIG_SITE = listOf(
        Source(VARBIT_OLD_COIN, 10),
        Source(VARBIT_ANCIENT_COIN, 10),
        Source(VARBIT_ANCIENT_SYMBOL, 10),
        Source(VARBIT_OLD_SYMBOL, 10),
        Source(VARBIT_POTTERY, 10),
    )

    val SOURCES: List<Source> = DIG_SITE

    fun total(player: Player): Int = SOURCES.sumOf { if (player.vars.getVarbit(it.varbit) >= it.atLeast) it.kudos else 0 }

    /** How many of [sources] the player has done. */
    fun done(player: Player, sources: List<Source>): Int = sources.count { player.vars.getVarbit(it.varbit) >= it.atLeast }

    /** Writes [total] to the client's count. Called whenever a source changes. */
    fun refresh(player: Player) {
        player.vars.setVarbit(VARBIT_KUDOS, total(player))
    }

    /**
     * At login: the count again, in case the save predates a source. Skipped, with nothing said, when the cache has no
     * museum varbits, as the headless tests' worlds that don't build the museum have none.
     */
    fun login(player: Player) {
        if (player.vars.types.varbit(VARBIT_KUDOS) == null) return
        refresh(player)
    }

    /** The information booth (`vm_information_booth` 24452). */
    const val BOOTH = 24452
    const val BOOTH_LOOK_AT = 1
    const val BOOTH_CHECK_KUDOS = 2

    /** `vm_kudos_info`: the Kudos List, drawn by its own onload script from the museum varbits. */
    const val KUDOS_LIST = 600
}

/** The information booth: Look at shows the museum map (guessed: the Wiki says the booth gives one) and Check Kudos the Kudos List. */
fun registerMuseumKudos(world: World) {
    with(Kudos) {
        world.handlers.onLoc(BOOTH, BOOTH_LOOK_AT) { world.interfaces.openModal(player, FrameSlot.MAINMODAL, MuseumCleaning.MAP_INTERFACE) }
        world.handlers.onLoc(BOOTH, BOOTH_CHECK_KUDOS) { world.interfaces.openModal(player, FrameSlot.MAINMODAL, KUDOS_LIST) }
    }
}
