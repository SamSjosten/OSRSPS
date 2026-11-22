package osrsps.game.content.randomevents

import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The Genie's lines; the words are in the dialogue file, from the Wiki's Transcript:Genie (read 2026-11-19). Live says
 * Master or Mistress by the player's look; the words file holds the one form, as the body type isn't kept here.
 */
object GenieLines : Lines("genie", "Transcript:Genie") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val HELLO = line("hello", "Overhead dialogue: while waiting, dialogue 1", "name")
    val EHEM = line("ehem", "Overhead dialogue: while waiting, dialogue 2", "name")
    val WAITING = line("waiting", "Overhead dialogue: while waiting, dialogue 3", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val LAMP = line("lamp", "Standard dialogue: the Genie gives the lamp", "name")
    val SOMEONE_ELSE = line("someone_else", "If the Genie is summoned by another player")
}

/**
 * The Genie (NPC 326, macro_geni; Talk-to, Dismiss), from the OSRS Wiki's Genie page and transcript (read
 * 2026-11-19): Talk-to gives a lamp ([XpLamp]) and ends the event. He is one of the three event NPCs that follow
 * their player into a bank. Guessed: every chathead expression, and that he goes at once after the lamp (the pre-2014
 * Genie waved first). Left out: the underwater Genie (327), as nowhere here is underwater.
 */
object Genie : RandomEvent("genie", 326) {
    override val entersBanks = true
    override val greeting = GenieLines.GREETING
    override val nags = listOf(GenieLines.HELLO, GenieLines.EHEM, GenieLines.WAITING)
    override val leaving = GenieLines.MAYBE_LATER
    override val notYours = GenieLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        chatNpc(ChatAnim.HAPPY, GenieLines.LAMP, "name" to player.username)
        events.reward(player, XpLamp.LAMP)
        return true
    }
}
