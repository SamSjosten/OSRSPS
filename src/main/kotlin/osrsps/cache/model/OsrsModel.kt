package osrsps.cache.model

/**
 * A model as the 241 client reads it from the models index (7): vertices in 1/128ths of a tile with y growing
 * downwards (so a point above the ground has a negative y), and triangles with a packed HSL colour each. The optional
 * per-face and per-vertex arrays are null when the model doesn't carry that section; their meaning follows RuneLite's
 * ModelDefinition, which the client's own loader matches.
 *
 * Only texture triangles of render type 0 (three vertex indices mapping the texture) are held: the other types exist in
 * the format but no model the server makes needs them, and [ModelCodec] refuses to read them.
 */
class OsrsModel(
    val vertexX: IntArray,
    val vertexY: IntArray,
    val vertexZ: IntArray,
    val faceA: IntArray,
    val faceB: IntArray,
    val faceC: IntArray,
    /** Packed HSL (6 bits hue, 3 saturation, 7 luminance), see [HslPalette]. */
    val faceColors: IntArray,
    /** 0 shaded, 1 flat; null means shaded throughout. */
    val faceRenderTypes: ByteArray? = null,
    /** Depth-sort priority a face, 0 to 10; null means every face has [priority]. */
    val facePriorities: ByteArray? = null,
    val priority: Int = 0,
    /** 0 opaque to 254 almost invisible; null means opaque throughout. */
    val faceAlphas: ByteArray? = null,
    /** Animation group a face (the format's "face skins"), 0 to 255; null when the model has none. */
    val faceSkins: IntArray? = null,
    /** Texture id a face, -1 for none; null when no face is textured. */
    val faceTextures: IntArray? = null,
    /** Index into the texture triangles a textured face, -1 for none; null when the model has no texture triangles. */
    val textureCoords: IntArray? = null,
    /** Animation group a vertex, 0 to 255; null when the model has none. */
    val vertexSkins: IntArray? = null,
    /** Animaya (skeletal) bone groups and weights a vertex; null when the model has none. */
    val animayaGroups: Array<IntArray>? = null,
    val animayaScales: Array<IntArray>? = null,
    /** Texture triangles of render type 0: the vertices that map the texture's corners. */
    val textureA: IntArray = IntArray(0),
    val textureB: IntArray = IntArray(0),
    val textureC: IntArray = IntArray(0),
    /** Per-face depth offsets; null when the model has none. */
    val faceZOffsets: ByteArray? = null,
    /** The 11 bytes of the unexplained block before the offsets, when a model carries one (first byte not 0). */
    val extra: ByteArray? = null,
) {
    val vertexCount: Int get() = vertexX.size
    val faceCount: Int get() = faceA.size

    init {
        require(vertexY.size == vertexCount && vertexZ.size == vertexCount) { "Vertex arrays differ in length" }
        require(faceB.size == faceCount && faceC.size == faceCount && faceColors.size == faceCount) { "Face arrays differ in length" }
        require(vertexCount <= MAX_VERTICES) { "$vertexCount vertices; the format holds at most $MAX_VERTICES" }
        require(faceCount <= MAX_FACES) { "$faceCount faces; the format holds at most $MAX_FACES" }
        require(textureB.size == textureA.size && textureC.size == textureA.size) { "Texture triangle arrays differ in length" }
        require(textureA.size <= MAX_TEXTURE_TRIANGLES) { "${textureA.size} texture triangles; the format holds at most $MAX_TEXTURE_TRIANGLES" }
        for (name in listOf("faceRenderTypes" to faceRenderTypes?.size, "facePriorities" to facePriorities?.size, "faceAlphas" to faceAlphas?.size, "faceZOffsets" to faceZOffsets?.size)) {
            require(name.second == null || name.second == faceCount) { "${name.first} has ${name.second} entries for $faceCount faces" }
        }
        for (name in listOf("faceSkins" to faceSkins?.size, "faceTextures" to faceTextures?.size, "textureCoords" to textureCoords?.size)) {
            require(name.second == null || name.second == faceCount) { "${name.first} has ${name.second} entries for $faceCount faces" }
        }
        require(vertexSkins == null || vertexSkins.size == vertexCount) { "vertexSkins has ${vertexSkins?.size} entries for $vertexCount vertices" }
        require((animayaGroups == null) == (animayaScales == null)) { "Animaya groups and scales go together" }
        require(animayaGroups == null || (animayaGroups.size == vertexCount && animayaScales!!.size == vertexCount)) { "Animaya arrays differ from the vertex count" }
        require(textureCoords == null || (faceTextures != null && textureA.isNotEmpty())) { "Texture coordinates without face textures and texture triangles" }
        require(priority in 0..254) { "Priority $priority is out of range" }
        require(extra == null || (extra.size == 11 && extra[0].toInt() != 0)) { "The extra block is 11 bytes starting with a non-zero byte" }
        for (i in 0 until faceCount) {
            require(faceA[i] in 0 until vertexCount && faceB[i] in 0 until vertexCount && faceC[i] in 0 until vertexCount) { "Face $i points outside the vertices" }
            require(faceColors[i] in 0..0xFFFF) { "Face $i's colour ${faceColors[i]} is not a packed HSL value" }
        }
    }

    /** The lowest y (the highest point, as y grows downwards) and the highest y. */
    val minY: Int get() = vertexY.minOrNull() ?: 0
    val maxY: Int get() = vertexY.maxOrNull() ?: 0

    companion object {
        const val MAX_VERTICES = 65535
        const val MAX_FACES = 65535
        const val MAX_TEXTURE_TRIANGLES = 255
    }
}
