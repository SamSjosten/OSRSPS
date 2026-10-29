package osrsps.game

import osrsps.game.dialogue.Lines

/**
 * The engine's game messages: login, logout, walking to things and ground items. Their words are in the dialogue file,
 * like every other line of Jagex text (Sam's rule, 2026-10-29); each line's `where` says what shows it and where the
 * words come from.
 */
object GameLines : Lines("game", "Choose Option") {
    val WELCOME = line("welcome", "the first line in the chatbox after logging in")
    val NOTHING_INTERESTING = line("nothing_interesting", "an option or Use that does nothing (the Choose Option page)")
    val CANT_REACH = line("cant_reach", "a target the route never reaches, once the player stops")
    val CANT_LOG_OUT = line("cant_log_out", "the logout button within 10 seconds of being hit (the Logout button page); words from memory")
    val TOO_LATE = line("too_late", "taking a ground item that has gone; RSMod's words (rev 233)")
    val NO_SPACE = line("no_space", "taking a ground item with no room for it; RSMod's words (rev 233)")
    val TILE_FULL = line("tile_full", "dropping an item on a tile that already holds 128; words guessed")
}
