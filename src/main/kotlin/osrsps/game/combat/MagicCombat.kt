package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.Weapons
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.RouteTarget
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/**
 * A player casting on [npc]: they walk until it is in range and in sight, then cast [spell] once, or, with [spell] null,
 * the spell they autocast, again and again. See [MagicCombat].
 */
class MagicInteraction internal constructor(val npc: Npc, spell: CombatSpell?, private val owner: MagicCombat) : Interaction {
    /** The spell picked in the spellbook for one cast; null while autocasting. */
    var spell: CombatSpell? = spell
        internal set

    /** Whether the player has cast on the NPC yet; until then the fight can still be refused on arrival. */
    internal var cast = false

    /** Where the NPC stood when the player's route to it was last made; null once they have stood in range. */
    internal var routedTo: Tile? = null

    /** Whether the player could cast on the NPC from where they stood when last tried. */
    internal var inRange = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun unreachable(player: Player) = owner.unreachable(player)

    override fun holding(player: Player) = inRange
}

/**
 * Combat spells from the standard spellbook cast on NPCs ([Spells] lists them): a spell picked in the spellbook and
 * clicked on an NPC becomes a [MagicInteraction]. The player walks until the NPC is within [RANGE] and in line of sight,
 * stops, waits for their attack delay if a swing or shot is still on it, and casts once. A player autocasting
 * ([Autocast], picked on the combat tab) fights with magic instead: Attack, or auto retaliate, makes a fight ([fight])
 * that casts the picked spell every [SPEED] ticks until the NPC dies or the player can't cast it any more. Single-way
 * combat works as for melee ([MeleeCombat.foughtByAnother], [MeleeCombat.underAttackByAnother]).
 *
 * From the OSRS Wiki:
 * - A spell needs its Magic level, boosts counting, and its runes, taken as it is cast; an elemental staff stands in
 *   for its runes ([Spells.runesFrom]).
 * - Accuracy and damage are the magic formulas ([Combat.magicMatchup]); a spell that misses splashes: no damage, the
 *   splash graphic on the target, and the base XP only (Splashing). Combat spells take [SPEED] ticks (Magic).
 * - XP is the spell's base XP and 2 a point of damage to Magic, and 1.33 a point to Hitpoints (Magic, Combat); a
 *   defensive autocast gives 1.33 a point to Magic and 1 to Defence instead of the 2 (Autocast).
 * - The hit lands [HitDelay.MAGIC] ticks after the cast, a tick later on an NPC ([HitDelay.ON_NPC]).
 * - Bind, Snare and Entangle hold the target for their ticks when they hit, not when they splash, and can't be cast
 *   on a target that is held or in the 5 ticks of immunity after ([Npcs.freeze]; Freeze, Snare). A splash of one gives
 *   less base XP than a hit ([CombatSpell.splashXpTenths]).
 *
 * Inferred as for ranged: that the XP is given as the spell is cast, for the damage rolled (capped at what the NPC has
 * left after the hits already on their way).
 *
 * Guessed and marked below: the messages for too low a level and too few runes, which rune they name when several are
 * short, the graphics' heights, the projectile's start, heights and arc, which animation counts as cast with a staff,
 * that the player keeps facing the NPC after the cast, what a splash shows besides its graphic, and that a spell cast
 * from the spellbook while autocasting is followed by the autocast spell (from memory of live).
 */
class MagicCombat(
    private val npcs: Npcs,
    private val collision: CollisionMap,
    private val movement: Movement,
    private val combat: Combat,
    private val objTypes: ObjTypes,
    private val spells: Spells,
    private val melee: MeleeCombat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** Fires a projectile for everyone near it. */
    private val projectile: (Projectile) -> Unit = {},
    /** What the player is told and shown; tests swap these for recorders. */
    private val message: (Player, String) -> Unit = Player::message,
    private val face: (Player, Npc) -> Unit = { _, _ -> },
    private val stopFacing: (Player) -> Unit = {},
) {
    /** A spell on its way to [npc], landing on [landTick] for [damage] if [hit], or splashing. */
    private class Cast(val player: Player, val npc: Npc, val spell: CombatSpell, val hit: Boolean, val damage: Int, val landTick: Long)

    private val inFlight = ArrayList<Cast>()

    /**
     * The player cast the spell at [component] of the standard spellbook on [npc]. Nothing happens for a spell that
     * isn't a combat spell here or an NPC that can't be fought; single-way combat refuses it as an Attack would.
     */
    fun cast(player: Player, npc: Npc, component: Int) {
        val spell = spells.spell(component)
        if (spell == null) {
            logger.info { "${player.username} cast spellbook component $component on $npc, which isn't a combat spell here" }
            return
        }
        if (combat.npcCombat[npc.type] == null) {
            logger.info { "${player.username} cast ${spell.name} on $npc, which can't be fought" }
            return
        }
        if (melee.underAttackByAnother(player, npc)) {
            message(player, MeleeCombat.ALREADY_UNDER_ATTACK)
            return
        }
        if (melee.foughtByAnother(player, npc)) {
            message(player, MeleeCombat.SOMEONE_ELSE)
            return
        }
        // The cast starts in the movement phase, as a ranged fight does.
        player.interaction = MagicInteraction(npc, spell, this)
    }

    /**
     * A fight with [npc] for a player autocasting ([Combat.style] is magic), or null when they aren't or their spell
     * isn't one here; see [MeleeCombat.fight].
     */
    fun fight(player: Player, npc: Npc): MagicInteraction? {
        if (combat.style(player).type != AttackType.MAGIC) return null
        autocastSpell(player) ?: return null
        return MagicInteraction(npc, null, this)
    }

    /** The spell the player autocasts, or null when they don't. */
    private fun autocastSpell(player: Player): CombatSpell? =
        if (combat.style(player).type == AttackType.MAGIC) spells.autocast(Autocast.key(player)) else null

    internal fun unreachable(player: Player) = stopFacing(player)

    internal fun tryInteract(player: Player, interaction: MagicInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level || npc.dead) {
            stopFacing(player)
            return true
        }
        val spell = interaction.spell ?: autocastSpell(player)
        if (spell == null) {
            // Autocast went off on the way (a style picked, the staff taken off): carry on with whatever fights now.
            val next = melee.fight(player, npc)
            if (next == null) {
                message(player, MeleeCombat.MELEE_ONLY)
                stopFacing(player)
            }
            player.interaction = next
            return true
        }
        val size = npc.type.size
        if (!LineOfSight.inRange(collision, player.level, player.x, player.z, 1, npc.x, npc.z, size, size, RANGE)) {
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
        // Another NPC may have caught them on the way; once the fight is on, a newcomer's hits don't stop it.
        if (!interaction.cast && melee.underAttackByAnother(player, npc)) {
            message(player, MeleeCombat.ALREADY_UNDER_ATTACK)
            stopFacing(player)
            return true
        }
        val tick = currentTick()
        if (tick < player.nextAttackTick) return false
        val autocast = interaction.spell == null
        if (!release(player, npc, spell, tick, defensive = autocast && Autocast.defensive(player))) {
            stopFacing(player)
            return true
        }
        interaction.cast = true
        // A spell from the book is cast once; then the autocast spell, if any, carries the fight on. Guessed: the player
        // keeps facing the NPC after a single cast, as live's face-entity stays until they do something else.
        interaction.spell = null
        return autocastSpell(player) == null
    }

    /**
     * Casts [spell] on [npc] on [tick], with defensive autocast's XP when [defensive]; false when the player can't, and
     * has been told why.
     */
    private fun release(player: Player, npc: Npc, spell: CombatSpell, tick: Long, defensive: Boolean): Boolean {
        if (player.skills.currentLevel(Skill.MAGIC) < spell.level) {
            message(player, LEVEL_TOO_LOW)
            return false
        }
        val provided = spells.runesFrom(player.equipment[WearPos.WEAPON]?.id)
        val short = spell.runes.firstOrNull { it.rune !in provided && player.inventory.count(it.rune) < it.count }
        if (short != null) {
            message(player, notEnough(short.rune))
            return false
        }
        if (spell.holds && (npc.freezeTicks > 0 || npc.freezeImmunityTicks > 0)) {
            message(player, if (npc.freezeTicks > 0) ALREADY_HELD else IMMUNE)
            return false
        }
        val matchup = combat.magicMatchup(player, npc, spell) ?: return false
        for (cost in spell.runes) if (cost.rune !in provided) take(player, cost)
        player.nextAttackTick = tick + SPEED
        val anim = if (combat.weaponCategory(player) == Weapons.STAFF) spell.staffAnim else spell.anim
        if (anim >= 0) player.effects.anim(anim, 0)
        if (spell.casting >= 0) player.effects.spotanim(0, spell.casting, CASTING_HEIGHT, 0)
        face(player, npc)
        val size = npc.type.size
        val distance = LineOfSight.distance(player.x, player.z, 1, npc.x, npc.z, size, size)
        val landTick = tick + HitDelay.MAGIC.ticks(distance) + HitDelay.ON_NPC
        if (spell.travel >= 0) projectile(flight(player, npc, spell.travel, landTick - tick))
        // Accuracy first, then damage, as for melee and ranged.
        val hit = CombatMath.rollHits(random, matchup.attackRoll, matchup.defenceRoll) || player.sureHits
        val rolled = if (hit) CombatMath.rollDamage(random, matchup.maxHit) else 0
        val damage = minOf(rolled, (npc.hitpoints - npc.incoming).coerceAtLeast(0))
        npc.incoming += damage
        val baseXp = if (hit) spell.xpTenths else spell.splashXpTenths
        if (defensive) {
            player.skills.addTenths(Skill.MAGIC, baseXp + CombatMath.defensiveSpellMagicXpTenths(damage))
            player.skills.addTenths(Skill.DEFENCE, CombatMath.defensiveSpellDefenceXpTenths(damage))
        } else {
            player.skills.addTenths(Skill.MAGIC, baseXp + CombatMath.spellDamageXpTenths(damage))
        }
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
        inFlight += Cast(player, npc, spell, hit, damage, landTick)
        return true
    }

    /** Takes [cost] from the inventory, from the first stacks of the rune on. */
    private fun take(player: Player, cost: RuneCost) {
        var left = cost.count
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id == cost.rune) left -= player.inventory.remove(slot, left)
        }
    }

    /** Guessed wording, from memory: "You do not have enough Fire Runes to cast this spell." */
    private fun notEnough(rune: Int): String {
        val name = objTypes[rune]?.name ?: "rune"
        val plural = name.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } } + "s"
        return "You do not have enough $plural to cast this spell."
    }

    /**
     * The NPC phase's part: spells whose time has come land. One that hit shows its impact graphic and its hitsplat (see
     * [MeleeCombat.hit]); one that missed shows the splash graphic and puts the NPC in combat ([MeleeCombat.splashed]).
     * A spell whose NPC died or went in the meantime does nothing.
     */
    fun process() {
        val tick = currentTick()
        val iterator = inFlight.iterator()
        while (iterator.hasNext()) {
            val cast = iterator.next()
            if (tick < cast.landTick) continue
            iterator.remove()
            val npc = cast.npc
            npc.incoming -= cast.damage
            if (npcs[npc.index] !== npc || npc.dead) continue
            if (cast.hit) {
                if (cast.spell.impact >= 0) npc.effects.spotanim(0, cast.spell.impact, IMPACT_HEIGHT)
                // Guessed: Bind, which can't do damage, shows no hitsplat, as a splash shows none.
                if (cast.spell.maxHit == 0) melee.splashed(npc, cast.player, tick) else melee.hit(npc, cast.player, cast.damage, tick)
                if (cast.spell.freezeTicks > 0 && !npc.dead) npcs.freeze(npc, cast.spell.freezeTicks)
            } else {
                if (spells.splash >= 0) npc.effects.spotanim(0, spells.splash, IMPACT_HEIGHT)
                melee.splashed(npc, cast.player, tick)
            }
        }
    }

    /** Spells cast and not landed yet, for tests and commands. */
    val castsInFlight: Int get() = inFlight.size

    /**
     * The projectile a spell flies as, landing as its hit does: [ticks] after it was cast, in client cycles. Guessed
     * heights, start, arc and offset: the arrows' heights, leaving a little later as the casting animation is longer.
     */
    private fun flight(player: Player, npc: Npc, id: Int, ticks: Long): Projectile = Projectile(
        id = id,
        fromX = player.x,
        fromZ = player.z,
        level = player.level,
        toX = npc.x,
        toZ = npc.z,
        startHeight = PROJECTILE_START_HEIGHT,
        endHeight = PROJECTILE_END_HEIGHT,
        startTime = PROJECTILE_START,
        endTime = maxOf(PROJECTILE_START + 1, (ticks * CYCLES_PER_TICK).toInt()),
        angle = PROJECTILE_ANGLE,
        progress = PROJECTILE_PROGRESS,
        sourceIndex = -(player.index + 1),
        targetIndex = npc.index + 1,
    )

    companion object {
        /** Spells reach 10 tiles (OSRS Wiki, Attack range). */
        const val RANGE = 10

        /** Combat spells from the spellbook take 5 ticks, 3 seconds (OSRS Wiki, Magic). */
        const val SPEED = 5

        /** A hold spell on a frozen target (OSRS Wiki, Snare). */
        const val ALREADY_HELD = "Your target is already held by a magical force."

        /** A hold spell on a target in the ticks after a freeze (OSRS Wiki, Snare). */
        const val IMMUNE = "The target is currently immune to that spell."

        /** Guessed wording, from memory. */
        const val LEVEL_TOO_LOW = "Your Magic level is not high enough for this spell."

        /** Guessed: about the hands, as the ranged launch graphics are. */
        const val CASTING_HEIGHT = 92

        /** Guessed: about the middle of a man-sized NPC. */
        const val IMPACT_HEIGHT = 124

        private const val CYCLES_PER_TICK = 30
        private const val PROJECTILE_START = 51
        private const val PROJECTILE_START_HEIGHT = 172
        private const val PROJECTILE_END_HEIGHT = 124
        private const val PROJECTILE_ANGLE = 16
        private const val PROJECTILE_PROGRESS = 64
    }
}
