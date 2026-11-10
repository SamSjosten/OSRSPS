package osrsps.game.skill

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/** Gathering 2, step G2: teak, mahogany, arctic pine and redwood trees against the Wiki's pages (read 2026-11-10). */
class WoodcuttingG2Test {
    private val data = WoodcuttingData.load()

    private fun tree(key: String) = data.trees.values.first { it.key == key }

    /** Each page's success chart pairs, bronze to crystal. */
    private val charts = mapOf(
        "teak" to listOf(15 to 46, 23 to 70, 31 to 93, 35 to 102, 39 to 117, 47 to 140, 55 to 164, 60 to 190, 60 to 200),
        "mahogany" to listOf(8 to 25, 12 to 38, 16 to 50, 18 to 54, 20 to 63, 25 to 75, 29 to 88, 34 to 94, 36 to 97),
        "arctic_pine" to listOf(8 to 25, 12 to 37, 16 to 50, 18 to 56, 20 to 62, 24 to 75, 28 to 87, 30 to 93, 32 to 100),
        "redwood" to listOf(2 to 6, 3 to 9, 4 to 12, 4 to 14, 5 to 15, 6 to 18, 7 to 21, 7 to 30, 8 to 35),
    )

    @Test
    fun `each tree's pairs are its Wiki chart's, axe by axe`() {
        for ((key, pairs) in charts) {
            assertEquals(TIERS.zip(pairs).toMap(), tree(key).rates, key)
        }
    }

    @Test
    fun `levels, XP, timers and respawns from the Wiki`() {
        val expected = mapOf(
            "teak" to listOf(35, 850, 50, 15, 15), "mahogany" to listOf(50, 1250, 100, 14, 14),
            "arctic_pine" to listOf(54, 400, 140, 14, 14), "redwood" to listOf(90, 3800, 440, 199, 199),
        )
        for ((key, values) in expected) {
            val tree = tree(key)
            assertEquals(values, listOf(tree.level, tree.xpTenths, tree.despawn, tree.respawnLow, tree.respawnHigh), key)
        }
        assertEquals(listOf(6333, 6332, 10810, 19669), listOf("teak", "mahogany", "arctic_pine", "redwood").map { tree(it).logs })
    }

    @Test
    fun `redwoods give no seed, ring or egg nests and every other tree does`() {
        assertEquals(listOf("redwood"), data.trees.values.distinct().filter { !it.birdNests }.map { it.key })
    }

    @Test
    fun `every tree loc has its stump, paired by gameval name`() {
        val stumps = mapOf(
            9036 to 9037, 36686 to 36687, 40758 to 40759, // teaktree, prif_teaktree, teaktree_update
            9034 to 9035, 36688 to 36689, 40760 to 40761, // mahoganytree, prif_mahoganytree, mahoganytree_update
            3037 to 21274, // arctic_pine to arctic_pine_tree_stump
            29668 to 29669, 29670 to 29671, // redwoodtree_l/_r to their _cut sections
        )
        for ((tree, stump) in stumps) assertEquals(stump, data.stumps[tree], "tree $tree")
    }

    @Test
    fun `a seeded run of teak rolls with a rune axe at 60 lands on the chart`() {
        val (low, high) = tree("teak").rates.getValue("rune")
        val chance = SuccessRoll.chance(low, high, 60)
        val random = GameRandom(9)
        val rolls = 100_000
        val hits = (1..rolls).count { SuccessRoll.roll(random, low, high, 60) }
        val expected = rolls * chance / 256.0
        assertTrue(abs(hits - expected) < 4 * Math.sqrt(expected * (1 - chance / 256.0)), "$hits vs $expected")
    }

    private companion object {
        val TIERS = listOf("bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "crystal")
    }
}
