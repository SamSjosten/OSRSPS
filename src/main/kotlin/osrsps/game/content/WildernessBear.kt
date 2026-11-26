package osrsps.game.content

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.map.LineOfSight
import osrsps.map.Tile

/**
 * Callisto's fight and Artio's, its weaker copy (Wilderness steps W4.8 and W4.12; the Wiki's Artio, Callisto and their
 * Strategies pages, read 2026-11-15, which give the two the same attacks at different numbers).
 *
 * - **The swipe** beside the target: [Config.meleeMin] to [Config.meleeMax] (Mod Ash: their melee's minimums are 15 and
 *   35), splashing onto everyone within a tile of the target, about half through Protect from Melee.
 * - **The crescents**, when the bear can't reach: one at everyone in the lair, ranged, up to [Config.rangedMax].
 * - **The knockback**: a white orb at the target that hits everyone within a tile of them unless they pray Protect from
 *   Magic, throwing each 3 tiles away: 5 damage when all 3 tiles are open, 15 more for each tile a wall cuts short (so
 *   50 against one), and 5 when a trap stops them.
 * - **The traps**: at 66% and 33% of the bear's hitpoints it roars, breaking any freeze, and traps cover the lair; a trap
 *   stepped on hurts up to 15 and binds; from 66% the traps are always down, a fresh set as the last runs out, and from
 *   33% a fresh set with every attack.
 *
 * GUESSED until recorded: the swipe always landing, halved under Protect from Melee, the splash's 3x3 round the target, each player rolling their own hit;
 * the knockback one attack in [KNOCKBACK_ODDS] and its flight; [TRAPS] traps, lasting [TRAP_TICKS] ticks, each binding
 * for [BIND_TICKS] ticks and gone once sprung, and never under a player when laid; the animations (the cache's
 * npc_callisto ones by name), the crescent's graphics (fx_callisto_ranged), the orb's (none in flight; the crescent's
 * impact on landing), the traps' (fx_callisto_trap) and the roar's (fx_callisto_enrage). Left out: the first freeze
 * always landing.
 */
class WildernessBear(val config: Config) {
    class Config(
        val name: String,
        val boss: Int,
        val meleeMin: Int,
        val meleeMax: Int,
        val rangedMax: Int,
        val speed: Int,
        val inArena: (Int, Int) -> Boolean,
    )

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[config.boss] ?: return null
        return EncounterDef(
            config.name, type,
            // Unleashed: it follows anywhere in its lair, not only near its spawn (Codex review of W4.8).
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = { boss.unleashed = true })),
            scriptedAttacks = true,
        )
    }

    /** One fight's state, by boss. */
    private class Fight {
        /** Roars done: 0, 1 at 66%, 2 at 33%. */
        var roars = 0
        val traps = HashSet<Tile>()
        var trapsEnd = -1L
    }

    private val fights = java.util.WeakHashMap<Npc, Fight>()

    /** The tiles her traps cover now, for tests. */
    fun traps(npc: Npc): Set<Tile> = fights[npc]?.traps.orEmpty()

    /** Lays a trap on [at] in [npc]'s fight, for tests. */
    internal fun layTrap(npc: Npc, at: Tile) {
        fights.getOrPut(npc) { Fight() }.traps += at
    }

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(boss) { Fight() }
        springTraps(fight)
        val roarAt = when (fight.roars) {
            0 -> boss.maxHitpoints * 2 / 3
            1 -> boss.maxHitpoints / 3
            else -> -1
        }
        val roared = boss.hitpoints in 1..roarAt
        if (roared) roar(fight)
        if (fight.roars > 0 && world.tick >= fight.trapsEnd) layTraps(fight)

        // Let go of a target who left the lair, so it is free for whoever comes in next (Codex review of W4.8).
        val target = heldTarget()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val reaches = beside(target)
        val sees = world.meleeCombat.sees(boss, target)
        if (reaches) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        // Its own attack timer, so a swing the generic fight made before this one began holds its next one.
        // No attack on a roar's tick, so the roar's animation is the one seen (an NPC shows one a tick).
        if (roared || !sees || world.tick < boss.nextAttackTick) return
        boss.nextAttackTick = world.tick + config.speed
        when {
            roll(1, KNOCKBACK_ODDS) == 1 -> knockback(fight, target)
            reaches -> swipe(target)
            else -> crescents()
        }
        if (fight.roars >= 2) layTraps(fight)
    }

    /**
     * Whether [player] is in the swipe's reach: beside the bear, diagonals too (the Wiki's Artio/Strategies: the swipe
     * "can be used diagonally across a target").
     */
    private fun EncounterContext.beside(player: Player): Boolean {
        if (player.level != boss.level) return false
        val size = boss.type.size
        val inBox = player.x in boss.x - 1..boss.x + size && player.z in boss.z - 1..boss.z + size
        val under = player.x in boss.x until boss.x + size && player.z in boss.z until boss.z + size
        return inBox && !under && (world.meleeCombat.reaches(boss, player) || world.meleeCombat.sees(boss, player))
    }

    /**
     * The swipe at [target], splashing onto everyone within a tile of them who is also beside the bear; each rolls their
     * own hit, halved under Protect from Melee.
     */
    private fun EncounterContext.swipe(target: Player) {
        boss.effects.anim(ANIM_MELEE)
        val struck = players.filter {
            !it.dead && abs(it.x - target.x) <= 1 && abs(it.z - target.z) <= 1 && (it === target || beside(it))
        }
        for (player in struck) {
            val rolled = roll(config.meleeMin, config.meleeMax)
            val damage = if (world.prayers.protects(player, AttackType.CRUSH)) rolled / 2 else rolled
            log("swipes at ${player.username} for $damage")
            after(1) { if (!player.dead && players.any { it === player }) hit(player, damage) }
        }
    }

    private fun EncounterContext.crescents() {
        val shot = NpcRangedAttack(anim = ANIM_RANGED, cast = SPOT_RANGED_CAST, projectile = SPOT_RANGED, impact = SPOT_RANGED_IMPACT, maxHit = config.rangedMax)
        for (player in players.toList()) {
            if (player.dead || player.level != boss.level) continue
            world.meleeCombat.fireAt(boss, player, world.tick, shot, AttackType.RANGED)
        }
        log("throws crescents at ${players.joinToString(" ") { it.username }}")
    }

    /** The white orb at [target]: everyone within a tile of them as it lands, unprotected, thrown 3 tiles away from her. */
    private fun EncounterContext.knockback(fight: Fight, target: Player) {
        boss.effects.anim(ANIM_MAGIC)
        val distance = max(1, LineOfSight.distance(boss.x, boss.z, boss.type.size, target.x, target.z, 1, 1))
        val ticks = 1 + (1 + distance) / 3
        log("knockback at ${target.username}")
        after(ticks) {
            if (target.dead || players.none { it === target }) return@after
            val centre = Tile(target.x, target.z)
            for (player in players.toList()) {
                if (player.dead || player.level != boss.level) continue
                if (abs(player.x - centre.x) > 1 || abs(player.z - centre.z) > 1) continue
                player.spotanim(SPOT_RANGED_IMPACT)
                if (world.prayers.protects(player, AttackType.MAGIC)) {
                    // Blocked, but still an attack on them: no logging out of it.
                    world.meleeCombat.attackedWithoutHit(player, boss, world.tick)
                    log("${player.username} blocks the orb")
                    continue
                }
                throwBack(fight, player)
            }
        }
    }

    private fun EncounterContext.throwBack(fight: Fight, player: Player) {
        val centreX = boss.x + boss.type.size / 2
        val centreZ = boss.z + boss.type.size / 2
        var dx = (player.x - centreX).sign
        var dz = (player.z - centreZ).sign
        if (dx == 0 && dz == 0) dz = 1
        if (abs(player.x - centreX) > 2 * abs(player.z - centreZ)) dz = 0
        if (abs(player.z - centreZ) > 2 * abs(player.x - centreX)) dx = 0
        var x = player.x
        var z = player.z
        var moved = 0
        var trapped = false
        while (moved < KNOCKBACK_TILES && world.canStep(player.level, x, z, dx, dz)) {
            x += dx
            z += dz
            moved++
            if (Tile(x, z) in fight.traps) {
                trapped = true
                break
            }
        }
        if (moved > 0) {
            world.teleport(player, x, z, player.level)
            player.waypoints.clear()
        }
        val damage = if (trapped) KNOCKBACK_BASE else KNOCKBACK_BASE + KNOCKBACK_PER_TILE * (KNOCKBACK_TILES - moved)
        hit(player, damage)
        log("threw ${player.username} $moved to $x,$z for $damage")
        // Thrown onto a trap: sprung now, before this tick's movement could walk them off it (Codex review of W4.8).
        if (trapped && fight.traps.remove(Tile(x, z))) spring(player)
    }

    private fun EncounterContext.roar(fight: Fight) {
        fight.roars++
        boss.effects.anim(ANIM_ROAR)
        boss.effects.spotanim(0, SPOT_ROAR, 0)
        boss.freezeTicks = 0
        log("roars (${fight.roars})")
        layTraps(fight)
    }

    /** A fresh set of traps on open tiles of the den, none under a player. */
    private fun EncounterContext.layTraps(fight: Fight) {
        fight.traps.clear()
        val taken = players.map { Tile(it.x, it.z) }.toSet()
        val span = 12
        var tries = 0
        while (fight.traps.size < TRAPS && tries++ < TRAPS * 20) {
            val at = Tile(boss.x + boss.type.size / 2 + roll(-span, span), boss.z + boss.type.size / 2 + roll(-span, span))
            if (at in taken || !config.inArena(at.x, at.z) || !open(at)) continue
            if (at.x in boss.x until boss.x + boss.type.size && at.z in boss.z until boss.z + boss.type.size) continue
            fight.traps += at
        }
        fight.trapsEnd = world.tick + TRAP_TICKS
        for (at in fight.traps) world.zones.mapAnim(at.x, at.z, boss.level, SPOT_TRAP)
        log("traps on ${fight.traps.joinToString(" ") { "${it.x},${it.z}" }}")
    }

    /** Whether a player could stand on [at]: some way onto it is open. */
    private fun EncounterContext.open(at: Tile): Boolean =
        world.canStep(boss.level, at.x - 1, at.z, 1, 0) || world.canStep(boss.level, at.x + 1, at.z, -1, 0) ||
            world.canStep(boss.level, at.x, at.z - 1, 0, 1) || world.canStep(boss.level, at.x, at.z + 1, 0, -1)

    /** Whoever stands on a trap springs it: hurt, bound, and the trap gone. */
    private fun EncounterContext.springTraps(fight: Fight) {
        if (fight.traps.isEmpty()) return
        for (player in players.toList()) {
            if (player.dead || player.level != boss.level) continue
            val at = Tile(player.x, player.z)
            if (fight.traps.remove(at)) spring(player)
        }
    }

    /** [player] springs the trap they stand on (gone from the lair already): hurt and bound. */
    private fun EncounterContext.spring(player: Player) {
        val damage = roll(0, TRAP_MAX)
        hit(player, damage)
        if (player.freezeTicks <= 0 && player.freezeImmunityTicks <= 0) {
            player.freezeTicks = BIND_TICKS
            player.frozenBy = null
            player.waypoints.clear()
        }
        log("${player.username} springs a trap for $damage")
    }

    fun register(world: World, boss: WildernessBoss) {
        // Not in a cache without it (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(boss)
        world.npcScripts.onTick(listOf(config.boss)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, config.inArena)
        }
    }

    companion object {
        const val KNOCKBACK_ODDS = 5
        const val KNOCKBACK_TILES = 3
        const val KNOCKBACK_BASE = 5
        const val KNOCKBACK_PER_TILE = 15

        const val TRAPS = 12
        const val TRAP_MAX = 15
        const val TRAP_TICKS = 10
        const val BIND_TICKS = 5

        const val ANIM_MELEE = 10012
        const val ANIM_RANGED = 10013
        const val ANIM_MAGIC = 10014
        const val ANIM_ROAR = 10015
        const val SPOT_TRAP = 2343
        const val SPOT_RANGED_CAST = 2349
        const val SPOT_RANGED = 2350
        const val SPOT_RANGED_IMPACT = 2351
        const val SPOT_ROAR = 2352
    }
}
