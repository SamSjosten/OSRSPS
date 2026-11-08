package osrsps.cache

import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Writes and reads a spotanim (a graphic) as config archive 13 holds one, with the opcodes RuneLite's SpotAnimLoader
 * reads (checked against the 241 cache on 2026-11-08: all 4,023 read back to their last byte). A spotanim names a
 * model, the sequence that animates it and recolours, so a graphic of ours is a cache model on a sequence, with its
 * colours swapped: the cache itself makes its cyan and dark Tele Other graphics (2059, 2285) that way from the blue
 * one (343, 342). The 241 cache writes every model id as opcode 3 (4 bytes); the encoder does the same, and the
 * decoder reads opcode 1 (2 bytes) too.
 */
object SpotanimCodec {
    const val INDEX = 2
    const val GROUP = 13

    /** The client's default size, 128 = unchanged, which the encoder leaves unwritten. */
    const val UNCHANGED = 128

    /** A colour as the client keeps one: hue 0 to 63, saturation 0 to 7, lightness 0 to 127, packed. */
    fun hsl(hue: Int, saturation: Int, lightness: Int): Int {
        require(hue in 0..63 && saturation in 0..7 && lightness in 0..127) { "Bad HSL $hue, $saturation, $lightness" }
        return (hue shl 10) or (saturation shl 7) or lightness
    }

    /**
     * A graphic: [model] (an id of index 7) played with sequence [seq], sized [resizeH] and [resizeV] (opcodes 4 and
     * 5, 128 = as is), turned [rotation] (6), lit with [ambient] and [contrast] (7 and 8; 0 = the client's own), and
     * [recolours] (40: each of the model's colours named is drawn as the other, packed HSL both) and [retextures] (41).
     */
    class Definition(
        val model: Int,
        val seq: Int,
        val resizeH: Int = UNCHANGED,
        val resizeV: Int = UNCHANGED,
        val rotation: Int = 0,
        val ambient: Int = 0,
        val contrast: Int = 0,
        val recolours: List<Pair<Int, Int>> = emptyList(),
        val retextures: List<Pair<Int, Int>> = emptyList(),
    ) {
        init {
            require(model in 0..0x7FFFFFFF) { "Bad model $model" }
            require(seq in 0..0xFFFF) { "Bad sequence $seq" }
            require(resizeH in 0..0xFFFF && resizeV in 0..0xFFFF) { "Bad size $resizeH by $resizeV" }
            require(rotation in 0..0xFFFF) { "Bad rotation $rotation" }
            require(ambient in 0..255 && contrast in 0..255) { "Bad lighting $ambient, $contrast" }
            require(recolours.size <= 255 && recolours.all { (from, to) -> from in 0..0xFFFF && to in 0..0xFFFF }) { "Bad recolours $recolours" }
            require(retextures.size <= 255 && retextures.all { (from, to) -> from in 0..0xFFFF && to in 0..0xFFFF }) { "Bad retextures $retextures" }
        }

        override fun equals(other: Any?): Boolean = other is Definition && other.fields() == fields()
        override fun hashCode(): Int = fields().hashCode()
        override fun toString(): String = "spotanim (model $model, sequence $seq)"

        private fun fields() = listOf(model, seq, resizeH, resizeV, rotation, ambient, contrast, recolours, retextures)
    }

    fun encode(definition: Definition): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write(value ushr 8)
            out.write(value and 0xFF)
        }
        fun pairs(opcode: Int, pairs: List<Pair<Int, Int>>) {
            if (pairs.isNotEmpty()) {
                out.write(opcode)
                out.write(pairs.size)
                for ((from, to) in pairs) {
                    u16(from)
                    u16(to)
                }
            }
        }
        out.write(3)
        out.write(definition.model ushr 24)
        out.write(definition.model ushr 16 and 0xFF)
        out.write(definition.model ushr 8 and 0xFF)
        out.write(definition.model and 0xFF)
        out.write(2)
        u16(definition.seq)
        if (definition.resizeH != UNCHANGED) {
            out.write(4)
            u16(definition.resizeH)
        }
        if (definition.resizeV != UNCHANGED) {
            out.write(5)
            u16(definition.resizeV)
        }
        if (definition.rotation != 0) {
            out.write(6)
            u16(definition.rotation)
        }
        if (definition.ambient != 0) {
            out.write(7)
            out.write(definition.ambient)
        }
        if (definition.contrast != 0) {
            out.write(8)
            out.write(definition.contrast)
        }
        pairs(40, definition.recolours)
        pairs(41, definition.retextures)
        out.write(0)
        return out.toByteArray()
    }

    /** Reads a spotanim back as the client would, the opcodes [encode] writes and the 2-byte model (1); another opcode, or no model or sequence, is refused. */
    fun decode(data: ByteArray): Definition {
        val buf = Unpooled.wrappedBuffer(data)
        var model: Int? = null
        var seq: Int? = null
        var resizeH = UNCHANGED
        var resizeV = UNCHANGED
        var rotation = 0
        var ambient = 0
        var contrast = 0
        var recolours = emptyList<Pair<Int, Int>>()
        var retextures = emptyList<Pair<Int, Int>>()
        fun pairs(): List<Pair<Int, Int>> = List(buf.readUnsignedByte().toInt()) { buf.readUnsignedShort() to buf.readUnsignedShort() }
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1 -> model = buf.readUnsignedShort()
                2 -> seq = buf.readUnsignedShort()
                3 -> model = buf.readInt()
                4 -> resizeH = buf.readUnsignedShort()
                5 -> resizeV = buf.readUnsignedShort()
                6 -> rotation = buf.readUnsignedShort()
                7 -> ambient = buf.readUnsignedByte().toInt()
                8 -> contrast = buf.readUnsignedByte().toInt()
                40 -> recolours = pairs()
                41 -> retextures = pairs()
                else -> error("Spotanim opcode $opcode is not one of ours")
            }
        }
        require(buf.readableBytes() == 0) { "${buf.readableBytes()} byte(s) after the spotanim's end" }
        require(model != null && seq != null) { "A spotanim without a model or a sequence" }
        return Definition(model, seq, resizeH, resizeV, rotation, ambient, contrast, recolours, retextures)
    }
}
