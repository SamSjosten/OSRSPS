package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals

class MiningTest {
    private fun rock(key: String) = Mining.ROCKS.values.first { it.type.key == key }.type

    /** Points from the Wiki's success charts (read 2026-10-29), as 256ths. */
    private val charts = mapOf(
        "clay" to mapOf(1 to 129, 10 to 154, 30 to 209, 46 to 254, 47 to 256),
        "copper" to mapOf(1 to 101, 2 to 104, 10 to 124, 40 to 200, 50 to 226, 60 to 252, 62 to 256),
        "tin" to mapOf(1 to 101, 20 to 149, 30 to 175, 55 to 239, 61 to 254, 62 to 256),
        "iron" to mapOf(15 to 133, 30 to 172, 50 to 224, 62 to 255, 63 to 256),
        "silver" to mapOf(20 to 60, 99 to 201),
        "coal" to mapOf(1 to 17, 30 to 42, 99 to 101),
        "gold" to mapOf(40 to 35, 99 to 76),
        "mithril" to mapOf(55 to 30, 60 to 33, 75 to 40, 90 to 47, 99 to 51),
        "adamantite" to mapOf(70 to 19, 76 to 21, 85 to 23, 93 to 25, 99 to 26),
    )
    // Silver and gold: Silver rocks and Gold rocks (read 2026-10-30), level 20 0.234375 to 99 0.78515625, 40 0.13671875 to 99 0.296875.

    @Test
    fun `each rock's pair gives the Wiki's success chart`() {
        for ((key, points) in charts) {
            val type = rock(key)
            for ((level, chance) in points) {
                assertEquals(chance, SuccessRoll.chance(type.low, type.high, level).coerceAtMost(256), "$key at level $level")
            }
        }
    }

    @Test
    fun `the gem table is 128 in 32,768 a roll, as the Wiki's`() {
        assertEquals(128, Mining.GEMS.sumOf { it.weight })
        assertEquals(1.0 / 256, Mining.GEM_TABLE_CHANCE.toDouble() / Mining.GEM_TABLE_DENOMINATOR)
    }

    @Test
    fun `every gem on the table has its own message`() {
        assertEquals(Mining.GEMS.map { it.obj }.filter { it != Mining.NOTHING }.toSet(), Mining.GEM_LINES.keys)
    }

    @Test
    fun `pickaxes go best first and every rock has an empty rock`() {
        assertEquals(Mining.PICKAXES.sortedByDescending { it.tool.level }.map { it.tool.level }, Mining.PICKAXES.map { it.tool.level })
        for (rock in Mining.ROCKS.values) assertEquals(true, rock.empty in Mining.EMPTY_ROCKS)
    }
}
