package osrsps.game.skill

import osrsps.game.GameRandom

/**
 * The roll most gathering and cooking actions make, from the OSRS Wiki's Skilling success rate page (read 2026-10-29):
 * an action has a [low] and a [high] value, and at level L it succeeds with chance
 * `(1 + floor(low * (99 - L) / 98 + high * (L - 1) / 98 + 0.5)) / 256`, clamped to 0..1. So level 1 gives (1 + low)/256
 * and 99 gives (1 + high)/256. The Wiki's skill charts are this formula's values, exact 256ths, which is how each
 * resource's pair is worked back and checked (see the skill data files).
 */
object SuccessRoll {
    const val DENOMINATOR = 256

    /**
     * The chance at [level], out of [DENOMINATOR], clamped to 0..256. The level is the current (boosted) one; past 99
     * the line carries on, as the Wiki's formula does, until the clamp.
     */
    fun chance(low: Int, high: Int, level: Int): Int {
        // floor(x / 98 + 0.5) in whole numbers: floor((2x + 98) / 196), with x = low (99 - L) + high (L - 1).
        val x = low.toLong() * (99 - level) + high.toLong() * (level - 1)
        val rounded = Math.floorDiv(2 * x + 98, 196)
        return (1 + rounded).coerceIn(0L, DENOMINATOR.toLong()).toInt()
    }

    /** One roll at [level] from the world's one random source. */
    fun roll(random: GameRandom, low: Int, high: Int, level: Int): Boolean =
        random.chance(chance(low, high, level), DENOMINATOR)
}
