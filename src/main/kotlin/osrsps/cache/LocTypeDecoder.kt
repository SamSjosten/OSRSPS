package osrsps.cache

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.map.LocCollisionType

/**
 * Reads the parts of a loc type (config archive 6) that collision needs, skipping everything else.
 * Every opcode has to be known to skip it, so an unknown one fails loudly instead of misreading the rest.
 * Opcode layouts follow RuneLite's cache ObjectLoader (revision 220+ sound data).
 */
object LocTypeDecoder {
    fun decode(data: ByteArray): LocCollisionType {
        val buf = Unpooled.wrappedBuffer(data)
        var width = 1
        var length = 1
        var interactType = 2
        var blocksProjectile = true
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1 -> buf.skipBytes(buf.readUnsignedByte() * 3)
                2 -> buf.skipString()
                5 -> buf.skipBytes(buf.readUnsignedByte() * 2)
                6 -> buf.skipBytes(buf.readUnsignedByte() * 5)
                7 -> buf.skipBytes(buf.readUnsignedByte() * 4)
                14 -> width = buf.readUnsignedByte().toInt()
                15 -> length = buf.readUnsignedByte().toInt()
                17 -> { interactType = 0; blocksProjectile = false }
                18 -> blocksProjectile = false
                19 -> buf.skipBytes(1)
                21, 22, 23, 62, 64, 73, 74, 89, 90, 94 -> {}
                24 -> buf.skipBytes(2)
                27 -> interactType = 1
                28, 29, 39, 69, 75, 81, 91, 95, 96 -> buf.skipBytes(1)
                in 30..34 -> buf.skipString()
                40, 41 -> buf.skipBytes(buf.readUnsignedByte() * 4)
                42, 61, 65, 66, 67, 68, 70, 71, 72, 82 -> buf.skipBytes(2)
                77 -> { buf.skipBytes(4); buf.skipBytes((buf.readUnsignedByte() + 1) * 2) }
                78 -> buf.skipBytes(4)
                79 -> { buf.skipBytes(6); buf.skipBytes(buf.readUnsignedByte() * 2) }
                92 -> { buf.skipBytes(6); buf.skipBytes((buf.readUnsignedByte() + 1) * 2) }
                93 -> buf.skipBytes(6)
                100 -> { buf.skipBytes(2); buf.skipString() }
                101 -> { buf.skipBytes(13); buf.skipString() }
                102 -> { buf.skipBytes(15); buf.skipString() }
                249 -> buf.skipParams()
                else -> error("Unknown loc type opcode $opcode")
            }
        }
        return LocCollisionType(width, length, interactType, blocksProjectile)
    }

    private fun ByteBuf.skipString() {
        while (readByte().toInt() != 0) continue
    }

    private fun ByteBuf.skipParams() {
        repeat(readUnsignedByte().toInt()) {
            val isString = readUnsignedByte().toInt()
            skipBytes(3)
            when (isString) {
                1 -> skipString()
                2 -> skipBytes(8)
                else -> skipBytes(4)
            }
        }
    }
}
