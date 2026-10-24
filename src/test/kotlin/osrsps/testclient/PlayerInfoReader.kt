package osrsps.testclient

/** A tile in the world. */
data class Coord(val x: Int, val z: Int, val level: Int = 0) {
    override fun toString() = "$x, $z, $level"
}

/** How a player got to their tile in one PLAYER_INFO update, as the client is told it. */
enum class MoveType { WALK, RUN, TELEPORT }

/** One player's movement in one PLAYER_INFO packet. */
data class PlayerMove(val index: Int, val type: MoveType, val to: Coord)

/**
 * The client's side of the player info protocol (GPI): keeps every player's position from REBUILD_LOGIN's init
 * block and each PLAYER_INFO's bit-packed movement, the same state the real client keeps. Extended info blocks
 * (appearance, chat, animations) are not read yet.
 *
 * Built against RSProt's PlayerInfo encoder: four byte-aligned passes, high resolution players who moved last
 * update, then those who didn't, then low resolution in the reverse order, with run-length skips between them.
 */
class PlayerInfoReader(val localIndex: Int) {
    private val flags = ByteArray(CAPACITY)
    private val highRes = BooleanArray(CAPACITY)
    private val coords = arrayOfNulls<Coord>(CAPACITY)

    /** Each player's low resolution position: level shl 16, x shr 13 shl 8, z shr 13. */
    private val lowRes = IntArray(CAPACITY)
    private var highResList = IntArray(0)
    private var lowResList = IntArray(0)

    /** Indices whose extended info block followed in the last packet. */
    var extendedInfo: List<Int> = emptyList()
        private set

    /** Where a high resolution player is, or null if they are not in view. */
    fun coord(index: Int): Coord? = if (highRes[index]) coords[index] else null

    /** Every high resolution player but the local one. */
    fun others(): Map<Int, Coord> = (1 until CAPACITY).filter { highRes[it] && it != localIndex }.associateWith { coords[it]!! }

    /** Reads REBUILD_LOGIN's init block, which comes before the rebuild's own fields. Returns the bytes it used. */
    fun init(payload: ByteArray): Int {
        val bits = BitReader(payload)
        val packed = bits.read(30)
        coords[localIndex] = Coord((packed shr 14) and 0x3FFF, packed and 0x3FFF, packed ushr 28)
        highRes[localIndex] = true
        for (i in 1 until CAPACITY) {
            if (i != localIndex) lowRes[i] = bits.read(18)
        }
        rebuildLists()
        return bits.alignedBytes()
    }

    /** Applies one PLAYER_INFO packet; returns who moved. */
    fun read(payload: ByteArray): List<PlayerMove> {
        val bits = BitReader(payload)
        val moves = ArrayList<PlayerMove>()
        val extended = ArrayList<Int>()
        for (stationary in listOf(false, true)) {
            var skip = 0
            for (index in highResList) {
                if (((flags[index].toInt() and 1) != 0) != stationary) continue
                if (skip > 0) {
                    skip--
                    flags[index] = (flags[index].toInt() or 2).toByte()
                    continue
                }
                if (bits.read(1) == 0) {
                    skip = readSkip(bits)
                    flags[index] = (flags[index].toInt() or 2).toByte()
                    continue
                }
                readHighRes(bits, index, moves, extended)
            }
            check(skip == 0) { "High resolution skip ran past the last player" }
            bits.align()
        }
        for (stationary in listOf(true, false)) {
            var skip = 0
            for (index in lowResList) {
                if (((flags[index].toInt() and 1) != 0) != stationary) continue
                if (skip > 0) {
                    skip--
                    flags[index] = (flags[index].toInt() or 2).toByte()
                    continue
                }
                if (bits.read(1) == 0) {
                    skip = readSkip(bits)
                    flags[index] = (flags[index].toInt() or 2).toByte()
                    continue
                }
                readLowRes(bits, index, extended)
            }
            check(skip == 0) { "Low resolution skip ran past the last player" }
            bits.align()
        }
        for (i in 1 until CAPACITY) flags[i] = (flags[i].toInt() shr 1).toByte()
        rebuildLists()
        extendedInfo = extended
        return moves
    }

    private fun readHighRes(bits: BitReader, index: Int, moves: MutableList<PlayerMove>, extended: MutableList<Int>) {
        val hasExtended = bits.read(1) == 1
        if (hasExtended) extended += index
        val from = coords[index]!!
        when (bits.read(2)) {
            0 -> if (!hasExtended) {
                // Out of view: back to low resolution.
                highRes[index] = false
                if (bits.read(1) == 1) readLowResMove(bits, index, bits.read(2))
            }
            1 -> {
                val (dx, dz) = WALK[bits.read(3)]
                moves += move(index, MoveType.WALK, Coord(from.x + dx, from.z + dz, from.level))
            }
            2 -> {
                val (dx, dz) = RUN[bits.read(4)]
                moves += move(index, MoveType.RUN, Coord(from.x + dx, from.z + dz, from.level))
            }
            else -> {
                val large = bits.read(1) == 1
                val dLevel = bits.read(2)
                val to = if (large) {
                    Coord((from.x + bits.read(14)) and 0x3FFF, (from.z + bits.read(14)) and 0x3FFF, (from.level + dLevel) and 3)
                } else {
                    Coord(from.x + signed(bits.read(5), 5), from.z + signed(bits.read(5), 5), (from.level + dLevel) and 3)
                }
                moves += move(index, MoveType.TELEPORT, to)
            }
        }
    }

    private fun readLowRes(bits: BitReader, index: Int, extended: MutableList<Int>) {
        val type = bits.read(2)
        if (type != 0) {
            readLowResMove(bits, index, type)
            return
        }
        // Into view: the low resolution position gives the top bits of the coordinates.
        if (bits.read(1) == 1) readLowResMove(bits, index, bits.read(2))
        val x = bits.read(13)
        val z = bits.read(13)
        if (bits.read(1) == 1) extended += index
        val low = lowRes[index]
        coords[index] = Coord((((low shr 8) and 0xFF) shl 13) + x, ((low and 0xFF) shl 13) + z, (low shr 16) and 3)
        highRes[index] = true
        flags[index] = (flags[index].toInt() or 2).toByte()
    }

    private fun readLowResMove(bits: BitReader, index: Int, type: Int) {
        var level = (lowRes[index] shr 16) and 3
        var x = (lowRes[index] shr 8) and 0xFF
        var z = lowRes[index] and 0xFF
        when (type) {
            1 -> level += bits.read(2)
            2 -> {
                level += bits.read(2)
                val (dx, dz) = WALK[bits.read(3)]
                x += dx
                z += dz
            }
            else -> {
                level += bits.read(2)
                x += bits.read(8)
                z += bits.read(8)
            }
        }
        lowRes[index] = ((level and 3) shl 16) or ((x and 0xFF) shl 8) or (z and 0xFF)
    }

    private fun move(index: Int, type: MoveType, to: Coord): PlayerMove {
        coords[index] = to
        return PlayerMove(index, type, to)
    }

    private fun readSkip(bits: BitReader): Int = when (bits.read(2)) {
        0 -> 0
        1 -> bits.read(5)
        2 -> bits.read(8)
        else -> bits.read(11)
    }

    /** Ascending order, as the client and RSProt both rebuild them after every packet. */
    private fun rebuildLists() {
        highResList = (1 until CAPACITY).filter { highRes[it] }.toIntArray()
        lowResList = (1 until CAPACITY).filter { !highRes[it] }.toIntArray()
    }

    private fun signed(value: Int, bitCount: Int): Int = if (value >= 1 shl (bitCount - 1)) value - (1 shl bitCount) else value

    private companion object {
        const val CAPACITY = 2048

        /** Walk opcodes 0-7: SW, S, SE, W, E, NW, N, NE. */
        val WALK = arrayOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

        /** Run opcodes 0-15, two tiles out: SSWW, SSW, SS, SSE, SSEE, SWW, SEE, WW, EE, NWW, NEE, NNWW, NNW, NN, NNE, NNEE. */
        val RUN = arrayOf(
            -2 to -2, -1 to -2, 0 to -2, 1 to -2, 2 to -2, -2 to -1, 2 to -1, -2 to 0,
            2 to 0, -2 to 1, 2 to 1, -2 to 2, -1 to 2, 0 to 2, 1 to 2, 2 to 2,
        )
    }
}

/** Reads big-endian bit fields, as Jagex's bit buffers write them. */
internal class BitReader(private val bytes: ByteArray) {
    private var position = 0

    fun read(count: Int): Int {
        var value = 0
        repeat(count) {
            val byte = bytes[position shr 3].toInt()
            val bit = (byte shr (7 - (position and 7))) and 1
            value = (value shl 1) or bit
            position++
        }
        return value
    }

    fun align() {
        position = (position + 7) and 7.inv()
    }

    fun alignedBytes(): Int {
        align()
        return position shr 3
    }
}
