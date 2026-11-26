package osrsps.game.content

import osrsps.game.World
import java.time.Instant
import java.time.ZoneOffset

/**
 * Misthalin's game-wide systems: the poll booths. Sources: the OSRS Wiki's Poll booth page, read 2026-11-19, and the 242
 * cache: the `pollbooth_*` locs, each colour a multiloc on varbit poll_playershouldvote that shows its `_standard` type
 * (0) or its `_highlight` one (1, a poll the player hasn't voted in), both with Use; and the ballot window, interface
 * 928 (`ballot`), whose onload (ballot_floater_init) shows "Loading..." until the server runs ballot_list_build (8325)
 * with the poll history: the year shown, a bitmask of the years with polls (bit 0 = 2013), and each poll's title, its
 * subtitle, its id and its category and status bits.
 *
 * - Use on any poll booth opens the ballot in the frame's floater, where the world map and All Settings go (inferred:
 *   ballot_floater_init drags and sizes it as a floater with toplevel_floater_drag_init), and sends the history.
 * - This server holds no polls, so the history is empty: no years to pick and no polls to view, under "Poll History"
 *   and this year (inferred: what the list shows with nothing in it). poll_playershouldvote stays 0, so every booth shows
 *   its standard look. Picking a year or a poll asks the server through IF_RUNSCRIPT, which this server doesn't answer;
 *   with an empty list neither can be picked.
 * - The window's close button ([CLOSE]; Esc triggers it too) closes it.
 *
 * Misthalin's booths: Edgeville's bank (3092, 3488), Draynor's bank (3096, 3244), the Grand Exchange (3161, 3499),
 * Varrock's west and east banks (3180, 3438 and 3251, 3423), Al Kharid's bank (3272, 3169), Lumbridge Castle's bank
 * (3209, 3215, level 2) and the clan hall's (1745, 5472). Those the map files put on level 1 over a bridge flag (the
 * Grand Exchange's and the clan hall's) stand on level 0. The handler is on every colour, so booths elsewhere work too.
 *
 * Not built: polls themselves (voting pages, results), the Wiki's "Vote here!" hint on a highlighted booth.
 */
object PollBooths {
    /** Every poll booth type: each multiloc and both its shown types (pollbooth_green_noop 50047 has no option). */
    val BOOTHS = listOf(8720, 26492, 26796) + (26797..26820) + listOf(32138, 32546, 32547, 32649, 33481, 33482, 38101)
    const val USE = 1

    /** The poll booth in the clan hall (pollbooth_brown, by the coffer), for the hall's instance locs. */
    const val CLAN_HALL_BOOTH = 26813

    const val BALLOT = 928

    /** ballot:close, the window's X. */
    const val CLOSE = 4

    /** ballot_list_build: (year, years bitmask, titles, subtitles, poll ids, category and status bits). */
    const val SCRIPT_LIST_BUILD = 8325

    /** The year ballot_list_build's bitmask counts from. */
    const val FIRST_YEAR = 2013

    /** The arguments for ballot_list_build with no polls: [year] shown, no years, no polls. */
    fun emptyHistory(year: Int): List<Any> = listOf(year, 0, arrayOf<String>(), arrayOf<String>(), IntArray(0), IntArray(0))

    fun thisYear(now: Instant = Instant.now()): Int = now.atOffset(ZoneOffset.UTC).year

    /** The names the startup check ([osrsps.cache.GameFrameCheck]) holds these ids to. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(BALLOT to ("ballot" to mapOf(CLOSE to "close")))
}

/** Use on every poll booth. */
fun registerPollBooths(world: World) {
    for (booth in PollBooths.BOOTHS) {
        world.handlers.onLoc(booth, PollBooths.USE) {
            arriveDelay()
            world.openBallot(player)
        }
    }
}
