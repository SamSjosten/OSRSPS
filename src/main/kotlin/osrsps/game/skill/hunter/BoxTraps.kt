package osrsps.game.skill.hunter

/**
 * Box trapping (Hunter step 2): a box trap laid near ferrets, chinchompas and carnivorous (red) chinchompas.
 *
 * Sources: the OSRS Wiki's Box trap page (read 2026-11-15) for the creatures, their levels and XP, and that laying one
 * plays the tinderbox's animation; Chinchompa (Hunter) and Carnivorous chinchompa for their catch pairs, Mod Ash's
 * "6/255 at level 1 and 268/255 at level 99" for greys and "-78 to 228" for reds (out of 255; [osrsps.game.skill.SuccessRoll]
 * works in 256ths, a difference under half a percent), and their drops: the chinchompa itself, one a catch. Ferret
 * (Hunter) for the ferret's level, XP and drop (the ferret). Ids from the rev 242 cache: the trap is hunting_box_trap,
 * its locs hunting_boxtrap_empty (set), _failing, _failed, _full_<creature> and four _trapping_<creature>_<side>
 * each. Guessed: the ferret's catch pair (its Wiki page has none yet), taken as the grey chinchompa's. Left out: black
 * chinchompas (the Wilderness thread builds them), embertailed jerboas and letvek (Varlamore), baiting and smoking,
 * the tufts (only on a Hunters' Rumour) and the baby chinchompa pet.
 */
object BoxTraps {
    const val BOX_TRAP = 10008 // hunting_box_trap
    const val SET = 9380 // hunting_boxtrap_empty
    const val FAILING = 9381 // hunting_boxtrap_failing
    const val BROKEN = 9385 // hunting_boxtrap_failed

    /** human_createfire: the Wiki's Box trap trivia, the tinderbox's animation. */
    const val LAY_ANIM = 733

    const val FERRET = 1505 // hunting_ferret
    const val CHINCHOMPA = 2910 // hunting_chinchompa
    const val CARNIVOROUS_CHINCHOMPA = 2911 // hunting_chinchompa_big

    const val FERRET_ITEM = 10092 // hunting_ferret
    const val CHINCHOMPA_ITEM = 10033 // chinchompa_captured
    const val RED_CHINCHOMPA_ITEM = 10034 // chinchompa_big_captured

    val CREATURES = listOf(
        // hunting_boxtrap_full_ferret, _trapping_ferret_n/e/s/w.
        Creature(FERRET, 27, 1152, 6, 268, 9394, 9384, listOf(CreatureLoot(FERRET_ITEM, 1)), listOf(9394, 9395, 9396, 9397)),
        // _full_chinchompa, _trapping_chinchompa_n/e/s/w.
        Creature(CHINCHOMPA, 53, 1984, 6, 268, 9386, 9382, listOf(CreatureLoot(CHINCHOMPA_ITEM, 1)), listOf(9386, 9387, 9388, 9389)),
        // _full_chinchompa_big, _trapping_chinchompa_big_n/e/s/w.
        Creature(CARNIVOROUS_CHINCHOMPA, 63, 2650, -78, 228, 9390, 9383, listOf(CreatureLoot(RED_CHINCHOMPA_ITEM, 1)), listOf(9390, 9391, 9392, 9393)),
    )

    val KIND = TrapKind("box trap", BOX_TRAP, SET, FAILING, BROKEN, LAY_ANIM, CREATURES, HunterLines.INVESTIGATE_BOX)
}
