package osrsps.game.content

import kotlin.math.abs
import kotlin.math.sign
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.map.LineOfSight

/**
 * The Chaos Elemental (Wilderness step W4.5; the Wiki's Chaos Elemental and its Strategies page, read 2026-11-15):
 * `chaoselemental` 2054, size 3, west of Rogues' Castle at level 50 and up, multi-way, combat 305, 250 hitpoints,
 * aggressive, every 4 ticks. Three attacks, each a projectile:
 * - Discord: magic, ranged or melee damage up to 28 (magic most often), which the matching protection prayer stops;
 * - Confusion: the player thrown a short way, away from it;
 * - Madness: up to four worn items taken off into the backpack, the weapon first; with no room in the backpack it
 *   never uses Madness.
 * Its drops (a minor drop every kill beside the major roll, the dragon pickaxe as a pre-roll) are in npc-drops.json and
 * its kill count in `total_chaosele_kills`.
 *
 * Its fight runs on the encounter framework from the tick it is in the world, against whoever it has taken on within
 * [AREA_RADIUS] tiles of where it spawned; it walks after them when they are out of its [RANGE].
 *
 * GUESSED until recorded: the attacks' shares ([DISCORD_WEIGHT], [CONFUSION_WEIGHT], [MADNESS_WEIGHT]) and Discord's
 * styles (magic half, ranged and melee a quarter each); Confusion's throw (2 to [THROW] tiles straight away from it,
 * as far as the ground lets); Madness taking the weapon and then up to three other worn items at random, as many as
 * the backpack has room for; when the throw and the unequipping happen (as the projectile lands); the 10-tile range;
 * its fight's area. The spotanims and animations are the cache's chaoselemental ones by name. No pet.
 */
object ChaosElemental {
    const val BOSS = 2054
    const val KILL_COUNT_VARP = 1513

    const val ATTACK_SPEED = 4
    const val MAX_HIT = 28
    const val RANGE = 10

    const val ANIM_ATTACK = 3146
    const val SPOT_MADNESS_CAST = 550
    const val SPOT_MADNESS = 551
    const val SPOT_MADNESS_IMPACT = 552
    const val SPOT_CONFUSION_CAST = 553
    const val SPOT_CONFUSION = 554
    const val SPOT_CONFUSION_IMPACT = 555
    const val SPOT_DISCORD_CAST = 556
    const val SPOT_DISCORD = 557
    const val SPOT_DISCORD_IMPACT = 558

    const val DISCORD_WEIGHT = 4
    const val CONFUSION_WEIGHT = 1
    const val MADNESS_WEIGHT = 1
    const val THROW = 5
    const val MADNESS_ITEMS = 4
    const val AREA_RADIUS = 25

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "chaoselemental", type,
            // Unleashed, so he follows anyone in his area past the spawn leash (exploit review round 39: from the area's
            // edge a leashed boss never reached them).
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = { boss.unleashed = true })),
            scriptedAttacks = true,
        )
    }


    private fun EncounterContext.tick() {
        val target = heldTarget()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val far = LineOfSight.distance(boss.x, boss.z, boss.type.size, target.x, target.z, 1, 1) > RANGE ||
            !world.meleeCombat.sees(boss, target)
        if (far) world.npcs.follow(boss, target) else world.npcs.stopFollowing(boss)
        if (far || world.tick < boss.nextAttackTick) return
        // The NPC's own cooldown, so a strike made before his fight began holds his first one here too.
        boss.nextAttackTick = world.tick + ATTACK_SPEED
        val madness = if (target.inventory.freeSlots > 0) MADNESS_WEIGHT else 0
        val pick = roll(1, DISCORD_WEIGHT + CONFUSION_WEIGHT + madness)
        when {
            pick <= DISCORD_WEIGHT -> discord(target)
            pick <= DISCORD_WEIGHT + CONFUSION_WEIGHT -> confusion(target)
            else -> madness(target)
        }
    }

    private fun EncounterContext.discord(target: Player) {
        val type = when (roll(1, 4)) {
            1, 2 -> AttackType.MAGIC
            3 -> AttackType.RANGED
            else -> AttackType.CRUSH
        }
        val shot = NpcRangedAttack(anim = ANIM_ATTACK, cast = SPOT_DISCORD_CAST, projectile = SPOT_DISCORD, impact = SPOT_DISCORD_IMPACT, maxHit = MAX_HIT)
        world.meleeCombat.fireAt(boss, target, world.tick, shot, type)
        log("discord (${type.name.lowercase()}) at ${target.username}")
    }

    /** A special's projectile at [target], landing after a magic bolt's flight; [land] then, if they are still about. */
    private fun EncounterContext.special(target: Player, cast: Int, travel: Int, impact: Int, land: () -> Unit) {
        boss.effects.anim(ANIM_ATTACK)
        boss.effects.spotanim(0, cast, 92)
        val ticks = specialTicks(boss, target)
        world.zones.projectile(specialProjectile(world, boss, target, travel, ticks))
        after(ticks) {
            if (target.dead || target.level != boss.level || players.none { it === target }) return@after
            target.spotanim(impact)
            world.meleeCombat.attackedWithoutHit(target, boss, world.tick)
            land()
        }
    }

    private fun EncounterContext.confusion(target: Player) {
        log("confusion at ${target.username}")
        special(target, SPOT_CONFUSION_CAST, SPOT_CONFUSION, SPOT_CONFUSION_IMPACT) {
            val centreX = boss.x + boss.type.size / 2
            val centreZ = boss.z + boss.type.size / 2
            var dx = (target.x - centreX).sign
            var dz = (target.z - centreZ).sign
            if (dx == 0 && dz == 0) dx = 1
            if (abs(target.x - centreX) > 2 * abs(target.z - centreZ)) dz = 0
            if (abs(target.z - centreZ) > 2 * abs(target.x - centreX)) dx = 0
            var x = target.x
            var z = target.z
            // Not past the edge of his fight's area: thrown out of it, a top damager would no longer be at the fight
            // for his drop (exploit review round 44, R44C-3). INFERRED: live's throw has no such edge to stop at.
            val fight = world.encounters.all.firstOrNull { it.boss === boss && !it.ended }
            repeat(roll(2, THROW)) {
                if (!world.canStep(target.level, x, z, dx, dz)) return@repeat
                if (fight != null && !fight.covers(x + dx, z + dz)) return@repeat
                x += dx
                z += dz
            }
            if (x != target.x || z != target.z) world.teleport(target, x, z, target.level)
            log("threw ${target.username} to $x,$z")
        }
    }

    private fun EncounterContext.madness(target: Player) {
        log("madness at ${target.username}")
        special(target, SPOT_MADNESS_CAST, SPOT_MADNESS, SPOT_MADNESS_IMPACT) {
            val worn = (0 until WearPos.COUNT).filter { it != WearPos.WEAPON && target.equipment[it] != null }.toMutableList()
            val order = ArrayList<Int>()
            if (target.equipment[WearPos.WEAPON] != null) order += WearPos.WEAPON
            while (order.size < MADNESS_ITEMS && worn.isNotEmpty()) order += worn.removeAt(roll(0, worn.size - 1))
            // An open trade ends first: the backpack holds still while one is open (exploit review round 39).
            if (order.isNotEmpty()) world.trades.decline(target)
            val off = order.filter { world.takeOff(target, it) }
            log("took off ${target.username}'s ${off.joinToString(" ")}")
        }
    }

    /** How many ticks a special takes from [boss] to [target]: a magic bolt's flight. */
    fun specialTicks(boss: Npc, target: Player): Int =
        1 + (1 + LineOfSight.distance(boss.x, boss.z, boss.type.size, target.x, target.z, 1, 1)) / 3

    /** A special's projectile, arriving as it lands [ticks] later: timed as an NPC's shot ([MeleeCombat.flight]). */
    fun specialProjectile(world: World, boss: Npc, target: Player, travel: Int, ticks: Int): Projectile =
        world.meleeCombat.flight(boss, target, travel, ticks)

    /** Whether [x], [z] is within [AREA_RADIUS] of [npc]'s spawn. */
    fun inArea(npc: Npc, x: Int, z: Int): Boolean = abs(x - npc.spawn.x) <= AREA_RADIUS && abs(z - npc.spawn.z) <= AREA_RADIUS
}

fun registerChaosElemental(world: World) {
    with(ChaosElemental) {
        // Not in a cache without it (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss("chaos elemental", setOf(BOSS), KILL_COUNT_VARP))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, { x, z -> inArea(npc, x, z) })
        }
    }
}
