package osrsps.net

import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.cache.overlay.CacheOverlay
import osrsps.cache.overlay.Js5Container
import osrsps.cache.overlay.Js5Group
import osrsps.cache.overlay.ReferenceTable
import osrsps.testclient.Js5Crcs

class Js5StoreTest {
    @Test
    fun `strips the version trailer from a compressed container`() {
        // gzip type, 3 compressed bytes, 4-byte uncompressed length, then a 2-byte trailer.
        val stored = byteArrayOf(2, 0, 0, 0, 3, 9, 9, 9, 0, 0, 0, 7, 0x12, 0x34)
        assertContentEquals(stored.copyOf(12), Js5Store.stripVersionTrailer(stored))
    }

    @Test
    fun `keeps an uncompressed container without a trailer`() {
        val stored = byteArrayOf(0, 0, 0, 0, 2, 5, 6)
        assertContentEquals(stored, Js5Store.stripVersionTrailer(stored))
    }

    @Test
    fun `rejects a container shorter than its header says`() {
        assertFailsWith<IllegalArgumentException> { Js5Store.stripVersionTrailer(byteArrayOf(0, 0, 0, 0, 9, 1)) }
    }

    @Test
    fun `writes the master index as crc and version per archive`() {
        val container = Js5Store.masterIndex(intArrayOf(0x01020304, -1), intArrayOf(7, 8))
        assertEquals(0, container[0].toInt())
        assertEquals(16, container[4].toInt())
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 0, 0, 0, 7, -1, -1, -1, -1, 0, 0, 0, 8),
            container.copyOfRange(5, container.size),
        )
    }

    /** Indices 0 to 7, index 7 holding group 3 and index 2 group 6 holding files 0, 1 and 4; closed when [block] returns. */
    private fun <T> tinyCache(dir: Path, block: (CacheLibrary) -> T): T {
        dir.resolve("main_file_cache.dat2").createFile()
        dir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(dir.toString())
        repeat(8) { library.createIndex(named = true) }
        library.put(7, 3, byteArrayOf(1, 2, 3))
        for (id in listOf(0, 1, 4)) library.put(2, 6, id, byteArrayOf(10, id.toByte()))
        library.update()
        return try {
            block(library)
        } finally {
            library.close()
        }
    }

    private fun container(frame: ByteBuf, archive: Int, group: Int): ByteArray {
        val bytes = ByteArray(frame.readableBytes()).also { frame.getBytes(frame.readerIndex(), it) }
        return Js5Crcs.readResponse(DataInputStream(ByteArrayInputStream(bytes)), archive, group)
    }

    @Test
    fun `serves an overlay group and lists it in its index's table and the master index`(@TempDir dir: Path) = tinyCache(dir) { library ->
        val model = ByteArray(1500) { it.toByte() }
        val plain = Js5Store.load(library)
        val store = Js5Store.load(library, CacheOverlay(listOf(CacheOverlay.Group(7, 65000, model))))
        assertEquals(plain.size + 1, store.size)

        val served = container(store.provide(7, 65000)!!, 7, 65000)
        assertEquals(Js5Container.NONE, Js5Container.type(served))
        assertContentEquals(model, Js5Container.unpack(served))

        val stored = library.index255!!.readArchiveSector(7)!!.data
        val tableContainer = container(store.provide(255, 7)!!, 255, 7)
        assertEquals(Js5Container.type(stored), Js5Container.type(tableContainer), "compressed as the cache stores it")
        val table = ReferenceTable.decode(Js5Container.unpack(tableContainer))
        assertEquals(listOf(3, 65000), table.groups.map { it.id })
        val entry = table.group(65000)!!
        assertEquals(Js5Container.crc(served), entry.crc)
        assertEquals(Js5Store.OVERLAY_VERSION, entry.version)
        assertEquals(listOf(0), entry.files.map { it.id })
        assertEquals(ReferenceTable.decode(Js5Container.unpack(stored)).group(3)!!.crc, table.group(3)!!.crc, "the live group is untouched")

        assertEquals(Js5Container.crc(tableContainer), store.crcs[7])
        assertNotEquals(plain.crcs[7], store.crcs[7])
        val master = container(store.provide(255, 255)!!, 255, 255)
        val masterCrc7 = ((master[5 + 56].toInt() and 0xFF) shl 24) or ((master[6 + 56].toInt() and 0xFF) shl 16) or
            ((master[7 + 56].toInt() and 0xFF) shl 8) or (master[8 + 56].toInt() and 0xFF)
        assertEquals(store.crcs[7], masterCrc7)
        assertContentEquals(container(plain.provide(7, 3)!!, 7, 3), container(store.provide(7, 3)!!, 7, 3))
    }

    @Test
    fun `adds an overlay file to a live group, which is packed again with a new CRC`(@TempDir dir: Path) = tinyCache(dir) { library ->
        val locType = byteArrayOf(2, 65, 0, 0)
        val plain = Js5Store.load(library)
        val store = Js5Store.load(library, CacheOverlay(emptyList(), listOf(CacheOverlay.File(2, 6, 65000, locType))))
        assertEquals(plain.size, store.size, "no new group")

        val stored = library.index(2)!!.readArchiveSector(6)!!.data
        val served = container(store.provide(2, 6)!!, 2, 6)
        assertEquals(Js5Container.type(stored), Js5Container.type(served), "compressed as the cache stores it")
        val table = ReferenceTable.decode(Js5Container.unpack(container(store.provide(255, 2)!!, 255, 2)))
        val entry = table.group(6)!!
        assertEquals(listOf(0, 1, 4, 65000), entry.files.map { it.id })
        assertEquals(Js5Container.crc(served), entry.crc)
        val before = ReferenceTable.decode(Js5Container.unpack(library.index255!!.readArchiveSector(2)!!.data)).group(6)!!
        assertEquals(before.version, entry.version)
        assertNotEquals(before.crc, entry.crc)
        val files = Js5Group.unpack(Js5Container.unpack(served), entry.files.map { it.id })
        assertContentEquals(locType, files[65000])
        for (id in listOf(0, 1, 4)) assertContentEquals(byteArrayOf(10, id.toByte()), files[id], "live file $id")
        assertEquals(Js5Container.crc(container(store.provide(255, 2)!!, 255, 2)), store.crcs[2])
        assertNotEquals(plain.crcs[2], store.crcs[2])
        assertEquals(plain.crcs[7], store.crcs[7], "other indices untouched")
    }

    @Test
    fun `refuses an overlay file the group has, a doubled one, and a group the cache doesn't have`(@TempDir dir: Path) = tinyCache(dir) { library ->
        fun load(vararg files: CacheOverlay.File) = Js5Store.load(library, CacheOverlay(emptyList(), files.toList()))
        assertContains(assertFailsWith<IllegalArgumentException> { load(CacheOverlay.File(2, 6, 4, byteArrayOf(0))) }.message!!, "go up to 4")
        assertContains(
            assertFailsWith<IllegalArgumentException> { load(CacheOverlay.File(2, 6, 9, byteArrayOf(0)), CacheOverlay.File(2, 6, 9, byteArrayOf(0))) }.message!!,
            "twice",
        )
        assertContains(assertFailsWith<IllegalStateException> { load(CacheOverlay.File(2, 7, 9, byteArrayOf(0))) }.message!!, "no group 7")
        load(CacheOverlay.File(2, 6, 5, byteArrayOf(0)))
    }

    @Test
    fun `refuses an overlay group the cache has, one a request can't carry, and an unknown index`(@TempDir dir: Path) = tinyCache(dir) { library ->
        fun load(index: Int, group: Int) = Js5Store.load(library, CacheOverlay(listOf(CacheOverlay.Group(index, group, byteArrayOf(1)))))
        assertContains(assertFailsWith<IllegalArgumentException> { load(7, 3) }.message!!, "go up to 3")
        assertContains(assertFailsWith<IllegalArgumentException> { load(7, 65536) }.message!!, "doesn't fit")
        assertContains(assertFailsWith<IllegalArgumentException> { load(9, 65000) }.message!!, "index 9")
        load(7, 4)
    }

    @Test
    fun `serves an overlay group of several files, listing them in the table`(@TempDir dir: Path) = tinyCache(dir) { library ->
        val files = mapOf(0 to byteArrayOf(1), 1 to byteArrayOf(2, 2), 5 to byteArrayOf(3, 3, 3))
        val store = Js5Store.load(library, CacheOverlay(listOf(CacheOverlay.Group(7, 65001, Js5Group.pack(files), files.keys.sorted()))))
        val table = ReferenceTable.decode(Js5Container.unpack(container(store.provide(255, 7)!!, 255, 7)))
        val entry = table.group(65001)!!
        assertEquals(listOf(0, 1, 5), entry.files.map { it.id })
        val served = container(store.provide(7, 65001)!!, 7, 65001)
        assertEquals(entry.crc, Js5Container.crc(served))
        val unpacked = Js5Group.unpack(Js5Container.unpack(served), entry.files.map { it.id })
        for ((id, data) in files) assertContentEquals(data, unpacked[id], "file $id")
    }
}
