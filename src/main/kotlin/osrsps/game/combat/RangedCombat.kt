package osrsps.game.combat

import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.RouteTarget
import osrsps.map.Tile

/** A player shooting [npc] with a bow or crossbow: they walk until it is in range and in sight, then fire. See [RangedCombat]. */
class RangedInteraction internal constructor(val npc: Npc, private val owner: RangedCombat) : Interaction {
    /** Where the NPC stood when the player's route to it was last made; null once they have stood in range. */
    internal var routedTo: Tile? = null

    /** Whether the player could shoot the NPC from where they stood when last tried. */
    internal var inRange = false

    /** Whether the player has fired at the NPC yet; until then the fight can still be refused on arrival. */
    internal var fired = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun unreachable(player: Player) = owner.unreachable(player)

    override fun holding(player: Player) = inRange
}

/**
 * Bows and crossbows against NPCs ([RangedWeapons] lists them). An Attack option with a ranged style becomes a
 * [RangedInteraction] (see [MeleeCombat.fight]): the player walks towards the NPC until it is within the weapon's range
 * and in line of sight ([LineOfSight.inRange]), stops there and fires every weapon-speed ticks. Single-way combat works
 * as for melee ([MeleeCombat.foughtByAnother], [MeleeCombat.underAttackByAnother]), and an NPC that is shot fights back
 * as one that is hit ([MeleeCombat.hit]).
 *
 * From the OSRS Wiki:
 * - Range is the weapon's (Attack range), 2 more on longrange up to 10; rapid fires a tick sooner (Ranged).
 * - Accuracy and damage are the ranged formulas ([CombatMath.playerRangedAttackRoll], [CombatMath.playerRangedMaxHit])
 *   against the NPC's defence for the style's weight ([Combat.rangedMatchup]).
 * - XP is given as the shot is fired, 4 a point of damage to Ranged, or 2 to Ranged and 2 to Defence on longrange, and
 *   1.33 to Hitpoints (Combat).
 * - The hit lands [HitDelay.BOW] ticks after firing, a tick later on an NPC ([HitDelay.ON_NPC]).
 * - Each shot uses one piece of ammunition; without an Ava's device a fifth of it breaks and the rest falls to the
 *   floor (Ava's accumulator: 72% back, 8% on the floor, 20% broken). The dark bow fires two at once.
 * - "There is no ammo left in your quiver." with nothing in the ammo slot (Ammunition).
 *
 * Guessed and marked below: that the ammunition lands under the NPC as the hit does, private to the shooter as a drop
 * is, the message for ammunition the weapon can't fire, the projectile's heights, start and arc, which crossbow
 * animation is played, and that a missing or wrong ammunition ends the fight when the player would fire rather than on
 * the click.
 */
class RangedCombat(
    private val npcs: Npcs,
    private val collision: CollisionMap,
    private val movement: Movement,
    private val combat: Combat,
    private val objTypes: ObjTypes,
    private val weapons: RangedWeapons,
    private val melee: MeleeCombat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** Fires a projectile for everyone near it. */
    private val projectile: (Projectile) -> Unit = {},
    /** Puts one piece of fired ammunition on the floor for its shooter. */
    private val dropAmmo: (type: ObjType, x: Int, z: Int, level: Int, owner: Player) -> Unit = { _, _, _, _, _ -> },
    /** What the player is told and shown; tests swap these for recorders. */
    private val message: (Player, String) -> Unit = Player::message,
    private val face: (Player, Npc) -> Unit = { _, _ -> },
    private val stopFacing: (Player) -> Unit = {},
) {
    private class Shot(val player: Player, val npc: Npc, val damage: Int, val landTick: Long, val ammo: ObjType)

    private val inFlight = ArrayList<Shot>()

    /** A ranged fight with [npc], or null when the player's style isn't ranged or their weapon isn't one this fires. */
    fun fight(player: Player, npc: Npc): RangedInteraction? {
        if (combat.style(player).type != AttackType.RANGED) return null
        weapon(player) ?: return null
        return RangedInteraction(npc, this)
    }

    internal fun unreachable(player: Player) = stopFacing(player)

    internal fun tryInteract(player: Player, interaction: RangedInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level || npc.dead) {
            stopFacing(player)
            return true
        }
        val weapon = weapon(player)
        val style = combat.style(player)
        if (weapon == null || style.type != AttackType.RANGED) {
            // The weapon or style changed on the way: carry on with whatever fights with the new one.
            val next = melee.fight(player, npc)
            if (next == null) {
                message(player, MeleeCombat.MELEE_ONLY)
                stopFacing(player)
            }
            player.interaction = next
            return true
        }
        val size = npc.type.size
        if (!LineOfSight.inRange(collision, player.level, player.x, player.z, 1, npc.x, npc.z, size, size, range(weapon, style))) {
            interaction.inRange = false
            val at = Tile(npc.x, npc.z)
            if (interaction.routedTo != at) {
                interaction.routedTo = at
                movement.request(player, RouteTarget.entity(npc.x, npc.z, size), player.invertRun)
            }
            return false
        }
        interaction.inRange = true
        interaction.routedTo = null
        player.waypoints.clear()
        if (melee.foughtByAnother(player, npc)) {
            message(player, MeleeCombat.SOMEONE_ELSE)
            stopFacing(player)
            return true
        }
        if (!interaction.fired && melee.underAttackByAnother(player, npc)) {
            message(player, MeleeCombat.ALREADY_UNDER_ATTACK)
            stopFacing(player)
            return true
        }
        val tick = currentTick()
        if (tick >= player.nextAttackTick) {
            if (!fire(player, npc, weapon, tick)) {
                stopFacing(player)
                return true
            }
            interaction.fired = true
        }
        return false
    }

    /** Fires at [npc] on [tick]; false when there is nothing to fire, and the player has been told. */
    private fun fire(player: Player, npc: Npc, weapon: RangedWeapon, tick: Long): Boolean {
        val worn = player.equipment[WearPos.AMMO]
        if (worn == null) {
            message(player, NO_AMMO)
            return false
        }
        val ammoType = objTypes[worn.id]
        val ammo = weapons.ammo(ammoType)
        if (ammoType == null || ammo == null || !weapon.fires(ammo)) {
            message(player, if (weapon.kind == AmmoKind.BOLTS) WRONG_BOLTS else WRONG_ARROWS)
            return false
        }
        val matchup = combat.rangedMatchup(player, npc) ?: return false
        val style = matchup.style
        player.nextAttackTick = tick + speed(player, style)
        // The dark bow fires one arrow when only one is left (OSRS Wiki, Dark bow).
        val shots = minOf(weapon.shots, worn.count)
        player.equipment.remove(WearPos.AMMO, shots)
        if (weapon.anim >= 0) player.effects.anim(weapon.anim, 0)
        val launch = if (shots > 1 && ammo.doubleLaunch >= 0) ammo.doubleLaunch else ammo.launch
        if (launch >= 0) player.effects.spotanim(0, launch, LAUNCH_HEIGHT, 0)
        face(player, npc)
        val size = npc.type.size
        val distance = LineOfSight.distance(player.x, player.z, 1, npc.x, npc.z, size, size)
        val landTick = tick + HitDelay.BOW.ticks(distance) + HitDelay.ON_NPC
        repeat(shots) { shot ->
            if (ammo.travel >= 0) projectile(flight(player, npc, ammo.travel, landTick - tick, shot))
            // Accuracy first, then damage, as for melee; a shot's random draws are always these two.
            val rolled = if (CombatMath.rollHits(random, matchup.attackRoll, matchup.defenceRoll)) CombatMath.rollDamage(random, matchup.maxHit) else 0
            // XP for what the shot can still deal: what the NPC has left after the shots already on their way.
            val left = (npc.hitpoints - inFlight.filter { it.npc === npc }.sumOf { it.damage }).coerceAtLeast(0)
            val damage = minOf(rolled, left)
            for (skill in style.xp) player.skills.addTenths(skill, CombatMath.styleXpTenths(damage, style.xp.size))
            player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
            inFlight += Shot(player, npc, damage, landTick, ammoType)
        }
        return true
    }

    /**
     * The NPC phase's part: shots whose time has come land, each its hitsplat (see [MeleeCombat.hit]) and its
     * ammunition on the floor four times in five. A shot lands whatever happened to its shooter since; one whose NPC
     * died or went in the meantime does nothing but drop its ammunition.
     */
    fun process() {
        val tick = currentTick()
        val iterator = inFlight.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            if (tick < shot.landTick) continue
            iterator.remove()
            val npc = shot.npc
            if (npcs[npc.index] === npc && !npc.dead) melee.hit(npc, shot.player, shot.damage, tick)
            // Guessed: under the NPC's south-west tile, where its drops go too.
            if (!random.chance(AMMO_BROKEN_NUMERATOR, AMMO_BROKEN_DENOMINATOR)) dropAmmo(shot.ammo, npc.x, npc.z, npc.level, shot.player)
        }
    }

    /** Shots fired and not landed yet, for tests and commands. */
    val shotsInFlight: Int get() = inFlight.size

    private fun weapon(player: Player): RangedWeapon? {
        val worn = player.equipment[WearPos.WEAPON] ?: return null
        return weapons.weapon(objTypes[worn.id])
    }

    /** The weapon's range, 2 more on longrange up to [RangedWeapons.MAX_RANGE] (OSRS Wiki, Attack range). */
    private fun range(weapon: RangedWeapon, style: AttackStyle): Int =
        if (style.stance == AttackStance.LONGRANGE) minOf(weapon.range + LONGRANGE_EXTRA, RangedWeapons.MAX_RANGE) else weapon.range

    /** Ticks between shots: the weapon's speed, one fewer on rapid (OSRS Wiki, Ranged). */
    private fun speed(player: Player, style: AttackStyle): Int {
        val base = combat.attackSpeed(player)
        return if (style.stance == AttackStance.RAPID) base - 1 else base
    }

    /**
     * The projectile a shot flies as, landing as its hit does: [ticks] after it was fired, in client cycles. Guessed
     * heights, start, arc and offset, the same as `::proj`'s; a second arrow from the dark bow leaves a little later.
     */
    private fun flight(player: Player, npc: Npc, id: Int, ticks: Long, shot: Int): Projectile {
        val start = PROJECTILE_START + shot * SECOND_SHOT_DELAY
        return Projectile(
            id = id,
            fromX = player.x,
            fromZ = player.z,
            level = player.level,
            toX = npc.x,
            toZ = npc.z,
            startHeight = PROJECTILE_START_HEIGHT,
            endHeight = PROJECTILE_END_HEIGHT,
            startTime = start,
            endTime = maxOf(start + 1, (ticks * CYCLES_PER_TICK).toInt()),
            angle = PROJECTILE_ANGLE,
            progress = PROJECTILE_PROGRESS,
            sourceIndex = -(player.index + 1),
            targetIndex = npc.index + 1,
        )
    }

    companion object {
        /** The live message for an empty ammo slot (OSRS Wiki, Ammunition). */
        const val NO_AMMO = "There is no ammo left in your quiver."

        /** Guessed wording: live's message for ammunition the weapon can't fire isn't on the Wiki. */
        const val WRONG_ARROWS = "You can't use that ammo with your bow."
        const val WRONG_BOLTS = "You can't use that ammo with your crossbow."

        const val LONGRANGE_EXTRA = 2

        /** One in five shots breaks its ammunition (OSRS Wiki, Ava's accumulator: 20% broken without one). */
        const val AMMO_BROKEN_NUMERATOR = 1
        const val AMMO_BROKEN_DENOMINATOR = 5

        /** Guessed: the launch graphic's height, about the hands. */
        const val LAUNCH_HEIGHT = 96

        private const val CYCLES_PER_TICK = 30
        private const val PROJECTILE_START = 30
        private const val SECOND_SHOT_DELAY = 10
        private const val PROJECTILE_START_HEIGHT = 172
        private const val PROJECTILE_END_HEIGHT = 124
        private const val PROJECTILE_ANGLE = 16
        private const val PROJECTILE_PROGRESS = 64
    }
}
