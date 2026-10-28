package osrsps.game

/**
 * The seven animations the client plays while the player stands, turns, walks and runs (a base animation set).
 * Live sends one for each weapon; the cache has the animations but not which weapon uses which.
 */
data class Stance(
    val ready: Int,
    val turn: Int,
    val walk: Int,
    val walkBack: Int,
    val walkLeft: Int,
    val walkRight: Int,
    val run: Int,
)

/**
 * What the wielded weapon decides: its combat tab (weapon category) and its stance.
 *
 * Verified in the 241 cache: the weapon categories 0 to 32 and their attack styles are rows of dbtable 78
 * (combat_interface_weapon_category), which the combat tab reads through varbit 357 (combat_weapon_category); every
 * animation id below has the gameval name in its comment.
 *
 * Inferred: which items get which category and stance. The cache gives each item a category (obj opcode 94) that
 * groups weapons (35 holds every axe and battleaxe, 21 every scimitar and longsword), so categories are mapped from
 * it by the names of the items in each, with a few by item name. Unmapped weapons fall back to unarmed and the human
 * stance. None of it is checked against live; a weapon that shows the wrong tab or walks oddly is a line here.
 */
object Weapons {
    /** Weapon categories, the values of varbit 357, by their dbtable 78 row names. */
    const val UNARMED = 0
    const val AXE = 1
    const val BLUNT = 2
    const val BOW = 3
    const val CLAW = 4
    const val CROSSBOW = 5
    const val FLAMER = 6
    const val GRENADE = 7
    const val HACKSWORD = 9
    const val HEAVYSWORD = 10
    const val PICKAXE = 11
    const val POLEARM = 12
    const val SCYTHE = 14
    const val SPEAR = 15
    const val SPIKED = 16
    const val STABSWORD = 17
    const val STAFF = 18
    const val THROWN = 19
    const val WHIP = 20
    const val BANNER = 25
    const val BLUNT_BLUDGEON = 27
    const val BULWARK = 28
    const val PARTISAN = 30

    /**
     * The categories whose dbtable 78 row has buttons 0, 1 and 3. Besides the named ones: 13 polestaff, 21 and 22
     * bladed staves, 24 and 29 self-powering staves and wands, 26 chargespear, 32 egg.
     */
    private val THREE_STYLE = setOf(UNARMED, BLUNT, BLUNT_BLUDGEON, BOW, CROSSBOW, GRENADE, POLEARM, 13, 26, STAFF, 21, 22, THROWN, WHIP, 24, 29, 32)

    /**
     * The attack style buttons [category] has, from dbtable 78's button column. Varp 43 (com_mode) holds the one the
     * player picked. 8 is the gun and 31 the tribrid weapon.
     */
    fun styleButtons(category: Int): List<Int> = when (category) {
        8 -> listOf(0, 1)
        FLAMER, 31 -> listOf(0, 1, 2)
        BULWARK -> listOf(0, 3)
        in THREE_STYLE -> listOf(0, 1, 3)
        else -> listOf(0, 1, 2, 3)
    }

    /** Obj category (opcode 94) to weapon category, from the names of the 241 items in each obj category. */
    private val CATEGORY_BY_OBJ_CATEGORY = mapOf(
        1 to STAFF, // staves, wands, sceptres
        21 to HACKSWORD, // scimitars, longswords
        24 to THROWN, // darts, knives, thrownaxes, the blowpipe
        25 to STABSWORD, // daggers, swords
        26 to BLUNT, // warhammers, mauls
        35 to AXE, // axes, battleaxes
        36 to SPEAR, // spears, hastas
        37 to CROSSBOW,
        39 to SPIKED, // maces
        42 to BANNER,
        61 to HEAVYSWORD, // 2h swords, godswords
        64 to BOW,
        65 to CLAW,
        66 to POLEARM, // halberds
        67 to PICKAXE,
        106 to BOW, // crystal bows, Bow of Faerdhinen
        150 to WHIP,
        273 to POLEARM, // crystal halberds
        567 to CROSSBOW, // ballistas
        572 to GRENADE, // chinchompas
        586 to FLAMER, // salamanders
        1014 to BULWARK,
        1193 to SCYTHE,
        1588 to PARTISAN,
    )

    /**
     * What the combat tab shows after "Category:", by weapon category. The words are the OSRS Wiki's weapon type names
     * (Weapons/Types, read 2026-10-28), matched to the categories by their dbtable 78 row names. Guessed: 22 (spellblade
     * staff), 26 (chargespear), 29 (self-powering wand) and 33 (sailing cannon), which the Wiki doesn't name apart.
     */
    private val CATEGORY_NAMES = listOf(
        "Unarmed", "Axe", "Blunt", "Bow", "Claw", "Crossbow", "Salamander", "Chinchompa", "Gun", "Slash sword",
        "2h sword", "Pickaxe", "Polearm", "Polestaff", "Scythe", "Spear", "Spiked", "Stab sword", "Staff", "Thrown",
        "Whip", "Bladed staff", "Bladed staff", "2h sword", "Powered Staff", "Banner", "Polearm", "Bludgeon", "Bulwark",
        "Powered Staff", "Partisan", "Multi-style", "Egg", "Blaster", "Multi-melee", "Flail",
    )

    fun categoryName(category: Int): String = CATEGORY_NAMES.getOrElse(category) { CATEGORY_NAMES[UNARMED] }

    fun category(weapon: ObjType?): Int {
        if (weapon == null) return UNARMED
        if (weapon.internalName == "abyssal_bludgeon") return BLUNT_BLUDGEON
        return CATEGORY_BY_OBJ_CATEGORY[weapon.category] ?: UNARMED
    }

    /** human_ready, human_turnonspot, human_walk_f, human_walk_b, human_walk_l, human_walk_r, human_running. */
    val HUMAN = Stance(808, 823, 819, 820, 821, 822, 824)

    /** human_staffready, then human_halberdturnonspot, human_halberdwalk_f/_b/_l/_r and human_halberdrunning. */
    val STAFF_STANCE = Stance(813, 1209, 1205, 1206, 1207, 1208, 1210)

    /** human_ds_ready with the halberd walks; guessed from the name, not seen. */
    val HALBERD_STANCE = Stance(809, 1209, 1205, 1206, 1207, 1208, 1210)

    /** human_dh_weapon_ready, _walk and _run; the human turn. */
    val TWO_HANDED_STANCE = Stance(2561, 823, 2562, 2562, 2562, 2562, 2563)

    /** dh_sword_update_ready, _turnonspot, _walk, _walk_left, _walk_right and _run. */
    val GODSWORD_STANCE = Stance(7053, 7044, 7052, 7052, 7048, 7047, 7043)

    /** slayer_abyssal_whip_walk and _run with the human ready and turn. */
    val WHIP_STANCE = Stance(808, 823, 1660, 1660, 1660, 1660, 1661)

    /** slayer_granite_maul_ready, _walk and _run. */
    val GRANITE_MAUL_STANCE = Stance(1662, 823, 1663, 1663, 1663, 1663, 1664)

    /** human_elder_maul_ready, _walk and _run. */
    val ELDER_MAUL_STANCE = Stance(7518, 823, 7520, 7520, 7520, 7520, 7519)

    /** human_dinhs_bulwark_ready, _walk and _run. */
    val BULWARK_STANCE = Stance(7508, 823, 7510, 7510, 7510, 7510, 7509)

    /** xbows_human_ready, xbows_human_walk_f and _b, xbows_human_run; the human turn and side steps. */
    val CROSSBOW_STANCE = Stance(4591, 823, 4226, 4227, 821, 822, 4228)

    /** human_chinchompa_ready, _walk and _run. */
    val CHINCHOMPA_STANCE = Stance(3175, 823, 3177, 3177, 3177, 3177, 3178)

    /** human_bannerready, human_bannerturnonspot, human_bannerwalk_f/_b/_l/_r, human_bannerrunning. */
    val BANNER_STANCE = Stance(1421, 1426, 1422, 1423, 1424, 1425, 1427)

    fun stance(weapon: ObjType?): Stance {
        if (weapon == null) return HUMAN
        val name = weapon.internalName.orEmpty()
        return when {
            weapon.name.contains("godsword", ignoreCase = true) -> GODSWORD_STANCE
            name.startsWith("granite_maul") -> GRANITE_MAUL_STANCE
            name.startsWith("elder_maul") -> ELDER_MAUL_STANCE
            else -> when (category(weapon)) {
                STAFF -> if (weapon.name.contains("staff", ignoreCase = true)) STAFF_STANCE else HUMAN
                POLEARM -> HALBERD_STANCE
                HEAVYSWORD -> TWO_HANDED_STANCE
                WHIP -> WHIP_STANCE
                BULWARK -> BULWARK_STANCE
                CROSSBOW -> CROSSBOW_STANCE
                GRENADE -> CHINCHOMPA_STANCE
                BANNER -> BANNER_STANCE
                else -> HUMAN
            }
        }
    }
}
