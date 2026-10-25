package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Doomsayer's lines; the words are in the dialogue file, from the Wiki's Transcript:Doomsayer (Standard dialogue), read 2026-10-25. */
object DoomsayerLines : Lines("doomsayer", "Transcript:Doomsayer") {
    val DOOM = line("doom", "the Doomsayer's first line")
    val WHERE = line("where", "the player's first question")
    val EVERYWHERE = line("everywhere", "the Doomsayer's answer")
    val STOP_IT = line("stop_it", "the player's second question")
    val MY_ROLE = line("my_role", "the Doomsayer naming his role")
    val TITLE = line("title", "the player asking about the title")
    val PATROL = line("patrol", "the Doomsayer's answer about the title")
    val DISABLE = line("disable", "the Doomsayer on turning warnings off")
    val DANGER_AREAS = line("danger_areas", "the Doomsayer on what the warnings mark")
    val SEE_AGAIN = line("see_again", "the player asking how to see the warnings again")
    val WAITING = line("waiting", "the Doomsayer's answer")
    val RE_ENABLE = line("re_enable", "the Doomsayer offering to turn warnings back on")
    val REMEMBER = line("remember", "with no warnings turned off: the player's thanks")
    val WELCOME = line("welcome", "with no warnings turned off: the Doomsayer's last line")
}

/**
 * The Doomsayer outside Lumbridge Castle's gate: spawned as multi-NPC 119, which players see as 6773 while varbit
 * 4153 is 0. From the Wiki: Talk-to, for a player who has turned off no warnings (none can be turned off here yet).
 * Not built: the warnings and their interface, so Toggle warnings does nothing. Guessed: every chathead expression.
 */
object Doomsayer {
    /** doomsayer_normal: the Doomsayer players see. */
    const val ID = 6773
    const val TALK_TO = 1

    internal suspend fun Dialogue.talk() = with(DoomsayerLines) {
        chatNpc(ChatAnim.SCARED, DOOM)
        chatPlayer(ChatAnim.CONFUSED, WHERE)
        chatNpc(ChatAnim.SCARED, EVERYWHERE)
        chatPlayer(ChatAnim.QUIZ, STOP_IT)
        chatNpc(ChatAnim.NEUTRAL, MY_ROLE)
        chatPlayer(ChatAnim.QUIZ, TITLE)
        chatNpc(ChatAnim.NEUTRAL, PATROL)
        chatNpc(ChatAnim.NEUTRAL, DISABLE)
        chatNpc(ChatAnim.NEUTRAL, DANGER_AREAS)
        chatPlayer(ChatAnim.QUIZ, SEE_AGAIN)
        chatNpc(ChatAnim.NEUTRAL, WAITING)
        chatNpc(ChatAnim.NEUTRAL, RE_ENABLE)
        chatPlayer(ChatAnim.HAPPY, REMEMBER)
        chatNpc(ChatAnim.NEUTRAL, WELCOME)
    }
}

/** The Doomsayer's Talk-to. Toggle warnings does nothing yet. */
fun registerDoomsayer(world: World) {
    world.handlers.onNpc(Doomsayer.ID, Doomsayer.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Doomsayer) { talk() } }
    }
}
