package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.content.LumbridgeSwampCaves
import osrsps.game.skill.Fishing
import osrsps.map.Tile

class NpcSpawnFileTest {
    private val types = NpcTypes.of(
        NpcType(3105, "Hans", internalName = "hans"),
        NpcType(2790, "Cow", size = 2, internalName = "cow"),
        NpcType(2791, "Cow", size = 2, internalName = "cow2"),
        NpcType(2793, "Cow", size = 2, internalName = "cow3"),
        NpcType(2792, "Cow calf", size = 2, internalName = "cow2_calf"),
        NpcType(2794, "Cow calf", size = 2, internalName = "cow3_calf"),
        NpcType(16821, "Captain Harlan", internalName = "captain_harlan"),
        // The 241 cache's ids (dump.npc).
        NpcType(2813, "Shop keeper", internalName = "generalshopkeeper1"),
        NpcType(2814, "Shop assistant", internalName = "generalassistant1"),
        NpcType(10619, "Bob", internalName = "bob"),
        NpcType(7546, "Bartender", internalName = "ram_bartender"),
        NpcType(16808, "Wilfred", internalName = "wilfred"),
        NpcType(16806, "Feoras", internalName = "lumbridge_blacksmith"),
        NpcType(921, "Donie", internalName = "lumbridge_guide2_woman"),
        NpcType(6816, "Gee", internalName = "lumbridge_guide2_man"),
        NpcType(311, "Ironman tutor", internalName = "ironman_tutor_1"),
        NpcType(8368, "Abigaila", internalName = "tob_spectator_misthalin"),
        NpcType(3106, "Man", internalName = "man"),
        NpcType(3107, "Man", internalName = "man2"),
        NpcType(3108, "Man", internalName = "man3"),
        NpcType(6818, "Man", internalName = "man_indoor"),
        NpcType(3111, "Woman", internalName = "woman"),
        NpcType(3112, "Woman", internalName = "woman2"),
        NpcType(3113, "Woman", internalName = "woman3"),
        NpcType(2854, "Rat", internalName = "rat"),
        NpcType(1530, "Fishing spot", internalName = "0_50_49_saltfish"),
        NpcType(1527, "Rod Fishing spot", internalName = "0_50_50_freshfish"),
    )

    private fun file(name: String) = NpcSpawnFile::class.java.getResource("/npc-spawns/$name")!!.readText()

    @Test
    fun `the Lumbridge file has the cows east of the river, each with a source`() {
        val spawns = NpcSpawnFile.parse(file("lumbridge.json"), types).spawns
        val cows = spawns.filter { it.type.name == "Cow" }
        assertEquals(21, cows.size)
        assertEquals(8, spawns.count { it.type.name == "Cow calf" })
        // The River Lum runs north from Lumbridge at about x 3240; the field is east of it.
        assertTrue(cows.all { it.x > 3240 && it.z in 3250..3300 })
        // All but the Bartender, at his bar, and the fishing spots wander.
        val still = setOf(7546, Fishing.SWAMP_SPOT, Fishing.RIVER_SPOT)
        assertTrue(spawns.all { it.source.isNotBlank() && (it.wander > 0 || it.type.id in still) })
    }

    @Test
    fun `the town centre's NPCs stand west of the river, Abigaila upstairs in the General Store`() {
        val spawns = NpcSpawnFile.parse(file("lumbridge.json"), types).spawns
        val town = spawns.filter { it.type.id in setOf(7546, 16808, 16806, 311, 8368) }
        assertEquals(5, town.size)
        assertTrue(town.all { it.x < 3240 && it.z in 3190..3260 })
        assertEquals(1, town.single { it.type.id == 8368 }.level)
        assertEquals(2, spawns.count { it.type.id == 921 })
        assertEquals(2, spawns.count { it.type.id == 6816 })
    }

    @Test
    fun `Captain Harlan alone stands on the hill north of the castle, the three 241 tutors gone`() {
        val result = NpcSpawnFile.parse(file("lumbridge.json"), types)
        assertEquals(emptyList(), result.unknown)
        assertTrue(result.spawns.none { it.type.id in 3216..3218 })
        val harlan = result.spawns.single { it.type.id == 16821 }
        assertEquals(Triple(3218, 3238, 0), Triple(harlan.x, harlan.z, harlan.level))
    }

    @Test
    fun `the swamp has five Net and Bait spots and the river two Lure and Bait spots, each on a tile of its area`() {
        val spawns = NpcSpawnFile.parse(file("lumbridge.json"), types).spawns
        assertEquals(5, spawns.count { it.type.id == Fishing.SWAMP_SPOT })
        assertEquals(2, spawns.count { it.type.id == Fishing.RIVER_SPOT })
        for (spot in spawns.filter { it.type.id == Fishing.SWAMP_SPOT || it.type.id == Fishing.RIVER_SPOT }) {
            assertTrue(Fishing.AREAS.any { Tile(spot.x, spot.z) in it }, "a spot at ${spot.x}, ${spot.z}")
        }
    }

    @Test
    fun `the Lumbridge Castle file reads, with the 241 cache's names`() {
        // Ids and gameval names as the 241 cache has them (checked 2026-10-25).
        val castle = NpcTypes.of(
            NpcType(3105, "Hans", internalName = "hans"),
            NpcType(4626, "Cook", internalName = "cook"),
            NpcType(7457, "null", internalName = "lost_property_merchant_standard"),
            NpcType(3106, "Man", internalName = "man"),
            NpcType(3108, "Man", internalName = "man3"),
            NpcType(3111, "Woman", internalName = "woman"),
            NpcType(5007, "Imp", internalName = "imp"),
            NpcType(2854, "Rat", internalName = "rat"),
            NpcType(815, "Duke Horacio", internalName = "duke_of_lumbridge"),
            NpcType(16807, "Cadmus", internalName = "cadmus"),
            NpcType(6520, "null", internalName = "deadman_banker_blue_south"),
            NpcType(2897, "Banker", internalName = "banker1_new"),
            NpcType(306, "Lumbridge Guide", internalName = "lumbridge_guide"),
            NpcType(119, "null", internalName = "cws_doomsayer"),
            NpcType(16804, "Mikasi", internalName = "mikasi_lumbridge_diary"),
        )
        val result = NpcSpawnFile.parse(file("lumbridge-castle.json"), castle, "lumbridge-castle.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(28, spawns.size)
        assertEquals(10, spawns.count { it.type.id == 2854 }, "rats")
        assertEquals(5, spawns.count { it.level == 1 })
        val hans = spawns.single { it.type.id == 3105 }
        assertEquals(10, hans.patrol.size)
        assertEquals(10, hans.patrol.sumOf { it.wait })
        assertEquals(MoveRestrict.OUTDOORS, hans.restrict)
        assertEquals(MoveRestrict.INDOORS, spawns.single { it.type.id == 4626 }.restrict)
        // Everyone but Hans, Cadmus and Mikasi wanders 5 or stands still on purpose.
        assertTrue(spawns.filter { it.patrol.isEmpty() && it.type.id !in setOf(16807, 16804) }.all { it.wander == 0 || it.wander == 5 })
        assertEquals(2, spawns.single { it.type.id == 16807 }.wander, "Cadmus: his Wiki map's radius")
        val mikasi = spawns.single { it.type.id == 16804 }
        assertEquals(Triple(3233, 3214, 4), Triple(mikasi.x, mikasi.z, mikasi.wander), "Mikasi: her Wiki map, where Hatius stood")
    }

    @Test
    fun `the farms file has Fred's sheep, rams, lambs and 33 chickens north-west of the castle, Gillie, Millie and nine farmers`() {
        val names = mapOf(
            732 to "fred_the_farmer", 2693 to "sheepunsheered", 2699 to "sheepunsheered3", 2694 to "sheepunsheeredg", 2695 to "sheepunsheeredw",
            2786 to "sheepunsheered3g", 2787 to "sheepunsheered3w", 1262 to "ramunsheered", 1263 to "ramunsheered2", 1264 to "ramunsheered3",
            1176 to "lamb1", 1177 to "lamb2", 1301 to "sheepsheered2w", 1308 to "sheepsheered3g", 1173 to "chicken", 1174 to "chicken_brown",
            4628 to "gillie_the_milkmaid", 4627 to "millie_the_miller",
            3114 to "farmer1", 3243 to "farmer2", 3244 to "farmer3", 11918 to "farmer1_f", 11919 to "farmer2_f", 11920 to "farmer3_f", 11921 to "farmer4",
        )
        val farmTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-farms.json"), farmTypes, "lumbridge-farms.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(1, spawns.count { it.type.id == 732 })
        assertEquals(16, spawns.count { it.type.internalName!!.startsWith("sheepunsheered") })
        assertEquals(4, spawns.count { it.type.internalName!!.startsWith("ram") })
        assertEquals(33, spawns.count { it.type.internalName!!.startsWith("chicken") })
        // Fred's farm is west of the River Lum and north of the castle; the two shorn sheep are in the pen west of it.
        val farmers = spawns.filter { it.type.internalName!!.startsWith("farmer") }
        val farm = spawns.filter { !it.type.internalName!!.startsWith("sheepsheered") && it.type.id !in setOf(4627, 4628) && it !in farmers }
        assertTrue(farm.all { it.x in 3165..3215 && it.z in 3255..3310 && it.level == 0 })
        assertTrue(spawns.filter { it.type.internalName!!.startsWith("sheepsheered") }.all { it.x < 3200 && it.z < 3225 })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == 5 })
        // Gillie in the Groats' dairy cow pen east of the river, Millie inside Mill Lane Mill.
        val gillie = spawns.single { it.type.id == 4628 }
        assertTrue(gillie.x in 3250..3258 && gillie.z in 3268..3278)
        val millie = spawns.single { it.type.id == 4627 }
        assertTrue(millie.x in 3163..3170 && millie.z in 3303..3310 && millie.restrict == MoveRestrict.INDOORS)
        // The Wiki's nine Misthalin farmers (Thieving 2): six on the farm south of Varrock, one at the Groats', two by Lumbridge.
        assertEquals(9, farmers.size)
        assertEquals(7, farmers.map { it.type.id }.toSet().size)
        assertTrue(farmers.all { it.x in 3150..3265 && it.z in 3285..3340 && it.level == 0 })
    }

    @Test
    fun `Thieving 2's Kandarin file has East Ardougne's knights, paladins, heroes and warriors, the watchmen and the Stronghold's gnomes`() {
        val names = mapOf(
            3297 to "knight_of_ardougne", 3293 to "paladin", 3294 to "paladin2", 3295 to "hero", 3260 to "warrior_woman", 5420 to "yanille_watchman",
            6094 to "gnome", 6095 to "browclothedgnome", 6096 to "darkskinned_gnome", 6086 to "gnomefemale", 6087 to "gnomefemale_dskinned",
            6077 to "gnomechildgreen", 6078 to "gnomechildblue", 6079 to "gnomechild",
        )
        val kandarinTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("thieving-kandarin.json"), kandarinTypes, "thieving-kandarin.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        fun of(vararg ids: Int) = spawns.filter { it.type.id in ids }
        assertEquals(listOf(5, 22, 3, 10, 4), listOf(of(3297).size, of(3293, 3294).size, of(3295).size, of(3260).size, of(5420).size))
        // East Ardougne, its castle's two floors among them; the Watchtower's first floor; the Tree Gnome Stronghold.
        assertTrue(of(3297, 3293, 3294, 3295, 3260).all { it.x in 2560..2687 && it.z in 3264..3391 })
        assertEquals(setOf(0, 1), of(3293).map { it.level }.toSet())
        assertTrue(of(5420).all { it.level == 1 && it.x in 2496..2559 && it.z in 3072..3135 })
        val gnomes = of(6094, 6095, 6096, 6086, 6087, 6077, 6078, 6079)
        assertEquals(153, gnomes.size)
        assertTrue(gnomes.all { it.x in 2368..2495 && it.z in 3328..3519 })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == 5 })
    }

    @Test
    fun `Thieving 2's far file has the cave goblins, Fremennik citizens, bandits, wealthy citizens, vyres, elves and TzHaar-Hur`() {
        val file = file("thieving-far.json")
        val names = Regex("\"npc\": \"([a-z0-9_]+)\"").findAll(file).map { it.groupValues[1] }.toSet().toList()
        val farTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(20000 + i, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file, farTypes, "thieving-far.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        fun named(prefix: String) = spawns.filter { it.type.internalName!!.startsWith(prefix) }
        assertEquals(
            listOf(23, 9, 21, 9, 52, 30),
            listOf(named("dorgesh_").size, named("viking_").size, named("fourdiamonds_").size, named("varlamore_wealthy").size,
                named("mourning_town_elf").size + named("prif_citizen").size, named("tzhaar_hur").size),
        )
        // Dorgesh-Kaan under Lumbridge, Rellekka, the Bandit Camp, Civitas illa Fortis, Lletya, Prifddinas, Mor Ul Rek.
        assertTrue(named("dorgesh_").all { it.x in 2688..2751 && it.z in 5248..5375 })
        assertTrue(named("viking_").all { it.x in 2624..2687 && it.z in 3648..3711 })
        assertTrue(named("fourdiamonds_").all { it.x in 3136..3199 && it.z in 2944..3007 })
        assertTrue(named("varlamore_wealthy").all { it.x in 1670..1720 && it.z in 3080..3110 })
        assertTrue(named("mourning_town_elf").all { it.x in 2304..2367 && it.z in 3136..3199 })
        assertTrue(named("prif_citizen").all { it.x in 3200..3327 && it.z in 6016..6143 })
        assertTrue(named("tzhaar_hur").all { it.x in 2432..2559 && it.z in 5056..5183 })
        assertEquals(30, named("").count { it.type.internalName!! in VYRE_NAMES })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == 5 })
    }

    @Test
    fun `the rogues are on Rogues' Castle's four floors and south-west of the Mage Arena`() {
        val rogueTypes = NpcTypes.of(NpcType(526, "rogue", internalName = "rogue"))
        val result = NpcSpawnFile.parse(file("rogues-castle.json"), rogueTypes, "rogues-castle.json")
        assertEquals(emptyList(), result.unknown)
        val rogues = result.spawns
        assertEquals(22, rogues.size)
        val castle = rogues.filter { it.x in 3264..3327 }
        assertEquals(20, castle.size)
        assertEquals(setOf(0, 1, 2, 3), castle.map { it.level }.toSet())
        assertTrue(rogues.all { it.z in 3904..3967 })
    }

    @Test
    fun `east of the river are 50 level 2 goblins and 6 giant spiders, and Barfy Bill at the canoe station`() {
        val names = (1..8).associate { 3028 + it to "goblin_unarmed_melee_$it" } + mapOf(3017 to "giantspider1", 1326 to "canoeing_bill")
        val eastTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-east.json"), eastTypes, "lumbridge-east.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(50, spawns.count { it.type.id in 3029..3036 })
        assertEquals(6, spawns.count { it.type.id == 3017 })
        assertEquals(Tile(3244, 3237), spawns.single { it.type.id == 1326 }.let { Tile(it.x, it.z) })
        // East of the river's west bank, north of the church, south of the cow field; all on the ground.
        assertTrue(spawns.all { it.x in 3238..3266 && it.z in 3215..3260 && it.level == 0 })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == if (it.type.id == 1326) 2 else 5 })
    }

    @Test
    fun `the swamp's four quest NPCs stand still where live has them, as the multi-NPCs that hide them after their quests`() {
        val names = mapOf(7428 to "mistmyst_abigale", 7429 to "mistmyst_hewey", 6278 to "slice_sergeant_mossfists_swamp", 6279 to "slice_sergeant_slimetoes_swamp")
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, "null", internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-swamp-quest.json"), types, "lumbridge-swamp-quest.json")
        assertEquals(emptyList(), result.unknown)
        val tiles = result.spawns.associate { it.type.id to Tile(it.x, it.z) }
        assertEquals(mapOf(7428 to Tile(3237, 3155), 7429 to Tile(3237, 3153), 6278 to Tile(3169, 3170), 6279 to Tile(3171, 3170)), tiles)
        assertTrue(result.spawns.all { it.wander == 0 && it.level == 0 && it.source.isNotBlank() })
    }

    @Test
    fun `west of the castle has mejrs's 11 goblins, 8 giant spiders and 7 spiders round the H A M trapdoor`() {
        val names = (1..7).associate { (3028 + it) to "goblin_unarmed_melee_$it" } + mapOf(3017 to "giantspider1", 3019 to "spider")
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-west.json"), types, "lumbridge-west.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(listOf(11, 8, 7), listOf(spawns.count { it.type.id in 3029..3035 }, spawns.count { it.type.id == 3017 }, spawns.count { it.type.id == 3019 }))
        assertTrue(spawns.all { it.x in 3141..3170 && it.z in 3222..3261 && it.level == 0 && it.wander == 5 && it.source.isNotBlank() })
    }

    @Test
    fun `the swamp caves have the Wiki's counts of their monsters, all on dark tiles, the two fishing spots and the seven wall beasts' holes`() {
        val names = mapOf(
            481 to "swamp_cave_bug", 482 to "cave_littlebug", 480 to "swamp_cave_slime", 406 to "slayer_cave_crawler_1", 407 to "slayer_cave_crawler_2",
            408 to "slayer_cave_crawler_3", 409 to "slayer_cave_crawler_4", 478 to "medium_frog", 477 to "giant_frog", 6434 to "cave_goblin",
            6435 to "cave_goblin2", 6436 to "cave_goblin3", 6437 to "cave_goblin4", 1497 to "0_49_149_cavefish", 1498 to "0_50_149_cavefish",
            421 to "slayer_rockslug", 422 to "slayer_rockslug_baby", 475 to "swamp_wallbeast",
        )
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-swamp-caves.json"), types, "lumbridge-swamp-caves.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        fun count(vararg ids: Int) = spawns.count { it.type.id in ids }
        // OSRS Wiki, Lumbridge Swamp Caves: 21 bugs, 36 larvae, 17 slimes, 3 crawlers, 11 big frogs, 3 giant frogs, 4 cave goblins.
        assertEquals(listOf(21, 36, 17, 3, 11, 3, 4), listOf(count(481), count(482), count(480), count(406, 407, 408, 409), count(478), count(477), count(6434, 6435, 6436, 6437)))
        assertEquals(5, count(421, 422))
        val monsters = spawns.filter { it.type.id !in setOf(1497, 1498, 475) }
        assertTrue(monsters.all { LumbridgeSwampCaves.isDark(it.x, it.z, it.level) && it.wander == 5 && it.source.isNotBlank() })
        assertEquals(setOf(Tile(3154, 9544), Tile(3245, 9570)), spawns.filter { it.type.id in setOf(1497, 1498) }.map { Tile(it.x, it.z) }.toSet())
        // The Wiki's seven holes, in the wall: each never moves, and the cave tile south of it is where it grabs.
        val holes = spawns.filter { it.type.id == 475 }
        assertEquals(7, holes.size)
        assertTrue(holes.all { it.restrict == MoveRestrict.NOMOVE && it.wander == 0 && !LumbridgeSwampCaves.isDark(it.x, it.z, 0) && LumbridgeSwampCaves.isDark(it.x, it.z - 1, 0) })
    }

    @Test
    fun `the HAM Hideout has its 26 pickpocketable members, 11 seated, 22 guards, the Deacon, Johanhus and Jimmy in his cell`() {
        val names = mapOf(
            2540 to "favour_male_ham_civilian", 2541 to "favour_female_ham_civilian", 2542 to "favour_npc_chair",
            2543 to "favour_npc_floor", 2536 to "favour_guard_male_no_beard", 2537 to "favour_guard_male_bearded",
            2538 to "favour_guard_long_beard",
        )
        val result = NpcSpawnFile.parse(file("ham-hideout.json"), NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray()), "ham-hideout.json")
        val spawns = result.spawns
        fun count(vararg ids: Int) = spawns.count { it.type.id in ids }
        assertEquals(listOf(26, 11, 22), listOf(count(2540, 2541), count(2542, 2543), count(2536, 2537, 2538)))
        assertTrue(spawns.all { it.x in 3140..3190 && it.z in 9600..9660 && it.level == 0 && it.source.isNotBlank() })
        assertTrue(spawns.filter { it.type.id in setOf(2542, 2543) }.all { it.wander == 0 })
        assertEquals(3, result.unknown.size, "the Deacon, Johanhus and Jimmy: ${result.unknown}")
    }

    @Test
    fun `the swamp has its 23 giant rats, 82 frogs, the level 5 goblin, the unicorn, its talking NPCs and tutors, and rats west of the castle`() {
        val names = mapOf(
            2856 to "giantrat", 2859 to "giantrat_grey", 2860 to "giantrat_grey2", 2861 to "giantrat_grey3", 2863 to "giantrat1_2",
            2864 to "giantrat1_3", 2854 to "rat", 8700 to "giant_frog_nodrops", 8701 to "medium_frog_nodrops", 8702 to "little_frog_nodrops",
            3045 to "goblin_armed", 2837 to "unicorn", 238 to "butterfly_viceroy", 923 to "father_urhney", 1157 to "archeradventurerpg",
            1158 to "warrioradventurerpg", 1159 to "monkadventurerpg", 1160 to "wizardadventuterpg", 1896 to "swamp_candle_guy",
            16809 to "monlum", 16810 to "finlay",
        )
        val swampTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-swamp.json"), swampTypes, "lumbridge-swamp.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        fun count(vararg ids: Int) = spawns.count { it.type.id in ids }
        assertEquals(10, count(2856))
        assertEquals(13, count(2863, 2864))
        assertEquals(4, count(2859, 2860, 2861))
        assertEquals(4, count(2854))
        assertEquals(listOf(45, 29, 8), listOf(count(8702), count(8701), count(8700)))
        assertEquals(1, count(3045))
        assertEquals(1, count(2837))
        // South of the castle and the church, west of the river, north of the swamp's south coast; all on the ground.
        assertTrue(spawns.all { it.x in 3130..3260 && it.z in 3140..3214 && it.level == 0 })
        // Father Urhney in his house, the four adventurers at their camp, the candle seller by the caves.
        val urhney = spawns.single { it.type.id == 923 }
        assertTrue(urhney.x in 3144..3149 && urhney.z in 3172..3177 && urhney.restrict == MoveRestrict.INDOORS)
        assertEquals(4, spawns.count { it.type.id in 1157..1160 && it.x in 3148..3153 && it.z in 3204..3208 })
        assertEquals(Tile(3170, 3176), spawns.single { it.type.id == 1896 }.let { Tile(it.x, it.z) })
        // The mining tutor by the east mine and the fishing tutor by the shore.
        assertEquals(Tile(3227, 3146), spawns.single { it.type.id == 16809 }.let { Tile(it.x, it.z) })
        assertEquals(Tile(3244, 3157), spawns.single { it.type.id == 16810 }.let { Tile(it.x, it.z) })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == when (it.type.id) { 1896 -> 2; 16809 -> 4; 16810 -> 3; else -> 5 } })
    }

    @Test
    fun `Al Kharid has two Border Guards each side of the toll gate, five bankers, a keeper for each shop, its fighters, camels and the Arena's people`() {
        val names = mapOf(
            4287 to "borderguard1", 4288 to "borderguard2", 3090 to "kharidbanker1", 3091 to "kharidbanker2",
            2817 to "generalshopkeeper3", 2818 to "generalassistant3", 2875 to "zeke", 2879 to "dommik", 2876 to "louie_legs",
            2878 to "ranael", 2874 to "gem_trader", 3533 to "feud_ali_m", 2877 to "kebab_seller", 2873 to "silk_trader",
            3231 to "ellis_tanner", 4285 to "hassan", 4286 to "osman", 11645 to "prince_ali_palace",
            3292 to "al_kharid_warrior", 3261 to "al_kharid_man", 3024 to "scorpion", 2835 to "camel", 5949 to "al_the_camel",
            5950 to "elly_the_camel", 5951 to "ollie_the_camel", 5952 to "cam_the_camel", 5953 to "alice_the_camel_multinpc",
            1528 to "0_51_49_saltfish", 4642 to "shantay", 4643 to "shantay_guard", 4648 to "shantay_guard_still",
            4641 to "tourtrap_qip_irena_happy", 3106 to "man", 3107 to "man2", 3108 to "man3", 3019 to "spider",
            6089 to "pilot_al_kharid", 11664 to "pvpa_guard_1", 11673 to "pvpa_duel_guide", 11672 to "pvpa_1v1_guide",
        )
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("al-kharid.json"), types, "al-kharid.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        // borderguard1 on the Lumbridge side (west of the gate's x 3268), borderguard2 on the Al Kharid side.
        assertEquals(setOf(Tile(3267, 3226), Tile(3267, 3229)), spawns.filter { it.type.id == 4287 }.map { Tile(it.x, it.z) }.toSet())
        assertEquals(setOf(Tile(3268, 3226), Tile(3268, 3229)), spawns.filter { it.type.id == 4288 }.map { Tile(it.x, it.z) }.toSet())
        // Five bankers behind the bank's booths (x 3268), as the Wiki counts them.
        assertEquals(5, spawns.count { it.type.id == 3090 || it.type.id == 3091 })
        assertTrue(spawns.filter { it.type.id == 3090 || it.type.id == 3091 }.all { it.x == 3267 && it.z in 3164..3169 })
        // One of each shop keeper, inside their shops.
        for (id in listOf(2817, 2818, 2875, 2879, 2876, 2878)) assertEquals(MoveRestrict.INDOORS, spawns.single { it.type.id == id }.restrict)
        assertEquals(1, spawns.count { it.type.id == 2874 })
        assertEquals(1, spawns.count { it.type.id == 3533 })
        assertEquals(1, spawns.count { it.type.id == 2877 })
        assertEquals(1, spawns.count { it.type.id == 2873 })
        assertEquals(MoveRestrict.INDOORS, spawns.single { it.type.id == 3231 }.restrict)
        // The palace: Hassan and Prince Ali in the throne room, Osman outside.
        for (id in listOf(4285, 11645)) assertTrue(spawns.single { it.type.id == id }.let { it.x in 3298..3303 && it.z in 3159..3166 })
        assertEquals(1, spawns.count { it.type.id == 4286 })
        // Fighters: nine warriors in the palace, six men, three spiders, nine scorpions in the mine and one south of the bank (the Wiki's counts).
        assertEquals(9, spawns.count { it.type.id == 3292 && it.x in 3281..3303 && it.z in 3159..3178 })
        assertEquals(6, spawns.count { it.type.id in listOf(3261, 3106, 3107, 3108) })
        assertEquals(3, spawns.count { it.type.id == 3019 })
        assertEquals(9, spawns.count { it.type.id == 3024 && it.z > 3270 })
        assertEquals(1, spawns.count { it.type.id == 3024 && it.z < 3160 })
        // The plain Camel and the five named ones.
        for (id in listOf(2835, 5949, 5950, 5951, 5952, 5953)) assertEquals(1, spawns.count { it.type.id == id })
        // Two fishing spots by the sea, south-west of the town.
        assertEquals(2, spawns.count { it.type.id == 1528 && it.x in 3266..3277 && it.z in 3139..3148 })
        // The Shantay Pass: Shantay, the gate guard, three guards and Irena.
        assertEquals(3, spawns.count { it.type.id == 4643 })
        for (id in listOf(4642, 4648, 4641)) assertEquals(1, spawns.count { it.type.id == id && it.z in 3110..3130 })
        // Captain Dalbur by his glider, and the Emir's Arena's guard and Guides (at guessed tiles) by its gate and in its lobby.
        assertEquals(1, spawns.count { it.type.id == 6089 && it.x == 3284 && it.z == 3212 })
        for (id in listOf(11664, 11673, 11672)) assertEquals(1, spawns.count { it.type.id == id && it.x in 3340..3380 && it.z in 3260..3280 })
        // Al Kharid: east of the toll gate's fence line, from the Shantay Pass to the mine's north end, and the Arena.
        assertTrue(spawns.all { it.x in 3267..3380 && it.z in 3110..3320 && it.level == 0 && it.source.isNotBlank() })
        assertTrue(spawns.filter { it.type.id in listOf(4287, 4288, 3090, 3091) }.all { it.wander == 0 })
    }

    @Test
    fun `Edgeville has four bankers behind the booths, the grange's seven men, its rats, giant spiders and imps, its shopkeepers, its guards, its people, the Basement of Doom, the outlaws' camp and the monastery`() {
        val names = mapOf(
            1613 to "banker1", 1618 to "banker2", 3106 to "man", 3107 to "man2", 3108 to "man3", 2854 to "rat", 3017 to "giantspider1", 5007 to "imp",
            2821 to "generalshopkeeper5", 2822 to "generalassistant5", 7457 to "lost_property_merchant_standard", 822 to "oziach", 2200 to "wilderness_capeseller_6",
            3254 to "guard1", 4808 to "hundred_dave_mum", 1325 to "canoeing_hari", 13644 to "ikov_lucien2", 4806 to "hundred_evil_dave",
            4809 to "hundred_dave_hellrat", 6793 to "hundred_dave_bigrat",
            2579 to "monk", 2577 to "abbot_langley", 2578 to "brother_jered", 1397 to "brother_althric",
        ) + (4167..4176).associateWith { "surok_outlaw${it - 4166}" }
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("edgeville.json"), types, "edgeville.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        // Four bankers (the Wiki's count), behind the booths at x 3095 and z 3493, standing still.
        val bankers = spawns.filter { it.type.id == 1613 || it.type.id == 1618 }
        assertEquals(4, bankers.size)
        assertTrue(bankers.all { it.x in 3096..3098 && it.z in 3489..3492 && it.wander == 0 })
        // Seven men (the Wiki's count), indoors in the grange north of the bank.
        val men = spawns.filter { it.type.id in 3106..3108 }
        assertEquals(7, men.size)
        assertTrue(men.all { it.x in 3092..3101 && it.z in 3508..3512 && it.restrict == MoveRestrict.INDOORS })
        assertEquals(3, spawns.count { it.type.id == 2854 })
        assertEquals(3, spawns.count { it.type.id == 3017 })
        assertEquals(4, spawns.count { it.type.id == 5007 })
        // Six guards (the Wiki's count) along the ditch, north of the town.
        val guards = spawns.filter { it.type.id == 3254 }
        assertEquals(6, guards.size)
        assertTrue(guards.all { it.z in 3512..3518 })
        // One each of the General Store's keepers, Perdu (upstairs, level 1), Oziach and Richard.
        assertEquals(listOf(2821, 2822, 7457, 822, 2200), spawns.map { it.type.id }.filter { it in setOf(2821, 2822, 7457, 822, 2200) })
        // One each of Doris, Hari and Lucien; Evil Dave, 18 hell-rats and the Wiki's four behemoths in the Basement of Doom.
        assertEquals(listOf(4808, 1325, 13644), spawns.map { it.type.id }.filter { it in setOf(4808, 1325, 13644) })
        val basement = spawns.filter { it.type.id in setOf(4806, 4809, 6793) }
        assertEquals(listOf(1, 18, 4), listOf(4806, 4809, 6793).map { id -> basement.count { it.type.id == id } })
        assertTrue(basement.all { it.x in 3064..3090 && it.z in 9870..9905 && it.level == 0 })
        // One of each of the ten outlaws, in their camp.
        val outlaws = spawns.filter { it.type.id in 4167..4176 }
        assertEquals((4167..4176).toList(), outlaws.map { it.type.id }.sorted())
        assertTrue(outlaws.all { it.x in 3114..3126 && it.z in 3468..3479 && it.level == 0 })
        // The monastery, west of the town: six monks (four in the courtyard, two upstairs), the Abbot and Brother Althric
        // downstairs and Brother Jered upstairs.
        val monastery = spawns.filter { it.type.id in setOf(2579, 2577, 2578, 1397) }
        assertEquals(listOf(6, 1, 1, 1), listOf(2579, 2577, 2578, 1397).map { id -> monastery.count { it.type.id == id } })
        assertEquals(listOf(0, 0, 0, 0, 1, 1), monastery.filter { it.type.id == 2579 }.map { it.level }.sorted())
        assertEquals(1, monastery.single { it.type.id == 2578 }.level)
        assertTrue(monastery.all { it.x in 3040..3062 && it.z in 3478..3510 })
        // The rest south of the Wilderness Ditch (z 3521), within map square 48,54 but for Oziach's hut just west of it (x 3069).
        assertTrue((spawns - basement.toSet() - monastery.toSet()).all { it.x in 3064..3135 && it.z in 3456..3520 && it.level == (if (it.type.id == 7457) 1 else 0) && it.source.isNotBlank() })
    }

    @Test
    fun `Barbarian Village has Peksa, Tassie Slipcast, Hunding up the tower, Atlas, Checkal, Litara and Sigurd`() {
        val names = mapOf(
            2872 to "peksa", 1352 to "favour_tassie_slipcast", 3063 to "fai_barbarian_9", 10658 to "bim_atlas", 10645 to "bim_checkal_barb",
            3363 to "sos_barb", 1324 to "canoeing_sigurd", 299 to "gunthor_the_brave", 2837 to "unicorn", 1526 to "0_48_53_freshfish",
        ) + (3055..3072).filter { it != 3063 }.associateWith { "fai_barbarian_$it" } + (3073..3076).associateWith { "fai_barbarian_goblin_armed_${it - 3072}" }
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("barbarian-village.json"), types, "barbarian-village.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(listOf(2872, 1352, 3063, 10658, 10645, 3363, 1324), spawns.take(7).map { it.type.id })
        assertEquals(2, spawns.single { it.type.id == 3063 }.level, "Hunding at the top of the lookout tower")
        assertTrue(spawns.filter { it.type.id != 3063 }.all { it.level == 0 })
        assertTrue(spawns.all { it.x in 3072..3135 && it.z in 3392..3455 && it.source.isNotBlank() }, "all in the village's map square")
        // Step 7: the 17 barbarians who fight, Gunthor, five goblins (two of one type) and two unicorns.
        assertEquals((3055..3072).filter { it != 3063 }, spawns.map { it.type.id }.filter { it in 3055..3072 && it != 3063 })
        assertEquals(1, spawns.count { it.type.id == 299 })
        assertEquals(listOf(3073, 3073, 3074, 3075, 3076), spawns.map { it.type.id }.filter { it in 3073..3076 })
        assertEquals(2, spawns.count { it.type.id == 2837 })
        // Step 8: two Lure/Bait spots on the river, each on a tile of its area.
        val spots = spawns.filter { it.type.id == Fishing.BARBARIAN_SPOT }
        assertEquals(2, spots.size)
        assertTrue(spots.all { spot -> Fishing.AREAS.any { Tile(spot.x, spot.z) in it } && spot.wander == 0 })
    }

    @Test
    fun `The Fractured Archive's lobby has its banker, General Montoya, the Record Keeper and the Wizard apprentice`() {
        val names = mapOf(16704 to "tfa_lobby_banker", 16705 to "general_montoya", 16692 to "tfa_record_keeper", 16693 to "tfa_wizard_apprentice")
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("fractured-archive-lobby.json"), types, "fractured-archive-lobby.json")
        assertEquals(emptyList(), result.unknown)
        assertEquals(listOf(16704, 16705, 16692, 16693), result.spawns.map { it.type.id })
        // All on the lobby's map square, 64,86, and only the apprentice walking about.
        assertTrue(result.spawns.all { it.x shr 6 == 64 && it.z shr 6 == 86 && it.level == 0 && it.source.isNotBlank() })
        assertEquals(listOf(16693), result.spawns.filter { it.wander > 0 }.map { it.type.id })
    }

    @Test
    fun `Draynor has three bankers, its townsfolk, seven pigs, the farmer, two fishing spots, Ned, Aggie, the jail guards, Leela and Morgan`() {
        val names = mapOf(
            1613 to "banker1", 1618 to "banker2", 3106 to "man", 3111 to "woman", 2854 to "rat", 5007 to "imp", 2796 to "pig_1",
            2797 to "pig_2", 2798 to "piglet_1", 2799 to "piglet_2", 2800 to "piglet_3", 3251 to "farmer_farming_on_48_51", Fishing.DRAYNOR_SPOT to "0_48_50_saltfish",
            1260 to "rag_wine_merchant", 500 to "seed_merchant", 8693 to "aprilfoolshorsesalesman", 4280 to "ned", 4284 to "aggie", 4276 to "jailguard",
            4277 to "jailguard_two_patrol", 4278 to "jailguard_three_patrol", 4279 to "jailguard_four_patrol", 4274 to "leela", 3479 to "morgan",
            2109 to "wom_multi", 2114 to "wom_bed_inactive", 2116 to "wom_gossip", 2123 to "wom_bankguard", 5732 to "farming_market_guard", 5730 to "master_farmer_1", 512 to "young_dark_wizard",
        )
        val draynorTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("draynor.json"), draynorTypes, "draynor.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(3, spawns.count { it.type.id in setOf(1613, 1618) })
        assertTrue(spawns.filter { it.type.id in setOf(1613, 1618) }.all { it.x == 3090 && it.z in 3242..3245 && it.wander == 0 })
        assertEquals(7, spawns.count { it.type.id in 2796..2800 })
        // The pig pen, fenced in at x 3075 to 3079, z 3259 to 3261.
        assertTrue(spawns.filter { it.type.id in 2796..2800 }.all { it.x in 3075..3079 && it.z in 3259..3261 })
        assertEquals(1, spawns.single { it.type.id == 3111 }.level, "the woman upstairs in Morgan's house")
        assertTrue(spawns.all { it.x in 3070..3135 && it.z in 3225..3300 && it.source.isNotBlank() })
        // The shore's two Net/Bait spots, each on a tile of Draynor's fishing area.
        val spots = spawns.filter { it.type.id == Fishing.DRAYNOR_SPOT }
        assertEquals(2, spots.size)
        assertTrue(spots.all { spot -> Fishing.AREAS.any { Tile(spot.x, spot.z) in it } && spot.wander == 0 })
        // Ned and Aggie in their houses.
        assertEquals(Tile(3100, 3258), spawns.single { it.type.id == 4280 }.let { Tile(it.x, it.z) })
        assertEquals(Tile(3086, 3259), spawns.single { it.type.id == 4284 }.let { Tile(it.x, it.z) })
        // The jail's four guards, one of each type, and Leela and Morgan.
        assertEquals(listOf(4276, 4277, 4278, 4279), spawns.map { it.type.id }.filter { it in 4276..4279 }.sorted())
        assertEquals(listOf(1, 1), listOf(4274, 3479).map { id -> spawns.count { it.type.id == id } })
        // The Wise Old Man in his house, his bed upstairs, Miss Schism and the bank guard.
        assertEquals(Tile(3088, 3255), spawns.single { it.type.id == 2109 }.let { Tile(it.x, it.z) })
        assertEquals(1, spawns.single { it.type.id == 2114 }.level)
        assertEquals(listOf(1, 1), listOf(2116, 2123).map { id -> spawns.count { it.type.id == id } })
        // The market's two guards.
        assertEquals(listOf(3247, 3253), spawns.filter { it.type.id == 5732 }.map { it.z }.sorted())
        // The Master Farmer in the market.
        assertEquals(Tile(3080, 3250), spawns.single { it.type.id == 5730 }.let { Tile(it.x, it.z) })
        // The two level 7 dark wizards south of the bank.
        assertEquals(listOf(3236, 3238), spawns.filter { it.type.id == 512 }.map { it.z }.sorted())
    }

    @Test
    fun `the Draynor Sewers have the Wiki's seven zombies, four skeletons, ten rats and Ruantun`() {
        val names = mapOf(
            38 to "zombie_unarmed_sewer1", 39 to "zombie_unarmed_sewer2", 40 to "zombie_unarmed_sewer3", 55 to "zombie_armed_sewer1",
            56 to "zombie_armed_sewer2", 57 to "zombie_armed_sewer3", 58 to "zombie_armed_sewer4", 70 to "skeleton_unarmed",
            72 to "skeleton_unarmed3", 77 to "skeleton_armed", 79 to "skeleton_armed3", 2854 to "rat", 3461 to "malak",
        )
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("draynor-sewers.json"), types, "draynor-sewers.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(7, spawns.count { it.type.id in 38..58 })
        assertEquals(4, spawns.count { it.type.id in 70..79 })
        assertEquals(10, spawns.count { it.type.id == 2854 })
        assertEquals(Tile(3112, 9690), spawns.single { it.type.id == 3461 }.let { Tile(it.x, it.z) })
        // All under the village, a dungeon's 6400 tiles north of it.
        assertTrue(spawns.all { it.x in 3077..3127 && it.z in 9640..9700 && it.level == 0 && it.source.isNotBlank() })
    }

    @Test
    fun `the Varrock Sewers have their rats, spiders, zombies, skeletons, ghosts and moss giants`() {
        val names = mapOf(
            2854 to "rat", 2856 to "giantrat", 2862 to "giantrat1", 2863 to "giantrat1_2", 2864 to "giantrat1_3", 3017 to "giantspider1",
            3018 to "giantspider2", 3019 to "spider", 3021 to "deadly_red_spider", 3024 to "scorpion", 39 to "zombie_unarmed_sewer2",
            41 to "zombie_unarmed_sewer4", 44 to "zombie2_c", 55 to "zombie_armed_sewer1", 56 to "zombie_armed_sewer2",
            57 to "zombie_armed_sewer3", 58 to "zombie_armed_sewer4", 74 to "skeleton_unagressive", 75 to "skeleton_unagressive2",
            76 to "skeleton_unagressive3", 77 to "skeleton_armed", 80 to "skeleton_armed4", 85 to "ghost", 89 to "ghost5", 92 to "ghost8",
            2090 to "mossgiant", 2091 to "mossgiant2", 2092 to "mossgiant3", 2093 to "mossgiant4",
        )
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("varrock-sewers.json"), types, "varrock-sewers.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        // As many of each as mejrs's live list has.
        assertEquals(89, spawns.size)
        assertEquals(29, spawns.count { it.type.id == 2854 })
        assertEquals(10, spawns.count { it.type.id in 2090..2093 })
        assertEquals(9, spawns.count { it.type.id == 3021 })
        assertEquals(9, spawns.count { it.type.id == 3019 })
        assertEquals(7, spawns.count { it.type.id in 39..58 })
        assertEquals(9, spawns.count { it.type.id in 74..80 })
        assertEquals(3, spawns.count { it.type.id in 85..92 })
        // All under the city, a dungeon's 6400 tiles north of it.
        assertTrue(spawns.all { it.x in 3150..3300 && it.z in 9840..9920 && it.level == 0 && it.wander > 0 && it.source.isNotBlank() })
    }

    @Test
    fun `Draynor Manor's grounds have the Wiki's 14 undead trees, standing still, and Veronica by the fence`() {
        val result = NpcSpawnFile.parse(file("draynor-manor.json"), manorTypes, "draynor-manor.json")
        assertEquals(emptyList(), result.unknown)
        val trees = result.spawns.filter { it.type.id == 4418 }
        assertEquals(14, trees.size)
        assertTrue(trees.all { it.wander == 0 && it.x in 3100..3145 && it.z in 3333..3366 })
        assertEquals(Tile(3110, 3330), result.spawns.single { it.type.id == 3561 }.let { Tile(it.x, it.z) })
    }

    private val manorTypes = NpcTypes.of(
        NpcType(4418, "null", internalName = "nasty_tree"), NpcType(3561, "Veronica", internalName = "veronica"),
        *listOf(473 to "house_ghost", 474 to "house_ghost2", 505 to "house_ghost3", 506 to "house_ghost4", 507 to "house_ghost5")
            .map { (id, name) -> NpcType(id, "Ghost", internalName = name) }.toTypedArray(),
        NpcType(3565, "Skeleton", internalName = "draynor_skeleton"), NpcType(2854, "Rat", internalName = "rat"),
        NpcType(3562, "Professor Oddenstein", internalName = "professor_oddenstein"), NpcType(4409, "Witch", internalName = "anma_witch"),
        NpcType(4407, "Ava", internalName = "anma_assistant"),
    )

    @Test
    fun `Draynor Manor's inside has five ghosts upstairs, the closet's skeleton, two rats and its three people, all kept indoors`() {
        val result = NpcSpawnFile.parse(file("draynor-manor.json"), manorTypes, "draynor-manor.json")
        assertEquals(emptyList(), result.unknown)
        val inside = result.spawns.filter { it.x in 3093..3127 && it.z in 3353..3375 }
        assertEquals(setOf(473, 474, 505, 506, 507), inside.filter { it.type.name == "Ghost" }.map { it.type.id }.toSet())
        assertTrue(inside.filter { it.type.name == "Ghost" }.all { it.level == 1 })
        assertEquals(Tile(3110, 3367), inside.single { it.type.id == 3565 }.let { Tile(it.x, it.z) })
        assertEquals(2, inside.count { it.type.id == 2854 && it.level == 0 })
        // Step 12b: Oddenstein on the top floor, the witch and Ava on the ground floor.
        assertEquals(
            mapOf(3562 to Triple(3110, 3367, 2), 4409 to Triple(3099, 3370, 0), 4407 to Triple(3093, 3357, 0)),
            inside.filter { it.type.id in setOf(3562, 4409, 4407) }.associate { it.type.id to Triple(it.x, it.z, it.level) },
        )
        assertEquals(11, inside.size)
        assertTrue(inside.all { it.restrict == MoveRestrict.INDOORS && it.source.isNotBlank() })
    }

    @Test
    fun `the church has Father Aereck inside and Count Check by the graveyard, the Prayer tutor and Nigel gone since 242`() {
        val names = mapOf(2812 to "father_aereck", 3223 to "aide_tutor_prayer", 6093 to "deadman_nigel", 7414 to "count_check")
        val churchTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("lumbridge-church.json"), churchTypes, "lumbridge-church.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns.associateBy { it.type.id }
        assertEquals(setOf(2812, 7414), spawns.keys)
        assertEquals(2, result.spawns.size)
        // The church's floor is 3239 to 3247 by 3205 to 3215.
        val father = spawns.getValue(2812)
        assertTrue(father.x in 3239..3247 && father.z in 3205..3215 && father.restrict == MoveRestrict.INDOORS)
        assertEquals(Tile(3238, 3199), spawns.getValue(7414).let { Tile(it.x, it.z) })
        assertTrue(result.spawns.all { it.source.isNotBlank() && it.level == 0 && it.wander == if (it.type.id == 2812) 5 else 2 })
    }

    @Test
    fun `Nigel and Arthur stand by the Grand Exchange since 242, Nigel north-west and Arthur by the south wall`() {
        val nigel = NpcType(16812, "null", internalName = "deadman_nigel_varrock")
        val arthur = NpcType(16811, "Arthur", internalName = "clue_hunter_arthur")
        val spawns = NpcSpawnFile.parse(file("varrock.json"), NpcTypes.of(nigel, arthur), "varrock.json").spawns.associateBy { it.type.id }
        assertEquals(setOf(16812, 16811), spawns.keys)
        assertEquals(Triple(3152, 3512, 2), spawns.getValue(16812).let { Triple(it.x, it.z, it.wander) })
        assertEquals(Triple(3158, 3469, 0), spawns.getValue(16811).let { Triple(it.x, it.z, it.wander) })
    }

    @Test
    fun `every spawn file in the resources is loaded`() {
        val dir = java.nio.file.Path.of(NpcSpawnFile::class.java.getResource("/npc-spawns")!!.toURI())
        val onDisk = java.nio.file.Files.list(dir).use { files -> files.map { it.fileName.toString() }.filter { it.endsWith(".json") }.toList() }
        assertEquals(onDisk.toSet(), NpcSpawnFile.FILES.toSet())
    }

    @Test
    fun `every file names only NPCs it can find in a full cache`() {
        // With no types at all every spawn is skipped by name, not dropped silently.
        for (file in NpcSpawnFile.FILES) {
            val text = NpcSpawnFile::class.java.getResource("/npc-spawns/$file")!!.readText()
            val result = NpcSpawnFile.parse(text, NpcTypes.NONE, file)
            assertTrue(result.spawns.isEmpty())
            assertTrue(result.unknown.isNotEmpty())
        }
    }

    @Test
    fun `optional fields, patrols and ids`() {
        val text = """
            { "about": "test", "spawns": [
              { "npc": 3105, "x": 1, "z": 2, "level": 1, "direction": "nw", "patrol": [[1, 2], [3, 4, 10]], "source": "a" },
              { "npc": "Cow calf", "x": 5, "z": 6, "wander": 2, "source": "b" },
              { "npc": "goblin", "x": 5, "z": 6, "source": "c" }
            ] }
        """
        val result = NpcSpawnFile.parse(text, types)
        val (hans, calf) = result.spawns
        assertEquals(Direction.NORTH_WEST, hans.direction)
        assertEquals(1, hans.level)
        assertEquals(listOf(PatrolStop(1, 2), PatrolStop(3, 4, 10)), hans.patrol)
        assertEquals(0, hans.wander)
        assertEquals(2792, calf.type.id)
        assertEquals(Direction.SOUTH, calf.direction)
        assertEquals(listOf("goblin"), result.unknown)
    }

    @Test
    fun `malformed entries name the file and entry`() {
        fun fails(entry: String): String = assertFailsWith<IllegalArgumentException> {
            NpcSpawnFile.parse("""{ "spawns": [ $entry ] }""", types, "x.json")
        }.message!!
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2 }""").startsWith("x.json: spawn 1: no \"source\""))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "source": " " }""").contains("blank source"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "direction": "up", "source": "a" }""").contains("direction 'up'"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "patrol": [[1]], "source": "a" }""").contains("[x, z]"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "patrol": [[1, 2, 3, 4]], "source": "a" }""").contains("[x, z, wait]"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "patrol": [[1, 2, -1]], "source": "a" }""").contains("wait -1"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "level": 4, "source": "a" }""").contains("level 4"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "restrict": "underground", "source": "a" }""").contains("restrict 'underground'"))
        assertTrue(fails("""{ "npc": "hans", "z": 2, "source": "a" }""").contains("no \"x\""))
    }

    @Test
    fun `Varrock has its 37 guards, five stray dogs, townsfolk, bankers, the Grand Exchange's, shop keepers, the palace's, churches', inns', the Fitzharmons, the museum's people, the Champions' Guild's and its fighters`() {
        val names = mapOf(
            11911 to "fai_varrock_guard02", 11912 to "fai_varrock_guard02_variant01", 11913 to "fai_varrock_guard02_variant02",
            11914 to "fai_varrock_guard02_f", 11915 to "fai_varrock_guard02_f_variant01", 11916 to "fai_varrock_guard02_f_variant02",
            11917 to "fai_varrock_guard_captain02", 3106 to "man", 3107 to "man2", 3108 to "man3", 3014 to "varrock_man1",
            3015 to "varrock_woman1", 3112 to "woman2", 5007 to "imp", 2854 to "rat", 2855 to "rat_indoors", 2902 to "dog_stray",
            2922 to "dog_stray2", 2897 to "banker1_new", 2898 to "banker2_new", 6521 to "deadman_banker_blue_west",
            6522 to "deadman_banker_blue_north", 8666 to "varrock_bank_doorguard", 1633 to "banker1_west",
            6528 to "deadman_banker_grey_west", 1634 to "banker1_east", 3089 to "banker2_east", 2148 to "ge_clerk_1",
            2149 to "ge_clerk_2", 2150 to "ge_clerk_3", 2151 to "ge_clerk_4", 2884 to "swordshop1", 2885 to "swordshop2",
            2882 to "horvik_the_armourer", 2883 to "lowe", 2880 to "zaff", 2815 to "generalshopkeeper2", 2816 to "generalassistant2",
            2886 to "aubury", 534 to "thessalia", 2887 to "tailorp", 8687 to "tea_seller", 5036 to "apothecary", 2881 to "baraek",
            5216 to "qip_soa_newspaperseller", 5215 to "king_roald", 3774 to "myq3_aeonisig_roalds_advisor",
            1390 to "queen_ellamaria", 5083 to "sir_prysin", 6203 to "reldo", 528 to "chaosmonk2",
            2557 to "aluft_gnome_diplomat_varrock", 5085 to "captain_rovin", 11906 to "fai_varrock_guard_trainer02",
            11907 to "fai_varrock_guard_training02", 11908 to "fai_varrock_guard_training02_variant01",
            11909 to "fai_varrock_guard_training02_f", 11910 to "fai_varrock_guard_training02_f_variant01",
            5038 to "father_lawrence", 2900 to "varrock_granny_2", 2582 to "rcu_zammy_mage1_edgeb", 1310 to "jollyboar_bartender",
            1311 to "donkey_bartender", 1312 to "bluemoon_bartender", 2895 to "fai_varrock_bluemoon_chef",
            2896 to "fai_varrock_jollyboar_chef", 5037 to "romeo", 5035 to "juliet", 5039 to "draul_leptoc", 5040 to "phillipa",
            7723 to "gertrude_post", 3501 to "shilop", 3502 to "philop", 3503 to "wilough", 3504 to "kanel", 11868 to "aris",
            5209 to "tramppg", 3255 to "tramp", 16277 to "dr_harlow_vis", 14139 to "jonny_the_beard_2op", 2899 to "varrock_granny_1",
            276 to "pmod_town_crier_varrock",
            4984 to "dimintheis", 5445 to "johnathon_fitzharmon_2ops", 10425 to "daddyshome_daddy",
            5214 to "curator", 1909 to "vm_info_booth_lady", 1910 to "vm_museum_guard", 1912 to "vm_museum_guard3",
            1901 to "vm_art_critic", 1902 to "vm_timeline_historian", 1913 to "vm_school_teacher_male_and_boy",
            1914 to "vm_school_girl_brunette", 1919 to "vm_school_boy_brunnete", 1920 to "vm_school_boy_blonde",
            1921 to "vm_school_girl_blonde", 1922 to "vm_teacher_girl_multi", 6001 to "vm_nathis_apprentice",
            6002 to "vm_natural_historian_north", 6003 to "vm_natural_historian_south", 6004 to "vm_natural_historian_east",
            6005 to "vm_natural_historian_west", 6019 to "vm_advent_01", 6020 to "vm_advent_02",
            6007 to "vm_natural_history_display_case_leech", 6008 to "vm_natural_history_display_case_seaslugs",
            6009 to "vm_natural_history_display_case_snail", 6010 to "vm_natural_history_display_case_monkey",
            6011 to "vm_natural_history_display_case_lizard", 6012 to "vm_natural_history_display_case_penguin",
            6013 to "vm_natural_history_display_case_camel", 6014 to "vm_natural_history_display_case_terror_bird",
            6015 to "vm_natural_history_display_dragon", 6016 to "vm_natural_history_display_case_wyvern",
            6017 to "vm_natural_history_display_case_battle_tortoise", 6018 to "vm_natural_history_display_case_mole",
            814 to "guildmaster", 2871 to "scavvo", 2870 to "valaine",
            513 to "mugger", 5086 to "qip_ds_young_dark_wizard1", 5087 to "qip_ds_young_dark_wizard2", 5088 to "qip_ds_young_dark_wizard3", 5089 to "qip_ds_young_dark_wizard4", 3252 to "thief1", 5217 to "qip_soa_thief_male01", 5219 to "qip_soa_thief_female01",
            5220 to "qip_soa_thief_female02", 2862 to "giantrat1", 2864 to "giantrat1_3", 3262 to "barbarianwoman_varrock",
            3260 to "warrior_woman", 2838 to "brownbear", 516 to "black_knight", 3019 to "spider",
            16812 to "deadman_nigel_varrock", 16811 to "clue_hunter_arthur", 5449 to "ge_expert_herbs",
        )
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("varrock.json"), types, "varrock.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        // The Wiki's Guard page: 13 in the city, 2 on the walls and 22 in the palace (7, 4 and 11 a floor).
        assertEquals(37, spawns.count { it.type.id in 11911..11917 })
        assertEquals(22, spawns.count { it.type.id in 11911..11917 && it.x in 3200..3225 && it.z in 3460..3500 })
        // Five stray dogs, three grey and two brown (Wiki).
        assertEquals(3, spawns.count { it.type.id == 2902 })
        assertEquals(2, spawns.count { it.type.id == 2922 })
        // Six bankers in each bank (the Wiki's West Bank count), standing behind its booths: x 3186 in the west, z 3419 in the east.
        val bankers = spawns.filter { it.type.id in listOf(2897, 2898, 6521, 6522, 8666) }
        assertEquals(6, bankers.count { it.x == 3187 && it.z in 3436..3446 })
        assertEquals(6, bankers.count { it.z == 3418 && it.x in 3251..3256 })
        assertTrue(bankers.all { it.wander == 0 })
        // The Grand Exchange: two bankers behind each side's booths and two clerks behind the north and south ones.
        val exchange = spawns.filter { it.type.id in listOf(1633, 1634, 3089, 6528) + (2148..2151) }
        assertEquals(4, exchange.count { it.type.id in 2148..2151 && it.x in 3164..3165 && it.z in listOf(3488, 3491) })
        assertEquals(4, exchange.count { it.type.id !in 2148..2151 && it.x in listOf(3163, 3166) && it.z in 3489..3490 })
        assertTrue(exchange.all { it.wander == 0 })
        // One of each weapon and armour keeper (the Swordshop has two), kept indoors.
        val keepers = spawns.filter { it.type.id in listOf(2880, 2882, 2883, 2884, 2885) }
        assertEquals(listOf(2880, 2882, 2883, 2884, 2885), keepers.map { it.type.id }.sorted())
        assertTrue(keepers.all { it.restrict == MoveRestrict.INDOORS })
        // Step 5: one of each other shop's keeper (the General Store has two), all indoors but the Tea Seller by his stall.
        val stores = spawns.filter { it.type.id in listOf(534, 2815, 2816, 2886, 2887, 8687) }
        assertEquals(listOf(534, 2815, 2816, 2886, 2887, 8687), stores.map { it.type.id }.sorted())
        assertEquals(listOf(8687), stores.filter { it.restrict != MoveRestrict.INDOORS }.map { it.type.id })
        // Step 5b: the Apothecary in his shop, Baraek and Benny in the square.
        val traders = spawns.filter { it.type.id in listOf(2881, 5036, 5216) }
        assertEquals(listOf(2881, 5036, 5216), traders.map { it.type.id }.sorted())
        assertEquals(listOf(5036), traders.filter { it.restrict == MoveRestrict.INDOORS }.map { it.type.id })
        // Step 6: the palace's people, one each but the five Trainee Guards (the 2022 looks), who stand at the roof's dummies.
        val palace = spawns.filter { it.type.id in listOf(528, 1390, 2557, 3774, 5083, 5085, 5215, 6203, 11906) + (11907..11910) }
        assertEquals(listOf(528, 1390, 2557, 3774, 5083, 5085, 5215, 6203, 11906), palace.filter { it.type.id !in 11907..11910 }.map { it.type.id }.sorted())
        val trainees = palace.filter { it.type.id in 11907..11910 }
        assertEquals(5, trainees.count { it.level == 2 && it.wander == 0 })
        assertTrue(palace.all { it.x in 3200..3230 && it.z in 3460..3500 })
        // Step 7: Father Lawrence, Elsie (upstairs, sitting) and the Mage of Zamorak; a bartender in each inn and two cooks, indoors.
        val churches = spawns.filter { it.type.id in listOf(2582, 2900, 5038) }
        assertEquals(listOf(2582, 2900, 5038), churches.map { it.type.id }.sorted())
        val elsie = churches.single { it.type.id == 2900 }
        assertEquals(1, elsie.level)
        assertEquals(0, elsie.wander)
        val inns = spawns.filter { it.type.id in listOf(1310, 1311, 1312, 2895, 2896) }
        assertEquals(listOf(1310, 1311, 1312, 2895, 2896), inns.map { it.type.id }.sorted())
        assertTrue(inns.all { it.restrict == MoveRestrict.INDOORS })
        // Step 8a: one of each townsperson, the multi-NPCs as the types they show after their quests; Iffie sits still.
        val townsfolk = listOf(276, 2899, 3255, 3501, 3502, 3503, 3504, 5035, 5037, 5039, 5040, 5209, 7723, 11868, 14139, 16277)
        assertEquals(townsfolk, spawns.filter { it.type.id in townsfolk }.map { it.type.id }.sorted())
        val iffie = spawns.single { it.type.id == 2899 }
        assertEquals(0, iffie.wander)
        // Step 8b: Dimintheis, Johnathon upstairs in the Jolly Boar (the type he shows after Family Crest) and Yarlo.
        val fitzharmons = listOf(4984, 5445, 10425)
        assertEquals(fitzharmons, spawns.filter { it.type.id in fitzharmons }.map { it.type.id }.sorted())
        assertEquals(1, spawns.single { it.type.id == 5445 }.level)
        // Step 9a: the museum's curator, its clerk behind the booth and the two guards, who stand still.
        val museum = spawns.filter { it.type.id in listOf(1909, 1910, 1912, 5214) }
        assertEquals(listOf(1909, 1910, 1912, 5214), museum.map { it.type.id }.sorted())
        assertTrue(museum.all { it.x in 3252..3267 && it.z in 3441..3456 })
        val museumStill = museum.filter { it.type.id != 5214 }
        assertTrue(museumStill.all { it.wander == 0 })
        // Step 9b: the museum's other people; the Natural History floor (map square 27,77) has its four historians, two
        // of each, Orlando Smith and the twelve animal displays, which stand still.
        val basement = spawns.filter { it.x in 1728..1791 && it.z in 4928..4991 }
        assertEquals(listOf(6002, 6002, 6003, 6003, 6004, 6004, 6005, 6005), basement.map { it.type.id }.filter { it in 6002..6005 }.sorted())
        assertEquals(1, basement.count { it.type.id == 6001 })
        val displays = basement.filter { it.type.id in 6007..6018 }
        assertEquals((6007..6018).toList(), displays.map { it.type.id }.sorted())
        assertTrue(displays.all { it.wander == 0 })
        val upstairs = spawns.filter { it.type.id in listOf(1901, 1902, 6019) }
        assertEquals(listOf(2, 1, 1), upstairs.sortedBy { it.type.id }.map { it.level })
        // Step 10: the Champions' Guild's Guildmaster inside its door, and Scavvo and Valaine upstairs.
        val guild = spawns.filter { it.type.id in listOf(814, 2870, 2871) }
        assertEquals(listOf(0, 1, 1), guild.sortedBy { it.type.id }.map { it.level })
        assertTrue(guild.all { it.x in 3188..3194 && it.z in 3350..3362 })
        // Step 12: the city's fighters, as many of each as mejrs's live list has.
        val fighters = mapOf(513 to 1, 3252 to 1, 5217 to 2, 5219 to 1, 5220 to 1, 2862 to 2, 2864 to 1, 3262 to 1, 3260 to 2, 2838 to 1, 516 to 1, 3019 to 1)
        assertEquals(fighters, fighters.keys.associateWith { id -> spawns.count { it.type.id == id } })
        // Step 14: the stone circle's dark wizards, six of level 7 and five of level 20, as mejrs's live list has them.
        val wizards = spawns.filter { it.type.id in 5086..5089 }
        assertEquals(mapOf(5086 to 3, 5087 to 3, 5088 to 3, 5089 to 2), wizards.groupingBy { it.type.id }.eachCount())
        assertTrue(wizards.all { it.x in 3220..3235 && it.z in 3360..3376 && it.level == 0 })
        // East of the Edgeville piece's strip (x 3150 and west), south of the Wilderness ditch, but the museum's Natural History
        // floor, and the Champions' Guild and the stone circle, south of the city.
        assertTrue((spawns - basement.toSet() - guild.toSet() - wizards.toSet()).all { it.x >= 3150 && it.z in 3375..3520 && it.source.isNotBlank() })
        // Arthur stands still at the Grand Exchange (his Wiki pin, as he stood still by the Sheared Ram).
        assertTrue((spawns - bankers.toSet() - exchange.toSet() - trainees.toSet() - museumStill.toSet() - displays.toSet() - elsie - iffie - spawns.filter { it.type.id == 16811 }.toSet()).all { it.wander > 0 })
    }

    @Test
    fun `the fishing spots outside Misthalin are Catherby's seven, Musa Point's six, Karamja's five, Piscatoris's five and Port Piscarilius's three, each on a tile of its area or staying put`() {
        val names = mapOf(
            1518 to "0_44_53_saltfish", 1519 to "0_44_53_rarefish", 1520 to "0_44_53_memberfish", 1521 to "0_45_49_saltfish", 1522 to "0_45_49_rarefish",
            4710 to "0_43_47_karambwanji", 4712 to "0_45_48_karambwan", 4713 to "general_karambwan",
            4316 to "swan_fishingspot", 6825 to "piscariliusfish",
        )
        val types = NpcTypes.of(*names.map { (id, name) -> NpcType(id, "Fishing spot", internalName = name) }.toTypedArray())
        val result = NpcSpawnFile.parse(file("fishing-spots.json"), types, "fishing-spots.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        // The OSRS Wiki's spot pages (read 2026-11-10): Catherby has two Cage/Harpoon and three Big Net/Harpoon spots,
        // Musa Point three Cage/Harpoon; mejrs's live list adds two Net/Bait at Catherby and three at Musa Point.
        // Karamja (step FC3): the Wiki's three karambwanji spots in the Holy Lake and two karambwan spots by DKP.
        assertEquals(listOf(2, 2, 3, 3, 3, 3, 1, 1), names.keys.take(8).map { id -> spawns.count { it.type.id == id } })
        // Step FC4: the Wiki's five Piscatoris spots and three Port Piscarilius anglerfish spots.
        assertEquals(listOf(5, 3), listOf(4316, 6825).map { id -> spawns.count { it.type.id == id } })
        for (spot in spawns) {
            val staysPut = spot.type.id in setOf(
                Fishing.KARAMBWANJI_SPOT, Fishing.KARAMBWAN_SPOT, Fishing.KARAMBWAN_SPOT_EAST, Fishing.PISCATORIS_SPOT, Fishing.PISCARILIUS_SPOT,
            )
            assertEquals(!staysPut, Fishing.AREAS.any { Tile(spot.x, spot.z) in it }, "a spot at ${spot.x}, ${spot.z}")
            assertTrue(spot.wander == 0 && spot.source.isNotBlank())
        }
    }

    private companion object {
        val VYRE_NAMES = setOf(
            "alek_constantine", "caninelle_draynar", "carnivus_belamorta", "crimsonette_van_marr", "diphylla_bechstein",
            "draconis_sanguine", "episcula_helsing", "grigor_rasputin", "haemas_lamescus", "lasenna_rasputin",
            "misdrievus_shadum", "mort_nightshade", "mortina_daubenton", "nakasa_jovkai", "natalidae_shadum",
            "noctillion_lugosi", "pipistrelle_draynar", "remus_kaninus", "valentin_rasputin", "valentina_diaemus",
            "vallessia_dracyula", "vallessia_von_pitt", "vampyressa_van_von", "vampyrus_diaemus", "violetta_sanguine",
            "vlad_bechstein", "vlad_diaemus", "von_van_von", "vonnetta_varnis", "vormar_vakan",
        )
    }
}
