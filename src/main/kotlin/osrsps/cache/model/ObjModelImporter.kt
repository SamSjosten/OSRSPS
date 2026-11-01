package osrsps.cache.model

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Turns a Wavefront OBJ (as Blender or the RS3 rip writes it: positions with optional vertex colours, texture
 * coordinates, faces grouped by `usemtl`, an MTL naming each material's colour or texture image) into an [OsrsModel].
 *
 * The client has no textures of its own we can map onto an imported model yet, so every face gets one flat colour:
 * the material's colour, or its texture sampled at the face's corners and centre, times the vertex colours, snapped to
 * the nearest colour of the client's palette. Positions are scaled by [Options.scale] and y is turned over, as the
 * client's y grows downwards; vertices that land on the same point are shared.
 *
 * Inferred: the OBJ's handedness matches the client's once y is turned over, so faces keep their visible side when
 * their winding is reversed along with it. [Options.flipFaces] and [Options.mirrorX] are there for when a model shows
 * inside out or mirrored in the game.
 */
object ObjModelImporter {
    class Options(
        /** Units in the OBJ per client unit; an RS3 model (512 a tile) into the client's 128 a tile is 0.25. */
        val scale: Double = 1.0,
        val mirrorX: Boolean = false,
        val flipFaces: Boolean = false,
        /** Transparency by material name, 0 opaque to 254. */
        val alphas: Map<String, Int> = emptyMap(),
        /** Render priority of every face, 0 to 10. */
        val priority: Int = 0,
    )

    class Material(val name: String, var color: Int = 0xFFFFFF, var texture: String? = null)

    class Result(val model: OsrsModel, val materials: List<String>, val droppedFaces: Int)

    /** Material libraries are found next to the OBJ, and each library's textures next to that library. */
    fun importFile(obj: Path, options: Options = Options()): Result {
        val text = Files.readString(obj)
        val dir = obj.toAbsolutePath().parent
        val materials = LinkedHashMap<String, Material>()
        for (raw in text.lineSequence()) {
            val parts = raw.substringBefore('#').trim().split(Regex("\\s+"))
            if (parts[0] != "mtllib" || parts.size < 2) continue
            val library = dir.resolve(parts.drop(1).joinToString(" ")).takeIf(Files::exists) ?: continue
            for (material in parseMtl(Files.readString(library)).values) {
                material.texture = material.texture?.let { library.toAbsolutePath().parent.resolve(it).toString() }
                materials[material.name] = material
            }
        }
        return import(text, materials, options) { name -> Path.of(name).takeIf(Files::exists)?.let { ImageIO.read(it.toFile()) } }
    }

    fun import(obj: String, mtl: String = "", options: Options = Options(), textures: (String) -> BufferedImage? = { null }): Result =
        import(obj, parseMtl(mtl), options, textures)

    /** [textures] is asked for each material's texture name, as its `map_Kd` says it or as [importFile] resolved it. */
    fun import(obj: String, materials: Map<String, Material>, options: Options, textures: (String) -> BufferedImage?): Result {
        val images = HashMap<String, BufferedImage?>()
        fun image(name: String) = images.getOrPut(name) { textures(name) }

        val positions = ArrayList<DoubleArray>()
        val colors = ArrayList<IntArray?>()
        val uvs = ArrayList<DoubleArray>()
        val pointIndex = HashMap<Long, Int>()
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        val zs = ArrayList<Int>()
        val faceA = ArrayList<Int>()
        val faceB = ArrayList<Int>()
        val faceC = ArrayList<Int>()
        val faceColors = ArrayList<Int>()
        val faceAlphas = ArrayList<Int>()
        val used = LinkedHashSet<String>()
        var material: Material? = null
        var dropped = 0
        val palette = HashMap<Int, Int>()
        // Turning y over reverses the winding; mirroring x reverses it back; flipping reverses it once more.
        val reverse = options.mirrorX == options.flipFaces

        fun clientVertex(p: DoubleArray): Int {
            val x = (p[0] * options.scale * if (options.mirrorX) -1 else 1).roundToInt()
            val y = (-p[1] * options.scale).roundToInt()
            val z = (p[2] * options.scale).roundToInt()
            val key = (x.toLong() and 0xFFFFF) or ((y.toLong() and 0xFFFFF) shl 20) or ((z.toLong() and 0xFFFFF) shl 40)
            return pointIndex.getOrPut(key) {
                xs += x
                ys += y
                zs += z
                xs.size - 1
            }
        }

        for (raw in obj.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> {
                    require(parts.size >= 4) { "Bad vertex line: $raw" }
                    positions += doubleArrayOf(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
                    colors += if (parts.size >= 7) intArrayOf(channel(parts[4]), channel(parts[5]), channel(parts[6])) else null
                }
                "vt" -> {
                    require(parts.size >= 3) { "Bad texture coordinate line: $raw" }
                    uvs += doubleArrayOf(parts[1].toDouble(), parts[2].toDouble())
                }
                "usemtl" -> {
                    val name = parts.drop(1).joinToString(" ")
                    material = materials[name] ?: Material(name)
                    used += name
                }
                "f" -> {
                    require(parts.size >= 4) { "Bad face line: $raw" }
                    val corners = parts.drop(1).map { corner ->
                        val fields = corner.split('/')
                        val v = objIndex(fields[0], positions.size)
                        val vt = fields.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { objIndex(it, uvs.size) }
                        v to vt
                    }
                    val rgb = faceColor(corners, positions, colors, uvs, material, ::image)
                    val hsl = palette.getOrPut(rgb) { HslPalette.nearest(rgb) }
                    val alpha = material?.let { options.alphas[it.name] } ?: 0
                    // A polygon becomes a fan of triangles from its first corner.
                    for (i in 1 until corners.size - 1) {
                        val a = clientVertex(positions[corners[0].first])
                        var b = clientVertex(positions[corners[i].first])
                        var c = clientVertex(positions[corners[i + 1].first])
                        if (a == b || b == c || a == c) { dropped++; continue }
                        if (reverse) { val swap = b; b = c; c = swap }
                        faceA += a
                        faceB += b
                        faceC += c
                        faceColors += hsl
                        faceAlphas += alpha
                    }
                }
                else -> {} // o, g, s, vn, mtllib and anything else say nothing the client needs
            }
        }
        require(faceA.isNotEmpty()) { "The OBJ has no faces" }
        require(options.priority in 0..10) { "Priority ${options.priority} is not 0 to 10" }
        val model = OsrsModel(
            xs.toIntArray(), ys.toIntArray(), zs.toIntArray(),
            faceA.toIntArray(), faceB.toIntArray(), faceC.toIntArray(), faceColors.toIntArray(),
            priority = options.priority,
            faceAlphas = if (faceAlphas.any { it != 0 }) ByteArray(faceAlphas.size) { faceAlphas[it].toByte() } else null,
        )
        return Result(model, used.toList(), dropped)
    }

    /** Parses the `newmtl`, `Kd` and `map_Kd` lines of an MTL. */
    fun parseMtl(mtl: String): Map<String, Material> {
        val materials = LinkedHashMap<String, Material>()
        var current: Material? = null
        for (raw in mtl.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "newmtl" -> current = Material(parts.drop(1).joinToString(" ")).also { materials[it.name] = it }
                "Kd" -> current?.color = (channel(parts[1]) shl 16) or (channel(parts[2]) shl 8) or channel(parts[3])
                "map_Kd" -> current?.texture = mapFile(parts.drop(1))
            }
        }
        return materials
    }

    /**
     * The file a `map_Kd` names: everything after its options (`-o 0 0 0`, `-clamp on`, each a flag with its numbers
     * or on/off), with the spaces a name can have kept. The options themselves say nothing a flat colour needs.
     */
    private fun mapFile(args: List<String>): String? {
        var i = 0
        while (i < args.size && args[i].startsWith("-")) {
            i++
            while (i < args.size && (args[i].toDoubleOrNull() != null || args[i] == "on" || args[i] == "off")) i++
        }
        return args.drop(i).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    private fun channel(value: String): Int = (value.toDouble().coerceIn(0.0, 1.0) * 255.0).roundToInt()

    private fun objIndex(field: String, count: Int): Int {
        val i = field.toInt()
        val index = if (i < 0) count + i else i - 1
        require(index in 0 until count) { "Index $field is outside the $count entries so far" }
        return index
    }

    /** The face's colour: the material's, or its texture sampled at each corner and at the centre, times the vertex colours. */
    private fun faceColor(
        corners: List<Pair<Int, Int?>>,
        positions: List<DoubleArray>,
        colors: List<IntArray?>,
        uvs: List<DoubleArray>,
        material: Material?,
        image: (String) -> BufferedImage?,
    ): Int {
        val texture = material?.texture?.let(image)
        val samples = ArrayList<IntArray>()
        val vertexColors = corners.map { colors[it.first] ?: intArrayOf(255, 255, 255) }
        val meanVertex = IntArray(3) { ch -> vertexColors.sumOf { it[ch] } / vertexColors.size }
        if (texture != null && corners.all { it.second != null }) {
            val cornerUvs = corners.map { uvs[it.second!!] }
            for ((i, uv) in cornerUvs.withIndex()) samples += multiply(sample(texture, uv[0], uv[1]), vertexColors[i])
            val centre = doubleArrayOf(cornerUvs.sumOf { it[0] } / cornerUvs.size, cornerUvs.sumOf { it[1] } / cornerUvs.size)
            samples += multiply(sample(texture, centre[0], centre[1]), meanVertex)
        } else {
            val base = material?.color ?: 0xFFFFFF
            samples += multiply(intArrayOf(base shr 16 and 255, base shr 8 and 255, base and 255), meanVertex)
        }
        val r = samples.sumOf { it[0] } / samples.size
        val g = samples.sumOf { it[1] } / samples.size
        val b = samples.sumOf { it[2] } / samples.size
        return (r shl 16) or (g shl 8) or b
    }

    private fun multiply(a: IntArray, b: IntArray): IntArray = IntArray(3) { a[it] * b[it] / 255 }

    /** The texture's pixel at ([u], [v]), v upwards as in an OBJ; 1 is the far edge, and past 0 to 1 the texture repeats. */
    private fun sample(image: BufferedImage, u: Double, v: Double): IntArray {
        val fu = if (u in 0.0..1.0) u else u - floor(u)
        val fv = if (v in 0.0..1.0) v else v - floor(v)
        val x = (fu * image.width).toInt().coerceIn(0, image.width - 1)
        val y = ((1 - fv) * image.height).toInt().coerceIn(0, image.height - 1)
        val rgb = image.getRGB(x, y)
        return intArrayOf(rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255)
    }
}
