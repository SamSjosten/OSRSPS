package osrsps.game.loc

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

private val logger = KotlinLogging.logger {}

/**
 * Fence gates: two leaves in a wall, the left one and the right one beside it, that swing open together (Draynor's pig
 * pen and farm, Lumbridge's fields). A gate type pairs with its open type by gameval name: `fencegate_l` with
 * `openfencegate_l`, `farming_fencegate_r` with `farming_openfencegate_r`, and so on for every `…fencegate_l`/`_r` with
 * an Open option whose `…openfencegate_…` type has a Close option; and the field gates north of Lumbridge,
 * `fai_varrock_gate_l`/`_r`, with `fai_varrock_gate_lc`/`_rc`.
 *
 * Opened, the leaves turn three quarters clockwise (a quarter anticlockwise) and move: the left one a tile outward,
 * the right one two tiles outward and a tile back along the wall, so both stand against the left leaf's hinge side.
 * That is RSMod's picket gate rule (rev 233), which its authors took from live; not yet watched on our own recording,
 * so guessed. Gates stay open until closed, like [Doors] (RSMod closes them again after 500 ticks; no source we
 * have says live does).
 */
class Gates(private val locs: Locs) {
    private class Leaf(val left: Boolean, val open: Boolean, val partner: Int, val otherLeaf: Int?)

    /** By each of a gate's four types: which leaf it is, open or not, and its other state. */
    private val leaves = HashMap<Int, Leaf>()

    init {
        val types = locs.types
        val byName = HashMap<String, LocType>()
        for (type in types.all) type.internalName?.let { byName[it] = type }
        for (closed in types.all) {
            val name = closed.internalName ?: continue
            if (!name.endsWith("_l") || name.contains("openfencegate")) continue
            val stem = name.dropLast(2)
            // `…fencegate_l` opens into `…openfencegate_l`; the Varrock-style field gates `…_gate_l` into `…_gate_lc`.
            val (leftOpenName, rightOpenName) = when {
                stem.endsWith("fencegate") -> open(stem) + "_l" to open(stem) + "_r"
                stem.endsWith("_gate") -> stem + "_lc" to stem + "_rc"
                else -> continue
            }
            val leftOpen = byName[leftOpenName] ?: continue
            val right = byName[stem + "_r"]
            val rightOpen = byName[rightOpenName]
            if (closed.op(Doors.OPEN) == 0 || leftOpen.op(Doors.CLOSE) == 0) continue
            val hasRight = right != null && rightOpen != null && right.op(Doors.OPEN) != 0 && rightOpen.op(Doors.CLOSE) != 0
            leaves[closed.id] = Leaf(left = true, open = false, partner = leftOpen.id, otherLeaf = right?.id.takeIf { hasRight })
            leaves[leftOpen.id] = Leaf(left = true, open = true, partner = closed.id, otherLeaf = rightOpen?.id.takeIf { hasRight })
            if (hasRight) {
                leaves[right!!.id] = Leaf(left = false, open = false, partner = rightOpen!!.id, otherLeaf = closed.id)
                leaves[rightOpen.id] = Leaf(left = false, open = true, partner = right.id, otherLeaf = leftOpen.id)
            }
        }
        if (leaves.isNotEmpty()) logger.info { "Paired ${leaves.size / 2} gate leaf types by their gameval names" }
    }

    private fun open(stem: String): String = stem.replace("fencegate", "openfencegate")

    /** Whether [id] is a gate leaf, open or closed. */
    fun isGate(id: Int): Boolean = id in leaves

    /**
     * Opens or closes the gate [loc] is a leaf of, with its other leaf when that is in place, if [option] is the leaf's
     * Open or Close. Returns the clicked leaf as it is now, or null if [loc] isn't a gate that option works on.
     */
    fun toggle(loc: ZoneLoc, option: Int): ZoneLoc? {
        val leaf = leaves[loc.id] ?: return null
        if (loc.shape != LocShape.WALL_STRAIGHT || !locs.exists(loc)) return null
        val type = locs.types[loc.id] ?: return null
        if (option != type.op(if (leaf.open) Doors.CLOSE else Doors.OPEN)) return null
        // The other leaf is PAIR of the leaves' angle from the left one, closed or open (the open leaves' moves keep it
        // so), and back by it from the right one.
        val (px, pz) = PAIR[loc.angle]
        val toOther = if (leaf.left) 1 else -1
        val (ox, oz) = px * toOther to pz * toOther
        val other = leaf.otherLeaf?.let { id ->
            locs[loc.x + ox, loc.z + oz, loc.level, loc.layer]?.takeIf { it.id == id && it.shape == loc.shape && it.angle == loc.angle }
        }
        val moved = swing(loc, leaf)
        if (other != null) swing(other, leaves.getValue(other.id))
        return moved
    }

    private fun swing(loc: ZoneLoc, leaf: Leaf): ZoneLoc {
        return if (!leaf.open) {
            val (dx, dz) = (if (leaf.left) LEFT_OPEN else RIGHT_OPEN)[loc.angle]
            val opened = ZoneLoc(leaf.partner, loc.shape, (loc.angle + OPEN_TURN) and 3, loc.x + dx, loc.z + dz, loc.level)
            locs.remove(loc)
            locs.add(opened)
            opened
        } else {
            val angle = (loc.angle - OPEN_TURN) and 3
            val (dx, dz) = (if (leaf.left) LEFT_OPEN else RIGHT_OPEN)[angle]
            val closed = ZoneLoc(leaf.partner, loc.shape, angle, loc.x - dx, loc.z - dz, loc.level)
            val cached = locs.cached(closed.x, closed.z, closed.level, closed.layer)
            if (cached == closed && locs.cached(loc.x, loc.z, loc.level, loc.layer) != loc) {
                // Back where the cache has it: both tiles go back to the cache's, so nothing is left to send.
                locs.restore(loc.x, loc.z, loc.level, loc.layer)
                locs.restore(closed.x, closed.z, closed.level, closed.layer)
            } else {
                locs.remove(loc)
                locs.add(closed)
            }
            closed
        }
    }

    companion object {
        /** Quarter turns clockwise a leaf makes as it opens (RSMod's turnAngle(3)). */
        private const val OPEN_TURN = 3

        /** By a closed gate's angle (0 west wall to 3 south): where the right leaf is from the left one. */
        private val PAIR = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)

        /** By a closed leaf's angle: how far the left and the right leaf move as they open. */
        private val LEFT_OPEN = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
        private val RIGHT_OPEN = listOf(-2 to -1, -1 to 2, 2 to 1, 1 to -2)
    }
}
