package osrsps.cache.model

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.audit.FlatCache
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Round-trips every live model of the newest format through the codec: decode, encode, decode again and compare,
 * and count how many come back byte for byte. Needs a clone of github.com/abextm/osrs-cache (7.flatcache) in
 * OSRSPS_FLATCACHE. Not in CI. The counts are the rev 241 cache's (tag 2026-10-14-rev241).
 */
@EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
class ModelCodecLiveTest {
    @Test
    fun `every live model of the newest format survives the codec`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        var total = 0
        var newest = 0
        var textured = 0
        var identical = 0
        var tooWide = 0
        cache.groups(7) { id, files ->
            val data = files[0] ?: return@groups
            total++
            if (!ModelCodec.isType3(data)) return@groups
            newest++
            val model = try {
                ModelCodec.decode(data)
            } catch (e: ModelCodec.UnsupportedModelException) {
                textured++
                return@groups
            } catch (e: RuntimeException) {
                throw AssertionError("Model $id can't be decoded: ${e.message}", e)
            }
            val encoded = try {
                ModelCodec.encode(model)
            } catch (e: IllegalArgumentException) {
                tooWide++ // Guessed: none, as a move beyond 16383 can't be in a live model either; counted to be sure.
                throw AssertionError("Model $id can't be encoded: ${e.message}", e)
            }
            if (encoded.contentEquals(data)) identical++
            val again = ModelCodec.decode(encoded)
            assertContentEquals(model.vertexX, again.vertexX, "model $id x")
            assertContentEquals(model.vertexY, again.vertexY, "model $id y")
            assertContentEquals(model.vertexZ, again.vertexZ, "model $id z")
            assertContentEquals(model.faceA, again.faceA, "model $id faces")
            assertContentEquals(model.faceB, again.faceB, "model $id faces")
            assertContentEquals(model.faceC, again.faceC, "model $id faces")
            assertContentEquals(model.faceColors, again.faceColors, "model $id colours")
            assertContentEquals(model.faceRenderTypes, again.faceRenderTypes, "model $id render types")
            assertContentEquals(model.facePriorities, again.facePriorities, "model $id priorities")
            assertEquals(model.priority, again.priority, "model $id priority")
            assertContentEquals(model.faceAlphas, again.faceAlphas, "model $id alphas")
            assertContentEquals(model.faceSkins, again.faceSkins, "model $id face skins")
            assertContentEquals(model.faceTextures, again.faceTextures, "model $id textures")
            assertContentEquals(model.textureCoords, again.textureCoords, "model $id texture coords")
            assertContentEquals(model.vertexSkins, again.vertexSkins, "model $id vertex skins")
            assertEquals(model.animayaGroups?.map { it.toList() }, again.animayaGroups?.map { it.toList() }, "model $id animaya")
            assertEquals(model.animayaScales?.map { it.toList() }, again.animayaScales?.map { it.toList() }, "model $id animaya")
            assertContentEquals(model.textureA, again.textureA, "model $id texture triangles")
            assertContentEquals(model.textureB, again.textureB, "model $id texture triangles")
            assertContentEquals(model.textureC, again.textureC, "model $id texture triangles")
            assertContentEquals(model.faceZOffsets, again.faceZOffsets, "model $id z offsets")
            assertContentEquals(model.extra, again.extra, "model $id extra")
        }
        println("models: $total, newest format: $newest, of which textured with other types (skipped): $textured, byte-identical after re-encoding: $identical, too wide: $tooWide")
        assertEquals(62051, total)
        assertEquals(35124, newest)
        assertEquals(868, textured)
        // Every model the codec reads comes back byte for byte: the encoder makes the same choices as Jagex's.
        assertEquals(newest - textured, identical, "only $identical of ${newest - textured} came back byte for byte")
    }
}
