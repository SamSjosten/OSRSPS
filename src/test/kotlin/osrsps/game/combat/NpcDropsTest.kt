package osrsps.game.combat

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes

/** The drop file against the Wiki's rates: 10,000 seeded kills of a man land within the odds of the tables. */
class NpcDropsTest {
    private val man = NpcType(3106, "Man", combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val imp = NpcType(5007, "Imp", combatLevel = 2, internalName = "imp")
    private val cow = NpcType(2790, "Cow", combatLevel = 2, internalName = "cow")
    private val rat = NpcType(2854, "Rat", combatLevel = 1, internalName = "rat")
    private val chicken = NpcType(1173, "Chicken", combatLevel = 1, internalName = "chicken")
    private val ram = NpcType(1262, "Ram", combatLevel = 2, internalName = "ramunsheered")
    private val goblin = NpcType(3029, "Goblin", combatLevel = 2, internalName = "goblin_unarmed_melee_1")
    private val spider = NpcType(3017, "Giant spider", combatLevel = 2, internalName = "giantspider1")
    private val giantRat = NpcType(2863, "Giant rat", combatLevel = 6, internalName = "giantrat1_2")
    private val frog = NpcType(8702, "Frog", combatLevel = 5, internalName = "little_frog_nodrops")
    private val bigFrog = NpcType(8701, "Big frog", combatLevel = 10, internalName = "medium_frog_nodrops")
    private val giantFrog = NpcType(8700, "Giant frog", combatLevel = 13, internalName = "giant_frog_nodrops")
    private val armedGoblin = NpcType(3045, "Goblin", combatLevel = 5, internalName = "goblin_armed")
    private val unicorn = NpcType(2837, "Unicorn", combatLevel = 15, internalName = "unicorn")
    private val warrior = NpcType(3292, "Al Kharid warrior", combatLevel = 9, internalName = "al_kharid_warrior")
    private val kharidMan = NpcType(3261, "Man", combatLevel = 2, internalName = "al_kharid_man")
    private val scorpion = NpcType(3024, "Scorpion", combatLevel = 14, internalName = "scorpion")
    private val shantayGuard = NpcType(4643, "Shantay Guard", combatLevel = 22, internalName = "shantay_guard")
    private val varrockGuard = NpcType(11911, "Guard", combatLevel = 21, internalName = "fai_varrock_guard02")
    private val edgevilleGuard = NpcType(3254, "Guard", combatLevel = 21, internalName = "guard1")
    private val monk = NpcType(2579, "Monk", combatLevel = 5, internalName = "monk")
    private val mugger = NpcType(513, "Mugger", combatLevel = 6, internalName = "mugger")
    private val blackKnight = NpcType(516, "Black Knight", combatLevel = 33, internalName = "black_knight")
    private val gangThief = NpcType(5219, "Thief", combatLevel = 16, internalName = "qip_soa_thief_female01")
    private val grizzly = NpcType(2838, "Grizzly bear", combatLevel = 21, internalName = "brownbear")
    private val outlaws = (1..10).map { NpcType(4166 + it, "Outlaw", combatLevel = 32, internalName = "surok_outlaw$it") }
    private val types = NpcTypes.of(
        man, imp, cow, rat, chicken, ram, goblin, spider, giantRat, frog, bigFrog, giantFrog, armedGoblin, unicorn, warrior, kharidMan, scorpion, shantayGuard,
        varrockGuard, edgevilleGuard, monk, mugger, blackKnight, gangThief, grizzly, *outlaws.toTypedArray(),
    )
    private val objTypes = DropTestItems.objTypes()
    private val drops = NpcDrops.load(types, objTypes)

    @Test
    fun `the file reads, naming every item as the cache does, and the tables add up`() {
        // The test's cache knows one man, cow and so on; every other warning would be a wrong item name.
        assertTrue(drops.warnings.all { it.startsWith("no NPC called") }, drops.warnings.toString())
        assertEquals(35, drops.size)
        val def = drops[man]!!
        assertEquals(listOf("Bones"), def.always.map { it.type.name })
        assertEquals(128, def.table!!.slots)
        assertEquals(listOf("Clue scroll (beginner)" to 90, "Clue scroll (easy)" to 128), def.tertiary.map { it.item.type.name to it.denominator })
        assertEquals(listOf("Cowhide", "Raw beef", "Bones"), drops[cow]!!.always.map { it.type.name })
        assertNull(drops[cow]!!.table)
        assertEquals("Fiendish ashes", drops[imp]!!.always.single().type.name)
        assertEquals(listOf("Bones", "Raw chicken"), drops[chicken]!!.always.map { it.type.name })
        assertEquals(listOf("Clue scroll (beginner)" to 300), drops[chicken]!!.tertiary.map { it.item.type.name to it.denominator })
        assertEquals("Bones", drops[ram]!!.always.single().type.name)
        assertNull(drops[ram]!!.table)
        assertEquals("Bones", drops[goblin]!!.always.single().type.name)
        assertEquals(
            listOf("Clue scroll (beginner)" to 64, "Clue scroll (easy)" to 128, "Ensouled goblin head" to 35, "Goblin champion scroll" to 5000),
            drops[goblin]!!.tertiary.map { it.item.type.name to it.denominator },
        )
        // A giant spider leaves no bones, only the odd beginner clue.
        assertEquals(emptyList(), drops[spider]!!.always)
        assertEquals(listOf("Clue scroll (beginner)" to 128), drops[spider]!!.tertiary.map { it.item.type.name to it.denominator })
        // The swamp: a giant rat leaves bones and its meat, frogs bones (big bones for the giant one), a unicorn its horn.
        assertEquals(listOf("Bones", "Raw rat meat"), drops[giantRat]!!.always.map { it.type.name })
        assertNull(drops[giantRat]!!.table)
        assertEquals(listOf("Clue scroll (beginner)" to 90), drops[frog]!!.tertiary.map { it.item.type.name to it.denominator })
        assertEquals(listOf("Clue scroll (beginner)" to 70), drops[bigFrog]!!.tertiary.map { it.item.type.name to it.denominator })
        assertEquals("Big bones", drops[giantFrog]!!.always.single().type.name)
        assertEquals(
            listOf("Clue scroll (beginner)" to 64, "Long bone" to 400, "Curved bone" to 5013),
            drops[giantFrog]!!.tertiary.map { it.item.type.name to it.denominator },
        )
        assertEquals(listOf("Bones", "Unicorn horn"), drops[unicorn]!!.always.map { it.type.name })
        assertEquals(128, drops[armedGoblin]!!.table!!.slots)
        // Al Kharid: its man and its warriors drop as any man; a scorpion leaves only its tertiaries.
        assertEquals(drops[man]!!.table!!.slots, drops[warrior]!!.table!!.slots)
        assertEquals(drops[man]!!.tertiary.map { it.item.type.name }, drops[kharidMan]!!.tertiary.map { it.item.type.name })
        assertEquals(emptyList(), drops[scorpion]!!.always)
        assertNull(drops[scorpion]!!.table)
        assertEquals(
            listOf("Ensouled scorpion head" to 25, "Clue scroll (beginner)" to 100),
            drops[scorpion]!!.tertiary.map { it.item.type.name to it.denominator },
        )
        assertEquals(listOf("Bones"), drops[shantayGuard]!!.always.map { it.type.name })
        // Varrock's and Edgeville's guards share one table, with a medium clue now and then.
        assertEquals(drops[varrockGuard]!!.table, drops[edgevilleGuard]!!.table)
        assertEquals(listOf("Clue scroll (medium)" to 128), drops[edgevilleGuard]!!.tertiary.map { it.item.type.name to it.denominator })
        // The ten outlaws share one table, with no tertiaries.
        assertTrue(outlaws.all { drops[it]!!.table == drops[outlaws[0]]!!.table && drops[it]!!.tertiary.isEmpty() })
        // A monk leaves only bones.
        assertEquals(listOf("Bones"), drops[monk]!!.always.map { it.type.name })
        assertNull(drops[monk]!!.table)
        // A rat drops nothing at all.
        val nothing = drops[rat]!!
        assertTrue(nothing.isEmpty)
        assertTrue(!def.isEmpty && !drops[cow]!!.isEmpty)
        assertEquals(emptyList(), nothing.roll(GameRandom(1)))
    }

    @Test
    fun `ten thousand men drop bones every time and the rest as the Wiki's table says`() {
        val def = drops[man]!!
        val random = GameRandom(5)
        val counts = HashMap<String, Int>()
        var boltsRange = Int.MAX_VALUE to 0
        var tableItems = 0
        repeat(KILLS) {
            val kill = def.roll(random)
            assertEquals("Bones", kill.first().type.name)
            assertEquals(1, kill.first().count)
            val rest = kill.drop(1)
            // A kill gives the bones, at most one table item and the tertiary drops.
            assertTrue(rest.count { !it.type.name.startsWith("Clue") } <= 1, kill.toString())
            if (rest.any { !it.type.name.startsWith("Clue") }) tableItems++
            for (drop in rest) {
                val key = if (drop.type.name == "Coins") "Coins x${drop.count}" else drop.type.name
                counts[key] = (counts[key] ?: 0) + 1
                if (drop.type.name == "Bronze bolts") boltsRange = minOf(boltsRange.first, drop.count) to maxOf(boltsRange.second, drop.count)
            }
        }
        counts["table item"] = tableItems
        // Each line's share of 128 slots, within five standard deviations of a binomial draw.
        fun expect(key: String, weight: Int, of: Int = 128) {
            val p = weight.toDouble() / of
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = counts[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()} (sigma ${sigma.toInt()})")
        }
        expect("Coins x3", 38)
        expect("Bronze bolts", 22)
        expect("Energy potion(3)", 9)
        expect("Fishing bait", 5)
        expect("Coins x15", 4)
        expect("Bronze arrow", 3)
        expect("Bronze med helm", 2)
        expect("Iron dagger", 1)
        expect("Cabbage", 1)
        // The herb table: 23/128 of kills give a herb, guam 32/128 of those.
        val herbs = counts.filterKeys { it.startsWith("Grimy") }.values.sum()
        counts["any herb"] = herbs
        expect("any herb", 23)
        expect("Grimy guam leaf", 23 * 32, 128 * 128)
        expect("Grimy ranarr weed", 23 * 11, 128 * 128)
        // Nothing: no drop beyond the bones, 8/128.
        expect("Clue scroll (beginner)", 1, 90)
        expect("Clue scroll (easy)", 1, 128)
        assertEquals(2 to 12, boltsRange)
        // Nothing 8/128 of the time: a table item on the other 120.
        expect("table item", 120)
    }

    @Test
    fun `a table whose lines don't fill its slots, a bad chance, or an unknown item is refused`() {
        val good = """{"npcs": [{"npcs": ["man"], "table": {"slots": 128, "lines": [{"item": 526, "name": "Bones", "weight": 128}]}, "source": "test"}]}"""
        assertEquals(1, NpcDrops.parse(good, types, objTypes).size)
        val short = good.replace("\"weight\": 128", "\"weight\": 100")
        assertTrue(assertFailsWith<IllegalArgumentException> { NpcDrops.parse(short, types, objTypes) }.message!!.contains("100, not the table's 128"))
        val badChance = """{"npcs": [{"npcs": ["man"], "tertiary": [{"item": 526, "name": "Bones", "chance": "3/128"}], "source": "test"}]}"""
        assertTrue(assertFailsWith<IllegalArgumentException> { NpcDrops.parse(badChance, types, objTypes) }.message!!.contains("\"1/N\""))
        // An item the cache lacks leaves its NPCs without drops rather than stopping the server (a test cache is small).
        val noItem = good.replace("526", "99999")
        val without = NpcDrops.parse(noItem, types, objTypes)
        assertEquals(0, without.size)
        assertEquals(listOf("man drop nothing: line 1: no item 99999 (Bones) in the cache"), without.warnings)
        val noHerb = NpcDrops.parse(
            """{"tables": {"herbs": {"slots": 1, "lines": [{"item": 99999, "name": "Herb", "weight": 1}]}},
               "npcs": [{"npcs": ["man"], "table": {"slots": 1, "lines": [{"table": "herbs", "weight": 1}]}, "source": "test"},
                        {"npcs": ["cow"], "always": [{"item": 526, "name": "Bones"}], "source": "test"}]}""",
            types, objTypes,
        )
        assertEquals(1, noHerb.size)
        assertEquals(listOf("table herbs: line 1: no item 99999 (Herb) in the cache", "man drop nothing: line 1: table herbs has items the cache lacks"), noHerb.warnings)
        val noTable = """{"npcs": [{"npcs": ["man"], "table": {"slots": 1, "lines": [{"table": "gems", "weight": 1}]}, "source": "test"}]}"""
        assertTrue(assertFailsWith<IllegalArgumentException> { NpcDrops.parse(noTable, types, objTypes) }.message!!.contains("no table called gems"))
        // A wrong name is only warned about: the id rules.
        val wrongName = good.replace("\"Bones\"", "\"Big bones\"")
        assertEquals(listOf("526 is Bones, not Big bones"), NpcDrops.parse(wrongName, types, objTypes).warnings)
    }

    @Test
    fun `ten thousand chickens drop bones and raw chicken every time, and 5 feathers half the time, 15 a quarter`() {
        val def = drops[chicken]!!
        val random = GameRandom(7)
        val feathers = HashMap<Int, Int>()
        repeat(KILLS) {
            val kill = def.roll(random)
            assertEquals(listOf("Bones", "Raw chicken"), kill.take(2).map { it.type.name })
            for (drop in kill.drop(2)) if (drop.type.name == "Feather") feathers[drop.count] = (feathers[drop.count] ?: 0) + 1
        }
        assertEquals(setOf(5, 15), feathers.keys)
        for ((count, weight) in listOf(5 to 64, 15 to 32)) {
            val p = weight / 128.0
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            assertTrue(abs(feathers[count]!! - mean) <= 5 * sigma, "$count feathers: ${feathers[count]} of $KILLS")
        }
    }

    @Test
    fun `ten thousand goblins drop bones every time and the rest as the Wiki's drop table 1 says`() {
        val def = drops[goblin]!!
        val random = GameRandom(11)
        val counts = HashMap<String, Int>()
        val tertiary = setOf("Clue scroll (beginner)", "Clue scroll (easy)", "Ensouled goblin head", "Goblin champion scroll")
        var nothing = 0
        repeat(KILLS) {
            val kill = def.roll(random)
            assertEquals("Bones", kill.first().type.name)
            val table = kill.drop(1).filter { it.type.name !in tertiary }
            assertTrue(table.size <= 1, kill.toString())
            if (table.isEmpty()) nothing++
            for (drop in table) {
                val key = if (drop.type.name == "Coins") "Coins x${drop.count}" else "${drop.type.name} x${drop.count}"
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        counts["nothing"] = nothing
        fun expect(key: String, weight: Int) {
            val p = weight / 128.0
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = counts[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        expect("nothing", 38)
        expect("Coins x15", 28)
        expect("Hammer x1", 15)
        expect("Water rune x6", 6)
        expect("Body rune x7", 5)
        expect("Goblin mail x1", 5)
        expect("Bronze spear x1", 4)
        expect("Bronze bolts x8", 3)
        expect("Coins x20", 2)
        expect("Goblin book x1", 2)
        expect("Brass necklace x1", 1)
    }

    @Test
    fun `ten thousand level 5 goblins drop bones every time and the rest as the Wiki's drop table 2 says`() {
        val def = drops[armedGoblin]!!
        val random = GameRandom(12)
        val counts = HashMap<String, Int>()
        val tertiary = setOf("Clue scroll (beginner)", "Clue scroll (easy)", "Ensouled goblin head", "Goblin champion scroll")
        var nothing = 0
        var herbs = 0
        repeat(KILLS) {
            val kill = def.roll(random)
            assertEquals("Bones", kill.first().type.name)
            val table = kill.drop(1).filter { it.type.name !in tertiary }
            assertTrue(table.size <= 1, kill.toString())
            if (table.isEmpty()) nothing++
            for (drop in table) {
                if (drop.type.name.startsWith("Grimy")) herbs++
                val key = if (drop.type.name == "Coins") "Coins x${drop.count}" else "${drop.type.name} x${drop.count}"
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        counts["nothing"] = nothing
        counts["herbs"] = herbs
        fun expect(key: String, weight: Int) {
            val p = weight / 128.0
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = counts[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        expect("Coins x8", 34)
        expect("Energy potion(2) x1", 13)
        expect("Goblin mail x1", 10)
        expect("Bronze spear x1", 9)
        expect("Hammer x1", 9)
        expect("nothing", 8)
        expect("Coins x5", 8)
        expect("Coins x16", 7)
        expect("Bronze arrow x7", 3)
        expect("Bronze javelin x5", 2)
        expect("herbs", 2)
        expect("Tin ore x1", 1)
    }

    @Test
    fun `ten thousand guards drop bones every time, coins most of all, and seeds from the general seed table`() {
        val def = drops[varrockGuard]!!
        val random = GameRandom(21)
        val counts = HashMap<String, Int>()
        var nothing = 0
        repeat(KILLS) {
            val kill = def.roll(random)
            assertEquals("Bones", kill.first().type.name)
            val table = kill.drop(1).filter { it.type.name != "Clue scroll (medium)" }
            assertTrue(table.size <= 1, kill.toString())
            if (table.isEmpty()) nothing++
            for (drop in table) {
                val key = "${drop.type.name} x${drop.count}"
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        counts["nothing"] = nothing
        counts["any seed"] = counts.filterKeys { "seed" in it }.values.sum()
        counts["Iron bolts"] = counts.filterKeys { it.startsWith("Iron bolts") }.values.sum()
        fun expect(key: String, weight: Int, of: Int = 128) {
            val p = weight.toDouble() / of
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = counts[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        expect("Coins x1", 19)
        expect("Coins x7", 16)
        expect("Iron bolts", 10)
        expect("Coins x12", 9)
        expect("nothing", 8)
        expect("Iron dagger x1", 6)
        expect("Steel arrow x1", 4)
        expect("Body talisman x1", 3)
        expect("Nature rune x1", 1)
        // The seed roll, 18/128, gives 4 potato seeds 368/1008 of the time (the Wiki's 1/19) and so on down.
        expect("any seed", 18)
        expect("Potato seed x4", 18 * 368, 128 * 1008)
        expect("Onion seed x4", 18 * 276, 128 * 1008)
        expect("Tomato seed x3", 18 * 92, 128 * 1008)
        assertEquals(setOf(4, 3, 2), counts.filterKeys { "seed" in it && it != "any seed" }.keys.map { it.substringAfterLast("x").toInt() }.toSet())
        // No free-to-play 5 coins.
        assertNull(counts["Coins x5"])
    }

    @Test
    fun `Varrock's muggers, thieves, Black Knight and grizzly bear drop as the Wiki's tables say`() {
        fun counts(def: NpcDropsDef, seed: Long): Map<String, Int> {
            val random = GameRandom(seed)
            val counts = HashMap<String, Int>()
            repeat(KILLS) {
                val kill = def.roll(random)
                assertEquals("Bones", kill.first().type.name)
                for (drop in kill.drop(1)) {
                    val key = "${drop.type.name} x${drop.count}"
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
            counts["any herb"] = counts.filterKeys { it.startsWith("Grimy") }.values.sum()
            return counts
        }
        fun expect(counts: Map<String, Int>, key: String, weight: Int) {
            val p = weight.toDouble() / 128
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = counts[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        val mugger = counts(drops[mugger]!!, 41)
        expect(mugger, "Rope x1", 40)
        expect(mugger, "any herb", 13)
        expect(mugger, "Coins x5", 12)
        expect(mugger, "Fishing bait x1", 6)
        assertNull(mugger["Coins x10"])
        assertEquals(listOf("Clue scroll (beginner)" to 80, "Clue scroll (easy)" to 128), drops[this.mugger]!!.tertiary.map { it.item.type.name to it.denominator })
        val thief = counts(drops[gangThief]!!, 42)
        expect(thief, "Coins x3", 38)
        expect(thief, "any herb", 23)
        expect(thief, "Earth talisman x1", 2)
        assertNull(thief["Coins x10"])
        val knight = counts(drops[blackKnight]!!, 43)
        expect(knight, "Coins x35", 21)
        expect(knight, "Coins x1", 14)
        expect(knight, "Steel bar x1", 6)
        expect(knight, "Iron sword x1", 4)
        assertNull(knight["Coins x5"])
        assertEquals(listOf("Bones", "Bear fur", "Raw bear meat"), drops[grizzly]!!.always.map { it.type.name })
        assertNull(drops[grizzly]!!.table)
        assertEquals(listOf("Ensouled bear head" to 25, "Clue scroll (beginner)" to 90), drops[grizzly]!!.tertiary.map { it.item.type.name to it.denominator })
    }

    @Test
    fun `ten thousand outlaws drop bones every time, rope most of all, and herbs from the herb table`() {
        val def = drops[outlaws[4]]!!
        val random = GameRandom(32)
        val counts = HashMap<String, Int>()
        var nothing = 0
        repeat(KILLS) {
            val kill = def.roll(random)
            assertEquals("Bones", kill.first().type.name)
            val table = kill.drop(1)
            assertTrue(table.size <= 1, kill.toString())
            if (table.isEmpty()) nothing++
            for (drop in table) {
                val key = "${drop.type.name} x${drop.count}"
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        counts["nothing"] = nothing
        counts["any herb"] = counts.filterKeys { it.startsWith("Grimy") }.values.sum()
        fun expect(key: String, weight: Int, of: Int = 128) {
            val p = weight.toDouble() / of
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = counts[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        expect("Rope x1", 46)
        expect("any herb", 32)
        expect("Fishing bait x1", 15)
        expect("Coins x5", 12)
        expect("Cabbage x1", 6)
        expect("Coins x15", 4)
        expect("Mind rune x9", 2)
        expect("Bronze med helm x1", 2)
        expect("Knife x1", 1)
        expect("nothing", 1)
        // The herb roll gives a guam leaf a quarter of the time: the Wiki's 1/16.
        expect("Grimy guam leaf x1", 32 * 32, 128 * 128)
        // No free-to-play 10 coins.
        assertNull(counts["Coins x10"])
    }

    @Test
    fun `Draynor Manor's ghosts drop only a beginner clue now and then, and its skeleton nothing`() {
        val names = listOf("house_ghost", "house_ghost5", "draynor_skeleton")
        val manorTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(2100 + i, name, internalName = name) }.toTypedArray())
        val manor = NpcDrops.load(manorTypes, objTypes)
        assertTrue(manor.warnings.all { it.startsWith("no NPC called") }, manor.warnings.toString())
        for (name in listOf("house_ghost", "house_ghost5")) {
            val def = manor[manorTypes.find(name)!!]!!
            assertEquals(emptyList(), def.always, name)
            assertEquals(null, def.table, name)
            assertEquals(listOf("Clue scroll (beginner)" to 90), def.tertiary.map { it.item.type.name to it.denominator }, name)
        }
        assertEquals(null, manor[manorTypes.find("draynor_skeleton")!!])
    }

    @Test
    fun `the Draynor Sewers' zombies and skeletons drop bones every time and the rest as the Wiki's tables say`() {
        val names = listOf("zombie_unarmed_sewer1", "zombie_armed_sewer4", "skeleton_unarmed3", "skeleton_armed")
        val sewerTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(2000 + i, name, internalName = name) }.toTypedArray())
        val sewer = NpcDrops.load(sewerTypes, objTypes)
        assertTrue(sewer.warnings.all { it.startsWith("no NPC called") }, sewer.warnings.toString())
        for (name in names) assertEquals(128, sewer[sewerTypes.find(name)!!]!!.table!!.slots, name)
        // Skeletons leave a beginner clue now and then; zombies only their table.
        assertEquals(listOf("Clue scroll (beginner)" to 100), sewer[sewerTypes.find("skeleton_armed")!!]!!.tertiary.map { it.item.type.name to it.denominator })
        assertEquals(emptyList(), sewer[sewerTypes.find("zombie_armed_sewer4")!!]!!.tertiary)
        fun kills(name: String, seed: Long): Map<String, Int> {
            val def = sewer[sewerTypes.find(name)!!]!!
            val random = GameRandom(seed)
            val counts = HashMap<String, Int>()
            repeat(KILLS) {
                val kill = def.roll(random)
                assertEquals("Bones", kill.first().type.name)
                for (drop in kill.drop(1).filter { !it.type.name.startsWith("Clue scroll") }) {
                    val key = "${drop.type.name} x${drop.count}"
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
            return counts
        }
        fun Map<String, Int>.expect(key: String, weight: Int, of: Int = 128) {
            val p = weight.toDouble() / of
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = this[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        val zombie = kills("zombie_armed_sewer4", 31)
        zombie.expect("Fishing bait x7", 26)
        zombie.expect("Coins x18", 21)
        zombie.expect("Coins x10", 10)
        zombie.expect("Iron mace x1", 3)
        // The gem table, 1/128, gives an uncut sapphire 32 of its 128 slots.
        zombie.expect("Uncut sapphire x1", 32, 128 * 128)
        // Underground, the shared talisman row is the chaos talisman.
        assertNull(zombie["Nature talisman x1"])
        val skeleton = kills("skeleton_armed", 32)
        skeleton.expect("Coins x5", 25)
        skeleton.expect("Coins x10", 24)
        skeleton.expect("Iron med helm x1", 6)
        skeleton.expect("Bronze bar x1", 5)
        // The unarmed skeleton's free-to-play 10 coins are left out.
        assertNull(kills("skeleton_unarmed3", 33)["Coins x10"])
        kills("zombie_unarmed_sewer1", 34).expect("Fishing bait x5", 37)
    }

    @Test
    fun `the Edgeville Dungeon's zombies, skeletons and hobgoblins drop as the Wiki's tables say`() {
        val names = listOf(
            "zombie2", "zombie2_b", "zombie2_c", "skeleton_unarmed2", "skeleton_unarmed4", "skeleton_unagressive", "skeleton_unagressive3",
            "skeleton_armed2", "skeleton_armed4", "skeleton_armed5", "hobgoblin_unarmed", "hobgoblin_armed", "giant", "giant6",
        )
        val dungeonTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(2200 + i, name, internalName = name) }.toTypedArray())
        val dungeon = NpcDrops.load(dungeonTypes, objTypes)
        assertTrue(dungeon.warnings.all { it.startsWith("no NPC called") }, dungeon.warnings.toString())
        fun def(name: String) = dungeon[dungeonTypes.find(name)!!]!!
        // The level 18 zombies drop their bones and nothing else.
        for (name in listOf("zombie2", "zombie2_b", "zombie2_c")) {
            assertEquals(listOf("Bones"), def(name).always.map { it.type.name }, name)
            assertNull(def(name).table, name)
        }
        for (name in names.drop(3).filter { !it.startsWith("giant") }) assertEquals(128, def(name).table!!.slots, name)
        assertEquals(listOf("Clue scroll (beginner)" to 70), def("hobgoblin_armed").tertiary.map { it.item.type.name to it.denominator })
        fun kills(name: String, seed: Long): Map<String, Int> {
            val random = GameRandom(seed)
            val counts = HashMap<String, Int>()
            repeat(KILLS) {
                val kill = def(name).roll(random)
                assertEquals("Bones", kill.first().type.name)
                for (drop in kill.drop(1).filter { !it.type.name.startsWith("Clue scroll") }) {
                    val key = "${drop.type.name} x${drop.count}"
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
            return counts
        }
        fun Map<String, Int>.expect(key: String, weight: Int, of: Int = 128) {
            val p = weight.toDouble() / of
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = this[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        val skeleton = kills("skeleton_unagressive", 41)
        skeleton.expect("Air rune x12", 2)
        skeleton.expect("Coins x2", 18)
        skeleton.expect("Iron dagger x1", 6)
        assertNull(skeleton["Coins x10"])
        val hobgoblin = kills("hobgoblin_armed", 42)
        hobgoblin.expect("Limpwurt root x1", 22)
        hobgoblin.expect("Coins x15", 16)
        hobgoblin.expect("Law rune x2", 3)
        hobgoblin.expect("Goblin mail x1", 2)
        kills("skeleton_armed4", 43).expect("Iron med helm x1", 6)
        // Step 11's hill giants: big bones every time; the giant key is a pre-roll that takes the main table's place
        // (the Wiki's Hill Giant, Pre-roll), and a beginner clue rolls on its own.
        assertEquals(listOf("Big bones"), def("giant6").always.map { it.type.name })
        assertEquals(listOf("Giant key" to 128), def("giant").preroll.map { it.item.type.name to it.denominator })
        assertEquals(listOf("Clue scroll (beginner)" to 50), def("giant").tertiary.map { it.item.type.name to it.denominator })
        assertEquals(127, def("giant").table!!.slots)
        val giant = HashMap<String, Int>()
        val random = GameRandom(44)
        repeat(KILLS) {
            val kill = def("giant").roll(random)
            assertEquals("Big bones", kill.first().type.name)
            val rest = kill.drop(1).filter { !it.type.name.startsWith("Clue scroll") }
            if (rest.any { it.type.name == "Giant key" }) assertEquals(listOf("Giant key"), rest.map { it.type.name }, "a key takes the main drop's place")
            for (drop in rest) giant["${drop.type.name} x${drop.count}"] = (giant["${drop.type.name} x${drop.count}"] ?: 0) + 1
        }
        giant.expect("Coins x38", 14)
        giant.expect("Limpwurt root x1", 11)
        giant.expect("Iron full helm x1", 5)
        giant.expect("Giant key x1", 1)
        assertNull(giant["Coins x5"])
    }

    @Test
    fun `the Vault of War's goblins, minotaurs, wolves and rats drop as the Wiki's tables say`() {
        val names = listOf(
            "sos_war_goblin_armed", "sos_war_goblin_helmet", "sos_war_goblin_green_soldier_1", "sos_war_goblin_armed2",
            "sos_war_goblin_helmet2", "sos_war_goblin_green_soldier_2", "sos_war_minotaur", "sos_war_minotaur3",
            "sos_war_wolf", "sos_war_wolf2", "sos_war_rat",
        )
        val vaultTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(2300 + i, name, internalName = name) }.toTypedArray())
        val vault = NpcDrops.load(vaultTypes, objTypes)
        assertTrue(vault.warnings.all { it.startsWith("no NPC called") }, vault.warnings.toString())
        fun def(name: String) = vault[vaultTypes.find(name)!!]!!
        for (name in names.take(6)) assertEquals(128, def(name).table!!.slots, name)
        assertEquals(101, def("sos_war_minotaur").table!!.slots)
        assertEquals(listOf("Wolf bones"), def("sos_war_wolf2").always.map { it.type.name })
        assertNull(def("sos_war_wolf").table)
        assertEquals(listOf("Clue scroll (beginner)" to 128), def("sos_war_wolf").tertiary.map { it.item.type.name to it.denominator })
        assertEquals(listOf("Bones"), def("sos_war_rat").always.map { it.type.name })
        assertNull(def("sos_war_rat").table)
        assertEquals(
            listOf("Ensouled minotaur head" to 50, "Clue scroll (beginner)" to 60, "Clue scroll (easy)" to 101),
            def("sos_war_minotaur3").tertiary.map { it.item.type.name to it.denominator },
        )
        fun kills(name: String, seed: Long): Map<String, Int> {
            val random = GameRandom(seed)
            val counts = HashMap<String, Int>()
            repeat(KILLS) {
                val kill = def(name).roll(random)
                assertEquals("Bones", kill.first().type.name)
                for (drop in kill.drop(1).filter { !it.type.name.startsWith("Clue scroll") && !it.type.name.startsWith("Ensouled") }) {
                    val key = "${drop.type.name} x${drop.count}"
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
            return counts
        }
        fun Map<String, Int>.expect(key: String, weight: Int, of: Int = 128) {
            val p = weight.toDouble() / of
            val mean = KILLS * p
            val sigma = sqrt(KILLS * p * (1 - p))
            val got = this[key] ?: 0
            assertTrue(abs(got - mean) <= 5 * sigma + 1, "$key: $got of $KILLS, expected about ${mean.toInt()}")
        }
        val goblin = kills("sos_war_goblin_helmet2", 45)
        goblin.expect("Coins x8", 34)
        goblin.expect("Energy potion(2) x1", 13)
        goblin.expect("Goblin mail x1", 10)
        assertNull(goblin["Coins x10"])
        val minotaur = kills("sos_war_minotaur", 46)
        minotaur.expect("Coins x7", 20, 101)
        minotaur.expect("Right skull half x1", 3, 101)
        minotaur.expect("Pure essence (noted) x15", 5, 101)
        assertEquals(10, (5..14).sumOf { minotaur["Iron arrow x$it"]?.let { 1 } ?: 0 }, "iron arrows come 5 to 14")
        assertNull(minotaur["Rune essence (noted) x5"])
    }

    companion object {
        private const val KILLS = 10_000
    }
}
