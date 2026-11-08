package osrsps.cache

import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Writes and reads a map element of our own: config archive 35, RuneLite's AreaDefinition and the client's
 * MapElementConfig. A loc that names one with opcode 82 gets its sprite drawn on the minimap where the loc stands (the
 * world map's icons come from the world map index instead, built with the map). Read from the revision 241 cache on
 * 2026-11-02: its 387 icon elements are all opcode 19 (a category), 1 (the sprite, a "big smart": 2 bytes below 32,768,
 * else 4 with the top bit set), 8 with a 1 and 7 with the flags 3, and a loc's icon rides on the loc the client draws,
 * so a multiloc's children carry it, not the parent. Labels on the world map use a name and text settings instead.
 * What opcode 8's byte and the two flag bits mean is the client's; an element of ours copies the icons.
 */
object MapElementCodec {
    const val INDEX = 2
    const val GROUP = 35

    /** The cache's icons set opcode 7 to this: both bits. */
    const val ICON_FLAGS = 3

    /** No category, or the one the client's world map legend groups the icon under (ours have none). */
    const val NO_CATEGORY = -1

    class Definition(val sprite: Int, val category: Int = NO_CATEGORY) {
        init {
            require(sprite in 0..0xFFFF) { "A sprite id is 0 to 65535, not $sprite" }
            require(category == NO_CATEGORY || category in 0..0xFFFF) { "Bad category $category" }
        }

        override fun equals(other: Any?): Boolean = other is Definition && other.sprite == sprite && other.category == category
        override fun hashCode(): Int = sprite * 31 + category
        override fun toString(): String = "map element (sprite $sprite${if (category == NO_CATEGORY) "" else ", category $category"})"
    }

    fun encode(definition: Definition): ByteArray {
        val out = ByteArrayOutputStream()
        if (definition.category != NO_CATEGORY) {
            out.write(19)
            out.write(definition.category ushr 8)
            out.write(definition.category and 0xFF)
        }
        out.write(1)
        if (definition.sprite < 0x8000) {
            out.write(definition.sprite ushr 8)
            out.write(definition.sprite and 0xFF)
        } else {
            out.write(0x80)
            out.write(definition.sprite ushr 16 and 0xFF)
            out.write(definition.sprite ushr 8 and 0xFF)
            out.write(definition.sprite and 0xFF)
        }
        out.write(8)
        out.write(1)
        out.write(7)
        out.write(ICON_FLAGS)
        out.write(0)
        return out.toByteArray()
    }

    /** Reads an icon element back as the client would: the opcodes [encode] writes, and a label's, so a test can tell them apart. */
    fun decode(data: ByteArray): Definition {
        val buf = Unpooled.wrappedBuffer(data)
        var sprite = -1
        var category = NO_CATEGORY
        var icon = false
        var flags = 0
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1 -> sprite = if (buf.getByte(buf.readerIndex()) < 0) buf.readInt() and 0x7FFFFFFF else buf.readUnsignedShort().let { if (it == 32767) -1 else it }
                7 -> flags = buf.readUnsignedByte().toInt()
                8 -> icon = buf.readUnsignedByte().toInt() == 1
                19 -> category = buf.readUnsignedShort()
                else -> error("Map element opcode $opcode is not one of an icon's")
            }
        }
        require(buf.readableBytes() == 0) { "${buf.readableBytes()} byte(s) after the map element's end" }
        require(sprite != -1 && icon && flags == ICON_FLAGS) { "Not an icon element: sprite $sprite, icon $icon, flags $flags" }
        return Definition(sprite, category)
    }
}
