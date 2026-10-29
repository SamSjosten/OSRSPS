package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals

class FishingTest {
    /** The chance a roll of [method] at [level] catches [fish]: the better fish it can catch are tried first. */
    private fun chance(method: FishingMethod, fish: Fish, level: Int): Double {
        var missed = 1.0
        for (tried in method.fish) {
            if (level < tried.level) continue
            val p = SuccessRoll.chance(tried.low, tried.high, level).coerceAtMost(256) / 256.0
            if (tried === fish) return missed * p
            missed *= 1 - p
        }
        error("${fish.obj} isn't caught by ${method.name} at level $level")
    }

    /** Points from the Wiki's success charts (read 2026-10-29), which plot each fish as the method gives it. */
    private val charts = listOf(
        Triple(Fishing.SMALL_NET, Fishing.ANCHOVIES, mapOf(15 to 0.15625, 50 to 0.30078125, 99 to 0.50390625)),
        Triple(Fishing.SMALL_NET, Fishing.SHRIMPS, mapOf(1 to 0.19140625, 15 to 0.2603759765625, 50 to 0.4178924560546875, 99 to 0.49609375)),
        Triple(Fishing.SEA_BAIT, Fishing.HERRING, mapOf(10 to 0.13671875, 50 to 0.30078125, 99 to 0.50390625)),
        Triple(Fishing.SEA_BAIT, Fishing.SARDINE, mapOf(5 to 0.15625, 10 to 0.161865234375, 50 to 0.3086395263671875, 99 to 0.3740081787109375)),
        Triple(Fishing.LURE, Fishing.SALMON, mapOf(30 to 0.16015625, 50 to 0.22265625, 99 to 0.37890625)),
        Triple(Fishing.LURE, Fishing.TROUT, mapOf(20 to 0.25, 30 to 0.262451171875, 50 to 0.3431243896484375, 99 to 0.4682464599609375)),
        Triple(Fishing.RIVER_BAIT, Fishing.PIKE, mapOf(25 to 0.14453125, 50 to 0.22265625, 99 to 0.37890625)),
    )

    @Test
    fun `each fish's pair, better fish first, gives the Wiki's success chart`() {
        for ((method, fish, points) in charts) {
            for ((level, wiki) in points) assertEquals(wiki, chance(method, fish, level), "${fish.obj} by ${method.name} at level $level")
        }
    }

    @Test
    fun `methods list their fish best first, and the lowest sets the level`() {
        for (spot in Fishing.SPOTS) {
            for (method in spot.methods) {
                assertEquals(method.fish.sortedByDescending { it.level }, method.fish)
                assertEquals(method.fish.last().level, method.level)
            }
        }
        assertEquals(listOf(1, 5, 20, 25), listOf(Fishing.SMALL_NET, Fishing.SEA_BAIT, Fishing.LURE, Fishing.RIVER_BAIT).map { it.level })
    }

    @Test
    fun `no two spots in an area share a tile`() {
        val tiles = Fishing.AREAS.flatten()
        assertEquals(tiles.size, tiles.toSet().size)
    }
}
