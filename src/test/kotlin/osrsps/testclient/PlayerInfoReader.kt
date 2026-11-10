package osrsps.testclient

import io.netty.buffer.Unpooled
import net.rsprot.buffer.JagByteBuf
import net.rsprot.buffer.extensions.toJagByteBuf

/** A tile in the world. */
data class Coord(val x: Int, val z: Int, val level: Int = 0) {
    override fun toString() = "$x, $z, $level"
}

/** How a player got to their tile in one PLAYER_INFO update, as the client is told it. */
enum class MoveType { WALK, RUN, TELEPORT }

/** One player's movement in one PLAYER_INFO packet. */
data class PlayerMove(val index: Int, val type: MoveType, val to: Coord)

/** What an actor's extended info started, as the real client raises it to RuneLite (AnimationChanged and the rest). */
enum class ActorEventKind { ANIMATION, SPOTANIM, HITSPLAT }

/**
 * An animation ([id], -1 to stop one), a spotanim ([id]) or a hitsplat ([id] its type, [amount] its damage) on a player
 * or an NPC by index, and the tick count the client had seen when it came.
 */
data class ActorEvent(val kind: ActorEventKind, val npc: Boolean, val index: Int, val id: Int, val amount: Int = 0, val ticks: Int = 0)

/**
 * A player's appearance block, as much of it as the client's player model shows of their gear: the item drawn in each
 * wearpos 0 to 11 (-1 for none, or for an identity kit such as hair), the NPC they are drawn as (-1 for none) and the
 * animation they stand in.
 */
data class AppearanceView(val worn: List<Int>, val npc: Int, val readyAnim: Int)

/**
 * The client's side of the player info protocol (GPI): keeps every player's position from REBUILD_LOGIN's init
 * block and each PLAYER_INFO's bit-packed movement, the same state the real client keeps. Every extended info block
 * RSProt's desktop writer sends is read past; of them the appearance ([appearance]), animations, spotanims and
 * hitsplats ([events]) are kept.
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

    /** Whether the local player's block in the last packet started an animation (a sequence). */
    var localAnimated: Boolean = false
        private set

    /** Whether the local player's block in the last packet held an exact move. */
    var localExactMoved: Boolean = false
        private set

    /** The animations, spotanims and hitsplats in the last packet's blocks, in order. */
    var events: List<ActorEvent> = emptyList()
        private set

    private val appearances = HashMap<Int, AppearanceView>()

    /** A player's last appearance block, or null when none has come since they came into view. */
    fun appearance(index: Int): AppearanceView? = appearances[index]

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
        readBlocks(payload, bits.alignedBytes(), extended)
        return moves
    }

    /**
     * The extended info after the bits, one block set per index in [extended], as RSProt 242's
     * PlayerAvatarExtendedInfoDesktopWriter writes them: a flag byte, a second and third when the one before has its
     * marker bit (0x4, 0x1000), then the blocks in the writer's order, each read as its RSProt encoder writes it.
     */
    private fun readBlocks(payload: ByteArray, start: Int, extended: List<Int>) {
        val buf = Unpooled.wrappedBuffer(payload, start, payload.size - start).toJagByteBuf()
        val events = ArrayList<ActorEvent>()
        localAnimated = false
        localExactMoved = false
        for (index in extended) {
            var flags = buf.g1()
            if (flags and EXTENDED_SHORT != 0) flags = flags or (buf.g1() shl 8)
            if (flags and EXTENDED_MEDIUM != 0) flags = flags or (buf.g1() shl 16)
            check(flags and KNOWN_FLAGS.inv() == 0) { "Player $index's extended info has flags 0x${flags.toString(16)}, not all known" }
            if (flags and EXACT_MOVE != 0) {
                buf.g1()
                buf.g1Alt3()
                buf.g1Alt3()
                buf.g1Alt3()
                buf.g2Alt1()
                buf.g2Alt2()
                buf.g2Alt3()
                if (index == localIndex) localExactMoved = true
            }
            if (flags and TINTING != 0) {
                buf.g2Alt1()
                buf.g2Alt3()
                buf.g1Alt1()
                buf.g1Alt1()
                buf.g1Alt3()
                buf.g1Alt2()
            }
            if (flags and FREEZE != 0) {
                buf.g2Alt2()
                buf.g2Alt2()
                buf.g1Alt3()
            }
            if (flags and PLAYER_RESET != 0) buf.g1Alt3()
            if (flags and MOVE_SPEED != 0) buf.g1Alt3()
            if (flags and SEQUENCE != 0) {
                val id = buf.g2()
                buf.g1Alt3() // delay
                events += ActorEvent(ActorEventKind.ANIMATION, false, index, if (id == NONE) -1 else id)
                if (index == localIndex) localAnimated = true
            }
            if (flags and HITMARKS != 0) {
                repeat(buf.g1()) {
                    val type = buf.gSmart1or2()
                    val value = buf.gSmart1or2()
                    buf.gSmart1or2() // delay
                    buf.gSmart1or2() // limit
                    events += ActorEvent(ActorEventKind.HITSPLAT, false, index, type, value)
                }
            }
            if (flags and SPOTANIM != 0) {
                repeat(buf.g1()) {
                    buf.g1Alt1() // slot
                    val id = buf.g2Alt3()
                    buf.skipRead(4) // delay and height
                    buf.g1() // loop
                    if (id != NONE) events += ActorEvent(ActorEventKind.SPOTANIM, false, index, id)
                }
            }
            if (flags and HEADBARS != 0) {
                repeat(buf.g1Alt2()) {
                    buf.gSmart1or2() // type
                    val end = buf.gSmart1or2()
                    if (end != HEADBAR_REMOVED) {
                        buf.gSmart1or2() // start time
                        buf.g1Alt3() // start fill
                        if (end > 0) buf.g1Alt2() // end fill
                    }
                }
            }
            if (flags and APPEARANCE != 0) {
                val data = ByteArray(buf.g1Alt2())
                buf.gdata(data)
                appearances[index] = readAppearance(data)
            }
            if (flags and TRANSPARENCY != 0) {
                buf.g2Alt3()
                buf.g2()
                buf.g1Alt1()
                buf.g1Alt3()
                buf.g1Alt1()
            }
            if (flags and CHAT != 0) {
                val colour = buf.g2() shr 8
                buf.g1Alt2() // mod icon
                buf.g1() // autotyper
                buf.skipRead(buf.g1Alt2()) // the Huffman text
                if (colour in 13..20) buf.skipRead(colour - 12)
            }
            if (flags and SAY != 0) buf.gjstr()
            if (flags and FACE != 0) readFace(buf, buf.g1Alt3())
            if (flags and TEMP_MOVE_SPEED != 0) buf.g1Alt3()
        }
        this.events = events
    }

    /** A face block after its flag, whose bits 3 to 5 say the kind, as RSProt's Face.encode writes it. */
    private fun readFace(buf: JagByteBuf, flag: Int) {
        when ((flag shr 3) and 7) {
            FACE_ENTITY -> {
                buf.gSmart1or2()
                buf.gSmart2or4null()
                buf.gSmart1or2()
            }
            FACE_LOC -> repeat(3) { buf.gSmart1or2() }
            FACE_ANGLE -> buf.gSmart1or2()
        }
    }

    /**
     * RSProt's PlayerAppearanceEncoder: body type, skull and prayer icons, then each wearpos 0 to 11 as a byte 0 for
     * nothing or two bytes (an item + 0x800, an identity kit + 0x100), or 0xFFFF and an NPC for a player drawn as one;
     * then the identity kits in the same form, five colours and the stance's animations, ready pose first.
     */
    private fun readAppearance(data: ByteArray): AppearanceView {
        val buf = Unpooled.wrappedBuffer(data).toJagByteBuf()
        repeat(3) { buf.g1() }
        val worn = MutableList(WORN_SLOTS) { -1 }
        var npc = -1
        for (wearpos in 0 until WORN_SLOTS) {
            val high = buf.g1()
            if (high == 0) continue
            val value = (high shl 8) or buf.g1()
            if (wearpos == 0 && value == NONE) {
                npc = buf.g2()
                break
            }
            if (value >= ITEM_OFFSET) worn[wearpos] = value - ITEM_OFFSET
        }
        for (wearpos in 0 until WORN_SLOTS) if (buf.g1() != 0) buf.g1()
        repeat(COLOURS) { buf.g1() }
        val ready = buf.g2()
        return AppearanceView(worn, npc, if (ready == NONE) -1 else ready)
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

        /** Block flags on the wire, as RSProt 242's desktop writer converts them. */
        const val EXTENDED_SHORT = 0x4
        const val EXTENDED_MEDIUM = 0x1000
        const val FACE = 0x1
        const val PLAYER_RESET = 0x2
        const val SPOTANIM = 0x8
        const val SEQUENCE = 0x10
        const val APPEARANCE = 0x40
        const val SAY = 0x80
        const val TEMP_MOVE_SPEED = 0x100
        const val MOVE_SPEED = 0x200
        const val CHAT = 0x800
        const val EXACT_MOVE = 0x2000
        const val TINTING = 0x8000
        const val TRANSPARENCY = 0x10000
        const val FREEZE = 0x40000
        const val HEADBARS = 0x80000
        const val HITMARKS = 0x100000
        const val KNOWN_FLAGS = EXTENDED_SHORT or EXTENDED_MEDIUM or APPEARANCE or SPOTANIM or PLAYER_RESET or SAY or FACE or
            SEQUENCE or MOVE_SPEED or CHAT or EXACT_MOVE or TINTING or TEMP_MOVE_SPEED or TRANSPARENCY or HITMARKS or FREEZE or HEADBARS

        /** Face kinds (RSProt's Face.Kind); a reset (3) has no more fields. */
        const val FACE_ENTITY = 0
        const val FACE_LOC = 1
        const val FACE_ANGLE = 2

        /** An id field's "none": no animation, no spotanim, no item. */
        const val NONE = 0xFFFF

        /** A head bar's end time that removes it, with no more fields. */
        const val HEADBAR_REMOVED = 32767

        /** The appearance's wearposes: head to jaw, 0 to 11 (ring and ammo aren't drawn). */
        const val WORN_SLOTS = 12
        const val ITEM_OFFSET = 0x800
        const val COLOURS = 5

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

    fun remaining(): Int = bytes.size * 8 - position

    fun align() {
        position = (position + 7) and 7.inv()
    }

    fun alignedBytes(): Int {
        align()
        return position shr 3
    }
}
