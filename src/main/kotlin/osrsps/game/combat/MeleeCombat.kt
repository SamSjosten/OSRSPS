package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcEffects
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap
import osrsps.map.Reach
import osrsps.map.RouteTarget
import osrsps.map.Tile
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/** A player fighting [npc] with melee: they walk up to it and swing until one of them is gone. See [MeleeCombat]. */
class MeleeInteraction internal constructor(val npc: Npc, private val owner: MeleeCombat) : Interaction {
    /** Where the NPC stood when the player's route to it was last made. */
    internal var routedTo: Tile? = null

    /** Whether the player stood in reach of the NPC when last tried. */
    internal var inReach = false

    /** Whether the player has swung at the NPC yet; until then the fight can still be refused on arrival. */
    internal var swung = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun unreachable(player: Player) = owner.unreachable(player)

    override fun holding(player: Player) = inReach
}

/**
 * Melee between players and NPCs. An Attack option becomes a [MeleeInteraction]: each movement phase the player
 * chases the NPC as for any NPC option (see [osrsps.game.npc.NpcInteractions]) and, once beside it, swings every
 * weapon-speed ticks. A swing rolls accuracy then damage ([CombatMath]), shows the hitsplat and health bar to everyone,
 * and gives XP by the attack style (OSRS Wiki, Combat).
 *
 * An NPC that is hit fights back ([process], in the NPC phase): it chases its attacker, keeping within its max range
 * of its spawn ([Npcs.MAX_RANGE]), and swings every attack-speed ticks once beside them; it gives up when they are
 * gone, dead, on another level, further than [CHASE_RANGE] from its spawn, or fought by another NPC. A player hit
 * with auto retaliate on fights back too (OSRS Wiki, Auto Retaliate: one tick after the hit, with their attack delay
 * set to half their weapon's speed, rounded up). Lumbridge is single-way: an NPC another player hit recently says
 * "Someone else is fighting that.", and a player another NPC is hitting can't attack a second one ("I'm already under
 * attack."). Hitpoints come back one a minute (Wiki, Hitpoints) for players and NPCs alike.
 *
 * From the Wiki (Hit delay): melee has no delay of its own, but NPCs are processed before players, so a player's swing
 * on an NPC lands in the next tick's NPC phase ([HitDelay.ON_NPC]); the XP comes with the swing, for the damage it can
 * still deal. An NPC's swing on a player lands on its own tick. An NPC with no hitpoints left dies and comes back at
 * its spawn after its respawn time. Guessed and marked below: how long an NPC counts as fighting after its last hit, how
 * long its body stays, that the respawn time counts from the killing blow, that an NPC strikes back from the tick
 * after it is hit, that the Wiki's retreat ranges are also the chase ranges, and which animation wins when a swing and
 * a block land on one tick (the swing). A player with no hitpoints left dies: every fight with them ends here and
 * [Deaths] takes it from there.
 */
class MeleeCombat(
    private val npcs: Npcs,
    private val collision: CollisionMap,
    private val movement: Movement,
    private val combat: Combat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** Takes a dead NPC out of the world, and puts a respawn in. */
    private val removeNpc: (Npc) -> Unit,
    private val spawnNpc: (NpcSpawn) -> Unit,
    /** Everyone logged in, for hitpoints regeneration. */
    private val players: () -> Sequence<Player> = { emptySequence() },
    /** What the player is told and shown; tests swap these for recorders. */
    private val message: (Player, String) -> Unit = Player::message,
    private val face: (Player, Npc) -> Unit = { _, _ -> },
    private val stopFacing: (Player) -> Unit = {},
    /** Told of a player with no hitpoints left; see [Deaths.die]. */
    private val onDeath: (Player) -> Unit = {},
    /** Puts a dead NPC's drops on the floor for its killer (null for none) once its body has gone. */
    private val loot: (Npc, Player?) -> Unit = { _, _ -> },
    /** A fight with a style melee doesn't fight with, or null for none: [MagicCombat.fight] or [RangedCombat.fight]. */
    private val ranged: (Player, Npc) -> Interaction? = { _, _ -> null },
    /** Where the single-way rules below don't hold. */
    private val multi: MultiCombat = MultiCombat(),
    /** Whether the player's protection prayers block an NPC's attack of this type; see [Prayers.protects]. */
    private val protects: (Player, AttackType) -> Boolean = { _, _ -> false },
    /** Whether a prayer with the effect counts for the player this tick; see [Prayers.has]. */
    private val hasPrayer: (Player, String) -> Boolean = { _, _ -> false },
    /** Every prayer off ([Prayers.deactivateAll]), for Redemption. */
    private val prayersOff: (Player) -> Unit = {},
    private val sound: (Player, Int) -> Unit = { player, id -> player.sound(id) },
    /** Each player's words for the game messages ([CombatLines]). */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Told of an NPC on the tick it is killed; see [osrsps.game.npc.NpcScripts.onDeath]. */
    private val npcDied: (Npc) -> Unit = {},
) {
    private class Dying(val npc: Npc, val killer: Player?, val removeTick: Long, val respawnTick: Long)

    internal fun unreachable(player: Player) = stopFacing(player)

    private val dying = ArrayList<Dying>()

    /**
     * The player used Attack on [npc]. Returns false when the NPC has no combat definition, so the click falls through
     * to whatever else handles it. With a style nothing fights with yet (magic, thrown weapons) the player is told so.
     */
    fun attack(player: Player, npc: Npc): Boolean = start(player, npc, now = true)

    /**
     * The player used Attack on [npc] with a style that doesn't walk up to it first (ranged, or one nothing fights with
     * yet, which is refused straight away). The fight starts in the movement phase, as one handed over on arrival does.
     * False for melee, which [attack] takes on arrival.
     */
    fun attackFromAfar(player: Player, npc: Npc): Boolean =
        combat.meleeType(combat.style(player)) == null && start(player, npc, now = false)

    /** Starts a fight with [npc], tried at once when [now]. False when the NPC can't be fought; see [attack]. */
    private fun start(player: Player, npc: Npc, now: Boolean): Boolean {
        combat.npcCombat[npc.type] ?: return false
        val fight = fight(player, npc)
        if (fight == null) {
            message(player, MELEE_ONLY)
            return true
        }
        if (underAttackByAnother(player, npc)) {
            message(player, words(player)[CombatLines.ALREADY_UNDER_ATTACK])
            return true
        }
        if (foughtByAnother(player, npc)) {
            message(player, words(player)[CombatLines.SOMEONE_ELSE])
            return true
        }
        player.interaction = fight
        // Beside it already: the click is handed over on arrival, so the first swing is this tick's.
        if (now && fight.tryInteract(player) && player.interaction === fight) player.interaction = null
        return true
    }

    /** A fight with [npc] in the player's style: melee here, magic or ranged from [ranged]; null when nothing fights with it. */
    fun fight(player: Player, npc: Npc): Interaction? =
        if (combat.meleeType(combat.style(player)) != null) MeleeInteraction(npc, this) else ranged(player, npc)

    internal fun tryInteract(player: Player, interaction: MeleeInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level || npc.dead) {
            stopFacing(player)
            return true
        }
        val size = npc.type.size
        if (!Reach.reachedEntity(collision, player.level, player.x, player.z, 1, npc.x, npc.z, size, size)) {
            interaction.inReach = false
            val at = Tile(npc.x, npc.z)
            if (interaction.routedTo != at) {
                interaction.routedTo = at
                movement.request(player, RouteTarget.entity(npc.x, npc.z, size), player.invertRun)
            }
            return false
        }
        interaction.inReach = true
        player.waypoints.clear()
        if (foughtByAnother(player, npc)) {
            message(player, words(player)[CombatLines.SOMEONE_ELSE])
            stopFacing(player)
            return true
        }
        // Another NPC may have caught them on the way; once the fight is on, a newcomer's hits don't stop it.
        if (!interaction.swung && underAttackByAnother(player, npc)) {
            message(player, words(player)[CombatLines.ALREADY_UNDER_ATTACK])
            stopFacing(player)
            return true
        }
        val tick = currentTick()
        if (tick >= player.nextAttackTick) {
            swing(player, npc, tick)
            interaction.swung = true
        }
        return false
    }

    /**
     * Whether another player hit [npc] within the last [IN_COMBAT_TICKS] ticks and is still here. Guessed: the Wiki
     * says a single-way NPC stays someone's for a while after their last hit, but not how long. Never for an NPC standing
     * in a multi-way area (guessed: by the NPC's tile, not the player's).
     */
    internal fun foughtByAnother(player: Player, npc: Npc): Boolean {
        if (multi.at(npc.level, npc.x, npc.z)) return false
        val other = npc.attacker ?: return false
        return other !== player && currentTick() - npc.attackedTick <= IN_COMBAT_TICKS && !other.loggedOut
    }

    /**
     * Whether an NPC other than [npc] is fighting [player] and hit them within the last [IN_COMBAT_TICKS] ticks. Never
     * for a player standing in a multi-way area (guessed: by the player's tile).
     */
    internal fun underAttackByAnother(player: Player, npc: Npc): Boolean {
        if (multi.at(player.level, player.x, player.z)) return false
        val other = player.attackedBy ?: return false
        return other !== npc && other.target === player && !other.dead && currentTick() - player.attackedTick <= IN_COMBAT_TICKS
    }

    private fun swing(player: Player, npc: Npc, tick: Long) {
        val style = combat.style(player)
        val matchup = combat.matchup(player, npc) ?: run {
            // The style changed on the way (the combat tab is open while walking): a bow carries on as a ranged fight.
            val next = ranged(player, npc)
            if (next == null) {
                message(player, MELEE_ONLY)
                stopFacing(player)
            }
            player.interaction = next
            return
        }
        player.nextAttackTick = tick + combat.attackSpeed(player)
        player.effects.anim(combat.anims(player).attack(style), 0)
        face(player, npc)
        // Accuracy first, then damage, as the Wiki describes a hit; the attack's random draws are always these two.
        val rolled = if (CombatMath.rollHits(random, matchup.attackRoll, matchup.defenceRoll) || player.sureHits) CombatMath.rollDamage(random, matchup.maxHit) else 0
        // XP for what the swing can still deal: what the NPC has left after the swings and shots already on their way.
        val damage = minOf(rolled, (npc.hitpoints - npc.incoming).coerceAtLeast(0))
        npc.incoming += damage
        for (skill in style.xp) player.skills.addTenths(skill, CombatMath.styleXpTenths(damage, style.xp.size))
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
        landing += Swing(player, npc, damage, tick + HitDelay.MELEE.ticks(0) + HitDelay.ON_NPC)
    }

    /** A player's swing on its way to an NPC: it lands on [landTick]. */
    private class Swing(val player: Player, val npc: Npc, val damage: Int, val landTick: Long)

    private val landing = ArrayList<Swing>()

    /** Swings rolled and not landed yet, for tests. */
    val swingsLanding: Int get() = landing.size

    /**
     * The swings whose tick has come land, as [RangedCombat]'s shots do, whatever happened to the swinger since; one
     * whose NPC died or went does nothing.
     */
    private fun land(tick: Long) {
        val iterator = landing.iterator()
        while (iterator.hasNext()) {
            val swing = iterator.next()
            if (tick < swing.landTick) continue
            iterator.remove()
            val npc = swing.npc
            npc.incoming -= swing.damage
            if (npcs[npc.index] === npc && !npc.dead) hit(npc, swing.player, swing.damage, tick)
        }
    }

    /**
     * [npc] takes [damage] from [source] (null for none, as from a command), capped at what it has left, on [tick]:
     * the hitsplat and health bar for everyone, its block animation, or its death. A hit NPC turns on whoever hit it.
     * Returns the damage dealt.
     */
    fun hit(npc: Npc, source: Player?, damage: Int, tick: Long): Int {
        val def = combat.npcCombat[npc.type] ?: throw IllegalArgumentException("${npc.type.name} can't be fought")
        if (npc.dead) return 0
        val dealt = damage.coerceIn(0, npc.hitpoints)
        npc.hitpoints -= dealt
        val from = if (source != null) NpcEffects.PLAYER_SOURCE + source.index else -1
        if (dealt > 0) npc.effects.hit(from, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER, dealt)
        else npc.effects.hit(from, HitsplatType.BLOCK_ME, HitsplatType.BLOCK_OTHER, 0)
        npc.effects.headBar(from, HEALTH_BAR, healthBarFill(npc.hitpoints, npc.type.stats.hitpoints))
        attacked(npc, source, tick)
        if (npc.hitpoints > 0) {
            // Its own swing this tick wins over the block (guessed).
            if (def.anims.defend >= 0 && npc.effects.anim == null) npc.effects.anim(def.anims.defend)
            if (source != null) retaliate(npc, source, tick)
        } else {
            die(npc, def, source, tick)
        }
        return dealt
    }

    /**
     * A spell [source] cast on [npc] splashed: no hitsplat, health bar or block animation (guessed: the Wiki shows the
     * splash graphic alone), but the NPC is under attack all the same and turns on whoever cast it, as one that is hit
     * does (OSRS Wiki, Splashing: it is how players train Magic on monsters that fight back).
     */
    fun splashed(npc: Npc, source: Player, tick: Long) {
        combat.npcCombat[npc.type] ?: throw IllegalArgumentException("${npc.type.name} can't be fought")
        if (npc.dead) return
        attacked(npc, source, tick)
        retaliate(npc, source, tick)
    }

    /** [npc] is in combat with [source] (null for none) from [tick], and faces them. */
    private fun attacked(npc: Npc, source: Player?, tick: Long) {
        npc.attacker = source
        npc.attackedTick = tick
        npc.holdTicks = IN_COMBAT_TICKS
        if (source != null) npc.face(source)
    }

    /** [npc] was hit by [player]: it turns on them, striking from the next tick (guessed) if it isn't fighting already. */
    private fun retaliate(npc: Npc, player: Player, tick: Long) {
        if (npc.target === player) return
        npc.target = player
        npc.nextAttackTick = maxOf(npc.nextAttackTick, tick + 1)
        npcs.follow(npc, player)
    }

    private fun die(npc: Npc, def: NpcCombatDef, killer: Player?, tick: Long) {
        npc.dead = true
        giveUp(npc)
        if (def.anims.death >= 0) npc.effects.anim(def.anims.death)
        // The body goes before the replacement comes, however short the respawn time (a rat's is 2 ticks).
        val respawnTick = tick + maxOf(def.respawnTicks, DEATH_TICKS)
        dying += Dying(npc, killer, tick + DEATH_TICKS, respawnTick)
        logger.debug { "$npc died; back on tick $respawnTick" }
        npcDied(npc)
    }

    /** [npc] stops fighting whoever it fought and goes back to what it did. */
    private fun giveUp(npc: Npc) {
        if (npc.target == null) return
        npc.target = null
        npcs.stopFollowing(npc)
    }

    /**
     * The NPC phase's combat: last tick's player swings land, every fighting NPC chases and hits its target or gives
     * up, the NPCs whose death is over are taken out with their drops put down, those whose respawn time is up put
     * back, and once a minute everyone heals a point.
     */
    fun process() {
        val tick = currentTick()
        land(tick)
        val iterator = dying.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (tick >= entry.removeTick && npcs[entry.npc.index] === entry.npc) {
                removeNpc(entry.npc)
                // The drops show as the body goes (OSRS Wiki, Drop: loot appears when the monster's death animation ends).
                loot(entry.npc, entry.killer)
            }
            if (tick >= entry.respawnTick) {
                // Never before the body has gone: a respawn no shorter than DEATH_TICKS and the removal above see to it.
                check(npcs[entry.npc.index] !== entry.npc) { "${entry.npc} respawning with its body still there" }
                iterator.remove()
                if (entry.npc.spawn.respawns) spawnNpc(entry.npc.spawn)
            }
        }
        for (npc in npcs.all) {
            val target = npc.target ?: continue
            fightBack(npc, target, tick)
        }
        regeneratePlayers()
        if (tick % REGEN_TICKS == 0L) regenerate()
    }

    private fun fightBack(npc: Npc, player: Player, tick: Long) {
        val def = combat.npcCombat[npc.type] ?: return giveUp(npc)
        if (npc.dead) return
        if (player.loggedOut || player.level != npc.level || player.hitpoints <= 0 || outOfRange(npc, player) || underAttackByAnother(player, npc)) {
            giveUp(npc)
            return
        }
        // Chase's own far limit may have let go of them; a fighting NPC keeps after them while they are in range.
        if (npc.following !== player) npcs.follow(npc, player)
        val size = npc.type.size
        if (!Reach.reachedEntity(collision, npc.level, npc.x, npc.z, size, player.x, player.z, 1, 1)) return
        if (tick < npc.nextAttackTick) return
        npc.nextAttackTick = tick + def.attackSpeed
        if (def.anims.attack >= 0) npc.effects.anim(def.anims.attack)
        npc.face(player)
        val roll = combat.npcAttack(npc, player) ?: return
        val damage = if (CombatMath.rollHits(random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(random, roll.maxHit) else 0
        // The dice are rolled either way, so a prayer doesn't change what comes after (World.random is shared).
        hitPlayer(player, npc, if (protects(player, def.attackType)) 0 else damage, tick)
    }

    /** Whether [player] is further than [CHASE_RANGE] from [npc]'s spawn on either axis. */
    private fun outOfRange(npc: Npc, player: Player): Boolean =
        abs(player.x - npc.spawn.x) > CHASE_RANGE || abs(player.z - npc.spawn.z) > CHASE_RANGE

    /**
     * [player] takes [damage] from [source] (null for none), capped at their hitpoints and 0 under `::god`, on [tick]:
     * the hitsplat and health bar for everyone, their block animation unless they swing this tick, and auto retaliate.
     * At no hitpoints they die; see the class note. Returns the damage dealt.
     */
    fun hitPlayer(player: Player, source: Npc?, damage: Int, tick: Long): Int {
        if (player.hitpoints <= 0 || player.dead) return 0
        val dealt = if (player.god) 0 else damage.coerceIn(0, player.hitpoints)
        player.hitpoints -= dealt
        val from = source?.index ?: -1
        if (dealt > 0) player.effects.hit(from, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER, dealt)
        else player.effects.hit(from, HitsplatType.BLOCK_ME, HitsplatType.BLOCK_OTHER, HitsplatType.BLOCK_OTHER, 0)
        player.effects.headBar(from, HEALTH_BAR, healthBarFill(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS)))
        // The NPC phase comes before the player's own swing, which overwrites this (guessed: the swing wins).
        if (player.effects.anim == null) player.effects.anim(combat.anims(player).defend, 0)
        if (source != null) {
            player.attackedBy = source
            player.attackedTick = tick
        }
        if (player.hitpoints <= 0) {
            died(player)
            return dealt
        }
        redemption(player)
        if (source != null && player.autoRetaliate && player.interaction == null && !player.modalOpen) {
            retaliate(player, source, tick)
        }
        return dealt
    }

    /**
     * [player] takes [damage] of poison or venom ([Poison]) with [hitsplat], capped at their hitpoints and 0 under
     * `::god`: no one hits them, so there is no block animation, no attacker and no retaliation, but it can kill and
     * Redemption counts it (guessed). Returns the damage dealt.
     */
    fun statusHit(player: Player, damage: Int, hitsplat: Int): Int {
        if (player.hitpoints <= 0 || player.dead) return 0
        val dealt = if (player.god) 0 else damage.coerceIn(0, player.hitpoints)
        player.hitpoints -= dealt
        player.effects.hit(-1, hitsplat, hitsplat, hitsplat, dealt)
        player.effects.headBar(-1, HEALTH_BAR, healthBarFill(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS)))
        if (player.hitpoints <= 0) died(player) else redemption(player)
        return dealt
    }

    /**
     * Redemption: hit to under 10% of their Hitpoints level, a 0 included, [player] is healed a quarter of their Prayer
     * level, rounded down, their prayer points go to 0 and every prayer goes off (OSRS Wiki, Redemption). Guessed: that
     * the heal comes on the tick of the hit, with the spotanim and the heal sound on the player.
     */
    private fun redemption(player: Player) {
        if (player.hitpoints * 10 >= player.skills.baseLevel(Skill.HITPOINTS) || !hasPrayer(player, Prayers.REDEMPTION)) return
        val max = player.skills.baseLevel(Skill.HITPOINTS)
        player.hitpoints = minOf(max, player.hitpoints + player.skills.baseLevel(Skill.PRAYER) / 4)
        player.skills.setCurrentLevel(Skill.PRAYER, 0)
        prayersOff(player)
        player.spotanim(REDEMPTION_SPOTANIM)
        sound(player, REDEMPTION_HEAL_SOUND)
        player.effects.headBar(-1, HEALTH_BAR, healthBarFill(player.hitpoints, max))
    }

    /**
     * Retribution, as [player] falls: every NPC they could fight within a tile of them takes up to a quarter of their
     * Prayer level, rounded down, a roll each, 0s included (OSRS Wiki, Retribution). Guessed: the Wiki says only
     * "nearby enemies", so the 3x3 around the player in multi-way combat, and in single-way the NPC that last hit them
     * alone, if it is in that 3x3; the spotanim on the player; the targets' hits on this tick.
     */
    private fun retribution(player: Player, tick: Long) {
        if (!hasPrayer(player, Prayers.RETRIBUTION)) return
        player.spotanim(RETRIBUTION_SPOTANIM)
        val max = player.skills.baseLevel(Skill.PRAYER) / 4
        val targets = if (multi.at(player.level, player.x, player.z)) {
            npcs.all.filter { !it.dead && it.level == player.level && combat.npcCombat[it.type] != null && withinATile(player, it) }.toList()
        } else {
            listOfNotNull(player.attackedBy?.takeIf { !it.dead && npcs[it.index] === it && it.level == player.level && withinATile(player, it) })
        }
        for (npc in targets) hit(npc, player, CombatMath.rollDamage(random, max), tick)
    }

    private fun withinATile(player: Player, npc: Npc): Boolean {
        val size = npc.type.size
        return player.x >= npc.x - 1 && player.x <= npc.x + size && player.z >= npc.z - 1 && player.z <= npc.z + size
    }

    /**
     * Auto retaliate: [player] turns on [npc], which hit them on [tick]. OSRS Wiki, Auto Retaliate: the character
     * retaliates one tick after being attacked, with the attack delay set to the weapon's speed halved and rounded up
     * whatever it was. The fight starts now and the first swing comes with that delay.
     */
    private fun retaliate(player: Player, npc: Npc, tick: Long) {
        if (foughtByAnother(player, npc)) return
        player.interaction = fight(player, npc) ?: return
        player.nextAttackTick = tick + (combat.attackSpeed(player) + 1) / 2
    }

    /** "Oh dear, you are dead!": every fight with the player ends, and [Deaths] has them from here. */
    private fun died(player: Player) {
        message(player, words(player)[CombatLines.DEAD])
        retribution(player, currentTick())
        endFights(player)
        onDeath(player)
    }

    /** Every fight with [player] ends: theirs, and those of the NPCs fighting them. */
    fun endFights(player: Player) {
        player.interaction = null
        stopFacing(player)
        for (npc in npcs.all) if (npc.target === player) giveUp(npc)
        player.attackedBy = null
    }

    /**
     * Each player's hitpoints countdown ([Player.hitpointsRegenTicks]): at 0 a hurt player gets a hitpoint back and it
     * starts again, at [REGEN_TICKS] (OSRS Wiki, Hitpoints: one a minute) or [Prayers.RAPID_HEAL_TICKS] with Rapid Heal.
     * A countdown set earlier in the tick (login, Rapid Heal toggled) counts this tick, so the hitpoint comes that many
     * ticks later; one started again here has had this tick, so it starts one lower.
     * Guessed: the countdown runs from login, and while hitpoints are full.
     */
    private fun regeneratePlayers() {
        for (player in players()) {
            if (player.hitpointsRegenTicks > 0) {
                player.hitpointsRegenTicks--
                continue
            }
            player.hitpointsRegenTicks = (if (hasPrayer(player, Prayers.RAPID_HEAL)) Prayers.RAPID_HEAL_TICKS else REGEN_TICKS) - 1
            val max = player.skills.baseLevel(Skill.HITPOINTS)
            if (player.hitpoints in 1 until max && !player.dead) player.hitpoints++
        }
    }

    /** A hitpoint back for every hurt NPC each [REGEN_TICKS]. Guessed for NPCs: the players' one a minute, on a world clock. */
    private fun regenerate() {
        for (npc in npcs.all) {
            if (!npc.dead && npc.hitpoints < npc.type.stats.hitpoints && combat.npcCombat[npc.type] != null) npc.hitpoints++
        }
    }

    /** The NPCs dead and waiting to come back, for tests and commands. */
    val respawning: Int get() = dying.size

    companion object {
        /** Not a live message: magic and the other ranged weapons come with later pieces. */
        const val MELEE_ONLY = "Only melee, bows and crossbows are in so far; the rest comes later."

        /**
         * Ticks an NPC counts as fighting its last attacker, and stands still, after their last hit; also how long a
         * player counts as under attack by the NPC that hit them last. Guessed; the Wiki gives no number.
         */
        const val IN_COMBAT_TICKS = 8

        /** Ticks a dead NPC's body stays before it vanishes. Guessed; the death animations are a few seconds. */
        const val DEATH_TICKS = 4

        /**
         * How far from its spawn, on either axis, an NPC's target may be before it gives up: the Wiki's retreat max
         * range (7) plus its interaction range (11). Guessed that chasing uses the same numbers.
         */
        const val CHASE_RANGE = Npcs.MAX_RANGE + 11

        /** Ticks between hitpoints coming back: one a minute (OSRS Wiki, Hitpoints). */
        const val REGEN_TICKS = 100

        /** redemption_spotanim and retribution_spotanim in the 241 cache; the redemption_heal sound (OSRS Wiki, Redemption). */
        const val REDEMPTION_SPOTANIM = 436
        const val RETRIBUTION_SPOTANIM = 437
        const val REDEMPTION_HEAL_SOUND = 2681

        /** The headbar NPCs and players show their health on: id 0, 30 pixels wide (cache config 33, rev 241). */
        const val HEALTH_BAR = 0
        const val HEALTH_BAR_WIDTH = 30

        /** Pixels of the health bar for [hitpoints] of [max]: at least one while alive, none when dead. */
        fun healthBarFill(hitpoints: Int, max: Int): Int {
            if (hitpoints <= 0 || max <= 0) return 0
            return (hitpoints * HEALTH_BAR_WIDTH / max).coerceIn(1, HEALTH_BAR_WIDTH)
        }
    }
}

/** The client's hitsplat ids (RuneLite's HitsplatID): the red damage splat and the blue block, each for the fighters and for onlookers. */
object HitsplatType {
    const val BLOCK_ME = 12
    const val BLOCK_OTHER = 13
    const val DAMAGE_ME = 16
    const val DAMAGE_OTHER = 17
}
