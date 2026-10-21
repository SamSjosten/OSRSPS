package osrsps.game.loc

import osrsps.game.zone.ZoneKey
import osrsps.game.zone.ZoneLoc

/**
 * The locs the cache's map files place, by zone, as the server first loads them. [Locs] keeps what has changed since.
 * The 241 maps place about 4.9 million locs, so each is packed in an int: id (up to 18 bits), tile in zone, shape and
 * angle.
 *
 * Levels are the ones players stand on: a loc on a bridge column is kept one level down, where its collision goes.
 */
class StaticLocs {
    private class ZoneLocs {
        var packed = IntArray(4)
        var count = 0
    }

    private val zones = HashMap<Int, ZoneLocs>()

    var size: Int = 0
        private set

    fun add(loc: ZoneLoc) {
        require(loc.id in 0..MAX_ID) { "Loc id ${loc.id} doesn't fit" }
        val zone = zones.getOrPut(ZoneKey.of(loc.x, loc.z, loc.level).packed, ::ZoneLocs)
        if (zone.count == zone.packed.size) zone.packed = zone.packed.copyOf(zone.count * 2)
        zone.packed[zone.count++] = (loc.id shl 13) or (((loc.x and 7) shl 3 or (loc.z and 7)) shl 7) or (loc.shape shl 2) or loc.angle
        size++
    }

    /** Frees the room left for more locs. Call once loading is done. */
    fun trim() {
        for (zone in zones.values) zone.packed = zone.packed.copyOf(zone.count)
    }

    /** Every loc on a tile, in the order the map file lists them. */
    fun at(x: Int, z: Int, level: Int): List<ZoneLoc> {
        val key = ZoneKey.of(x, z, level)
        val zone = zones[key.packed] ?: return emptyList()
        val tile = ((x and 7) shl 3) or (z and 7)
        var found: MutableList<ZoneLoc>? = null
        for (i in 0 until zone.count) {
            val packed = zone.packed[i]
            if ((packed ushr 7) and 0x3F != tile) continue
            val loc = ZoneLoc(packed ushr 13, (packed ushr 2) and 0x1F, packed and 3, x, z, level)
            (found ?: ArrayList<ZoneLoc>(2).also { found = it }).add(loc)
        }
        return found ?: emptyList()
    }

    /** The loc on a tile's [layer], if any. The client keeps only one, so a later one in the file wins. */
    fun at(x: Int, z: Int, level: Int, layer: Int): ZoneLoc? = at(x, z, level).lastOrNull { it.layer == layer }

    companion object {
        const val MAX_ID = (1 shl 18) - 1
    }
}
