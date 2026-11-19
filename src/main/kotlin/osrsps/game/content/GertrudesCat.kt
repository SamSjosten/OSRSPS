package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** Fluffs' lines; the words are in the dialogue file, from the Wiki's Transcript:Gertrude's cat (NPC) and Transcript:Gertrude's Cat, read 2026-11-19. */
object FluffsLines : Lines("fluffs", "Transcript:Gertrude's cat (NPC)") {
    val HELLO = line("hello", "with a catspeak amulet, in the Lumber Yard after the quest: the player's greeting")
    val CAN_TALK = line("can_talk", "her first line: so you can talk to cats now?")
    val SPHINX = line("sphinx", "the player: the amulet came from a Sphinx")
    val THANK_YOU = line("thank_you", "her thanks for finding her kitten")
    val CUTE = line("cute", "without a kitten or cat: the player says they're cute")
    val PURR = line("purr", "her purr")
    val PROUD = line("proud", "the box: Fluffs looks proud")
    val FATHER = line("father", "her children take after their father")
    val LOOK_AFTER = line("look_after", "the player's goodbye")
    val YOU_TOO = line("you_too", "her goodbye, {name} the player's", "name")
    val MIAOWW = line("miaoww", "overhead, Talk-to without a catspeak amulet (Transcript:Gertrude's Cat, Finding Fluffs)")
    val HISS = line("hiss", "overhead, Pick-up (Transcript:Gertrude's Cat, Pick-up Fluffs)")
    val PURR_OVERHEAD = line("purr_overhead", "overhead, Stroke (Transcript:Gertrude's Cat, her purr as the kitten comes back)")
}

/**
 * Gertrude's cat, Fluffs (`gertrudescat` 3497), up the ladder in the Lumber Yard, where the Wiki's Fluffs page has her
 * after Gertrude's Cat (map at 3310,3506, upstairs). Talk-to with a catspeak amulet worn (4677, or the enchanted 6544)
 * follows the transcript's Lumber Yard talk without a kitten or cat.
 *
 * Guessed: without the amulet she says "Miaoww" overhead, as during the quest (the transcript has no line for it after);
 * Pick-up gets her quest hiss and Stroke a purr (the transcript has neither after the quest), with no scratch; every
 * chathead expression. Left out: the talk with a kitten or cat following (no followers yet), her scratch for a player
 * with a cat in the bank, and feeding her.
 */
object GertrudesCat {
    const val ID = 3497
    const val PICK_UP = 1
    const val STROKE = 3
    const val TALK_TO = 4

    /** The amulet of catspeak and its enchanted form. */
    val CATSPEAK_AMULETS = listOf(4677, 6544)

    internal suspend fun Dialogue.talk() = with(FluffsLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.HAPPY, CAN_TALK)
        chatPlayer(ChatAnim.NEUTRAL, SPHINX)
        chatNpc(ChatAnim.NEUTRAL, THANK_YOU)
        chatPlayer(ChatAnim.HAPPY, CUTE)
        chatNpc(ChatAnim.HAPPY, PURR)
        mesbox(PROUD)
        chatNpc(ChatAnim.HAPPY, FATHER)
        chatPlayer(ChatAnim.NEUTRAL, LOOK_AFTER)
        chatNpc(ChatAnim.HAPPY, YOU_TOO, "name" to player.username)
    }
}

fun registerGertrudesCat(world: World) {
    fun Npc.say(line: Line) = effects.say(world.dialogueText[line])
    with(GertrudesCat) {
        world.handlers.onNpc(ID, TALK_TO) { op ->
            val npc = op.npc ?: return@onNpc
            if (CATSPEAK_AMULETS.any { player.equipment.count(it) > 0 }) {
                world.dialogues.start(this, npc) { talk() }
            } else {
                npc.say(FluffsLines.MIAOWW)
            }
        }
        world.handlers.onNpc(ID, PICK_UP) { op -> op.npc?.say(FluffsLines.HISS) }
        world.handlers.onNpc(ID, STROKE) { op -> op.npc?.say(FluffsLines.PURR_OVERHEAD) }
    }
}
