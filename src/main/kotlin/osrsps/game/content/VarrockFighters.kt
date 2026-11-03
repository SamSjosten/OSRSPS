package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Blue Moon Inn barbarian's lines, from the Wiki's Transcript:Barbarian ("Blue Moon Inn Barbarian"), read 2026-11-02. */
object BlueMoonBarbarianLines : Lines("blue_moon_barbarian", "Transcript:Barbarian") {
    val HELLO = line("hello", "the player's first line")
    val WHAT_WANT = line("what_want", "her answer, before the menu")
    val DRINK = line("drink", "option 1 (no line from the player)")
    val DRINK_1 = line("drink_1", "option 1: she laughs at the offer", "name")
    val DRINK_2 = line("drink_2", "option 1: the player asks again")
    val DRINK_3 = line("drink_3", "option 1: she'll test the player anyway")
    val DRINK_4 = line("drink_4", "option 1: the player asks what the test is")
    val DRINK_5 = line("drink_5", "option 1: the barbarians' scream; can the player do it?")
    val YELL = line("yell", "option 1: her scream, in her box and over her head, twice")
    val PLAYER_YELL = line("player_yell", "option 1: the player's scream, twice")
    val DRINK_END = line("drink_end", "option 1: her last line", "name")
    val FIGHT = line("fight", "option 2, which the player then says")
    val FIGHT_ANSWER = line("fight_answer", "option 2: her answer")
    val UM = line("um", "option 3, which the player then says")
    val UM_ANSWER = line("um_answer", "option 3: her answer")
}

/**
 * The city's fighters (the Varrock piece's step 12): their combat, drops and spawns are data (npc-combat.json,
 * npc-drops.json, npc-spawns/varrock.json); this is the Blue Moon Inn's barbarian's talk, her own on the Wiki's
 * Transcript:Barbarian. Guessed: every expression; that she screams first and they take turns; that her scream shows
 * over her head as the talk reaches it. Left out: the player's scream over their own head (no overhead text for
 * players yet).
 */
object VarrockFighters {
    /** barbarianwoman_varrock, the Level 8 barbarian in the Blue Moon Inn. */
    const val BLUE_MOON_BARBARIAN = 3262
    const val TALK_TO = 1

    internal suspend fun Dialogue.blueMoonBarbarian(name: String) = with(BlueMoonBarbarianLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.LAUGH, WHAT_WANT)
        when (choice(DRINK, FIGHT, UM)) {
            1 -> {
                chatNpc(ChatAnim.LAUGH, DRINK_1, "name" to name)
                chatPlayer(ChatAnim.NEUTRAL, DRINK_2)
                chatNpc(ChatAnim.NEUTRAL, DRINK_3)
                chatPlayer(ChatAnim.QUIZ, DRINK_4)
                chatNpc(ChatAnim.NEUTRAL, DRINK_5)
                repeat(2) {
                    npc?.effects?.say(text(YELL))
                    chatNpc(ChatAnim.ANGRY, YELL)
                    chatPlayer(ChatAnim.ANGRY, PLAYER_YELL)
                }
                chatNpc(ChatAnim.LAUGH, DRINK_END, "name" to name)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, FIGHT)
                chatNpc(ChatAnim.LAUGH, FIGHT_ANSWER)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, UM)
                chatNpc(ChatAnim.LAUGH, UM_ANSWER)
            }
        }
    }
}

fun registerVarrockFighters(world: World) {
    world.handlers.onNpc(VarrockFighters.BLUE_MOON_BARBARIAN, VarrockFighters.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(VarrockFighters) { blueMoonBarbarian(player.username) } }
    }
}
