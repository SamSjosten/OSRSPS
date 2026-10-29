package osrsps.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class FileSaveStoreTest {
    @Test
    fun `a save is written whole, replaces the old one, and leaves no temporary file`(@TempDir dir: Path) {
        val store = FileSaveStore(dir.resolve("players"))
        assertNull(store.read("alice"))
        store.write("alice", "first")
        store.write("alice", "second ✓")
        assertEquals("second ✓", store.read("alice"))
        assertEquals(listOf("alice.json"), dir.resolve("players").listDirectoryEntries().map { it.name })
    }

    @Test
    fun `a temporary file left by a crash is written over`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("alice.json.tmp"), "a much longer half-written save from before the crash")
        val store = FileSaveStore(dir)
        store.write("alice", "short")
        assertEquals("short", store.read("alice"))
    }
}
