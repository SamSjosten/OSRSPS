package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.WearPos

/**
 * Ava's devices (Draynor piece step 12c), worn in the cape slot: each sends some of the player's fired ammunition back to
 * where it came from (OSRS Wiki, Ava's device, read 2026-11-02). A fifth of all fired ammunition breaks with or without
 * one; of the rest the attractor sends 60% back (20% falls), the accumulator 72% (8% falls) and the assembler 80% (none
 * falls). None works while the player wears metallic torso armour, the Wiki's list ([INTERFERING]).
 */
object AvaDevices {
    const val ATTRACTOR = 10498
    const val ACCUMULATOR = 10499
    const val ASSEMBLER = 22109

    /** Out of 100: how much ammunition breaks on landing, with or without a device. */
    const val BROKEN = 20

    /** Out of 100, the share of fired ammunition each device sends back. */
    val RECOVERED: Map<Int, Int> = mapOf(ATTRACTOR to 60, ACCUMULATOR to 72, ASSEMBLER to 80)

    /**
     * The torsos the Wiki's Metallic interference table marks "Interferes" (read 2026-11-02), every state of each: the
     * barrows tops' degraded ones and the PvP Arena's (`br_`) copies, and the three Castle Wars decorative platebodies.
     * Every other body, bronze and the (h) and god-d'hide ones among them, lets a device work.
     */
    val INTERFERING: Set<Int> = setOf(
        1111, 1123, 20415, 2607, 2599, // adamant chainbody, platebody (and its PvP Arena copy), (g), (t)
        4712, 4868, 4869, 4870, 4871, 4872, 20598, // Ahrim's robetop
        1107, 1125, 2591, 2583, // black chainbody, platebody, (g), (t)
        4069, 4504, 4509, // decorative armour platebodies (red, white, gold)
        4720, 4892, 4893, 4894, 4895, 4896, 25515, // Dharok's platebody
        3140, 20428, 21892, // dragon chainbody (and its PvP Arena copy), dragon platebody
        29562, 3481, // elite black platebody, gilded platebody
        4728, 4916, 4917, 4918, 4919, 4920, // Guthan's platebody
        2669, 2661, 2653, // Guthix, Saradomin and Zamorak platebodies
        1101, 1115, // iron chainbody, platebody
        22327, // Justiciar chestguard
        1109, 1121, // mithril chainbody, platebody
        1113, 1127, 20421, 2615, 2623, // rune chainbody, platebody (and its PvP Arena copy), (g), (t)
        27834, // Statius's platebody (bh)
        1119, // steel platebody
        4749, 4964, 4965, 4966, 4967, 4968, // Torag's platebody
        4757, 4988, 4989, 4990, 4991, 4992, 27190, // Verac's brassard
    )

    /** Out of 100, how much of [player]'s fired ammunition comes back now: 0 with no device or with interference. */
    fun recovery(player: Player): Int {
        val device = player.equipment[WearPos.CAPE]?.id ?: return 0
        val recovered = RECOVERED[device] ?: return 0
        if (player.equipment[WearPos.BODY]?.id in INTERFERING) return 0
        return recovered
    }
}
