package osrsps.cache.overlay

import com.displee.cache.CacheLibrary
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir
import osrsps.net.Js5Store

class Js5GroupTest {
    private val files = linkedMapOf(0 to byteArrayOf(1, 2, 3), 3 to ByteArray(0), 9 to ByteArray(700) { it.toByte() })

    @Test
    fun `packs files in id order with their sizes as changes and one chunk, and unpacks them`() {
        val data = Js5Group.pack(mapOf(9 to files.getValue(9), 0 to files.getValue(0), 3 to files.getValue(3)))
        assertEquals(3 + 0 + 700 + 3 * 4 + 1, data.size)
        assertEquals(1, data.last().toInt(), "one chunk")
        // 3, then 0 - 3, then 700 - 0.
        assertContentEquals(byteArrayOf(0, 0, 0, 3, -1, -1, -1, -3, 0, 0, 2, -68), data.copyOfRange(703, 715))
        val unpacked = Js5Group.unpack(data, listOf(0, 3, 9))
        assertEquals(listOf(0, 3, 9), unpacked.keys.toList())
        for ((id, bytes) in files) assertContentEquals(bytes, unpacked[id], "file $id")
    }

    @Test
    fun `a single file is the group's data as it is`() {
        val data = byteArrayOf(5, 6, 7, 1)
        assertContentEquals(data, Js5Group.pack(mapOf(4 to data)))
        assertContentEquals(data, Js5Group.unpack(data, listOf(4))[4])
    }

    @Test
    fun `reads a group of files as the cache library writes it`(@TempDir dir: Path) {
        dir.resolve("main_file_cache.dat2").createFile()
        dir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(dir.toString())
        library.createIndex()
        for ((id, bytes) in files) library.put(0, 5, id, bytes)
        library.update()
        try {
            val stored = library.index(0)!!.readArchiveSector(5)!!.data
            val table = ReferenceTable.decode(Js5Container.unpack(library.index255!!.readArchiveSector(0)!!.data))
            val ids = table.group(5)!!.files.map { it.id }
            assertEquals(listOf(0, 3, 9), ids)
            val unpacked = Js5Group.unpack(Js5Container.unpack(Js5Store.stripVersionTrailer(stored)), ids)
            for ((id, bytes) in files) assertContentEquals(bytes, unpacked[id], "file $id")
        } finally {
            library.close()
        }
    }

    @Test
    fun `refuses what doesn't add up`() {
        assertFailsWith<IllegalArgumentException>("no files") { Js5Group.pack(emptyMap()) }
        assertFailsWith<IllegalArgumentException>("no ids") { Js5Group.unpack(byteArrayOf(1), emptyList()) }
        assertFailsWith<IllegalArgumentException>("sizes don't fit") { Js5Group.unpack(byteArrayOf(0, 0, 0, 9, 1), listOf(1, 2)) }
        val data = Js5Group.pack(files)
        assertFailsWith<IllegalArgumentException>("a size past the data") { Js5Group.unpack(data.copyOf().also { it[706] = 9 }, listOf(0, 3, 9)) }
        assertFailsWith<IllegalArgumentException>("a negative size") { Js5Group.unpack(data.copyOf().also { it[706] = 2 }, listOf(0, 3, 9)) }
        assertFailsWith<IllegalArgumentException>("bytes left over") { Js5Group.unpack(data.copyOf().also { it[714] = 0xB0.toByte() }, listOf(0, 3, 9)) }
    }
}
