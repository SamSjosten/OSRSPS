package osrsps.cache

import java.io.ByteArrayOutputStream

/**
 * A sprite as the client's sprite index (8) holds one: a group of frames, each a palette image. Ours are one frame
 * each. Pixels are ARGB; alpha 0 is transparent (palette entry 0), and a frame with any other alpha below 255 carries
 * an alpha plane.
 */
class Sprite(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width in 1..0xFFFF && height in 1..0xFFFF) { "A sprite is 1 to 65535 pixels a side, not $width by $height" }
        require(pixels.size == width * height) { "A $width by $height sprite has ${width * height} pixels, not ${pixels.size}" }
    }

    operator fun get(x: Int, z: Int): Int = pixels[z * width + x]
}

/**
 * Reads and writes the client's sprite format (RuneLite's SpriteLoader, read 2026-11-02; the format has been the same
 * since the 2000s): every frame's pixels first (a flags byte, the palette indices, and the alphas when the flag says
 * so), then the palette (3 bytes a colour, entry 0 left out: it is transparent), then, for the group, the frames'
 * largest width and height, the palette size less one, each frame's x offset, y offset, width and height, and the
 * frame count in the last 2 bytes. A palette holds 255 colours besides the transparent one, so an image with more is
 * reduced to 255 first ([quantize]).
 */
object SpriteCodec {
    const val INDEX = 8
    const val MAX_COLOURS = 255
    private const val FLAG_VERTICAL = 1
    private const val FLAG_ALPHA = 2

    /** Encodes [frames] as one group; [frames] usually holds one sprite. */
    fun encode(frames: List<Sprite>): ByteArray {
        require(frames.isNotEmpty()) { "A sprite group has at least one frame" }
        val reduced = frames.map { quantize(it) }
        val palette = LinkedHashMap<Int, Int>()
        for (sprite in reduced) for (argb in sprite.pixels) {
            if (argb ushr 24 == 0) continue
            val rgb = (argb and 0xFFFFFF).let { if (it == 0) 1 else it }
            palette.getOrPut(rgb) { palette.size + 1 }
        }
        require(palette.size <= MAX_COLOURS) { "The frames together use ${palette.size} colours; a palette holds $MAX_COLOURS" }
        val out = ByteArrayOutputStream()
        for (sprite in reduced) {
            val alpha = sprite.pixels.any { (it ushr 24) in 1..254 }
            out.write(if (alpha) FLAG_ALPHA else 0)
            for (argb in sprite.pixels) out.write(if (argb ushr 24 == 0) 0 else palette.getValue((argb and 0xFFFFFF).let { if (it == 0) 1 else it }))
            if (alpha) for (argb in sprite.pixels) out.write(argb ushr 24)
        }
        for (rgb in palette.keys) {
            out.write(rgb ushr 16 and 0xFF)
            out.write(rgb ushr 8 and 0xFF)
            out.write(rgb and 0xFF)
        }
        out.short(reduced.maxOf { it.width })
        out.short(reduced.maxOf { it.height })
        out.write(palette.size)
        for (sprite in reduced) out.short(0)
        for (sprite in reduced) out.short(0)
        for (sprite in reduced) out.short(sprite.width)
        for (sprite in reduced) out.short(sprite.height)
        out.short(reduced.size)
        return out.toByteArray()
    }

    /** One sprite as one group. */
    fun encode(sprite: Sprite): ByteArray = encode(listOf(sprite))

    /** The frames of a group, each as a full-size image (a frame's offsets put it in the group's largest size). */
    fun decode(data: ByteArray): List<Sprite> {
        require(data.size >= 9) { "A sprite group is at least 9 bytes, not ${data.size}" }
        val count = short(data, data.size - 2)
        var at = data.size - 7 - count * 8
        require(count >= 1 && at >= 0) { "A sprite group of $count frames doesn't fit ${data.size} bytes" }
        val maxWidth = short(data, at)
        val maxHeight = short(data, at + 2)
        val paletteSize = (data[at + 4].toInt() and 0xFF) + 1
        at += 5
        val offsetsX = IntArray(count) { short(data, at + it * 2) }
        at += count * 2
        val offsetsY = IntArray(count) { short(data, at + it * 2) }
        at += count * 2
        val widths = IntArray(count) { short(data, at + it * 2) }
        at += count * 2
        val heights = IntArray(count) { short(data, at + it * 2) }
        val paletteAt = data.size - 7 - count * 8 - (paletteSize - 1) * 3
        require(paletteAt >= 0) { "A palette of $paletteSize colours doesn't fit ${data.size} bytes" }
        val palette = IntArray(paletteSize)
        for (i in 1 until paletteSize) {
            val p = paletteAt + (i - 1) * 3
            palette[i] = ((data[p].toInt() and 0xFF) shl 16) or ((data[p + 1].toInt() and 0xFF) shl 8) or (data[p + 2].toInt() and 0xFF)
            if (palette[i] == 0) palette[i] = 1
        }
        var pos = 0
        return List(count) { frame ->
            val width = widths[frame]
            val height = heights[frame]
            val size = width * height
            val flags = data[pos++].toInt() and 0xFF
            val indices = ByteArray(size)
            if (flags and FLAG_VERTICAL == 0) {
                System.arraycopy(data, pos, indices, 0, size)
            } else {
                for (x in 0 until width) for (y in 0 until height) indices[y * width + x] = data[pos + x * height + y]
            }
            pos += size
            val alphas = ByteArray(size) { -1 }
            if (flags and FLAG_ALPHA != 0) {
                if (flags and FLAG_VERTICAL == 0) {
                    System.arraycopy(data, pos, alphas, 0, size)
                } else {
                    for (x in 0 until width) for (y in 0 until height) alphas[y * width + x] = data[pos + x * height + y]
                }
                pos += size
            }
            val pixels = IntArray(maxWidth * maxHeight)
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                val index = indices[i].toInt() and 0xFF
                require(index < paletteSize) { "Frame $frame names palette entry $index of $paletteSize" }
                val alpha = if (index == 0) 0 else alphas[i].toInt() and 0xFF
                pixels[(y + offsetsY[frame]) * maxWidth + x + offsetsX[frame]] = (alpha shl 24) or palette[index]
            }
            Sprite(maxWidth, maxHeight, pixels)
        }
    }

    /**
     * [sprite] with at most [MAX_COLOURS] opaque colours: the same image when it has no more, else its colours rounded
     * to fewer bits a channel until they fit (a coarse cut, enough for the drawn images we serve).
     */
    fun quantize(sprite: Sprite): Sprite {
        fun colours(pixels: IntArray) = pixels.asSequence().filter { it ushr 24 != 0 }.map { it and 0xFFFFFF }.toSet().size
        if (colours(sprite.pixels) <= MAX_COLOURS) return sprite
        for (bits in 7 downTo 1) {
            val keep = (0xFF shl (8 - bits)) and 0xFF
            val mask = (keep shl 16) or (keep shl 8) or keep
            val pixels = IntArray(sprite.pixels.size) { i ->
                val argb = sprite.pixels[i]
                if (argb ushr 24 == 0) 0 else (argb and 0xFF000000.toInt()) or (argb and mask)
            }
            if (colours(pixels) <= MAX_COLOURS) return Sprite(sprite.width, sprite.height, pixels)
        }
        error("Can't reduce a sprite to $MAX_COLOURS colours")
    }

    private fun short(data: ByteArray, at: Int): Int = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun ByteArrayOutputStream.short(value: Int) {
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }
}
