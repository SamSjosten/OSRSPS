package osrsps.audit

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.ObjTypeLoader
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.WearRequirements
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemAuditTest {
    /** Made-up items, not the cache's: a sword with one wrong bonus, and a helm with no requirements listed. */
    private val sword = ObjType(
        id = 1, name = "Test sword", cost = 100, wearpos1 = WearPos.WEAPON, weight = 1800, internalName = "test_sword",
        params = mapOf(0 to 10, 1 to 5, 14 to 4),
    )
    private val helm = ObjType(id = 2, name = "Test helm", cost = 50, wearpos1 = WearPos.HEAD, weight = 900, internalName = "test_helm")

    private fun wikiSword(stab: Int) = WikiEquipment(
        id = 1, name = "Test sword", version = "", slot = WearPos.WEAPON, speed = 4, category = "", twoHanded = false,
        weight = 1800, bonuses = IntArray(14).also { it[0] = stab; it[1] = 5 },
    )

    @Test
    fun `reports only the stats that disagree`() {
        val audit = ItemAudit(
            ObjTypes.of(sword, helm),
            listOf(wikiSword(stab = 11)),
            listOf(
                WikiItem(1, "Test sword", false, false, 100, 1800, true, emptyMap()),
                WikiItem(2, "Test helm", false, false, 50, 900, true, mapOf("defence" to 20)),
            ),
            WearRequirements.NONE,
        ).run()
        assertEquals(listOf("Bonus: stab attack" to "10/11", "Requirements" to "none/defence=20"), audit.findings.map { it.check to "${it.ours}/${it.wiki}" })
        assertTrue("2\tTest helm\tdefence=20" in audit.requirementCandidates())
    }

    @Test
    fun `parses the calculator's equipment and the item database`() {
        val equipment = ItemAudit.parseEquipment(
            """[{"name":"Test sword","id":1,"weight":1.8,"version":"","slot":"weapon","speed":4,"category":"Stab Sword",
               "bonuses":{"str":7,"ranged_str":0,"magic_str":0,"prayer":0},
               "offensive":{"stab":10,"slash":5,"crush":-2,"magic":0,"ranged":0},
               "defensive":{"stab":0,"slash":1,"crush":0,"magic":0,"ranged":0},"isTwoHanded":false}]""",
        ).single()
        assertEquals(WearPos.WEAPON, equipment.slot)
        assertEquals(1800, equipment.weight)
        assertEquals(listOf(10, 5, -2, 0, 0, 0, 1, 0, 0, 0, 7, 0, 0, 0), equipment.bonuses.toList())
        val item = ItemAudit.parseItems(
            """{"2":{"id":2,"name":"Test helm","members":false,"tradeable_on_ge":true,"cost":50,"weight":0.907,
               "equipable_by_player":true,"duplicate":false,"equipment":{"requirements":{"defence":20}}},
               "3":{"id":3,"name":"Copy","duplicate":true}}""",
        ).single()
        assertEquals(907, item.weight)
        assertEquals(mapOf("defence" to 20), item.requirements)
    }

    @Test
    fun `splits a group into its files`() {
        // Two files, one chunk: "ab" and "c", then the size deltas 2 and -1, then the chunk count.
        val data = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 0, 0, 2, -1, -1, -1, -1, 1)
        val files = FlatCache.split(data, listOf(5, 9))
        assertEquals("ab", String(files.getValue(5)))
        assertEquals("c", String(files.getValue(9)))
    }

    /**
     * The audit itself, against the live cache. It runs only when given the files, so not in CI:
     * - OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at a rev 241 tag (2.flatcache and 24.flatcache);
     * - OSRSPS_WIKI_EQUIPMENT: weirdgloop/osrs-dps-calc's cdn/json/equipment.json;
     * - OSRSPS_WIKI_ITEMS: 0xNeffarion/osrsreboxed-db's docs/items-complete.json (50 MB: set OSRSPS_TEST_HEAP=2g).
     * It writes build/item-audit/report.md and requirements-candidates.tsv, and fails only if an item doesn't decode.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "OSRSPS_WIKI_EQUIPMENT", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "OSRSPS_WIKI_ITEMS", matches = ".+")
    fun `audit the live cache against the Wiki`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val names = cache.group(GAMEVAL_INDEX, GAMEVAL_OBJS)
        val decoded = cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) ->
            ObjTypeLoader.decode(id, data, names[id]?.let { String(it, Charsets.ISO_8859_1) })
        }
        val objs = ObjTypeLoader.link(decoded)
        val audit = ItemAudit(
            objs,
            ItemAudit.parseEquipment(Path(System.getenv("OSRSPS_WIKI_EQUIPMENT")).readText()),
            ItemAudit.parseItems(Path(System.getenv("OSRSPS_WIKI_ITEMS")).readText()),
            WearRequirements.load(objs),
        ).run()
        val out = Path("build", "item-audit").createDirectories()
        val source = "Cache: ${decoded.size} items from ${System.getenv("OSRSPS_FLATCACHE")}. " +
            "Wiki: osrs-dps-calc equipment.json, osrsreboxed items-complete.json."
        out.resolve("report.md").writeText(audit.report(source))
        out.resolve("requirements-candidates.tsv").writeText(audit.requirementCandidates())
        println("Item audit: ${audit.findings.size} findings in ${out.resolve("report.md").toAbsolutePath()}")
        assertTrue(Files.size(out.resolve("report.md")) > 0)
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val OBJ_GROUP = 10
        const val GAMEVAL_INDEX = 24
        const val GAMEVAL_OBJS = 0
    }
}
