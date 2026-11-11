package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.SettingsSide

/**
 * What it costs to take items back from a gravestone (raid step 31), as the OSRS Wiki's Gravestone page gives it and
 * the 241 cache's own scripts work it out for the gravestone's paid list (proc gravestone_generic_window_set and
 * proc script1797; osrs-dumps, read 2026-11-10):
 *
 * - An item is free below 100,000 coins a unit; from there each unit costs 1,000, from 1,000,000 10,000 and from
 *   10,000,000 100,000 ("1,000 for each reclaimed item between 100k and 1m", and so on); "the total combined fees are
 *   capped at 500,000".
 * - An Ironman pays half (script1797: any `ironman` varbit but 0; the Wiki: "Ironmen get a 50% discount").
 * - Then, for a grave not merged with an earlier one (`gravestone_has_merged` 0), made in The Fractured Archive's map
 *   squares (proc script10182) by a player with fewer than 5 clears (`total_completed_thefracturedarchive`), a quarter
 *   of that (the Wiki: "Death costs will be reduced by 75% until the player has cleared the raid 5 times"). The cache
 *   gives the same quarter to the Perilous Moons and other bosses, none of which are built.
 *
 * Inferred: that the cap is reduced as each item's fee is (the script caps every item's shown fee at the reduced
 * 500,000), and that the total is the reduced fees added up, capped there. Rounding down is the script's (`scale`).
 */
object GraveFees {
    const val CAP = 500_000L

    /** total_completed_thefracturedarchive: the player's clears of the raid. */
    const val VARP_ARCHIVE_CLEARS = 5894

    /** The clears under which a death in the raid costs a quarter. */
    const val ARCHIVE_DISCOUNTED_CLEARS = 5

    /** What one unit of an item worth [value] costs to take back. */
    fun unitFee(value: Long): Long = when {
        value >= 10_000_000 -> 100_000
        value >= 1_000_000 -> 10_000
        value >= 100_000 -> 1_000
        else -> 0
    }

    /** [fee] as [player] pays it, for a grave made at [coord] (level shl 28 or x shl 14 or z), [merged] or not. */
    fun reduce(player: Player, fee: Long, coord: Int, merged: Boolean): Long {
        var left = fee
        if (varbit(player, SettingsSide.VARBIT_IRONMAN) != 0) left /= 2
        if (!merged && inArchive(coord) && varp(player, VARP_ARCHIVE_CLEARS) < ARCHIVE_DISCOUNTED_CLEARS) left /= 4
        return left
    }

    /**
     * The total for items of these unit [values] and [counts], each fee reduced ([reduce]) and capped at the reduced
     * [CAP], and the total capped there too.
     */
    fun total(player: Player, units: List<Pair<Long, Int>>, coord: Int, merged: Boolean): Long {
        val cap = reduce(player, CAP, coord, merged)
        val sum = units.sumOf { (value, count) -> minOf(cap, reduce(player, unitFee(value) * count, coord, merged)) }
        return minOf(cap, sum)
    }

    /**
     * proc script10182, the raid's map squares: 63 and 65 by 86 to 88 and 64,87 on any level, and 64,88 on levels 0 to
     * 2 (its zone from 64,87's local 0,56 to 64,88's 63,63, levels 0 to 2, which 64,87's own takes in). Not 64,86, the
     * reward vault (64,90) or the lobby.
     */
    fun inArchive(coord: Int): Boolean {
        if (coord < 0) return false
        val level = coord ushr 28
        val squareX = ((coord ushr 14) and 0x3fff) shr 6
        val squareZ = (coord and 0x3fff) shr 6
        if (level > 3) return false
        return when (squareX) {
            63, 65 -> squareZ in 86..88
            64 -> squareZ == 87 || (squareZ == 88 && level <= 2)
            else -> false
        }
    }

    fun coord(x: Int, z: Int, level: Int): Int = (level shl 28) or (x shl 14) or z

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    private fun varp(player: Player, id: Int): Int = if (id < player.vars.types.varpCount) player.vars[id] else 0
}
