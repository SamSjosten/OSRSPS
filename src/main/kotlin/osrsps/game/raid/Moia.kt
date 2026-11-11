package osrsps.game.raid

import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.combat.Poison
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.map.Tile

/**
 * Moia (raid step 19), the third row's west boss, in her own room: a 3 by 3 necromancer who throws daggers from afar,
 * teleports about her room, coughs a sickness that spreads, and is guarded by her Undead binders.
 *
 * - Every [ATTACK_SPEED] ticks an action, at a player in her fight picked at random, in a cycle of [CYCLE]: a thrown
 *   dagger ([throwDagger]), a second, her cough ([cough]), a third, and a teleport ([teleport]).
 * - A dagger is her ranged attack, up to [RANGED_MAX] (the npc-combat file's), Protect from Missiles blocking it.
 * - Her teleport: she goes from sight where she stands and is seen [TELEPORT_LANDS] ticks later on another of [SPOTS],
 *   then throws a magic dagger, up to [MAGIC_MAX], Protect from Magic blocking it, and [PAIR_GAP] ticks on a ranged one
 *   at the same player, so the prayer must be switched between the two.
 * - Her cough: a projectile that lands on the player [COUGH_LANDS] ticks on and infects them ([infect]). An infection
 *   hurts and drains Prayer every [INFECTION_EVERY] ticks, by [INFECTION_START] and then [INFECTION_RAMP] more each time,
 *   [INFECTION_HITS] times. Each time it hurts it spreads to every player and binder next to the infected one. Immune to
 *   poison (an antipoison's or a sanfew serum's dose), a player takes half of both.
 * - Her binders ([binders]): [RING] slots round the middle of her room, every slot but one taken as her fight begins.
 *   Every [SHUFFLE_EVERY] ticks they walk a slot on round the ring, the empty one with them. Each hits a player within
 *   [BINDER_REACH] tiles every [BINDER_SPEED] ticks, Protect from Melee blocking it. A binder that dies rises again in
 *   its slot [BINDER_RETURN] ticks later.
 * - At [ENRAGE_PERCENT] of her hitpoints her enrage ([enrage]): her binders go, she goes back to the middle of her room
 *   and teleports no more, and four binders rise round her in a diamond and give her their power for [CHARGE_TICKS]
 *   ticks ([charge]). Every one of them killed before then breaks the charge; otherwise it bursts on everyone in her
 *   fight for [BURST_MIN] to [BURST_MAX], no prayer helping, and the binders left go. A fresh diamond rises
 *   [CHARGE_GAP] ticks after either. Her daggers and cough go on through it.
 *
 * From the OSRS Wiki (Moia and Undead binder, read 2026-11-11): her stats and bonuses, 3 by 3, her hitpoints by party
 * size (1,900 for one or two), the 5-tick attack speed, ranged max hit 32 and magic max hit 44, the 80% air weakness,
 * the 60% cap on her Defence's drain; a binder's stats, 1 by 1, 95 hitpoints, its 5-tick attack, "Move around the arena
 * periodically and attack nearby players". From The Fractured Archive/Strategies' contents (read 2026-11-11): her
 * sections are Undead Binders, Magic Attack, Cough and Enrage; the cough is "rapidly ramping poison damage" and a
 * prayer drain, which a sanfew serum helps with. From guides (osrstoolkit and ironman.guide, read 2026-11-11): she
 * starts out of reach of melee; her binders start evenly spaced with one opening, shuffle, and hit anyone within two
 * tiles; mostly fast ranged attacks; after a teleport a slow magic projectile, then a faster ranged one, to switch
 * prayers between; the cough infecting one player and spreading to those and binders next to them; below a third of
 * her hitpoints binders in a diamond and a charge over the arena that hurts the whole party a lot unless every binder
 * dies first, repeating with fresh ones. From the cache: the ids below, by name, and her room's map: the round floor
 * of square 63,88 (about 4046-4081 x 5646-5680), the walled floor in its middle (4059-4067 x 5659-5667, open at its
 * west, north and south), and the way in east of it (x 4082 on).
 *
 * No capture of her fight exists (the tsps capture notes have none), nor a recording of Sam's: everything about her
 * timings, places and damage past the Wiki's max hits is guessed (each also in the README): where she stands (the
 * middle of the walled floor) and the spots she teleports to; her action every 5 ticks and its cycle; her target picked
 * at random each time; the teleport's 2 ticks out of sight; the binders' ring round the walled floor's middle, its
 * slots, their walk a slot on every 16 ticks, their 2-tile reach and max hit, their rising again 30 ticks after they
 * die; every number of the cough and its spread, and the halving for a player immune to poison (a Serpentine helm
 * isn't built); the enrage at a third, her going back to the middle, the diamond 4 tiles out, the 30-tick charge, the
 * burst's damage, and 20 ticks before a fresh diamond; her daggers and cough going on through the enrage; the
 * binders' empowering projectiles (`moia_minion_empower_*`) and the burst's spotanim (`moia_chant_player_explode`)
 * used for the charge, by their names. Not in: the binders pushing out a crowd under one (28 October 2026), and her
 * shadow shield (`tfa_moia_shadow_shield_amount`, varbit 16418), whose use isn't known.
 */
object Moia {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "moia"

    /** `tfa_moia`. */
    const val BOSS = 16667

    /** `tfa_moia_minion`: an Undead binder. */
    const val BINDER = 16670

    /** Her size, the Wiki's 3 by 3. */
    const val SIZE = 3

    /** moia_projectile_attack01, a throw; moia_chant01, her enrage's chant; moia_teleport01, a teleport's start. */
    const val ANIM_THROW = 14785
    const val ANIM_CHANT = 14787
    const val ANIM_TELEPORT = 14788

    /** moia_minion_attack, a binder's hit. */
    const val ANIM_BINDER_ATTACK = 14791

    /** moia_dagger_magic and moia_dagger, her magic and ranged daggers; moia_dagger_magic_impact, the magic one landing. */
    const val SPOT_DAGGER_MAGIC = 4213
    const val SPOT_DAGGER = 4214
    const val SPOT_MAGIC_IMPACT = 4216

    /** moia_infect_spotanim on an infected player; moia_infect_attack_projanim, her cough. */
    const val SPOT_INFECTED = 4217
    const val SPOT_COUGH = 4218

    /** moia_teleport_spotanim where she goes from, moia_teleport_in_spotanim where she comes to. */
    const val SPOT_TELEPORT = 4219
    const val SPOT_TELEPORT_IN = 4220

    /** The binders' power going to her: out of a binder, the projectile, into her. */
    const val SPOT_EMPOWER_OUT = 4222
    const val SPOT_EMPOWER = 4223
    const val SPOT_EMPOWER_IN = 4224

    /** moia_chant_player_explode, the charge bursting on a player. */
    const val SPOT_BURST = 4225

    /** Her south-west tile as her fight begins: the middle of the walled floor in her room's middle. Guessed. */
    const val X = 4062
    const val Z = 5662

    /** Her room's round floor, her arena; the way in is east of it (cache map, square 63,88). */
    const val ARENA_MIN_X = 4046
    const val ARENA_MAX_X = 4081
    const val ARENA_MIN_Z = 5646
    const val ARENA_MAX_Z = 5680

    /** A tile on her arena's east edge, by the way in: where the Navigator comes once she's dead. Guessed. */
    const val NAVIGATOR_X = 4077
    const val NAVIGATOR_Z = 5663

    /** The Wiki's attack speed: an action every this many ticks. */
    const val ATTACK_SPEED = 5

    /** Her actions, in turn, over and over. Guessed. */
    enum class Action { DAGGER, COUGH, TELEPORT }

    val CYCLE = listOf(Action.DAGGER, Action.DAGGER, Action.COUGH, Action.DAGGER, Action.TELEPORT)

    /** The Wiki's max hits. A ranged dagger's is the npc-combat file's. */
    const val RANGED_MAX = 32
    const val MAGIC_MAX = 44

    /**
     * Her south-west tile at each place she teleports to, as offsets from [X], [Z]: the middle, then north, south, west
     * and east of the walled floor, all on her room's open floor in the cache. Guessed.
     */
    val SPOTS = listOf(Tile(0, 0), Tile(0, 9), Tile(0, -9), Tile(-15, 0), Tile(9, 0))

    /** She comes back into sight this many ticks after a teleport begins; the magic dagger the tick after that. Guessed. */
    const val TELEPORT_LANDS = 2

    /** The ranged dagger after a teleport's magic one, this many ticks on. Guessed. */
    const val PAIR_GAP = 2

    /** Her cough lands this many ticks after it's thrown. Guessed. */
    const val COUGH_LANDS = 3

    /** An infection: a hit every this many ticks, this many of them, the first this much and each this much more. Guessed. */
    const val INFECTION_EVERY = 3
    const val INFECTION_HITS = 6
    const val INFECTION_START = 2
    const val INFECTION_RAMP = 2

    /**
     * The binders' slots round the walled floor's middle (4063,5663), as offsets from her starting middle: west, then
     * round by north, east and south. The walled floor's openings are on three of them. Guessed.
     */
    val RING = listOf(Tile(-4, 0), Tile(-3, 3), Tile(0, 4), Tile(3, 3), Tile(4, 0), Tile(3, -3), Tile(0, -4), Tile(-3, -3))

    /** The binders walk a slot on every this many ticks. Guessed. */
    const val SHUFFLE_EVERY = 16

    /** A binder hits a player this near (the guides'), every this many ticks (the Wiki's). */
    const val BINDER_REACH = 2
    const val BINDER_SPEED = 5

    /** A binder that dies rises again in its slot this many ticks later, before her enrage. Guessed. */
    const val BINDER_RETURN = 30

    /** Her enrage begins at this share of her hitpoints, in percent (the guides' third). */
    const val ENRAGE_PERCENT = 33

    /** The enrage's diamond of binders: this far from her middle, north, east, south and west. Guessed. */
    const val DIAMOND = 4

    /** A charge fills in this many ticks, the binders sending her their power every this many. Guessed. */
    const val CHARGE_TICKS = 30
    const val EMPOWER_EVERY = 5

    /** A fresh diamond this many ticks after the last charge burst or broke. Guessed. */
    const val CHARGE_GAP = 20

    /** A charge's burst on everyone in her fight, no prayer helping. Guessed (the guides' "massive damage"). */
    const val BURST_MIN = 50
    const val BURST_MAX = 70

    /** The thrown dagger and the magic dagger, as she throws them (her throw's animation for both: guessed). */
    val DAGGER = NpcRangedAttack(anim = ANIM_THROW, projectile = SPOT_DAGGER)
    val MAGIC_DAGGER = NpcRangedAttack(anim = ANIM_THROW, projectile = SPOT_DAGGER_MAGIC, impact = SPOT_MAGIC_IMPACT, maxHit = MAGIC_MAX)

    /** The fight, or null when the cache hasn't Moia (a test world's). Without the binder's type, no binders rise. */
    fun def(types: NpcTypes): EncounterDef? {
        val type = types[BOSS] ?: return null
        val binder = types[BINDER]
        return EncounterDef(
            NAME, type,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(CycleStep(1) { tick(binder) }),
                    onStart = {
                        // She never walks: she stands, or teleports.
                        boss.unleashed = true
                        world.npcs.stopFollowing(boss)
                        state.nextAction = ATTACK_SPEED.toLong()
                        if (binder != null) ring(binder)
                        log("Moia at ${boss.x},${boss.z}; ${state.slots.count { it.value != null }} binders round the ring")
                    },
                    endsAtPercent = ENRAGE_PERCENT,
                ),
                PhaseDef(
                    "enrage",
                    cycle = listOf(CycleStep(1) { enrageTick(binder) }),
                    onStart = { enrage() },
                ),
            ),
            scriptedAttacks = true,
            status = { "${type.name} ${boss.hitpoints}/${boss.maxHitpoints} hp, ${adds.count { it.type.id == BINDER }} binders, ${state.infected.size} infected" },
            // Her binders rise again without end: what hitting them earns is capped (exploit review round 26, R26A-3).
            minions = setOf(BINDER),
        )
    }

    /** Whether ([x], [z]) is on the floor of the arena of a Moia who began with her south-west tile at ([spawnX], [spawnZ]). */
    fun inArena(spawnX: Int, spawnZ: Int, x: Int, z: Int): Boolean =
        x - spawnX in ARENA_MIN_X - X..ARENA_MAX_X - X && z - spawnZ in ARENA_MIN_Z - Z..ARENA_MAX_Z - Z

    /** Whether ([x], [z]) is within a binder's reach of one on ([binderX], [binderZ]). */
    fun inBinderReach(binderX: Int, binderZ: Int, x: Int, z: Int): Boolean =
        max(abs(x - binderX), abs(z - binderZ)) in 1..BINDER_REACH

    /** An infection's [hit]th hit (from 0), before halving. */
    fun infectionDamage(hit: Int): Int = INFECTION_START + INFECTION_RAMP * hit

    /** What a fight keeps between ticks, by boss. */
    private class State {
        /** The phase tick of her next action, and how far round [CYCLE] she is. */
        var nextAction = 0L
        var actions = 0

        /** Which of [SPOTS] she stands on. */
        var spot = 0

        /** The binder in each slot of [RING] (null for the empty one), and how far round the ring they've walked. */
        val slots = LinkedHashMap<Int, Npc?>()
        var turn = 0

        /** The world tick each dead binder's slot rises again on. */
        val returns = LinkedHashMap<Int, Long>()

        /** Each binder's next hit, by world tick. */
        val nextHit = WeakHashMap<Npc, Long>()

        /** The infected, players and binders, with their hits so far and the world tick of their next. */
        val infected = LinkedHashMap<Any, Infection>()

        /** The enrage's diamond, the world tick its charge fills on, and when the next one rises. */
        val diamond = ArrayList<Npc>()
        var chargeFills = Long.MAX_VALUE
        var nextDiamond = Long.MAX_VALUE

        /** Whether her enrage has begun. */
        var enraged = false
    }

    private class Infection(var hits: Int, var next: Long)

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss

    private fun EncounterContext.alive(npc: Npc?): Npc? = npc?.takeIf { !it.dead && world.npcs[it.index] === it }

    /** Her starting middle, as the fight's instance has it. */
    private fun EncounterContext.middle(): Tile = Tile(boss.spawn.x + SIZE / 2, boss.spawn.z + SIZE / 2)

    /** One tick before her enrage: her action when due, the binders, and the infections. */
    private fun EncounterContext.tick(binder: NpcType?) {
        if (phaseTick >= state.nextAction) act(teleports = true)
        if (binder != null) returns(binder)
        turnRing()
        binders()
        infections()
    }

    /** Her action due now, the next of [CYCLE]; with [teleports] false (her enrage) a teleport's turn is a dagger. */
    private fun EncounterContext.act(teleports: Boolean) {
        val target = randomPlayer() ?: return
        val action = CYCLE[state.actions % CYCLE.size]
        state.actions++
        state.nextAction = phaseTick + ATTACK_SPEED
        when {
            action == Action.COUGH -> cough(target)
            action == Action.TELEPORT && teleports -> teleport(target)
            else -> throwDagger(target, magic = false)
        }
    }

    /** A dagger at [player], magic or ranged, landing after its flight as an NPC's shot does. */
    private fun EncounterContext.throwDagger(player: Player, magic: Boolean) {
        boss.face(player)
        world.meleeCombat.fireAt(boss, player, world.tick, if (magic) MAGIC_DAGGER else DAGGER, if (magic) AttackType.MAGIC else AttackType.RANGED)
        log("${if (magic) "a magic" else "a"} dagger at ${player.username}")
    }

    /**
     * She teleports: out of sight where she stands, then [TELEPORT_LANDS] ticks on into sight on another of [SPOTS], and
     * the tick after a magic dagger at [player], and [PAIR_GAP] ticks later a ranged one.
     */
    private fun EncounterContext.teleport(player: Player) {
        boss.effects.anim(ANIM_TELEPORT)
        world.zones.mapAnim(boss.x + SIZE / 2, boss.z + SIZE / 2, boss.level, SPOT_TELEPORT)
        val others = SPOTS.indices.filter { it != state.spot }
        val to = others[roll(0, others.size - 1)]
        log("teleports")
        // Enraged by the time it's due (her enrage begins before the tick's waiting actions), she stays in sight in
        // the middle: hidden then, she'd never be shown again (Codex on #625).
        after(1) { if (!gone() && !state.enraged) world.hide(boss) }
        after(TELEPORT_LANDS) {
            // Enraged meanwhile, she's back in the middle already.
            if (gone() || state.enraged) return@after
            state.spot = to
            val spot = SPOTS[to]
            world.npcs.teleport(boss, boss.spawn.x + spot.x, boss.spawn.z + spot.z)
            world.show(boss)
            world.zones.mapAnim(boss.x + SIZE / 2, boss.z + SIZE / 2, boss.level, SPOT_TELEPORT_IN)
            log("comes to ${boss.x},${boss.z}")
        }
        after(TELEPORT_LANDS + 1) { if (!gone() && players.any { it === player }) throwDagger(player, magic = true) }
        after(TELEPORT_LANDS + 1 + PAIR_GAP) { if (!gone() && players.any { it === player }) throwDagger(player, magic = false) }
        // Her next action waits for the pair.
        state.nextAction = phaseTick + TELEPORT_LANDS + 1 + PAIR_GAP + ATTACK_SPEED
    }

    /** Her cough at [player]: a projectile landing on them [COUGH_LANDS] ticks on, infecting them if they're still in her fight. */
    private fun EncounterContext.cough(player: Player) {
        boss.face(player)
        boss.effects.anim(ANIM_THROW)
        world.zones.projectile(
            Projectile(
                id = SPOT_COUGH, fromX = boss.x + SIZE / 2, fromZ = boss.z + SIZE / 2, level = boss.level, toX = player.x, toZ = player.z,
                startHeight = 80, endHeight = 30, startTime = 0, endTime = COUGH_LANDS * CYCLES_PER_TICK, angle = 16, progress = 64,
                sourceIndex = boss.index + 1, targetIndex = -(player.index + 1),
            ),
        )
        log("coughs at ${player.username}")
        after(COUGH_LANDS) {
            if (gone() || player.dead || players.none { it === player }) return@after
            infect(player)
        }
    }

    /** [who], a player or a binder, infected afresh, unless they are already. */
    private fun EncounterContext.infect(who: Any) {
        if (who in state.infected) return
        state.infected[who] = Infection(0, world.tick + INFECTION_EVERY)
        when (who) {
            is Player -> {
                who.effects.spotanim(0, SPOT_INFECTED, 0, 0)
                log("${who.username} is infected")
            }
            is Npc -> {
                who.effects.spotanim(0, SPOT_INFECTED, 0)
                log("a binder at ${who.x},${who.z} is infected")
            }
        }
    }

    /** Each infection due hurts, drains and spreads; one done with its hits, or whose bearer is gone, ends. */
    private fun EncounterContext.infections() {
        val fighting = players
        for ((who, infection) in state.infected.entries.toList()) {
            if (gone()) return
            val bearer = when (who) {
                is Player -> who.takeIf { !it.dead && fighting.any { p -> p === it } }
                is Npc -> alive(who)
                else -> null
            }
            if (bearer == null) {
                state.infected.remove(who)
                continue
            }
            if (world.tick < infection.next) continue
            val full = infectionDamage(infection.hits)
            infection.hits++
            infection.next = world.tick + INFECTION_EVERY
            val (x, z) = when (bearer) {
                is Player -> bearer.x to bearer.z
                else -> (bearer as Npc).x to bearer.z
            }
            when (bearer) {
                is Player -> {
                    // Guessed: a player immune to poison takes half, as the Wiki says a sanfew serum helps.
                    val damage = if (world.poison.value(bearer) < 0) full / 2 else full
                    val dealt = world.meleeCombat.statusHit(bearer, damage, Poison.POISON_HITSPLAT)
                    val prayer = bearer.skills.currentLevel(Skill.PRAYER)
                    bearer.skills.setCurrentLevel(Skill.PRAYER, max(0, prayer - damage))
                    if (!bearer.dead) bearer.attackedTick = world.tick
                    log("the infection hurts ${bearer.username} for $damage" + (if (dealt != damage) ", $dealt dealt" else "") + ", draining ${prayer - max(0, prayer - damage)} prayer")
                }
                is Npc -> {
                    val dealt = world.meleeCombat.statusHit(bearer, full, Poison.POISON_HITSPLAT)
                    log("the infection hurts a binder for $dealt")
                }
            }
            // It spreads to whoever is next to its bearer as it hurts.
            for (player in fighting) if (player !== bearer && !player.dead && max(abs(player.x - x), abs(player.z - z)) <= 1) infect(player)
            for (npc in adds) if (npc !== bearer && npc.type.id == BINDER && max(abs(npc.x - x), abs(npc.z - z)) <= 1) infect(npc)
            if (infection.hits >= INFECTION_HITS) {
                state.infected.remove(who)
                log("an infection ends")
            }
        }
    }

    /** The binders rise in every slot of [RING] but one, picked at random. */
    private fun EncounterContext.ring(type: NpcType) {
        val open = roll(0, RING.size - 1)
        for (slot in RING.indices) {
            state.slots[slot] = if (slot == open) null else spawnBinder(type, slotTile(slot))
        }
    }

    /** Where slot [slot] of the ring stands now, the binders having walked [State.turn] slots on. */
    private fun EncounterContext.slotTile(slot: Int): Tile {
        val ring = RING[(slot + state.turn) % RING.size]
        val middle = middle()
        return Tile(middle.x + ring.x, middle.z + ring.z)
    }

    private fun EncounterContext.spawnBinder(type: NpcType, at: Tile): Npc? {
        val npc = spawnAdd(type, at.x, at.z) ?: return null
        // They fight only as this fight says, and walk only where it sends them.
        npc.scriptedAttacks = true
        state.nextHit[npc] = world.tick + BINDER_SPEED
        return npc
    }

    /** A dead binder's slot fills again [BINDER_RETURN] ticks after its death is seen. */
    private fun EncounterContext.returns(type: NpcType) {
        for ((slot, npc) in state.slots.entries.toList()) {
            if (npc == null) continue
            if (alive(npc) != null) continue
            state.slots[slot] = null
            state.returns[slot] = world.tick + BINDER_RETURN
            log("a binder falls at ${npc.x},${npc.z}")
        }
        for ((slot, due) in state.returns.entries.toList()) {
            if (world.tick < due) continue
            state.returns.remove(slot)
            state.slots[slot] = spawnBinder(type, slotTile(slot))
            log("a binder rises again")
        }
    }

    /** Every [SHUFFLE_EVERY] ticks the ring turns a slot on; each binder walks a step a tick to its slot. */
    private fun EncounterContext.turnRing() {
        if (phaseTick > 0 && phaseTick % SHUFFLE_EVERY == 0L) {
            state.turn = (state.turn + 1) % RING.size
            log("the binders shuffle")
        }
        for ((slot, npc) in state.slots) {
            val binder = alive(npc) ?: continue
            val to = slotTile(slot)
            if (binder.x != to.x || binder.z != to.z) world.npcs.stepTo(binder, to.x, to.z)
        }
    }

    /** Each binder of the ring hits the nearest player within its reach when its hit is due (a diamond's give her their power instead: guessed). */
    private fun EncounterContext.binders() {
        val fighting = players.filter { !it.dead }
        for (npc in state.slots.values.mapNotNull { alive(it) }.sortedBy { it.index }) {
            if (gone()) return
            val near = fighting.filter { inBinderReach(npc.x, npc.z, it.x, it.z) }
                .minByOrNull { max(abs(it.x - npc.x), abs(it.z - npc.z)) } ?: continue
            npc.face(near)
            if (world.tick < (state.nextHit[npc] ?: 0L)) continue
            state.nextHit[npc] = world.tick + BINDER_SPEED
            world.meleeCombat.swingAt(npc, near, world.tick, ANIM_BINDER_ATTACK)
            log("a binder hits at ${near.username}")
        }
    }

    /** Her enrage begins: the ring's binders go, she goes back to the middle, chants, and the first diamond rises. */
    private fun EncounterContext.enrage() {
        log("enrage at ${boss.hitpoints} hp")
        state.enraged = true
        for (npc in state.slots.values) alive(npc)?.let { world.removeNpc(it) }
        state.slots.clear()
        state.returns.clear()
        if (boss.hidden) world.show(boss)
        if (state.spot != 0 || boss.x != boss.spawn.x || boss.z != boss.spawn.z) {
            state.spot = 0
            world.npcs.teleport(boss, boss.spawn.x, boss.spawn.z)
            world.zones.mapAnim(boss.x + SIZE / 2, boss.z + SIZE / 2, boss.level, SPOT_TELEPORT_IN)
        }
        state.nextDiamond = world.tick
        state.nextAction = phaseTick + ATTACK_SPEED
    }

    /** A tick of her enrage: her daggers and cough, the diamond and its charge, and the infections. */
    private fun EncounterContext.enrageTick(binder: NpcType?) {
        if (phaseTick >= state.nextAction) act(teleports = false)
        if (binder != null && world.tick >= state.nextDiamond) diamond(binder)
        charge()
        infections()
    }

    /** Four binders rise round her, north, east, south and west, and their charge begins. */
    private fun EncounterContext.diamond(type: NpcType) {
        state.nextDiamond = Long.MAX_VALUE
        val middle = Tile(boss.x + SIZE / 2, boss.z + SIZE / 2)
        state.diamond.clear()
        for ((dx, dz) in listOf(0 to DIAMOND, DIAMOND to 0, 0 to -DIAMOND, -DIAMOND to 0)) {
            spawnBinder(type, Tile(middle.x + dx, middle.z + dz))?.let { state.diamond += it }
        }
        boss.effects.anim(ANIM_CHANT)
        state.chargeFills = world.tick + CHARGE_TICKS
        log("${state.diamond.size} binders rise in a diamond; the charge fills on tick ${state.chargeFills}")
    }

    /**
     * The diamond's charge: their power going to her every [EMPOWER_EVERY] ticks; broken once every one of them is dead,
     * bursting on everyone in her fight once it fills.
     */
    private fun EncounterContext.charge() {
        if (state.chargeFills == Long.MAX_VALUE) return
        val standing = state.diamond.mapNotNull { alive(it) }
        if (standing.isEmpty()) {
            state.chargeFills = Long.MAX_VALUE
            state.diamond.clear()
            state.nextDiamond = world.tick + CHARGE_GAP
            log("the charge is broken")
            return
        }
        val left = state.chargeFills - world.tick
        if (left > 0) {
            if (left % EMPOWER_EVERY == 0L) for (npc in standing) empower(npc)
            return
        }
        state.chargeFills = Long.MAX_VALUE
        for (player in players) {
            if (gone()) return
            player.effects.spotanim(0, SPOT_BURST, 0, 0)
            val dealt = world.meleeCombat.hitPlayer(player, boss, roll(BURST_MIN, BURST_MAX), world.tick)
            log("the charge bursts on ${player.username} for $dealt")
        }
        for (npc in standing) world.removeNpc(npc)
        state.diamond.clear()
        state.nextDiamond = world.tick + CHARGE_GAP
    }

    /** [binder] sends her its power: out of it, across, into her. */
    private fun EncounterContext.empower(binder: Npc) {
        binder.effects.spotanim(0, SPOT_EMPOWER_OUT, 0)
        world.zones.projectile(
            Projectile(
                id = SPOT_EMPOWER, fromX = binder.x, fromZ = binder.z, level = boss.level, toX = boss.x + SIZE / 2, toZ = boss.z + SIZE / 2,
                startHeight = 40, endHeight = 60, startTime = 0, endTime = 2 * CYCLES_PER_TICK, angle = 16, progress = 64,
                sourceIndex = binder.index + 1, targetIndex = boss.index + 1,
            ),
        )
        after(2) { if (!gone()) boss.effects.spotanim(0, SPOT_EMPOWER_IN, 0) }
    }

    private const val CYCLES_PER_TICK = 30
}
