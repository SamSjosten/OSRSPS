package osrsps.game.combat

import kotlin.math.abs
import kotlin.math.max
import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.map.Tile

/**
 * How an NPC attacks players who haven't attacked it, from its combat file's `"aggressive"` (OSRS Wiki,
 * Aggressiveness, read 2026-10-30). [huntRange] is how far from its south-west tile it looks, not counting its size;
 * the Wiki gives no numbers, so 5, the 2004Scape engine's default `huntrange`, is guessed. [ignoresLevels]: it attacks
 * whatever the player's combat level, as bosses and most Wilderness monsters do. [tolerant]: it leaves a player alone
 * after their ten minutes in the area; bosses, dark beasts and bandits never do.
 */
data class NpcAggression(val huntRange: Int = DEFAULT_HUNT_RANGE, val ignoresLevels: Boolean = false, val tolerant: Boolean = true) {
    init {
        require(huntRange in 0..MAX_HUNT_RANGE) { "Bad hunt range $huntRange" }
    }

    companion object {
        const val DEFAULT_HUNT_RANGE = 5
        const val MAX_HUNT_RANGE = 32
    }
}

/**
 * Where a player's ten minutes towards aggressive monsters' tolerance are counted, as RuneLite's NPC Aggression Timer
 * plugin works it out (NpcAggroAreaPlugin, read 2026-10-30): two areas of [Aggression.AREA_RADIUS] tiles each way
 * around two centers. Each tick a player outside both moves them on (the newer center becomes the older, their tile
 * the newer) and starts the count again; a jump of more than [Aggression.TELEPORT_TILES] (a teleport) forgets the older
 * one too. The areas and the count are saved: logging out isn't leaving the area (Wiki). Guessed: that the count runs
 * in logged-in ticks only, and that levels (floors) don't matter, as they don't to the plugin.
 */
class Tolerance {
    /** The older center and the newer, null before there is one. */
    var older: Tile? = null
        internal set
    var newer: Tile? = null
        internal set

    /** Ticks spent in the areas since the count last started. */
    var ticks: Int = 0
        internal set

    /** Where the player stood when this was last counted, null after a login, for spotting a teleport. */
    internal var last: Tile? = null

    /** Whether tolerant monsters leave the player alone now. */
    val tolerant: Boolean get() = ticks >= Aggression.TOLERANCE_TICKS

    /** Puts saved areas and a count back, at a login. */
    fun restore(older: Tile?, newer: Tile?, ticks: Int) {
        require(ticks >= 0) { "Tolerance ticks $ticks" }
        this.older = older
        this.newer = newer
        this.ticks = ticks
        last = null
    }

    /** Sets the count, for `::aggro`. */
    fun setTicks(ticks: Int) {
        require(ticks >= 0) { "Tolerance ticks $ticks" }
        this.ticks = ticks
    }
}

/**
 * Aggressive NPCs hunting players, each tick before NPCs fight. An NPC with [NpcCombatDef.aggression] that isn't
 * fighting or busy with anyone goes for a player who is, all at once:
 * - within its hunt range of its south-west tile ([NpcAggression.huntRange], not counting its size, so a big NPC may
 *   not see a player beside its north or east side);
 * - within its aggression range of its spawn point, which is its max range ([Npcs.MAX_RANGE]) plus its attack range,
 *   counting its size (Wiki); melee's 1 tile is taken for everyone until NPCs attack from afar;
 * - of a combat level at most twice its own, unless it is level 63 or more or ignores levels (Wiki);
 * - not tolerated: [Tolerance] has their ten minutes, unless it never becomes tolerant;
 * - not under attack by another NPC, in single-way combat ([MeleeCombat.underAttackByAnother]).
 * It picks the nearest by its south-west tile, ties at random, then chases and fights them as if they had hit it.
 * Guessed (the Wiki gives none of these): that it hunts every tick, that it picks the nearest, and the tie rule. Not
 * here yet: line of sight (a hunt mode's check_vis), retreat, and attacks from afar.
 */
class Aggression(
    private val npcs: Npcs,
    private val npcCombat: (Npc) -> NpcCombatDef?,
    private val melee: MeleeCombat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    private val players: () -> Sequence<Player>,
) {
    /** Counts every player's tolerance, then lets each aggressive NPC hunt. */
    fun process() {
        for (player in players()) count(player)
        val tick = currentTick()
        for (npc in npcs.all) hunt(npc, tick)
    }

    /** One tick of [player]'s [Tolerance]: a tick more in the areas, or new ones. */
    fun count(player: Player) {
        val tolerance = player.tolerance
        val here = Tile(player.x, player.z)
        val last = tolerance.last
        tolerance.last = here
        if (last != null && chebyshev(last, here) > TELEPORT_TILES) {
            tolerance.older = null
            tolerance.newer = here
            tolerance.ticks = 0
            return
        }
        if (!inArea(tolerance.older, here) && !inArea(tolerance.newer, here)) {
            tolerance.older = tolerance.newer
            tolerance.newer = here
            tolerance.ticks = 0
            return
        }
        if (tolerance.ticks < Int.MAX_VALUE) tolerance.ticks++
    }

    private fun hunt(npc: Npc, tick: Long) {
        val def = npcCombat(npc) ?: return
        val aggression = def.aggression ?: return
        if (npc.dead || npc.target != null || npc.engagedWith != null) return
        var nearest = Int.MAX_VALUE
        val chosen = ArrayList<Player>(1)
        for (player in players()) {
            if (!huntable(npc, aggression, player)) continue
            val distance = max(abs(player.x - npc.x), abs(player.z - npc.z))
            if (distance < nearest) {
                nearest = distance
                chosen.clear()
            }
            if (distance == nearest) chosen += player
        }
        if (chosen.isEmpty()) return
        // The dice only when there is a tie, so a lone player costs no draw from the shared random.
        val target = if (chosen.size == 1) chosen[0] else chosen[random.nextInt(chosen.size)]
        melee.aggress(npc, target, tick)
    }

    /** Whether [npc] would go for [player]; see the class note. */
    fun huntable(npc: Npc, aggression: NpcAggression, player: Player): Boolean {
        if (player.loggedOut || player.dead || player.hitpoints <= 0 || player.level != npc.level) return false
        if (max(abs(player.x - npc.x), abs(player.z - npc.z)) > aggression.huntRange) return false
        if (!inAggressionRange(npc, player)) return false
        if (!aggression.ignoresLevels && npc.type.combatLevel < ALWAYS_LEVEL && player.skills.combatLevel > 2 * npc.type.combatLevel) return false
        if (aggression.tolerant && player.tolerance.tolerant) return false
        return !melee.underAttackByAnother(player, npc)
    }

    /** Whether [player] stands within [npc]'s max range plus attack range of its spawn point, counting its size. */
    private fun inAggressionRange(npc: Npc, player: Player): Boolean {
        val range = Npcs.MAX_RANGE + ATTACK_RANGE
        val size = npc.type.size
        val spawn = npc.spawn
        return player.x in spawn.x - range until spawn.x + size + range && player.z in spawn.z - range until spawn.z + size + range
    }

    /** What `::aggro` says of [player]'s tolerance. */
    fun describe(player: Player): String {
        val tolerance = player.tolerance
        val areas = listOfNotNull(tolerance.older, tolerance.newer).joinToString(" and ") { "${it.x}, ${it.z}" }
        val state = if (tolerance.tolerant) {
            "aggressive monsters here are tolerant of you"
        } else {
            "aggressive monsters here leave you alone in ${TOLERANCE_TICKS - tolerance.ticks} ticks"
        }
        return "${tolerance.ticks} ticks in the areas around ${areas.ifEmpty { "nowhere yet" }}: $state."
    }

    companion object {
        /** Ten minutes. */
        const val TOLERANCE_TICKS = 1000

        /** Each tolerance area reaches this far each way from its center: 21 by 21 tiles (RuneLite). */
        const val AREA_RADIUS = 10

        /** A move further than this in a tick is a teleport, which starts the areas again (RuneLite). */
        const val TELEPORT_TILES = 40

        /** A monster of at least this level attacks whatever the player's level (Wiki). */
        const val ALWAYS_LEVEL = 63

        /** Melee's reach, the attack range in [inAggressionRange]. */
        const val ATTACK_RANGE = 1

        private fun chebyshev(a: Tile, b: Tile) = max(abs(a.x - b.x), abs(a.z - b.z))

        private fun inArea(center: Tile?, tile: Tile) = center != null && chebyshev(center, tile) <= AREA_RADIUS
    }
}
