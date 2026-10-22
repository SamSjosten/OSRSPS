package osrsps.game

import kotlin.math.atan2

/** Which way one thing looks at another, in the client's angles. */
object Facing {
    /**
     * The angle something [fromSize] tiles square at [fromX], [fromZ] turns to so it looks at something [toSize] tiles
     * square at [toX], [toZ]: 0 south, 512 west, 1024 north, 1536 east, out of 2048. Measured between their centres
     * with the client's own sum, `atan2(dx, dz) * 325.949 & 2047`. Null when the centres are the same.
     */
    fun angle(fromX: Int, fromZ: Int, fromSize: Int, toX: Int, toZ: Int, toSize: Int): Int? {
        // Twice each centre, so a 2x2's centre on a tile corner stays a whole number.
        val dx = (fromX * 2 + fromSize) - (toX * 2 + toSize)
        val dz = (fromZ * 2 + fromSize) - (toZ * 2 + toSize)
        if (dx == 0 && dz == 0) return null
        return (atan2(dx.toDouble(), dz.toDouble()) * 325.949).toInt() and 0x7FF
    }
}
