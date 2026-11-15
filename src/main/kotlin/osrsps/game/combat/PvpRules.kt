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
    val TARGET_IN_COMBAT = line("target_in_combat", "Attack on a player another player or a monster is fighting, in single-way combat")
    val SKULL_PREVENTED = line("skull_prevented", "Attack refused by the PK Skull Prevention setting, as it would skull you")
    val FROZEN = line("frozen", "A player's ice spell froze you (words from the Wiki's Freeze page)")
    val TELEBLOCKED = line("teleblocked", "A player's Tele Block landed on you, naming the caster (words from the Wiki's Tele Block page)", "name")
    val TELEBLOCK_REMOVED = line("teleblock_removed", "You were credited with killing the player whose Tele Block was on you, naming them (words from the Wiki's Tele Block page)", "name")
    val TELEBLOCK_ALREADY = line("teleblock_already", "Tele Block cast on a player who is Tele Blocked already (Wiki, Tele Block: immune while it lasts; the words unsourced)")
    val TELEPORT_BLOCKED = line("teleport_blocked", "Any teleport tried while Tele Blocked (the words unsourced)")
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
