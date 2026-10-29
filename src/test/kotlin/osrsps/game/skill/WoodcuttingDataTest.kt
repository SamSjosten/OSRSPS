package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * woodcutting.json against the OSRS Wiki's tree pages (read 2026-10-29). Each chart point is the chance at a level, out
 * of 256 (the Wiki shows exact 256ths), or [FULL] for the first level the chart reaches 100%.
 */
class WoodcuttingDataTest {
    private val data = WoodcuttingData.load()

    private fun tree(key: String) = data.trees.values.first { it.key == key }

    private fun check(key: String, points: List<List<Pair<Int, Int>>>) {
        val rates = tree(key).rates
        for ((tier, chart) in TIERS.zip(points)) {
            val (low, high) = rates.getValue(tier)
            for ((level, chance) in chart) {
                if (chance == FULL) {
                    assertEquals(256, SuccessRoll.chance(low, high, level), "$key $tier reaches 100% at $level")
                    assertTrue(SuccessRoll.chance(low, high, level - 1) < 256, "$key $tier is under 100% at ${level - 1}")
                } else {
                    assertEquals(chance, SuccessRoll.chance(low, high, level), "$key $tier at level $level")
                }
            }
        }
    }

    @Test
    fun `regular trees`() = check(
        "regular",
        listOf(
            listOf(1 to 65, 99 to 201), listOf(1 to 97, 78 to FULL), listOf(1 to 129, 47 to FULL), listOf(1 to 145, 37 to FULL),
            listOf(1 to 161, 29 to FULL), listOf(1 to 193, 17 to FULL), listOf(1 to 225, 8 to FULL), listOf(1 to 241, 4 to FULL),
            listOf(1 to 251, 2 to FULL),
        ),
    )

    @Test
    fun `dead and evergreen trees chop like regular ones`() {
        assertEquals(tree("regular").rates, tree("dead").rates)
        assertEquals(tree("regular").rates, tree("evergreen").rates)
    }

    @Test
    fun oaks() = check(
        "oak",
        listOf(
            listOf(15 to 43, 99 to 101), listOf(15 to 64, 99 to 151), listOf(15 to 84, 99 to 201), listOf(15 to 95, 99 to 226),
            listOf(15 to 105, 99 to 251), listOf(15 to 126, 78 to FULL), listOf(15 to 147, 60 to FULL), listOf(15 to 157, 53 to FULL),
            listOf(15 to 165, 48 to FULL),
        ),
    )

    @Test
    fun willows() = check(
        "willow",
        listOf(
            listOf(30 to 27, 99 to 51), listOf(30 to 40, 99 to 76), listOf(30 to 53, 99 to 101), listOf(30 to 59, 99 to 113),
            listOf(30 to 66, 99 to 126), listOf(31 to 80, 99 to 151), listOf(41 to 106, 99 to 176), listOf(61 to 139, 99 to 188),
            listOf(71 to 159, 99 to 196),
        ),
    )

    @Test
    fun maples() = check(
        "maple",
        listOf(
            listOf(45 to 17, 99 to 26), listOf(45 to 24, 99 to 38), listOf(45 to 32, 99 to 51), listOf(45 to 36, 99 to 57),
            listOf(45 to 40, 99 to 63), listOf(45 to 48, 99 to 76), listOf(45 to 55, 99 to 88), listOf(61 to 70, 99 to 94),
            listOf(71 to 82, 99 to 101),
        ),
    )

    @Test
    fun yews() = check(
        "yew",
        listOf(
            listOf(60 to 10, 99 to 13), listOf(60 to 15, 99 to 20), listOf(60 to 19, 99 to 26), listOf(60 to 21, 99 to 29),
            listOf(60 to 24, 99 to 32), listOf(60 to 28, 99 to 38), listOf(60 to 33, 99 to 45), listOf(61 to 36, 99 to 48),
            listOf(71 to 41, 99 to 51),
        ),
    )

    @Test
    fun `magic trees`() = check(
        "magic",
        listOf(
            listOf(75 to 6, 99 to 7), listOf(75 to 9, 99 to 10), listOf(75 to 11, 99 to 13), listOf(75 to 12, 99 to 14),
            listOf(75 to 14, 99 to 16), listOf(75 to 16, 99 to 19), listOf(75 to 19, 99 to 22), listOf(75 to 19, 99 to 23),
            listOf(75 to 20, 99 to 24),
        ),
    )

    @Test
    fun `Lumbridge's trees and their stumps`() {
        assertEquals(1342, data.stumps[1276])
        assertEquals(1342, data.stumps[1278])
        assertEquals(1351, data.stumps[1286])
        assertEquals(1352, data.stumps[1365])
        assertEquals(1358, data.stumps[1383])
        assertEquals(1356, data.stumps[10820])
        assertEquals(9711, data.stumps[10819])
        assertEquals("dead", data.trees.getValue(1282).key)
    }

    @Test
    fun `levels, XP, timers and respawns from the Wiki`() {
        val expected = mapOf(
            "regular" to listOf(1, 250, null, 60, 100), "oak" to listOf(15, 375, 45, 14, 14), "willow" to listOf(30, 675, 50, 14, 14),
            "maple" to listOf(45, 1000, 100, 59, 59), "yew" to listOf(60, 1750, 190, 99, 99), "magic" to listOf(75, 2500, 390, 199, 199),
        )
        for ((key, values) in expected) {
            val tree = tree(key)
            assertEquals(values, listOf(tree.level, tree.xpTenths, tree.despawn, tree.respawnLow, tree.respawnHigh), key)
        }
    }

    @Test
    fun `axes best first, with the Wiki's levels`() {
        assertEquals(listOf(71, 61, 61, 61, 41, 41, 31, 21, 11, 6, 1, 1), data.axes.map { it.tool.level })
        assertEquals(listOf(1351, 879), data.axes.last().let { listOf(it.tool.obj, it.tool.anim) })
    }

    private companion object {
        const val FULL = -1
        val TIERS = listOf("bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "crystal")
    }

    @Test
    fun `a tree timer runs down to 0 and no further, and refills while nobody chops`() {
        // An oak (45 ticks) chopped down to 0, then 20 more ticks of failed rolls: still 0.
        var left = 44
        repeat(64) { left = Woodcutting.runDown(left, 0, 45) }
        assertEquals(0, left)
        // 30 idle ticks refill it to 30, and the tick of chopping takes one off.
        assertEquals(29, Woodcutting.runDown(left, 30, 45))
        // A long wait fills it to the top.
        assertEquals(44, Woodcutting.runDown(0, 1000, 45))
    }
}
