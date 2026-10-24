package osrsps.testclient

import io.netty.buffer.Unpooled
import net.rsprot.buffer.JagByteBuf
import net.rsprot.buffer.extensions.toJagByteBuf
import net.rsprot.protocol.game.outgoing.prot.GameServerProt

/** An obj on the ground the client can see. */
data class GroundObjView(val id: Int, val count: Int, val coord: Coord)

/**
 * A loc the server changed on a tile, on top of what the client's cache map has: [id] -1 is a cache loc removed, as
 * after LOC_DEL. [layer] is the client's: 0 walls, 1 wall decorations, 2 ground locs, 3 ground decorations.
 */
data class LocChange(val id: Int, val shape: Int, val angle: Int, val coord: Coord) {
    val layer: Int get() = layerOf(shape)

    companion object {
        fun layerOf(shape: Int): Int = when (shape) {
            in 0..3 -> 0
            in 4..8 -> 1
            in 9..21 -> 2
            else -> 3
        }
    }
}

/**
 * The client's side of zone updates: ground objs and loc changes in the build area. Built against RSProt 241's zone
 * encoders: UPDATE_ZONE_FULL_FOLLOWS wipes a zone, UPDATE_ZONE_PARTIAL_ENCLOSED carries a batch of zone prots (each
 * led by its index in the client's own order, see [ENCLOSED]), and UPDATE_ZONE_PARTIAL_FOLLOWS names the zone for the
 * single zone prots after it. Zone headers are tiles from the build area's south-west corner, which REBUILD gives.
 * Animations, projectiles, sounds and obj customisations are read past but not kept.
 */
class ZoneReader {
    private var originX = 0
    private var originZ = 0
    private var zoneX = 0
    private var zoneZ = 0
    private var level = 0
    private val objs = LinkedHashMap<Coord, MutableList<GroundObjView>>()
    private val locs = LinkedHashMap<Pair<Coord, Int>, LocChange>()

    fun groundObjs(): List<GroundObjView> = objs.values.flatten()

    fun locChanges(): List<LocChange> = locs.values.toList()

    /**
     * A new build area centred on zone [centreX], [centreZ]. The real client reloads the scene, which drops its ground
     * objs and loc changes (as in the deobfuscated 317 client; not checked on a 241 client), so the server has to
     * send every zone in view again.
     */
    fun rebuild(centreX: Int, centreZ: Int) {
        originX = (centreX - BUILD_AREA_ZONE_RADIUS) shl 3
        originZ = (centreZ - BUILD_AREA_ZONE_RADIUS) shl 3
        objs.clear()
        locs.clear()
    }

    /** Applies [prot] if it is a zone packet; returns false for anything else. */
    fun read(prot: GameServerProt, payload: ByteArray): Boolean {
        val buf = Unpooled.wrappedBuffer(payload).toJagByteBuf()
        when (prot) {
            GameServerProt.UPDATE_ZONE_FULL_FOLLOWS -> {
                zoneZ = buf.g1()
                level = buf.g1()
                zoneX = buf.g1()
                wipeZone()
            }
            GameServerProt.UPDATE_ZONE_PARTIAL_FOLLOWS -> {
                level = buf.g1Alt1()
                zoneZ = buf.g1Alt1()
                zoneX = buf.g1()
            }
            GameServerProt.UPDATE_ZONE_PARTIAL_ENCLOSED -> {
                zoneZ = buf.g1()
                zoneX = buf.g1()
                level = buf.g1Alt3()
                while (buf.isReadable) readZoneProt(ENCLOSED[buf.g1()], buf)
            }
            in ENCLOSED -> readZoneProt(prot, buf)
            else -> return false
        }
        return true
    }

    private fun wipeZone() {
        val inZone = { coord: Coord -> coord.level == level && (coord.x - originX) and 7.inv() == zoneX && (coord.z - originZ) and 7.inv() == zoneZ }
        objs.keys.removeIf(inZone)
        locs.keys.removeIf { inZone(it.first) }
    }

    /** The tile a packed coord-in-zone byte names: x in bits 4-6, z in bits 0-2. */
    private fun tile(packed: Int) = Coord(originX + zoneX + (packed shr 4 and 7), originZ + zoneZ + (packed and 7), level)

    private fun readZoneProt(prot: GameServerProt, buf: JagByteBuf) {
        when (prot) {
            GameServerProt.LOC_ADD_CHANGE_V2 -> {
                val properties = buf.g1Alt1()
                buf.g1() // op flags
                val coord = tile(buf.g1Alt1())
                repeat(buf.g1Alt2()) {
                    buf.g1Alt2() // op
                    buf.gjstr()
                }
                val id = buf.g2()
                val change = LocChange(id, properties shr 2, properties and 3, coord)
                locs[coord to change.layer] = change
            }
            GameServerProt.LOC_DEL -> {
                val properties = buf.g1Alt3()
                val coord = tile(buf.g1())
                val change = LocChange(-1, properties shr 2, properties and 3, coord)
                locs[coord to change.layer] = change
            }
            GameServerProt.OBJ_ADD -> {
                buf.g1Alt2() // ownership type
                val coord = tile(buf.g1Alt3())
                val id = buf.g2Alt1()
                buf.g2Alt2() // ticks until it despawns
                buf.g1Alt2() // op flags
                val count = buf.g4()
                buf.g2Alt3() // ticks until it turns public
                buf.g1Alt3() // never turns public
                objs.getOrPut(coord, ::ArrayList) += GroundObjView(id, count, coord)
            }
            GameServerProt.OBJ_DEL -> {
                val id = buf.g2Alt1()
                val coord = tile(buf.g1Alt2())
                val count = buf.g4()
                val onTile = objs[coord] ?: return
                // Guessed: the newest matching obj on the tile goes, as the client's obj list is newest first.
                val index = onTile.indexOfLast { it.id == id && it.count == count }
                if (index >= 0) onTile.removeAt(index)
                if (onTile.isEmpty()) objs.remove(coord)
            }
            GameServerProt.OBJ_COUNT -> {
                val coord = tile(buf.g1Alt1())
                val id = buf.g2Alt3()
                val newCount = buf.g4Alt3()
                val oldCount = buf.g4()
                val onTile = objs[coord] ?: return
                val index = onTile.indexOfLast { it.id == id && it.count == oldCount }
                if (index >= 0) onTile[index] = onTile[index].copy(count = newCount)
            }
            GameServerProt.LOC_ANIM -> buf.skipRead(1 + 1 + 2)
            GameServerProt.LOC_MERGE -> buf.skipRead(1 + 1 + 2 + 1 + 1 + 2 + 2 + 1 + 2 + 1 + 2)
            GameServerProt.OBJ_ENABLED_OPS -> buf.skipRead(2 + 1 + 1)
            GameServerProt.MAP_ANIM -> buf.skipRead(2 + 1 + 2 + 1)
            GameServerProt.SOUND_AREA -> buf.skipRead(1 + 1 + 1 + 1 + 1 + 2)
            GameServerProt.OBJ_CUSTOMISE -> buf.skipRead(1 + 2 + 2 + 4 + 2 + 2 + 2 + 2)
            GameServerProt.OBJ_UNCUSTOMISE -> buf.skipRead(1 + 2 + 4)
            GameServerProt.MAP_PROJANIM_V2 -> buf.skipRead(1 + 2 + 3 + 2 + 2 + 1 + 2 + 2 + 3 + 2 + 4)
            GameServerProt.SCRIPTEDPROJ_ADD -> buf.skipRead(2 + 3 + 2 + 2 + 2 + 2 + 2 + 2 + 3 + 2 + 2 + 2 + 2 + 2 + 4 + 2 + 1)
            GameServerProt.SCRIPTEDPROJ_CHANGE -> buf.skipRead(2 + 3 + 2 + 1 + 2 + 2 + 2 + 4)
            else -> error("$prot is not a zone prot")
        }
    }

    private companion object {
        const val BUILD_AREA_ZONE_RADIUS = 6

        /** The zone prots by their index inside UPDATE_ZONE_PARTIAL_ENCLOSED, in the client's order. */
        val ENCLOSED = listOf(
            GameServerProt.MAP_ANIM,
            GameServerProt.MAP_PROJANIM_V2,
            GameServerProt.OBJ_COUNT,
            GameServerProt.LOC_DEL,
            GameServerProt.OBJ_ENABLED_OPS,
            GameServerProt.OBJ_DEL,
            GameServerProt.LOC_ANIM,
            GameServerProt.OBJ_CUSTOMISE,
            GameServerProt.OBJ_UNCUSTOMISE,
            GameServerProt.OBJ_ADD,
            GameServerProt.SCRIPTEDPROJ_CHANGE,
            GameServerProt.LOC_ADD_CHANGE_V2,
            GameServerProt.LOC_MERGE,
            GameServerProt.SCRIPTEDPROJ_ADD,
            GameServerProt.SOUND_AREA,
        )
    }
}
