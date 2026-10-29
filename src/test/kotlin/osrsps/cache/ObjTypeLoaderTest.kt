package osrsps.cache

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType

class ObjTypeLoaderTest {
    /** Writes an item type the way the cache stores it. These are made-up items, not copies of the cache's. */
    private fun obj(build: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            build()
            writeByte(0)
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.string(opcode: Int, text: String) {
        writeByte(opcode)
        write(text.toByteArray(Charsets.ISO_8859_1))
        writeByte(0)
    }

    /** An axe laid out like the rev 241 cache's items, new 4-byte model opcodes and data-less flags included. */
    private val axe = obj {
        writeByte(12); writeInt(16)
        writeByte(44); writeInt(70_000)
        writeByte(7); writeShort(-15)
        writeByte(4); writeShort(1060)
        string(36, "Wield")
        writeByte(13); writeByte(3)
        writeByte(45); writeInt(510); writeByte(0)
        writeByte(48); writeInt(510); writeByte(6)
        writeByte(46); writeInt(511)
        writeByte(94); writeShort(35)
        writeByte(75); writeShort(1360)
        writeByte(65)
        writeByte(251)
        writeByte(15)
        writeByte(160)
        writeByte(161); writeShort(2); writeShort(15_118); writeShort(15_119)
        writeByte(40); writeByte(1); writeShort(61); writeShort(5652)
        writeByte(43); writeByte(1); writeByte(1); write("Sub".toByteArray()); writeByte(0); writeByte(0)
        string(2, "Test axe")
        string(3, "A test axe.")
        string(33, "Light")
        string(34, "Hidden")
        writeByte(97); writeShort(2)
        writeByte(148); writeShort(3)
        writeByte(249); writeByte(2)
        writeByte(0); writeByte(0); writeShort(10); writeInt(5)
        writeByte(1); writeByte(0); writeShort(300); write("text".toByteArray()); writeByte(0)
    }

    @Test
    fun `decodes the fields the server uses and skips the rest`() {
        val type = ObjTypeLoader.decode(1, axe, "test_axe")
        assertEquals("Test axe", type.name)
        assertEquals("A test axe.", type.desc)
        assertEquals(16, type.cost)
        assertEquals(listOf(null, "Wield", null, null, "Drop"), type.iops)
        assertEquals(listOf(null, null, "Take", "Light", null), type.ops)
        assertEquals(3, type.wearpos1)
        assertEquals(35, type.category)
        assertEquals(1360, type.weight)
        assertTrue(type.tradeable)
        assertFalse(type.stackable)
        assertEquals(2, type.certlink)
        assertEquals(3, type.placeholderlink)
        assertEquals(mapOf(10 to 5, 300 to "text"), type.params)
        assertEquals("test_axe", type.internalName)
        assertEquals(9, type.highAlch)
        assertEquals(6, type.lowAlch)
    }

    @Test
    fun `an empty definition gets the client's defaults`() {
        val type = ObjTypeLoader.decode(5, obj {})
        assertEquals("null", type.name)
        assertNull(type.desc)
        assertEquals(1, type.cost)
        assertEquals(ObjType.DEFAULT_IOPS, type.iops)
        assertEquals(-1, type.certlink)
    }

    @Test
    fun `fails loudly on an unknown opcode or bytes after the end`() {
        assertFailsWith<IllegalStateException> { ObjTypeLoader.decode(1, byteArrayOf(10, 0)) }
        assertFailsWith<IllegalStateException> { ObjTypeLoader.decode(1, byteArrayOf(11, 0, 0)) }
    }

    @Test
    fun `notes take their name and value from the item and stack, placeholders take the name`() {
        val note = ObjTypeLoader.decode(2, obj { writeByte(97); writeShort(1); writeByte(98); writeShort(799) })
        val placeholder = ObjTypeLoader.decode(3, obj { writeByte(148); writeShort(1); writeByte(149); writeShort(14401) })
        val types = ObjTypeLoader.link(mapOf(1 to ObjTypeLoader.decode(1, axe), 2 to note, 3 to placeholder))
        val linkedNote = types[2]!!
        assertTrue(linkedNote.isCert)
        assertEquals("Test axe", linkedNote.name)
        assertEquals(16, linkedNote.cost)
        assertTrue(linkedNote.stackable)
        assertTrue(linkedNote.tradeable)
        assertEquals(ObjTypeLoader.CERT_DESC, linkedNote.desc)
        assertEquals(listOf(null, null, null, null, "Drop"), linkedNote.iops)
        assertEquals("Test axe", types[3]!!.name)
        assertEquals(0, types[3]!!.cost)
    }

    @Test
    fun `a note or placeholder whose item doesn't link back takes nothing from it`() {
        // Both point at the axe, which links to 2 and 3, not to 4 and 5.
        val note = ObjTypeLoader.decode(4, obj { writeByte(97); writeShort(1); writeByte(98); writeShort(799) })
        val placeholder = ObjTypeLoader.decode(5, obj { writeByte(148); writeShort(1); writeByte(149); writeShort(14401) })
        val types = ObjTypeLoader.link(mapOf(1 to ObjTypeLoader.decode(1, axe), 4 to note, 5 to placeholder))
        val oneWay = types[4]!!
        assertEquals(listOf("null", 1, false), listOf(oneWay.name, oneWay.cost, oneWay.tradeable))
        assertEquals("null", types[5]!!.name)
    }

    @Test
    fun `loads items and their gameval names from a cache`(@TempDir dir: Path) {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(25) { library.createIndex(named = true) }
        // displee only reads group N when the .dat2 is at least 6 * (N + 1) bytes, a check real caches always pass.
        library.put(0, 0, kotlin.random.Random(1).nextBytes(1_000))
        library.put(2, 10, 1, axe)
        library.put(2, 10, 7, byteArrayOf(10, 0))
        library.put(24, 0, 1, "test_axe".toByteArray())
        library.update()
        val types = ObjTypeLoader.load(library)
        library.close()
        assertEquals("Test axe", types[1]?.name)
        assertNull(types[7], "an item that doesn't decode is left out")
        assertEquals(1, types.find("TEST_AXE")?.id)
        assertEquals(1, types.findByName("test axe")?.id)
    }

    @Test
    fun `a cache without items loads none`(@TempDir dir: Path) {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(3) { library.createIndex(named = true) }
        library.update()
        assertEquals(0, ObjTypeLoader.load(library).size)
        library.close()
    }
}
