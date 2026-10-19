package osrsps.cache

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class OpenRs2ArchiveTest {
    private val caches = """
        [
          {"id": 1, "scope": "runescape", "game": "oldschool", "environment": "live", "language": "en",
           "builds": [{"major": 241, "minor": 1}], "timestamp": "2026-09-30T11:00:00Z", "disk_store_valid": true},
          {"id": 2, "scope": "runescape", "game": "oldschool", "environment": "live", "language": "en",
           "builds": [{"major": 241, "minor": 3}], "timestamp": "2026-10-14T11:00:00Z", "disk_store_valid": true},
          {"id": 3, "scope": "runescape", "game": "oldschool", "environment": "live", "language": "en",
           "builds": [{"major": 241, "minor": 3}], "timestamp": "2026-10-15T11:00:00Z", "disk_store_valid": false},
          {"id": 4, "scope": "runescape", "game": "oldschool", "environment": "beta", "language": "en",
           "builds": [{"major": 242, "minor": 0}], "timestamp": "2026-10-16T11:00:00Z"},
          {"id": 5, "scope": "runescape", "game": "runescape", "environment": "live", "language": "en",
           "builds": [{"major": 241, "minor": 9}], "timestamp": "2026-10-17T11:00:00Z"},
          {"id": 6, "scope": "runescape", "game": "oldschool", "environment": "live", "language": "en",
           "builds": [], "timestamp": null}
        ]
    """.trimIndent()

    @Test
    fun `selects the newest valid live oldschool build for the revision`() {
        val entry = OpenRs2Archive.selectLatest(caches, 241)!!
        assertEquals(2, entry.id)
        assertEquals(3, entry.minor)
    }

    @Test
    fun `returns null when no cache matches`() {
        assertNull(OpenRs2Archive.selectLatest(caches, 242))
    }

    @Test
    fun `extracts disk zip without the cache folder`(@TempDir dir: Path) {
        OpenRs2Archive.extractCache(zipOf("cache/main_file_cache.dat2" to "dat", "cache/main_file_cache.idx255" to "idx"), dir)
        assertEquals("dat", dir.resolve("main_file_cache.dat2").readText())
        assertTrue(Files.exists(dir.resolve("main_file_cache.idx255")))
    }

    @Test
    fun `rejects zip entries that escape the cache folder`(@TempDir dir: Path) {
        assertFailsWith<IllegalArgumentException> {
            OpenRs2Archive.extractCache(zipOf("cache/../../evil" to "x"), dir.resolve("cache"))
        }
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
