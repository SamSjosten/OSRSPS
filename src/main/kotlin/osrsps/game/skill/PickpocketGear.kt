package osrsps.game.skill

import osrsps.game.EquipmentTab
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.Lines

/** The gloves of silence's and the dodgy necklace's messages. */
object PickpocketGearLines : Lines("pickpocket_gear", "Pickpocketing gear") {
    val GLOVES_NEW = line("gloves_new", "Check on gloves of silence that will last 62 more failures (the Wiki quotes it)")
    val GLOVES_GOOD = line("gloves_good", "Check on gloves of silence with 34 to 61 failures left (the Wiki quotes it)")
    val GLOVES_SHABBY = line("gloves_shabby", "Check on gloves of silence with 20 to 33 failures left (the Wiki quotes it)")
    val GLOVES_STARTING_REPAIR = line("gloves_starting_repair", "Check on gloves of silence with 10 to 19 failures left (the Wiki quotes it)")
    val GLOVES_NEED_REPAIR = line("gloves_need_repair", "Check on gloves of silence with 2 to 9 failures left (the Wiki quotes it)")
    val GLOVES_ABOUT_TO_FALL = line("gloves_about_to_fall", "Check on gloves of silence with 1 failure left (the Wiki quotes it)")
    val GLOVES_WARNING = line("gloves_warning", "a failed pickpocket that leaves the gloves of silence one failure from breaking (the Wiki quotes it)")
    val GLOVES_FELL_APART = line("gloves_fell_apart", "the gloves of silence's last failure: they are gone; words unsourced")
    val NECKLACE_PROTECTS = line("necklace_protects", "the dodgy necklace stops a stun, with the charges left; words unsourced", "charges")
    val NECKLACE_CRUMBLES = line("necklace_crumbles", "the dodgy necklace stops a stun with its last charge and crumbles; words unsourced")
    val NECKLACE_CHECK = line("necklace_check", "Check on a dodgy necklace, with its charges; words unsourced", "charges")
}

/**
 * What worn gear does to a pickpocket (Thieving 2 step T1a), as the OSRS Wiki's Gloves of silence, Dodgy necklace and
 * the NPCs' success charts give it (read 2026-11-10):
 *
 * - Gloves of silence (worn) raise an NPC's low and high by 5%, rounded down: the charts' 150/240 to 157/252 (farmer),
 *   100/240 to 105/252 (Al Kharid warrior), 50/240 to 52/252 (guard). Each failed pickpocket wears them; the 62nd
 *   destroys them. A warning comes when one failure is left. Check says how worn they are, by the Wiki's six messages.
 * - A dodgy necklace (worn) stops a failed pickpocket's stun and damage one time in four, using a charge; the tenth
 *   crumbles it. Check says how many are left.
 * - Both counts belong to the player, not the item ([osrsps.game.Charges]), as the Wiki says for both.
 *
 * Guessed: the bands between the Wiki's six Check messages (the Wiki gives each one's failures left, read as the most
 * failures left that show it); a stopped stun lets the next pickpocket come after [Pickpocketing.ATTEMPT_TICKS] alone;
 * the gloves wear on every failure, even one the necklace stops; the words of the necklace's messages and the gloves'
 * last one. Left out: the Ardougne diary (which turns the gloves off), repairing the gloves, the necklace's Break,
 * Shadow Veil.
 */
class PickpocketGear(private val world: World) {

    fun register() {
        world.objTypes[GLOVES]?.let { gloves ->
            option(gloves.iops, CHECK)?.let { world.handlers.onObj(GLOVES, it) { message(player, glovesCheck(player)) } }
            wornOption(gloves, CHECK)?.let { world.handlers.onWorn(GLOVES, it) { message(player, glovesCheck(player)) } }
        }
        world.objTypes[NECKLACE]?.let { necklace ->
            option(necklace.iops, CHECK)?.let { world.handlers.onObj(NECKLACE, it) { message(player, necklaceCheck(player)) } }
            wornOption(necklace, CHECK)?.let { world.handlers.onWorn(NECKLACE, it) { message(player, necklaceCheck(player)) } }
        }
    }

    /** [low] raised by the gloves when [player] wears them. */
    fun low(player: Player, low: Int): Int = if (wearsGloves(player)) low * GLOVES_PERCENT / 100 else low

    /** [high] raised by the gloves when [player] wears them. */
    fun high(player: Player, high: Int): Int = if (wearsGloves(player)) high * GLOVES_PERCENT / 100 else high

    fun wearsGloves(player: Player) = player.equipment[WearPos.HANDS]?.id == GLOVES

    fun wearsNecklace(player: Player) = player.equipment[WearPos.NECK]?.id == NECKLACE

    /** A failed pickpocket wears the gloves, if worn: the warning one failure before the end, and the end. */
    fun glovesFailed(player: Player) {
        if (!wearsGloves(player)) return
        val used = player.charges[GLOVES_USED] + 1
        if (used >= GLOVES_FAILURES) {
            player.charges[GLOVES_USED] = 0
            player.equipment.remove(WearPos.HANDS, 1)
            message(player, world.textFor(player)[PickpocketGearLines.GLOVES_FELL_APART])
            return
        }
        player.charges[GLOVES_USED] = used
        if (used == GLOVES_FAILURES - 1) message(player, world.textFor(player)[PickpocketGearLines.GLOVES_WARNING])
    }

    /** Whether a worn necklace stops this failure's stun and damage, one time in four; it uses a charge when it does. */
    fun necklaceSaves(player: Player): Boolean {
        if (!wearsNecklace(player)) return false
        if (!world.random.chance(1, NECKLACE_ODDS)) return false
        val used = player.charges[NECKLACE_USED] + 1
        val text = world.textFor(player)
        if (used >= NECKLACE_CHARGES) {
            player.charges[NECKLACE_USED] = 0
            player.equipment.remove(WearPos.NECK, 1)
            message(player, text[PickpocketGearLines.NECKLACE_CRUMBLES])
        } else {
            player.charges[NECKLACE_USED] = used
            message(player, text[PickpocketGearLines.NECKLACE_PROTECTS, "charges" to NECKLACE_CHARGES - used])
        }
        return true
    }

    fun glovesCheck(player: Player): String {
        val left = GLOVES_FAILURES - player.charges[GLOVES_USED]
        val line = when {
            left >= GLOVES_FAILURES -> PickpocketGearLines.GLOVES_NEW
            left >= 34 -> PickpocketGearLines.GLOVES_GOOD
            left >= 20 -> PickpocketGearLines.GLOVES_SHABBY
            left >= 10 -> PickpocketGearLines.GLOVES_STARTING_REPAIR
            left >= 2 -> PickpocketGearLines.GLOVES_NEED_REPAIR
            else -> PickpocketGearLines.GLOVES_ABOUT_TO_FALL
        }
        return world.textFor(player)[line]
    }

    fun necklaceCheck(player: Player): String =
        world.textFor(player)[PickpocketGearLines.NECKLACE_CHECK, "charges" to NECKLACE_CHARGES - player.charges[NECKLACE_USED]]

    private fun message(player: Player, text: String) = player.message(text)

    companion object {
        /** hunting_silent_gloves. */
        const val GLOVES = 10075

        /** dodgy_necklace. */
        const val NECKLACE = 21143

        const val GLOVES_PERCENT = 105
        const val GLOVES_FAILURES = 62
        const val NECKLACE_CHARGES = 10
        const val NECKLACE_ODDS = 4

        /** The player's gloves failures and necklace charges used, in [osrsps.game.Charges]. */
        const val GLOVES_USED = "gloves_of_silence_failures"
        const val NECKLACE_USED = "dodgy_necklace_used"

        private const val CHECK = "Check"

        private fun option(iops: List<String?>, name: String): Int? =
            iops.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)

        private fun wornOption(type: ObjType, name: String): Int? =
            (1..EquipmentTab.WORN_OPS).firstOrNull { type.stringParam(EquipmentTab.PARAM_WORN_OP1 + it - 1).equals(name, ignoreCase = true) }
    }
}
