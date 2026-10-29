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
    private val types = NpcTypes.of(man, imp, cow, rat, chicken, ram)
    private val objTypes = DropTestItems.objTypes()
    private val drops = NpcDrops.load(types, objTypes)

    @Test
    fun `the file reads, naming every item as the cache does, and the tables add up`() {
        // The test's cache knows one man, cow and so on; every other warning would be a wrong item name.
        assertTrue(drops.warnings.all { it.startsWith("no NPC called") }, drops.warnings.toString())
        assertEquals(6, drops.size)
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

    companion object {
        private const val KILLS = 10_000
    }
}
