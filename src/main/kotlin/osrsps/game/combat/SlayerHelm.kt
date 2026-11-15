package osrsps.game.combat

/**
 * What a black mask or Slayer helmet worn on task does to a player's attack on the task's monster (OSRS Wiki, Black mask,
 * Slayer helmet): [MELEE] makes melee 7/6 as accurate and hard hitting (Mod Ash's "7/6", the Wiki's 16.67%), [IMBUED]
 * (the black mask (i) or Slayer helmet (i)) also makes ranged and magic 15% more accurate and hard hitting. [NONE] is
 * anything else: no mask, or a monster that isn't the task.
 */
enum class SlayerHelm {
    NONE, MELEE, IMBUED;

    /** [roll] or a max hit with melee's 7/6, rounded down (inferred, as for the other percentage bonuses). */
    fun melee(value: Int): Int = if (this == NONE) value else (value.toLong() * 7 / 6).toInt()

    /** [value] with the imbued mask's 15% for ranged or magic, rounded down (inferred). */
    fun rangedOrMagic(value: Int): Int = if (this == IMBUED) (value.toLong() * 23 / 20).toInt() else value
}
