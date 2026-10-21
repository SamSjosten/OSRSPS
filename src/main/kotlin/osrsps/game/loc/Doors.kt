package osrsps.game.loc

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

private val logger = KotlinLogging.logger {}

/**
 * Single doors: "Open" swaps a closed door for its open type, swung onto the next tile; "Close" swings it back.
 *
 * Which type a door opens into isn't in the loc types themselves, but Jagex's gameval names pair most doors: a closed
 * door `poordoor` (with an Open option) opens into `poordooropen` or `poordoor_open` (with a Close option). Doors
 * whose names don't pair, such as Lumbridge Castle's kitchen door `hundred_lumbridge_door`, wait for content to
 * [register] them. Double doors (another closed door beside this one in the same wall) are left to content too: both
 * leaves open together, the other way round.
 *
 * Where an opened door goes: a closed door on the west edge of its tile (angle 0) opens onto the tile to the west,
 * turned a quarter clockwise to lie on that tile's north edge, and so on round. That is the usual RS2 engine rule, and
 * the 241 maps agree with it: of the 191 paired open doors they place, 151 have the wall they would close into lined up
 * with this rule and 19 with the opposite turn (mostly left leaves of double doors). Not yet watched on live. Opened
 * doors stay open until closed (guessed: no timer), and door sounds come with task 12.
 */
class Doors(private val locs: Locs) {
    private val openTypes = HashMap<Int, Int>()
    private val closedTypes = HashMap<Int, Int>()

    init {
        val types = locs.types
        for (closed in types.all) {
            val name = closed.internalName ?: continue
            if (closed.op(OPEN) == 0) continue
            val open = (types.byInternalName(name + "open") ?: types.byInternalName(name + "_open")) ?: continue
            if (open.op(CLOSE) == 0) continue
            register(closed.id, open.id)
        }
        if (openTypes.isNotEmpty()) logger.info { "Paired ${openTypes.size} door types by their gameval names" }
    }

    /** Makes [closed] open into [open] and [open] close into [closed], replacing any pairing either had. */
    fun register(closed: Int, open: Int) {
        openTypes[closed] = open
        closedTypes[open] = closed
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
        val open = openTypes[loc.id]
        if (open != null && type.op(OPEN) == option) return if (isDoubleDoor(loc)) null else open(loc, open)
        val closed = closedTypes[loc.id]
        if (closed != null && type.op(CLOSE) == option) return close(loc, closed)
        return null
    }

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

    private fun open(door: ZoneLoc, openType: Int): ZoneLoc {
        val (dx, dz) = SWING[door.angle]
        val opened = ZoneLoc(openType, door.shape, (door.angle + 1) and 3, door.x + dx, door.z + dz, door.level)
        locs.remove(door)
        locs.add(opened)
        return opened
    }

    private fun close(door: ZoneLoc, closedType: Int): ZoneLoc {
        val angle = (door.angle + 3) and 3
        val (dx, dz) = SWING[angle]
        val closed = ZoneLoc(closedType, door.shape, angle, door.x - dx, door.z - dz, door.level)
        if (locs.cached(closed.x, closed.z, closed.level, closed.layer) == closed && locs.cached(door.x, door.z, door.level, door.layer) != door) {
            // Closing a door the cache has closed: both tiles go back to the cache's, so nothing is left to send.
            locs.restore(door.x, door.z, door.level, door.layer)
            locs.restore(closed.x, closed.z, closed.level, closed.layer)
        } else {
            locs.remove(door)
            locs.add(closed)
        }
        return closed
    }

    companion object {
        const val OPEN = "Open"
        const val CLOSE = "Close"

        /** By a closed door's angle: the tile its open type goes on. */
        private val SWING = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
    }
}
