package osrsps.game.instance

import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone
import osrsps.game.loc.LocTypes
import osrsps.game.loc.Locs
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneKey
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollision

/**
 * One instance: a block of map squares in the dynamic area whose zones are copies of zones elsewhere on the map. The
 * client draws each copied zone from the cache's own map ([referenceZone]); the server copies its collision and locs.
 * [exitX], [exitZ] and [exitLevel] are where a player inside goes when they log out, and where `::instance leave`
 * takes them.
 */
class Instance internal constructor(
    val id: Int,
    /** The south-west map square of the block. */
    val squareX: Int,
    val squareZ: Int,
    /** The block's size in map squares. */
    val widthSquares: Int,
    val heightSquares: Int,
    val exitX: Int,
    val exitZ: Int,
    val exitLevel: Int,
) {
    /** The south-west tile of the block. */
    val baseX: Int get() = squareX shl 6
    val baseZ: Int get() = squareZ shl 6

    /** The north-east tile of the block. */
    val maxX: Int get() = baseX + (widthSquares shl 6) - 1
    val maxZ: Int get() = baseZ + (heightSquares shl 6) - 1

    /** By instance zone ([ZoneKey.packed]): the zone of the map it copies. Rotation is always 0 for now. */
    internal val sources = HashMap<Int, ZoneKey>()

    /** How many zones it copies, on all levels together. */
    val copiedZones: Int get() = sources.size

    fun contains(x: Int, z: Int): Boolean = x in baseX..maxX && z in baseZ..maxZ

    override fun toString() = "Instance #$id at $baseX, $baseZ"
}

/**
 * Private copies of parts of the map (instances, the client's dynamic regions). Each [Instance] is a block of map
 * squares in the dynamic area, from map square [FIRST_SQUARE_X] east, where the cache has no maps; each of its zones
 * shows and plays as a zone of the real map. A REBUILD_REGION tells the client which zone to draw where
 * ([referenceZone]); the server copies the zone's collision and its cache locs, so walking, doors and line of sight
 * work as at the original. A block keeps a free map square around it, so nobody in one instance is ever within view
 * of anybody in another.
 *
 * Copies are made unrotated. Rotation, which the raid's rooms may need, waits for them. Locs and objs changed in the
 * original (an open door) aren't copied: an instance starts as the cache has it. Inferred from the client's
 * dynamic-region protocol (RSProt's RebuildRegionV2), not compared with live's instances.
 */
class Instances(private val collision: CollisionMap, private val staticLocs: StaticLocs, private val locTypes: LocTypes) {
    private val byId = LinkedHashMap<Int, Instance>()

    /** Which grid cells (map squares from [FIRST_SQUARE_X], 0) a block or its margin takes. */
    private val taken = BooleanArray(GRID_WIDTH * GRID_HEIGHT)
    private var nextId = 1

    /** Every instance there is now, oldest first. */
    val all: Collection<Instance> get() = byId.values

    /** The instance holding tile [x], [z], if any. */
    fun at(x: Int, z: Int): Instance? = if (!inDynamicArea(x, z)) null else byId.values.firstOrNull { it.contains(x, z) }

    /** Whether tile [x], [z] lies in the dynamic area, where only instances are. */
    fun inDynamicArea(x: Int, z: Int): Boolean = x shr 6 in FIRST_SQUARE_X until FIRST_SQUARE_X + GRID_WIDTH && z shr 6 in 0 until GRID_HEIGHT

    /**
     * Makes an instance copying the [widthZones] by [heightZones] zones from tile [fromX], [fromZ] (rounded down to its
     * zone) on every level, laid out as they are. [exitX], [exitZ], [exitLevel] is where its players leave to. Returns
     * null when the dynamic area has no room for it.
     */
    fun create(fromX: Int, fromZ: Int, widthZones: Int, heightZones: Int, exitX: Int, exitZ: Int, exitLevel: Int): Instance? {
        require(widthZones in 1..MAX_ZONES && heightZones in 1..MAX_ZONES) { "Bad instance size $widthZones x $heightZones zones" }
        val widthSquares = (widthZones + 7) / 8
        val heightSquares = (heightZones + 7) / 8
        val (squareX, squareZ) = place(widthSquares, heightSquares) ?: return null
        val instance = Instance(nextId++, squareX, squareZ, widthSquares, heightSquares, exitX, exitZ, exitLevel)
        mark(instance, true)
        byId[instance.id] = instance
        val fromZoneX = fromX shr 3
        val fromZoneZ = fromZ shr 3
        for (dx in 0 until widthZones) {
            for (dz in 0 until heightZones) {
                // Off the cache's maps there is nothing to copy; the zone stays out of the world, as its original is.
                if (collision[(fromZoneX + dx) shl 3, (fromZoneZ + dz) shl 3, 0] == CollisionFlag.UNLOADED) continue
                for (level in 0 until LEVELS) {
                    copyTerrain(instance, ZoneKey(fromZoneX + dx, fromZoneZ + dz, level), ZoneKey((instance.baseX shr 3) + dx, (instance.baseZ shr 3) + dz, level))
                }
            }
        }
        // The locs once every zone's terrain is in, so a loc whose flags reach into the next zone (a wall's other side,
        // a big object) keeps them there.
        for ((to, from) in instance.sources.entries.toList()) copyLocs(instance, from, ZoneKey(to))
        return instance
    }

    /**
     * Ends [instance]: its zones leave the world, with their collision and copied locs. The caller clears what lives in
     * the area first (NPCs, objs, loc changes) and makes sure nobody is in it.
     */
    fun destroy(instance: Instance) {
        if (byId.remove(instance.id) == null) return
        for (zone in instance.sources.keys.map(::ZoneKey)) {
            staticLocs.removeZone(zone.baseX, zone.baseZ, zone.level)
            if (zone.level == 0) collision.unloadZone(zone.x, zone.z)
        }
        mark(instance, false)
    }

    /**
     * What the client draws in the zone [zoneX], [zoneZ] (in zones) on [level] for REBUILD_REGION: the map zone it
     * copies, or null for none (left empty).
     */
    fun referenceZone(zoneX: Int, zoneZ: Int, level: Int): RebuildRegionZone? {
        if (zoneX < 0 || zoneZ < 0 || level !in 0 until LEVELS) return null
        val zone = ZoneKey(zoneX, zoneZ, level)
        val instance = at(zone.baseX, zone.baseZ) ?: return null
        val source = instance.sources[zone.packed] ?: return null
        return RebuildRegionZone(source.x, source.z, source.level, 0)
    }

    /** The terrain's own flags of zone [from], into [to]; [copyLocs] adds the locs' after. */
    private fun copyTerrain(instance: Instance, from: ZoneKey, to: ZoneKey) {
        instance.sources[to.packed] = from
        if (to.level == 0) collision.markZoneLoaded(to.x, to.z)
        for (x in 0 until ZoneKey.SIZE) {
            for (z in 0 until ZoneKey.SIZE) {
                collision.set(to.baseX + x, to.baseZ + z, to.level, collision.stored(from.baseX + x, from.baseZ + z, from.level) and Locs.TERRAIN)
            }
        }
    }

    /**
     * Zone [from]'s cache locs, into [to], with their collision as the map loader builds it; a wall on the copy's edge
     * flags the tile beyond only when that tile is the instance's too.
     */
    private fun copyLocs(instance: Instance, from: ZoneKey, to: ZoneKey) {
        val dx = to.baseX - from.baseX
        val dz = to.baseZ - from.baseZ
        val level = to.level
        val cells = LocCollision(collision)
        staticLocs.forEachInZone(from.baseX, from.baseZ, level) { loc ->
            val copied = loc.copy(x = loc.x + dx, z = loc.z + dz)
            staticLocs.add(copied)
            val type = locTypes[loc.id] ?: return@forEachInZone
            cells.cells(type.collision, copied.x, copied.z, level, copied.shape, copied.angle) { tx, tz, mask ->
                if (instance.contains(tx, tz)) collision.add(tx, tz, level, mask)
            }
        }
    }

    /** First fit, row by row, of a block and the free square round it. */
    private fun place(widthSquares: Int, heightSquares: Int): Pair<Int, Int>? {
        for (gz in MARGIN..GRID_HEIGHT - heightSquares - MARGIN) {
            for (gx in MARGIN..GRID_WIDTH - widthSquares - MARGIN) {
                if (free(gx - MARGIN, gz - MARGIN, widthSquares + 2 * MARGIN, heightSquares + 2 * MARGIN)) return FIRST_SQUARE_X + gx to gz
            }
        }
        return null
    }

    private fun free(gx: Int, gz: Int, width: Int, height: Int): Boolean {
        for (x in gx until gx + width) for (z in gz until gz + height) if (taken[x * GRID_HEIGHT + z]) return false
        return true
    }

    /** Marks the block's own squares; its margin stays free to share with a neighbour's, which keeps them a square apart. */
    private fun mark(instance: Instance, value: Boolean) {
        val gx = instance.squareX - FIRST_SQUARE_X
        for (x in gx until gx + instance.widthSquares) {
            for (z in instance.squareZ until instance.squareZ + instance.heightSquares) taken[x * GRID_HEIGHT + z] = value
        }
    }

    companion object {
        /** The first map square east of the dynamic area's west edge: x 6400, past every map in the cache. Guessed. */
        const val FIRST_SQUARE_X = 100
        private const val GRID_WIDTH = 100
        private const val GRID_HEIGHT = 200

        /** Free squares kept between blocks. */
        private const val MARGIN = 1
        private const val LEVELS = 4

        /** The most zones an instance copies across or up: 8 map squares. */
        const val MAX_ZONES = 64
    }
}
