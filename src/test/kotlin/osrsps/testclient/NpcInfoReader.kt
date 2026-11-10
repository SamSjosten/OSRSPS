package osrsps.testclient

import io.netty.buffer.Unpooled
import net.rsprot.buffer.extensions.toJagByteBuf

/** Overhead text an NPC was shown saying, and the tick count the client had seen when it came. */
data class NpcSaid(val index: Int, val text: String, val ticks: Int)

/** A hitsplat on an NPC as this client was sent it (its own type when it dealt the hit), and the ticks seen then. */
data class NpcHit(val index: Int, val type: Int, val value: Int, val ticks: Int)

/** An NPC's head bar as the client was last told it: its type, and how much of the type's width is filled. */
data class HeadbarView(val type: Int, val fill: Int)

/** An NPC the client can see: its index in the world, its type id and its tile. */
data class NpcView(val index: Int, val id: Int, val coord: Coord)

/**
 * The client's side of NPC info: the list of NPCs in view, kept from NPC_INFO_SMALL_V6 and NPC_INFO_LARGE_V6 as the
 * real client keeps it. Of the extended info blocks overhead text ([said]), hitsplats ([hits]), animations and spotanims
 * ([events]), head bars ([health]) and a change of type are kept. Every block RSProt's desktop writer sends is read but
 * the head, body and head icon customisations; an NPC with one of those stops the reading there.
 *
 * Built against RSProt's NpcInfo and DesktopLowResolutionChangeEncoder: a count of the NPCs still in view, a bit-packed
 * update for each of them in order (no change, extended info only, walk, run or crawl, or removal), then the NPCs new to
 * the view, each placed relative to the local player, until a 0xFFFF index or the end of the bits. Only the root world
 * is read; world entities (boats) are not.
 */
class NpcInfoReader {
    private val inView = ArrayList<NpcView>()

    /** Indices whose extended info block followed in the last packet. */
    var extendedInfo: List<Int> = emptyList()
        private set

    /** Overhead text in the last packet: each NPC's index and what it said. */
    var said: List<Pair<Int, String>> = emptyList()
        private set

    /** Hitsplats in the last packet: each NPC's index, the hitmark type and the damage. */
    var hits: List<Triple<Int, Int, Int>> = emptyList()
        private set

    /** The animations, spotanims and hitsplats in the last packet's blocks, in order. */
    var events: List<ActorEvent> = emptyList()
        private set

    /** Each NPC's head bar as the client was last told it, by index; gone when the bar was removed. */
    private val fills = HashMap<Int, HeadbarView>()

    fun npcs(): List<NpcView> = inView.toList()

    /** An NPC's health bar; null while it shows none. */
    fun health(index: Int): HeadbarView? = fills[index]

    /** Applies one packet; [local] is the local player's tile after this tick's player info. */
    fun read(payload: ByteArray, large: Boolean, local: Coord) {
        val bits = BitReader(payload)
        val extended = ArrayList<Int>()
        val count = bits.read(8)
        check(count <= inView.size) { "NPC info keeps $count NPCs but only ${inView.size} are in view" }
        val kept = ArrayList<NpcView>(count)
        for (npc in inView.take(count)) {
            if (bits.read(1) == 0) {
                kept += npc
                continue
            }
            when (bits.read(2)) {
                0 -> {
                    extended += npc.index
                    kept += npc
                }
                1 -> {
                    val to = step(npc.coord, bits.read(3))
                    if (bits.read(1) == 1) extended += npc.index
                    kept += npc.copy(coord = to)
                }
                2 -> {
                    val run = bits.read(1) == 1
                    var to = step(npc.coord, bits.read(3))
                    if (run) to = step(to, bits.read(3))
                    if (bits.read(1) == 1) extended += npc.index
                    kept += npc.copy(coord = to)
                }
                // 3: removed from view.
            }
        }
        val deltaBits = if (large) 8 else 6
        while (bits.remaining() >= 16) {
            val index = bits.read(16)
            if (index == 0xFFFF) break
            // RSProt 242's DesktopLowResolutionChangeEncoder.
            val dx = signed(bits.read(deltaBits), deltaBits)
            bits.read(3) // direction it faces
            if (bits.read(1) == 1) extended += index
            val id = bits.read(TYPE_BITS[bits.read(2)])
            if (bits.read(1) == 1) {
                val clockBits = SPAWN_CLOCK_BITS[bits.read(2)]
                bits.read(clockBits) // spawn cycle
            }
            bits.read(1) // jump: whether the client snaps it there rather than walking it
            val dz = signed(bits.read(deltaBits), deltaBits)
            kept += NpcView(index, id, Coord(local.x + dx, local.z + dz, local.level))
        }
        inView.clear()
        inView += kept
        fills.keys.retainAll(kept.mapTo(HashSet()) { it.index })
        extendedInfo = extended
        readBlocks(payload, bits.alignedBytes(), extended)
    }

    /**
     * The extended info after the bits, one block set per index in [extended], as RSProt 242's
     * NpcAvatarExtendedInfoDesktopWriter writes them: a flag byte, a second, third and fourth when the one before has its
     * marker bit (0x4, 0x400, 0x200000), then the blocks in the writer's order, each read as its RSProt encoder writes it.
     */
    private fun readBlocks(payload: ByteArray, start: Int, extended: List<Int>) {
        val said = ArrayList<Pair<Int, String>>()
        val hits = ArrayList<Triple<Int, Int, Int>>()
        val events = ArrayList<ActorEvent>()
        val buf = Unpooled.wrappedBuffer(payload, start, payload.size - start).toJagByteBuf()
        for (index in extended) {
            if (!buf.isReadable) break
            var flags = buf.g1()
            if (flags and EXTENDED_SHORT != 0) flags = flags or (buf.g1() shl 8)
            if (flags and EXTENDED_MEDIUM != 0) flags = flags or (buf.g1() shl 16)
            if (flags and EXTENDED_INT != 0) flags = flags or (buf.g1() shl 24)
            if (flags and KNOWN_FLAGS.inv() != 0) break
            if (flags and SPOTANIM != 0) {
                repeat(buf.g1Alt3()) {
                    buf.g1Alt3() // slot
                    val id = buf.g2Alt3()
                    buf.skipRead(4) // delay and height
                    buf.g1Alt2() // loop
                    if (id != NONE) events += ActorEvent(ActorEventKind.SPOTANIM, true, index, id)
                }
            }
            if (flags and HITMARKS != 0) {
                repeat(buf.g1Alt3()) {
                    val type = buf.gSmart1or2()
                    val value = buf.gSmart1or2()
                    buf.gSmart1or2() // delay
                    buf.gSmart1or2() // limit
                    hits += Triple(index, type, value)
                    events += ActorEvent(ActorEventKind.HITSPLAT, true, index, type, value)
                }
            }
            if (flags and SEQUENCE != 0) {
                val id = buf.g2()
                buf.g1() // delay
                events += ActorEvent(ActorEventKind.ANIMATION, true, index, if (id == NONE) -1 else id)
            }
            if (flags and TRANSPARENCY != 0) {
                buf.g2Alt2()
                buf.g2Alt2()
                buf.g1Alt1()
                buf.g1Alt1()
                buf.g1Alt2()
            }
            if (flags and HEADBARS != 0) {
                repeat(buf.g1()) {
                    val type = buf.gSmart1or2()
                    val end = buf.gSmart1or2()
                    if (end == HEADBAR_REMOVED) {
                        fills.remove(index)
                    } else {
                        buf.gSmart1or2() // start time
                        val startFill = buf.g1Alt1()
                        // A bar that slides (an end time) ends at its end fill, which is what it shows once it settles.
                        fills[index] = HeadbarView(type, if (end > 0) buf.g1Alt3() else startFill)
                    }
                }
            }
            if (flags and SAY != 0) said += index to buf.gjstr()
            if (flags and NAME_CHANGE != 0) buf.gjstr()
            if (flags and FACE != 0) {
                when ((buf.g1Alt1() shr 3) and 7) {
                    FACE_ENTITY -> {
                        buf.gSmart1or2()
                        buf.gSmart2or4null()
                        buf.gSmart1or2()
                    }
                    FACE_LOC -> repeat(3) { buf.gSmart1or2() }
                    FACE_ANGLE -> buf.gSmart1or2()
                }
            }
            if (flags and TINTING != 0) {
                buf.g2Alt2()
                buf.g2()
                buf.g1Alt2()
                buf.g1Alt3()
                buf.g1Alt2()
                buf.g1Alt3()
            }
            if (flags and FREEZE != 0) {
                buf.g2Alt3()
                buf.g2Alt1()
                buf.g1()
            }
            if (flags and OPS != 0) buf.g1Alt1()
            if (flags and TRANSFORMATION != 0) {
                val id = buf.g2Alt2()
                val at = inView.indexOfFirst { it.index == index }
                if (at >= 0) inView[at] = inView[at].copy(id = id)
            }
            if (flags and EXACT_MOVE != 0) {
                buf.g1Alt3()
                buf.g1()
                buf.g1Alt2()
                buf.g1Alt3()
                buf.g2Alt1()
                buf.g2()
                buf.g2()
            }
            if (flags and BAS_CHANGE != 0) {
                // A flag of the stance animations that follow, two bytes each.
                buf.skipRead(Integer.bitCount(buf.g4()) * 2)
            }
            if (flags and LEVEL_CHANGE != 0) buf.skipRead(4)
        }
        this.said = said
        this.hits = hits
        this.events = events
    }

    private fun step(from: Coord, direction: Int): Coord {
        val (dx, dz) = DIRECTIONS[direction]
        return Coord(from.x + dx, from.z + dz, from.level)
    }

    private fun signed(value: Int, bitCount: Int): Int = if (value >= 1 shl (bitCount - 1)) value - (1 shl bitCount) else value

    private companion object {
        /** NPC step directions 0-7: NW, N, NE, W, E, SW, S, SE (not the player order). */
        val DIRECTIONS = arrayOf(-1 to 1, 0 to 1, 1 to 1, -1 to 0, 1 to 0, -1 to -1, 0 to -1, 1 to -1)
        val SPAWN_CLOCK_BITS = intArrayOf(18, 19, 20, 32)
        val TYPE_BITS = intArrayOf(12, 14, 17, 24)

        /** Block flags on the wire (RSProt 242's desktop writer). */
        const val EXTENDED_SHORT = 0x4
        const val EXTENDED_MEDIUM = 0x400
        const val EXTENDED_INT = 0x200000
        const val SPOTANIM = 0x1
        const val SEQUENCE = 0x2
        const val FREEZE = 0x8
        const val SAY = 0x10
        const val FACE = 0x20
        const val TRANSFORMATION = 0x80
        const val TINTING = 0x100
        const val NAME_CHANGE = 0x200
        const val EXACT_MOVE = 0x800
        const val TRANSPARENCY = 0x1000
        const val LEVEL_CHANGE = 0x4000
        const val OPS = 0x8000
        const val HITMARKS = 0x400000
        const val BAS_CHANGE = 0x800000
        const val HEADBARS = 0x1000000
        const val KNOWN_FLAGS = EXTENDED_SHORT or EXTENDED_MEDIUM or EXTENDED_INT or TRANSFORMATION or SPOTANIM or SEQUENCE or
            FACE or SAY or FREEZE or OPS or EXACT_MOVE or NAME_CHANGE or LEVEL_CHANGE or TINTING or TRANSPARENCY or HITMARKS or
            BAS_CHANGE or HEADBARS

        /** Face kinds (RSProt's Face.Kind); a reset (3) has no more fields. */
        const val FACE_ENTITY = 0
        const val FACE_LOC = 1
        const val FACE_ANGLE = 2

        /** An id field's "none". */
        const val NONE = 0xFFFF

        /** A head bar's end time that removes it, with no more fields. */
        const val HEADBAR_REMOVED = 32767
    }
}
