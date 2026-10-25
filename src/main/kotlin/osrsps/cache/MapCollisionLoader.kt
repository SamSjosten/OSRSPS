package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import java.nio.charset.Charset

private val logger = KotlinLogging.logger {}

/** The world as the cache's maps build it: collision, every loc the map files place, and the loc types. */
class WorldMap(val collision: CollisionMap, val locs: StaticLocs, val locTypes: LocTypes)

/**
 * Builds the world's collision from the cache: each map square's terrain and locs in the maps index,
 * and loc types from config archive 6. Neither map file is XTEA-encrypted in revision 241.
 */
object MapCollisionLoader {
    private const val CONFIG_INDEX = 2
    private const val LOC_TYPE_GROUP = 6
    private const val MAPS_INDEX = 5
    private const val GAMEVAL_INDEX = 24
    private const val GAMEVAL_LOCS = 6
    private val CP1252: Charset = Charset.forName("windows-1252")

    /** Every one of the 4 x 64 x 64 tiles needs at least its two-byte end marker. */
    private const val MIN_TERRAIN_SIZE = 4 * 64 * 64 * 2

    /** Bit 1 of a tile's settings: the tile can't be walked on. */
    private const val SETTING_BLOCKED = 0x1

    /** Bit 2 on level 1: a bridge. Everything on this column belongs one level lower. */
    private const val SETTING_BRIDGE = 0x2

    /** Bit 3: the tile is under a roof. Checked against the 241 cache: set over Lumbridge Castle's keep and towers. */
    private const val SETTING_ROOF = 0x4

    fun load(library: CacheLibrary): CollisionMap = loadWorld(library).collision

    fun loadWorld(library: CacheLibrary): WorldMap {
        val map = CollisionMap()
        val locTypes = loadLocTypes(library)
        val staticLocs = StaticLocs()
        val collision = LocCollision(map)
        var squares = 0
        var badLocFiles = 0
        var badTerrainFiles = 0
        val notSquares = ArrayList<String>()
        for ((squareX, squareZ, files) in mapSquares(library)) {
            val terrain = files.terrain() ?: continue
            if (terrain.size < MIN_TERRAIN_SIZE) {
                // Too short to hold every tile, so not terrain. In the 241 cache, group 25287 ("98_199") is 37 files
                // of 3 bytes each, apparently a colour table kept in the maps index; real squares have files 0 to 4.
                notSquares.add("$squareX shl 8 or $squareZ")
                continue
            }
            val settings = try {
                decodeTerrain(terrain)
            } catch (e: Exception) {
                // Left unloaded, so nobody walks there, rather than failing the whole start.
                if (badTerrainFiles++ == 0) logger.warn(e) { "Could not read terrain for map square ${squareX}_$squareZ" }
                continue
            }
            map.markLoaded(squareX, squareZ)
            addTerrain(collision, settings, squareX shl 6, squareZ shl 6)
            try {
                files.locs()?.let { locs ->
                    addLocs(collision, staticLocs, locTypes, settings, locs, squareX shl 6, squareZ shl 6)
                }
            } catch (e: Exception) {
                if (badLocFiles++ == 0) logger.warn(e) { "Could not read locs for map square ${squareX}_$squareZ" }
            }
            squares++
        }
        staticLocs.trim()
        if (squares == 0) logger.warn { "No map squares found in the cache: every tile is unwalkable" }
        if (notSquares.isNotEmpty()) logger.debug { "Skipped maps groups that hold no terrain: ${notSquares.joinToString()}" }
        if (badTerrainFiles > 0) logger.warn { "$badTerrainFiles map squares are unwalkable: their terrain did not decode" }
        if (badLocFiles > 0) logger.warn { "$badLocFiles map squares have no loc collision: their loc files did not decode" }
        logger.info { "Loaded collision for $squares map squares, ${staticLocs.size} locs and ${locTypes.size} loc types" }
        return WorldMap(map, staticLocs, locTypes)
    }

    private class SquareFiles(val terrain: () -> ByteArray?, val locs: () -> ByteArray?)

    private data class MapSquare(val x: Int, val z: Int, val files: SquareFiles)

    /**
     * Older caches name each square's groups `m{x}_{z}` and `l{x}_{z}`. Current ones (revision 241 included) leave
     * the maps index unnamed: group `x shl 8 or z` holds the terrain as file 0 and the locs as file 1, as RuneLite's
     * cache RegionLoader reads them.
     */
    private fun mapSquares(library: CacheLibrary): Sequence<MapSquare> {
        val index = library.index(MAPS_INDEX) ?: return emptySequence()
        if (index.isNamed()) {
            return (0 until 256 * 256).asSequence().map { id ->
                val x = id shr 8
                val z = id and 0xFF
                MapSquare(x, z, SquareFiles({ library.data(MAPS_INDEX, "m${x}_$z") }, { library.data(MAPS_INDEX, "l${x}_$z") }))
            }
        }
        return index.archiveIds().asSequence().map { id ->
            MapSquare(id shr 8, id and 0xFF, SquareFiles({ library.data(MAPS_INDEX, id, 0) }, { library.data(MAPS_INDEX, id, 1) }))
        }
    }

    private fun loadLocTypes(library: CacheLibrary): LocTypes {
        val archive = library.index(CONFIG_INDEX)?.archive(LOC_TYPE_GROUP) ?: return LocTypes.NONE
        val types = HashMap<Int, LocType>()
        // Jagex's own names for locs (poordoor, poordooropen), shipped in the cache from about revision 230. Doors pair
        // up by them.
        val gamevals = library.index(GAMEVAL_INDEX)?.archive(GAMEVAL_LOCS)
        var failed = 0
        for (file in archive.files()) {
            val data = file.data ?: continue
            try {
                val internalName = gamevals?.file(file.id)?.data?.let { String(it, CP1252) }
                types[file.id] = LocTypeDecoder.decode(file.id, data, internalName)
            } catch (e: Exception) {
                if (failed++ == 0) logger.warn(e) { "Could not decode loc type ${file.id}" }
            }
        }
        // A loc type we can't read adds no collision, so players could walk through it.
        if (failed > 0) logger.warn { "$failed loc types did not decode and will not block" }
        if (gamevals == null) logger.warn { "No loc names in the cache's gameval index; doors will not open" }
        return LocTypes(types)
    }

    /** Returns the settings byte of each tile, indexed `[level][x][z]`. */
    fun decodeTerrain(data: ByteArray): Array<Array<ByteArray>> {
        val settings = Array(4) { Array(64) { ByteArray(64) } }
        val buf = Unpooled.wrappedBuffer(data)
        for (level in 0 until 4) {
            for (x in 0 until 64) {
                for (z in 0 until 64) {
                    while (true) {
                        val opcode = buf.readUnsignedShort()
                        when {
                            opcode == 0 -> break
                            opcode == 1 -> { buf.skipBytes(1); break } // height
                            opcode <= 49 -> buf.skipBytes(2) // overlay
                            opcode <= 81 -> settings[level][x][z] = (opcode - 49).toByte()
                            // Anything higher is an underlay, with no extra bytes.
                        }
                    }
                }
            }
        }
        return settings
    }

    private fun addTerrain(collision: LocCollision, settings: Array<Array<ByteArray>>, baseX: Int, baseZ: Int) {
        for (level in 0 until 4) {
            for (x in 0 until 64) {
                for (z in 0 until 64) {
                    val setting = settings[level][x][z].toInt()
                    if (setting and (SETTING_BLOCKED or SETTING_ROOF) == 0) continue
                    val target = adjustedLevel(settings, level, x, z)
                    if (target < 0) continue
                    if (setting and SETTING_BLOCKED != 0) collision.addFloor(baseX + x, baseZ + z, target)
                    if (setting and SETTING_ROOF != 0) collision.addRoof(baseX + x, baseZ + z, target)
                }
            }
        }
    }

    private fun addLocs(
        collision: LocCollision,
        staticLocs: StaticLocs,
        types: LocTypes,
        settings: Array<Array<ByteArray>>,
        data: ByteArray,
        baseX: Int,
        baseZ: Int,
    ) {
        decodeLocs(data) { id, x, z, level, shape, rotation ->
            val target = adjustedLevel(settings, level, x, z)
            val type = types[id]
            if (target >= 0 && type != null) {
                collision.addLoc(type.collision, baseX + x, baseZ + z, target, shape, rotation)
                staticLocs.add(ZoneLoc(id, shape, rotation, baseX + x, baseZ + z, target))
            }
        }
    }

    /** Calls [visit] with each loc in a loc file: its id, local x and z, level, shape and rotation. */
    fun decodeLocs(data: ByteArray, visit: (id: Int, x: Int, z: Int, level: Int, shape: Int, rotation: Int) -> Unit) {
        val buf = Unpooled.wrappedBuffer(data)
        var id = -1
        while (true) {
            val idDelta = buf.readExtendedSmart()
            if (idDelta == 0) break
            id += idDelta
            var packed = 0
            while (true) {
                val posDelta = buf.readUnsignedSmart()
                if (posDelta == 0) break
                packed += posDelta - 1
                val attributes = buf.readUnsignedByte().toInt()
                visit(id, packed shr 6 and 0x3F, packed and 0x3F, packed shr 12 and 0x3, attributes shr 2, attributes and 0x3)
            }
        }
    }

    private fun adjustedLevel(settings: Array<Array<ByteArray>>, level: Int, x: Int, z: Int): Int =
        if (settings[1][x][z].toInt() and SETTING_BRIDGE != 0) level - 1 else level

    private fun ByteBuf.readUnsignedSmart(): Int =
        if (getUnsignedByte(readerIndex()) < 128) readUnsignedByte().toInt() else readUnsignedShort() - 0x8000

    /** A smart that keeps adding while it reads 32767, for loc ids above that. */
    private fun ByteBuf.readExtendedSmart(): Int {
        var total = 0
        var value = readUnsignedSmart()
        while (value == 32767) {
            total += 32767
            value = readUnsignedSmart()
        }
        return total + value
    }
}
