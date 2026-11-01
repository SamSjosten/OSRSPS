package osrsps.game.loc

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.Zones
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollision

private val logger = KotlinLogging.logger {}

/**
 * Every loc in the world as it is now: the cache's [StaticLocs] plus what content has added, removed or changed since.
 * Each change updates collision and tells the players watching the zone, through [Zones].
 *
 * Like the client, a tile holds at most one loc per layer (wall, wall decoration, ground loc, ground decoration), so
 * adding a loc replaces whatever was on its layer there. Only touched on the game thread.
 */
class Locs(
    val types: LocTypes,
    private val static: StaticLocs,
    private val map: CollisionMap,
    private val zones: Zones,
    private val currentTick: () -> Long,
) {
    private val collision = LocCollision(map)

    /**
     * The widest footprint, in tiles, of any loc type that blocks: how far away a loc can be and still flag a tile.
     * 33 in the 241 cache.
     */
    private val reach: Int by lazy {
        types.all.filter { it.collision.interactType != 0 }.maxOfOrNull { maxOf(it.collision.width, it.collision.length) } ?: 1
    }

    /** By tile and layer: what is there now where it differs from the cache. A null value is a removed cache loc. */
    private val changes = HashMap<Long, ZoneLoc?>()

    /** By tile and layer: when to put the tile back how it was, and what it was. */
    private val reverts = HashMap<Long, Revert>()

    private class Revert(val dueTick: Long, val previous: ZoneLoc?)

    /** The loc on a tile's [layer] now, if any. */
    operator fun get(x: Int, z: Int, level: Int, layer: Int): ZoneLoc? {
        val slot = slot(x, z, level, layer)
        return if (slot in changes) changes[slot] else static.at(x, z, level, layer)
    }

    /** The loc the cache puts on a tile's [layer], whatever is there now. */
    fun cached(x: Int, z: Int, level: Int, layer: Int): ZoneLoc? = static.at(x, z, level, layer)

    /** Every loc on a tile now, wall first and ground decoration last. */
    fun at(x: Int, z: Int, level: Int): List<ZoneLoc> = (0 until LAYERS).mapNotNull { get(x, z, level, it) }

    /** The loc of type [id] whose south-west tile is [x], [z], or null if there is none now. */
    fun find(id: Int, x: Int, z: Int, level: Int): ZoneLoc? = at(x, z, level).firstOrNull { it.id == id }

    /** Whether any layer of a tile has a timed change still to run, such as a picked crop waiting to grow back. */
    fun reverting(x: Int, z: Int, level: Int): Boolean = (0 until LAYERS).any { slot(x, z, level, it) in reverts }

    /** Whether [loc] is still in the world, exactly as given. */
    fun exists(loc: ZoneLoc): Boolean = get(loc.x, loc.z, loc.level, loc.layer) == loc

    /**
     * Puts [loc] on its tile, replacing whatever was on its layer. With [revertAfter] ticks, the tile goes back to how
     * it was then, unless something changes it again first.
     */
    fun add(loc: ZoneLoc, revertAfter: Int = 0) = change(loc.x, loc.z, loc.level, loc.layer, loc, revertAfter)

    /** Takes [loc] away. Returns false, changing nothing, if it isn't there. */
    fun remove(loc: ZoneLoc, revertAfter: Int = 0): Boolean {
        if (!exists(loc)) return false
        change(loc.x, loc.z, loc.level, loc.layer, null, revertAfter)
        return true
    }

    /**
     * Swaps [loc] for a loc of type [id] on the same tile, shape and angle, the way content turns a tree into a stump.
     * Returns the new loc, or null, changing nothing, if [loc] isn't there.
     */
    fun replace(loc: ZoneLoc, id: Int, revertAfter: Int = 0): ZoneLoc? {
        if (!exists(loc)) return null
        val replacement = loc.copy(id = id)
        change(loc.x, loc.z, loc.level, loc.layer, replacement, revertAfter)
        return replacement
    }

    /** Puts a tile's [layer] back to what the cache has there, dropping any timed revert. */
    fun restore(x: Int, z: Int, level: Int, layer: Int) = change(x, z, level, layer, static.at(x, z, level, layer), 0)

    /** Puts back the tiles whose [add], [remove] or [replace] has run its time. Call once a tick. */
    fun process() {
        if (reverts.isEmpty()) return
        val tick = currentTick()
        val due = reverts.filterValues { it.dueTick <= tick }
        for ((slot, revert) in due) {
            reverts.remove(slot)
            set(slot, revert.previous)
        }
    }

    /**
     * Forgets every change and timed revert on the tiles from [fromX], [fromZ] to [toX], [toZ] on every level, sending
     * nothing and leaving collision alone: for an instance's area at its end, when nobody is left to see it and its
     * collision goes too ([osrsps.game.instance.Instances]).
     */
    fun forgetArea(fromX: Int, fromZ: Int, toX: Int, toZ: Int) {
        val inArea = { slot: Long ->
            val x = (slot shr 16).toInt() and 0x3FFF
            val z = (slot shr 2).toInt() and 0x3FFF
            x in fromX..toX && z in fromZ..toZ
        }
        changes.keys.removeIf(inArea)
        reverts.keys.removeIf(inArea)
    }

    /** Tiles that differ from the cache now, for ::locs and tests. */
    val changed: Int get() = changes.size

    private fun change(x: Int, z: Int, level: Int, layer: Int, loc: ZoneLoc?, revertAfter: Int) {
        require(revertAfter >= 0) { "Can't revert after $revertAfter ticks" }
        val slot = slot(x, z, level, layer)
        val previous = get(x, z, level, layer)
        // A newer change owns the tile: an older timer would undo it.
        reverts.remove(slot)
        if (revertAfter > 0) reverts[slot] = Revert(currentTick() + revertAfter, previous)
        set(slot, loc)
    }

    private fun set(slot: Long, loc: ZoneLoc?) {
        val x = (slot shr 16).toInt() and 0x3FFF
        val z = (slot shr 2).toInt() and 0x3FFF
        val level = (slot shr 30).toInt() and 3
        val layer = slot.toInt() and 3
        val old = get(x, z, level, layer)
        if (old == loc) return
        if (loc != null && types[loc.id] == null) logger.warn { "Adding loc ${loc.id}, which the cache doesn't have: it won't block" }
        val touched = HashSet<Long>()
        for (changed in listOfNotNull(old, loc)) {
            val type = types[changed.id] ?: continue
            collision.cells(type.collision, changed.x, changed.z, level, changed.shape, changed.angle) { tx, tz, _ -> touched.add(tileKey(tx, tz)) }
        }
        val cache = static.at(x, z, level, layer)
        if (loc == cache) changes.remove(slot) else changes[slot] = loc
        recompute(touched, level)
        // Removing a loc where the cache has none, or putting the cache's back, leaves nothing for later viewers.
        if (loc != null) zones.addLoc(loc) else zones.removeLoc(x, z, level, old!!.shape, old.angle)
        if (loc == cache) zones.forgetLoc(x, z, level, layer)
    }

    /**
     * Works out the flags of each [touched] tile again from the terrain and every loc there now. Clearing only the
     * changed loc's flags, as the client does, would also clear a wall flag a neighbouring loc shares: an open door
     * lying along the same tile edge as a wall leaves a hole in the wall once it closes (Varrock Palace's first floor,
     * 3217-3218, 3490). The client rebuilds its map whenever the player loads the area, so its holes don't last; ours
     * would until a restart.
     */
    private fun recompute(touched: Set<Long>, level: Int) {
        if (touched.isEmpty()) return
        val flags = HashMap<Long, Int>(touched.size * 2)
        var minX = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        for (key in touched) {
            val tx = (key shr 32).toInt()
            val tz = key.toInt()
            flags[key] = map.stored(tx, tz, level) and TERRAIN
            minX = minOf(minX, tx); maxX = maxOf(maxX, tx)
            minZ = minOf(minZ, tz); maxZ = maxOf(maxZ, tz)
        }
        // A wall flags the tile beyond its edge too, and a loc as wide as [reach] flags tiles that far east and north.
        forEachLoc(minX - reach, minZ - reach, maxX + 1, maxZ + 1, level) { loc ->
            val type = types[loc.id] ?: return@forEachLoc
            collision.cells(type.collision, loc.x, loc.z, level, loc.shape, loc.angle) { tx, tz, mask ->
                val key = tileKey(tx, tz)
                flags[key]?.let { flags[key] = it or mask }
            }
        }
        for ((key, value) in flags) map.set((key shr 32).toInt(), key.toInt(), level, value)
    }

    /** Every loc there is now with its south-west tile in the area, on [level]. */
    private fun forEachLoc(fromX: Int, fromZ: Int, toX: Int, toZ: Int, level: Int, visit: (ZoneLoc) -> Unit) {
        val x0 = fromX.coerceAtLeast(0)
        val z0 = fromZ.coerceAtLeast(0)
        val x1 = toX.coerceAtMost(MAX_COORD - 1)
        val z1 = toZ.coerceAtMost(MAX_COORD - 1)
        if (x0 > x1 || z0 > z1) return
        for (zoneX in (x0 shr 3)..(x1 shr 3)) {
            for (zoneZ in (z0 shr 3)..(z1 shr 3)) {
                static.forEachInZone(zoneX shl 3, zoneZ shl 3, level) { loc ->
                    if (loc.x !in x0..x1 || loc.z !in z0..z1) return@forEachInZone
                    val slot = slot(loc.x, loc.z, level, loc.layer)
                    // A changed layer keeps the cache's other locs there; only the one the change replaced is gone.
                    if (slot in changes && loc == static.at(loc.x, loc.z, level, loc.layer)) return@forEachInZone
                    visit(loc)
                }
            }
        }
        for (loc in changes.values) {
            if (loc != null && loc.level == level && loc.x in x0..x1 && loc.z in z0..z1) visit(loc)
        }
    }

    private fun tileKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    // A slot is level (2 bits), x (14), z (14) and layer (2), so it unpacks without a lookup.
    private fun slot(x: Int, z: Int, level: Int, layer: Int): Long {
        require(x in 0 until MAX_COORD && z in 0 until MAX_COORD && level in 0..3) { "No tile $x, $z, $level" }
        return (level.toLong() shl 30) or (x.toLong() shl 16) or (z.toLong() shl 2) or layer.toLong()
    }

    companion object {
        const val LAYERS = 4

        /** Flags only the terrain sets, which no loc change touches. */
        const val TERRAIN = CollisionFlag.FLOOR or CollisionFlag.ROOF
        private const val MAX_COORD = 1 shl 14
    }
}
