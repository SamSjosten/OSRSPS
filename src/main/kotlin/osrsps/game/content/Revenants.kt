package osrsps.game.content

import java.util.WeakHashMap
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.Combat
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.npc.Npc
import osrsps.map.LineOfSight

/**
 * The revenants of the Revenant Caves (Wilderness step W5.2; the Wiki's Revenants, Revenants/Strategies, Revenant
 * Caves and each revenant's page, read 2026-11-15): eleven kinds, `wild_cave_imp` 7881 and `wild_cave_goblin` 7931 to
 * `wild_cave_dragon` 7940, 30 in all (npc-spawns/revenant-caves.json), aggressive, with the infoboxes' speeds, max
 * hits and bonuses in npc-combat.json. Each fights with all three styles:
 *
 * - "By default, all revenants attack with Magic, but can quickly adapt based on the player's defensive bonuses and
 *   prayers": each attack is magic unless the player prays against magic or is weaker against another style; ranged,
 *   or its own melee style when beside them ([style]).
 * - "A revenant's magic attack, which resembles a weaker form of Ice Barrage, can momentarily freeze the player".
 * - "They recover 20 hitpoints each time they heal ... a cooldown that begins once they have healed, preventing them
 *   from healing again for 15 ticks ... guaranteed a heal whenever they attack while they're below half HP ... a total
 *   of 8-25 times" (Mod Ash, 27 May 2025): [heal], capped at their hitpoints.
 *
 * GUESSED: how they weigh the player's defences (the style the player's worn defence bonus is lowest against, magic on
 * a tie; one prayed against never chosen unless all are); the freeze's [FREEZE_TICKS], on a magic hit; the 7-tile
 * range; the ranged attack's look (no projectile; the cache names none for them); the attack animations (each kind's
 * own creature's); the heal's cap at full health. Not built: their "very high accuracy" against a player without a
 * charged bracelet of ethereum (they roll as other monsters do) and the bracelet; the magic attack's hits on up to
 * nine others on the target's tile; the heal's lightning; their drops (step W5.3).
 */
object Revenants {
    /** A kind of revenant: its type and its melee style. */
    class Kind(val id: Int, val internalName: String, val melee: AttackType)

    val KINDS = listOf(
        Kind(7881, "wild_cave_imp", AttackType.STAB),
        Kind(7931, "wild_cave_goblin", AttackType.CRUSH),
        Kind(7932, "wild_cave_pyrefiend", AttackType.CRUSH),
        Kind(7933, "wild_cave_hobgoblin", AttackType.SLASH),
        Kind(7934, "wild_cave_cyclops", AttackType.CRUSH),
        Kind(7935, "wild_cave_hellhound", AttackType.STAB),
        Kind(7936, "wild_cave_demon", AttackType.CRUSH),
        Kind(7937, "wild_cave_ork", AttackType.SLASH),
        Kind(7938, "wild_cave_darkbeast", AttackType.CRUSH),
        Kind(7939, "wild_cave_knight", AttackType.SLASH),
        Kind(7940, "wild_cave_dragon", AttackType.SLASH),
    )
    val IDS: Set<Int> = KINDS.map { it.id }.toSet()

    const val HEAL = 20
    const val HEAL_COOLDOWN = 15
    const val HEALS_MIN = 8
    const val HEALS_MAX = 25
    const val FREEZE_TICKS = 5
    const val RANGE = 7

    /** How far it fires when it can't come closer: the longest reach of a player's own attacks. */
    const val STALLED_RANGE = 10

    /** Where each following revenant stood last tick, to see it held in place. */
    private val lastSpot = WeakHashMap<Npc, Long>()

    const val SPOT_MAGIC = 1415
    const val SPOT_MAGIC_IMPACT = 1454

    val MAGIC = NpcRangedAttack(projectile = SPOT_MAGIC, impact = SPOT_MAGIC_IMPACT)
    val RANGED = NpcRangedAttack()

    /** A revenant's heals: how many it has left, and the tick its next may come. */
    class Heals(var left: Int, var readyTick: Long = 0)

    private val heals = WeakHashMap<Npc, Heals>()

    /** [npc]'s heals, rolled the first time it is asked about (8 to 25). */
    /** The player each revenant was fighting as of its last tick, so a new fight's first attack waits a tick. */
    private val lastTarget = WeakHashMap<Npc, Player>()

    fun heals(world: World, npc: Npc): Heals = heals.getOrPut(npc) { Heals(world.random.between(HEALS_MIN, HEALS_MAX)) }

    /**
     * The style [npc] attacks [player] with, of magic, ranged and, when [beside], its [melee] style: of those [prayed]
     * doesn't block, the one the player's [defence] bonus is lowest against, magic on a tie.
     */
    fun style(melee: AttackType, beside: Boolean, prayed: (AttackType) -> Boolean, defence: (AttackType) -> Int): AttackType {
        val styles = listOfNotNull(AttackType.MAGIC, AttackType.RANGED, melee.takeIf { beside })
        val open = styles.filter { !prayed(it) }.ifEmpty { styles }
        return open.minBy { defence(it) }
    }

    /** One tick of [npc]'s fight, while it has a target: walking into range, and its heal and attack when due. */
    fun tick(world: World, kind: Kind, npc: Npc) {
        npc.scriptedAttacks = true
        val player = npc.target ?: run {
            lastTarget.remove(npc)
            return
        }
        // A fight it has just taken up strikes from the next tick, as an aggressive monster's does (Codex review on #1013).
        if (lastTarget.put(npc, player) !== player) npc.nextAttackTick = maxOf(npc.nextAttackTick, world.tick + 1)
        if (npc.dead || npc.hidden || player.dead || player.level != npc.level) return
        val beside = world.meleeCombat.reaches(npc, player)
        val distance = LineOfSight.distance(npc.x, npc.z, npc.type.size, player.x, player.z)
        val sees = distance <= STALLED_RANGE && world.meleeCombat.sees(npc, player)
        // Held at its leash or by a rock while following, it fires anyway out to a player's own longest reach, so there
        // is no spot just past its range to shoot it from (exploit review R65B-2/3). GUESSED: no source covers it.
        val spot = (npc.x.toLong() shl 32) or npc.z.toLong()
        val stalled = npc.following === player && lastSpot.put(npc, spot) == spot
        val inShot = sees && (distance in 1..RANGE || (stalled && distance in 1..STALLED_RANGE))
        if (!inShot && !beside) {
            if (npc.following !== player) world.npcs.follow(npc, player)
            return
        }
        lastSpot.remove(npc)
        if (npc.following === player) world.npcs.stopFollowing(npc)
        if (world.tick < npc.nextAttackTick) return
        heal(world, npc)
        val bonuses = world.combat.bonuses(player)
        val type = style(kind.melee, beside, { world.prayers.protects(player, it) }) { Combat.defenceBonus(bonuses, it) }
        when (type) {
            AttackType.MAGIC -> world.meleeCombat.fireAt(npc, player, world.tick, MAGIC, AttackType.MAGIC) { freeze(player) }
            AttackType.RANGED -> world.meleeCombat.fireAt(npc, player, world.tick, RANGED, AttackType.RANGED)
            else -> world.meleeCombat.swingAt(npc, player, world.tick)
        }
    }

    /** Below half its hitpoints with a heal ready and some left, [npc] heals [HEAL] as it attacks. */
    fun heal(world: World, npc: Npc) {
        if (npc.hitpoints * 2 >= npc.maxHitpoints) return
        val left = heals(world, npc)
        if (left.left <= 0 || world.tick < left.readyTick) return
        npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + HEAL)
        // The client's health bar shows the heal now, not at its next hit (Codex review on #1013).
        npc.effects.headBar(-1, osrsps.game.combat.MeleeCombat.HEALTH_BAR, osrsps.game.combat.MeleeCombat.healthBarFill(npc.hitpoints, npc.maxHitpoints))
        left.left--
        left.readyTick = world.tick + HEAL_COOLDOWN
    }

    /** A magic hit freezes [player] for [FREEZE_TICKS], unless frozen or immune already. */
    private fun freeze(player: Player) {
        if (player.dead || player.freezeTicks > 0 || player.freezeImmunityTicks > 0) return
        player.freezeTicks = FREEZE_TICKS
        player.frozenBy = null
        player.waypoints.clear()
    }
}

fun registerRevenants(world: World) {
    for (kind in Revenants.KINDS) {
        world.npcScripts.onTick(listOf(kind.id)) { npc -> Revenants.tick(world, kind, npc) }
    }
}
