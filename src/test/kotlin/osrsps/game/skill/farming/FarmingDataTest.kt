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
        assertEquals(42, Crops.ALL.size)
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
    fun `compost cuts the disease chance as the Wiki's table does, and a grown flower keeps its allotment crops well`() {
        // The Disease page's herb row: 27, 14, 6 and 3 in 128.
        assertEquals(listOf(27, 14, 6, 3), (0..3).map { Compost.diseaseChance(PatchKind.HERB, it, false) })
        assertEquals(14, Compost.diseaseChance(PatchKind.ALLOTMENT, Compost.NONE, true))
        assertEquals(listOf(180, 260, 360), (1..3).map { Compost.xpTenths(it) })
        assertTrue(Crops.protects(Crops.MARIGOLD, Crops.POTATO) && Crops.protects(Crops.ROSEMARY, Crops.CABBAGE))
        assertTrue(Crops.protects(Crops.WHITE_LILY, Crops.SNAPE_GRASS) && Crops.protects(Crops.NASTURTIUM, Crops.WATERMELON))
        assertTrue(!Crops.protects(Crops.MARIGOLD, Crops.CABBAGE) && !Crops.protects(Crops.LIMPWURT, Crops.STRAWBERRY))

        val farming = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap(), random = GameRandom(3)), clock = { 0L })
        val state = FarmingState()
        val nw = FarmingPatches.FALADOR_NW.key
        state.patches[nw] = PatchState(0, Crops.POTATO, 2)
        assertTrue(!farming.isProtected(state, nw, Crops.POTATO))
        // A growing marigold isn't enough; a grown one is.
        state.patches[FarmingPatches.FALADOR_FLOWER.key] = PatchState(0, Crops.MARIGOLD, 3)
        assertTrue(!farming.isProtected(state, nw, Crops.POTATO))
        state.patches.getValue(FarmingPatches.FALADOR_FLOWER.key).stage = 4
        assertTrue(farming.isProtected(state, nw, Crops.POTATO))
        assertTrue(!farming.isProtected(state, nw, Crops.CABBAGE))
    }

    @Test
    fun `herbs fall ill at the Wiki's rate past their first stage, die if left, and composted or protected crops less`() {
        val farming = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap(), random = GameRandom(11)), clock = { 0L })
        fun survivors(crop: Crop, patchKey: String, compost: Int, flower: Crop? = null): Double {
            val patches = 4_000
            var grown = 0
            repeat(patches) {
                val state = FarmingState()
                state.patches[patchKey] = PatchState(0, crop, compost = compost)
                if (flower != null) state.patches[FarmingPatches.FALADOR_FLOWER.key] = PatchState(0, flower, flower.stages, lives = 1)
                farming.grow(state, 1, 200)
                val patch = state.patches.getValue(patchKey)
                if (patch.grown) grown++ else assertTrue(patch.dead, "a crop left ill dies")
            }
            return grown.toDouble() / patches
        }
        // Three rolls (growths 2 to 4) of 27/128, then of 3/128 with ultracompost (the Disease page's (1 - P)^n).
        assertEquals(Math.pow(101.0 / 128, 3.0), survivors(Crops.GUAM, FarmingPatches.FALADOR_HERB.key, Compost.NONE), 0.03)
        assertEquals(Math.pow(125.0 / 128, 3.0), survivors(Crops.GUAM, FarmingPatches.FALADOR_HERB.key, Compost.ULTRA), 0.02)
        assertEquals(1.0, survivors(Crops.POTATO, FarmingPatches.FALADOR_NW.key, Compost.NONE, flower = Crops.MARIGOLD))
        // An ultracomposted herb has six lives when it is grown.
        val state = FarmingState()
        state.patches[FarmingPatches.FALADOR_HERB.key] = PatchState(0, Crops.GUAM, 3, compost = Compost.ULTRA)
        Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap()), clock = { 0L }, diseases = false).grow(state, 4, 4)
        assertEquals(6, state.patches.getValue(FarmingPatches.FALADOR_HERB.key).lives)
    }

    @Test
    fun `compost stays on a patch that grows over, and a flower protects from the tick after it grows`() {
        val farming = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap(), random = GameRandom(5)), clock = { 0L })
        val state = FarmingState()
        state.patches[FarmingPatches.FALADOR_NW.key] = PatchState(0, compost = Compost.SUPER)
        farming.grow(state, 1, 10)
        val nw = state.patches.getValue(FarmingPatches.FALADOR_NW.key)
        assertEquals(PatchKind.ALLOTMENT.weeds, nw.weeds)
        assertEquals(Compost.SUPER, nw.compost)
        assertEquals(Compost.SUPER, FarmingSaves.fromJson(FarmingSaves.toJson(state)).patches.getValue(FarmingPatches.FALADOR_NW.key).compost)

        // A marigold one stage from grown and potatoes in both allotments: on the tick it grows, both allotments still
        // roll unprotected, whichever comes first in the map (both ill on that tick 4% of the time), and afterwards
        // both are protected.
        // The marigold can fall ill itself on that tick; those tries are left out.
        fun flowerTick(): FarmingState? {
            val s = FarmingState()
            s.patches[FarmingPatches.FALADOR_FLOWER.key] = PatchState(0, Crops.MARIGOLD, 3)
            s.patches[FarmingPatches.FALADOR_NW.key] = PatchState(0, Crops.POTATO, 1)
            s.patches[FarmingPatches.FALADOR_SE.key] = PatchState(0, Crops.POTATO, 1)
            farming.grow(s, 2, 2)
            if (!s.patches.getValue(FarmingPatches.FALADOR_FLOWER.key).grown) return null
            assertTrue(farming.isProtected(s, FarmingPatches.FALADOR_NW.key, Crops.POTATO) && farming.isProtected(s, FarmingPatches.FALADOR_SE.key, Crops.POTATO))
            return s
        }
        val illSeen = (1..300).any {
            val s = flowerTick() ?: return@any false
            s.patches.getValue(FarmingPatches.FALADOR_NW.key).diseased && s.patches.getValue(FarmingPatches.FALADOR_SE.key).diseased
        }
        assertTrue(illSeen, "both allotments roll unprotected on the flower's own tick")
    }

    @Test
    fun `trees draw as the cache numbers them, fall ill at their own rates and dig up as many roots as the Wiki says`() {
        val trees = Crops.ALL.filter { it.kind == PatchKind.TREE }
        assertEquals(listOf(4, 6, 8, 10, 12), trees.map { it.stages })
        val values = trees.flatMap { crop -> crop.growing + (0..2).map { crop.harvest + it } + crop.diseased + crop.dead }
        assertEquals(values.size, values.toSet().size, "a tree value drawn twice")
        assertTrue(values.all { it in 8..189 }, "0 to 7 are the weed stages")
        // Oak: growing 8 to 11, Check-health 12, Chop down 13, stump 14 (the cache's tree multiloc).
        val oak = PatchState(0, Crops.OAK, 2)
        assertEquals(10, oak.value(PatchKind.TREE))
        oak.stage = 4
        assertEquals(12, oak.value(PatchKind.TREE))
        oak.checked = true
        assertEquals(13, oak.value(PatchKind.TREE))
        oak.stump = true
        assertEquals(14, oak.value(PatchKind.TREE))
        assertEquals(62, PatchState(0, Crops.MAGIC, 12, checked = true, stump = true).value(PatchKind.TREE))
        // The Disease page's maple and magic rows.
        assertEquals(listOf(13, 7, 3, 2), (0..3).map { Compost.diseaseChance(Crops.MAPLE.disease, it, false) })
        assertEquals(listOf(9, 5, 2, 1), (0..3).map { Compost.diseaseChance(Crops.MAGIC.disease, it, false) })
        // Roots: one at the tree's level, one more every 8 levels, four at most (the maple and yew pages).
        assertEquals(listOf(1, 1, 2, 3, 4, 4), listOf(45, 52, 53, 61, 69, 99).map { Trees.roots(Crops.MAPLE, it) })
        assertEquals(listOf(1, 2, 3, 4), listOf(60, 68, 76, 84).map { Trees.roots(Crops.YEW, it) })
        // Offline a yew's first stage ends at the first farming tick 5 minutes after planting: planted at minute 4 (tick
        // 0), not at tick 1 (minute 5) but at tick 2; planted at minute 5, at tick 2. Online it grows on time, every game
        // tick (the headless tree test). Then every eighth tick; an oak every eighth from the start.
        val farming = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap()), clock = { 0L }, diseases = false)
        val state = FarmingState()
        state.patches[FarmingPatches.LUMBRIDGE_TREE.key] = PatchState(0, Crops.YEW).also { it.plantedMillis = 4 * 60_000L }
        state.patches[FarmingPatches.VARROCK_TREE.key] = PatchState(0, Crops.OAK)
        state.patches[FarmingPatches.FALADOR_TREE.key] = PatchState(0, Crops.YEW).also { it.plantedMillis = 5 * 60_000L }
        farming.grow(state, 1, 1)
        assertEquals(0, state.patches.getValue(FarmingPatches.LUMBRIDGE_TREE.key).stage, "one minute after planting")
        assertEquals(0, state.patches.getValue(FarmingPatches.FALADOR_TREE.key).stage)
        farming.grow(state, 2, 2)
        assertEquals(1, state.patches.getValue(FarmingPatches.LUMBRIDGE_TREE.key).stage, "six minutes after planting")
        assertEquals(1, state.patches.getValue(FarmingPatches.FALADOR_TREE.key).stage, "five minutes after planting")
        assertEquals(0, state.patches.getValue(FarmingPatches.VARROCK_TREE.key).stage)
        // Caught up offline, a yew planted at minute 34 ends its first stage at 39 and grows again at the tick of minute
        // 40 (tick 8), as it would online: logging out costs no growth.
        val caught = FarmingState().apply { patches["lumbridge_tree"] = PatchState(0, Crops.YEW).also { it.plantedMillis = 34 * 60_000L } }
        farming.grow(caught, 7, 7)
        assertEquals(0, caught.patches.getValue("lumbridge_tree").stage)
        farming.grow(caught, 8, 8)
        assertEquals(2, caught.patches.getValue("lumbridge_tree").stage)
        // The planting minute survives a save while the yew is in its first stage.
        val saved = FarmingState().apply { patches["lumbridge_tree"] = PatchState(0, Crops.YEW).also { it.plantedMillis = 123 } }
        assertEquals(123, FarmingSaves.fromJson(FarmingSaves.toJson(saved)).patches.getValue("lumbridge_tree").plantedMillis)
        state.patches.remove(FarmingPatches.FALADOR_TREE.key)
        farming.grow(state, 3, 72)
        assertEquals(10, state.patches.getValue(FarmingPatches.LUMBRIDGE_TREE.key).stage, "5 + 9 x 40 minutes")
        assertEquals(4, state.patches.getValue(FarmingPatches.VARROCK_TREE.key).stage)
        // A stump grows back at the tree's next tick.
        val varrock = state.patches.getValue(FarmingPatches.VARROCK_TREE.key)
        varrock.checked = true
        varrock.stump = true
        farming.grow(state, 73, 79)
        assertTrue(varrock.stump)
        farming.grow(state, 80, 80)
        assertTrue(!varrock.stump && varrock.checked)
        // ::farmdisease off: with diseases on in the world, a player's crops still never fall ill on their own.
        val ill = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap()), clock = { 0L })
        repeat(200) {
            val trial = FarmingState().apply { noDisease = true }
            trial.patches["varrock_tree"] = PatchState(0, Crops.OAK, 1)
            ill.grow(trial, 8, 24)
            assertTrue(!trial.patches.getValue("varrock_tree").diseased && trial.patches.getValue("varrock_tree").stage == 4)
        }
        // Checked and stump survive a save.
        varrock.stump = true
        val back = FarmingSaves.fromJson(FarmingSaves.toJson(state))
        assertEquals(14, back.value(FarmingPatches.VARROCK_TREE))
    }

    @Test
    fun `fruit trees draw as the cache numbers them, fall ill in 4 of 6 growths and grow a fruit every 40 minutes`() {
        val fruit = Crops.ALL.filter { it.kind == PatchKind.FRUIT_TREE }
        assertEquals(8, fruit.size)
        assertTrue(fruit.all { it.stages == 6 && it.disease == 18 })
        val values = fruit.flatMap { crop -> crop.growing + (0..6).map { crop.harvest + it } + crop.diseased + crop.dead + crop.stumpValue + crop.checkValue }
        assertEquals(values.size, values.toSet().size, "a fruit tree value drawn twice")
        assertTrue(values.all { it in 8..253 }, "0 to 7 are the weed stages")
        // Apple: growing 8 to 13, 0 to 6 apples 14 to 20, diseased from 21, dead from 27, stump 33, Check-health 34.
        val apple = PatchState(0, Crops.APPLE, 3)
        assertEquals(11, apple.value(PatchKind.FRUIT_TREE))
        apple.stage = 6
        assertEquals(34, apple.value(PatchKind.FRUIT_TREE))
        apple.checked = true
        apple.lives = 6
        assertEquals(20, apple.value(PatchKind.FRUIT_TREE))
        apple.lives = 0
        assertEquals(14, apple.value(PatchKind.FRUIT_TREE))
        apple.stump = true
        assertEquals(33, apple.value(PatchKind.FRUIT_TREE))
        assertEquals(22, PatchState(0, Crops.APPLE, 2, diseased = true).value(PatchKind.FRUIT_TREE))
        assertEquals(253, PatchState(0, Crops.DRAGONFRUIT, 6).value(PatchKind.FRUIT_TREE))
        // The Disease page's fruit tree row: 18, 9, 4, 2.
        assertEquals(listOf(18, 9, 4, 2), (0..3).map { Compost.diseaseChance(Crops.APPLE.disease, it, false) })
        // Every 32nd tick a stage; with every growth ill, only 4 of the 6 roll (stages 1 to 4).
        val farming = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap()), clock = { 0L }, diseases = false)
        val state = FarmingState()
        val key = FarmingPatches.CATHERBY_FRUIT.key
        state.patches[key] = PatchState(0, Crops.BANANA)
        farming.grow(state, 1, 31)
        assertEquals(0, state.patches.getValue(key).stage)
        farming.grow(state, 32, 192)
        val banana = state.patches.getValue(key)
        assertTrue(banana.grown && !banana.checked && banana.lives == 0, "grown at 6 x 160 minutes, no fruit before the check")
        farming.grow(state, 193, 300)
        assertEquals(0, banana.lives, "no fruit grows before the check")
        banana.checked = true
        banana.lives = 4
        farming.grow(state, 301, 303)
        assertEquals(4, banana.lives)
        farming.grow(state, 304, 304)
        assertEquals(5, banana.lives, "a fruit at the next 40-minute tick")
        farming.grow(state, 305, 400)
        assertEquals(6, banana.lives, "six at most")
        // Lives, checked and a stump with no fruit survive a save.
        banana.lives = 0
        banana.stump = true
        val back = FarmingSaves.fromJson(FarmingSaves.toJson(state))
        assertEquals(60, back.value(FarmingPatches.CATHERBY_FRUIT))
        banana.stump = false
        assertEquals(41, FarmingSaves.fromJson(FarmingSaves.toJson(state)).value(FarmingPatches.CATHERBY_FRUIT), "a checked banana tree with no fruit")
        assertTrue(Crops.ALL.filter { it.kind == PatchKind.FRUIT_TREE }.all { it.lastDiseaseStage == 4 })
        // Each patch is found where the cache places it; the Gnome Stronghold's two share their map square.
        assertEquals(FarmingPatches.GNOME_STRONGHOLD_TREE, FarmingPatches.at(19147, 2435, 3414))
        assertEquals(FarmingPatches.GNOME_STRONGHOLD_FRUIT, FarmingPatches.at(7962, 2475, 3445))
        assertEquals(FarmingPatches.GNOME_VILLAGE_FRUIT, FarmingPatches.at(7963, 2489, 3179))
        assertEquals(FarmingPatches.BRIMHAVEN_FRUIT, FarmingPatches.at(7964, 2764, 3212))
        assertEquals(FarmingPatches.CATHERBY_FRUIT, FarmingPatches.at(7965, 2860, 3433))
        // With disease on, the growths from stages 0 and 5 never fall ill; from stage 1 some do (18/128).
        val ill = Farming(osrsps.game.World(IntArray(0), osrsps.map.CollisionMap()), clock = { 0L })
        val illFrom = IntArray(6)
        repeat(300) {
            for (stage in 0..5) {
                val trial = FarmingState()
                trial.patches[key] = PatchState(0, Crops.APPLE, stage)
                ill.grow(trial, 32, 32)
                if (trial.patches.getValue(key).diseased) illFrom[stage]++
            }
        }
        assertEquals(0, illFrom[0])
        assertEquals(0, illFrom[5])
        assertTrue((1..4).all { illFrom[it] in 10..90 }, "about 42 in 300 for each: ${illFrom.toList()}")
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
        state.patches[FarmingPatches.FALADOR_SE.key] = PatchState(1, compost = Compost.SUPER)
        state.patches[FarmingPatches.FALADOR_HERB.key] = PatchState(0, Crops.RANARR, 2, compost = Compost.ULTRA, diseased = true)
        state.patches[FarmingPatches.FALADOR_FLOWER.key] = PatchState(0, Crops.MARIGOLD, 3, dead = true)
        val json = FarmingSaves.toJson(state)
        val back = FarmingSaves.fromJson(json)
        assertEquals(41, back.lastTick)
        assertEquals(3, back.skipped)
        assertEquals(FarmingSaves.toJson(state), FarmingSaves.toJson(back))
        assertEquals(FarmingPatches.ALL.map { state.value(it) }, FarmingPatches.ALL.map { back.value(it) })
        assertEquals(listOf(Compost.SUPER, Compost.ULTRA), listOf(back.patches.getValue(FarmingPatches.FALADOR_SE.key).compost, back.patches.getValue(FarmingPatches.FALADOR_HERB.key).compost))
        assertEquals(141, back.value(FarmingPatches.FALADOR_HERB))
        assertEquals(203, back.value(FarmingPatches.FALADOR_FLOWER))
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
        // Ill and dead crops draw their stage's value, counted from stage 1.
        assertEquals(136, PatchState(0, Crops.POTATO, 2, diseased = true).value(PatchKind.ALLOTMENT))
        assertEquals(200, PatchState(0, Crops.POTATO, 2, dead = true).value(PatchKind.ALLOTMENT))
        assertEquals(171, PatchState(0, Crops.TORSTOL, 2, dead = true).value(PatchKind.HERB))
    }
}
