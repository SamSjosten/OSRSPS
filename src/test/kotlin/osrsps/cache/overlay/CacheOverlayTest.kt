package osrsps.cache.overlay

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir
import osrsps.cache.model.ModelCodec
import osrsps.cache.model.OsrsModel

class CacheOverlayTest {
    private fun model(color: Int) = ModelCodec.encode(
        OsrsModel(
            vertexX = intArrayOf(0, 100, 0), vertexY = intArrayOf(0, 0, -50), vertexZ = intArrayOf(0, 0, 100),
            faceA = intArrayOf(0), faceB = intArrayOf(1), faceC = intArrayOf(2), faceColors = intArrayOf(color),
        ),
    )

    @Test
    fun `loads the models in data models by id and leaves other files alone`(@TempDir dataDir: Path) {
        val models = Files.createDirectories(dataDir.resolve("models"))
        Files.write(models.resolve("65001.dat"), model(1))
        Files.write(models.resolve("65000.dat"), model(2))
        Files.writeString(models.resolve("README.md"), "where these came from")
        Files.writeString(models.resolve("lodestone.obj"), "v 0 0 0")
        Files.writeString(models.resolve("notes.dat"), "not a model, not an id")
        val overlay = CacheOverlay.load(dataDir)
        assertEquals(2, overlay.size)
        assertEquals("7/65000, 7/65001", overlay.toString())
        assertContentEquals(model(2), overlay.groups[0].data)
        assertContentEquals(model(1), overlay.groups[1].data)
        assertEquals(setOf(CacheOverlay.MODELS), overlay.byIndex().keys)
    }

    @Test
    fun `is empty without the folder`(@TempDir dataDir: Path) {
        assertEquals(0, CacheOverlay.load(dataDir).size)
        Files.createDirectories(dataDir.resolve("models"))
        assertEquals(0, CacheOverlay.load(dataDir).size)
    }

    @Test
    fun `names a file that isn't a model the client reads`(@TempDir dataDir: Path) {
        val models = Files.createDirectories(dataDir.resolve("models"))
        Files.writeString(models.resolve("65000.dat"), "v 0 0 0\nf 1 2 3\n")
        val refused = assertFailsWith<IllegalArgumentException> { CacheOverlay.load(dataDir) }
        assertContains(refused.message!!, "65000.dat is not a model the client reads")
    }

    @Test
    fun `refuses an id a JS5 request can't carry`(@TempDir dataDir: Path) {
        val models = Files.createDirectories(dataDir.resolve("models"))
        Files.write(models.resolve("65536.dat"), model(1))
        val refused = assertFailsWith<IllegalArgumentException> { CacheOverlay.load(dataDir) }
        assertContains(refused.message!!, "below 65536")
    }

    @Test
    fun `refuses an id too long for a number, saying why`(@TempDir dataDir: Path) {
        val models = Files.createDirectories(dataDir.resolve("models"))
        Files.write(models.resolve("12345678901.dat"), model(1))
        val refused = assertFailsWith<IllegalArgumentException> { CacheOverlay.load(dataDir) }
        assertContains(refused.message!!, "below 65536")
    }
}
