package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Twiggy O'Korn's lines; the words are in the dialogue file, from the Wiki's Transcript:Twiggy O'Korn, read 2026-11-19. */
object TwiggyLines : Lines("twiggy", "Transcript:Twiggy O'Korn") {
    val HEY_THERE = line("hey_there", "her greeting, before every menu")
    val CHOCOLATE = line("chocolate", "the 1 in 50 chance: her chocolate")
    val WOW_THANKS = line("wow_thanks", "the 1 in 50 chance: the player's thanks")
    val WHO_ARE_YOU = line("who_are_you", "menu option 1, which the player then says")
    val MISTRESS = line("mistress", "who she is: her answer")
    val LIKEWISE = line("likewise", "who she is: the player's answer")
    val NICE_CAPE = line("nice_cape", "menu option 2, which the player then says")
    val CAPE = line("cape", "the cape: her answer")
    val GOODBYE = line("goodbye", "menu option 3, which the player then says")
}

/**
 * Twiggy O'Korn (5527) south of Draynor Village bank, the Achievement Diaries' mistress, and her squirrel (5528, no
 * options), spawned in npc-spawns/diaries.json. She sells the Achievement diary cape and hood for 99,000 coins to whoever
 * has done every diary; only two of the twelve are built, so nobody can, and she always talks as to a player who hasn't
 * (the Wiki's transcript, "has not completed all Achievement Diaries"): her menu, and her chocolate bar 1 time in 50 (the
 * Wiki's drop table). Her shop waits until every diary is built.
 *
 * Guessed: every chathead expression, and the menu coming back after "Who are you?" (the transcript's "other" goes on to
 * the other options).
 */
object Twiggy {
    const val ID = 5527
    const val SQUIRREL = 5528
    const val TALK_TO = 1
    const val CHOCOLATE_BAR = 1973

    internal suspend fun Dialogue.talk(world: World) = with(TwiggyLines) {
        chatNpc(ChatAnim.HAPPY, HEY_THERE)
        if (world.random.chance(1, 50)) {
            chatNpc(ChatAnim.HAPPY, CHOCOLATE)
            world.objTypes[CHOCOLATE_BAR]?.let { world.groundObjs.give(player, it, 1) }
            chatPlayer(ChatAnim.HAPPY, WOW_THANKS)
        }
        while (true) {
            when (choice(WHO_ARE_YOU, NICE_CAPE, GOODBYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO_ARE_YOU)
                    chatNpc(ChatAnim.HAPPY, MISTRESS)
                    chatPlayer(ChatAnim.HAPPY, LIKEWISE)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, NICE_CAPE)
                    chatNpc(ChatAnim.HAPPY, CAPE)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.HAPPY, GOODBYE)
                    return@with
                }
            }
        }
    }
}

fun registerTwiggy(world: World) {
    world.handlers.onNpc(Twiggy.ID, Twiggy.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Twiggy) { talk(world) } }
    }
}
