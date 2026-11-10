package osrsps.game.skill

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

class FishingTest {
    /** The chance a roll of [method] at [level] catches [fish]: the better fish it can catch are tried first. */
    private fun chance(method: FishingMethod, fish: Fish, level: Int, percent: Int = 100): Double {
        var missed = 1.0
        for (tried in method.fish) {
            if (level < tried.level) continue
            val p = SuccessRoll.chance(tried.low * percent / 100, tried.high * percent / 100, level).coerceAtMost(256) / 256.0
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
    fun `the swamp caves' spots net frog spawn and swamp weed from 33 and bait cave eels from 38, slimy eels below`() {
        for (spot in listOf(Fishing.SWAMP_CAVES_WEST_SPOT, Fishing.SWAMP_CAVES_EAST_SPOT)) {
            assertEquals(listOf(Fishing.CAVE_NET, Fishing.CAVE_BAIT), Fishing.SPOTS.single { it.npc == spot }.methods)
        }
        assertEquals(listOf(33, 28), listOf(Fishing.CAVE_NET, Fishing.CAVE_BAIT).map { it.level })
        assertEquals(listOf(5004, 10978), Fishing.CAVE_NET.fish.map { it.obj })
        assertEquals(listOf(750, 10), Fishing.CAVE_NET.fish.map { it.xpTenths })
        assertEquals(listOf(5001, 3379), Fishing.CAVE_BAIT.fish.map { it.obj })
        assertEquals(listOf(800, 650), Fishing.CAVE_BAIT.fish.map { it.xpTenths })
        // The Wiki's charts: frog spawn 16/96 and swamp weed a flat 10/256; both eels 10/80, the cave eel tried first.
        assertEquals(SuccessRoll.chance(16, 96, 33) / 256.0, chance(Fishing.CAVE_NET, Fishing.FROG_SPAWN, 33), 1e-9)
        assertEquals((1 - SuccessRoll.chance(16, 96, 50) / 256.0) * SuccessRoll.chance(10, 10, 50) / 256.0, chance(Fishing.CAVE_NET, Fishing.SWAMP_WEED, 50), 1e-9)
        assertEquals((1 - SuccessRoll.chance(10, 80, 38) / 256.0) * SuccessRoll.chance(10, 80, 38) / 256.0, chance(Fishing.CAVE_BAIT, Fishing.SLIMY_EEL, 38), 1e-9)
        // No area: they stay put.
        assertTrue(Fishing.AREAS.flatten().none { it == osrsps.map.Tile(3154, 9544) || it == osrsps.map.Tile(3245, 9570) })
    }

    @Test
    fun `no two spots in an area share a tile`() {
        val tiles = Fishing.AREAS.flatten()
        assertEquals(tiles.size, tiles.toSet().size)
    }
    @Test
    fun `cage and harpoon fish have the Wiki's levels, XP and pairs, and the dragon harpoon's pairs are its charts'`() {
        // The Wiki's Raw lobster, Raw tuna, Raw swordfish and Raw shark pages (read 2026-11-10).
        assertEquals(listOf(40, 35, 50, 76), listOf(Fishing.LOBSTER, Fishing.TUNA, Fishing.SWORDFISH, Fishing.SHARK).map { it.level })
        assertEquals(listOf(900, 800, 1000, 1100), listOf(Fishing.LOBSTER, Fishing.TUNA, Fishing.SWORDFISH, Fishing.SHARK).map { it.xpTenths })
        assertEquals(listOf(377, 359, 371, 383), listOf(Fishing.LOBSTER, Fishing.TUNA, Fishing.SWORDFISH, Fishing.SHARK).map { it.obj })
        val pairs = listOf(Fishing.LOBSTER, Fishing.TUNA, Fishing.SWORDFISH, Fishing.SHARK).map { it.low to it.high }
        assertEquals(listOf(6 to 95, 8 to 64, 4 to 48, 3 to 40), pairs)
        // The dragon harpoon's charts: tuna 9/76, swordfish 4/57, shark 3/48, the harpoon's times 1.2 rounded down.
        val dragon = Fishing.HARPOON_TUNA.otherTools.single { it.obj == Fishing.DRAGON_HARPOON }
        assertEquals(61, dragon.level)
        assertEquals(listOf(9 to 76, 4 to 57, 3 to 48), listOf(Fishing.TUNA, Fishing.SWORDFISH, Fishing.SHARK).map { it.low * dragon.percent / 100 to it.high * dragon.percent / 100 })
        assertEquals(100, Fishing.HARPOON_TUNA.otherTools.single { it.obj == Fishing.BARB_TAIL_HARPOON }.percent)
        assertEquals(Fishing.HARPOON_TUNA.otherTools, Fishing.HARPOON_SHARK.otherTools)
        assertEquals(listOf(40, 35, 76), listOf(Fishing.CAGE, Fishing.HARPOON_TUNA, Fishing.HARPOON_SHARK).map { it.level })
    }

    @Test
    fun `seeded rolls catch each cage and harpoon fish as often as the Wiki's charts say`() {
        val random = GameRandom(1234)
        val cases = listOf(
            Triple(Fishing.CAGE, 40, 100), Triple(Fishing.CAGE, 99, 100),
            Triple(Fishing.HARPOON_TUNA, 35, 100), Triple(Fishing.HARPOON_TUNA, 50, 100), Triple(Fishing.HARPOON_TUNA, 99, 100),
            Triple(Fishing.HARPOON_TUNA, 80, 120), Triple(Fishing.HARPOON_SHARK, 76, 100), Triple(Fishing.HARPOON_SHARK, 99, 120),
        )
        val rolls = 200_000
        for ((method, level, percent) in cases) {
            val caught = HashMap<Fish, Int>()
            repeat(rolls) {
                // As Fishing.roll: each fish the level allows, best first, its pair scaled by the tool.
                val fish = method.fish.firstOrNull {
                    level >= it.level && SuccessRoll.roll(random, it.low * percent / 100, it.high * percent / 100, level)
                }
                if (fish != null) caught.merge(fish, 1, Int::plus)
            }
            for (fish in method.fish.filter { level >= it.level }) {
                val expected = chance(method, fish, level, percent)
                val seen = (caught[fish] ?: 0).toDouble() / rolls
                assertTrue(abs(seen - expected) < 0.005, "${fish.obj} by ${method.name} at $level with $percent%: $seen, the chart's $expected")
            }
        }
    }

    @Test
    fun `Catherby and Musa Point have their spots' methods, and every spot's spawn tiles are in one area`() {
        fun methods(npc: Int) = Fishing.SPOTS.single { it.npc == npc }.methods
        assertEquals(listOf(Fishing.CAGE, Fishing.HARPOON_TUNA), methods(Fishing.CATHERBY_CAGE_SPOT))
        assertEquals(listOf(Fishing.CAGE, Fishing.HARPOON_TUNA), methods(Fishing.MUSA_POINT_CAGE_SPOT))
        assertEquals(listOf(Fishing.HARPOON_SHARK), methods(Fishing.CATHERBY_SHARK_SPOT))
        assertEquals(listOf(Fishing.SMALL_NET, Fishing.SEA_BAIT), methods(Fishing.CATHERBY_NET_SPOT))
        assertEquals(listOf(Fishing.SMALL_NET, Fishing.SEA_BAIT), methods(Fishing.MUSA_POINT_NET_SPOT))
        assertEquals(listOf(Fishing.DRAGON_HARPOON, Fishing.BARB_TAIL_HARPOON, Fishing.HARPOON), Fishing.HARPOON_TUNA.tools)
    }
}
