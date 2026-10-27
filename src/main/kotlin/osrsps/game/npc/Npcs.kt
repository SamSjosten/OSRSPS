package osrsps.game.npc

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.Reach
import osrsps.map.Steps
import osrsps.map.Tile
import kotlin.math.abs
import kotlin.math.sign

/**
 * Every NPC in the world, and how they move: wandering near their spawn, walking a patrol, or following a player, one
 * tile a tick. An NPC busy with a player ([Npc.engagedWith]) stands still.
 *
 * From the OSRS Wiki ("Wander radius", "Pathfinding"): an NPC picks a tile inside the square of its wander radius
 * around its spawn, then walks to it, and it does so even if something has taken it outside that square. NPCs walk
 * "naively": diagonally toward the tile, then straight once no diagonal is left.
 *
 * Guessed, not from the wiki or a recording, and marked where they are used: how often an idle NPC picks a new tile
 * ([WANDER_CHANCE]), which straight step it tries when the diagonal is blocked, that it gives up when no step is
 * possible, and that NPCs can't walk into each other.
 *
 * Every random roll comes from [random], one NPC at a time in index order, so a seed gives the same paths.
 * Only touched on the game thread.
 */
class Npcs(
    private val collision: CollisionMap,
    private val random: GameRandom,
) {
    private val npcs = arrayOfNulls<Npc>(MAX_NPCS)

    /** How many NPCs cover each tile, by [tileKey]. */
    private val occupied = HashMap<Int, Int>()

    val count: Int get() = npcs.count { it != null }

    /** Every NPC, in index order. */
    val all: Sequence<Npc> get() = npcs.asSequence().filterNotNull()

    operator fun get(index: Int): Npc? = npcs.getOrNull(index)

    /** Puts a new NPC in the world at its spawn. Returns null when every slot is taken. */
    fun add(spawn: NpcSpawn): Npc? {
        // Index 0 is left free, as players leave theirs.
        val index = (1 until MAX_NPCS).firstOrNull { npcs[it] == null } ?: return null
        val npc = Npc(index, spawn)
        npcs[index] = npc
        occupy(npc, 1)
        return npc
    }

    fun remove(npc: Npc) {
        if (npcs[npc.index] !== npc) return
        occupy(npc, -1)
        npcs[npc.index] = null
    }

    /**
     * Has [npc] follow [player] until [stopFollowing]: it walks after them and stops beside them, the way an NPC
     * chases its target in combat. It walks naively, as NPCs do (OSRS Wiki, "Pathfinding"), so a wall between them can
     * hold it up. It faces them while it follows. Pets trail a tile behind instead; that isn't modelled.
     */
    fun follow(npc: Npc, player: Player) {
        npc.following = player
        npc.destination = null
        // Busy with someone, it keeps facing them; release turns it to the player it follows.
        if (npc.engagedWith == null) npc.face(player)
    }

    fun stopFollowing(npc: Npc) {
        if (npc.following == null) return
        npc.following = null
        if (npc.engagedWith == null) npc.resetFace()
    }

    /** Lets go of [player] everywhere: they left the world. NPCs following or busy with them go back to what they did. */
    fun forget(player: Player, stillBusy: (Player) -> Boolean = { true }) {
        for (npc in all) if (npc.following === player) stopFollowing(npc)
        leave(player, stillBusy)
    }

    /**
     * [player] turned to something else (walked off, clicked another NPC or a loc, teleported, left): NPCs busy with
     * them pass to their next waiting player who is [stillBusy], or go back to what they did, and they stop waiting on
     * any NPC.
     */
    fun leave(player: Player, stillBusy: (Player) -> Boolean = { true }) {
        for (npc in all) {
            npc.waiting.remove(player)
            if (npc.engagedWith === player) handOver(npc, npc.engagedTick, stillBusy)
        }
    }

    /** [npc] is busy with [player], from [tick]: it stops, faces them, and doesn't wander until [release]. */
    fun engage(npc: Npc, player: Player, tick: Long) {
        npc.waiting.remove(player)
        npc.engagedWith = player
        npc.engagedTick = tick
        npc.destination = null
        npc.face(player)
    }

    /** [player] used an option on [npc] while it was busy with someone else; it turns to them when that one is done. */
    fun wait(npc: Npc, player: Player) {
        if (npc.engagedWith !== player && player !in npc.waiting) npc.waiting += player
    }

    /**
     * [npc]'s player is done with it: the oldest waiting player who is [stillBusy] with it takes over from [tick], or,
     * with none, it goes back to what it was doing. Guessed, as live NPCs talk to several players at once.
     */
    fun handOver(npc: Npc, tick: Long, stillBusy: (Player) -> Boolean = { true }) {
        while (npc.waiting.isNotEmpty()) {
            val next = npc.waiting.removeAt(0)
            if (stillBusy(next)) {
                engage(npc, next, tick)
                return
            }
        }
        release(npc)
    }

    fun release(npc: Npc) {
        npc.waiting.clear()
        if (npc.engagedWith == null) return
        npc.engagedWith = null
        val following = npc.following
        if (following != null) npc.face(following) else npc.resetFace()
    }

    /** The NPCs on [level] whose south-west tile is within [radius] tiles of [x], [z] on both axes. */
    fun near(x: Int, z: Int, level: Int, radius: Int): List<Npc> =
        all.filter { it.level == level && abs(it.x - x) <= radius && abs(it.z - z) <= radius }.toList()

    /** One tick of movement for every NPC, in index order. */
    fun process() {
        for (npc in npcs) {
            if (npc == null) continue
            val following = npc.following
            if (npc.holdTicks > 0) {
                // Being fought, it stands and faces its attacker, unless it fights back: then it chases them. Guessed.
                npc.holdTicks--
                if (following == null) continue
            } else if (npc.attacker != null && npc.target == null) {
                // The fight is over for it: nobody's any more, and it looks where it did before (guessed).
                npc.attacker = null
                if (npc.engagedWith == null) {
                    if (following != null) npc.face(following) else npc.resetFace()
                }
            }
            when {
                // Guessed: an NPC busy with a player doesn't move, even if it follows someone.
                npc.engagedWith != null -> continue
                following != null -> chase(npc, following)
                npc.spawn.patrol.isNotEmpty() -> patrol(npc)
                else -> wander(npc)
            }
            step(npc)
        }
    }

    /**
     * One step after [target]. Beside them it stays put. Under them it steps out, trying west, east, south, then north
     * (guessed order). Otherwise it steps toward them, diagonally first, never onto their tile, so from a corner it
     * slides along to their side. Guessed: it gives up when the target is on another level or more than
     * [FOLLOW_RANGE] tiles away.
     */
    private fun chase(npc: Npc, target: Player) {
        val size = npc.type.size
        if (target.level != npc.level || abs(target.x - npc.x) > FOLLOW_RANGE || abs(target.z - npc.z) > FOLLOW_RANGE) {
            stopFollowing(npc)
            return
        }
        if (Reach.reachedEntity(collision, npc.level, npc.x, npc.z, size, target.x, target.z, 1, 1)) return
        val (sx, sz) = if (npc.covers(target.x, target.z)) {
            // The side that clears the target in the fewest steps; a 3x3 or bigger NPC can need several ticks.
            STEP_OUT.sortedBy { (dx, dz) -> stepsToClear(npc, dx, dz, target) }.firstOrNull { (dx, dz) -> canStep(npc, dx, dz) }
                ?: return
        } else {
            // Toward the target from the nearest edge of the NPC, so a big NPC lines up with them.
            val dx = if (target.x < npc.x) -1 else if (target.x >= npc.x + size) 1 else 0
            val dz = if (target.z < npc.z) -1 else if (target.z >= npc.z + size) 1 else 0
            sequenceOf(dx to dz, dx to 0, 0 to dz)
                .filter { (sx, sz) -> sx != 0 || sz != 0 }
                .firstOrNull { (sx, sz) -> !overlaps(npc.x + sx, npc.z + sz, size, target.x, target.z) && canStep(npc, sx, sz) }
                ?: return
        }
        // Fighting, it keeps within its max range of its spawn (OSRS Wiki, Retreat: almost always 7 tiles), standing
        // at the edge rather than stepping past it. Guessed that this is the same range a chasing NPC keeps to.
        if (npc.target != null && (abs(npc.x + sx - npc.spawn.x) > MAX_RANGE || abs(npc.z + sz - npc.spawn.z) > MAX_RANGE)) return
        move(npc, sx, sz)
    }

    /** Steps [npc] must take in direction [dx], [dz] (one of [STEP_OUT]) until it no longer covers [target]. */
    private fun stepsToClear(npc: Npc, dx: Int, dz: Int, target: Player): Int {
        val size = npc.type.size
        return when {
            dx < 0 -> npc.x + size - target.x
            dx > 0 -> target.x - npc.x + 1
            dz < 0 -> npc.z + size - target.z
            else -> target.z - npc.z + 1
        }
    }

    private fun overlaps(x: Int, z: Int, size: Int, tileX: Int, tileZ: Int) = tileX in x until x + size && tileZ in z until z + size

    private fun wander(npc: Npc) {
        val range = npc.spawn.wander
        // Rolls only for NPCs that can wander and are standing, so a still NPC never uses up a roll. Guessed.
        if (range == 0 || npc.destination != null) return
        // Guessed: 1 in 8 a tick, as the 2004Scape project's RS2 engine has it; not measured on OSRS.
        if (!random.chance(1, WANDER_CHANCE)) return
        val x = npc.spawn.x + random.between(-range, range)
        val z = npc.spawn.z + random.between(-range, range)
        if (x != npc.x || z != npc.z) npc.destination = Tile(x, z)
    }

    /**
     * The next stop is set once the last one is reached and its wait has passed: an NPC that arrives on tick t with a
     * wait of n stands from t + 1 to t + n and steps off on t + n + 1, as in RS2 (the 2004Scape project). Guessed: a
     * stop it gives up on, blocked, counts as reached, so it waits where it stopped and then heads for the next one.
     */
    private fun patrol(npc: Npc) {
        if (npc.destination != null) return
        if (npc.patrolWait > 0) {
            npc.patrolWait--
            return
        }
        val patrol = npc.spawn.patrol
        val stop = patrol[npc.patrolIndex]
        npc.destination = Tile(stop.x, stop.z)
        npc.patrolWait = stop.wait
        npc.patrolIndex = (npc.patrolIndex + 1) % patrol.size
    }

    /** One naive step toward the destination. */
    private fun step(npc: Npc) {
        val target = npc.destination ?: return
        val dx = (target.x - npc.x).sign
        val dz = (target.z - npc.z).sign
        if (dx == 0 && dz == 0) {
            npc.destination = null
            return
        }
        // Guessed: with the diagonal blocked, the x step is tried before the z step.
        val (sx, sz) = sequenceOf(dx to dz, dx to 0, 0 to dz)
            .filter { (sx, sz) -> sx != 0 || sz != 0 }
            .firstOrNull { (sx, sz) -> canStep(npc, sx, sz) }
            ?: run {
                // Guessed: a blocked NPC gives up on the tile and picks another later.
                npc.destination = null
                return
            }
        move(npc, sx, sz)
        if (npc.x == target.x && npc.z == target.z) npc.destination = null
    }

    private fun move(npc: Npc, dx: Int, dz: Int) {
        occupy(npc, -1)
        npc.x += dx
        npc.z += dz
        occupy(npc, 1)
        npc.avatar?.walk(dx, dz)
    }

    /**
     * Whether [npc] can take one step of [dx], [dz]: the map allows it, its [MoveRestrict] does, and no other NPC stands
     * in the way.
     */
    fun canStep(npc: Npc, dx: Int, dz: Int): Boolean {
        val size = npc.type.size
        if (!Steps.canStep(collision, npc.level, npc.x, npc.z, dx, dz, size)) return false
        if (!restrictAllows(npc, dx, dz)) return false
        // Inferred from live, where NPCs don't stack: tiles the NPC steps onto must be free of others. Players don't
        // block NPCs here; that isn't checked.
        for (tx in npc.x + dx until npc.x + dx + size) {
            for (tz in npc.z + dz until npc.z + dz + size) {
                if (!npc.covers(tx, tz) && (occupied[tileKey(tx, tz, npc.level)] ?: 0) > 0) return false
            }
        }
        return true
    }

    /**
     * An indoors NPC needs a roof over every tile the step crosses, an outdoors one open sky: the tiles it moves onto
     * and, for a diagonal, both tiles beside the corner, as RS2's step checks read each of them (the 2004Scape
     * project's route finder). Inferred, not seen on live.
     */
    private fun restrictAllows(npc: Npc, dx: Int, dz: Int): Boolean {
        val restrict = npc.spawn.restrict
        if (restrict == MoveRestrict.NORMAL) return true
        val size = npc.type.size
        val offsets = if (dx != 0 && dz != 0) listOf(dx to 0, 0 to dz, dx to dz) else listOf(dx to dz)
        for ((ox, oz) in offsets) {
            for (tx in npc.x + ox until npc.x + ox + size) {
                for (tz in npc.z + oz until npc.z + oz + size) {
                    if (npc.covers(tx, tz)) continue
                    val roofed = collision[tx, tz, npc.level] and CollisionFlag.ROOF != 0
                    if (roofed != (restrict == MoveRestrict.INDOORS)) return false
                }
            }
        }
        return true
    }

    private fun occupy(npc: Npc, change: Int) {
        val size = npc.type.size
        for (tx in npc.x until npc.x + size) {
            for (tz in npc.z until npc.z + size) {
                val key = tileKey(tx, tz, npc.level)
                val count = (occupied[key] ?: 0) + change
                if (count <= 0) occupied.remove(key) else occupied[key] = count
            }
        }
    }

    private fun tileKey(x: Int, z: Int, level: Int) = (level shl 28) or (x shl 14) or z

    companion object {
        /** RSProt's NPC indices run to 65534; far more than one test world needs. */
        const val MAX_NPCS = 32_768

        /** An idle wandering NPC picks a tile with a chance of 1 in this, each tick. Guessed; see [wander]. */
        const val WANDER_CHANCE = 8

        /** A following NPC gives up on a target further than this on either axis. Guessed; see [chase]. */
        const val FOLLOW_RANGE = 32

        /** How far from its spawn, on either axis, an NPC walks after a player it fights; see [chase]. */
        const val MAX_RANGE = 7

        private val STEP_OUT = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
