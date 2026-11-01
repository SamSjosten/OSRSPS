package osrsps.map

/**
 * Collision flags for the whole world, stored per 8x8 zone and allocated only where something is flagged.
 * Tiles in map squares the cache has no terrain for read as [CollisionFlag.UNLOADED], so nobody walks off the map.
 */
class CollisionMap {
    private val zones = HashMap<Int, IntArray>()
    private val loadedSquares = HashSet<Int>()

    /** Marks the 64x64 map square at [squareX], [squareZ] as part of the world, on every level. */
    fun markLoaded(squareX: Int, squareZ: Int) {
        loadedSquares.add(squareKey(squareX, squareZ))
    }

    operator fun get(x: Int, z: Int, level: Int): Int {
        if (x < 0 || z < 0 || level !in 0..3) return CollisionFlag.UNLOADED
        // A wall or large loc on a square's edge can flag tiles in a neighbouring square with no terrain; those
        // tiles still read as unloaded.
        if (squareKey(x shr 6, z shr 6) !in loadedSquares) return CollisionFlag.UNLOADED
        return zones[zoneKey(x, z, level)]?.get(tileIndex(x, z)) ?: 0
    }

    /** A tile's flags as stored, even in a square with no terrain (which [get] reads as unloaded). */
    fun stored(x: Int, z: Int, level: Int): Int {
        if (x < 0 || z < 0 || level !in 0..3) return 0
        return zones[zoneKey(x, z, level)]?.get(tileIndex(x, z)) ?: 0
    }

    /** Replaces a tile's flags. */
    fun set(x: Int, z: Int, level: Int, flags: Int) {
        if (x < 0 || z < 0 || level !in 0..3) return
        val zone = zones[zoneKey(x, z, level)] ?: if (flags == 0) return else IntArray(64).also { zones[zoneKey(x, z, level)] = it }
        zone[tileIndex(x, z)] = flags
    }

    fun add(x: Int, z: Int, level: Int, mask: Int) {
        if (x < 0 || z < 0 || level !in 0..3) return
        val zone = zones.getOrPut(zoneKey(x, z, level)) { IntArray(64) }
        zone[tileIndex(x, z)] = zone[tileIndex(x, z)] or mask
    }

    /**
     * Clears [mask] on a tile, as the client does when a loc is removed: a flag two locs both set goes with either.
     * [osrsps.game.loc.Locs] doesn't use it for that reason; it works each changed tile out again from the locs on it.
     */
    fun remove(x: Int, z: Int, level: Int, mask: Int) {
        if (x < 0 || z < 0 || level !in 0..3) return
        val zone = zones[zoneKey(x, z, level)] ?: return
        zone[tileIndex(x, z)] = zone[tileIndex(x, z)] and mask.inv()
    }

    private fun squareKey(squareX: Int, squareZ: Int) = (squareX shl 8) or squareZ

    private fun zoneKey(x: Int, z: Int, level: Int) = (level shl 28) or ((x shr 3) shl 14) or (z shr 3)

    private fun tileIndex(x: Int, z: Int) = ((x and 7) shl 3) or (z and 7)
}
