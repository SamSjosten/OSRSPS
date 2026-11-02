package osrsps.game

/**
 * The Wilderness level of a tile, as the client's overlay shows it: level 1 at the ditch (z 3520, the first two tiles
 * past it being safe for combat but not for teleports) and one more every 8 tiles north, to 56 in the far north
 * (verified on the OSRS Wiki's Wilderness level page, which gives no formula). The formula itself, `(z - 3520) / 8 + 1`
 * between x 2944 and 3391, and the same band 6400 tiles up for the Wilderness dungeons, is RuneLite's and the
 * community's; inferred, not checked against the client's script. Teleports stop working above [TELEPORT_LIMIT].
 */
object Wilderness {
    const val TELEPORT_LIMIT = 20

    /** The level of [x], [z], or 0 outside the Wilderness. */
    fun level(x: Int, z: Int): Int {
        if (x !in WEST..EAST) return 0
        return when (z) {
            in SOUTH..NORTH -> (z - SOUTH) / 8 + 1
            in SOUTH + UNDERGROUND..NORTH + UNDERGROUND -> (z - SOUTH - UNDERGROUND) / 8 + 1
            else -> 0
        }
    }

    private const val WEST = 2944
    private const val EAST = 3391
    private const val SOUTH = 3520
    private const val NORTH = 3967
    private const val UNDERGROUND = 6400
}
