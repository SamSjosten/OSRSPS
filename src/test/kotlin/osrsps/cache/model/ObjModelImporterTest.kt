package osrsps.cache.model

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ObjModelImporterTest {
    /** A 2 by 2 texture: red top-left, green top-right, blue bottom-left, white bottom-right. */
    private fun texture(): BufferedImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB).apply {
        setRGB(0, 0, 0xFF0000)
        setRGB(1, 0, 0x00FF00)
        setRGB(0, 1, 0x0000FF)
        setRGB(1, 1, 0xFFFFFF)
    }

    private val mtl = """
        newmtl stone
        Kd 0.5 0.5 0.5
        newmtl painted
        Kd 1 1 1
        map_Kd tex.png
    """.trimIndent()

    /** A quad in the RS3 rip's units, 512 a tile, 1 unit above the ground, split into two triangles by material. */
    private val obj = """
        # comment
        mtllib x.mtl
        o quad
        v 0 1 0
        v 512 1 0
        v 512 1 512
        v 0 1 512
        vt 0 0
        vt 1 0
        vt 1 1
        vt 0 1
        usemtl stone
        f 1/1 2/2 3/3
        usemtl painted
        f 1/1 3/3 4/4
    """.trimIndent()

    @Test
    fun `scales, turns y over, shares vertices and colours faces from material and texture`() {
        val asked = ArrayList<String>()
        val result = ObjModelImporter.import(obj, mtl, ObjModelImporter.Options(scale = 0.25)) { name -> asked += name; if (name == "tex.png") texture() else null }
        val model = result.model
        assertEquals(listOf("tex.png"), asked, "the painted material's texture is read once")
        assertEquals(4, model.vertexCount)
        assertEquals(2, model.faceCount)
        assertContentEquals(intArrayOf(0, 128, 128, 0), model.vertexX)
        assertContentEquals(intArrayOf(0, 0, 0, 0), model.vertexY) // 1 unit up, a quarter of a client unit, rounds to 0
        assertContentEquals(intArrayOf(0, 0, 128, 128), model.vertexZ)
        // y turned over reverses the winding, so b and c swap.
        assertEquals(listOf(0, 2, 1), listOf(model.faceA[0], model.faceB[0], model.faceC[0]))
        assertEquals(listOf(0, 3, 2), listOf(model.faceA[1], model.faceB[1], model.faceC[1]))
        // Grey from Kd.
        assertEquals(HslPalette.nearest(0x808080), model.faceColors[0])
        assertEquals(0, HslPalette.saturation(model.faceColors[0]))
        // The painted face's corners are at uv (0,0), (1,1) and (0,1): the blue, green and red texels; its centre
        // (1/3, 2/3) is in the red texel again. The mean of blue, green, red and red is (127, 63, 63).
        assertEquals(HslPalette.nearest(0x7F3F3F), model.faceColors[1])
        assertNotEquals(HslPalette.nearest(0xFFFFFF), model.faceColors[1], "not the material's white")
        assertEquals(listOf("stone", "painted"), result.materials)
        assertEquals(0, result.droppedFaces)
        assertNull(model.faceAlphas)
        assertEquals(0, model.priority)
    }

    @Test
    fun `vertex colours multiply the material colour`() {
        val coloured = obj.replace("v 0 1 0", "v 0 1 0 0 0 1").replace("v 512 1 0", "v 512 1 0 0 0 1").replace("v 512 1 512", "v 512 1 512 0 0 1")
        val model = ObjModelImporter.import(coloured, "newmtl stone\nKd 1 1 1\nnewmtl painted\nKd 1 1 1").model
        val rgb = HslPalette.rgb[model.faceColors[0]]
        assertTrue((rgb and 255) > (rgb shr 16 and 255) + 100 && (rgb and 255) > (rgb shr 8 and 255) + 100, "not blue: ${rgb.toString(16)}")
    }

    @Test
    fun `mirroring x, flipping faces, alphas and priority`() {
        val options = ObjModelImporter.Options(scale = 0.25, mirrorX = true, flipFaces = true, alphas = mapOf("painted" to 100), priority = 7)
        val model = ObjModelImporter.import(obj, mtl, options).model
        assertContentEquals(intArrayOf(0, -128, -128, 0), model.vertexX)
        // Mirroring x undoes the reversal that turning y over brings, and flipping reverses once more.
        assertEquals(listOf(0, 2, 1), listOf(model.faceA[0], model.faceB[0], model.faceC[0]))
        val mirroredOnly = ObjModelImporter.import(obj, mtl, ObjModelImporter.Options(scale = 0.25, mirrorX = true)).model
        assertEquals(listOf(0, 1, 2), listOf(mirroredOnly.faceA[0], mirroredOnly.faceB[0], mirroredOnly.faceC[0]))
        assertContentEquals(byteArrayOf(0, 100), model.faceAlphas)
        assertEquals(7, model.priority)
    }

    @Test
    fun `a polygon fans into triangles and a collapsed face is dropped`() {
        val quad = "v 0 0 0\nv 10 0 0\nv 10 0 10\nv 0 0 10\nv 0 0 10.2\nf 1 2 3 4\nf 4 5 1"
        val result = ObjModelImporter.import(quad)
        assertEquals(2, result.model.faceCount) // the quad's two; the sliver's vertices 4 and 5 round together
        assertEquals(1, result.droppedFaces)
        assertEquals(4, result.model.vertexCount)
    }

    @Test
    fun `negative indices count back and a bad face fails`() {
        val model = ObjModelImporter.import("v 0 0 0\nv 1 0 0\nv 0 0 1\nf -3 -2 -1").model
        assertEquals(1, model.faceCount)
        assertFailsWith<IllegalArgumentException> { ObjModelImporter.import("v 0 0 0\nf 1 2 3") }
        assertFailsWith<IllegalArgumentException> { ObjModelImporter.import("v 0 0 0\n") }
    }

    @Test
    fun `importFile finds a material library next to the OBJ and its textures next to the library`(@TempDir dir: Path) {
        val materials = Files.createDirectories(dir.resolve("materials"))
        ImageIO.write(texture(), "png", materials.resolve("tex.png").toFile())
        Files.writeString(materials.resolve("stone.mtl"), mtl)
        Files.writeString(dir.resolve("quad.obj"), obj.replace("mtllib x.mtl", "mtllib materials/stone.mtl"))
        val model = ObjModelImporter.importFile(dir.resolve("quad.obj"), ObjModelImporter.Options(scale = 0.25)).model
        assertEquals(HslPalette.nearest(0x808080), model.faceColors[0])
        assertEquals(HslPalette.nearest(0x7F3F3F), model.faceColors[1], "the texture was found and sampled")
    }

    @Test
    fun `the imported model encodes`() {
        val model = ObjModelImporter.import(obj, mtl, ObjModelImporter.Options(scale = 0.25)).model
        val again = ModelCodec.decode(ModelCodec.encode(model))
        assertContentEquals(model.faceColors, again.faceColors)
        assertContentEquals(model.vertexX, again.vertexX)
    }
}
