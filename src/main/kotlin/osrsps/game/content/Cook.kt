package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The Cook's lines, after Cook's Assistant and Recipe for Disaster; the words are in the dialogue file, from the Wiki's
 * Transcript:Cook (Lumbridge), read 2026-10-25. That page's "After Recipe for Disaster" gives his own first line and
 * then the same options as "After Cook's Assistant".
 */
object CookLines : Lines("cook", "Transcript:Cook (Lumbridge)") {
    val GREETING = line("greeting", "After Recipe for Disaster: the Cook's first line")
    val OTHER_QUESTS = line("other_quests", "After Cook's Assistant: option 1")
    val NOTHING_TO_DO = line("nothing_to_do", "After Cook's Assistant: the Cook's answer to option 1 on free-to-play worlds")
    val STRONG = line("strong", "After Cook's Assistant: option 2")
    val STRONG_SAID = line("strong_said", "After Cook's Assistant: what the player says after option 2 (longer than the option)")
    val GLAD = line("glad", "After Cook's Assistant: the Cook's answer to option 2")
    val DYING = line("dying", "After Cook's Assistant: option 3, which the player then says")
    val COME_BACK = line("come_back", "After Cook's Assistant: the Cook's answer to option 3")
    val RANGE = line("range", "After Cook's Assistant: option 4, which the player then says")
    val RANGE_YES = line("range_yes", "After Cook's Assistant, option 4: the Cook's first line")
    val RANGE_NAME = line("range_name", "After Cook's Assistant, option 4: the Cook's second line, naming the range")
    val BURN_LESS = line("burn_less", "After Cook's Assistant, option 4: the player's question about burning")
    val SALESMAN = line("salesman", "After Cook's Assistant, option 4: the Cook's answer")
    val THANKS = line("thanks", "After Cook's Assistant, option 4: the player's last line")
}

/**
 * The Cook (NPC 4626) in Lumbridge Castle's kitchen, as he is once Cook's Assistant and Recipe for Disaster are done,
 * which every account here has. Guessed: every chathead expression (the Wiki notes none), and option 1. On members'
 * worlds the Wiki has option 1 lead into Recipe for Disaster's start, and gives nothing for after the quest, so he
 * answers as on a free-to-play world, straight after the option with no line from the player.
 */
object Cook {
    const val ID = 4626
    const val TALK_TO = 1

    internal suspend fun Dialogue.talk() = with(CookLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(OTHER_QUESTS, STRONG, DYING, RANGE)) {
            1 -> chatNpc(ChatAnim.SAD, NOTHING_TO_DO)
            2 -> {
                chatPlayer(ChatAnim.ANGRY, STRONG_SAID)
                chatNpc(ChatAnim.HAPPY, GLAD)
            }
            3 -> {
                chatPlayer(ChatAnim.SAD, DYING)
                chatNpc(ChatAnim.LAUGH, COME_BACK)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, RANGE)
                chatNpc(ChatAnim.HAPPY, RANGE_YES)
                chatNpc(ChatAnim.HAPPY, RANGE_NAME)
                chatPlayer(ChatAnim.QUIZ, BURN_LESS)
                chatNpc(ChatAnim.SHIFTY, SALESMAN)
                chatPlayer(ChatAnim.CONFUSED, THANKS)
            }
        }
    }
}

/** The Cook's Talk-to. */
fun registerCook(world: World) {
    world.handlers.onNpc(Cook.ID, Cook.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Cook) { talk() } }
    }
}
