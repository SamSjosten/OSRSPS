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

    /**
     * The rows north of the ditch where players can't fight: the Wiki's Wilderness page says the first two steps of the
     * Wilderness are safe, players there can't attack or be attacked. Inferred: the two rows just past the ditch's, 3523
     * and 3524, all along the surface's width; the caves have no ditch, so none there.
     */
    val SAFE_ROWS = DITCH_NORTH + 1..DITCH_NORTH + 2

    /**
     * The Wilderness level players fight by at [x], [z]: [level] inside the Wilderness past its safe rows, else 0, where
     * nobody can attack a player or be attacked by one.
     */
    fun pvpLevel(x: Int, z: Int): Int {
        if (!inside(x, z)) return 0
        if (z in SAFE_ROWS && SURFACE.contains(x, z)) return 0
        if (inFerox(x, z)) return 0
        return level(x, z)
    }

    /**
     * Ferox Enclave's safe area (step W2.3), levels 13 to 16, where nobody can attack a player or be attacked by one
     * (the Wiki's Ferox Enclave and Barrier pages): the surface tiles of [FEROX_ROWS]. INFERRED from the 242 map (round
     * 37): every tile of the town's rectangle, x 3123 to 3155 and z 3617 to 3645, that can't be walked to from outside
     * without a barrier, so the walls and its four barriers are its only edges, and all of the town inside them (its
     * north-east corner past z 3639 too) is safe. The Wiki's safe tiles around each barrier outside wait for a recording.
     */
    fun inFerox(x: Int, z: Int): Boolean = FEROX_ROWS[z]?.any { x in it } == true

    /**
     * [inFerox]'s tiles, row by row: each z's runs of x. Worked out from the abextm/osrs-cache 2026-11-11-rev242 map
     * by walking out from outside the town; [osrsps.audit.FeroxAuditTest] checks them against the cache.
     */
    val FEROX_ROWS: Map<Int, List<IntRange>> = mapOf(
        3617 to listOf(3125..3140, 3145..3146),
        3618 to listOf(3125..3143, 3145..3146, 3154..3154),
        3619 to listOf(3125..3150),
        3620 to listOf(3125..3151),
        3621 to listOf(3125..3151),
        3622 to listOf(3123..3152, 3154..3154),
        3623 to listOf(3123..3153),
        3624 to listOf(3123..3153),
        3625 to listOf(3123..3154),
        3626 to listOf(3123..3155),
        3627 to listOf(3123..3155),
        3628 to listOf(3123..3155),
        3629 to listOf(3123..3155),
        3630 to listOf(3123..3155),
        3631 to listOf(3123..3155),
        3632 to listOf(3123..3155),
        3633 to listOf(3123..3155),
        3634 to listOf(3123..3154),
        3635 to listOf(3123..3154),
        3636 to listOf(3123..3123, 3125..3155),
        3637 to listOf(3125..3155),
        3638 to listOf(3125..3155),
        3639 to listOf(3123..3123, 3125..3155),
        3640 to listOf(3123..3123, 3132..3133, 3136..3155),
        3641 to listOf(3123..3124, 3126..3127, 3132..3133, 3136..3155),
        3642 to listOf(3123..3128, 3138..3155),
        3643 to listOf(3123..3128, 3138..3155),
        3644 to listOf(3123..3124, 3127..3128, 3136..3155),
        3645 to listOf(3123..3123, 3128..3128, 3136..3155),
    )

    /** The rectangle [FEROX_ROWS] lies in. */
    const val FEROX_WEST = 3123
    const val FEROX_EAST = 3155
    const val FEROX_SOUTH = 3617
    const val FEROX_NORTH = 3645

    /**
     * Whether players of combat levels [a] and [b], fighting by Wilderness levels [levelA] and [levelB], are in range of
     * each other: the gap in combat levels is at most the Wilderness level (the Wiki's Wilderness page: level 100 at
     * level 24 attacks 76 to 124). Inferred: each player's own level must cover it, so the lower of the two counts.
     */
    fun inRange(a: Int, levelA: Int, b: Int, levelB: Int): Boolean = Math.abs(a - b) <= minOf(levelA, levelB)

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
