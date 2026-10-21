package osrsps.cache

import com.displee.cache.CacheLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class EffectIdLoaderTest {
    private fun library(dir: Path, indices: Int, fill: CacheLibrary.() -> Unit): CacheLibrary {
        val cacheDir = Files.createDirectories(dir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(indices) { library.createIndex() }
        library.fill()
        library.update()
        library.close()
        return CacheLibrary.create(cacheDir.toString())
    }

    @Test
    fun `lists the ids of each kind`(@TempDir dir: Path) {
        val library = library(dir, indices = 12) {
            put(2, 12, 0, byteArrayOf(0))
            put(2, 12, 866, byteArrayOf(0))
            put(2, 13, 85, byteArrayOf(0))
            put(4, 3813, 0, byteArrayOf(1))
            put(11, 2, 0, byteArrayOf(1))
        }
        val ids = library.use { EffectIdLoader.load(it) }
        assertEquals(listOf(true, true, false), listOf(0, 866, 1).map(ids::seqExists))
        assertEquals(listOf(true, false), listOf(85, 0).map(ids::spotanimExists))
        assertEquals(listOf(true, false), listOf(3813, 0).map(ids::synthExists))
        assertEquals(listOf(true, false), listOf(2, 0).map(ids::jingleExists))
    }

    @Test
    fun `allows any id of a kind the cache has no index for`(@TempDir dir: Path) {
        val library = library(dir, indices = 5) { put(4, 1, 0, byteArrayOf(1)) }
        val ids = library.use { EffectIdLoader.load(it) }
        assertEquals(listOf(true, true), listOf(0, 65535).map(ids::jingleExists))
        assertEquals(listOf(true, false), listOf(1, 2).map(ids::synthExists))
    }
}
