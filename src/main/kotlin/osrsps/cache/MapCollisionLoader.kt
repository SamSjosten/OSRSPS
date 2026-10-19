package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType

private val logger = KotlinLogging.logger {}

/**
 * Builds the world's collision from the cache: terrain (`m{x}_{z}`) and locs (`l{x}_{z}`) in the maps archive,
 * and loc types from config archive 6. Neither map file is XTEA-encrypted in revision 241.
 */
object MapCollisionLoader {
    private const val CONFIG_INDEX = 2
    private const val LOC_TYPE_GROUP = 6
    private const val MAPS_INDEX = 5

    /** Bit 1 of a tile's settings: the tile can't be walked on. */
    private const val SETTING_BLOCKED = 0x1

    /** Bit 2 on level 1: a bridge. Everything on this column belongs one level lower. */
    private const val SETTING_BRIDGE = 0x2

    fun load(library: CacheLibrary): CollisionMap {
        val map = CollisionMap()
        val locTypes = loadLocTypes(library)
        val collision = LocCollision(map)
        var squares = 0
        var badLocFiles = 0
        for (squareX in 0 until 256) {
            for (squareZ in 0 until 256) {
                val terrain = library.data(MAPS_INDEX, "m${squareX}_$squareZ") ?: continue
                val settings = decodeTerrain(terrain)
                map.markLoaded(squareX, squareZ)
                addTerrain(collision, settings, squareX shl 6, squareZ shl 6)
                try {
                    library.data(MAPS_INDEX, "l${squareX}_$squareZ")?.let { locs ->
                        addLocs(collision, locTypes, settings, locs, squareX shl 6, squareZ shl 6)
                    }
                } catch (e: Exception) {
                    if (badLocFiles++ == 0) logger.warn(e) { "Could not read locs for map square ${squareX}_$squareZ" }
                }
                squares++
            }
        }
        if (badLocFiles > 0) logger.warn { "$badLocFiles map squares have no loc collision: their loc files did not decode" }
        logger.info { "Loaded collision for $squares map squares and ${locTypes.size} loc types" }
        return map
    }

    private fun loadLocTypes(library: CacheLibrary): Map<Int, LocCollisionType> {
        val archive = library.index(CONFIG_INDEX)?.archive(LOC_TYPE_GROUP) ?: return emptyMap()
        val types = HashMap<Int, LocCollisionType>()
        var failed = 0
        for (file in archive.files()) {
            val data = file.data ?: continue
            try {
                types[file.id] = LocTypeDecoder.decode(data)
            } catch (e: Exception) {
                if (failed++ == 0) logger.warn(e) { "Could not decode loc type ${file.id}" }
            }
        }
        // A loc type we can't read adds no collision, so players could walk through it.
        if (failed > 0) logger.warn { "$failed loc types did not decode and will not block" }
        return types
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
                    if (settings[level][x][z].toInt() and SETTING_BLOCKED == 0) continue
                    val target = adjustedLevel(settings, level, x, z)
                    if (target >= 0) collision.addFloor(baseX + x, baseZ + z, target)
                }
            }
        }
    }

    private fun addLocs(
        collision: LocCollision,
        types: Map<Int, LocCollisionType>,
        settings: Array<Array<ByteArray>>,
        data: ByteArray,
        baseX: Int,
        baseZ: Int,
    ) {
        decodeLocs(data) { id, x, z, level, shape, rotation ->
            val target = adjustedLevel(settings, level, x, z)
            val type = types[id]
            if (target >= 0 && type != null) collision.addLoc(type, baseX + x, baseZ + z, target, shape, rotation)
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
