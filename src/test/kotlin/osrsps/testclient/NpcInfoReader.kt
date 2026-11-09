package osrsps.testclient

/** Overhead text an NPC was shown saying, and the tick count the client had seen when it came. */
data class NpcSaid(val index: Int, val text: String, val ticks: Int)

/** A hitsplat on an NPC as this client was sent it (its own type when it dealt the hit), and the ticks seen then. */
data class NpcHit(val index: Int, val type: Int, val value: Int, val ticks: Int)

/** An NPC the client can see: its index in the world, its type id and its tile. */
data class NpcView(val index: Int, val id: Int, val coord: Coord)

/**
 * The client's side of NPC info: the list of NPCs in view, kept from NPC_INFO_SMALL_V6 and NPC_INFO_LARGE_V6 as the
 * real client keeps it. Of the extended info blocks overhead text ([said]) and hitsplats ([hits]) are read, and only
 * while every NPC's blocks before them in the packet are ones this reader knows the size of: head bars, an animation,
 * facing another entity, overhead text and hitsplats. An NPC with any other block stops the reading there.
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

    fun npcs(): List<NpcView> = inView.toList()

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
        extendedInfo = extended
        readBlocks(payload, bits.alignedBytes(), extended)
    }

    /**
     * The extended info after the bits, one block set per index in [extended], as RSProt's
     * NpcAvatarExtendedInfoDesktopWriter writes them: a flag byte, a second, third and fourth when the one before has its
     * marker bit (0x4, 0x400, 0x200000), then the blocks in the writer's order. Of those, hitsplats, the animation, head
     * bars, overhead text and a face at an entity are read, from RSProt's NpcHeadbarEncoder, NpcSequenceEncoder,
     * NpcFaceEncoder, NpcSayEncoder and NpcHitmarkEncoder; any other flag or face kind stops the reading.
     */
    private fun readBlocks(payload: ByteArray, start: Int, extended: List<Int>) {
        val said = ArrayList<Pair<Int, String>>()
        val hits = ArrayList<Triple<Int, Int, Int>>()
        var position = start
        fun u1(): Int = payload[position++].toInt() and 0xFF
        fun smart(): Int = if ((payload[position].toInt() and 0xFF) < 128) u1() else ((u1() shl 8) or u1()) - 32768
        for (index in extended) {
            if (position >= payload.size) break
            var flags = u1()
            if (flags and EXTENDED_SHORT != 0) flags = flags or (u1() shl 8)
            if (flags and EXTENDED_MEDIUM != 0) flags = flags or (u1() shl 16)
            if (flags and EXTENDED_INT != 0) flags = flags or (u1() shl 24)
            if (flags and (EXTENDED_SHORT or EXTENDED_MEDIUM or EXTENDED_INT or HEADBAR or SEQUENCE or FACE or SAY or HIT).inv() != 0) break
            if (flags and HIT != 0) {
                repeat((128 - u1()) and 0xFF) {
                    val type = smart()
                    val value = smart()
                    smart() // delay
                    smart() // limit (RSProt's NpcHitmarkEncoder writes four smarts a hit)
                    hits += Triple(index, type, value)
                }
            }
            if (flags and SEQUENCE != 0) position += 3 // id, delay
            if (flags and HEADBAR != 0) {
                repeat(u1()) {
                    smart() // type
                    val end = smart()
                    if (end != HEADBAR_REMOVED) {
                        smart() // start time
                        u1() // start fill
                        if (end > 0) u1() // end fill
                    }
                }
            }
            if (flags and SAY != 0) {
                val end = (position until payload.size).firstOrNull { payload[it].toInt() == 0 } ?: break
                said += index to String(payload, position, end - position, Charsets.ISO_8859_1)
                position = end + 1
            }
            if (flags and FACE != 0) {
                // A flag byte (plus 128) with the kind in bits 3-5. Only kind 0, facing an entity, is read: its type, its
                // index (2 bytes, or 4 with the top bit set) and one more smart, as Face.encode writes them.
                if ((((u1() - 128) and 0xFF) shr 3) and 7 != 0) break
                smart()
                position += if ((payload[position].toInt() and 0x80) == 0) 2 else 4
                smart()
            }
        }
        this.said = said
        this.hits = hits
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

        /** Block flags on the wire (RSProt 242's desktop writer), and the markers for a second, third and fourth flag byte. */
        const val EXTENDED_SHORT = 0x4
        const val EXTENDED_MEDIUM = 0x400
        const val EXTENDED_INT = 0x200000
        const val SEQUENCE = 0x2
        const val FACE = 0x20
        const val SAY = 0x10
        const val HIT = 0x400000
        const val HEADBAR = 0x1000000

        /** A head bar's end time that removes it, with no more fields. */
        const val HEADBAR_REMOVED = 32767
    }
}
