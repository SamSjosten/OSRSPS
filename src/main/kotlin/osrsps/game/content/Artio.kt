package osrsps.game.content

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.map.LineOfSight
import osrsps.map.Tile

/** The lesser Wilderness bosses' lairs' words (keys only; the words are in data/dialogue.txt). */
object WildernessLairLines : Lines("wildy_lair", "Wilderness boss lairs") {
    val PEEK = line("peek", "Peek on a Wilderness boss lair's way in: how many players are inside (no source yet)", "count")
    val PEEK_KILLS = line("peek_kills", "Peek with fewer than 20 kills of the lair's boss (the Wiki's Hunter's End: 20 kills to peek; no words yet)")
    val FEE_PAID = line("fee_paid", "Check-Fee on a lair's way in with the fee paid (no source yet)")
    val FEE_DUE = line("fee_due", "Check-Fee on a lair's way in with the fee still to pay (no source yet)", "fee")
}

/**
 * Artio and her lair, Hunter's End (Wilderness step W4.8; the Wiki's Artio, its Strategies page and Hunter's End, read
 * 2026-11-15). `callisto_singles` 11992, size 3, combat 320, 450 hitpoints, aggressive, every 5 ticks.
 *
 * - **The way in** (`wild_callisto_singles_entrance01` 47141, 3 by 3 at 3115,3676 at level 20): Enter takes the lairs'
 *   fee ([WildernessLairFee]) and puts the player in the north of the den; Peek says how many players are inside, once
 *   the player has 20 kills of her; Check-Fee says whether the fee is paid or what it is. The way out
 *   (`wild_callisto_exit01` 47122 at 1758,11531, the den's south side) puts them outside the way in. The den is map
 *   square 27_180, at level 21. Live asks for the hard Wilderness Diary or a Callisto task; ours is open (Sam, 9 Nov).
 * - **Her swipe** beside her target: 15 to 35 ("Artio's melee attack has a minimum hit of 15"), about half through
 *   Protect from Melee.
 * - **Her crescents**, when she can't reach: one at everyone in the den, ranged, up to 17.
 * - **Her knockback**: a white orb at her target that hits everyone within a tile of them unless they pray Protect from
 *   Magic, throwing each 3 tiles away from her: 5 damage when all 3 tiles are open, 15 more for each tile a wall cuts
 *   short (so 50 against one), and 5 when a trap stops them.
 * - **Her traps**: at 66% and 33% of her hitpoints she roars, breaking any freeze, and traps cover the den; a trap
 *   stepped on hurts up to 15 and binds; from 66% the traps are always down, a fresh set as the last runs out, and from
 *   33% a fresh set with every attack.
 *
 * Her drops are in npc-drops.json and her kill count in `total_artio_kills`; she is a lair boss, so her top damage
 * dealer gets 10,000 off the fee.
 *
 * GUESSED until recorded: her swipe always landing (15 to 35, halved under Protect from Melee), on her target alone
 * (the Wiki's diagonal splash isn't built); the knockback one attack in [KNOCKBACK_ODDS] and its flight; [TRAPS] traps,
 * lasting [TRAP_TICKS] ticks, each binding for [BIND_TICKS] ticks and gone once sprung, and never under a player when
 * laid; her animations (the cache's npc_callisto ones by name), the crescent's graphics (fx_callisto_ranged), the orb's
 * (none in flight; the crescent's impact on landing), the traps' (fx_callisto_trap) and her roar's (fx_callisto_enrage);
 * where Enter and Exit put the player and where she spawns. Left out: singles-plus combat (the den is single-way, so one
 * player fights her at a time), her first freeze always landing, the 3-tick teleport delay on entering, the lair's
 * Slayer and diary checks.
 */
object Artio {
    const val BOSS = 11992
    const val KILL_COUNT_VARP = 3761

    const val ENTRANCE = 47141
    const val EXIT = 47122
    const val ENTER = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20

    /** Where Enter puts the player (the den's north), where Exit does (outside the way in), and where she spawns. */
    val ARRIVAL = Tile(1759, 11549)
    val OUTSIDE = Tile(3116, 3675)
    val SPAWN = Tile(1757, 11535)

    const val ATTACK_SPEED = 5
    const val MELEE_MIN = 15
    const val MELEE_MAX = 35
    const val RANGED_MAX = 17
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

    /** The den: its map square. */
    fun inLair(x: Int, z: Int): Boolean = x shr 6 == 27 && z shr 6 == 180

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "artio", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }))),
            scriptedAttacks = true,
        )
    }

    /** One fight's state, by boss. */
    private class Fight {
        var nextAttack = Long.MIN_VALUE
        /** Roars done: 0, 1 at 66%, 2 at 33%. */
        var roars = 0
        val traps = HashSet<Tile>()
        var trapsEnd = -1L
    }

    private val fights = java.util.WeakHashMap<Npc, Fight>()

    /** The tiles her traps cover now, for tests. */
    fun traps(npc: Npc): Set<Tile> = fights[npc]?.traps.orEmpty()

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(boss) { Fight() }
        springTraps(fight)
        val roarAt = when (fight.roars) {
            0 -> boss.maxHitpoints * 2 / 3
            1 -> boss.maxHitpoints / 3
            else -> -1
        }
        if (boss.hitpoints in 1..roarAt) roar(fight)
        if (fight.roars > 0 && world.tick >= fight.trapsEnd) layTraps(fight)

        val target = boss.target?.takeIf { held -> players.any { it === held } }
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val reaches = world.meleeCombat.reaches(boss, target)
        val sees = world.meleeCombat.sees(boss, target)
        if (reaches) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        if (!sees || world.tick < fight.nextAttack) return
        fight.nextAttack = world.tick + ATTACK_SPEED
        when {
            roll(1, KNOCKBACK_ODDS) == 1 -> knockback(fight, target)
            reaches -> swipe(target)
            else -> crescents()
        }
        if (fight.roars >= 2) layTraps(fight)
    }

    private fun EncounterContext.swipe(target: Player) {
        boss.effects.anim(ANIM_MELEE)
        val rolled = roll(MELEE_MIN, MELEE_MAX)
        val damage = if (world.prayers.protects(target, AttackType.CRUSH)) rolled / 2 else rolled
        log("swipes at ${target.username} for $damage")
        after(1) { if (!target.dead && players.any { it === target }) hit(target, damage) }
    }

    private fun EncounterContext.crescents() {
        val shot = NpcRangedAttack(anim = ANIM_RANGED, cast = SPOT_RANGED_CAST, projectile = SPOT_RANGED, impact = SPOT_RANGED_IMPACT, maxHit = RANGED_MAX)
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
                if (world.prayers.protects(player, AttackType.MAGIC)) continue
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
            if (at in taken || !inLair(at.x, at.z) || !open(at)) continue
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
            if (!fight.traps.remove(at)) continue
            val damage = roll(0, TRAP_MAX)
            hit(player, damage)
            if (player.freezeTicks <= 0 && player.freezeImmunityTicks <= 0) {
                player.freezeTicks = BIND_TICKS
                player.frozenBy = null
                player.waypoints.clear()
            }
            log("${player.username} springs a trap for $damage")
        }
    }
}

fun registerArtio(world: World) {
    with(Artio) {
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, 0)
        }
        world.handlers.onLoc(ENTRANCE, PEEK) {
            arriveDelay()
            if (KILL_COUNT_VARP < player.vars.types.varpCount && player.vars[KILL_COUNT_VARP] < PEEK_KILLS) {
                return@onLoc world.message(player, WildernessLairLines.PEEK_KILLS)
            }
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inLair(it.x, it.z) && it.level == 0 })
        }
        world.handlers.onLoc(ENTRANCE, CHECK_FEE) {
            arriveDelay()
            if (WildernessLairFee.paid(player)) world.message(player, WildernessLairLines.FEE_PAID)
            else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
        }
        // Callisto's den has the same way out; his step gives it its own place to put the player.
        world.handlers.onLoc(EXIT, 1) { op ->
            val loc = op.loc!!
            if (!inLair(loc.x, loc.z)) return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        }
        // Not in a cache without her (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss("artio", setOf(BOSS), KILL_COUNT_VARP, lair = true))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, { x, z -> inLair(x, z) })
        }
    }
}
