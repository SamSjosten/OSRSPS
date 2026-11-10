package osrsps.game.combat

import kotlin.math.abs
import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Movement
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.RouteTarget
import osrsps.map.Tile

/** A player shooting [npc] with a ranged weapon: they walk until it is in range and in sight, then fire. See [RangedCombat]. */
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
 * Bows, crossbows, the blowpipe and chinchompas against NPCs ([RangedWeapons] lists them). An Attack option with a ranged style becomes a
 * [RangedInteraction] (see [MeleeCombat.fight]): the player walks towards the NPC until it is within the weapon's range
 * and in line of sight ([LineOfSight.inRange]), stops there and fires every weapon-speed ticks. Single-way combat works
 * as for melee ([MeleeCombat.foughtByAnother], [MeleeCombat.underAttackByAnother]), and an NPC that is shot fights back
 * as one that is hit ([MeleeCombat.hit]).
 *
 * From the OSRS Wiki:
 * - Range is the weapon's (Attack range), 2 more on longrange up to 10; rapid fires a tick sooner (Ranged).
 * - Accuracy and damage are the ranged formulas ([CombatMath.playerRangedAttackRoll], [CombatMath.playerRangedMaxHit])
 *   against the NPC's defence for the style's weight ([Combat.rangedMatchup]).
 * - XP is 4 a point of damage to Ranged, or 2 to Ranged and 2 to Defence on longrange, and 1.33 to Hitpoints (Combat).
 * - The hit lands [HitDelay.BOW] ticks after firing, a tick later on an NPC ([HitDelay.ON_NPC]).
 * - Each shot uses one piece of ammunition; a fifth of it breaks and the rest falls to the floor, or with an Ava's
 *   device worn its share comes back ([AvaDevices]). The dark bow fires two at once.
 * - "There is no ammo left in your quiver." with nothing in the ammo slot (Ammunition).
 *
 * Inferred, not from the Wiki: that the XP is given as the shot is fired, for the damage rolled (capped at what the NPC
 * has left after the shots already on their way), even if the hit then lands for less or not at all. RuneLite's XP drop
 * "predicted hit" works from that: the drop comes before the hitsplat. Not checked against a recording.
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
    /** Where shots bounce and the single-way rules don't hold. */
    private val multi: MultiCombat = MultiCombat(),
    /** Each player's words for the game messages ([CombatLines]). */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Out of 100, how much of a player's fired ammunition their Ava's device sends back now ([AvaDevices.recovery]). */
    private val recovery: (Player) -> Int = { 0 },
) {
    /** Out of 100, how much of [player]'s fired ammunition comes back now, for `::combat`. */
    fun recovered(player: Player): Int = recovery(player)

    /**
     * A shot on its way to [npc]. [ammo] falls when it lands (null for a bounce, which fires nothing); [bounces] is how
     * many more NPCs it can go on to, each rolled with [attackRoll] and [style], the first shot's, for up to two thirds
     * of [maxHit], in [travel]. What the player wears or picks once it has left doesn't change them.
     */
    private class Shot(
        val player: Player,
        val npc: Npc,
        val damage: Int,
        val landTick: Long,
        val ammo: ObjType?,
        val bounces: Int = 0,
        val style: AttackStyle? = null,
        val attackRoll: Int = 0,
        val maxHit: Int = 0,
        val travel: Int = -1,
        /** Whether its accuracy roll passed, which decides a chinchompa's blast too. */
        val hit: Boolean = false,
        /** How many NPCs its blast can hit, its target among them; 1 for no blast. */
        val targets: Int = 1,
        /** The poison severity it gives one time in eight, 0 for none, and whether it envenoms ([PoisonedWeapons]). */
        val poison: Int = 0,
        val venom: Boolean = false,
        /** The max hit [damage] was rolled against (two thirds of [maxHit] for a bounce), for the max hit hitsplat. */
        val rolledMax: Int = maxHit,
        /** Where the player stood when it was fired, for the NPC's retreat ([MeleeCombat.hit]). */
        val from: Tile? = null,
        /** Out of 100, how much of [ammo] comes back to the player's [slot] ([AvaDevices]); 0 without a device. */
        val recover: Int = 0,
        val slot: Int = WearPos.AMMO,
        /** Whether [ammo] that doesn't come back or break falls to the floor (a throw's never does, yet). */
        val floor: Boolean = true,
    )

    private val inFlight = ArrayList<Shot>()

    /** The blowpipe's darts and scales; World registers its item options. */
    val blowpipe = Blowpipe(objTypes, weapons, words)

    /** A ranged fight with [npc], or null when the player's style isn't ranged or their weapon isn't one this fires. */
    fun fight(player: Player, npc: Npc): RangedInteraction? {
        if (combat.style(player).type != AttackType.RANGED) return null
        weapon(player) ?: return null
        return RangedInteraction(npc, this)
    }

    internal fun unreachable(player: Player) = stopFacing(player)

    internal fun tryInteract(player: Player, interaction: RangedInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level || npc.outOfPlay) {
            stopFacing(player)
            return true
        }
        // Checked at every attack, not just the first: a boosted Slayer level can drop back mid-fight, and a player can
        // leave where the NPC is fought.
        val barred = combat.barred(player, npc)
        if (barred != null) {
            message(player, words(player)[barred])
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
            message(player, words(player)[CombatLines.SOMEONE_ELSE])
            stopFacing(player)
            return true
        }
        if (!interaction.fired && melee.underAttackByAnother(player, npc)) {
            message(player, words(player)[CombatLines.ALREADY_UNDER_ATTACK])
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
            // The last chinchompa thrown: the fight ends rather than going on bare-handed (guessed).
            if (weapon.thrown && player.equipment[WearPos.WEAPON] == null) {
                stopFacing(player)
                return true
            }
        }
        return false
    }

    /** Fires at [npc] on [tick]; false when there is nothing to fire, and the player has been told. */
    private fun fire(player: Player, npc: Npc, weapon: RangedWeapon, tick: Long): Boolean {
        if (weapon.loaded) return fireLoaded(player, npc, weapon, tick)
        if (weapon.thrown) return fireThrown(player, npc, weapon, tick)
        val worn = player.equipment[WearPos.AMMO]
        if (worn == null) {
            message(player, words(player)[CombatLines.NO_AMMO])
            return false
        }
        val ammoType = objTypes[worn.id]
        val ammo = weapons.ammo(ammoType)
        if (ammoType == null || ammo == null || !weapon.fires(ammo)) {
            message(player, words(player)[if (weapon.kind == AmmoKind.BOLTS) CombatLines.WRONG_BOLTS else CombatLines.WRONG_ARROWS])
            return false
        }
        val matchup = combat.rangedMatchup(player, npc) ?: return false
        // The dark bow fires one arrow when only one is left (OSRS Wiki, Dark bow).
        val shots = minOf(weapon.shots, worn.count)
        player.equipment.remove(WearPos.AMMO, shots)
        release(player, npc, weapon, ammo, ammoType, matchup, shots, tick, poison = PoisonedWeapons.ranged(ammoType), recover = recovery(player))
        return true
    }

    /**
     * A blowpipe's shot: its dart, a scale two times in three ([Blowpipe.use]), and nothing to the floor, as no Ava's
     * device brings the blowpipe's darts back yet.
     */
    private fun fireLoaded(player: Player, npc: Npc, weapon: RangedWeapon, tick: Long): Boolean {
        val ammo = weapons.ammo(blowpipe.dart(player))
        val matchup = combat.rangedMatchup(player, npc) ?: return false
        if (!blowpipe.use(player, { message(player, it) }, { random.chance(SCALE_KEPT_NUMERATOR, SCALE_KEPT_DENOMINATOR) })) return false
        release(player, npc, weapon, ammo, null, matchup, 1, tick, venom = true)
        return true
    }

    /**
     * A throw: one from the stack in the weapon slot, the numbers taken while it was still there, and nothing to the
     * floor. Chinchompas can't be picked up again, even when they do no damage (OSRS Wiki, Chinchompa (weapon)), and no
     * Ava's device brings them back (Ava's device); darts, knives, thrownaxes and the rest come back to the weapon slot
     * as arrows do to the quiver.
     */
    private fun fireThrown(player: Player, npc: Npc, weapon: RangedWeapon, tick: Long): Boolean {
        val matchup = combat.rangedMatchup(player, npc) ?: return false
        val thrown = combat.weapon(player)
        val poison = PoisonedWeapons.ranged(thrown)
        player.equipment.remove(WearPos.WEAPON, 1)
        val recover = if (weapon.targets > 1) 0 else recovery(player)
        val back = if (recover > 0) thrown else null
        release(player, npc, weapon, null, back, matchup, 1, tick, poison = poison, recover = recover, slot = WearPos.WEAPON, floor = false)
        return true
    }

    /** The shots leave: the animation, graphics and projectiles, and each one's roll and XP. [dropped] falls where they land. */
    private fun release(
        player: Player,
        npc: Npc,
        weapon: RangedWeapon,
        ammo: RangedAmmo?,
        dropped: ObjType?,
        matchup: RangedMatchup,
        shots: Int,
        tick: Long,
        poison: Int = 0,
        venom: Boolean = false,
        recover: Int = 0,
        slot: Int = WearPos.AMMO,
        floor: Boolean = true,
    ) {
        val style = matchup.style
        player.nextAttackTick = tick + speed(player, style)
        if (weapon.anim >= 0) player.effects.anim(weapon.anim, 0)
        val launch = when {
            weapon.launch >= 0 -> weapon.launch
            ammo == null -> -1
            shots > 1 && ammo.doubleLaunch >= 0 -> ammo.doubleLaunch
            else -> ammo.launch
        }
        val travel = if (weapon.travel >= 0) weapon.travel else ammo?.travel ?: -1
        if (launch >= 0) player.effects.spotanim(0, launch, LAUNCH_HEIGHT, 0)
        face(player, npc)
        val size = npc.type.size
        val distance = LineOfSight.distance(player.x, player.z, 1, npc.x, npc.z, size, size)
        val landTick = tick + weapon.delay.ticks(distance) + HitDelay.ON_NPC
        repeat(shots) { shot ->
            if (travel >= 0) projectile(flight(player, npc, travel, landTick - tick, shot))
            // Accuracy first, then damage, as for melee; a shot's random draws are always these two.
            val sure = combat.sureHit(player, npc, style.type)
            val hit = CombatMath.rollHits(random, matchup.attackRoll, matchup.defenceRoll) || player.sureHits || sure == SureHit.MAX
            val rolled = if (hit) CombatMath.rollDamage(random, matchup.maxHit).let { if (sure != SureHit.ROLLED) matchup.maxHit else it } else 0
            val damage = reserve(player, npc, style, rolled)
            melee.attackMade(player, npc, tick)
            inFlight += Shot(player, npc, damage, landTick, dropped, weapon.bounces, style, matchup.attackRoll, matchup.maxHit, travel, hit, weapon.targets, poison, venom, from = Tile(player.x, player.z), recover = recover, slot = slot, floor = floor)
        }
    }

    /**
     * The part of [rolled] that [npc] can still take, what it has left after the shots and swings already on their way,
     * kept for it and given as XP now.
     */
    private fun reserve(player: Player, npc: Npc, style: AttackStyle, rolled: Int): Int {
        val damage = minOf(melee.armoured(npc, rolled, magic = false), (npc.hitpoints - combat.floor(npc, player) - npc.incoming).coerceAtLeast(0))
        npc.incoming += damage
        val bonus = combat.npcCombat[npc.type]?.xpBonusTenths ?: 0
        for (skill in style.xp) player.skills.addTenths(skill, CombatMath.withXpBonus(CombatMath.styleXpTenths(damage, style.xp.size), bonus))
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.withXpBonus(CombatMath.hitpointsXpTenths(damage), bonus))
        return damage
    }

    /**
     * A Venator bow's shot that landed on [from] in a multi-way area goes on to another NPC within [BOUNCE_RANGE] of it
     * (OSRS Wiki, Venator bow): one picked at random that can be fought, possibly the first target again but never the
     * one it just hit, whether the last hit missed or killed. Its accuracy is rolled on its own, the first shot's attack
     * roll against that NPC's defence, and its max hit is two thirds of the first shot's, rounded down. Null when there
     * is nowhere to go.
     *
     * Guessed: that the range is counted edge to edge as attack range is, that the NPC it goes to must be in a
     * multi-way area too, that no line of sight is needed, that it lands [BOUNCE_TICKS] after the hit before it, and
     * that the XP comes as it is rolled, as a shot's does.
     */
    private fun bounce(shot: Shot, tick: Long): Shot? {
        val from = shot.npc
        if (!multi.at(from.level, from.x, from.z)) return null
        val size = from.type.size
        val targets = npcs.inPlay.filter { npc ->
            npc !== from && !npc.outOfPlay && npc.level == from.level && combat.npcCombat[npc.type] != null &&
                multi.at(npc.level, npc.x, npc.z) && combat.barred(shot.player, npc) == null &&
                LineOfSight.distance(from.x, from.z, size, npc.x, npc.z, npc.type.size, npc.type.size) <= BOUNCE_RANGE
        }.toList()
        if (targets.isEmpty()) return null
        val next = random.pick(targets)
        val style = shot.style ?: return null
        val defenceRoll = combat.rangedDefenceRoll(next, style) ?: return null
        val rolled = if (CombatMath.rollHits(random, shot.attackRoll, defenceRoll) || shot.player.sureHits) CombatMath.rollDamage(random, shot.maxHit * 2 / 3) else 0
        val damage = reserve(shot.player, next, style, rolled)
        if (shot.travel >= 0) projectile(bounceFlight(from, next, shot.travel))
        return Shot(shot.player, next, damage, tick + BOUNCE_TICKS, null, shot.bounces - 1, style, shot.attackRoll, shot.maxHit, shot.travel, poison = shot.poison, venom = shot.venom, rolledMax = shot.maxHit * 2 / 3, from = shot.from)
    }

    /**
     * A chinchompa landing on [shot]'s NPC in a multi-way area blows up the 3x3 around it, centred on its south-west tile
     * (OSRS Wiki, Multicombat area: its "true tile"): every other NPC there that is in a multi-way area too is hit if the
     * target was, and missed if it was missed (OSRS Wiki, Chinchompa (weapon)), up to the weapon's cap: 11 NPCs, or 12
     * for black chinchompas (OSRS Wiki, Chinchompa, Black chinchompa). In single-way combat only the target is hit (OSRS
     * Wiki, Multicombat area).
     *
     * Guessed: that each NPC's damage is rolled on its own, 0 to the throw's max hit; that the cap counts the target;
     * which NPCs the cap leaves out (here the ones last in the NPC list); that an NPC is in the area when its south-west
     * tile is; and that their XP comes as the blast lands. The explosion graphic isn't shown yet.
     */
    private fun splash(shot: Shot, tick: Long) {
        val from = shot.npc
        if (!multi.at(from.level, from.x, from.z)) return
        val style = shot.style ?: return
        val around = npcs.inPlay.filter { npc ->
            npc !== from && !npc.outOfPlay && npc.level == from.level && combat.npcCombat[npc.type] != null &&
                combat.barred(shot.player, npc) == null && abs(npc.x - from.x) <= SPLASH_RADIUS && abs(npc.z - from.z) <= SPLASH_RADIUS && multi.at(npc.level, npc.x, npc.z)
        }.take(shot.targets - 1).toList()
        for (npc in around) {
            val rolled = if (shot.hit) CombatMath.rollDamage(random, shot.maxHit) else 0
            val damage = reserve(shot.player, npc, style, rolled)
            npc.incoming -= damage
            melee.hit(npc, shot.player, damage, tick, shot.maxHit, shot.from)
        }
    }

    /**
     * The NPC phase's part: shots whose time has come land, each its hitsplat (see [MeleeCombat.hit]) and its
     * ammunition on the floor four times in five. A shot lands whatever happened to its shooter since; one whose NPC
     * died or went in the meantime does nothing but drop its ammunition.
     */
    fun process() {
        val tick = currentTick()
        val bounced = ArrayList<Shot>()
        val iterator = inFlight.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            if (tick < shot.landTick) continue
            iterator.remove()
            val npc = shot.npc
            npc.incoming -= shot.damage
            // A shot whose NPC is still there hits it, and bounces on even if that hit kills it. Guessed for a hidden
            // NPC: a shot on its way when it went from sight does nothing, as for one that died.
            val landed = npcs[npc.index] === npc && !npc.outOfPlay
            if (landed) melee.weaponPoison(npc, melee.hit(npc, shot.player, shot.damage, tick, shot.rolledMax, shot.from), shot.poison, shot.venom, ranged = true)
            land(shot, npc)
            if (landed && shot.bounces > 0) bounce(shot, tick)?.let { bounced += it }
            if (landed && shot.targets > 1) splash(shot, tick)
        }
        inFlight += bounced
    }

    /**
     * What becomes of a landed shot's ammunition: a fifth breaks; without an Ava's device the rest falls, under the NPC's
     * south-west tile where its drops go too (guessed); with one, its share goes back to the slot it came from and the
     * rest falls. Guessed: that it comes back as the shot lands rather than as it is fired, and that one with no room
     * there (the player has put on other ammunition, or logged out) falls instead.
     */
    private fun land(shot: Shot, npc: Npc) {
        val ammo = shot.ammo ?: return
        if (shot.recover <= 0) {
            if (shot.floor && !random.chance(AMMO_BROKEN_NUMERATOR, AMMO_BROKEN_DENOMINATOR)) dropAmmo(ammo, npc.x, npc.z, npc.level, shot.player)
            return
        }
        val roll = random.nextInt(100)
        if (roll < AvaDevices.BROKEN) return
        if (roll < AvaDevices.BROKEN + shot.recover && giveBack(shot.player, shot.slot, ammo)) return
        if (shot.floor) dropAmmo(ammo, npc.x, npc.z, npc.level, shot.player)
    }

    /** Puts one [type] back in [player]'s worn [slot]; false when it holds something else or a full stack, or they've gone. */
    private fun giveBack(player: Player, slot: Int, type: ObjType): Boolean {
        if (player.loggedOut) return false
        val worn = player.equipment[slot]
        if (worn != null && (worn.id != type.id || worn.count >= Inventory.MAX_STACK)) return false
        player.equipment[slot] = InvObj(type.id, (worn?.count ?: 0) + 1)
        return true
    }

    /** Forgets the shots at NPCs on the tiles where [inside] holds (an instance ending), so none drops its ammunition there. */
    fun forgetArea(inside: (Int, Int) -> Boolean) {
        for (shot in inFlight) if (inside(shot.npc.x, shot.npc.z)) shot.npc.incoming -= shot.damage
        inFlight.removeIf { inside(it.npc.x, it.npc.z) }
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

    /** A bounce's projectile, from the NPC the shot hit to the next; guessed the same heights and arc as a shot's. */
    private fun bounceFlight(from: Npc, to: Npc, id: Int): Projectile = Projectile(
        id = id,
        fromX = from.x,
        fromZ = from.z,
        level = from.level,
        toX = to.x,
        toZ = to.z,
        startHeight = PROJECTILE_END_HEIGHT,
        endHeight = PROJECTILE_END_HEIGHT,
        startTime = 0,
        endTime = (BOUNCE_TICKS * CYCLES_PER_TICK).toInt(),
        angle = PROJECTILE_ANGLE,
        progress = PROJECTILE_PROGRESS,
        sourceIndex = from.index + 1,
        targetIndex = to.index + 1,
    )

    companion object {
        /** How far a bounce reaches from the NPC it hit (OSRS Wiki, Venator bow: within 2 tiles). */
        const val BOUNCE_RANGE = 2

        /** A chinchompa's blast reaches one tile round its target: 3x3 (OSRS Wiki, Chinchompa (weapon)). */
        const val SPLASH_RADIUS = 1

        /** Guessed: the Wiki doesn't say how long a bounce takes. */
        const val BOUNCE_TICKS = 1L

        const val LONGRANGE_EXTRA = 2

        /** One in five shots breaks its ammunition (OSRS Wiki, Ava's accumulator: 20% broken without one). */
        const val AMMO_BROKEN_NUMERATOR = 1
        const val AMMO_BROKEN_DENOMINATOR = 5

        /** A blowpipe shot keeps its scale one time in three (OSRS Wiki, Toxic blowpipe). */
        const val SCALE_KEPT_NUMERATOR = 1
        const val SCALE_KEPT_DENOMINATOR = 3

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
