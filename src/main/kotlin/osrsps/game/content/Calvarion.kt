package osrsps.game.content

import kotlin.math.abs
import kotlin.math.max
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.map.Tile

/** Calvar'ion's overhead shouts (keys only; the words are in data/dialogue.txt, from Transcript:Calvar'ion). */
object CalvarionLines : Lines("calvarion", "Transcript:Calvar'ion") {
    private fun many(key: String, count: Int, about: String) = (1..count).map { line("${key}_$it", about) }

    val SHOUTS = many("shout", 19, "one of his shouts as he casts his lightning, normal form")
    val BASH = many("bash", 8, "one of his shouts as he bashes with his shield, normal form")
    val HOUNDS = many("hounds", 6, "one of his shouts as he calls his hellhounds, normal form")
    val HOUNDS_DEAD = many("hounds_dead", 4, "one of his shouts once his hellhounds are dead, normal form")
    val AGAIN = line("again", "his shout on reaching 0 hitpoints in his normal form")
    val ENRAGED_SHOUTS = many("enraged_shout", 19, "one of his shouts as he casts his lightning, enraged (the normal one in capitals)")
    val ENRAGED_BASH = many("enraged_bash", 8, "one of his shouts as he bashes with his shield, enraged")
    val ENRAGED_HOUNDS = many("enraged_hounds", 6, "one of his shouts as he calls his hellhounds, enraged")
    val ENRAGED_HOUNDS_DEAD = many("enraged_hounds_dead", 4, "one of his shouts once his hellhounds are dead, enraged")
    val DYING = many("dying", 6, "one of his shouts as his enraged form dies")
}

/**
 * Vet'ion's fight and Calvar'ion's, its weaker copy (Wilderness step W4.9; the Wiki's Calvar'ion, its Strategies page,
 * its transcript and Vet'ion's Strategies page, which the Calvar'ion page says it is "identical" to, read 2026-11-15).
 *
 * - **Lightning**, his attack: bolts strike tiles about the arena, each hurting a 3 by 3 area, the struck tile itself the
 *   most, after the tiles glow. Alone, one bolt lands on or beside the player and four on random tiles; in a group, one
 *   for each player, at most seven. No prayer stops it.
 * - **Shield bash**, only in melee distance: shadows warn of the area, one tile thick around and under him or two thick
 *   on one side; caught in it, up to [Config.bashMax] and no attacking for 4.8 seconds (8 ticks).
 * - **Hellhounds**: at half his hitpoints in each form, two hellhounds, which make him immune while either lives.
 * - **Enraged**: at 0 hitpoints in his normal form he shouts, strikes a ring of lightning round himself, and comes back
 *   as his enraged form with full hitpoints, attacking faster; not killed within 5 minutes, he goes back to his normal
 *   form.
 *
 * GUESSED until recorded: a bolt's direct hit up to the form's max and the eight tiles round it up to half that; the
 * glow's [LIGHTNING_WARN] ticks and graphics (fx_vetion_attack_magic and its impact, by the form); the random bolts
 * within [ARENA_SPAN] of him; the bash one attack in [BASH_ODDS] when he can reach, its [BASH_WARN] ticks and shadows
 * (fx_vetion_attack_melee), either shape at even odds; the enraged ring 2 to 3 tiles out from him; where the hounds come
 * in (beside him, west and east) and a hound let go after [HOUND_IDLE_TICKS] without a swing; the enraged form's max
 * hit (the Wiki says only that it is higher); a shout with every attack.
 * Left out: his defence drained and restored as he enrages, singles-plus combat (the arena is single-way).
 */
class SkeletalLord(val config: Config) {
    class Config(
        val name: String,
        val normal: Int,
        val enraged: Int,
        val hound: Int,
        val greaterHound: Int,
        val maxHit: Int,
        val enragedMaxHit: Int,
        val bashMax: Int,
        val speed: Int,
        val enragedSpeed: Int,
        val inArena: (Int, Int) -> Boolean,
        val shouts: List<Line>,
        val bash: List<Line>,
        val hounds: List<Line>,
        val houndsDead: List<Line>,
        val again: Line,
        val enragedShouts: List<Line>,
        val enragedBash: List<Line>,
        val enragedHounds: List<Line>,
        val enragedHoundsDead: List<Line>,
        val dying: List<Line>,
    )

    /** One fight's state, by boss. */
    private class Fight {
        var enraged = false
        /** Whether this form has called its hounds yet. */
        var houndsCalled = false
        val hounds = ArrayList<Npc>()
        /** Each hound's next attack tick as last seen, and since when: one that never swings is let go. */
        val houndSwings = HashMap<Npc, kotlin.Pair<Long, Long>>()
    }

    private val fights = java.util.WeakHashMap<Npc, Fight>()

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[config.normal] ?: return null
        return EncounterDef(
            config.name, type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = {
                // Unleashed as the other Wilderness bosses are, so a player far from his spawn can't hold him off (exploit
                // review round 45, R45C-1).
                boss.unleashed = true
                boss.holdsAtZero = true
                holdForHounds()
            })),
            scriptedAttacks = true,
        )
    }

    /** Whether [npc] is one of this lord's forms with a hound of his alive, so he takes nothing. */
    fun shielded(npc: Npc): Boolean = fights[npc]?.hounds?.any { !it.dead && !it.hidden } == true

    fun hounds(npc: Npc): List<Npc> = fights[npc]?.hounds.orEmpty()

    private fun EncounterContext.shout(lines: List<Line>) = boss.effects.say(world.dialogueText[lines[roll(0, lines.size - 1)]])

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(boss) { Fight() }
        val hadHounds = fight.hounds.isNotEmpty()
        letGoIdleHounds(fight)
        fight.hounds.removeAll { it.dead || it.hidden || world.npcs.all.none { npc -> npc === it } }
        if (hadHounds && fight.hounds.isEmpty()) {
            shout(if (fight.enraged) config.enragedHoundsDead else config.houndsDead)
            log("his hounds are dead")
        }
        if (!fight.enraged && boss.hitpoints <= 0) return enrage(fight)
        // The transformation runs out on its own after [ENRAGED_TICKS]; his normal form is back.
        if (fight.enraged && boss.transformedTo == null) calmDown(fight)
        if (!fight.houndsCalled && boss.hitpoints <= boss.maxHitpoints / 2) callHounds(fight)

        val target = heldTarget()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val reaches = world.meleeCombat.reaches(boss, target)
        if (reaches) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        if (world.tick < boss.nextAttackTick) return
        boss.nextAttackTick = world.tick + if (fight.enraged) config.enragedSpeed else config.speed
        if (reaches && roll(1, BASH_ODDS) == 1) bash(fight, target) else lightning(fight, target)
    }

    private fun EncounterContext.lightning(fight: Fight, target: Player) {
        shout(if (fight.enraged) config.enragedShouts else config.shouts)
        boss.effects.anim(if (fight.enraged) ANIM_MAGIC_ENRAGED else ANIM_MAGIC)
        val centres = ArrayList<Tile>()
        val here = players.filter { !it.dead && it.level == boss.level }
        if (here.size <= 1) {
            centres += near(Tile(target.x, target.z))
            repeat(SOLO_RANDOM_BOLTS) { randomTile()?.let(centres::add) }
        } else {
            // Seven picked at random from those here, not the first seven to log in (exploit review round 45, R45C-4).
            val pool = here.toMutableList()
            repeat(minOf(MAX_GROUP_BOLTS, pool.size)) {
                val player = pool.removeAt(roll(0, pool.size - 1))
                centres += near(Tile(player.x, player.z))
            }
        }
        strike(fight, centres)
        log("lightning on ${centres.joinToString(" ") { "${it.x},${it.z}" }}")
    }

    /** A bolt on each of [centres]: the tile itself up to the form's max, the eight round it up to half that. */
    private fun EncounterContext.strike(fight: Fight, centres: Collection<Tile>) {
        val max = if (fight.enraged) config.enragedMaxHit else config.maxHit
        val warn = if (fight.enraged) SPOT_WARN_ENRAGED else SPOT_WARN
        val impact = if (fight.enraged) SPOT_IMPACT_ENRAGED else SPOT_IMPACT
        for (centre in centres) {
            hazard(listOf(centre), warning = warn, warnTicks = LIGHTNING_WARN, ticks = 1, minDamage = 0, maxDamage = max, spotanim = impact)
            val ring = (-1..1).flatMap { dx -> (-1..1).map { dz -> Tile(centre.x + dx, centre.z + dz) } }.filter { it != centre }
            hazard(ring, warning = warn, warnTicks = LIGHTNING_WARN, ticks = 1, minDamage = 0, maxDamage = max / 2)
        }
    }

    /** The shield bash: shadows on its area, then up to [Config.bashMax] and no attacking for 8 ticks for whoever is in it. */
    private fun EncounterContext.bash(fight: Fight, target: Player) {
        shout(if (fight.enraged) config.enragedBash else config.bash)
        boss.effects.anim(if (fight.enraged) ANIM_MELEE_ENRAGED else ANIM_MELEE)
        val size = boss.type.size
        val area = HashSet<Tile>()
        if (roll(1, 2) == 1) {
            for (x in boss.x - 1..boss.x + size) for (z in boss.z - 1..boss.z + size) area += Tile(x, z)
        } else {
            val dx = Integer.signum(target.x - (boss.x + size / 2)).takeIf { abs(target.x - (boss.x + size / 2)) > size / 2 } ?: 0
            val dz = Integer.signum(target.z - (boss.z + size / 2)).takeIf { abs(target.z - (boss.z + size / 2)) > size / 2 } ?: 0
            for (x in boss.x until boss.x + size) for (z in boss.z until boss.z + size) area += Tile(x, z)
            for (step in 1..2) {
                for (i in -1..size) {
                    when {
                        dx != 0 && dz == 0 -> area += Tile(if (dx > 0) boss.x + size - 1 + step else boss.x - step, boss.z + i)
                        dz != 0 && dx == 0 -> area += Tile(boss.x + i, if (dz > 0) boss.z + size - 1 + step else boss.z - step)
                        else -> area += Tile(boss.x + size / 2 + dx * (size / 2 + step) + i * 0, boss.z + size / 2 + dz * (size / 2 + step))
                    }
                }
                if (dx != 0 && dz != 0) for (a in 0..step) for (b in 0..step) {
                    area += Tile(boss.x + size / 2 + dx * (size / 2 + a), boss.z + size / 2 + dz * (size / 2 + b))
                }
            }
        }
        for (at in area) world.zones.mapAnim(at.x, at.z, boss.level, SPOT_BASH)
        log("bash on ${area.size} tiles")
        after(BASH_WARN) {
            for (player in players.toList()) {
                if (player.dead || player.level != boss.level || Tile(player.x, player.z) !in area) continue
                val damage = roll(0, config.bashMax)
                hit(player, damage)
                // As a lock, so a retaliation in the meantime can't bring the next attack sooner (Codex review on #818).
                player.attackLockedUntil = max(player.attackLockedUntil, world.tick + BASH_STUN_TICKS)
                player.nextAttackTick = max(player.nextAttackTick, player.attackLockedUntil)
                log("bash hits ${player.username} for $damage")
            }
        }
    }

    /**
     * No hit takes a form from above half its hitpoints past half before its hounds come: a big hit, or several in a
     * tick, would otherwise skip them (Codex review on #818). Inferred: the Wiki says each form calls them at half.
     */
    private fun EncounterContext.holdForHounds() {
        boss.holdsAt = boss.maxHitpoints / 2
    }

    private fun EncounterContext.callHounds(fight: Fight) {
        fight.houndsCalled = true
        boss.holdsAt = 0
        shout(if (fight.enraged) config.enragedHounds else config.hounds)
        val type = world.npcTypes[if (fight.enraged) config.greaterHound else config.hound] ?: return
        // Beside his west and east sides where a step from under him reaches, inside his tomb, so neither lands behind a wall
        // and leaves him immune (exploit review round 47, R47B-1); with only one side open, both there.
        val open = world.npcs.openBeside(boss).filter { config.inArena(it.x, it.z) }
        val west = open.minByOrNull { it.x } ?: return log("no room for his hounds")
        val east = open.maxByOrNull { it.x } ?: west
        for (at in listOf(west, east)) {
            val hound = spawnAdd(type, at.x, at.z) ?: continue
            if (!world.npcs.canStand(hound, at.x, at.z)) {
                world.removeNpc(hound)
                continue
            }
            fight.hounds += hound
            fight.houndSwings[hound] = hound.nextAttackTick to world.tick
            (boss.target ?: players.firstOrNull())?.let { world.meleeCombat.aggress(hound, it, world.tick) }
        }
        log("calls ${fight.hounds.size} hounds")
    }

    /**
     * A hound that hasn't swung for [HOUND_IDLE_TICKS] is let go, so one stuck where it can reach no one doesn't keep him
     * immune for good (exploit review round 47, R47B-1).
     */
    private fun EncounterContext.letGoIdleHounds(fight: Fight) {
        for (hound in fight.hounds.toList()) {
            if (hound.dead || hound.hidden) continue
            val seen = fight.houndSwings[hound]
            if (seen == null || seen.first != hound.nextAttackTick) {
                fight.houndSwings[hound] = hound.nextAttackTick to world.tick
            } else if (world.tick - seen.second >= HOUND_IDLE_TICKS) {
                world.removeNpc(hound)
                fight.houndSwings.remove(hound)
                log("lets go an idle hound")
            }
        }
        fight.houndSwings.keys.retainAll(fight.hounds.toSet())
    }

    /** At 0 in his normal form: a ring of lightning round him, and back as his enraged form with full hitpoints. */
    private fun EncounterContext.enrage(fight: Fight) {
        shout(listOf(config.again))
        boss.effects.anim(ANIM_ENRAGE)
        val size = boss.type.size
        val ring = ArrayList<Tile>()
        for (x in boss.x - 3..boss.x + size + 2) for (z in boss.z - 3..boss.z + size + 2) {
            val out = maxOf(boss.x - x, x - (boss.x + size - 1), boss.z - z, z - (boss.z + size - 1))
            if (out in 2..3 && (x + z) % 2 == 0) ring += Tile(x, z)
        }
        fight.enraged = true
        fight.houndsCalled = false
        strike(fight, ring)
        world.npcTypes[config.enraged]?.let { world.npcs.transform(boss, it, ENRAGED_TICKS) }
        // The moment it runs out, at the start of a tick before its hits land, he is his normal form at full hitpoints,
        // held at 0 and at half for his hounds, so that tick's hits count against him (Codex review on #818);
        // [calmDown] does the rest in his fight's tick without touching his hitpoints again.
        val lord = boss
        lord.transformEnded = {
            lord.hitpoints = lord.maxHitpoints
            fullBar(lord)
            lord.holdsAtZero = true
            lord.holdsAt = lord.maxHitpoints / 2
            lord.transformEnded = null
        }
        boss.hitpoints = boss.maxHitpoints
        // The hit that emptied his normal form sent an empty bar; the new form's is full (Codex review on #818).
        fullBar(boss)
        boss.holdsAtZero = false
        holdForHounds()
        log("enrages")
    }

    /** Shows [npc]'s health bar full again, as a hit's would after a heal. */
    private fun fullBar(npc: Npc) =
        npc.effects.headBar(-1, osrsps.game.combat.MeleeCombat.HEALTH_BAR, osrsps.game.combat.MeleeCombat.healthBarFill(npc.hitpoints, npc.maxHitpoints))

    /** Not killed within 5 minutes enraged: his normal form again; its hitpoints were restored as the transformation ended. */
    private fun EncounterContext.calmDown(fight: Fight) {
        fight.enraged = false
        fight.houndsCalled = false
        log("calms down")
    }

    private fun EncounterContext.near(at: Tile): Tile = Tile(at.x + roll(-1, 1), at.z + roll(-1, 1))

    private fun EncounterContext.randomTile(): Tile? {
        val cx = boss.x + boss.type.size / 2
        val cz = boss.z + boss.type.size / 2
        repeat(20) {
            val at = Tile(cx + roll(-ARENA_SPAN, ARENA_SPAN), cz + roll(-ARENA_SPAN, ARENA_SPAN))
            if (config.inArena(at.x, at.z)) return at
        }
        return null
    }

    /** His last words as his enraged form dies. */
    fun died(world: World, npc: Npc) {
        npc.transformEnded = null
        val fight = fights.remove(npc) ?: return
        for (hound in fight.hounds) if (!hound.dead) world.removeNpc(hound)
        if (fight.enraged) npc.effects.say(world.dialogueText[config.dying[world.random.nextInt(config.dying.size)]])
    }

    fun register(world: World, boss: WildernessBoss) {
        val def = def(world) ?: return
        world.wildernessBosses.register(boss)
        val immune = world.combat.immune
        world.combat.immune = { npc -> ((npc.spawn.type.id == config.normal) && shielded(npc)) || immune(npc) }
        world.npcScripts.onDeath(listOf(config.normal, config.enraged)) { npc -> died(world, npc) }
        world.npcScripts.onTick(listOf(config.normal)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, config.inArena)
        }
    }

    companion object {
        const val ENRAGED_TICKS = 500
        const val SOLO_RANDOM_BOLTS = 4
        const val MAX_GROUP_BOLTS = 7
        const val LIGHTNING_WARN = 3
        const val ARENA_SPAN = 7
        const val HOUND_IDLE_TICKS = 50L
        const val BASH_ODDS = 3
        const val BASH_WARN = 2
        const val BASH_STUN_TICKS = 8

        const val ANIM_MAGIC = 9969
        const val ANIM_MAGIC_ENRAGED = 9970
        const val ANIM_MELEE = 9971
        const val ANIM_MELEE_ENRAGED = 9972
        const val ANIM_ENRAGE = 9978
        const val SPOT_WARN = 2344
        const val SPOT_WARN_ENRAGED = 2345
        const val SPOT_IMPACT = 2346
        const val SPOT_IMPACT_ENRAGED = 2347
        const val SPOT_BASH = 2348
    }
}

/**
 * Calvar'ion and the Skeletal Tomb (Wilderness step W4.9): `vetion_single` 11993 and his enraged form `vetion_2_single`
 * 11994, size 3, combat 264, 150 hitpoints a form, every 6 ticks (5 enraged), max hit 26; his hellhounds
 * `vetion_hellhound_jnr_singles` 12107 and, enraged, `vetion_hellhound_snr_singles` 12108. His fight is [SkeletalLord]'s.
 *
 * The tomb: Enter on its way in (`wild_vetion_singles_entrance01` 46996 at 3180,3683, level 20, in the Graveyard of
 * Shadows) takes the lairs' fee and drops the player into the tomb, map square 29_180 on level 1, at level 21; Peek and
 * Check-Fee as at every lair ([WildernessLairLines]); its way out (`wild_vetion_exit01` 46925 at 1886,11534) puts them
 * back outside. Live asks for the hard Wilderness Diary or a Vet'ion task; ours is open (Sam, 9 Nov). His drops are in
 * npc-drops.json and his kill count in `total_calvarion_kills`; he is a lair boss, so his top damage dealer gets 10,000
 * off the fee.
 *
 * GUESSED: where Enter and Exit put the player (the tomb's north alcove, and south of the way in) and where he spawns
 * (the tomb's middle); his enraged max hit [ENRAGED_MAX_HIT].
 */
object Calvarion {
    const val BOSS = 11993
    const val ENRAGED = 11994
    const val HOUND = 12107
    const val GREATER_HOUND = 12108
    const val KILL_COUNT_VARP = 3763

    const val ENTRANCE = 46996
    const val EXIT = 46925
    const val ENTER = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20
    const val TOMB_LEVEL = 1

    val ARRIVAL = Tile(1887, 11553)
    val OUTSIDE = Tile(3180, 3682)
    val SPAWN = Tile(1886, 11545)

    const val MAX_HIT = 26
    const val ENRAGED_MAX_HIT = 30

    /** The tomb: its map square, on level 1. */
    fun inTomb(x: Int, z: Int): Boolean = x shr 6 == 29 && z shr 6 == 180

    val FIGHT = SkeletalLord(
        SkeletalLord.Config(
            "calvarion", BOSS, ENRAGED, HOUND, GREATER_HOUND, MAX_HIT, ENRAGED_MAX_HIT, bashMax = MAX_HIT, speed = 6, enragedSpeed = 5,
            inArena = ::inTomb,
            shouts = CalvarionLines.SHOUTS, bash = CalvarionLines.BASH, hounds = CalvarionLines.HOUNDS,
            houndsDead = CalvarionLines.HOUNDS_DEAD, again = CalvarionLines.AGAIN,
            enragedShouts = CalvarionLines.ENRAGED_SHOUTS, enragedBash = CalvarionLines.ENRAGED_BASH,
            enragedHounds = CalvarionLines.ENRAGED_HOUNDS, enragedHoundsDead = CalvarionLines.ENRAGED_HOUNDS_DEAD,
            dying = CalvarionLines.DYING,
        ),
    )
}

fun registerCalvarion(world: World) {
    with(Calvarion) {
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, TOMB_LEVEL)
        }
        world.handlers.onLoc(ENTRANCE, PEEK) {
            arriveDelay()
            if (KILL_COUNT_VARP < player.vars.types.varpCount && player.vars[KILL_COUNT_VARP] < PEEK_KILLS) {
                return@onLoc world.message(player, WildernessLairLines.PEEK_KILLS)
            }
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inTomb(it.x, it.z) && it.level == TOMB_LEVEL })
        }
        world.handlers.onLoc(ENTRANCE, CHECK_FEE) {
            arriveDelay()
            // A fee discounted to nothing lets them in unpaid, so it reads as paid, as Artio's does (Codex review on #818).
            if (WildernessLairFee.paid(player) || WildernessLairFee.fee(player) == 0) world.message(player, WildernessLairLines.FEE_PAID)
            else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
        }
        // Vet'ion's Rest has the same way out; his step gives it its own place to put the player.
        world.handlers.onLoc(EXIT, 1) { op ->
            val loc = op.loc!!
            if (!inTomb(loc.x, loc.z)) return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        }
        FIGHT.register(world, WildernessBoss("calvarion", setOf(BOSS, ENRAGED), KILL_COUNT_VARP, lair = true))
    }
}
