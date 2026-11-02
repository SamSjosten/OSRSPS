package osrsps.cache.overlay

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir
import osrsps.cache.Sprite
import osrsps.cache.SpriteCodec
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

    private fun sprite(colour: Int) = SpriteCodec.encode(Sprite(2, 2, IntArray(4) { 0xFF000000.toInt() or colour }))

    @Test
    fun `loads the sprites in data sprites after the models`(@TempDir dataDir: Path) {
        Files.write(Files.createDirectories(dataDir.resolve("models")).resolve("65000.dat"), model(1))
        val sprites = Files.createDirectories(dataDir.resolve("sprites"))
        Files.write(sprites.resolve("65003.dat"), sprite(0x123456))
        Files.write(sprites.resolve("65000.dat"), sprite(0x654321))
        Files.writeString(sprites.resolve("map.png"), "not read")
        val overlay = CacheOverlay.load(dataDir)
        assertEquals("7/65000, 8/65000, 8/65003", overlay.toString())
        assertContentEquals(sprite(0x654321), overlay.groups[1].data)
        assertEquals(listOf(0), overlay.groups[1].files)
        assertEquals(setOf(CacheOverlay.MODELS, CacheOverlay.SPRITES), overlay.byIndex().keys)
    }

    @Test
    fun `names a file that isn't a sprite the client reads`(@TempDir dataDir: Path) {
        Files.write(Files.createDirectories(dataDir.resolve("sprites")).resolve("65003.dat"), model(1))
        val refused = assertFailsWith<IllegalArgumentException> { CacheOverlay.load(dataDir) }
        assertContains(refused.message!!, "65003.dat is not a sprite the client reads")
    }

    @Test
    fun `a group from data keeps its place over one built in code`(@TempDir dataDir: Path) {
        Files.write(Files.createDirectories(dataDir.resolve("sprites")).resolve("65003.dat"), sprite(0x123456))
        val loaded = CacheOverlay.load(dataDir)
        val merged = loaded.withGroups(listOf(CacheOverlay.Group(8, 65003, sprite(0x654321)), CacheOverlay.Group(8, 65000, sprite(1)), CacheOverlay.Group(3, 30000, byteArrayOf(1))))
        assertEquals("8/65003, 8/65000, 3/30000", merged.toString())
        assertContentEquals(sprite(0x123456), merged.groups[0].data, "the file's")
        assertEquals(0, loaded.withGroups(emptyList()).groups.size - loaded.groups.size)
    }

    @Test
    fun `a group's files are listed in order, none twice`() {
        CacheOverlay.Group(3, 30000, byteArrayOf(1), listOf(0, 1, 5))
        assertFailsWith<IllegalArgumentException> { CacheOverlay.Group(3, 30000, byteArrayOf(1), listOf(1, 0)) }
        assertFailsWith<IllegalArgumentException> { CacheOverlay.Group(3, 30000, byteArrayOf(1), listOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { CacheOverlay.Group(3, 30000, byteArrayOf(1), emptyList()) }
    }
}
