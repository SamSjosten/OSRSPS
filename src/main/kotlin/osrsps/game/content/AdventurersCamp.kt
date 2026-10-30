package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The camp's warrior after Lost City; the words are in the dialogue file, from the Wiki's Transcript:Warrior (Lost City), read 2026-10-30. */
object CampWarriorLines : Lines("lost_city_warrior", "Transcript:Warrior (Lost City)") {
    val THANKS = line("thanks", "standard dialogue after Lost City: the player thanks the warrior")
    val PARTY = line("party", "the warrior's worry that the party finds out")
    val ASKS = line("asks", "the warrior asks what the player found")
    val SECRET = line("secret", "the player's last line")
}

/** The camp's archer after Lost City (Transcript:Archer (Lost City)). */
object CampArcherLines : Lines("lost_city_archer", "Transcript:Archer (Lost City)") {
    val FOUND_IT = line("found_it", "standard dialogue after Lost City: the player says they found the way in")
    val DONT = line("dont", "the archer's first reply")
    val YEP = line("yep", "the player again")
    val NOT = line("not", "the archer's second reply")
    val BELIEVE = line("believe", "the player's last line")
}

/** The camp's monk after Lost City (Transcript:Monk (Lost City)). */
object CampMonkLines : Lines("lost_city_monk", "Transcript:Monk (Lost City)") {
    val ANYMORE = line("anymore", "standard dialogue after Lost City: the monk's one line")
}

/** The camp's wizard after Lost City (Transcript:Wizard (Lost City)). */
object CampWizardLines : Lines("lost_city_wizard", "Transcript:Wizard (Lost City)") {
    val LAUGH = line("laugh", "standard dialogue after Lost City: the wizard laughs at the player")
    val GO = line("go", "the wizard sends the player off")
    val RIGHT = line("right", "the player's last line")
}

/**
 * The adventurers' camp in the north-west of Lumbridge Swamp: the warrior (NPC 1158), archer (1157), monk (1159) and
 * wizard (1160) who were looking for Zanaris, as they are after Lost City, which every account has done. Each talks
 * with the Wiki transcript's standard dialogue after the quest (not the warrior's first one, which a player sees
 * once, on finishing it). Guessed: every chathead expression.
 */
object AdventurersCamp {
    const val ARCHER = 1157
    const val WARRIOR = 1158
    const val MONK = 1159
    const val WIZARD = 1160
    const val TALK_TO = 1

    internal suspend fun Dialogue.warrior() = with(CampWarriorLines) {
        chatPlayer(ChatAnim.HAPPY, THANKS)
        chatNpc(ChatAnim.SCARED, PARTY)
        chatNpc(ChatAnim.QUIZ, ASKS)
        chatPlayer(ChatAnim.SHIFTY, SECRET)
    }

    internal suspend fun Dialogue.archer() = with(CampArcherLines) {
        chatPlayer(ChatAnim.HAPPY, FOUND_IT)
        chatNpc(ChatAnim.ANGRY, DONT)
        chatPlayer(ChatAnim.HAPPY, YEP)
        chatNpc(ChatAnim.ANGRY, NOT)
        chatPlayer(ChatAnim.LAUGH, BELIEVE)
    }

    internal suspend fun Dialogue.monk() = with(CampMonkLines) {
        chatNpc(ChatAnim.ANGRY, ANYMORE)
    }

    internal suspend fun Dialogue.wizard() = with(CampWizardLines) {
        chatNpc(ChatAnim.LAUGH, LAUGH)
        chatNpc(ChatAnim.ANGRY, GO)
        chatPlayer(ChatAnim.NEUTRAL, RIGHT)
    }
}

/** The four adventurers' Talk-to. */
fun registerAdventurersCamp(world: World) {
    world.handlers.onNpc(AdventurersCamp.WARRIOR, AdventurersCamp.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(AdventurersCamp) { warrior() } }
    }
    world.handlers.onNpc(AdventurersCamp.ARCHER, AdventurersCamp.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(AdventurersCamp) { archer() } }
    }
    world.handlers.onNpc(AdventurersCamp.MONK, AdventurersCamp.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(AdventurersCamp) { monk() } }
    }
    world.handlers.onNpc(AdventurersCamp.WIZARD, AdventurersCamp.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(AdventurersCamp) { wizard() } }
    }
}
