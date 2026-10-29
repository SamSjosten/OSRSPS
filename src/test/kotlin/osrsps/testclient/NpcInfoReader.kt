package osrsps.testclient

/** Overhead text an NPC was shown saying, and the tick count the client had seen when it came. */
data class NpcSaid(val index: Int, val text: String, val ticks: Int)

/** An NPC the client can see: its index in the world, its type id and its tile. */
data class NpcView(val index: Int, val id: Int, val coord: Coord)

/**
 * The client's side of NPC info: the list of NPCs in view, kept from NPC_INFO_SMALL_V6 and NPC_INFO_LARGE_V6 as the
 * real client keeps it. Of the extended info blocks only overhead text ([said]) is read, and only while every block
 * before it in the packet is one: an NPC with any other block stops the reading there.
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
            val dz = signed(bits.read(deltaBits), deltaBits)
            bits.read(1) // jump: whether the client snaps it there rather than walking it
            bits.read(3) // direction it faces
            if (bits.read(1) == 1) {
                val clockBits = SPAWN_CLOCK_BITS[bits.read(2)]
                bits.read(clockBits) // spawn cycle
            }
            if (bits.read(1) == 1) extended += index
            val id = bits.read(TYPE_BITS[bits.read(2)])
            val dx = signed(bits.read(deltaBits), deltaBits)
            kept += NpcView(index, id, Coord(local.x + dx, local.z + dz, local.level))
        }
        inView.clear()
        inView += kept
        extendedInfo = extended
        said = readSaid(payload, bits.alignedBytes(), extended)
    }

    /**
     * The extended info after the bits, one block set per index in [extended], as RSProt's
     * NpcAvatarExtendedInfoDesktopWriter writes them: a flag byte, a second, third and fourth when the one before has its
     * marker bit (0x10, 0x2000, 0x40000), then the blocks. A say block alone ([SAY], a string ended by 0) is read.
     */
    private fun readSaid(payload: ByteArray, start: Int, extended: List<Int>): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var position = start
        fun u1(): Int = payload[position++].toInt() and 0xFF
        for (index in extended) {
            if (position >= payload.size) break
            var flags = u1()
            if (flags and 0x10 != 0) flags = flags or (u1() shl 8)
            if (flags and 0x2000 != 0) flags = flags or (u1() shl 16)
            if (flags and 0x40000 != 0) flags = flags or (u1() shl 24)
            if (flags and (0x10 or 0x2000 or 0x40000).inv() != SAY) break
            val end = (position until payload.size).firstOrNull { payload[it].toInt() == 0 } ?: break
            out += index to String(payload, position, end - position, Charsets.ISO_8859_1)
            position = end + 1
        }
        return out
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

        /** The say block's flag on the wire (RSProt's desktop writer). */
        const val SAY = 0x40
    }
}
