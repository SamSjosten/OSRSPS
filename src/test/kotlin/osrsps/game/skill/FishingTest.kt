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
            // The big net rolls each entry on its own, in Mod Ash's order, so its order isn't by level.
            for (method in spot.methods.filter { !it.rollsEach }) {
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
        assertEquals(listOf(Fishing.BIG_NET_FISHING, Fishing.HARPOON_SHARK), methods(Fishing.CATHERBY_SHARK_SPOT))
        assertEquals(listOf(Fishing.SMALL_NET, Fishing.SEA_BAIT), methods(Fishing.CATHERBY_NET_SPOT))
        assertEquals(listOf(Fishing.SMALL_NET, Fishing.SEA_BAIT), methods(Fishing.MUSA_POINT_NET_SPOT))
        assertEquals(listOf(Fishing.DRAGON_HARPOON, Fishing.BARB_TAIL_HARPOON, Fishing.HARPOON), Fishing.HARPOON_TUNA.tools)
    }

    @Test
    fun `the big net rolls Mod Ash's nine in order, with the Wiki's pairs where it gives them`() {
        val net = Fishing.BIG_NET_FISHING
        assertTrue(net.rollsEach)
        assertEquals(16, net.level)
        // Boots, seaweed, gloves, mackerel, another mackerel, oyster, casket, cod, bass.
        assertEquals(listOf(1061, 401, 1059, 353, 353, 407, 405, 341, 363), net.fish.map { it.obj })
        // The Wiki: mackerel 5/65 then a second roll of 10/10, cod 4/55 from 23, bass 3/40 from 46, with 20, 45 and 100 XP.
        assertEquals(listOf(5 to 65, 10 to 10, 4 to 55, 3 to 40), listOf(Fishing.MACKEREL, Fishing.MACKEREL_AGAIN, Fishing.COD, Fishing.BASS).map { it.low to it.high })
        assertEquals(listOf(16, 23, 46), listOf(Fishing.MACKEREL, Fishing.COD, Fishing.BASS).map { it.level })
        assertEquals(listOf(200, 450, 1000), listOf(Fishing.MACKEREL, Fishing.COD, Fishing.BASS).map { it.xpTenths })
        // The Wiki's casket: about 1 in 128 at low levels, 1 in 85.3 at high ones.
        assertEquals(2, SuccessRoll.chance(Fishing.BIG_NET_CASKET.low, Fishing.BIG_NET_CASKET.high, 16))
        assertEquals(3, SuccessRoll.chance(Fishing.BIG_NET_CASKET.low, Fishing.BIG_NET_CASKET.high, 99))
        assertEquals(listOf(Fishing.BIG_NET_FISHING, Fishing.HARPOON_SHARK), Fishing.SPOTS.single { it.npc == Fishing.CATHERBY_SHARK_SPOT }.methods)
    }

    @Test
    fun `seeded big net rolls bring up each catch at its own rate, several at once`() {
        val random = GameRandom(4321)
        val level = 99
        val rolls = 200_000
        val caught = IntArray(Fishing.BIG_NET_FISHING.fish.size)
        var several = 0
        repeat(rolls) {
            var count = 0
            Fishing.BIG_NET_FISHING.fish.forEachIndexed { index, fish ->
                if (SuccessRoll.roll(random, fish.low, fish.high, level)) { caught[index]++; count++ }
            }
            if (count > 1) several++
        }
        Fishing.BIG_NET_FISHING.fish.forEachIndexed { index, fish ->
            val expected = SuccessRoll.chance(fish.low, fish.high, level) / 256.0
            val seen = caught[index].toDouble() / rolls
            assertTrue(abs(seen - expected) < 0.004, "${fish.obj}: $seen, the pair's $expected")
        }
        assertTrue(several > 0, "some rolls bring up more than one catch")
    }

    @Test
    fun `karambwanji net level over 5 plus one at a time before shrimps, and the vessel catches karambwan from 65`() {
        val net = Fishing.KARAMBWANJI_NET
        assertEquals(listOf(Fishing.KARAMBWANJI, Fishing.KARAMBWANJI_SHRIMPS), net.fish)
        // The spot needs 5, shrimps too (the Wiki's Raw shrimp sources), though shrimps elsewhere need 1.
        assertEquals(5, net.level)
        assertEquals(Fishing.SHRIMPS.obj to (48 to 256), Fishing.KARAMBWANJI_SHRIMPS.obj to (Fishing.KARAMBWANJI_SHRIMPS.low to Fishing.KARAMBWANJI_SHRIMPS.high))
        assertEquals(listOf(5, 50, 100, 250), Fishing.KARAMBWANJI.let { listOf(it.level, it.xpTenths, it.low, it.high) })
        // The Wiki's table: 2 from 5 to 9, 3 from 10, 20 from 95, 21 boosted past 99; one of anything else.
        assertEquals(listOf(2, 2, 3, 13, 20, 21), listOf(5, 9, 10, 64, 99, 100).map { Fishing.KARAMBWANJI.amount(it) })
        assertEquals(1, Fishing.SHRIMPS.amount(99))
        // Its chart: karambwanji tried first, then shrimps.
        assertEquals(SuccessRoll.chance(100, 250, 50) / 256.0, chance(net, Fishing.KARAMBWANJI, 50), 1e-9)
        assertEquals((1 - SuccessRoll.chance(100, 250, 50) / 256.0) * SuccessRoll.chance(48, 256, 50) / 256.0, chance(net, Fishing.KARAMBWANJI_SHRIMPS, 50), 1e-9)

        val vessel = Fishing.KARAMBWAN_FISHING
        assertEquals(listOf(Fishing.KARAMBWAN), vessel.fish)
        assertEquals(listOf(65, 500, 5, 160), Fishing.KARAMBWAN.let { listOf(it.level, it.xpTenths, it.low, it.high) })
        assertEquals(Fishing.BAITED_VESSEL, vessel.tool)
        assertEquals(listOf(Fishing.BAITED_VESSEL, Fishing.KARAMBWAN_VESSEL), vessel.tools)
        assertEquals(Fishing.RAW_KARAMBWANJI, vessel.vessel!!.bait)
        assertEquals("Fish", vessel.name)

        fun methods(npc: Int) = Fishing.SPOTS.single { it.npc == npc }.methods
        assertEquals(listOf(net), methods(Fishing.KARAMBWANJI_SPOT))
        assertEquals(listOf(vessel), methods(Fishing.KARAMBWAN_SPOT))
        assertEquals(listOf(vessel), methods(Fishing.KARAMBWAN_SPOT_EAST))
    }

    @Test
    fun `seeded vessel rolls catch karambwan as often as the chart says, and the rest lose the bait`() {
        val random = GameRandom(65)
        val rolls = 100_000
        for (level in listOf(65, 80, 99)) {
            val caught = (1..rolls).count { SuccessRoll.roll(random, Fishing.KARAMBWAN.low, Fishing.KARAMBWAN.high, level) }
            val expected = SuccessRoll.chance(Fishing.KARAMBWAN.low, Fishing.KARAMBWAN.high, level) / 256.0
            assertTrue(abs(caught.toDouble() / rolls - expected) < 0.006, "level $level: ${caught.toDouble() / rolls}, chart $expected")
        }
        // About 107 in 256 at 65, 161 at 99 (the Wiki's chart).
        assertEquals(107, SuccessRoll.chance(5, 160, 65))
        assertEquals(161, SuccessRoll.chance(5, 160, 99))
    }

    @Test
    fun `monkfish net from 62 at Piscatoris and anglerfish bait with sandworms from 82 at Port Piscarilius, by the Wiki's pairs`() {
        assertEquals(listOf(7944, 62, 1200, 48, 90), Fishing.MONKFISH.let { listOf(it.obj, it.level, it.xpTenths, it.low, it.high) })
        assertEquals(listOf(13439, 82, 1200, 3, 36), Fishing.ANGLERFISH.let { listOf(it.obj, it.level, it.xpTenths, it.low, it.high) })
        assertEquals(Fishing.NET, Fishing.MONKFISH_NET.tool)
        assertEquals(62, Fishing.MONKFISH_NET.level)
        assertEquals(Fishing.FISHING_ROD, Fishing.ANGLERFISH_BAIT.tool)
        assertEquals(Fishing.SANDWORMS, Fishing.ANGLERFISH_BAIT.bait!!.obj)
        assertEquals(82, Fishing.ANGLERFISH_BAIT.level)
        fun methods(npc: Int) = Fishing.SPOTS.single { it.npc == npc }.methods
        assertEquals(listOf(Fishing.MONKFISH_NET, Fishing.HARPOON_TUNA), methods(Fishing.PISCATORIS_SPOT))
        assertEquals(listOf(Fishing.ANGLERFISH_BAIT), methods(Fishing.PISCARILIUS_SPOT))
        // Mod Ash (13 February 2023, the Raw anglerfish page): about 12% a roll at 82, 14% at 99.
        assertEquals(31, SuccessRoll.chance(3, 36, 82))
        assertEquals(37, SuccessRoll.chance(3, 36, 99))
    }

    @Test
    fun `seeded rolls catch monkfish and anglerfish as often as the Wiki's charts say`() {
        val random = GameRandom(62)
        val rolls = 100_000
        for ((fish, level) in listOf(Fishing.MONKFISH to 62, Fishing.MONKFISH to 99, Fishing.ANGLERFISH to 82, Fishing.ANGLERFISH to 99)) {
            val caught = (1..rolls).count { SuccessRoll.roll(random, fish.low, fish.high, level) }
            val expected = SuccessRoll.chance(fish.low, fish.high, level) / 256.0
            assertTrue(abs(caught.toDouble() / rolls - expected) < 0.006, "${fish.obj} at $level: ${caught.toDouble() / rolls}, chart $expected")
        }
    }
}
