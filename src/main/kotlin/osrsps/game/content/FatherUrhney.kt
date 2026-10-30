package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * Father Urhney's lines once The Restless Ghost is done; the words are in the dialogue file, from the Wiki's
 * Transcript:Father Urhney (its post-quest section, and the repossess branch it reuses), read 2026-10-30.
 */
object UrhneyLines : Lines("father_urhney", "Transcript:Father Urhney") {
    val GO_AWAY = line("go_away", "his first line, telling the player to leave")
    val FRIENDLY = line("friendly", "option 1, which the player then says")
    val STILL_GO = line("still_go", "after option 1: he tells the player to leave again")
    val GROUCH = line("grouch", "after option 1: the player's last line")
    val LOST_AMULET = line("lost_amulet", "option 2, which the player then says")
    val HAVE_IT = line("have_it", "option 2 with the amulet carried or worn")
    val IN_BANK = line("in_bank", "option 2 with the amulet in the bank")
    val NO_SPACE = line("no_space", "option 2 with no amulet and no free inventory slot")
    val SPARE = line("spare", "option 2 with no amulet and a free slot: he has a spare")
    val HANDS_AMULET = line("hands_amulet", "the item box with the amulet he hands over")
    val CAREFUL = line("careful", "after the amulet: he asks the player to take more care")
    val OKAY = line("okay", "after the amulet: the player's last line")
    val REPOSSESS = line("repossess", "option 3, which the player then says")
    val GROUNDS = line("grounds", "after option 3: he asks on what grounds")
    val MORTGAGE = line("mortgage", "the grounds menu, option 1, which the player then says")
    val WHAT = line("what", "after the mortgage: his one-word surprise")
    val BUILT_IT = line("built_it", "after the mortgage: he has no mortgage and built the house himself")
    val WRONG_HOUSE = line("wrong_house", "after the mortgage: the player apologises, the wrong house")
    val WHAT_HOUSES = line("what_houses", "after the mortgage: he asks what houses")
    val NEVER_MIND = line("never_mind", "after the mortgage: the player's last line")
    val WANTED_IT = line("wanted_it", "the grounds menu, option 2, which the player then says")
    val GET_OUT = line("get_out", "after option 2 of the grounds menu: he sends the player off")
}

/**
 * Father Urhney in his house in the south-west of Lumbridge Swamp (NPC 923), as he is after The Restless Ghost, which
 * every account has done. He hands out a new ghostspeak amulet to a player who has lost theirs.
 *
 * From the Wiki: each branch's lines and which one the amulet's whereabouts pick (carried, banked, or none with or
 * without a free slot). Guessed: every chathead expression; that a worn amulet counts as carried (the transcript says
 * only the inventory); that the bank is the only store checked (there is no Death's Office or item retrieval yet); his
 * sigh, an action in the transcript, which is shown as no page of its own; and that the amulet goes in before the item
 * box shows it.
 */
object FatherUrhney {
    const val ID = 923
    const val TALK_TO = 1

    /** amulet_of_ghostspeak. */
    const val GHOSTSPEAK_AMULET = 552

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit) = with(UrhneyLines) {
        chatNpc(ChatAnim.ANGRY, GO_AWAY)
        when (choice(FRIENDLY, LOST_AMULET, REPOSSESS)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, FRIENDLY)
                chatNpc(ChatAnim.ANGRY, STILL_GO)
                chatPlayer(ChatAnim.ANGRY, GROUCH)
            }
            2 -> lostAmulet(give)
            else -> repossess()
        }
    }

    private suspend fun Dialogue.lostAmulet(give: (Player, Int) -> Unit) = with(UrhneyLines) {
        chatPlayer(ChatAnim.SAD, LOST_AMULET)
        when {
            carries(player) -> chatNpc(ChatAnim.ANGRY, HAVE_IT)
            player.bank.count(GHOSTSPEAK_AMULET) > 0 -> chatNpc(ChatAnim.ANGRY, IN_BANK)
            player.inventory.freeSlots == 0 -> chatNpc(ChatAnim.ANGRY, NO_SPACE)
            else -> {
                chatNpc(ChatAnim.ANGRY, SPARE)
                give(player, GHOSTSPEAK_AMULET)
                objbox(GHOSTSPEAK_AMULET, HANDS_AMULET)
                chatNpc(ChatAnim.NEUTRAL, CAREFUL)
                chatPlayer(ChatAnim.NEUTRAL, OKAY)
            }
        }
    }

    private suspend fun Dialogue.repossess() = with(UrhneyLines) {
        chatPlayer(ChatAnim.NEUTRAL, REPOSSESS)
        chatNpc(ChatAnim.QUIZ, GROUNDS)
        if (choice(MORTGAGE, WANTED_IT) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, MORTGAGE)
            chatNpc(ChatAnim.CONFUSED, WHAT)
            chatNpc(ChatAnim.ANGRY, BUILT_IT)
            chatPlayer(ChatAnim.SAD, WRONG_HOUSE)
            chatNpc(ChatAnim.CONFUSED, WHAT_HOUSES)
            chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, WANTED_IT)
            chatNpc(ChatAnim.ANGRY, GET_OUT)
        }
    }

    fun carries(player: Player): Boolean =
        player.inventory.count(GHOSTSPEAK_AMULET) > 0 || player.equipment.count(GHOSTSPEAK_AMULET) > 0
}

/** Father Urhney's Talk-to. */
fun registerFatherUrhney(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { player.inventory.add(it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(FatherUrhney.ID, FatherUrhney.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(FatherUrhney) { talk(give) } }
    }
}
