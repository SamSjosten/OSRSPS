package osrsps.game

/**
 * The Wilderness level of a tile, as the client's overlay shows it, and whether a tile counts as inside the Wilderness.
 *
 * [level] is the client's own sum: client script proc `wilderness_level` (384 in the 242 cache, osrs-dumps
 * 2026-11-11-rev242), which `pvp_icons_wildernesslevel` draws as "Level: n" every few client cycles while the overlay is
 * open. On the surface that is one level every 8 tiles from z 3520, between x 2944 and 3391, with a band of fixed or
 * shifted levels in the Wilderness's caves; the OSRS Wiki's Wilderness page agrees (level 1 at the ditch, 56 in the far
 * north). The script reads every plane alike, and so does this. Teleports stop working above [TELEPORT_LIMIT].
 */
object Wilderness {
    const val TELEPORT_LIMIT = 20

    /** The level of [x], [z], or 0 outside the Wilderness. The checks run in the client script's order. */
    fun level(x: Int, z: Int): Int = when {
        DEEP_NORTH_EAST.contains(x, z) && !DEEP_NORTH_EAST_HOLE.contains(x, z) -> 5
        SURFACE.contains(x, z) -> (z - SURFACE.minZ) / 8 + 1
        CAVE_SHIFTED.contains(x, z) -> (z - UNDERGROUND_SOUTH) / 8 - 1
        CAVE_35_WEST.contains(x, z) || CAVE_35_EAST.contains(x, z) -> 35
        CAVE_40.contains(x, z) -> 40
        CAVE_21_WEST.contains(x, z) || CAVE_21_EAST.contains(x, z) -> 21
        CAVE_29.contains(x, z) -> 29
        // The script's scale(a, b, c) is a * c / b: 33 rising to 40 across the square.
        CAVE_SLOPE.contains(x, z) -> 33 + (Math.floorMod(z, 64) - 6) * 7 / 50
        UNDERGROUND.contains(x, z) -> (z - UNDERGROUND_SOUTH) / 8 + 1
        else -> 0
    }

    /**
     * Whether [x], [z] is inside the Wilderness: it has a level and isn't south of the ditch. The client's sum gives level
     * 1 from z 3520, but the ditch (`ditch_wilderness_cover`, 1 by 2 tiles at z 3521 in the 242 cache's map) covers 3521
     * and 3522, so 3520 is still Misthalin's side. Inferred: live opens the overlay north of the ditch only, as the
     * Wiki's Wilderness Ditch page says the ditch marks where the Wilderness starts; nobody here has seen it at 3520.
     */
    fun inside(x: Int, z: Int): Boolean = level(x, z) > 0 && z !in SURFACE.minZ..DITCH_NORTH

    /** The ditch's northern row; the Wilderness proper starts on the row after it. */
    const val DITCH_NORTH = 3522

    /** Where the client's sum puts the caves' level 1: the surface's 3520, 6,400 tiles up. */
    private const val UNDERGROUND_SOUTH = 155 * 64

    /** A box of whole map squares, as the script's `inzone` takes them: [minX] to [maxX], [minZ] to [maxZ], inclusive. */
    private class Box(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
        fun contains(x: Int, z: Int) = x in minX..maxX && z in minZ..maxZ
    }

    /** Map squares [fromX], [fromZ] to [toX], [toZ], whole. */
    private fun squares(fromX: Int, fromZ: Int, toX: Int, toZ: Int) = Box(fromX * 64, fromZ * 64, toX * 64 + 63, toZ * 64 + 63)

    private val DEEP_NORTH_EAST = squares(52, 62, 54, 64)
    private val DEEP_NORTH_EAST_HOLE = Box(53 * 64 + 21, 63 * 64 + 21, 53 * 64 + 42, 63 * 64 + 42)
    private val SURFACE = squares(46, 55, 52, 67)
    private val CAVE_SHIFTED = squares(47, 158, 47, 158)
    private val CAVE_35_WEST = squares(51, 159, 51, 159)
    private val CAVE_35_EAST = squares(53, 159, 53, 159)
    private val CAVE_40 = squares(52, 161, 52, 161)
    private val CAVE_21_WEST = squares(27, 180, 27, 180)
    private val CAVE_21_EAST = squares(29, 180, 29, 180)
    private val CAVE_29 = squares(25, 180, 25, 180)
    private val CAVE_SLOPE = squares(52, 160, 52, 160)
    private val UNDERGROUND = squares(46, 155, 53, 169)
}
