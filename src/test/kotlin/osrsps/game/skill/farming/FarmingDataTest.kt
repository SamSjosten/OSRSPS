package osrsps.game.skill.farming

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import osrsps.game.GameRandom
import osrsps.game.skill.SuccessRoll

class FarmingDataTest {
    @Test
    fun `every allotment value is its own, within the varbit, and clear of the weed values`() {
        val values = Crops.ALL.filter { it.kind == PatchKind.ALLOTMENT }.flatMap { crop ->
            crop.growing + crop.watered + (0..2).map { crop.harvest + it } + crop.diseased + crop.dead
        }
        assertEquals(values.size, values.toSet().size, "a value drawn twice")
        assertTrue(values.all { it in 6..255 }, "0 to 3 are the weed stages and the varbit is 8 bits")
    }

    @Test
    fun `every herb and flower value is its own but the herbs' shared dead ones, and clear of the weed values`() {
        val herbs = Crops.ALL.filter { it.kind == PatchKind.HERB }
        val herbValues = herbs.flatMap { crop -> crop.growing + (0..2).map { crop.harvest + it } + crop.diseased }
        assertEquals(herbValues.size, herbValues.toSet().size, "a herb value drawn twice")
        assertTrue(herbValues.none { it in 0..3 || it in 170..172 }, "0 to 3 are the weed stages and 170 to 172 the dead herbs")
        assertTrue(herbs.all { it.dead == listOf(170, 171, 172) && it.watered.isEmpty() })
        val flowers = Crops.ALL.filter { it.kind == PatchKind.FLOWER }
        val flowerValues = flowers.flatMap { crop -> crop.growing + crop.watered + crop.harvest + crop.diseased + crop.dead }
        assertEquals(flowerValues.size, flowerValues.toSet().size, "a flower value drawn twice")
        assertTrue(flowerValues.all { it in 8..255 }, "0 to 7 are the weed stages")
        assertEquals(29, Crops.ALL.size)
        assertEquals(Crops.ALL.size, Crops.ALL.map { it.seed }.toSet().size)
    }

    @Test
    fun `the chance to keep a life matches each seed page's figures at levels 1 and 99`() {
        // The Wiki's percentages, to the 0.1% it gives them.
        val pages = mapOf(
            Crops.POTATO to (39.8 to 70.7), Crops.ONION to (41.4 to 70.7), Crops.CABBAGE to (42.2 to 70.7),
            Crops.TOMATO to (44.1 to 70.7), Crops.SWEETCORN to (34.8 to 70.7), Crops.STRAWBERRY to (40.6 to 70.7),
            Crops.WATERMELON to (49.6 to 70.7), Crops.SNAPE_GRASS to (58.2 to 76.6),
        )
        for ((crop, percents) in pages) {
            for ((level, percent) in listOf(1 to percents.first, 99 to percents.second)) {
                val chance = SuccessRoll.chance(crop.ctsLow, crop.ctsHigh, level) * 100.0 / SuccessRoll.DENOMINATOR
                assertTrue(abs(chance - percent) < 0.05, "${crop.key} at $level: $chance%, the Wiki says $percent%")
            }
        }
    }

    /** Expected crops from [lives] lives: lives / (1 - chance to keep one), the Wiki's Variable crop yield. */
    private fun expected(crop: Crop, level: Int, lives: Int, secateurs: Boolean): Double {
        val (low, high) = Farming.lifeChances(crop, secateurs)
        return lives / (1 - SuccessRoll.chance(low, high, level).toDouble() / SuccessRoll.DENOMINATOR)
    }

    @Test
    fun `potato yields match the Wiki's chart, with and without magic secateurs`() {
        // The Potato seed page's chart (six lives, ultracompost's): 9.97 and 20.48 plain, 10.67 and 26.95 with secateurs.
        assertEquals(9.97, expected(Crops.POTATO, 1, 6, false), 0.01)
        assertEquals(20.48, expected(Crops.POTATO, 99, 6, false), 0.01)
        assertEquals(10.67, expected(Crops.POTATO, 1, 6, true), 0.01)
        assertEquals(26.95, expected(Crops.POTATO, 99, 6, true), 0.01)
    }

    @Test
    fun `seeded harvests average the expected yield`() {
        val random = GameRandom(7)
        val (low, high) = Farming.lifeChances(Crops.POTATO, false)
        val patches = 20_000
        var crops = 0L
        repeat(patches) {
            var lives = Farming.BASE_LIVES
            while (lives > 0) {
                crops++
                if (!SuccessRoll.roll(random, low, high, 1)) lives--
            }
        }
        assertEquals(expected(Crops.POTATO, 1, Farming.BASE_LIVES, false), crops.toDouble() / patches, 0.05)
    }

    @Test
    fun `limpwurt gives three roots and a tenth of a roll below the Farming level more, woad three leaves`() {
        val farming = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap(), random = GameRandom(3)), clock = { 0L })
        fun yields(level: Int, secateurs: Boolean = false) = (1..20_000).map { farming.flowerYield(Crops.LIMPWURT, level, secateurs) }
        // The Limpwurt seed page: a random number from 0 to level - 1, divided by ten: four at most at level 11, five
        // at 30 (not six), twelve at 91 to 99.
        assertEquals((3..3).toSet(), yields(10).toSet())
        assertEquals((3..4).toSet(), yields(11).toSet())
        assertEquals((3..5).toSet(), yields(30).toSet())
        assertEquals((3..12).toSet(), yields(99).toSet())
        // Evenly: at 30 each of 0, 1 and 2 extra comes a third of the time; at 35 the third extra only 5 times in 35.
        val at30 = yields(30)
        for (extra in 0..2) assertEquals(1.0 / 3, at30.count { it == 3 + extra } / 20_000.0, 0.02)
        assertEquals(5.0 / 35, yields(35).count { it == 6 } / 20_000.0, 0.015)
        // Magic secateurs draw from 10% more numbers: 108 at 99, so a 13th root is possible and the average higher.
        val with = yields(99, secateurs = true)
        assertEquals((3..13).toSet(), with.toSet())
        assertTrue(with.average() > yields(99).average() + 0.3)
        assertEquals(3, farming.flowerYield(Crops.WOAD, 99, true))
        assertEquals(1, farming.flowerYield(Crops.MARIGOLD, 99, false))
    }

    @Test
    fun `a farming tick is five minutes of the clock plus the offset and the skipped ticks`() {
        // 00:04 is in tick 0; a 1-minute offset moves it into tick 1; a skipped tick adds one more.
        val fourPast = 4 * 60_000L
        assertEquals(0, FarmingClock.tick(fourPast, 0, 0))
        assertEquals(1, FarmingClock.tick(fourPast, 1, 0))
        assertEquals(2, FarmingClock.tick(fourPast, 1, 1))
    }

    @Test
    fun `a save keeps every patch, and drops patches and crops this server doesn't know`() {
        val state = FarmingState()
        state.lastTick = 41
        state.skipped = 3
        state.patches[FarmingPatches.FALADOR_NW.key] = PatchState(0, Crops.SNAPE_GRASS, 7, false, 2)
        state.patches[FarmingPatches.FALADOR_SE.key] = PatchState(1)
        val json = FarmingSaves.toJson(state)
        val back = FarmingSaves.fromJson(json)
        assertEquals(41, back.lastTick)
        assertEquals(3, back.skipped)
        assertEquals(FarmingSaves.toJson(state), FarmingSaves.toJson(back))
        assertEquals(
            listOf(state.value(FarmingPatches.FALADOR_NW), state.value(FarmingPatches.FALADOR_SE)),
            listOf(back.value(FarmingPatches.FALADOR_NW), back.value(FarmingPatches.FALADOR_SE)),
        )
        val unknown = Json.parseToJsonElement(
            """{"tick": 1, "patches": {"nowhere": {"weeds": 1}, "falador_allotment_nw": {"crop": "moonflower", "stage": 1}}}""",
        ).jsonObject
        assertTrue(FarmingSaves.fromJson(unknown).patches.isEmpty())
    }

    @Test
    fun `a patch draws its weeds, growth, water and lives as the cache numbers them`() {
        val patch = PatchState.overgrown(PatchKind.ALLOTMENT)
        assertEquals(0, patch.value(PatchKind.ALLOTMENT))
        patch.weeds = 0
        assertEquals(3, patch.value(PatchKind.ALLOTMENT))
        patch.crop = Crops.SWEETCORN
        patch.stage = 5
        assertEquals(39, patch.value(PatchKind.ALLOTMENT))
        patch.watered = true
        assertEquals(103, patch.value(PatchKind.ALLOTMENT))
        patch.stage = 6
        patch.lives = 5
        assertEquals(40, patch.value(PatchKind.ALLOTMENT))
        patch.lives = 1
        assertEquals(42, patch.value(PatchKind.ALLOTMENT))
        // A grown herb counts its lives down from its first grown value; a grown flower has one value.
        val herb = PatchState(0, Crops.RANARR, 4, false, 3)
        assertEquals(36, herb.value(PatchKind.HERB))
        herb.lives = 1
        assertEquals(38, herb.value(PatchKind.HERB))
        assertEquals(32, PatchState(0, Crops.LIMPWURT, 4, false, 1).value(PatchKind.FLOWER))
        assertEquals(94, PatchState(0, Crops.LIMPWURT, 2, true).value(PatchKind.FLOWER))
    }
}
