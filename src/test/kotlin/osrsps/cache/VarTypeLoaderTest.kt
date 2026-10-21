package osrsps.cache

import com.displee.cache.CacheLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.VarbitType

class VarTypeLoaderTest {
    @Test
    fun `decodes a varbit`() {
        // Opcode 1: base varp 0x1234 (u16), bits 3 to 9, then end.
        assertEquals(VarbitType(0x1234, 3, 9), VarTypeLoader.decodeVarbit(byteArrayOf(1, 0x12, 0x34, 3, 9, 0)))
    }

    @Test
    fun `fails loudly on an unknown opcode or a varbit with no bits`() {
        assertFailsWith<IllegalStateException> { VarTypeLoader.decodeVarbit(byteArrayOf(2, 0)) }
        assertFailsWith<IllegalStateException> { VarTypeLoader.decodeVarbit(byteArrayOf(0)) }
        assertFailsWith<IllegalArgumentException> { VarTypeLoader.decodeVarbit(byteArrayOf(1, 0, 1, 9, 3, 0)) }
    }

    @Test
    fun `loads varp count and varbits from a cache`(@TempDir dir: Path) {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(3) { library.createIndex(named = true) }
        // displee only reads group N when the .dat2 is at least 6 * (N + 1) bytes, a check real caches always pass.
        library.put(0, 0, kotlin.random.Random(1).nextBytes(1_000))
        // Varps 0 and 173 (opcode 5, client code 1); the varp count runs to the highest id.
        library.put(2, 16, 0, byteArrayOf(0))
        library.put(2, 16, 173, byteArrayOf(5, 0, 1, 0))
        library.put(2, 14, 4, byteArrayOf(1, 0, (173).toByte(), 0, 0, 0))
        library.put(2, 14, 7, byteArrayOf(9, 0)) // does not decode, so is left out
        library.update()
        library.close()

        val lib = CacheLibrary.create(cacheDir.toString())
        val types = try { VarTypeLoader.load(lib) } finally { lib.close() }
        assertEquals(174, types.varpCount)
        assertEquals(VarbitType(173, 0, 0), types.varbit(4))
        assertNull(types.varbit(7))
        assertEquals(1, types.varbitCount)
    }
}
