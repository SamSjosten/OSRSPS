package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.game.NpcType
import osrsps.game.NpcTypes
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
        NpcType(3216, "Melee combat tutor", internalName = "aide_tutor_melee"),
        NpcType(3217, "Ranged combat tutor", internalName = "aide_tutor_ranging"),
        NpcType(3218, "Magic combat tutor", internalName = "aide_tutor_magic"),
        // The 241 cache's ids (dump.npc).
        NpcType(2813, "Shop keeper", internalName = "generalshopkeeper1"),
        NpcType(2814, "Shop assistant", internalName = "generalassistant1"),
        NpcType(10619, "Bob", internalName = "bob"),
        NpcType(7546, "Bartender", internalName = "ram_bartender"),
        NpcType(8665, "Arthur the Clue Hunter", internalName = "aide_tutor_clues"),
        NpcType(3219, "Cooking tutor", internalName = "aide_tutor_cooking"),
        NpcType(3226, "Woodsman tutor", internalName = "aide_tutor_woodsman"),
        NpcType(3224, "Smithing apprentice", internalName = "aide_tutor_smithing_apprentice"),
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
        // All but the Bartender, at his bar, Arthur, who stands facing north, and the fishing spots wander.
        val still = setOf(7546, 8665, Fishing.SWAMP_SPOT, Fishing.RIVER_SPOT)
        assertTrue(spawns.all { it.source.isNotBlank() && (it.wander > 0 || it.type.id in still) })
    }

    @Test
    fun `the town centre's NPCs stand west of the river, Abigaila upstairs in the General Store`() {
        val spawns = NpcSpawnFile.parse(file("lumbridge.json"), types).spawns
        val town = spawns.filter { it.type.id in setOf(7546, 8665, 3219, 3226, 3224, 311, 8368) }
        assertEquals(7, town.size)
        assertTrue(town.all { it.x < 3240 && it.z in 3190..3260 })
        assertEquals(1, town.single { it.type.id == 8368 }.level)
        assertEquals(2, spawns.count { it.type.id == 921 })
        assertEquals(2, spawns.count { it.type.id == 6816 })
    }

    @Test
    fun `the three combat tutors stand together on the hill north of the castle`() {
        val result = NpcSpawnFile.parse(file("lumbridge.json"), types)
        assertEquals(emptyList(), result.unknown)
        val tutors = result.spawns.filter { it.type.id in 3216..3218 }
        assertEquals(listOf(3216, 3217, 3218), tutors.map { it.type.id }.sorted())
        assertTrue(tutors.all { it.x in 3217..3219 && it.z in 3238..3239 && it.level == 0 })
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
            NpcType(3220, "Crafting tutor", internalName = "aide_tutor_crafting"),
            NpcType(6520, "null", internalName = "deadman_banker_blue_south"),
            NpcType(3227, "Banker tutor", internalName = "aide_tutor_banker"),
            NpcType(306, "Lumbridge Guide", internalName = "lumbridge_guide"),
            NpcType(119, "null", internalName = "cws_doomsayer"),
            NpcType(5523, "Hatius Cosaintus", internalName = "hatius_lumbridge_diary"),
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
        // Everyone but Hans wanders or stands still on purpose.
        assertTrue(spawns.filter { it.patrol.isEmpty() }.all { it.wander == 0 || it.wander == 5 })
    }

    @Test
    fun `the farms file has Fred's sheep, rams, lambs and 33 chickens north-west of the castle, Gillie and Millie`() {
        val names = mapOf(
            732 to "fred_the_farmer", 2693 to "sheepunsheered", 2699 to "sheepunsheered3", 2694 to "sheepunsheeredg", 2695 to "sheepunsheeredw",
            2786 to "sheepunsheered3g", 2787 to "sheepunsheered3w", 1262 to "ramunsheered", 1263 to "ramunsheered2", 1264 to "ramunsheered3",
            1176 to "lamb1", 1177 to "lamb2", 1301 to "sheepsheered2w", 1308 to "sheepsheered3g", 1173 to "chicken", 1174 to "chicken_brown",
            4628 to "gillie_the_milkmaid", 4627 to "millie_the_miller",
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
        val farm = spawns.filter { !it.type.internalName!!.startsWith("sheepsheered") && it.type.id !in setOf(4627, 4628) }
        assertTrue(farm.all { it.x in 3165..3215 && it.z in 3255..3310 && it.level == 0 })
        assertTrue(spawns.filter { it.type.internalName!!.startsWith("sheepsheered") }.all { it.x < 3200 && it.z < 3225 })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == 5 })
        // Gillie in the Groats' dairy cow pen east of the river, Millie inside Mill Lane Mill.
        val gillie = spawns.single { it.type.id == 4628 }
        assertTrue(gillie.x in 3250..3258 && gillie.z in 3268..3278)
        val millie = spawns.single { it.type.id == 4627 }
        assertTrue(millie.x in 3163..3170 && millie.z in 3303..3310 && millie.restrict == MoveRestrict.INDOORS)
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
    fun `the swamp has its 23 giant rats, 82 frogs, the level 5 goblin, the unicorn, its talking NPCs and tutors, and rats west of the castle`() {
        val names = mapOf(
            2856 to "giantrat", 2859 to "giantrat_grey", 2860 to "giantrat_grey2", 2861 to "giantrat_grey3", 2863 to "giantrat1_2",
            2864 to "giantrat1_3", 2854 to "rat", 8700 to "giant_frog_nodrops", 8701 to "medium_frog_nodrops", 8702 to "little_frog_nodrops",
            3045 to "goblin_armed", 2837 to "unicorn", 238 to "butterfly_viceroy", 923 to "father_urhney", 1157 to "archeradventurerpg",
            1158 to "warrioradventurerpg", 1159 to "monkadventurerpg", 1160 to "wizardadventuterpg", 1896 to "swamp_candle_guy",
            3222 to "aide_tutor_mining", 3221 to "aide_tutor_fishing",
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
        assertEquals(Tile(3227, 3146), spawns.single { it.type.id == 3222 }.let { Tile(it.x, it.z) })
        assertEquals(Tile(3246, 3157), spawns.single { it.type.id == 3221 }.let { Tile(it.x, it.z) })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander == if (it.type.id in setOf(1896, 3221, 3222)) 2 else 5 })
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
    fun `Draynor has three bankers, its townsfolk, seven pigs and the farmer`() {
        val names = mapOf(
            1613 to "banker1", 1618 to "banker2", 3106 to "man", 3111 to "woman", 2854 to "rat", 5007 to "imp", 2796 to "pig_1",
            2797 to "pig_2", 2798 to "piglet_1", 2799 to "piglet_2", 2800 to "piglet_3", 3251 to "farmer_farming_on_48_51",
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
}
