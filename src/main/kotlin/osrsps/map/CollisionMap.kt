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
        val zone = zones[zoneKey(x, z, level)]
        if (zone != null) return zone[tileIndex(x, z)]
        return if (squareKey(x shr 6, z shr 6) in loadedSquares) 0 else CollisionFlag.UNLOADED
    }

    fun add(x: Int, z: Int, level: Int, mask: Int) {
        if (x < 0 || z < 0 || level !in 0..3) return
        val zone = zones.getOrPut(zoneKey(x, z, level)) { IntArray(64) }
        zone[tileIndex(x, z)] = zone[tileIndex(x, z)] or mask
    }

    private fun squareKey(squareX: Int, squareZ: Int) = (squareX shl 8) or squareZ

    private fun zoneKey(x: Int, z: Int, level: Int) = (level shl 28) or ((x shr 3) shl 14) or (z shr 3)

    private fun tileIndex(x: Int, z: Int) = ((x and 7) shl 3) or (z and 7)
}
