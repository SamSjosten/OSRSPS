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
 * One part of an instance's layout: the [widthZones] by [heightZones] zones of the map from tile [fromX], [fromZ]
 * (rounded down to its zone) on [fromLevel], put [toDx], [toDz] zones in from the block's south-west corner, on
 * [toLevel]. A part can take a zone from one level to another: the client draws it on the level it is put on.
 */
data class InstancePart(
    val fromX: Int,
    val fromZ: Int,
    val fromLevel: Int,
    val widthZones: Int,
    val heightZones: Int,
    val toDx: Int,
    val toDz: Int,
    val toLevel: Int,
)

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
 * An instance is laid out from [InstancePart]s, each copying zones of one level to a place and level in its block, and
 * [replace] lays a part over zones it has already, for a room that changes as a raid goes on. Copies are made
 * unrotated; no room built so far needs rotating. Locs and objs changed in the
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
    fun create(fromX: Int, fromZ: Int, widthZones: Int, heightZones: Int, exitX: Int, exitZ: Int, exitLevel: Int): Instance? =
        create(widthZones, heightZones, (0 until LEVELS).map { InstancePart(fromX, fromZ, it, widthZones, heightZones, 0, 0, it) }, exitX, exitZ, exitLevel)

    /**
     * Makes a [widthZones] by [heightZones] zone instance laid out from [parts]; zones no part fills are left out of the
     * world. Returns null when the dynamic area has no room for it.
     */
    fun create(widthZones: Int, heightZones: Int, parts: List<InstancePart>, exitX: Int, exitZ: Int, exitLevel: Int): Instance? {
        require(widthZones in 1..MAX_ZONES && heightZones in 1..MAX_ZONES) { "Bad instance size $widthZones x $heightZones zones" }
        for (part in parts) {
            require(part.toDx >= 0 && part.toDz >= 0 && part.toDx + part.widthZones <= widthZones && part.toDz + part.heightZones <= heightZones) { "$part is outside $widthZones x $heightZones zones" }
            require(part.fromLevel in 0 until LEVELS && part.toLevel in 0 until LEVELS) { "$part has no such level" }
        }
        val widthSquares = (widthZones + 7) / 8
        val heightSquares = (heightZones + 7) / 8
        val (squareX, squareZ) = place(widthSquares, heightSquares) ?: return null
        val instance = Instance(nextId++, squareX, squareZ, widthSquares, heightSquares, exitX, exitZ, exitLevel)
        mark(instance, true)
        byId[instance.id] = instance
        val copied = parts.flatMap { copyTerrain(instance, it) }
        // The locs once every zone's terrain is in, so a loc whose flags reach into the next zone (a wall's other side,
        // a big object) keeps them there.
        for ((from, to) in copied) copyLocs(instance, from, to)
        return instance
    }

    /**
     * Lays [part] over what [instance] shows in its zones now: their collision and locs go, and the part's come in, as
     * for a room that changes as a raid goes on. The caller sends the players in it a new REBUILD_REGION, and clears
     * the loc changes and objs in those zones first if they mustn't stay. Guessed safe: a loc of a neighbouring zone
     * whose flags reached into these zones loses them there.
     */
    fun replace(instance: Instance, part: InstancePart) {
        require(byId[instance.id] === instance) { "$instance has ended" }
        require(part.toDx >= 0 && part.toDz >= 0 && part.toDx + part.widthZones <= instance.widthSquares * 8 && part.toDz + part.heightZones <= instance.heightSquares * 8) { "$part is outside $instance" }
        for (dx in 0 until part.widthZones) {
            for (dz in 0 until part.heightZones) {
                val zone = ZoneKey((instance.baseX shr 3) + part.toDx + dx, (instance.baseZ shr 3) + part.toDz + dz, part.toLevel)
                if (instance.sources.remove(zone.packed) == null) continue
                staticLocs.removeZone(zone.baseX, zone.baseZ, zone.level)
                for (x in 0 until ZoneKey.SIZE) for (z in 0 until ZoneKey.SIZE) collision.set(zone.baseX + x, zone.baseZ + z, zone.level, 0)
            }
        }
        for ((from, to) in copyTerrain(instance, part)) copyLocs(instance, from, to)
    }

    /**
     * Ends [instance]: its zones leave the world, with their collision and copied locs. The caller clears what lives in
     * the area first (NPCs, objs, loc changes) and makes sure nobody is in it.
     */
    fun destroy(instance: Instance) {
        if (byId.remove(instance.id) == null) return
        val zones = instance.sources.keys.map(::ZoneKey)
        for (zone in zones) staticLocs.removeZone(zone.baseX, zone.baseZ, zone.level)
        for ((x, z) in zones.map { it.x to it.z }.distinct()) collision.unloadZone(x, z)
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

    /**
     * The terrain of each zone of [part] that is on the cache's maps, into [instance]; returns the zones copied, from and
     * to, for [copyLocs]. Off the cache's maps there is nothing to copy, and the zone stays out of the world, as its
     * original is.
     */
    private fun copyTerrain(instance: Instance, part: InstancePart): List<Pair<ZoneKey, ZoneKey>> {
        val copied = ArrayList<Pair<ZoneKey, ZoneKey>>()
        for (dx in 0 until part.widthZones) {
            for (dz in 0 until part.heightZones) {
                val fromZoneX = (part.fromX shr 3) + dx
                val fromZoneZ = (part.fromZ shr 3) + dz
                if (collision[fromZoneX shl 3, fromZoneZ shl 3, 0] == CollisionFlag.UNLOADED) continue
                val from = ZoneKey(fromZoneX, fromZoneZ, part.fromLevel)
                val to = ZoneKey((instance.baseX shr 3) + part.toDx + dx, (instance.baseZ shr 3) + part.toDz + dz, part.toLevel)
                copyTerrain(instance, from, to)
                copied += from to to
            }
        }
        return copied
    }

    /** The terrain's own flags of zone [from], into [to]; [copyLocs] adds the locs' after. */
    private fun copyTerrain(instance: Instance, from: ZoneKey, to: ZoneKey) {
        instance.sources[to.packed] = from
        collision.markZoneLoaded(to.x, to.z)
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
        staticLocs.forEachInZone(from.baseX, from.baseZ, from.level) { loc ->
            val copied = loc.copy(x = loc.x + dx, z = loc.z + dz, level = level)
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
