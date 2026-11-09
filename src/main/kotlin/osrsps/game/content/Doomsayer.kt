package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The Doomsayer's lines as since 242.1, which made his talk "more in-universe"; the words are in the dialogue file, from
 * the Wiki's Transcript:Doomsayer (Standard dialogue), read 2026-11-08. Keys whose words didn't change are kept.
 */
object DoomsayerLines : Lines("doomsayer", "Transcript:Doomsayer") {
    val DOOM = line("doom", "the Doomsayer's first line")
    val WHERE = line("where", "first time: the player's first question")
    val EVERYWHERE = line("everywhere", "first time: the Doomsayer's answer")
    val STOP_IT = line("stop_it", "first time: the player's second question")
    val NOTHING_NEEDED = line("nothing_needed", "first time: nothing to do, he is the Doomsayer and roams sensing danger")
    val DANGER_AREAS = line("danger_areas", "first time: the Doomsayer on what the warnings mark")
    val GET_RID = line("get_rid", "the option to get rid of warning signs, which the player then says")
    val VERY_WELL = line("very_well", "get rid of signs: his answer")
    val THANKS_SIGNS = line("thanks_signs", "first time: the thanks option, which the player then says")
    val WELCOME = line("welcome", "first time, after the thanks: his reply (the Wiki is missing it; the old talk's words guessed)")
    val BE_OFF = line("be_off", "later times: the leaving option, which the player then says")
}

/**
 * The Doomsayer outside Lumbridge Castle's gate: spawned as multi-NPC 119, which players see as 6773 while varbit
 * 4153 is 0. From the Wiki: Talk-to as since 242.1, the first time (varbit doomsayer_intro, 16791, then set) and after.
 * Not built: the warnings and their interface, so getting rid of signs ends after his answer and Toggle warnings does
 * nothing. Guessed: every chathead expression; the thanks branch, which the Wiki marks missing (the player's line, then
 * the old talk's "welcome").
 */
object Doomsayer {
    /** doomsayer_normal: the Doomsayer players see. */
    const val ID = 6773
    const val TALK_TO = 1

    /** doomsayer_intro: the player has heard his first talk. */
    const val VARBIT_INTRO = 16791

    internal suspend fun Dialogue.talk() = with(DoomsayerLines) {
        chatNpc(ChatAnim.SCARED, DOOM)
        val first = player.vars.types.varbit(VARBIT_INTRO) != null && player.vars.getVarbit(VARBIT_INTRO) == 0
        if (first) {
            player.vars.setVarbit(VARBIT_INTRO, 1)
            chatPlayer(ChatAnim.CONFUSED, WHERE)
            chatNpc(ChatAnim.SCARED, EVERYWHERE)
            chatPlayer(ChatAnim.QUIZ, STOP_IT)
            chatNpc(ChatAnim.NEUTRAL, NOTHING_NEEDED)
            chatNpc(ChatAnim.NEUTRAL, DANGER_AREAS)
        }
        val leave = if (first) THANKS_SIGNS else BE_OFF
        if (choice(GET_RID, leave) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, GET_RID)
            chatNpc(ChatAnim.SAD, VERY_WELL)
        } else {
            chatPlayer(ChatAnim.HAPPY, leave)
            // Guessed: the Wiki marks his reply to the thanks missing; the old talk's words.
            if (first) chatNpc(ChatAnim.HAPPY, WELCOME)
        }
    }
}

/** The Doomsayer's Talk-to. Toggle warnings does nothing yet. */
fun registerDoomsayer(world: World) {
    world.handlers.onNpc(Doomsayer.ID, Doomsayer.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Doomsayer) { talk() } }
    }
}
