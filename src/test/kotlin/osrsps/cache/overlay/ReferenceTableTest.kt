package osrsps.cache.overlay

import com.displee.cache.CacheLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ReferenceTableTest {
    private fun group(id: Int, vararg files: Int, nameHash: Int = id * 31, version: Int = id + 100) = ReferenceTable.Group(
        id, nameHash, crc = id * 0x01010101, compressedSize = id * 3, decompressedSize = id * 5, version = version,
        files = files.map { ReferenceTable.File(it, it * 17) },
    )

    private fun assertSame(expected: ReferenceTable, actual: ReferenceTable) {
        assertEquals(expected.protocol, actual.protocol)
        assertEquals(expected.revision, actual.revision)
        assertEquals(expected.named, actual.named)
        assertEquals(expected.sized, actual.sized)
        assertEquals(expected.groups.size, actual.groups.size)
        for ((e, a) in expected.groups.zip(actual.groups)) {
            assertEquals(e.id, a.id)
            assertEquals(if (expected.named) e.nameHash else 0, a.nameHash)
            assertEquals(e.crc, a.crc)
            assertEquals(if (expected.sized) e.compressedSize else 0, a.compressedSize)
            assertEquals(if (expected.sized) e.decompressedSize else 0, a.decompressedSize)
            assertEquals(e.version, a.version)
            assertEquals(e.files.map { it.id }, a.files.map { it.id })
            assertEquals(e.files.map { if (expected.named) it.nameHash else 0 }, a.files.map { it.nameHash })
        }
    }

    @Test
    fun `round trips protocol 7 with names, sizes and ids past 32767`() {
        // 40000 - 1 doesn't fit the 2-byte form, so it goes as a 4-byte value with the top bit set.
        val table = ReferenceTable(7, 123456, named = true, sized = true, mutableListOf(group(1, 0, 1, 2), group(40000, 0), group(65535, 3, 40000)))
        val encoded = table.encode()
        assertEquals(7, encoded[0].toInt())
        assertEquals(5, encoded[5].toInt(), "named and sized flags")
        assertSame(table, ReferenceTable.decode(encoded))
        assertContentEquals(encoded, ReferenceTable.decode(encoded).encode())
    }

    @Test
    fun `round trips protocols 5 and 6 without names or sizes`() {
        val sizes = (5..6).map { protocol ->
            val table = ReferenceTable(protocol, if (protocol == 6) 77 else 0, named = false, sized = false, mutableListOf(group(0, 0), group(9, 0, 4)))
            val encoded = table.encode()
            assertSame(table, ReferenceTable.decode(encoded))
            encoded.size
        }
        assertEquals(sizes[0] + 4, sizes[1], "the revision is written from protocol 6")
    }

    @Test
    fun `put keeps the groups in id order and replaces one with the same id`() {
        val table = ReferenceTable(7, 1, named = false, sized = false, mutableListOf(group(2, 0), group(8, 0)))
        table.put(group(5, 0))
        table.put(group(8, 0, 1, version = 999))
        table.put(group(1, 0))
        assertEquals(listOf(1, 2, 5, 8), table.groups.map { it.id })
        assertEquals(999, table.group(8)!!.version)
        assertEquals(2, table.group(8)!!.files.size)
        assertEquals(8, table.maxGroupId)
        assertNull(table.group(3))
        assertSame(table, ReferenceTable.decode(table.encode()))
    }

    @Test
    fun `refuses what it can't read back`() {
        val table = ReferenceTable(7, 1, named = false, sized = false, mutableListOf(group(3, 0), group(2, 0)))
        assertFailsWith<IllegalArgumentException>("ids out of order") { table.encode() }
        val good = ReferenceTable(7, 1, named = false, sized = false, mutableListOf(group(3, 0))).encode()
        assertFailsWith<IllegalArgumentException>("protocol 4") { ReferenceTable.decode(byteArrayOf(4) + good.drop(1)) }
        assertFailsWith<IllegalArgumentException>("flag 2") { ReferenceTable.decode(good.copyOf().also { it[5] = 2 }) }
        assertFailsWith<IllegalArgumentException>("trailing byte") { ReferenceTable.decode(good + 0) }
        // Below protocol 7 every count and id is 2 bytes; from 7 on, 70000 goes as 4 bytes.
        assertFailsWith<IllegalArgumentException>("70000 needs protocol 7") {
            ReferenceTable(6, 1, named = false, sized = false, mutableListOf(group(0, 0), group(70000, 0))).encode()
        }
        val wide = ReferenceTable(7, 1, named = false, sized = false, mutableListOf(group(0, 0), group(70000, 0)))
        assertSame(wide, ReferenceTable.decode(wide.encode()))
    }

    @Test
    fun `reads the table the cache library writes, whose CRC is the index CRC the client logs in with`(@TempDir dir: Path) {
        dir.resolve("main_file_cache.dat2").createFile()
        dir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(dir.toString())
        library.createIndex(named = true)
        library.put(0, "first", byteArrayOf(1, 2, 3))
        library.put(0, 7, byteArrayOf(4))
        library.update()
        try {
            val index = library.index(0)!!
            val stored = library.index255!!.readArchiveSector(0)!!.data
            assertEquals(index.crc, Js5Container.crc(stored))
            val table = ReferenceTable.decode(Js5Container.unpack(stored))
            assertTrue(table.named)
            assertFalse(table.sized)
            assertEquals(listOf(0, 7), table.groups.map { it.id })
            assertEquals(index.archive(7)!!.crc, table.group(7)!!.crc)
            assertEquals(listOf(0), table.group(0)!!.files.map { it.id })
            assertEquals(7, table.maxGroupId)
            assertContentEquals(Js5Container.unpack(stored), table.encode(), "written back byte for byte")
        } finally {
            library.close()
        }
    }
}
