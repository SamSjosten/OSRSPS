package osrsps.game.combat

import osrsps.game.Weapons

/**
 * The animations a player fights with for one weapon category: [attack] by the style they swing with, and [defend]
 * when they are hit. Ids are the 241 cache's gameval names (human_unarmedpunch 422 and so on); which style plays which
 * is guessed from the names, not from a recording, and only the categories a Lumbridge fighter holds are filled in.
 * Any other category punches.
 */
class WeaponAnims(private val attackAnim: (AttackStyle) -> Int, val defend: Int) {
    fun attack(style: AttackStyle): Int = attackAnim(style)

    companion object {
        /** human_unarmedpunch, human_unarmedkick for the Kick style, human_unarmedblock. */
        val UNARMED = WeaponAnims({ if (it.name == "Kick") 423 else 422 }, 424)

        /** human_sword_stab, human_sword_lunge for Lunge, human_sword_slash; human_sword_def. */
        private val SWORD = WeaponAnims(
            {
                when {
                    it.name == "Lunge" -> 392
                    it.type == AttackType.SLASH -> 390
                    else -> 386
                }
            },
            388,
        )

        /** human_axe_chop for Chop, human_axe_hack, human_axe_smash for crush; human_axe_def. */
        private val AXE = WeaponAnims(
            {
                when {
                    it.name == "Chop" -> 393
                    it.type == AttackType.CRUSH -> 396
                    else -> 395
                }
            },
            398,
        )

        /** human_spear_spike, human_spear_lunge for Lunge; human_spear_def. */
        private val SPEAR = WeaponAnims({ if (it.name == "Lunge") 429 else 428 }, 431)

        /** human_dinhs_bulwark_crush for Pummel; human_dinhs_bulwark_block. Guessed from the names, as above. */
        private val BULWARK = WeaponAnims({ 7507 }, 7512)

        private val BY_CATEGORY = mapOf(
            Weapons.UNARMED to UNARMED,
            Weapons.STABSWORD to SWORD,
            Weapons.HACKSWORD to SWORD,
            Weapons.AXE to AXE,
            Weapons.SPEAR to SPEAR,
            Weapons.BULWARK to BULWARK,
        )

        /** The animations of weapon [category] (varbit 357's value); [UNARMED] for one not filled in. */
        fun of(category: Int): WeaponAnims = BY_CATEGORY[category] ?: UNARMED
    }
}
