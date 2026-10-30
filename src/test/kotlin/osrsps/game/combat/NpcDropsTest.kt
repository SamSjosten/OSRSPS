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
    private val types = NpcTypes.of(
        man, imp, cow, rat, chicken, ram, goblin, spider, giantRat, frog, bigFrog, giantFrog, armedGoblin, unicorn, warrior, kharidMan, scorpion, shantayGuard,
    )
    private val objTypes = DropTestItems.objTypes()
    private val drops = NpcDrops.load(types, objTypes)

    @Test
    fun `the file reads, naming every item as the cache does, and the tables add up`() {
        // The test's cache knows one man, cow and so on; every other warning would be a wrong item name.
        assertTrue(drops.warnings.all { it.startsWith("no NPC called") }, drops.warnings.toString())
        assertEquals(18, drops.size)
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

    companion object {
        private const val KILLS = 10_000
    }
}
