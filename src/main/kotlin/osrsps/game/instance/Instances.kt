package osrsps.game.instance

import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone
import osrsps.game.loc.LocTypes
import osrsps.game.loc.Locs
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneKey
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollision

/**
 * One part of an instance's layout: the [widthZones] by [heightZones] zones of the map from tile [fromX], [fromZ]
 * (rounded down to its zone) on [fromLevel], put [toDx], [toDz] zones in from the block's south-west corner, on
 * [toLevel]. A part can take a zone from one level to another: the client draws it on the level it is put on.
 *
 * A part of one zone can be turned [rotation] quarters clockwise, as a player-owned house turns its rooms: the client
 * draws the zone turned (REBUILD_REGION's rotation), and the server turns its terrain and locs to match. Bigger parts are
 * only copied as they are.
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
    val rotation: Int = 0,
) {
    init {
        require(rotation in 0..3) { "Rotation $rotation is out of range" }
        require(rotation == 0 || (widthZones == 1 && heightZones == 1)) { "Only a part of one zone can be turned" }
    }
}

/** The zone of the map an instance zone copies, and how many quarters clockwise it is turned. */
data class ZoneSource(val zone: ZoneKey, val rotation: Int)

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

    /** By instance zone ([ZoneKey.packed]): the zone of the map it copies, and its turn. */
    internal val sources = HashMap<Int, ZoneSource>()

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
 * [replace] lays a part over zones it has already, for a room that changes as a raid goes on. A one-zone part can be
 * turned ([InstancePart.rotation]); bigger ones are copied as they are. Locs and objs changed in the
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
        for ((from, to, rotation) in copied) copyLocs(instance, from, to, rotation)
        return instance
    }

    /**
     * Lays [part] over what [instance] shows in its zones now: their collision and locs go, and the part's come in, as
     * for a room that changes as a raid goes on. The caller sends the players in it a new REBUILD_REGION, and clears
     * the loc changes and objs in those zones first if they mustn't stay. Collision across the zones' edges is kept
     * right both ways: the outgoing locs' flags on the tiles beyond go (a wall on the edge flags both sides), and the
     * neighbouring zones' cache locs whose flags reach into these zones, or onto those tiles, are added back (Codex
     * review on #798). A loc change (not the cache's) next door that reaches in isn't put back.
     */
    fun replace(instance: Instance, part: InstancePart) =
        lay(instance, part.toDx, part.toDz, part.widthZones, part.heightZones, part.toLevel, part)

    /**
     * Empties the zone [toDx], [toDz] zones in from [instance]'s south-west corner on [level], as [replace] lays a part
     * there: what it showed goes, and the client is left nothing to draw there (an empty cell upstairs in a house).
     */
    fun clear(instance: Instance, toDx: Int, toDz: Int, level: Int) = lay(instance, toDx, toDz, 1, 1, level, null)

    private fun lay(instance: Instance, toDx: Int, toDz: Int, widthZones: Int, heightZones: Int, level: Int, part: InstancePart?) {
        require(byId[instance.id] === instance) { "$instance has ended" }
        require(toDx >= 0 && toDz >= 0 && toDx + widthZones <= instance.widthSquares * 8 && toDz + heightZones <= instance.heightSquares * 8) { "Zones at $toDx, $toDz are outside $instance" }
        val replaced = ArrayList<ZoneKey>()
        for (dx in 0 until widthZones) {
            for (dz in 0 until heightZones) {
                replaced += ZoneKey((instance.baseX shr 3) + toDx + dx, (instance.baseZ shr 3) + toDz + dz, level)
            }
        }
        val inReplaced = { x: Int, z: Int -> replaced.any { x shr 3 == it.x && z shr 3 == it.z } }
        // Tiles beyond the zones that lose an outgoing loc's flags, so the neighbours' own can be put back on them.
        val touched = HashSet<Long>()
        for (zone in replaced) {
            if (zone.packed !in instance.sources) continue
            staticLocs.forEachInZone(zone.baseX, zone.baseZ, zone.level) { loc ->
                locCells(loc) { tx, tz, mask ->
                    if (!inReplaced(tx, tz) && instance.contains(tx, tz)) {
                        collision.remove(tx, tz, zone.level, mask)
                        touched += tileKey(tx, tz)
                    }
                }
            }
        }
        for (zone in replaced) {
            if (instance.sources.remove(zone.packed) == null) continue
            staticLocs.removeZone(zone.baseX, zone.baseZ, zone.level)
            for (x in 0 until ZoneKey.SIZE) for (z in 0 until ZoneKey.SIZE) collision.set(zone.baseX + x, zone.baseZ + z, zone.level, 0)
        }
        if (part != null) for ((from, to, rotation) in copyTerrain(instance, part)) copyLocs(instance, from, to, rotation)
        val neighbours = HashSet<ZoneKey>()
        for (zone in replaced) {
            for (nx in -1..1) for (nz in -1..1) {
                val next = ZoneKey(zone.x + nx, zone.z + nz, zone.level)
                if (next !in replaced && next.packed in instance.sources) neighbours += next
            }
        }
        for (zone in neighbours) {
            staticLocs.forEachInZone(zone.baseX, zone.baseZ, zone.level) { loc ->
                locCells(loc) { tx, tz, mask ->
                    if ((inReplaced(tx, tz) || tileKey(tx, tz) in touched) && instance.contains(tx, tz)) collision.add(tx, tz, zone.level, mask)
                }
            }
        }
    }

    /** The collision cells of [loc], placed as it is, as the map loader flags them; nothing for an unknown type. */
    private fun locCells(loc: ZoneLoc, visit: (Int, Int, Int) -> Unit) {
        val type = locTypes[loc.id] ?: return
        LocCollision(collision).cells(type.collision, loc.x, loc.z, loc.level, loc.shape, loc.angle, visit)
    }

    private fun tileKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

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
        return RebuildRegionZone(source.zone.x, source.zone.z, source.zone.level, source.rotation)
    }

    /**
     * The terrain of each zone of [part] that is on the cache's maps, into [instance]; returns the zones copied, from and
     * to, for [copyLocs]. Off the cache's maps there is nothing to copy, and the zone stays out of the world, as its
     * original is.
     */
    private fun copyTerrain(instance: Instance, part: InstancePart): List<Triple<ZoneKey, ZoneKey, Int>> {
        val copied = ArrayList<Triple<ZoneKey, ZoneKey, Int>>()
        for (dx in 0 until part.widthZones) {
            for (dz in 0 until part.heightZones) {
                val fromZoneX = (part.fromX shr 3) + dx
                val fromZoneZ = (part.fromZ shr 3) + dz
                if (collision[fromZoneX shl 3, fromZoneZ shl 3, 0] == CollisionFlag.UNLOADED) continue
                val from = ZoneKey(fromZoneX, fromZoneZ, part.fromLevel)
                val to = ZoneKey((instance.baseX shr 3) + part.toDx + dx, (instance.baseZ shr 3) + part.toDz + dz, part.toLevel)
                copyTerrain(instance, from, to, part.rotation)
                copied += Triple(from, to, part.rotation)
            }
        }
        return copied
    }

    /** The terrain's own flags of zone [from], into [to] turned [rotation] quarters; [copyLocs] adds the locs' after. */
    private fun copyTerrain(instance: Instance, from: ZoneKey, to: ZoneKey, rotation: Int) {
        instance.sources[to.packed] = ZoneSource(from, rotation)
        collision.markZoneLoaded(to.x, to.z)
        for (x in 0 until ZoneKey.SIZE) {
            for (z in 0 until ZoneKey.SIZE) {
                val flags = collision.stored(from.baseX + x, from.baseZ + z, from.level) and Locs.TERRAIN
                collision.set(to.baseX + rotateX(x, z, rotation, 1, 1), to.baseZ + rotateZ(x, z, rotation, 1, 1), to.level, flags)
            }
        }
    }

    /**
     * Zone [from]'s cache locs, into [to] turned [rotation] quarters, with their collision as the map loader builds it;
     * a wall on the copy's edge flags the tile beyond only when that tile is the instance's too. A turned loc keeps its
     * whole footprint in the zone, moved by its size as the client moves it, and turns with the zone.
     */
    private fun copyLocs(instance: Instance, from: ZoneKey, to: ZoneKey, rotation: Int) {
        val level = to.level
        val cells = LocCollision(collision)
        staticLocs.forEachInZone(from.baseX, from.baseZ, from.level) { loc ->
            val type = locTypes[loc.id]
            // A loc turned a quarter on its own swaps its footprint, before the zone's turn moves it.
            val (width, length) = when {
                type == null -> 1 to 1
                loc.angle and 1 == 1 -> type.collision.length to type.collision.width
                else -> type.collision.width to type.collision.length
            }
            val x = loc.x - from.baseX
            val z = loc.z - from.baseZ
            val copied = loc.copy(
                x = to.baseX + rotateX(x, z, rotation, width, length),
                z = to.baseZ + rotateZ(x, z, rotation, width, length),
                level = level,
                angle = (loc.angle + rotation) and 3,
            )
            staticLocs.add(copied)
            if (type == null) return@forEachInZone
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
        /**
         * Where a [width] by [length] footprint whose south-west tile is [x], [z] in a zone lands, as the zone turns
         * [rotation] quarters clockwise: its new south-west tile's x. As the client places a turned zone's locs (the
         * dynamic map loader's own sum), so the server's copy lines up with what is drawn.
         */
        fun rotateX(x: Int, z: Int, rotation: Int, width: Int, length: Int): Int = when (rotation and 3) {
            0 -> x
            1 -> z
            2 -> ZoneKey.SIZE - 1 - x - (width - 1)
            else -> ZoneKey.SIZE - 1 - z - (length - 1)
        }

        /** As [rotateX], the new south-west tile's z. */
        fun rotateZ(x: Int, z: Int, rotation: Int, width: Int, length: Int): Int = when (rotation and 3) {
            0 -> z
            1 -> ZoneKey.SIZE - 1 - x - (width - 1)
            2 -> ZoneKey.SIZE - 1 - z - (length - 1)
            else -> x
        }

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
