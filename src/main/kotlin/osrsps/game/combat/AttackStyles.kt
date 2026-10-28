package osrsps.game.combat

import osrsps.game.Skill

/** What an attack hits with, which picks the target's defence bonus. */
enum class AttackType {
    STAB,
    SLASH,
    CRUSH,
    RANGED,
    MAGIC,

    /** A style with no attack type in its tooltip: a bulwark's block, a gun's "Aim and Fire". */
    NONE,
    ;

    val melee: Boolean get() = this == STAB || this == SLASH || this == CRUSH
}

/**
 * The weight a ranged style's tooltip names, which picks the target's ranged defence bonus: light for thrown weapons,
 * standard for bows, heavy for crossbows, as the 241 cache's tooltips have them.
 */
enum class RangedWeight {
    LIGHT,
    STANDARD,
    HEAVY,
}

/** The combat tab's word for a style: what it adds to the player's effective levels. */
enum class AttackStance {
    ACCURATE,
    AGGRESSIVE,
    DEFENSIVE,
    CONTROLLED,
    RAPID,
    LONGRANGE,

    /** Anything else: a bulwark's block, "Aim and Fire". */
    OTHER,
}

/**
 * One button of the combat tab for one weapon category: [button] is the value of varp 43 (com_mode) that picks it.
 * [xp] lists the skills its damage trains, Hitpoints aside. A ranged style also has its [weight].
 */
data class AttackStyle(
    val button: Int,
    val name: String,
    val stance: AttackStance,
    val type: AttackType,
    val xp: List<Skill>,
    val weight: RangedWeight? = null,
)

/**
 * The combat tab's attack styles for every weapon category, from the cache's dbtable 78 (combat_interface_weapon_category):
 * column 0 is the category (varbit 357's value), column 1 a tuple per button of (button, name, tooltip, sprite). The
 * tooltip spells the style out, "(Accurate)<br>(Crush)<br>(Attack XP)", and is what the client shows; it is read here
 * so the server agrees with the tab. Checked on the 241 cache (2026-10-26): every category 0 to 35 reads.
 */
class AttackStyles(private val byCategory: Map<Int, List<AttackStyle>>) {
    /** The styles of [category], by button. */
    fun styles(category: Int): List<AttackStyle> = byCategory[category].orEmpty()

    /** The style button [button] picks for [category], or null when the category has no such button. */
    fun style(category: Int, button: Int): AttackStyle? = styles(category).firstOrNull { it.button == button }

    /** How many weapon categories are known: 1 with only [DEFAULT]'s unarmed styles. */
    val categories: Int get() = byCategory.size

    companion object {
        /**
         * The unarmed styles alone, as the 241 cache has them, for tests and a cache with no dbtable 78: Punch, Kick and
         * Block on buttons 0, 1 and 3.
         */
        val DEFAULT = AttackStyles(
            mapOf(
                0 to listOf(
                    AttackStyle(0, "Punch", AttackStance.ACCURATE, AttackType.CRUSH, listOf(Skill.ATTACK)),
                    AttackStyle(1, "Kick", AttackStance.AGGRESSIVE, AttackType.CRUSH, listOf(Skill.STRENGTH)),
                    AttackStyle(3, "Block", AttackStance.DEFENSIVE, AttackType.CRUSH, listOf(Skill.DEFENCE)),
                ),
            ),
        )

        /**
         * Builds the table from dbtable 78's rows, each as its column 0 (the category) and column 1 (the buttons'
         * tuples, flattened). Rows that don't read are skipped and named in [problems].
         */
        fun fromRows(rows: Collection<Pair<List<Any>?, List<Any>?>>, problems: MutableList<String> = ArrayList()): AttackStyles {
            val byCategory = HashMap<Int, List<AttackStyle>>()
            for ((categoryColumn, buttonsColumn) in rows) {
                val category = categoryColumn?.firstOrNull() as? Int
                if (category == null || buttonsColumn == null || buttonsColumn.size % TUPLE != 0) {
                    problems += "a row with category $categoryColumn and buttons $buttonsColumn doesn't read"
                    continue
                }
                byCategory[category] = buttonsColumn.chunked(TUPLE).map { (button, name, tooltip) ->
                    parse(button as Int, name as String, tooltip as String)
                }
            }
            return AttackStyles(byCategory)
        }

        /** One button's style from its tooltip: the bracketed words, in any order. */
        fun parse(button: Int, name: String, tooltip: String): AttackStyle {
            val words = BRACKETED.findAll(tooltip).map { it.groupValues[1].lowercase() }.toList()
            val stance = words.firstNotNullOfOrNull { STANCES[it] } ?: AttackStance.OTHER
            val type = words.firstNotNullOfOrNull { TYPES[it] } ?: AttackType.NONE
            val xp = words.flatMap { XP[it].orEmpty() }
            val weight = words.firstNotNullOfOrNull { WEIGHTS[it] }
            return AttackStyle(button, name, stance, type, xp, weight)
        }

        private const val TUPLE = 4

        private val BRACKETED = Regex("""\(([^)]*)\)""")

        private val STANCES = mapOf(
            "accurate" to AttackStance.ACCURATE,
            "aggressive" to AttackStance.AGGRESSIVE,
            "defensive" to AttackStance.DEFENSIVE,
            "controlled" to AttackStance.CONTROLLED,
            "rapid" to AttackStance.RAPID,
            "longrange" to AttackStance.LONGRANGE,
            // Chinchompas' fuses are the accurate, rapid and longrange styles under other names (OSRS Wiki, Chinchompa
            // (weapon): their speeds, ranges and XP are those styles').
            "short fuse" to AttackStance.ACCURATE,
            "medium fuse" to AttackStance.RAPID,
            "long fuse" to AttackStance.LONGRANGE,
        )

        /** Ranged styles name their ammunition's weight (light, standard, heavy) instead of "Ranged". */
        private val TYPES = mapOf(
            "stab" to AttackType.STAB,
            "slash" to AttackType.SLASH,
            "crush" to AttackType.CRUSH,
            "ranged" to AttackType.RANGED,
            "light" to AttackType.RANGED,
            "standard" to AttackType.RANGED,
            "heavy" to AttackType.RANGED,
            "magic" to AttackType.MAGIC,
        )

        private val WEIGHTS = mapOf("light" to RangedWeight.LIGHT, "standard" to RangedWeight.STANDARD, "heavy" to RangedWeight.HEAVY)

        private val XP = mapOf(
            "attack xp" to listOf(Skill.ATTACK),
            "strength xp" to listOf(Skill.STRENGTH),
            "defence xp" to listOf(Skill.DEFENCE),
            "shared xp" to listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
            "ranged xp" to listOf(Skill.RANGED),
            "magic xp" to listOf(Skill.MAGIC),
        )
    }
}
