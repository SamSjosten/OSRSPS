package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Cadmus's lines; the words are in the dialogue file, from the Wiki's Transcript:Cadmus, read 2026-11-08. */
object CadmusLines : Lines("cadmus", "Transcript:Cadmus") {
    val HELLO = line("hello", "Standard dialogue: the player's hello")
    val GREETING = line("greeting", "Standard dialogue: his greeting")
    val WHAT_DO_YOU_DO = line("what_do_you_do", "first menu: what he does here, which the player then says")
    val STEWARD = line("steward", "what he does: the castle's steward")
    val ENTAIL = line("entail", "what he does: the player asking what that entails")
    val REPAIRS = line("repairs", "what he does: repair work")
    val HANDYMAN = line("handyman", "what he does: the player calling him a handyman")
    val LEARNED = line("learned", "what he does: he learned some crafting")
    val TIPS = line("tips", "menu after what he does: crafting tips, which the player then says")
    val OF_COURSE = line("of_course", "crafting tips: his answer, before the advice")
    val BEST_BE_OFF = line("best_be_off", "menu after what he does: best be off, which the player then says")
    val ADVICE = line("advice", "first menu: any advice, which the player then says")
    val ADVICE_1 = line("advice_1", "advice: he knows a bit about crafting")
    val ADVICE_2 = line("advice_2", "advice: crafting is a catch-all term")
    val ADVICE_3 = line("advice_3", "advice: spinning yarn first, his spinning wheel")
    val MATERIALS = line("materials", "advice: the player asking about materials")
    val ADVICE_4 = line("advice_4", "advice: balls of wool")
    val WHEEL_HINT = line("wheel_hint", "advice: the spinning wheel icon's box (item 34765)")
    val ADVICE_5 = line("advice_5", "advice: one of many things")
    val GUIDE_HINT = line("guide_hint", "advice: the Skills panel box (end of dialogue)")
    val GOOD = line("good", "first menu: good, thanks, which the player then says")
    val VERY_WELL = line("very_well", "good, thanks or best be off: his answer (end of dialogue)")
}

/**
 * Cadmus (NPC 16807), the castle's steward: the Crafting tutor renamed in 242.1, beside the spinning wheel on the
 * castle's first floor.
 *
 * From the Wiki: every branch, its lines and its order, and the spinning wheel icon's box. Guessed: every chathead
 * expression, and that the Skills panel box is a plain message box (we have no sprite box).
 */
object Cadmus {
    const val ID = 16807
    const val TALK_TO = 1

    /** spinning_wheel_icon_dummy, new in 242. */
    const val SPINNING_WHEEL_ICON = 34765

    internal suspend fun Dialogue.talk() = with(CadmusLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(WHAT_DO_YOU_DO, ADVICE, GOOD)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_DO_YOU_DO)
                chatNpc(ChatAnim.NEUTRAL, STEWARD)
                chatPlayer(ChatAnim.QUIZ, ENTAIL)
                chatNpc(ChatAnim.NEUTRAL, REPAIRS)
                chatPlayer(ChatAnim.HAPPY, HANDYMAN)
                chatNpc(ChatAnim.NEUTRAL, LEARNED)
                if (choice(TIPS, BEST_BE_OFF) == 1) {
                    chatPlayer(ChatAnim.QUIZ, TIPS)
                    chatNpc(ChatAnim.HAPPY, OF_COURSE)
                    advice()
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, BEST_BE_OFF)
                    chatNpc(ChatAnim.NEUTRAL, VERY_WELL)
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, ADVICE)
                advice()
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, GOOD)
                chatNpc(ChatAnim.NEUTRAL, VERY_WELL)
            }
        }
    }

    /** The advice, from "he knows a bit" to the Skills panel box; the talk ends after it. */
    private suspend fun Dialogue.advice() = with(CadmusLines) {
        chatNpc(ChatAnim.HAPPY, ADVICE_1)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_2)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_3)
        chatPlayer(ChatAnim.QUIZ, MATERIALS)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_4)
        objbox(SPINNING_WHEEL_ICON, WHEEL_HINT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_5)
        mesbox(GUIDE_HINT)
    }
}

/** Cadmus's Talk-to. */
fun registerCadmus(world: World) {
    world.handlers.onNpc(Cadmus.ID, Cadmus.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Cadmus) { talk() } }
    }
}
