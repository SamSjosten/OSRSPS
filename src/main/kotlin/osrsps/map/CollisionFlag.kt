package osrsps.map

/**
 * Collision flag bits, as the client's collision map stores them. North is +z.
 *
 * A wall flag on a tile means that side (or corner) of the tile is blocked. The `_PROJ` variants are the same walls
 * for projectiles and line of sight; walking ignores them.
 */
object CollisionFlag {
    const val WALL_NORTH_WEST = 0x1
    const val WALL_NORTH = 0x2
    const val WALL_NORTH_EAST = 0x4
    const val WALL_EAST = 0x8
    const val WALL_SOUTH_EAST = 0x10
    const val WALL_SOUTH = 0x20
    const val WALL_SOUTH_WEST = 0x40
    const val WALL_WEST = 0x80

    /** A solid loc (a tree, a table, a fountain) covers the tile. */
    const val LOC = 0x100

    /** Projectile walls are the walking walls shifted left by this much. */
    const val PROJ_SHIFT = 9
    const val LOC_PROJ = 0x20000

    /** A ground decoration that blocks walking. */
    const val FLOOR_DECORATION = 0x40000

    /** The tile itself can't be walked on: water, a cliff, the edge of the world. */
    const val FLOOR = 0x200000

    /** Set by the client for tiles it has no map for. */
    const val UNLOADED = 0x1000000

    /**
     * The tile is under a roof (bit 4 of its map settings). Ours only: the client keeps roofs out of its collision map.
     * NPCs restricted to indoors or outdoors read it; walking ignores it.
     */
    const val ROOF = 0x4000000

    /** Any of these on a tile stops a player standing on it. */
    const val BLOCK_WALK = LOC or FLOOR_DECORATION or FLOOR or UNLOADED
}
