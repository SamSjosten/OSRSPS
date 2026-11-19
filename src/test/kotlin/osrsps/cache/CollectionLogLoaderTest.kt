package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.audit.FlatCache

class CollectionLogLoaderTest {
    @Test
    fun `tabs come from structs 471 to 475, pages from each tab's enum, items from each page's, in order`() {
        val enums = mapOf(
            900 to intEnum(0 to 800, 1 to 801),
            901 to intEnum(0 to 802),
            700 to intEnum(0 to 22372),
            701 to intEnum(0 to 12653, 1 to 11286, 2 to 13273),
            702 to intEnum(0 to 11286),
            CollectionLogLoader.DISPLAYED_ENUM to intEnum(13273 to 25624),
        )
        val structs = mapOf(
            471 to structOf(683 to 900, 684 to 0),
            475 to structOf(683 to 901, 684 to 4),
            800 to structOf(690 to 700),
            801 to structOf(690 to 701),
            802 to structOf(690 to 702),
        )
        val scripts = mapOf(CollectionLogLoader.COMPLETED_GETTER to completedScript(800 to 11965, 802 to 11990))
        val catalog = CollectionLogLoader.build(enums::get, structs::get, scripts::get)
        assertEquals(listOf(0, 4), catalog.tabs.map { it.index })
        assertEquals(listOf(800, 801), catalog.tabs[0].pages.map { it.struct })
        assertEquals(listOf(12653, 11286, 13273), catalog.page(801)!!.items)
        assertEquals(1, catalog.page(801)!!.index)
        assertEquals(11965, catalog.page(800)!!.completedVarbit)
        assertNull(catalog.page(801)!!.completedVarbit)
        assertEquals(11990, catalog.page(802)!!.completedVarbit)
        // An item on two tabs' pages is one item, on both pages.
        assertEquals(setOf(22372, 12653, 11286, 13273), catalog.items)
        assertEquals(listOf(801, 802), catalog.pagesOf(11286).map { it.struct })
        assertEquals(25624, catalog.displayed(13273))
        assertEquals(11286, catalog.displayed(11286))
    }

    @Test
    fun `no cache data, an empty log`() {
        val catalog = CollectionLogLoader.build({ null }, { null }, { null })
        assertEquals(0, catalog.items.size)
        assertEquals(0, catalog.tabs.size)
    }

    /**
     * The real 242 log, when given a checkout of github.com/abextm/osrs-cache at a rev 242 tag in OSRSPS_FLATCACHE. Not
     * in CI. The counts and ids are the rev 242 dump's (Joshua-F/osrs-dumps, 2026-11-18).
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the live cache's log`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val enums = cache.group(2, 8)
        val structs = cache.group(2, 34)
        val catalog = CollectionLogLoader.build(enums::get, structs::get) { cache.group(12, it)[0] }
        assertEquals(listOf(57, 4, 11, 22, 31), catalog.tabs.map { it.pages.size })
        assertEquals(1944, catalog.tabs.sumOf { tab -> tab.pages.sumOf { it.items.size } })
        assertEquals(1738, catalog.items.size)
        assertEquals(listOf(22372), catalog.page(478)!!.items) // Bryophyta: her essence
        assertEquals(11965, catalog.page(478)!!.completedVarbit) // collection_bosses_bryophyta_completed
        assertEquals(listOf(12653, 7980, 11920, 11286), catalog.page(491)!!.items) // King Black Dragon
        assertEquals(25624, catalog.displayed(13273)) // abyssalsire_unsired to its dummy
    }

    /** `iload 0; switch; jump end`, then `get_varbit v; return` for each case, as script4103's cases are. */
    private fun completedScript(vararg cases: Pair<Int, Int>): ByteArray = bytes {
        writeByte(0)
        fun op(opcode: Int, operand: Int = 0) {
            writeShort(opcode)
            if (opcode < 100 && opcode != 21) writeInt(operand) else writeByte(operand)
        }
        op(33); op(60); op(6, cases.size * 2)
        for ((_, varbit) in cases) { op(25, varbit); op(21) }
        writeInt(3 + cases.size * 2)
        repeat(6) { writeShort(if (it == 0) 1 else 0) }
        writeByte(1); writeShort(cases.size)
        cases.forEachIndexed { index, (struct, _) -> writeInt(struct); writeInt(1 + index * 2) }
        writeShort(1 + 2 + 8 * cases.size)
    }

    private fun intEnum(vararg entries: Pair<Int, Int>): ByteArray = bytes {
        writeByte(6); writeShort(entries.size)
        for ((key, value) in entries) { writeInt(key); writeInt(value) }
        writeByte(0)
    }

    private fun structOf(vararg params: Pair<Int, Int>): ByteArray = bytes {
        writeByte(249); writeByte(params.size)
        for ((key, value) in params) { writeByte(0); writeMedium(key); writeInt(value) }
        writeByte(0)
    }

    private fun DataOutputStream.writeMedium(value: Int) {
        writeByte(value shr 16); writeByte(value shr 8); writeByte(value)
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).use(block) }.toByteArray()
}
