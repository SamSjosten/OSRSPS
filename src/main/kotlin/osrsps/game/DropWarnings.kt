package osrsps.game

import osrsps.game.dialogue.Dialogues
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptRunner

/** The drop item warning's words, in the dialogue file; the chatbox's look and words are guessed, not from a capture. */
object DropLines : Lines("drop", "Drop") {
    val TITLE = line("warning_title", "the question over the two options, before dropping a valuable item", "item", "value")
    val YES = line("warning_yes", "the option that drops it")
    val NO = line("warning_no", "the option that keeps it")
}

/**
 * The All Settings "Drop item warning" (varbit option_dropwarning_on) and its "Minimum item value needed for drop item
 * warning" (option_dropwarning_value): with the warning on, dropping an item whose alchemy value is at least that much
 * asks first. OSRS Wiki, Drop: the warning compares the alchemy value, at or above the threshold, 30,000 coins by
 * default. Guessed: high alchemy, one item of a stack, and a two-option chat menu.
 */
class DropWarnings(
    private val objTypes: ObjTypes,
    private val scripts: ScriptRunner,
    private val dialogues: () -> Dialogues,
    /** Drops the slot's stack; see [GroundObjs.drop]. */
    private val dropNow: (Player, Int) -> Unit,
) {
    fun drop(player: Player, slot: Int) {
        val held = player.inventory[slot] ?: return
        val type = objTypes[held.id]
        val threshold = threshold(player)
        if (type == null || threshold == null || type.highAlch < threshold) {
            dropNow(player, slot)
            return
        }
        scripts.start(player) {
            dialogues().start(this) {
                val title = text(DropLines.TITLE, "item" to type.name, "value" to "%,d".format(type.highAlch))
                if (choice(DropLines.YES, DropLines.NO, title = title) != 1) return@start
                // The menu waited: only the same stack, still in its slot, is dropped.
                if (player.inventory[slot] == held) dropNow(player, slot)
            }
        }
    }

    /** The value that warns, or null with the warning off (or no cache vars, in tests of other things). */
    private fun threshold(player: Player): Int? {
        val types = player.vars.types
        if (types.varbit(VARBIT_ON) == null || types.varbit(VARBIT_VALUE) == null) return null
        if (player.vars.getVarbit(VARBIT_ON) != 1) return null
        return player.vars.getVarbit(VARBIT_VALUE).takeIf { it > 0 }
    }

    companion object {
        const val VARBIT_ON = AllSettings.VARBIT_DROP_WARNING
        const val VARBIT_VALUE = AllSettings.VARBIT_DROP_WARNING_VALUE

        /** OSRS Wiki, Drop: the threshold a new account starts with. */
        const val DEFAULT_THRESHOLD = 30_000
    }
}
