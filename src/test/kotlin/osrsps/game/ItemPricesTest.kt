package osrsps.game

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ItemPricesTest {
    // Made-up items and prices in the Wiki modules' shapes, not the Wiki's figures.
    private val axe = ObjType(1351, "Bronze axe", cost = 16)
    private val axeNote = ObjType(1352, "Bronze axe", cost = 16, stackable = true, certlink = 1351, certtemplate = 799)
    private val cape = ObjType(6570, "Fire cape", cost = 30_000)

    private val pricesJson = """{"%LAST_UPDATE%":1793291895,"%LAST_UPDATE_F%":"29 October 2026 16:38:15 (UTC)",
        "Bronze axe":21,"3rd Age axe":2403298359}"""
    private val limitsJson = """{"%LAST_UPDATE%":1793291895,"Bronze axe":40,"3rd Age axe":40}"""
    private val idsJson = """{"%LAST_UPDATE%":1793291895,"Bronze axe":1351,"3rd Age axe":20011}"""

    private fun wiki(url: String) = when (url) {
        ItemPrices.PRICES_URL -> pricesJson
        ItemPrices.LIMITS_URL -> limitsJson
        ItemPrices.IDS_URL -> idsJson
        else -> error("unexpected $url")
    }

    @Test
    fun `the Wiki's three modules make the snapshot, by item id, prices past an int too`() {
        val prices = ItemPrices.download(::wiki)
        assertEquals(2, prices.size)
        assertEquals(21L, prices.price(axe))
        assertEquals(40, prices.limit(axe))
        assertEquals(2_403_298_359L, prices.price(ObjType(20011, "3rd age axe")))
        assertEquals("29 October 2026 16:38:15 (UTC)", prices.snapshot)
    }

    @Test
    fun `a note has its item's price, an item with none has none`() {
        val prices = ItemPrices.download(::wiki)
        assertEquals(21L, prices.price(axeNote))
        assertNull(prices.price(cape))
        assertNull(prices.limit(cape))
    }

    @Test
    fun `a price below 1 counts as none, so the Grand Exchange never fills for free`() {
        val prices = ItemPrices.parse("1351\tBronze axe\t0\t40\n6570\tFire cape\t-5\t\n")
        assertNull(prices.price(axe))
        assertNull(prices.price(axeNote))
        assertNull(prices.price(cape))
        assertEquals(40, prices.limit(axe))
        assertEquals(18_000L, prices.deathValue(cape))
    }

    @Test
    fun `on death an item counts its price, or its high alchemy value without one`() {
        val prices = ItemPrices.download(::wiki)
        assertEquals(21L, prices.deathValue(axe))
        assertEquals(18_000L, prices.deathValue(cape))
    }

    @Test
    fun `the file reads back what it wrote, and a hand edit counts`() {
        val prices = ItemPrices.download(::wiki)
        val text = prices.format()
        assertTrue(text.startsWith("# Grand Exchange guide prices"))
        val edited = text.replace("1351\tBronze axe\t21\t40", "1351\tBronze axe\t25\t")
        val read = ItemPrices.parse(edited)
        assertEquals(25L, read.price(axe))
        assertNull(read.limit(axe))
        assertEquals(prices.snapshot, read.snapshot)
        assertFailsWith<IllegalArgumentException> { ItemPrices.parse("1351\tBronze axe\tlots\t40") }
        assertFailsWith<IllegalArgumentException> { ItemPrices.parse("1351\tBronze axe") }
    }

    @Test
    fun `the first start downloads the file, later starts read it`(@TempDir dir: Path) {
        val file = dir.resolve(ItemPrices.FILE_NAME)
        assertEquals(2, ItemPrices.loadOrDownload(file, ::wiki).size)
        assertTrue(file.exists())
        file.writeText(file.readText().replace("\t21\t", "\t30\t"))
        val again = ItemPrices.loadOrDownload(file) { error("no download once the file is there") }
        assertEquals(30L, again.price(axe))
    }

    @Test
    fun `a failed download leaves no prices and no file, so the next start tries again`(@TempDir dir: Path) {
        val file = dir.resolve(ItemPrices.FILE_NAME)
        val prices = ItemPrices.loadOrDownload(file) { error("offline") }
        assertEquals(0, prices.size)
        assertFalse(file.exists())
    }

    @Test
    fun `a refresh keeps the old file as a backup`(@TempDir dir: Path) {
        val file = dir.resolve(ItemPrices.FILE_NAME)
        file.writeText("1351\tBronze axe\t99\t\n")
        ItemPrices.refresh(file, ::wiki)
        assertEquals(21L, ItemPrices.parse(file.readText()).price(axe))
        assertEquals(99L, ItemPrices.parse(dir.resolve("${ItemPrices.FILE_NAME}.bak").readText()).price(axe))
    }
}
