package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Why an attack on a player is refused (keys only; the words are in data/dialogue.txt). */
object PvpLines : Lines("pvp", "Wilderness") {
    val NOT_IN_WILDERNESS = line("not_in_wilderness", "Attack on a player while the attacker is outside the Wilderness or on its safe rows")
    val TARGET_NOT_IN_WILDERNESS = line("target_not_in_wilderness", "Attack on a player who is outside the Wilderness or on its safe rows")
    val LEVEL_DIFFERENCE = line("level_difference", "Attack on a player whose combat level is out of range for the Wilderness level")
    val MOVE_DEEPER = line("move_deeper", "the line after level_difference, telling the attacker to go deeper")
    val POWERED_STAFF = line("powered_staff", "Attack on a player with a powered staff's own spell, which can't be used on players")
    val FROZEN = line("frozen", "A player's ice spell froze you (Wiki, Freeze: You have been frozen!)")
}

/**
 * Who may attack whom (Wilderness step W1.2): only players in the Wilderness past its safe rows, and only within each
 * other's Wilderness level of combat level ([Wilderness.inRange]). The checks are made as the click is handled, on where
 * both players stand then. The refusals' keys and their order (the attacker's place, the target's, then the range) are
 * inferred; their words wait for a source.
 */
object PvpRules {
    /** The lines refusing [attacker]'s attack on [target], or empty when it is allowed. */
    fun refusal(attacker: Player, target: Player): List<Line> {
        val own = Wilderness.pvpLevel(attacker.x, attacker.z)
        if (own == 0) return listOf(PvpLines.NOT_IN_WILDERNESS)
        val theirs = Wilderness.pvpLevel(target.x, target.z)
        if (theirs == 0) return listOf(PvpLines.TARGET_NOT_IN_WILDERNESS)
        if (!Wilderness.inRange(attacker.skills.combatLevel, own, target.skills.combatLevel, theirs)) {
            return listOf(PvpLines.LEVEL_DIFFERENCE, PvpLines.MOVE_DEEPER)
        }
        return emptyList()
    }
}
