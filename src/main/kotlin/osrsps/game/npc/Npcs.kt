package osrsps.game.npc

import osrsps.game.GameRandom
import osrsps.map.CollisionMap
import osrsps.map.Steps
import osrsps.map.Tile
import kotlin.math.abs
import kotlin.math.sign

/**
 * Every NPC in the world, and how they move: wandering near their spawn, walking a patrol, one tile a tick.
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

    /** The NPCs on [level] whose south-west tile is within [radius] tiles of [x], [z] on both axes. */
    fun near(x: Int, z: Int, level: Int, radius: Int): List<Npc> =
        all.filter { it.level == level && abs(it.x - x) <= radius && abs(it.z - z) <= radius }.toList()

    /** One tick of movement for every NPC, in index order. */
    fun process() {
        for (npc in npcs) {
            if (npc == null) continue
            if (npc.spawn.patrol.isNotEmpty()) patrol(npc) else wander(npc)
            step(npc)
        }
    }

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

    /** Guessed: the next patrol tile is set as soon as the last one is reached or given up on. */
    private fun patrol(npc: Npc) {
        if (npc.destination != null) return
        val patrol = npc.spawn.patrol
        npc.destination = patrol[npc.patrolIndex]
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
        occupy(npc, -1)
        npc.x += sx
        npc.z += sz
        occupy(npc, 1)
        npc.avatar?.walk(sx, sz)
        if (npc.x == target.x && npc.z == target.z) npc.destination = null
    }

    /** Whether [npc] can take one step of [dx], [dz]: the map allows it and no other NPC stands in the way. */
    fun canStep(npc: Npc, dx: Int, dz: Int): Boolean {
        val size = npc.type.size
        if (!Steps.canStep(collision, npc.level, npc.x, npc.z, dx, dz, size)) return false
        // Inferred from live, where NPCs don't stack: tiles the NPC steps onto must be free of others. Players don't
        // block NPCs here; that isn't checked.
        for (tx in npc.x + dx until npc.x + dx + size) {
            for (tz in npc.z + dz until npc.z + dz + size) {
                if (!npc.covers(tx, tz) && (occupied[tileKey(tx, tz, npc.level)] ?: 0) > 0) return false
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
    }
}
