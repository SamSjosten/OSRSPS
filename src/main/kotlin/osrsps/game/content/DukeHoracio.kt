package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * Duke Horacio's lines once Rune Mysteries and Dragon Slayer I are done; the words are in the dialogue file, from the
 * Wiki's Transcript:Duke Horacio (its one section, Standard dialogue), read 2026-10-25.
 */
object DukeLines : Lines("duke", "Transcript:Duke Horacio") {
    val GREETING = line("greeting", "the Duke's first line")
    val SHIELD = line("shield", "the player asking for a shield against dragon breath (an option, shown with no anti-dragon shield)")
    val WHICH_DRAGON = line("which_dragon", "shield: the Duke asking which dragon")
    val NO_DRAGON = line("no_dragon", "shield: what the player says after 'no dragon in particular' (longer than the option)")
    val EARNED = line("earned", "shield, after Dragon Slayer I: the Duke's answer")
    val HANDS_SHIELD = line("hands_shield", "shield, after Dragon Slayer I: the line as he hands it over")
    val QUESTS = line("quests", "the player asking for quests (an option, which the player then says)")
    val ALL_WELL = line("all_well", "quests, after giving the talisman to Sedridor: the Duke's answer")
    val MONEY = line("money", "the player asking where to find money (an option, which the player then says)")
    val SMITHING = line("smithing", "money: the Duke's answer")
}

/**
 * Duke Horacio (NPC 815) in his room on the castle's first floor, as he is once every quest is done. He gives an
 * anti-dragon shield to a player who has none.
 *
 * From the Wiki: the lines in each branch, that the shield option shows only without an anti-dragon shield, and that he
 * hands one over after Dragon Slayer I. Guessed: every chathead expression; the first menu's order (the Wiki doesn't show
 * it); that after Dragon Slayer I the "which dragon" menu, left with one option, isn't shown and the player just says
 * it; that the shield goes in before the line showing it; that "has one" means carried or worn (there is no bank yet);
 * and that with a full inventory the shield lands at the player's feet, theirs alone, as a drop does.
 */
object DukeHoracio {
    const val ID = 815
    const val TALK_TO = 1

    /** antidragonbreathshield. */
    const val ANTI_DRAGON_SHIELD = 1540

    internal suspend fun Dialogue.talk(world: World) = with(DukeLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        val options = listOfNotNull(SHIELD.takeUnless { hasShield(player) }, QUESTS, MONEY)
        when (options[choice(*options.toTypedArray()) - 1]) {
            SHIELD -> {
                chatPlayer(ChatAnim.QUIZ, SHIELD)
                chatNpc(ChatAnim.QUIZ, WHICH_DRAGON)
                chatPlayer(ChatAnim.NEUTRAL, NO_DRAGON)
                chatNpc(ChatAnim.HAPPY, EARNED)
                giveShield(world, player)
                objbox(ANTI_DRAGON_SHIELD, HANDS_SHIELD)
            }
            QUESTS -> {
                chatPlayer(ChatAnim.QUIZ, QUESTS)
                chatNpc(ChatAnim.HAPPY, ALL_WELL)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, MONEY)
                chatNpc(ChatAnim.NEUTRAL, SMITHING)
            }
        }
    }

    fun hasShield(player: Player): Boolean =
        player.inventory.count(ANTI_DRAGON_SHIELD) > 0 || player.equipment.count(ANTI_DRAGON_SHIELD) > 0

    private fun giveShield(world: World, player: Player) {
        val type = world.objTypes[ANTI_DRAGON_SHIELD] ?: return logger.warn { "No anti-dragon shield in the cache" }
        if (player.inventory.add(type, 1) == 1) return
        world.groundObjs.addPrivate(
            type, 1, player.x, player.z, player.level, player.saveKey,
            GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS,
        )
    }
}

/** Duke Horacio's Talk-to. */
fun registerDukeHoracio(world: World) {
    world.handlers.onNpc(DukeHoracio.ID, DukeHoracio.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(DukeHoracio) { talk(world) } }
    }
}
