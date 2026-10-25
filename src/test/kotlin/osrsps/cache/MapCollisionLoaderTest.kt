package osrsps.cache

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag

class MapCollisionLoaderTest {
    /** Terrain where every tile is plain, except the given settings for (level, x, z). */
    private fun terrain(settings: Map<Triple<Int, Int, Int>, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xFF) }
        for (level in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
            u16(82 + 3) // an underlay, which has no payload
            u16(10); u16(7) // an overlay with its id
            settings[Triple(level, x, z)]?.let { u16(49 + it) }
            if ((x + z) % 2 == 0) u16(0) else { u16(1); out.write(20) } // both ways a tile can end
        }
        return out.toByteArray()
    }

    /** A loc file holding one loc. */
    private fun locs(id: Int, x: Int, z: Int, level: Int, shape: Int, rotation: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun smart(v: Int) = if (v < 128) out.write(v) else { out.write((v + 0x8000) shr 8); out.write((v + 0x8000) and 0xFF) }
        var idDelta = id + 1
        while (idDelta >= 32767) { smart(32767); idDelta -= 32767 }
        smart(idDelta)
        smart(((level shl 12) or (x shl 6) or z) + 1)
        out.write((shape shl 2) or rotation)
        smart(0)
        smart(0)
        return out.toByteArray()
    }

    @Test
    fun `decodes terrain settings`() {
        val settings = MapCollisionLoader.decodeTerrain(terrain(mapOf(Triple(2, 5, 6) to 3)))
        assertEquals(3, settings[2][5][6].toInt())
        assertEquals(0, settings[0][5][6].toInt())
    }

    @Test
    fun `decodes a loc with a large id`() {
        val found = ArrayList<List<Int>>()
        MapCollisionLoader.decodeLocs(locs(40_000, 12, 34, 1, 10, 3)) { id, x, z, level, shape, rotation ->
            found.add(listOf(id, x, z, level, shape, rotation))
        }
        assertEquals(listOf(listOf(40_000, 12, 34, 1, 10, 3)), found)
    }

    @Test
    fun `loads collision from a cache, moving bridge tiles down a level`(@TempDir dir: Path) {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(6) { library.createIndex(named = true) }
        // Loc 5: a solid 2x1 table. Opcodes 14 (width) and 2 (name) then end.
        library.put(2, 6, 5, byteArrayOf(14, 2, 2, 'T'.code.toByte(), 0, 0))
        // Square 50_50: blocked floor at 3, 4 on level 0; a bridge column at 10, 10 with blocked floor on level 1; a
        // roof over 6, 7 on level 0 and over 12, 12 on the bridge's level 1.
        val settings = mapOf(Triple(0, 3, 4) to 1, Triple(1, 10, 10) to 3, Triple(0, 6, 7) to 4, Triple(1, 12, 12) to 6)
        library.put(5, "m50_50", terrain(settings))
        library.put(5, "l50_50", locs(5, 20, 20, 0, 10, 0))
        library.update()
        library.close()

        val map = CacheLibrary.create(cacheDir.toString()).use { MapCollisionLoader.load(it) }
        assertEquals(CollisionFlag.FLOOR, map[3203, 3204, 0])
        assertEquals(CollisionFlag.FLOOR, map[3210, 3210, 0], "bridge floor drops to level 0")
        assertEquals(0, map[3210, 3210, 1])
        assertEquals(CollisionFlag.ROOF, map[3206, 3207, 0], "a roof walks like open ground")
        assertEquals(CollisionFlag.ROOF, map[3212, 3212, 0], "a bridge roof drops to level 0 too")
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3220, 3220, 0])
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3221, 3220, 0])
        assertEquals(0, map[3222, 3220, 0])
        assertEquals(CollisionFlag.UNLOADED, map[3264, 3220, 0])
    }

    @Test
    fun `loads squares from an unnamed maps index, as current caches store them`(@TempDir dir: Path) {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(5) { library.createIndex(named = true) }
        library.createIndex(named = false)
        // displee only reads group N when the .dat2 is at least 6 * (N + 1) bytes, a check real caches always pass.
        library.put(0, 0, kotlin.random.Random(1).nextBytes(100_000)) // random, so compression keeps it large
        library.put(2, 6, 5, byteArrayOf(14, 2, 0))
        // Square 50_50 (group 50 shl 8 or 50) with terrain in file 0 and locs in file 1.
        library.put(5, (50 shl 8) or 50, 0, terrain(mapOf(Triple(0, 3, 4) to 1)))
        library.put(5, (50 shl 8) or 50, 1, locs(5, 20, 20, 0, 10, 0))
        // Square 51_50 has terrain only.
        library.put(5, (51 shl 8) or 50, 0, terrain(emptyMap()))
        // Group 52_50 holds no terrain: 37 three-byte files, like group 25287 in the live 241 cache.
        repeat(37) { library.put(5, (52 shl 8) or 50, it, byteArrayOf(0xFF.toByte(), 0, 0)) }
        library.update()
        library.close()

        val map = CacheLibrary.create(cacheDir.toString()).use { MapCollisionLoader.load(it) }
        assertEquals(CollisionFlag.FLOOR, map[3203, 3204, 0])
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3221, 3220, 0])
        assertEquals(0, map[3270, 3220, 0], "51_50 is loaded and open")
        assertEquals(CollisionFlag.UNLOADED, map[3340, 3220, 0], "the non-terrain group is skipped")
    }

    @Test
    fun `fails loudly on an unknown loc opcode`() {
        assertFailsWith<IllegalStateException> { LocTypeDecoder.decode(byteArrayOf(200.toByte(), 0)) }
    }

    @Test
    fun `skips the opcodes collision does not need`() {
        val def = byteArrayOf(
            1, 1, 0, 9, 10, // models with types
            30, 'W'.code.toByte(), 'a'.code.toByte(), 0, // op1 text
            77, 0, 1, 0, 2, 1, 0, 3, 0, 4, // multiloc: varbit, varp, count 1, two ids
            102, 0, 0, 1, 0, 2, 0, 3, 0, 0, 0, 4, 0, 0, 0, 5, 'x'.code.toByte(), 0, // conditional sub op
            249.toByte(), 1, 1, 0, 0, 7, 'p'.code.toByte(), 0, // one string param
            15, 3, 27, 0,
        )
        val type = LocTypeDecoder.decode(def)
        assertEquals(1, type.width)
        assertEquals(3, type.length)
        assertEquals(1, type.interactType)
    }

    @Test
    fun `reads a loc type's name, options, access sides and multiloc`() {
        fun text(value: String) = value.toByteArray(Charsets.ISO_8859_1).toList() + 0.toByte()
        val def = (
            listOf<Byte>(2) + text("Door") +
                listOf<Byte>(30) + text("Open") +
                listOf<Byte>(31) + text("Hidden") +
                listOf<Byte>(19, 1, 69, 29) +
                listOf<Byte>(92, 0, 1, 0, 2, 0, 9, 1, 0, 3, 0, 4) + // multiloc with a default: varbit, varp, default, count 1, two ids
                listOf<Byte>(0)
            ).toByteArray()
        val type = LocTypeDecoder.decode(1535, def, "poordoor")
        assertEquals("Door", type.name)
        assertEquals(listOf("Open", null, null, null, null), type.ops)
        assertEquals(1, type.op("open"))
        assertEquals(29, type.accessBlock)
        assertTrue(type.multiloc)
        assertEquals("poordoor", type.internalName)
    }

    @Test
    fun `keeps the map's locs and the loc types' gameval names`(@TempDir dir: Path) {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(5) { library.createIndex(named = true) }
        library.createIndex(named = false)
        repeat(19) { library.createIndex(named = false) }
        library.put(0, 0, kotlin.random.Random(1).nextBytes(100_000))
        library.put(2, 6, 5, byteArrayOf(14, 2, 0))
        library.put(24, 6, 5, "table".toByteArray())
        library.put(5, (50 shl 8) or 50, 0, terrain(emptyMap()))
        library.put(5, (50 shl 8) or 50, 1, locs(5, 20, 21, 0, 10, 1))
        library.update()
        library.close()

        val world = CacheLibrary.create(cacheDir.toString()).use { MapCollisionLoader.loadWorld(it) }
        assertEquals(listOf(ZoneLoc(5, 10, 1, 3220, 3221, 0)), world.locs.at(3220, 3221, 0))
        assertEquals(1, world.locs.size)
        assertEquals("table", world.locTypes[5]?.internalName)
        assertEquals(2, world.locTypes[5]?.width)
        // Turned a quarter, the 2x1 table covers 3220, 3221 and 3220, 3222.
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, world.collision[3220, 3222, 0])
    }
}

private inline fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T = try { block(this) } finally { close() }
