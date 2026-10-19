package osrsps.cache

import com.displee.cache.CacheLibrary
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class CacheInfoTest {
    @Test
    fun `summarises a flat-file cache`(@TempDir dir: Path) {
        // An empty data file and reference index make a valid empty cache to build on.
        dir.resolve("main_file_cache.dat2").createFile()
        dir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(dir.toString())
        library.createIndex()
        library.put(0, 5, byteArrayOf(1, 2, 3))
        library.put(0, 9, byteArrayOf(4, 5, 6))
        library.update()
        library.close()

        val summary = CacheInfo.read(dir)
        assertEquals(1, summary.indices.size)
        assertEquals(2, summary.indices.single().groups)
    }

    @Test
    fun `explains a missing cache`(@TempDir dir: Path) {
        assertFailsWith<IllegalArgumentException> { CacheInfo.read(dir) }
    }
}
