package osrsps.cache.model

import java.io.ByteArrayOutputStream

/**
 * Reads and writes the newest of the model formats the 241 client loads (the one ending in the bytes FF FD, RuneLite's
 * "type 3"), which 35,124 of the 62,051 live models use; the rest use the one before it, which this doesn't read. The
 * layout is a run of sections whose lengths the 26-byte footer gives, as in RuneLite's ModelLoader.decodeType3, which
 * [decode] follows step for step. [encode] writes the same sections in the same order and chooses the compact forms the
 * live models use (a vertex flag bit only for a delta that isn't 0, a face index type that reuses the last face's
 * vertices when it can), so a live model decoded and encoded again usually comes out byte for byte.
 */
object ModelCodec {
    /** Thrown for a model in a format this doesn't read: the older format, or texture triangles of types 1 to 3. */
    class UnsupportedModelException(message: String) : IllegalArgumentException(message)

    private const val FOOTER = 26
    private const val SMART_MIN = -16384
    private const val SMART_MAX = 16383

    fun isType3(data: ByteArray): Boolean = data.size >= FOOTER && data[data.size - 1] == (-3).toByte() && data[data.size - 2] == (-1).toByte()

    fun decode(data: ByteArray): OsrsModel {
        if (!isType3(data)) throw UnsupportedModelException("Not a model of the newest format (ends ${data.takeLast(2).map { it.toInt() and 0xFF }})")
        val footer = Reader(data, data.size - FOOTER)
        val vertexCount = footer.u16()
        val faceCount = footer.u16()
        val textureCount = footer.u8()
        val hasRenderTypes = footer.u8() == 1
        val priority = footer.u8()
        val hasAlphas = footer.u8() == 1
        val hasFaceSkins = footer.u8() == 1
        val hasTextures = footer.u8() == 1
        val hasVertexSkins = footer.u8() == 1
        val hasAnimaya = footer.u8() == 1
        val xLength = footer.u16()
        val yLength = footer.u16()
        val zLength = footer.u16()
        val faceIndexLength = footer.u16()
        val textureCoordLength = footer.u16()
        val vertexSkinLength = footer.u16()

        val textureRenderTypes = ByteArray(textureCount) { data[it] }
        if (textureRenderTypes.any { it.toInt() != 0 }) throw UnsupportedModelException("Texture triangles of a type other than 0")

        var offset = textureCount + vertexCount
        val renderTypesOffset = offset
        if (hasRenderTypes) offset += faceCount
        val faceTypesOffset = offset
        offset += faceCount
        val prioritiesOffset = offset
        if (priority == 255) offset += faceCount
        val faceSkinsOffset = offset
        if (hasFaceSkins) offset += faceCount
        val vertexSkinsOffset = offset
        offset += vertexSkinLength
        val alphasOffset = offset
        if (hasAlphas) offset += faceCount
        val faceIndexOffset = offset
        offset += faceIndexLength
        val textureIdsOffset = offset
        if (hasTextures) offset += faceCount * 2
        val textureCoordsOffset = offset
        offset += textureCoordLength
        val colorsOffset = offset
        offset += faceCount * 2
        val xOffset = offset
        offset += xLength
        val yOffset = offset
        offset += yLength
        val zOffset = offset
        offset += zLength
        val textureTrianglesOffset = offset
        offset += textureCount * 6
        val end = offset

        val vertexX = IntArray(vertexCount)
        val vertexY = IntArray(vertexCount)
        val vertexZ = IntArray(vertexCount)
        val flags = Reader(data, textureCount)
        val xs = Reader(data, xOffset)
        val ys = Reader(data, yOffset)
        val zs = Reader(data, zOffset)
        val skins = Reader(data, vertexSkinsOffset)
        val vertexSkins = if (hasVertexSkins) IntArray(vertexCount) else null
        var x = 0
        var y = 0
        var z = 0
        for (i in 0 until vertexCount) {
            val flag = flags.u8()
            if (flag and 1 != 0) x += xs.shortSmart()
            if (flag and 2 != 0) y += ys.shortSmart()
            if (flag and 4 != 0) z += zs.shortSmart()
            vertexX[i] = x
            vertexY[i] = y
            vertexZ[i] = z
            if (vertexSkins != null) vertexSkins[i] = skins.u8()
        }
        var animayaGroups: Array<IntArray>? = null
        var animayaScales: Array<IntArray>? = null
        if (hasAnimaya) {
            animayaGroups = Array(vertexCount) { IntArray(0) }
            animayaScales = Array(vertexCount) { IntArray(0) }
            for (i in 0 until vertexCount) {
                val count = skins.u8()
                animayaGroups[i] = IntArray(count)
                animayaScales[i] = IntArray(count)
                for (j in 0 until count) {
                    animayaGroups[i][j] = skins.u8()
                    animayaScales[i][j] = skins.u8()
                }
            }
        }
        check(skins.offset == vertexSkinsOffset + vertexSkinLength) { "Vertex skin section length ${skins.offset - vertexSkinsOffset} differs from the footer's $vertexSkinLength" }

        val faceColors = IntArray(faceCount)
        val faceRenderTypes = if (hasRenderTypes) ByteArray(faceCount) else null
        val facePriorities = if (priority == 255) ByteArray(faceCount) else null
        val faceAlphas = if (hasAlphas) ByteArray(faceCount) else null
        val faceSkins = if (hasFaceSkins) IntArray(faceCount) else null
        val faceTextures = if (hasTextures) IntArray(faceCount) else null
        val textureCoords = if (hasTextures && textureCount > 0) IntArray(faceCount) else null
        val colors = Reader(data, colorsOffset)
        val renderTypes = Reader(data, renderTypesOffset)
        val priorities = Reader(data, prioritiesOffset)
        val alphas = Reader(data, alphasOffset)
        val faceSkinReader = Reader(data, faceSkinsOffset)
        val textureIds = Reader(data, textureIdsOffset)
        val coords = Reader(data, textureCoordsOffset)
        for (i in 0 until faceCount) {
            faceColors[i] = colors.u16()
            if (faceRenderTypes != null) faceRenderTypes[i] = renderTypes.s8()
            if (facePriorities != null) facePriorities[i] = priorities.s8()
            if (faceAlphas != null) faceAlphas[i] = alphas.s8()
            if (faceSkins != null) faceSkins[i] = faceSkinReader.u8()
            if (faceTextures != null) faceTextures[i] = textureIds.u16() - 1
            if (textureCoords != null) textureCoords[i] = if (faceTextures!![i] != -1) coords.u8() - 1 else -1
        }
        check(coords.offset == textureCoordsOffset + textureCoordLength) { "Texture coordinate section length differs from the footer's $textureCoordLength" }

        val faceA = IntArray(faceCount)
        val faceB = IntArray(faceCount)
        val faceC = IntArray(faceCount)
        val indices = Reader(data, faceIndexOffset)
        val types = Reader(data, faceTypesOffset)
        var a = 0
        var b = 0
        var c = 0
        var last = 0
        for (i in 0 until faceCount) {
            when (val type = types.u8()) {
                1 -> {
                    a = indices.shortSmart() + last
                    b = indices.shortSmart() + a
                    c = indices.shortSmart() + b
                    last = c
                }
                2 -> {
                    b = c
                    c = indices.shortSmart() + last
                    last = c
                }
                3 -> {
                    a = c
                    c = indices.shortSmart() + last
                    last = c
                }
                4 -> {
                    val swap = a
                    a = b
                    b = swap
                    c = indices.shortSmart() + last
                    last = c
                }
                else -> throw IllegalArgumentException("Face $i has index type $type")
            }
            faceA[i] = a
            faceB[i] = b
            faceC[i] = c
        }
        check(indices.offset == faceIndexOffset + faceIndexLength) { "Face index section length differs from the footer's $faceIndexLength" }

        val textureA = IntArray(textureCount)
        val textureB = IntArray(textureCount)
        val textureC = IntArray(textureCount)
        val triangles = Reader(data, textureTrianglesOffset)
        for (i in 0 until textureCount) {
            textureA[i] = triangles.u16()
            textureB[i] = triangles.u16()
            textureC[i] = triangles.u16()
        }

        val tail = Reader(data, end)
        val extraFlag = tail.u8()
        // A non-zero flag byte is followed by three shorts and an int the client reads and ignores.
        val extra = if (extraFlag != 0) ByteArray(11).also { it[0] = extraFlag.toByte(); for (i in 1 until 11) it[i] = tail.s8() } else null
        val faceZOffsets = if (tail.u8() == 1) ByteArray(faceCount) { tail.s8() } else null
        check(tail.offset == data.size - FOOTER) { "${data.size - FOOTER - tail.offset} bytes left over before the footer" }

        return OsrsModel(
            vertexX, vertexY, vertexZ, faceA, faceB, faceC, faceColors,
            faceRenderTypes = faceRenderTypes,
            facePriorities = facePriorities,
            priority = if (priority == 255) 0 else priority,
            faceAlphas = faceAlphas,
            faceSkins = faceSkins,
            faceTextures = faceTextures,
            textureCoords = textureCoords,
            vertexSkins = vertexSkins,
            animayaGroups = animayaGroups,
            animayaScales = animayaScales,
            textureA = textureA,
            textureB = textureB,
            textureC = textureC,
            faceZOffsets = faceZOffsets,
            extra = extra,
        )
    }

    fun encode(model: OsrsModel): ByteArray {
        val vertexCount = model.vertexCount
        val faceCount = model.faceCount
        val textureCount = model.textureA.size

        // Vertices: a flag byte each saying which of x, y and z moved, and three streams of the moves.
        val flags = ByteArrayOutputStream(vertexCount)
        val xs = ByteArrayOutputStream()
        val ys = ByteArrayOutputStream()
        val zs = ByteArrayOutputStream()
        var x = 0
        var y = 0
        var z = 0
        for (i in 0 until vertexCount) {
            val dx = model.vertexX[i] - x
            val dy = model.vertexY[i] - y
            val dz = model.vertexZ[i] - z
            var flag = 0
            if (dx != 0) { flag = flag or 1; xs.shortSmart(dx, "vertex $i's x") }
            if (dy != 0) { flag = flag or 2; ys.shortSmart(dy, "vertex $i's y") }
            if (dz != 0) { flag = flag or 4; zs.shortSmart(dz, "vertex $i's z") }
            flags.write(flag)
            x = model.vertexX[i]
            y = model.vertexY[i]
            z = model.vertexZ[i]
        }

        // Faces: a type byte each, and the index stream it says how to read.
        val types = ByteArrayOutputStream(faceCount)
        val indices = ByteArrayOutputStream()
        var a = 0
        var b = 0
        var c = 0
        var last = 0
        for (i in 0 until faceCount) {
            val fa = model.faceA[i]
            val fb = model.faceB[i]
            val fc = model.faceC[i]
            when {
                i > 0 && fa == a && fb == c -> { types.write(2); indices.shortSmart(fc - last, "face $i") }
                i > 0 && fa == c && fb == b -> { types.write(3); indices.shortSmart(fc - last, "face $i") }
                i > 0 && fa == b && fb == a -> { types.write(4); indices.shortSmart(fc - last, "face $i") }
                else -> {
                    types.write(1)
                    indices.shortSmart(fa - last, "face $i")
                    indices.shortSmart(fb - fa, "face $i")
                    indices.shortSmart(fc - fb, "face $i")
                }
            }
            a = fa
            b = fb
            c = fc
            last = fc
        }

        val out = ByteArrayOutputStream()
        repeat(textureCount) { out.write(0) } // texture render types: all type 0
        out.write(flags.toByteArray())
        model.faceRenderTypes?.let { out.write(it) }
        out.write(types.toByteArray())
        model.facePriorities?.let { out.write(it) }
        model.faceSkins?.forEach { out.write(it.checkByte("a face skin")) }
        val vertexSkinStart = out.size()
        model.vertexSkins?.forEach { out.write(it.checkByte("a vertex skin")) }
        model.animayaGroups?.let { groups ->
            val scales = model.animayaScales!!
            for (i in 0 until vertexCount) {
                out.write(groups[i].size.checkByte("vertex $i's animaya count"))
                for (j in groups[i].indices) {
                    out.write(groups[i][j].checkByte("an animaya group"))
                    out.write(scales[i][j].checkByte("an animaya scale"))
                }
            }
        }
        val vertexSkinLength = out.size() - vertexSkinStart
        model.faceAlphas?.let { out.write(it) }
        val faceIndexBytes = indices.toByteArray()
        out.write(faceIndexBytes)
        var textureCoordLength = 0
        model.faceTextures?.let { textures ->
            for (i in 0 until faceCount) {
                require(textures[i] in -1..0xFFFE) { "Face $i's texture ${textures[i]} doesn't fit" }
                out.u16(textures[i] + 1)
            }
        }
        // The client reads a coordinate for every textured face once there are texture triangles, so a model
        // without coordinates gets the "none" value (-1, stored as 0) for each.
        if (textureCount > 0) model.faceTextures?.let { textures ->
            for (i in 0 until faceCount) {
                if (textures[i] == -1) continue
                val coord = model.textureCoords?.get(i) ?: -1
                require(coord in -1..254) { "Face $i's texture coordinate $coord doesn't fit" }
                out.write(coord + 1)
                textureCoordLength++
            }
        }
        for (i in 0 until faceCount) out.u16(model.faceColors[i])
        val xBytes = xs.toByteArray()
        val yBytes = ys.toByteArray()
        val zBytes = zs.toByteArray()
        out.write(xBytes)
        out.write(yBytes)
        out.write(zBytes)
        for (i in 0 until textureCount) {
            out.u16(model.textureA[i].checkVertex(model, "texture triangle $i"))
            out.u16(model.textureB[i].checkVertex(model, "texture triangle $i"))
            out.u16(model.textureC[i].checkVertex(model, "texture triangle $i"))
        }
        if (model.extra != null) out.write(model.extra) else out.write(0)
        if (model.faceZOffsets != null) {
            out.write(1)
            out.write(model.faceZOffsets)
        } else {
            out.write(0)
        }

        // The footer the client reads first.
        out.u16(vertexCount)
        out.u16(faceCount)
        out.write(textureCount)
        out.write(if (model.faceRenderTypes != null) 1 else 0)
        out.write(if (model.facePriorities != null) 255 else model.priority)
        out.write(if (model.faceAlphas != null) 1 else 0)
        out.write(if (model.faceSkins != null) 1 else 0)
        out.write(if (model.faceTextures != null) 1 else 0)
        out.write(if (model.vertexSkins != null) 1 else 0)
        out.write(if (model.animayaGroups != null) 1 else 0)
        out.u16(xBytes.size.checkLength("x deltas"))
        out.u16(yBytes.size.checkLength("y deltas"))
        out.u16(zBytes.size.checkLength("z deltas"))
        out.u16(faceIndexBytes.size.checkLength("face indices"))
        out.u16(textureCoordLength)
        out.u16(vertexSkinLength.checkLength("vertex skins"))
        out.write(0xFF)
        out.write(0xFD)
        return out.toByteArray()
    }

    private fun Int.checkByte(what: String): Int {
        require(this in 0..255) { "$what, $this, doesn't fit a byte" }
        return this
    }

    private fun Int.checkLength(what: String): Int {
        require(this <= 0xFFFF) { "The $what section is $this bytes; the format holds at most 65535" }
        return this
    }

    private fun Int.checkVertex(model: OsrsModel, what: String): Int {
        require(this in 0 until model.vertexCount) { "$what points at vertex $this of ${model.vertexCount}" }
        return this
    }

    private fun ByteArrayOutputStream.u16(value: Int) {
        write(value shr 8 and 0xFF)
        write(value and 0xFF)
    }

    /** A signed value in one byte (-64 to 63) or two (-16384 to 16383), the way readShortSmart reads it. */
    private fun ByteArrayOutputStream.shortSmart(value: Int, what: String) {
        require(value in SMART_MIN..SMART_MAX) { "$what moves by $value; the format holds moves of $SMART_MIN to $SMART_MAX" }
        if (value in -64..63) write(value + 64) else u16(value + 0xC000)
    }

    private class Reader(private val data: ByteArray, var offset: Int) {
        fun u8(): Int = data[offset++].toInt() and 0xFF
        fun s8(): Byte = data[offset++]
        fun u16(): Int = (u8() shl 8) or u8()
        fun shortSmart(): Int = if (data[offset].toInt() and 0xFF < 128) u8() - 64 else u16() - 0xC000
    }
}
