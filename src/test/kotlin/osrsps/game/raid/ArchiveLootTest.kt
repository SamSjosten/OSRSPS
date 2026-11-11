package osrsps.game.raid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.combat.DropItem
import osrsps.game.combat.DropSlot
import osrsps.game.combat.DropTable

/**
 * The reward vault's loot (raid step 29a): the file as the Wiki's chest page has it, and the rolls on it. The rolls are
 * drawn from a seeded [GameRandom], so each count here is the same every run.
 */
class ArchiveLootTest {
    private val text = assertNotNull(ArchiveLoot::class.java.getResource(ArchiveLoot.RESOURCE), "the loot file").readText()

    /** Every item the file names, as a cache that has them all would: an entry is any object with an item and a name. */
    private fun named(element: JsonElement, into: MutableMap<Int, String>) {
        when (element) {
            is JsonObject -> {
                val id = element["item"]
                val name = element["name"]
                if (id != null && name != null) into[id.jsonPrimitive.int] = name.jsonPrimitive.content
                element.values.forEach { named(it, into) }
            }
            is JsonArray -> element.forEach { named(it, into) }
            else -> Unit
        }
    }

    private val names = LinkedHashMap<Int, String>().also { named(Json.parseToJsonElement(text), it) }
    private val all = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name) }.toTypedArray())
    private val loot = ArchiveLoot.parse(text, all)

    private fun id(name: String) = names.entries.first { it.value == name }.key

    @Test
    fun `the file is the Wiki's table - a unique 1 in 14 by weights of 15, three rolls of 330, and the tertiary drops`() {
        assertEquals(emptyList(), loot.warnings)
        assertEquals(14, loot.uniqueOneIn)
        assertEquals(4, loot.perPlayerPastTwo)
        val uniques = assertNotNull(loot.uniques)
        assertEquals(15, uniques.slots)
        assertEquals(
            listOf("Rondache" to 5, "Zorya's tome (uncharged)" to 4, "Horn of Bran" to 3, "Ascension crossbows" to 2, "Obligator (uncharged)" to 1),
            uniques.lines.map { it.item!!.type.name to it.weight },
        )
        assertTrue(uniques.lines.all { it.item!!.min == 1 && it.item!!.max == 1 })
        val standard = assertNotNull(loot.standard)
        assertEquals(3, loot.standardRolls)
        assertEquals(51, loot.fiveTimesOneIn)
        assertEquals(330, standard.slots)
        assertEquals(33, standard.lines.size)
        assertEquals(mapOf(11 to 15, 7 to 8, 6 to 9, 55 to 1), standard.lines.groupingBy { it.weight }.eachCount())
        val shards = standard.lines.single { it.weight == 55 }.item!!
        assertEquals(Triple("Ascension shards", 75, 82), Triple(shards.type.name, shards.min, shards.max))
        assertEquals("Clue scroll (elite)" to 10, loot.clue?.type?.name to loot.clueOneIn)
        assertEquals("Archivist's folio (inert)" to 15, loot.folio?.name to loot.folioOneIn)
        assertEquals(setOf(34722, 34723), loot.folioForms)
        assertEquals(20, loot.fragmentOneIn)
        // The four fragments, each with its own varbit of the four the cache has in a row.
        assertEquals(4, loot.fragments.size)
        assertEquals(setOf(16434, 16435, 16436, 16437), loot.fragments.map { it.unlocked }.toSet())
        assertEquals("Cabbage", loot.junk?.name)
    }

    @Test
    fun `an item the cache doesn't have is left out with a warning, a name it doesn't agree with is warned about, and weights that don't add up stop the load`() {
        val rondache = id("Rondache")
        val cabbage = id("Cabbage")
        val without = ObjTypes.of(*names.filterKeys { it != rondache }.map { (id, name) -> ObjType(id, if (id == cabbage) "Lettuce" else name) }.toTypedArray())
        val parsed = ArchiveLoot.parse(text, without)
        assertEquals(listOf("no item $rondache (Rondache) in the cache", "$cabbage is Lettuce, not Cabbage"), parsed.warnings)
        // Its line's weight goes with it: the other four of the unique table's fifteen.
        assertEquals(10, parsed.uniques?.slots)
        assertEquals(330, parsed.standard?.slots)
        // A cache with none of it: nothing to give, and no stop.
        val none = ArchiveLoot.parse(text, ObjTypes.of())
        assertNull(none.uniques)
        assertNull(none.standard)
        assertNull(none.junk)
        assertTrue(none.fragments.isEmpty())
        val error = assertFailsWith<IllegalArgumentException> { ArchiveLoot.parse(text.replace("\"slots\": 330", "\"slots\": 331"), all) }
        assertTrue(error.message!!.contains("the standard table's weights don't add up to its 331 slots"), error.message)
    }

    @Test
    fun `the unique's chance is 1 in 14 for one or two and 4 percent of that more for each player past two`() {
        assertEquals(100 to 1400, loot.uniqueChance(1))
        assertEquals(100 to 1400, loot.uniqueChance(2))
        // The Wiki's own figures: 1/13.46 for three, 1/11.29 for eight (1/90.32 a player).
        assertEquals(104 to 1400, loot.uniqueChance(3))
        assertEquals(124 to 1400, loot.uniqueChance(8))
        assertEquals("13.46", "%.2f".format(java.util.Locale.ROOT, 1400.0 / 104))
        assertEquals("90.32", "%.2f".format(java.util.Locale.ROOT, 8 * 1400.0 / 124))
    }

    @Test
    fun `a death costs a tenth of what one of the party is expected to earn, and never more than they have`() {
        assertEquals(700, ArchiveLoot.contribution(700, 0, 1))
        // The Wiki's examples: 35 of a duo's 350, and 8 of an eight's 87.5.
        assertEquals(315, ArchiveLoot.contribution(350, 1, 2))
        assertEquals(280, ArchiveLoot.contribution(350, 2, 2))
        assertEquals(79, ArchiveLoot.contribution(87, 1, 8))
        assertEquals(0, ArchiveLoot.contribution(60, 1, 1))
        assertEquals(0, ArchiveLoot.contribution(0, 0, 4))
    }

    @Test
    fun `a party's unique comes 1 time in 14, to one member by their share of the contribution, and is all their chest holds`() {
        val random = GameRandom(29)
        val members = listOf(LootMember("ann", 600), LootMember("bob", 100))
        val uniques = loot.uniques!!.lines.map { it.item!!.type.id }.toSet()
        val finders = HashMap<String, Int>()
        val found = HashMap<Int, Int>()
        val runs = 14_000
        repeat(runs) {
            val result = loot.roll(random, members)
            assertEquals(listOf("ann", "bob"), result.chests.keys.toList())
            val finder = result.unique
            for ((key, chest) in result.chests) {
                if (key == finder) {
                    val drop = chest.single()
                    assertTrue(drop.type.id in uniques && drop.count == 1, "$drop")
                    found.merge(drop.type.id, 1, Int::plus)
                } else {
                    assertTrue(chest.none { it.type.id in uniques }, "a unique in $key's chest without the find")
                }
            }
            if (finder != null) finders.merge(finder, 1, Int::plus)
        }
        // 1000 expected of 14,000, six in seven of them Ann's; a third of them the Rondache, a fifteenth the Obligator.
        val total = finders.values.sum()
        assertTrue(total in 900..1100, "$total uniques")
        assertTrue(finders.getValue("ann") * 7 in total * 5..total * 13 / 2, "$finders")
        assertTrue(found.getValue(id("Rondache")) in total / 4..total * 5 / 12, "$found")
        assertTrue(found.getValue(id("Obligator (uncharged)")) in total / 30..total / 9, "$found")
    }

    @Test
    fun `a chest without the unique holds three standard rolls, one in 51 of them five times over, and the tertiary drops each on its own`() {
        val random = GameRandom(291)
        val standard = loot.standard!!.lines.associate { it.item!!.type.id to it.item!! }
        val tertiary = setOf(loot.clue!!.type.id, loot.folio!!.id) + loot.fragments.map { it.type.id }
        var clues = 0
        var folios = 0
        var fragments = 0
        var fiveTimes = 0
        var rolls = 0
        val runs = 6_000
        // Nobody finds a unique with the table's chance at none in the loader's hundredths: the same table, never rolled.
        val table = ArchiveLoot(
            loot.uniqueOneIn, loot.perPlayerPastTwo, null, loot.standardRolls, loot.fiveTimesOneIn, loot.standard, loot.clue, loot.clueOneIn,
            loot.folio, loot.folioOneIn, loot.folioForms, loot.fragments, loot.fragmentOneIn, loot.junk,
        )
        repeat(runs) {
            val chest = table.roll(random, listOf(LootMember("ann", 700))).chests.getValue("ann")
            assertEquals(chest.size, chest.map { it.type.id }.toSet().size, "an item twice in $chest")
            val drops = chest.filter { it.type.id !in tertiary }
            assertTrue(drops.size in 1..3, "$chest")
            for (drop in drops) {
                val item = assertNotNull(standard[drop.type.id], "${drop.type.name} isn't on the table")
                // One roll of it, or more of the three, each its count or five times that.
                assertTrue(drop.count >= item.min && drop.count <= 3 * 5 * item.max, "$drop")
                if (drops.size == 3) {
                    rolls++
                    if (drop.count > item.max) {
                        assertTrue(drop.count in 5 * item.min..5 * item.max, "$drop")
                        fiveTimes++
                    }
                }
            }
            for (drop in chest.filter { it.type.id in tertiary }) {
                assertEquals(1, drop.count, "$drop")
                when (drop.type.id) {
                    loot.clue!!.type.id -> clues++
                    loot.folio!!.id -> folios++
                    else -> fragments++
                }
            }
        }
        // 600, 400 and 300 expected of 6,000; and one roll in 51 five times over.
        assertTrue(clues in 520..680, "$clues clues")
        assertTrue(folios in 330..470, "$folios folios")
        assertTrue(fragments in 240..360, "$fragments fragments")
        assertTrue(fiveTimes * 51 in rolls / 2..rolls * 2, "$fiveTimes of $rolls rolls five times over")
    }

    @Test
    fun `nobody gets a folio they own or a fragment they own or have broken, and with all four no fragment is rolled`() {
        val random = GameRandom(7)
        val folio = loot.folio!!.id
        val ids = loot.fragments.map { it.type.id }
        val last = ids.last()
        val seen = HashSet<Int>()
        repeat(4_000) {
            val result = loot.roll(random, listOf(LootMember("ann", 700, ownsFolio = true, fragments = ids.dropLast(1).toSet())))
            seen += result.chests.getValue("ann").map { it.type.id }
        }
        assertTrue(folio !in seen, "a second folio")
        assertTrue(last in seen && ids.dropLast(1).none { it in seen }, "fragments ${seen.filter { it in ids }}")
        // With all four the fragment's draw isn't taken: the same seed gives one draw fewer than with one to find.
        val one = GameRandom(11)
        val none = GameRandom(11)
        val plain = ArchiveLoot(14, 4, null, 0, 51, null, null, 10, null, 15, emptySet(), loot.fragments, 1_000_000, null)
        plain.roll(one, listOf(LootMember("ann", 700, fragments = ids.dropLast(1).toSet())))
        plain.roll(none, listOf(LootMember("ann", 700, fragments = ids.toSet())))
        assertEquals(1L to 0L, one.draws to none.draws)
    }

    @Test
    fun `a member with no share doesn't count towards the party's size for the unique`() {
        val random = GameRandom(24)
        val shield = ObjType(1171, "Wooden shield")
        val cabbage = ObjType(1965, "Cabbage")
        // A unique one time in two for one or two, and certain for three: so a third member shows plainly.
        val table = ArchiveLoot(
            2, 100, DropTable(1, listOf(DropSlot(1, DropItem(shield, 1, 1)))), 3, 51, null,
            null, 10, null, 15, emptySet(), emptyList(), 20, cabbage,
        )
        val three = listOf(LootMember("ann", 200), LootMember("bob", 200), LootMember("cat", 300))
        assertEquals(400, (1..400).count { table.roll(random, three).unique != null }, "three who earned: certain")
        // An alt brought along and left idle, or doing too little for a share (exploit review round 24, R24A-2): the
        // party is two for the unique.
        val idle = listOf(LootMember("ann", 0), LootMember("bob", 300), LootMember("cat", 400))
        val token = listOf(LootMember("ann", 1), LootMember("bob", 300), LootMember("cat", 400))
        for (party in listOf(idle, token)) {
            val found = (1..400).count { table.roll(random, party).unique != null }
            assertTrue(found in 150..250, "$found of 400, about half")
        }
    }

    @Test
    fun `a member with no contribution left gets a cabbage and nothing else, never the unique, and a party of them rolls nothing`() {
        val random = GameRandom(3)
        val axe = ObjType(1351, "Bronze axe")
        val shield = ObjType(1171, "Wooden shield")
        val cabbage = ObjType(1965, "Cabbage")
        // A unique every run, and a standard table of one line: three axes a roll, five times over every time.
        val table = ArchiveLoot(
            1, 4, DropTable(1, listOf(DropSlot(1, DropItem(shield, 1, 1)))), 3, 1, DropTable(1, listOf(DropSlot(1, DropItem(axe, 3, 3)))),
            null, 10, null, 15, emptySet(), emptyList(), 20, cabbage,
        )
        repeat(200) {
            val result = table.roll(random, listOf(LootMember("ann", 0), LootMember("bob", 100), LootMember("cat", 150)))
            assertEquals(listOf(cabbage to 1), result.chests.getValue("ann").map { it.type to it.count })
            val finder = assertNotNull(result.unique)
            assertTrue(finder == "bob" || finder == "cat", finder)
            val other = if (finder == "bob") "cat" else "bob"
            assertEquals(listOf(shield to 1), result.chests.getValue(finder).map { it.type to it.count })
            // Three rolls of three, each five times over, in one stack.
            assertEquals(listOf(axe to 45), result.chests.getValue(other).map { it.type to it.count })
        }
        val before = random.draws
        val result = table.roll(random, listOf(LootMember("ann", 0), LootMember("bob", 0)))
        assertNull(result.unique)
        assertEquals(listOf(listOf(cabbage), listOf(cabbage)), result.chests.values.map { chest -> chest.map { it.type } })
        assertEquals(before, random.draws, "no draw for a party with nothing")
        // With no table at all (a cache with none of the items) the chests come up empty.
        assertEquals(mapOf("ann" to emptyList(), "bob" to emptyList()), ArchiveLoot.NONE.roll(random, listOf(LootMember("ann", 0), LootMember("bob", 700))).chests)
    }

    @Test
    fun `a member under a quarter of an even share performed poorly - a cabbage, and never the unique`() {
        assertEquals(listOf(175, 87, 58, 43, 21), listOf(1, 2, 3, 4, 8).map { ArchiveLoot.poorBelow(it) })
        val random = GameRandom(24)
        val axe = ObjType(1351, "Bronze axe")
        val shield = ObjType(1171, "Wooden shield")
        val cabbage = ObjType(1965, "Cabbage")
        // A unique every run, and a standard table of one line.
        val table = ArchiveLoot(
            1, 4, DropTable(1, listOf(DropSlot(1, DropItem(shield, 1, 1)))), 3, 1, DropTable(1, listOf(DropSlot(1, DropItem(axe, 1, 1)))),
            null, 10, null, 15, emptySet(), emptyList(), 20, cabbage,
        )
        // A carry and three alts that each touched a room for a point or so (R24A-1): a party of four, 43 to pass.
        val party = listOf(LootMember("carry", 690), LootMember("alt1", 7), LootMember("alt2", 2), LootMember("alt3", 42))
        repeat(200) {
            val result = table.roll(random, party)
            assertEquals("carry", result.unique)
            for (alt in listOf("alt1", "alt2", "alt3")) assertEquals(listOf(cabbage to 1), result.chests.getValue(alt).map { it.type to it.count })
        }
        // At the line, a member is paid as anyone else.
        val paid = table.roll(random, listOf(LootMember("ann", 600), LootMember("bob", 87)))
        assertTrue(paid.chests.values.none { chest -> chest.any { it.type == cabbage } }, "${paid.chests}")
    }

    @Test
    fun `the poor line and the death penalty go by those who earn a share, so idle alts don't lower the bar`() {
        // Exploit review round 25, R25A-2. A carry, a half-active alt and six who did nothing: only the carry earns.
        val idle = List(6) { 0 to 0 }
        assertEquals(1, ArchiveLoot.sharingSize(listOf(650 to 0, 50 to 0) + idle))
        // Each step up the line knocks out more: at eight the alt's 50 passes 21, at two it fails 87.
        assertEquals(2, ArchiveLoot.sharingSize(listOf(600 to 0, 100 to 0) + idle))
        // An even party keeps its size; a party where nobody earns has none.
        assertEquals(4, ArchiveLoot.sharingSize(List(4) { 175 to 0 }))
        assertEquals(0, ArchiveLoot.sharingSize(listOf(0 to 0, 0 to 0)))
        assertEquals(0, ArchiveLoot.sharingSize(emptyList()))
        // Deaths count at the shrunk size: alone the line is 175 and a death 70, so 250 with one death (180) passes and
        // with two (110) doesn't, though a party of eight would charge 8 a death.
        assertEquals(1, ArchiveLoot.sharingSize(listOf(250 to 1) + idle + listOf(0 to 0)))
        assertEquals(0, ArchiveLoot.sharingSize(listOf(250 to 2) + idle + listOf(0 to 0)))

        val random = GameRandom(25)
        val axe = ObjType(1351, "Bronze axe")
        val cabbage = ObjType(1965, "Cabbage")
        val table = ArchiveLoot(14, 4, null, 3, 51, DropTable(1, listOf(DropSlot(1, DropItem(axe, 1, 1)))), null, 10, null, 15, emptySet(), emptyList(), 20, cabbage)
        // Without the size given, roll works it out from the contributions: the half-active alt gets a cabbage.
        val party = listOf(LootMember("carry", 650), LootMember("alt", 50)) + List(6) { LootMember("idle$it", 0) }
        val result = table.roll(random, party)
        assertEquals(listOf(cabbage to 1), result.chests.getValue("alt").map { it.type to it.count })
        assertTrue(result.chests.getValue("carry").none { it.type == cabbage })
    }
}
