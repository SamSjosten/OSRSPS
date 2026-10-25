package osrsps.game.loc

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

private val logger = KotlinLogging.logger {}

/**
 * Single doors: "Open" swaps a closed door for its open type, swung onto the next tile; "Close" swings it back.
 *
 * Which type a door opens into isn't in the loc types themselves, but Jagex's gameval names pair most doors: a closed
 * door `poordoor` (with an Open option) opens into `poordooropen` or `poordoor_open` (with a Close option), and
 * `poordoor_m` into `poordooropen_m` (see [openNames]). Doors
 * whose names don't pair, such as Lumbridge Castle's kitchen door `hundred_lumbridge_door`, wait for content to
 * [register] them, or to [addClosedType] when they open into a type another door already pairs with. Double doors
 * (another closed door beside this one in the same wall) open only once content has given their types to
 * [registerDouble]; both leaves then open together, the left one turning the other way round.
 *
 * Where an opened door goes: a closed door on the west edge of its tile (angle 0) opens onto the tile to the west,
 * turned a quarter clockwise to lie on that tile's north edge, and so on round. That is the usual RS2 engine rule, and
 * the 241 maps agree with it: of the 191 paired open doors they place, 151 have the wall they would close into lined up
 * with this rule and 19 with the opposite turn (mostly left leaves of double doors). Not yet watched on live. Opened
 * doors stay open until closed (guessed: no timer), and play no sound until a recording gives the ids.
 */
class Doors(private val locs: Locs) {
    private val openTypes = HashMap<Int, Int>()
    private val closedTypes = HashMap<Int, Int>()

    /** Registered double doors, by each of their four types. */
    private val doubleDoors = HashMap<Int, DoubleDoor>()

    /**
     * The types of a pair of double doors. Closed, the right leaf is the next tile clockwise along the wall from the left
     * one: north of it when the wall runs along the west edge (angle 0).
     */
    private class DoubleDoor(val leftClosed: Int, val leftOpen: Int, val rightClosed: Int, val rightOpen: Int) {
        fun leaf(left: Boolean, open: Boolean): Int = if (left) (if (open) leftOpen else leftClosed) else (if (open) rightOpen else rightClosed)
    }

    init {
        val types = locs.types
        for (closed in types.all) {
            val name = closed.internalName ?: continue
            if (closed.op(OPEN) == 0) continue
            val open = openNames(name).firstNotNullOfOrNull { types.byInternalName(it) } ?: continue
            if (open.op(CLOSE) == 0) continue
            register(closed.id, open.id)
        }
        if (openTypes.isNotEmpty()) logger.info { "Paired ${openTypes.size} door types by their gameval names" }
    }

    /**
     * The names [closed]'s open type may have: `open` or `_open` after the name, or after its last "door" when
     * something follows (`poordoor_m` opens into `poordooropen_m`, north of Lumbridge Castle). Trapdoors aren't wall
     * doors, so the second form leaves them out.
     */
    private fun openNames(closed: String): List<String> {
        val names = mutableListOf(closed + "open", closed + "_open")
        val door = closed.lastIndexOf("door")
        val end = door + "door".length
        if (door >= 0 && end < closed.length && !closed.contains("trapdoor")) {
            names += closed.substring(0, end) + "open" + closed.substring(end)
            names += closed.substring(0, end) + "_open" + closed.substring(end)
        }
        return names
    }

    /** Makes [closed] open into [open] and [open] close into [closed], replacing any pairing either had. */
    fun register(closed: Int, open: Int) {
        // Unlink both old partners, so a replaced open type can't still close into [closed] and [open]'s old closed
        // type can't still open into it.
        openTypes.remove(closed)?.let { closedTypes.remove(it) }
        closedTypes.remove(open)?.let { openTypes.remove(it) }
        openTypes[closed] = open
        closedTypes[open] = closed
    }

    /**
     * Makes [closed] open into [open] as well, leaving whatever [open] closes into elsewhere as it is (or [closed], if it
     * had no partner). Where the cache has a [closed] door, closing the opened one puts the [closed] type back.
     */
    fun addClosedType(closed: Int, open: Int) {
        openTypes[closed] = open
        closedTypes.putIfAbsent(open, closed)
    }

    /**
     * Registers double doors: each leaf's closed and open type. Opening or closing either leaf moves the other with it
     * when it is in place. Several pairs can share open types; closing then goes back to the cache's types.
     */
    fun registerDouble(leftClosed: Int, leftOpen: Int, rightClosed: Int, rightOpen: Int) {
        val door = DoubleDoor(leftClosed, leftOpen, rightClosed, rightOpen)
        for (id in listOf(leftClosed, leftOpen, rightClosed, rightOpen)) doubleDoors[id] = door
    }

    fun openType(closed: Int): Int? = openTypes[closed]

    fun closedType(open: Int): Int? = closedTypes[open]

    /**
     * Opens or closes [loc] if it is a door and [option] is its Open or Close. Returns the door as it is now, or null
     * if [loc] isn't a door that option works on.
     */
    fun toggle(loc: ZoneLoc, option: Int): ZoneLoc? {
        // Diagonal doors (shape 9) would need their own rule.
        if (loc.shape != LocShape.WALL_STRAIGHT || !locs.exists(loc)) return null
        val type = locs.types[loc.id] ?: return null
        val double = doubleDoors[loc.id]
        if (double != null) return toggleDouble(loc, type, option, double)
        val open = openTypes[loc.id]
        if (open != null && type.op(OPEN) == option) return if (isDoubleDoor(loc)) null else open(loc, open)
        val closed = closedTypes[loc.id]
        if (closed != null && type.op(CLOSE) == option) return close(loc, closed)
        return null
    }

    /**
     * Opens or closes a leaf of [door] and the other leaf with it, if that one is in place. The right leaf swings as a
     * single door does; the left one onto the same tile but turned the other way, so the two open apart. RSMod's rule
     * (rev 233); not yet watched on live.
     */
    private fun toggleDouble(leaf: ZoneLoc, type: LocType, option: Int, door: DoubleDoor): ZoneLoc? {
        val opening = leaf.id == door.leftClosed || leaf.id == door.rightClosed
        if (option != type.op(if (opening) OPEN else CLOSE)) return null
        val left = leaf.id == door.leftClosed || leaf.id == door.leftOpen
        // Open or closed, the right leaf is the next tile clockwise along the closed wall from the left one.
        val wallAngle = if (opening) leaf.angle else (leaf.angle - turn(left)) and 3
        val (ax, az) = SWING[(wallAngle + 1) and 3]
        val step = if (left) 1 else -1
        val otherAngle = if (opening) wallAngle else (wallAngle + turn(!left)) and 3
        val other = locs[leaf.x + step * ax, leaf.z + step * az, leaf.level, leaf.layer]
            ?.takeIf { it.id == door.leaf(!left, open = !opening) && it.shape == leaf.shape && it.angle == otherAngle }
        if (other != null) swing(other, !left, opening, door)
        return swing(leaf, left, opening, door)
    }

    private fun swing(leaf: ZoneLoc, left: Boolean, opening: Boolean, door: DoubleDoor): ZoneLoc =
        if (opening) open(leaf, door.leaf(left, open = true), turn(left)) else close(leaf, door.leaf(left, open = false), turn(left))

    private fun turn(left: Boolean) = if (left) LEFT_TURN else RIGHT_TURN

    /** Whether the wall tile on either side of a closed [door], along its wall, holds another closed door facing the same way. */
    private fun isDoubleDoor(door: ZoneLoc): Boolean {
        val alongZ = door.angle and 1 == 0
        return listOf(-1, 1).any { step ->
            val x = if (alongZ) door.x else door.x + step
            val z = if (alongZ) door.z + step else door.z
            val other = locs[x, z, door.level, door.layer] ?: return@any false
            other.shape == door.shape && other.angle == door.angle && (locs.types[other.id]?.op(OPEN) ?: 0) != 0
        }
    }

    /** Swings [door] open as [openType], turned [turn] quarters clockwise. */
    private fun open(door: ZoneLoc, openType: Int, turn: Int = RIGHT_TURN): ZoneLoc {
        val (dx, dz) = SWING[door.angle]
        val opened = ZoneLoc(openType, door.shape, (door.angle + turn) and 3, door.x + dx, door.z + dz, door.level)
        locs.remove(door)
        locs.add(opened)
        return opened
    }

    /**
     * Swings [door] shut, undoing [open] with the same [turn]. It becomes [closedType], unless the cache has a door there
     * that opens into this one: then it goes back to the cache's type, as several closed types can share an open one.
     */
    private fun close(door: ZoneLoc, closedType: Int, turn: Int = RIGHT_TURN): ZoneLoc {
        val angle = (door.angle - turn) and 3
        val (dx, dz) = SWING[angle]
        val cachedDoor = locs.cached(door.x - dx, door.z - dz, door.level, door.layer)
            ?.takeIf { it.shape == door.shape && it.angle == angle && opensInto(it.id) == door.id }
        val closed = ZoneLoc(cachedDoor?.id ?: closedType, door.shape, angle, door.x - dx, door.z - dz, door.level)
        if (cachedDoor != null && locs.cached(door.x, door.z, door.level, door.layer) != door) {
            // Closing a door the cache has closed: both tiles go back to the cache's, so nothing is left to send.
            locs.restore(door.x, door.z, door.level, door.layer)
            locs.restore(closed.x, closed.z, closed.level, closed.layer)
        } else {
            locs.remove(door)
            locs.add(closed)
        }
        return closed
    }

    /** What the closed door type [closed] opens into, as a single door or a leaf of a double door. */
    private fun opensInto(closed: Int): Int? = openTypes[closed] ?: doubleDoors[closed]?.let {
        when (closed) {
            it.leftClosed -> it.leftOpen
            it.rightClosed -> it.rightOpen
            else -> null
        }
    }

    companion object {
        const val OPEN = "Open"
        const val CLOSE = "Close"

        /** Quarter turns clockwise a door makes as it opens: a single door or a right leaf one, a left leaf three. */
        private const val RIGHT_TURN = 1
        private const val LEFT_TURN = 3

        /** By a closed door's angle: the tile its open type goes on. */
        private val SWING = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
    }
}
