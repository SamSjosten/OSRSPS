package osrsps.game.zone

import io.netty.buffer.ByteBuf
import net.rsprot.protocol.api.util.ZonePartialEnclosedCacheBuffer
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.util.OpFlags
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialFollows
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim
import net.rsprot.protocol.game.outgoing.zone.payload.LocDel
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import net.rsprot.protocol.game.outgoing.zone.payload.ObjDel
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import net.rsprot.protocol.message.OutgoingGameMessage
import net.rsprot.protocol.message.ZoneProt
import osrsps.game.Player

/** A stack of objs on the ground. Only [owner] sees it while [owner] is set; nobody else is told about it. */
class ZoneObj(
    val id: Int,
    val count: Int,
    val x: Int,
    val z: Int,
    val level: Int,
    /** The username that alone can see it, or null when everyone can. */
    val owner: String? = null,
)

/** A loc placed where the cache has a different one, or none. */
data class ZoneLoc(val id: Int, val shape: Int, val angle: Int, val x: Int, val z: Int, val level: Int) {
    /** The client keeps one loc per layer on a tile: wall, wall decoration, ground loc and ground decoration. */
    val layer: Int get() = layerOf(shape)

    companion object {
        /** Shapes 0-3 are walls, 4-8 wall decorations, 9-21 ground locs and roofs, 22 ground decoration (the client's split). */
        fun layerOf(shape: Int): Int = when (shape) {
            in 0..3 -> 0
            in 4..8 -> 1
            in 9..21 -> 2
            22 -> 3
            else -> throw IllegalArgumentException("No loc shape $shape")
        }
    }
}

/** Which zones a player's client is getting updates for. Reset whenever the client rebuilds its map. */
class ZoneView {
    internal var centre: ZoneKey? = null
    internal val visible = LinkedHashSet<Int>()

    /** Forgets everything, so every zone in view is sent in full on the next update. */
    fun reset() {
        centre = null
        visible.clear()
    }

    /** The zones in view, for tests. */
    val zones: List<ZoneKey> get() = visible.map(::ZoneKey)
}

/**
 * Per-zone updates: what every player near a zone must see happen in it.
 *
 * Two kinds of thing live here. Lasting changes (locs that differ from the cache, objs on the ground) are kept per zone
 * and sent in full, after an UpdateZoneFullFollows that wipes the client's copy, whenever a zone comes into a player's
 * view. Everything that happened in a zone this cycle (those same changes, plus graphics, projectiles and sounds) is
 * sent once to each player already watching the zone, in one UpdateZonePartialEnclosed whose payload is encoded once
 * and shared. Objs only one player can see go to that player alone in an UpdateZonePartialFollows.
 *
 * A player watches the zones within [VIEW_RADIUS] of their own on their level that fit in their build area. That
 * radius and the rest of this flow follow RSMod's and RSProt's reading of the client, not a recording of live.
 */
class Zones {
    private val states = HashMap<Int, ZoneState>()
    private val pending = LinkedHashMap<Int, MutableList<ZoneEvent>>()
    private val enclosedCache = ZonePartialEnclosedCacheBuffer(listOf(OldSchoolClientType.DESKTOP))
    private val enclosedPayloads = HashMap<Int, ByteBuf>()

    private class ZoneState {
        /** By tile-in-zone and layer: what the client must be told on top of the cache. */
        val locs = LinkedHashMap<Int, LocChange>()
        val objs = ArrayList<ZoneObj>()

        val isEmpty: Boolean get() = locs.isEmpty() && objs.isEmpty()
    }

    private class LocChange(val loc: ZoneLoc, val removed: Boolean)

    /** One update in a zone this cycle. [onlyFor] is the username that alone sees it, or null for everyone. */
    private class ZoneEvent(val prot: ZoneProt, val onlyFor: String?)

    /** Plays spotanim [id] on a tile. [height] is above the ground; [delay] is in client cycles (20 ms). */
    fun mapAnim(x: Int, z: Int, level: Int, id: Int, height: Int = 0, delay: Int = 0) =
        event(x, z, level, MapAnim(id, delay, height, x and 7, z and 7))

    /** Fires a projectile; it belongs to the zone it starts in. */
    fun projectile(projectile: Projectile) = with(projectile) {
        event(
            fromX, fromZ, level,
            MapProjAnimV2(
                id, startHeight, endHeight, startTime, endTime, angle, progress, sourceIndex, targetIndex,
                fromX and 7, fromZ and 7, toX, toZ, level,
            ),
        )
    }

    /** Plays sound [id] from a tile, heard up to [radius] tiles away (31 at most). */
    fun soundArea(x: Int, z: Int, level: Int, id: Int, radius: Int = 5, loops: Int = 1, delay: Int = 0, size: Int = 1) =
        event(x, z, level, SoundArea(id, delay, loops, radius, size, x and 7, z and 7))

    /** Plays animation [anim] on the loc of [shape] and [angle] on a tile. */
    fun locAnim(x: Int, z: Int, level: Int, shape: Int, angle: Int, anim: Int) =
        event(x, z, level, LocAnim(anim, x and 7, z and 7, shape, angle))

    /** Puts [obj] on the ground. */
    fun addObj(obj: ZoneObj) {
        state(obj.x, obj.z, obj.level).objs += obj
        event(obj.x, obj.z, obj.level, objAdd(obj), obj.owner)
    }

    /** Takes [obj] off the ground. Returns false if it wasn't there. */
    fun removeObj(obj: ZoneObj): Boolean {
        val key = ZoneKey.of(obj.x, obj.z, obj.level).packed
        val state = states[key] ?: return false
        if (!state.objs.removeIf { it === obj }) return false
        if (state.isEmpty) states.remove(key)
        event(obj.x, obj.z, obj.level, ObjDel(obj.id, obj.count, obj.x and 7, obj.z and 7), obj.owner)
        return true
    }

    /** The objs on a tile, oldest first, including ones only some players see. */
    fun objsAt(x: Int, z: Int, level: Int): List<ZoneObj> =
        states[ZoneKey.of(x, z, level).packed]?.objs?.filter { it.x == x && it.z == z }.orEmpty()

    /** Shows [loc] on its tile, replacing whatever was on its layer there. Collision is the caller's business. */
    fun addLoc(loc: ZoneLoc) {
        state(loc.x, loc.z, loc.level).locs[locSlot(loc.x, loc.z, loc.layer)] = LocChange(loc, removed = false)
        event(loc.x, loc.z, loc.level, locAddChange(loc))
    }

    /** Removes the loc of [shape] and [angle] from a tile, whether it came from the cache or [addLoc]. */
    fun removeLoc(x: Int, z: Int, level: Int, shape: Int, angle: Int) {
        val loc = ZoneLoc(-1, shape, angle, x, z, level)
        state(x, z, level).locs[locSlot(x, z, loc.layer)] = LocChange(loc, removed = true)
        event(x, z, level, LocDel(x and 7, z and 7, shape, angle))
    }

    /**
     * Sends [player] this cycle's zone updates through [out]: zones that came into view in full, and what happened this
     * cycle in the zones they were already watching. Call once a cycle per player, after their map rebuild if any.
     */
    fun update(player: Player, out: (OutgoingGameMessage) -> Unit) {
        val view = player.zoneView
        val centre = ZoneKey.of(player.x, player.z, player.level)
        val buildAreaX = (player.buildAreaZoneX - BUILD_AREA_ZONE_RADIUS) shl 3
        val buildAreaZ = (player.buildAreaZoneZ - BUILD_AREA_ZONE_RADIUS) shl 3
        if (centre == view.centre) {
            for (zone in view.visible) sendPartial(player.username, ZoneKey(zone), buildAreaX, buildAreaZ, out)
            return
        }
        val inView = zonesInView(centre, buildAreaX, buildAreaZ)
        for (zone in inView) {
            if (zone.packed !in view.visible) sendFull(player.username, zone, buildAreaX, buildAreaZ, out)
        }
        // A zone that just came into view was sent in full, which already has this cycle's lasting changes. Its
        // graphics and sounds from this cycle are not sent, as RSMod does; live is not checked.
        for (zone in inView) {
            if (zone.packed in view.visible) sendPartial(player.username, zone, buildAreaX, buildAreaZ, out)
        }
        view.visible.clear()
        inView.mapTo(view.visible) { it.packed }
        view.centre = centre
    }

    /** Forgets this cycle's updates and frees their shared buffers. Call once at the end of every cycle. */
    fun endCycle() {
        pending.clear()
        enclosedPayloads.clear()
        enclosedCache.releaseBuffers()
    }

    private fun zonesInView(centre: ZoneKey, buildAreaX: Int, buildAreaZ: Int): List<ZoneKey> {
        val zones = ArrayList<ZoneKey>((2 * VIEW_RADIUS + 1) * (2 * VIEW_RADIUS + 1))
        for (dx in -VIEW_RADIUS..VIEW_RADIUS) {
            for (dz in -VIEW_RADIUS..VIEW_RADIUS) {
                val x = centre.x + dx
                val z = centre.z + dz
                if (x !in 0 until MAX_ZONE || z !in 0 until MAX_ZONE) continue
                val zone = ZoneKey(x, z, centre.level)
                if (zone.baseX - buildAreaX in 0 until BUILD_AREA_SIZE && zone.baseZ - buildAreaZ in 0 until BUILD_AREA_SIZE) {
                    zones += zone
                }
            }
        }
        return zones
    }

    private fun sendFull(observer: String, zone: ZoneKey, buildAreaX: Int, buildAreaZ: Int, out: (OutgoingGameMessage) -> Unit) {
        out(UpdateZoneFullFollows(zone.baseX - buildAreaX, zone.baseZ - buildAreaZ, zone.level))
        val state = states[zone.packed] ?: return
        for (change in state.locs.values) {
            val loc = change.loc
            out(if (change.removed) LocDel(loc.x and 7, loc.z and 7, loc.shape, loc.angle) else locAddChange(loc))
        }
        for (obj in state.objs) {
            if (obj.owner == null || obj.owner == observer) out(objAdd(obj))
        }
    }

    private fun sendPartial(observer: String, zone: ZoneKey, buildAreaX: Int, buildAreaZ: Int, out: (OutgoingGameMessage) -> Unit) {
        val events = pending[zone.packed] ?: return
        val dx = zone.baseX - buildAreaX
        val dz = zone.baseZ - buildAreaZ
        val payload = enclosedPayloads[zone.packed] ?: sharedPayload(zone, events)
        if (payload != null) out(UpdateZonePartialEnclosed(dx, dz, zone.level, payload))
        // Guessed: private updates follow the shared ones, so their order against each other is lost.
        var header = false
        for (event in events) {
            if (event.onlyFor != observer) continue
            if (!header) {
                out(UpdateZonePartialFollows(dx, dz, zone.level))
                header = true
            }
            out(event.prot)
        }
    }

    /** Encodes the zone's public updates once for everyone watching it; null if they are all private. */
    private fun sharedPayload(zone: ZoneKey, events: List<ZoneEvent>): ByteBuf? {
        val public = events.filter { it.onlyFor == null }.map { it.prot }
        if (public.isEmpty()) return null
        val payload = enclosedCache.computeZoneForClient(OldSchoolClientType.DESKTOP, public)
        enclosedPayloads[zone.packed] = payload
        return payload
    }

    private fun event(x: Int, z: Int, level: Int, prot: ZoneProt, onlyFor: String? = null) {
        pending.getOrPut(ZoneKey.of(x, z, level).packed, ::ArrayList) += ZoneEvent(prot, onlyFor)
    }

    private fun state(x: Int, z: Int, level: Int): ZoneState = states.getOrPut(ZoneKey.of(x, z, level).packed, ::ZoneState)

    private fun locSlot(x: Int, z: Int, layer: Int) = (x and 7) or ((z and 7) shl 3) or (layer shl 6)

    // Guessed: every option shown, until item and loc types say otherwise.
    private fun objAdd(obj: ZoneObj) = ObjAdd(obj.id, obj.count, obj.x and 7, obj.z and 7, OpFlags.ALL_SHOWN)

    private fun locAddChange(loc: ZoneLoc) = LocAddChangeV2(loc.id, loc.x and 7, loc.z and 7, loc.shape, loc.angle, OpFlags.ALL_SHOWN)

    companion object {
        /** Zones watched in each direction: a 7x7 block of zones (56x56 tiles) around the player. From RSMod, not live. */
        const val VIEW_RADIUS = 3

        /** The client's build area is 13x13 zones, centred on the zone it was built around. */
        const val BUILD_AREA_ZONE_RADIUS = 6
        const val BUILD_AREA_SIZE = 104

        /** Zones per side of the map: 16,384 tiles. */
        private const val MAX_ZONE = 2048
    }
}

/**
 * A projectile from one tile to another. Heights are in the client's height units, no longer multiplied by 4 in the
 * client; times are client cycles (20 ms) from when the client gets it. [sourceIndex] and [targetIndex] lock it to an
 * entity: 0 for none, `-(index + 1)` for a player, `index + 1` for an NPC.
 */
data class Projectile(
    val id: Int,
    val fromX: Int,
    val fromZ: Int,
    val level: Int,
    val toX: Int,
    val toZ: Int,
    val startHeight: Int,
    val endHeight: Int,
    val startTime: Int,
    val endTime: Int,
    val angle: Int,
    val progress: Int,
    val sourceIndex: Int = 0,
    val targetIndex: Int = 0,
)
