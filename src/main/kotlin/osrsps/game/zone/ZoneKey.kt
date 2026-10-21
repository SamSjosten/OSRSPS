package osrsps.game.zone

/**
 * One zone: 8x8 tiles on one level, the unit the client takes loc, ground item, graphic and projectile updates in.
 * Packed as `x | z << 11 | level << 22`, with x and z counted in zones (tile coordinate shr 3).
 */
@JvmInline
value class ZoneKey(val packed: Int) {
    constructor(x: Int, z: Int, level: Int) : this(x or (z shl 11) or (level shl 22))

    /** In zones, 0 to 2047. */
    val x: Int get() = packed and COORD_MASK
    val z: Int get() = (packed ushr 11) and COORD_MASK
    val level: Int get() = packed ushr 22

    /** The south-west tile. */
    val baseX: Int get() = x shl 3
    val baseZ: Int get() = z shl 3

    override fun toString() = "Zone($x, $z, $level)"

    companion object {
        const val SIZE = 8
        private const val COORD_MASK = 0x7FF

        /** The zone holding the tile. */
        fun of(x: Int, z: Int, level: Int) = ZoneKey(x shr 3, z shr 3, level)
    }
}
