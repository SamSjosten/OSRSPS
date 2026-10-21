package osrsps.game

/** Something the player clicked and is walking to use: a loc's option, an obj on the ground. See [Interactions]. */
fun interface Interaction {
    /**
     * Uses the target if the player has reached it. Returns true once the interaction is over: used, or gone. It may
     * set the player's interaction to a new one, which then stays.
     */
    fun tryInteract(player: Player): Boolean
}

/**
 * Each tick's movement phase tries the player's interaction before they move and again after, so a target next to the
 * player is used on the tick the click is read, and one further away on the tick the player arrives. That two-check
 * order is the RS2 world cycle as the 2004Scape project reconstructed it, assumed to carry over to OSRS; task 21 checks
 * it against recordings. A target the route never reaches says "I can't reach that!" once the player stops, live's
 * message.
 */
class Interactions(
    /** What the player is told; tests swap it for a recorder, as they have no client. */
    private val message: (Player, String) -> Unit = Player::message,
) {
    /**
     * Runs [player]'s part of the movement phase: their interaction, if any, around [move], which walks them one tick.
     * A delayed player's interaction waits for the delay to end.
     */
    fun process(player: Player, move: () -> Unit) {
        val interaction = player.interaction
        if (interaction == null || player.scripts.delayed || tryInteract(player, interaction)) {
            move()
            return
        }
        move()
        if (player.interaction !== interaction || tryInteract(player, interaction)) return
        if (player.waypoints.isEmpty()) {
            player.interaction = null
            message(player, CANT_REACH)
        }
    }

    private fun tryInteract(player: Player, interaction: Interaction): Boolean {
        if (!interaction.tryInteract(player)) return false
        if (player.interaction === interaction) player.interaction = null
        return true
    }

    companion object {
        const val CANT_REACH = "I can't reach that!"
    }
}
