package osrsps.cache.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelCodecTest {
    /** Two triangles sharing an edge, with every optional section filled. */
    private fun fullModel() = OsrsModel(
        vertexX = intArrayOf(0, 128, 128, 0),
        vertexY = intArrayOf(0, 0, -200, -16500),
        vertexZ = intArrayOf(0, 0, 128, 128),
        faceA = intArrayOf(0, 2),
        faceB = intArrayOf(1, 3),
        faceC = intArrayOf(2, 0),
        faceColors = intArrayOf(0x1234, 0xFFFF),
        faceRenderTypes = byteArrayOf(0, 1),
        facePriorities = byteArrayOf(3, 10),
        faceAlphas = byteArrayOf(0, 200.toByte()),
        faceSkins = intArrayOf(5, 255),
        faceTextures = intArrayOf(-1, 40),
        textureCoords = intArrayOf(-1, 0),
        vertexSkins = intArrayOf(0, 1, 2, 255),
        animayaGroups = arrayOf(intArrayOf(), intArrayOf(1), intArrayOf(2, 3), intArrayOf()),
        animayaScales = arrayOf(intArrayOf(), intArrayOf(255), intArrayOf(10, 20), intArrayOf()),
        textureA = intArrayOf(2),
        textureB = intArrayOf(3),
        textureC = intArrayOf(0),
        faceZOffsets = byteArrayOf(-1, 7),
        extra = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0, 0, 0, 5),
    )

    private fun plainModel() = OsrsModel(
        vertexX = intArrayOf(0, 100, 0),
        vertexY = intArrayOf(0, 0, -50),
        vertexZ = intArrayOf(0, 0, 100),
        faceA = intArrayOf(0),
        faceB = intArrayOf(1),
        faceC = intArrayOf(2),
        faceColors = intArrayOf(0x7F),
        priority = 4,
    )

    private fun assertSame(expected: OsrsModel, actual: OsrsModel) {
        assertContentEquals(expected.vertexX, actual.vertexX)
        assertContentEquals(expected.vertexY, actual.vertexY)
        assertContentEquals(expected.vertexZ, actual.vertexZ)
        assertContentEquals(expected.faceA, actual.faceA)
        assertContentEquals(expected.faceB, actual.faceB)
        assertContentEquals(expected.faceC, actual.faceC)
        assertContentEquals(expected.faceColors, actual.faceColors)
        assertContentEquals(expected.faceRenderTypes, actual.faceRenderTypes)
        assertContentEquals(expected.facePriorities, actual.facePriorities)
        assertEquals(expected.priority, actual.priority)
        assertContentEquals(expected.faceAlphas, actual.faceAlphas)
        assertContentEquals(expected.faceSkins, actual.faceSkins)
        assertContentEquals(expected.faceTextures, actual.faceTextures)
        assertContentEquals(expected.textureCoords, actual.textureCoords)
        assertContentEquals(expected.vertexSkins, actual.vertexSkins)
        assertEquals(expected.animayaGroups?.map { it.toList() }, actual.animayaGroups?.map { it.toList() })
        assertEquals(expected.animayaScales?.map { it.toList() }, actual.animayaScales?.map { it.toList() })
        assertContentEquals(expected.textureA, actual.textureA)
        assertContentEquals(expected.textureB, actual.textureB)
        assertContentEquals(expected.textureC, actual.textureC)
        assertContentEquals(expected.faceZOffsets, actual.faceZOffsets)
        assertContentEquals(expected.extra, actual.extra)
    }

    @Test
    fun `a model with every section survives encoding and decoding`() {
        val model = fullModel()
        val bytes = ModelCodec.encode(model)
        assertTrue(ModelCodec.isType3(bytes))
        assertSame(model, ModelCodec.decode(bytes))
    }

    @Test
    fun `a plain model keeps its single priority and no optional sections`() {
        val model = plainModel()
        val decoded = ModelCodec.decode(ModelCodec.encode(model))
        assertSame(model, decoded)
        assertNull(decoded.faceAlphas)
        assertEquals(4, decoded.priority)
    }

    @Test
    fun `the footer is the last 26 bytes and names the counts`() {
        val bytes = ModelCodec.encode(plainModel())
        val footer = bytes.copyOfRange(bytes.size - 26, bytes.size)
        assertEquals(listOf(0, 3, 0, 1), footer.take(4).map { it.toInt() and 0xFF }) // 3 vertices, 1 face
        assertEquals(0, footer[4].toInt()) // no texture triangles
        assertEquals(4, footer[6].toInt() and 0xFF) // the single priority
        assertEquals(listOf(0xFF, 0xFD), footer.takeLast(2).map { it.toInt() and 0xFF })
    }

    @Test
    fun `faces that reuse the last face's vertices take the short index types`() {
        // Face 1 keeps A and takes the last C as B (type 2); face 2 takes the last C as A, keeps B (type 3);
        // face 3 swaps A and B (type 4).
        val model = OsrsModel(
            vertexX = IntArray(6) { it * 10 }, vertexY = IntArray(6), vertexZ = IntArray(6) { it },
            faceA = intArrayOf(0, 0, 3, 2),
            faceB = intArrayOf(1, 2, 2, 3),
            faceC = intArrayOf(2, 3, 4, 5),
            faceColors = IntArray(4),
        )
        val bytes = ModelCodec.encode(model)
        assertSame(model, ModelCodec.decode(bytes))
        // Type bytes sit right after the 6 vertex flags (no texture render types, no face render types).
        assertEquals(listOf(1, 2, 3, 4), bytes.copyOfRange(6, 10).map { it.toInt() })
        // 3 smart bytes for the first face and 1 for each of the others.
        assertEquals(6, (bytes[bytes.size - 26 + 18].toInt() shl 8) or (bytes[bytes.size - 26 + 19].toInt() and 0xFF))
    }

    @Test
    fun `big moves take two bytes and moves beyond the format are refused`() {
        val wide = OsrsModel(
            vertexX = intArrayOf(0, 16383, -1), vertexY = intArrayOf(0, 0, 0), vertexZ = intArrayOf(0, 64, 0),
            faceA = intArrayOf(0), faceB = intArrayOf(1), faceC = intArrayOf(2), faceColors = intArrayOf(1),
        )
        assertSame(wide, ModelCodec.decode(ModelCodec.encode(wide)))
        val tooWide = OsrsModel(
            vertexX = intArrayOf(0, 16384, 0), vertexY = intArrayOf(0, 0, 0), vertexZ = intArrayOf(0, 0, 0),
            faceA = intArrayOf(0), faceB = intArrayOf(1), faceC = intArrayOf(2), faceColors = intArrayOf(1),
        )
        assertFailsWith<IllegalArgumentException> { ModelCodec.encode(tooWide) }
    }

    @Test
    fun `the older format and other texture types are refused, not misread`() {
        assertFailsWith<ModelCodec.UnsupportedModelException> { ModelCodec.decode(ByteArray(40)) }
        val bytes = ModelCodec.encode(fullModel())
        bytes[0] = 1 // the one texture triangle's render type
        assertFailsWith<ModelCodec.UnsupportedModelException> { ModelCodec.decode(bytes) }
    }

    @Test
    fun `a model's arrays have to agree`() {
        assertFailsWith<IllegalArgumentException> {
            OsrsModel(intArrayOf(0), intArrayOf(0), intArrayOf(0), intArrayOf(0), intArrayOf(0), intArrayOf(1), intArrayOf(0))
        }
        assertFailsWith<IllegalArgumentException> {
            OsrsModel(intArrayOf(0, 1, 2), intArrayOf(0, 0, 0), intArrayOf(0, 0, 0), intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(0x10000))
        }
    }
}
