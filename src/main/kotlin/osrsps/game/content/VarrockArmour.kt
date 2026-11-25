package osrsps.game.content

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.WearPos

/**
 * Varrock armour 1 to 4 (13104 to 13107), the Varrock Diary's rewards, worn: from the Wiki's pages for each (read
 * 2026-11-19), a 10% chance of a second ore when mining an ore rock and of a second bar when smelting at the Edgeville
 * furnace, each up to a level by tier. Ore up to gold, Lovakite, adamantite or any (by the rock's Mining level: 40, 65, 70);
 * bars up to steel, mithril, adamantite or any (by the bar's Smithing level: 30, 50, 70).
 *
 * Left out: armour 3 and 4 standing for a chef's hat at the Cooks' Guild (step 9), armour 4 as a prospector jacket (no
 * prospector kit is built), a second tephra, amethyst, sunstone or guardian fragment (none are built here) and the second
 * gem roll (the Wiki's note for gem rocks, which have no double here). Guessed: the second ore, bar or quarry piece is
 * one more of the same, with its XP, after the first; the second bar takes the ores for it and isn't made without them;
 * a second iron bar is smelted as the first, spending a ring of forging use or else impure half the time.
 */
object VarrockArmour {
    val PIECES = listOf(13104, 13105, 13106, 13107)

    /** The highest Mining level of a rock whose ore doubles, by tier; the last is any. */
    private val ORE_LEVEL = listOf(40, 65, 70, Int.MAX_VALUE)

    /** The highest Smithing level of a bar that doubles, by tier. */
    private val BAR_LEVEL = listOf(30, 50, 70, Int.MAX_VALUE)

    /** The Edgeville furnace's building, from the Wiki's map of it. */
    private val EDGEVILLE_FURNACE = DiaryZone(3105, 3496, 3111, 3502)

    /** The worn armour's tier, 0 to 3, or null. */
    fun tier(player: Player): Int? = player.equipment[WearPos.BODY]?.let { worn -> PIECES.indexOf(worn.id).takeIf { it >= 0 } }

    /** Whether the armour mines a second ore from a rock of Mining level [level] this time. */
    fun doubleOre(player: Player, random: GameRandom, level: Int): Boolean {
        val tier = tier(player) ?: return false
        return level <= ORE_LEVEL[tier] && random.chance(1, 10)
    }

    /**
     * Whether the armour mines a second piece of sandstone or granite this time: armour 2 and up (the Wiki's Mining
     * enhancers, read 2026-11-25), though sandstone's level would let armour 1 in by [ORE_LEVEL].
     */
    fun doubleQuarry(player: Player, random: GameRandom): Boolean {
        val tier = tier(player) ?: return false
        return tier >= 1 && random.chance(1, 10)
    }

    /** Whether the armour smelts a second bar of Smithing level [level] this time, at a furnace on tile [x], [z]. */
    fun doubleBar(player: Player, random: GameRandom, level: Int, x: Int, z: Int): Boolean {
        val tier = tier(player) ?: return false
        return EDGEVILLE_FURNACE.hasTile(x, z) && level <= BAR_LEVEL[tier] && random.chance(1, 10)
    }
}
