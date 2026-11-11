package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/**
 * What Bork and the Smorks say over their heads. The words are those the tsps capture notes quote from live fights
 * (RSPSApp/tsps, docs/fractured-archive.md), typed into the dialogue file like every other line.
 */
object BorkLines : Lines("tfa_bork", "The_Fractured_Archive/Strategies") {
    val OOF = line("oof", "Bork, dazed by the Smorks' heavy cannonball")
    val SORRY = (1..3).map { line("sorry_$it", "a Smork after the daze: the ${ordinal(it)} of the three lines it picks from") }
    val NAPALM = (1..3).map { line("napalm_$it", "a Smork before their napalm: the ${ordinal(it)} of three") }
    val BIG_BOMB = line("big_bomb", "a Smork before their heavy cannonball")
    val VERY_ANGRY = line("very_angry", "Bork as his stomp begins after the daze")
    val STOMP_TIME = line("stomp_time", "Bork as his stomp begins after a heavy cannonball misses him (the Wiki's)")
    val SMASH = line("smash", "Bork naming his next tank as his stomp ends", "player")
    val TEMPO_BORK = (1..3).map { line("tempo_bork_$it", "Bork as the Smorks fall to ${100 - 25 * it}%, the tempo rising") }
    val TEMPO_SMORKS = (1..3).map { line("tempo_smorks_$it", "a Smork as they fall to ${100 - 25 * it}%, the tempo rising") }
    val AVENGE = line("avenge", "Bork as he dies")
    val HUMAN_DIE = line("human_die", "a Smork as Bork's body goes, before their frenzy")
    val UH_OH = line("uh_oh", "a Smork as the Smorks die")
    val MINE = line("mine", "a Smork in their frenzy, naming who their napalm goes at", "player")

    private fun ordinal(n: Int) = listOf("first", "second", "third")[n - 1]
}

/**
 * Bork and the Smorks (raid steps 18a, 18b and 18c), the second row's other pair of bosses, in their own room: Bork,
 * a 4 by 4 ork who keeps to his tank, and the Smorks, three goblins in a 3 by 3 cannon tower in the middle of the room. Both
 * have to die for the room to be done; the fight's own NPC is only its anchor (`tfa_bork_smork_hp_ui_handler`,
 * [ANCHOR]), which carries the party size and is taken away once both are gone.
 *
 * - Bork walks after his tank, the first player in, and beats his gong ([gong]) [FIRST_GONG] ticks in and every
 *   [ATTACK_SPEED] ticks after: two strikes [GONG_GAP] ticks apart. The first reaches out to his tank when they stand
 *   2 or 3 tiles off, over every tile out to them; otherwise, and always for the second, it hits the ring of tiles
 *   round him. The tower in the way of a strike ([shadowed]) keeps it off whoever is behind it.
 * - The Smorks go through their cycle ([firstCycle], [laterCycle]): volleys ([volley]) of bones, ranged, or potions,
 *   magic, at everyone Bork isn't after; napalm ([napalm]), whose splash ([splash]) leaves oil that burns whoever
 *   stands in it; and the heavy cannonball ([cannonball]) on a player Bork isn't after, which the party lures him
 *   onto. On him it dazes him ([daze]): [DAZE_TICKS] ticks with no armour, and melee always hitting him. Then, or
 *   [STOMP_AFTER_MISS] ticks after a cannonball that misses, he stomps ([stomp]): [STOMP_WAVES] shockwaves over the
 *   whole room, but for the tower's shadow, and at the end he names his next tank ([callTank]).
 * - Bork's flat armour ([armourFor]): [ARMOUR_BASE] off each melee and ranged hit on him as it's rolled, and
 *   [ARMOUR_STEP] more of it, and of his magic resistance, for every [ARMOUR_EVERY_PERCENT]% the Smorks have lost, up
 *   to [ARMOUR_MAX] and [MAGIC_ARMOUR_MAX]; none while he is dazed.
 * - The tempo (18c): at every 25% the Smorks lose, both call it out and the spacing of his gongs and their shots falls
 *   by a tick, 7 to 4 ([tempo]). Bork dead first, the Smorks go into their frenzy ([frenzyAhead]): a volley at
 *   everyone every [FRENZY_SPACING] ticks, napalm between. The Smorks dead first, Bork stomps over the whole room every
 *   [LONE_STOMP_EVERY] ticks, no tower left to hide behind. Their tower goes up [SMORKS_BURST] ticks after they fall
 *   and is gone at 9 (npc-combat.json's deathTicks), as Bork is at 7.
 *
 * From the OSRS Wiki (Bork, The Fractured Archive's; Smorks; read 2026-11-09): their stats and bonuses (npc-combat
 * .json), sizes, the 7-tick attack speed, their hitpoints by party size (party-scaling.json), the flat armour ("starts
 * at 8 flat armour and 0 magic resistance at 100% Smorks health, and gains 2 of each for every 25% of damage the
 * Smorks take", up to 14 and 6), the oil's 6-7 a tick and its burn, the daze taking his armour and melee always
 * hitting. From the tsps capture notes (RSPSApp/tsps docs/fractured-archive.md, read 2026-11-09: five spectator
 * captures of live fights on rev 241.1, so they show no damage on players): where Bork and the tower stand as it
 * begins, the gong's timings and reach, the Smorks' cycle and its spacing, napalm's timings and splash, the oil's 199
 * ticks, the heavy cannonball's timings and target, the 14-tick daze, the stomp's timings, its 4 shockwaves over the
 * room bar its corners and the tower's shadow, the tank call 15 ticks into it and the gong 19 ticks in, the next
 * cycle 39 ticks after a daze, and every overhead line. From guides (bald.gg, tonsofxp, osrsbestinslot; read
 * 2026-11-09): Protect from Melee reducing his damage, and the volleys and napalm at players Bork isn't after. From
 * the cache: the ids below.
 *
 * From Sam's recordings of 2026-11-09 (three fights, parties of four and five): through Protect from Melee a strike
 * never missing, the first of a gong's two doing 4 to 7 and the second 5 to 8 ([PRAYED_FIRST], [PRAYED_SECOND]: 35
 * strikes on his tank); with the prayer run out, a 6 and a 16, then a 32 and a killing blow.
 *
 * Guessed, until a recording shows more of the damage (each also in the README): a strike with no prayer never
 * missing either, and anything from the prayer's least up to his max hit ([strikeRange]); the 2-3 tile one's max hit
 * ([WIDE_MAX]) and its being a first strike's through the prayer; the tower's shadow keeping a strike off too;
 * a volley's style picked at random; the gap from a volley
 * to napalm and to the cannonball's call (a spacing); napalm's and the cannonball's target being a random player Bork
 * isn't after, his tank when there is none; the splash's drip being the target tile itself, and which way counts as
 * straight; napalm landing on Bork hurting him by an oil tick's 6-7, once; the burn's 2 every 4 ticks, 5 times, not
 * starting again while it runs; the cannonball hurting no player; the stomp after a miss and its line in place of the
 * daze's; a shockwave's damage ([STOMP_MIN] to [STOMP_MAX], no prayer helping), hitting all at once; the shadow's
 * fitted line test ([shadowed]); his next tank a random other player; the Smorks' cycle stopping as Bork dies, till
 * their frenzy; the frenzy's splash running away from the tower only from a tile beside it; the lone stomps' first
 * 45 ticks after the Smorks die, with the daze's line; the tempo's lines said by whichever of them is up; flat armour
 * not taking a hit below 0; the anchor being the hp handler NPC.
 */
object Bork {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "bork"

    /** `tfa_bork`. */
    const val BORK = 16640

    /** `tfa_smork`: the Smorks' tower. */
    const val SMORKS = 16641

    /** `tfa_bork_smork_hp_ui_handler`: the fight's anchor, never fought. */
    const val ANCHOR = 16642

    /** npc_bork_gong_melee01, his gong, and vfx_bork_gong_melee_spotanim01 with it. */
    const val ANIM_BEAT = 14542
    const val SPOT_BEAT = 4037

    /** vfx_bork_death_spotanim01, with his death animation (npc-combat.json's npc_bork_death01). */
    const val SPOT_BORK_DEATH = 4036

    /** npc_bork_14_cycle_stun01 and vfx_bork_14_cycle_stun_spotanim01: the daze. */
    const val ANIM_DAZE = 14545
    const val SPOT_DAZE = 4041

    /** npc_bork_enraged_stomp_attack_in01, _attack01 and _attack_cycle_and_out01, and vfx_bork_enraged_stomp_spotanim01. */
    const val ANIM_STOMP_IN = 14549
    const val ANIM_STOMP = 14550
    const val ANIM_STOMP_OUT = 14551
    const val SPOT_STOMP = 4039

    /** npc_smork_cannon_attack01: the Smorks' shot, bones, potions or napalm. */
    const val ANIM_SMORKS_ATTACK = 14554

    /** npc_smork_big_cannon_attack01, vfx_smork_big_cannon_attack_spotanim01 and its flash, where it lands. */
    const val ANIM_BIG_CANNON = 14555
    const val SPOT_BIG_CANNON = 4043
    const val SPOT_BIG_CANNON_LANDS = 4044

    /** vfx_smork_range_attack_projanim01, the bones' flight, and vfx_smork_range_player_spotanim01 as they land. */
    const val SPOT_BONES = 4056
    const val SPOT_BONES_HIT = 4055

    /** vfx_smork_mage_attack_spotanim01, _projanim01 and vfx_smork_magic_hit_spotanim01: the potions. */
    const val SPOT_POTIONS_CAST = 4058
    const val SPOT_POTIONS = 4057
    const val SPOT_POTIONS_HIT = 4059

    /** vfx_smork_napalm_fire_projanim01, _fire_spotanim01, _splat_spotanim01 and _despawn_spotanim01. */
    const val SPOT_NAPALM = 4045
    const val SPOT_NAPALM_FIRE = 4046
    const val SPOT_NAPALM_SPLAT = 4047
    const val SPOT_OIL_GONE = 4051

    /** gargboss_debris_shadow_120 and _150: where napalm and the heavy cannonball will land. */
    const val SPOT_NAPALM_SHADOW = 1447
    const val SPOT_CANNONBALL_SHADOW = 1448

    /** tfa_bork_ragged_tile: the oil. */
    const val LOC_OIL = 62544

    /**
     * vfx_smork_tower_death_spotanim01, vfx_smork_goblins_death_spotanim01 and vfx_smork_death_explosion_spotanim01: the
     * tower going up, [SMORKS_BURST] ticks after the Smorks fall (tsps; Sam's recording of 2026-11-09 shows the same).
     */
    val SPOTS_SMORKS_DEATH = listOf(4060, 4061, 4062)
    const val SMORKS_BURST = 2

    /** Bork's south-west tile as the fight begins, this far east and north of the tower's (tsps: 4194,5603 to 4190,5598). */
    const val BORK_EAST = 4
    const val BORK_NORTH = 5

    /** The room's floor round the tower's south-west tile, without its 4 corner tiles (tsps: 4184-4198 x 5591-5607). */
    const val ARENA_WEST = -6
    const val ARENA_EAST = 8
    const val ARENA_SOUTH = -7
    const val ARENA_NORTH = 9

    /** The Wiki's attack speed, both of them: the spacing of his gongs and the Smorks' shots until the tempo rises (18c). */
    const val ATTACK_SPEED = 7

    /** His first gong this many ticks in, its second strike this many after its first (tsps). */
    const val FIRST_GONG = 8
    const val GONG_GAP = 2

    /** A strike's reach out to his tank, at most, and its max hit then. The reach is the tsps notes'; the max is guessed. */
    const val WIDE_REACH = 3
    const val WIDE_MAX = 50

    /**
     * What a strike of his gong does through Protect from Melee, its first and its second: every one of 35 on his tank
     * in Sam's recordings of 2026-11-09, none a 0.
     */
    val PRAYED_FIRST = 4..7
    val PRAYED_SECOND = 5..8

    /** The Smorks' first volley this many ticks in (tsps). */
    const val FIRST_VOLLEY = 13

    /**
     * The potions' max hit, the bones' too (npc-combat.json's): the most seen in Sam's recording of 2026-11-09, some 45
     * hits of both on a party of four, most of them in the frenzy. The formula's from their Ranged would be 28.
     */
    const val POTIONS_MAX = 7

    /** Napalm: its cast this long after its call, its splash this long after the cast, and the oil a tick on (tsps). */
    const val NAPALM_CAST = 1
    const val NAPALM_FLIGHT = 4
    const val OIL_AFTER_SPLASH = 1

    /** How long the oil lies (tsps), and what it does each tick to whoever stands in it (Wiki). */
    const val OIL_TICKS = 199
    const val OIL_MIN = 6
    const val OIL_MAX = 7

    /** The oil's burn: the Wiki's 2, every [BURN_EVERY] ticks, [BURN_HITS] times (tsps's estimate). */
    const val BURN_DAMAGE = 2
    const val BURN_EVERY = 4
    const val BURN_HITS = 5

    /** The heavy cannonball: its shot this long after its call, and landing this long after the shot (tsps). */
    const val CANNON_SHOT = 2
    const val CANNON_FLIGHT = 5

    /** The daze's length (tsps and the Wiki). */
    const val DAZE_TICKS = 14

    /** His stomp this long after a cannonball that misses him. Guessed (the tsps notes' estimate). */
    const val STOMP_AFTER_MISS = 2

    /** The stomp, in ticks from its start (tsps): its swing, its shockwaves, his recovery, his call and his next gong. */
    const val STOMP_SWING = 2
    val STOMP_WAVES = listOf(3, 6, 9, 12)
    const val STOMP_RECOVER = 8
    const val STOMP_CALL = 15
    const val STOMP_GONG = 19

    /** The Smorks' next cycle this long after his stomp begins (tsps: 39 ticks after a daze, which lasts 14). */
    const val CYCLE_AFTER_STOMP = 25

    /** A shockwave's damage. The most is the one hit seen in Sam's recording of 2026-11-09; the least is guessed. */
    const val STOMP_MIN = 15
    const val STOMP_MAX = 34

    /** The tempo: a stage for every 25% the Smorks have lost, up to 3, each a tick off the spacing (tsps: 7, 6, 5, 4). */
    const val TEMPO_EVERY_PERCENT = 25
    const val TEMPO_MAX = 3

    /** After Bork dies (tsps): his body gone this long after, and the Smorks' frenzy this long after that. */
    const val BORK_GONE = 7
    const val FRENZY_AFTER = 4

    /**
     * The frenzy (tsps): a volley at everyone every [FRENZY_SPACING] ticks; napalm after the first two or three, the next
     * volley [FRENZY_AFTER_NAPALM] ticks after its cast, then [FRENZY_VOLLEYS] volleys between napalms.
     */
    const val FRENZY_SPACING = 2
    const val FRENZY_AFTER_NAPALM = 3
    const val FRENZY_VOLLEYS = 5

    /** With the Smorks dead, Bork stomps every this many ticks over the whole room, the first this long after they die (the tsps notes' estimate). */
    const val LONE_STOMP_EVERY = 45

    /** Bork's flat armour: the Wiki's 8, 2 more for each 25% the Smorks have lost, up to 14; his magic resistance up to 6. */
    const val ARMOUR_BASE = 8
    const val ARMOUR_STEP = 2
    const val ARMOUR_EVERY_PERCENT = 25
    const val ARMOUR_MAX = 14
    const val MAGIC_ARMOUR_MAX = 6

    /** The Smorks' bones. */
    val BONES = NpcRangedAttack(anim = ANIM_SMORKS_ATTACK, projectile = SPOT_BONES, impact = SPOT_BONES_HIT)

    /** The Smorks' potions. */
    val POTIONS = NpcRangedAttack(anim = ANIM_SMORKS_ATTACK, cast = SPOT_POTIONS_CAST, projectile = SPOT_POTIONS, impact = SPOT_POTIONS_HIT, maxHit = POTIONS_MAX)

    /** One of the Smorks' moves in a cycle. */
    enum class Shot { VOLLEY, NAPALM, CANNONBALL }

    /** Their first cycle (tsps): a volley, napalm, one or two volleys, the heavy cannonball. */
    fun firstCycle(more: Int): List<Shot> = listOf(Shot.VOLLEY, Shot.NAPALM) + List(more) { Shot.VOLLEY } + Shot.CANNONBALL

    /** A later one (tsps): two or three volleys, napalm, then volleys to four in all, the heavy cannonball. */
    fun laterCycle(before: Int): List<Shot> = List(before) { Shot.VOLLEY } + Shot.NAPALM + List(4 - before) { Shot.VOLLEY } + Shot.CANNONBALL

    /** The frenzy's start (tsps): two or three volleys, then napalm. */
    fun frenzyStart(volleys: Int): List<Shot> = List(volleys) { Shot.VOLLEY } + Shot.NAPALM

    /** The frenzy after that, over and over (tsps): [FRENZY_VOLLEYS] volleys, then napalm. */
    fun frenzyRound(): List<Shot> = List(FRENZY_VOLLEYS) { Shot.VOLLEY } + Shot.NAPALM

    /** The tempo for the Smorks at [hitpoints] of [max] (dead: at its most): a stage for every whole 25% lost, up to 3. */
    fun tempoFor(hitpoints: Int, max: Int): Int {
        if (max <= 0) return TEMPO_MAX
        val lost = (max - hitpoints.coerceIn(0, max)) * 100 / max
        return minOf(TEMPO_MAX, lost / TEMPO_EVERY_PERCENT)
    }

    /**
     * What a strike can do to a player, never missing, for a Bork whose max hit is [max]. [praying] with Protect from
     * Melee: [PRAYED_FIRST], or [PRAYED_SECOND] for a gong's [second]. Without: from the least of those up to [max], or
     * to [WIDE_MAX] for the one reaching out ([wide]); the recordings have a 6 and a 16 for the first and a 32 for the
     * second, with [max] at 34, and the rest of it is guessed.
     */
    fun strikeRange(max: Int, wide: Boolean, second: Boolean, praying: Boolean): IntRange {
        val prayed = if (second) PRAYED_SECOND else PRAYED_FIRST
        if (praying) return prayed
        val most = if (wide) WIDE_MAX else max
        return minOf(prayed.first, most)..most
    }

    /** The fight, or null when the cache hasn't the three (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val anchor = types[ANCHOR] ?: return null
        val borkType = types[BORK] ?: return null
        val smorksType = types[SMORKS] ?: return null
        return EncounterDef(
            NAME, anchor,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(CycleStep(1) { tick() }),
                    onStart = {
                        // Scaled to the party the anchor was (the raid's, or ::raid bork party=n), at least two.
                        val party = boss.scaledFor.takeIf { it > 0 } ?: PartyScaling.MIN_COUNTED
                        val smorks = spawnAdd(smorksType, boss.x, boss.z)
                        val bork = spawnAdd(borkType, boss.x + BORK_EAST, boss.z + BORK_NORTH)
                        for (npc in listOfNotNull(smorks, bork)) {
                            world.partyScaling.scale(npc, party.coerceIn(1, PartyScaling.MAX_PARTY))
                            // They attack only as this fight says, and never give up on whoever they're set on.
                            npc.scriptedAttacks = true
                            npc.unleashed = true
                        }
                        state.smorks = smorks?.let(::WeakReference)
                        state.bork = bork?.let(::WeakReference)
                        state.nextGong = FIRST_GONG.toLong()
                        state.smorksNext = FIRST_VOLLEY.toLong()
                        state.plan.addAll(firstCycle(roll(1, 2)))
                        // Worked out as each hit on him is rolled, so a hit taking the Smorks past a quarter counts at
                        // once for the next on him, in the same tick too; none at all while he is dazed.
                        val held = smorks?.let(::WeakReference)
                        val game = world
                        bork?.let { npc -> npc.armour = { magic -> if (game.tick < npc.meleeSureUntil) 0 else armourAgainst(held?.get(), magic) } }
                        bork?.let { armour(it) }
                        log("Bork at ${bork?.x},${bork?.z}, the Smorks at ${smorks?.x},${smorks?.z}")
                    },
                ),
            ),
            scriptedAttacks = true,
            status = {
                val bork = state.bork?.get()
                val smorks = state.smorks?.get()
                "Bork ${hp(bork)}, the Smorks ${hp(smorks)}"
            },
            // The anchor never moves and the Smorks' tower stands: Bork is the one a hold keeps off.
            held = { listOfNotNull(liveBork) },
            // The boss NPC is only the anchor: Bork and the Smorks are what the party fights.
            bosses = setOf(BORK, SMORKS),
        )
    }

    private fun hp(npc: Npc?): String = if (npc == null) "none" else "${if (npc.dead) 0 else npc.hitpoints}/${npc.maxHitpoints} hp"

    /** A tile of oil: its loc, and the phase tick it dries up on. */
    private class Oil(val loc: ZoneLoc, val until: Long)

    /** What a fight keeps between ticks, by its anchor. Holds its NPCs and players only weakly. */
    private class State {
        var bork: WeakReference<Npc>? = null
        var smorks: WeakReference<Npc>? = null

        /** His tank, kept here as his combat target can go (they died, or left). */
        var tank: WeakReference<Player>? = null

        /** The phase tick of his next gong, and until when he stands still (dazed, stomping). */
        var nextGong = 0L
        var boundUntil = 0L

        /** The Smorks' moves to come in this cycle, and the phase tick of the next; [Long.MAX_VALUE] while they wait. */
        val plan = ArrayDeque<Shot>()
        var smorksNext = 0L

        /** The oil, by tile. */
        val oil = HashMap<Tile, Oil>()

        /** Until when each player's burn runs, in phase ticks. */
        val burning = WeakHashMap<Player, Long>()

        /** Whether each one's death has been seen. */
        var borkDown = false
        var smorksDown = false

        /** The tempo stage reached, 0 to [TEMPO_MAX]. */
        var tempo = 0

        /** Whether the Smorks are in their frenzy, Bork dead. */
        var frenzy = false

        /** With the Smorks dead, the phase tick of Bork's next stomp. */
        var loneStomp = Long.MAX_VALUE

        /** His flat armour and magic resistance as last logged. */
        var armour = -1
        var magicArmour = -1
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    private fun EncounterContext.inWorld(npc: Npc): Boolean = world.npcs[npc.index] === npc

    private fun EncounterContext.alive(npc: Npc?): Npc? = npc?.takeIf { !it.dead && inWorld(it) }

    private val EncounterContext.liveBork: Npc? get() = alive(state.bork?.get())

    private val EncounterContext.liveSmorks: Npc? get() = alive(state.smorks?.get())

    private fun EncounterContext.text(line: Line, vararg values: Pair<String, Any>): String = world.dialogueText.get(line, *values)

    /** The spacing of his gongs and their shots: [ATTACK_SPEED], a tick less for each tempo stage (tsps). */
    private fun EncounterContext.spacing(): Int = ATTACK_SPEED - state.tempo

    /** One tick of the fight. */
    private fun EncounterContext.tick() {
        // One that couldn't be spawned (no NPC slot): nothing happens, and the raid takes the fight back (ArchiveRaids).
        if (state.bork == null || state.smorks == null) return
        val borkHeld = state.bork?.get()
        val smorksHeld = state.smorks?.get()
        if (borkHeld != null && borkHeld.dead && !state.borkDown) {
            state.borkDown = true
            borkHeld.effects.spotanim(0, SPOT_BORK_DEATH, 0)
            borkHeld.effects.say(text(BorkLines.AVENGE))
            log("Bork down")
            if (!state.smorksDown) frenzyAhead(sinceDeath(borkHeld))
        }
        if (smorksHeld != null && smorksHeld.dead && !state.smorksDown) {
            state.smorksDown = true
            // A fall seen a tick late (a hit after this tick's step) still bursts on its tick.
            after((SMORKS_BURST - sinceDeath(smorksHeld)).coerceAtLeast(1)) {
                SPOTS_SMORKS_DEATH.forEachIndexed { slot, spot -> smorksHeld.effects.spotanim(slot, spot, 0) }
                log("the tower bursts")
            }
            smorksHeld.effects.say(text(BorkLines.UH_OH))
            log("the Smorks down")
            state.plan.clear()
            state.smorksNext = Long.MAX_VALUE
            if (!state.borkDown) state.loneStomp = phaseTick + LONE_STOMP_EVERY - sinceDeath(smorksHeld)
        }
        // Done once both are gone, their death animations over: the anchor dies, which does the room.
        if ((borkHeld == null || !inWorld(borkHeld)) && (smorksHeld == null || !inWorld(smorksHeld))) {
            log("both gone")
            boss.dead = true
            world.removeNpc(boss)
            return
        }
        oil()
        val bork = alive(borkHeld)
        val smorks = alive(smorksHeld)
        tempo(bork, smorks)
        if (bork != null) {
            armour(bork)
            val tank = tank(bork)
            if (phaseTick >= state.loneStomp && phaseTick >= state.boundUntil) {
                // The Smorks dead: his stomp over the whole room every [LONE_STOMP_EVERY] ticks, no tower to hide behind.
                state.loneStomp = phaseTick + LONE_STOMP_EVERY
                stomp(missed = false)
            }
            if (phaseTick < state.boundUntil) {
                hold(bork)
            } else if (tank != null) {
                chase(bork, tank)
                if (phaseTick >= state.nextGong) {
                    gong(bork, tank)
                    state.nextGong = phaseTick + spacing()
                }
            }
        }
        if (smorks != null && phaseTick >= state.smorksNext) smorksMove(smorks, bork)
    }

    /** The tempo rising as the Smorks fall, each stage called out by both, and the spacing a tick shorter (tsps). */
    private fun EncounterContext.tempo(bork: Npc?, smorks: Npc?) {
        val held = state.smorks?.get() ?: return
        val stage = if (held.dead) TEMPO_MAX else tempoFor(held.hitpoints, held.maxHitpoints)
        if (stage <= state.tempo) return
        state.tempo = stage
        bork?.effects?.say(text(BorkLines.TEMPO_BORK[stage - 1]))
        smorks?.effects?.say(text(BorkLines.TEMPO_SMORKS[stage - 1]))
        log("tempo $stage, every ${spacing()} ticks")
    }

    /**
     * Bork dead with the Smorks up: their cycle stops; as his body goes [BORK_GONE] ticks on they swear revenge, and
     * [FRENZY_AFTER] after that their frenzy begins (tsps).
     */
    private fun EncounterContext.frenzyAhead(late: Int) {
        state.plan.clear()
        state.smorksNext = Long.MAX_VALUE
        after(BORK_GONE - late) { liveSmorks?.effects?.say(text(BorkLines.HUMAN_DIE)) }
        after(BORK_GONE + FRENZY_AFTER - late) {
            if (liveSmorks == null) return@after
            state.frenzy = true
            state.plan.clear()
            state.plan.addAll(frenzyStart(roll(2, 3)))
            log("the Smorks' frenzy")
            // This runs after the tick's cycle step, so the first volley goes now rather than a tick late.
            liveSmorks?.let { smorksMove(it, null) }
        }
    }

    /**
     * Bork's flat armour and magic resistance for the Smorks at [smorksHitpoints] of [smorksMax] (0 hitpoints for
     * Smorks dead): [ARMOUR_STEP] each for every whole [ARMOUR_EVERY_PERCENT]% they've lost.
     */
    fun armourFor(smorksHitpoints: Int, smorksMax: Int): Pair<Int, Int> {
        val lostPercent = if (smorksMax <= 0) 100 else (smorksMax - smorksHitpoints.coerceIn(0, smorksMax)) * 100 / smorksMax
        val steps = lostPercent / ARMOUR_EVERY_PERCENT
        return minOf(ARMOUR_MAX, ARMOUR_BASE + ARMOUR_STEP * steps) to minOf(MAGIC_ARMOUR_MAX, ARMOUR_STEP * steps)
    }

    /** Bork's flat armour, or magic resistance for a spell, with [smorks] as they are now (null or dead: at its most). */
    private fun armourAgainst(smorks: Npc?, magic: Boolean): Int {
        val (flat, resistance) = if (smorks == null || smorks.dead) armourFor(0, 1) else armourFor(smorks.hitpoints, smorks.maxHitpoints)
        return if (magic) resistance else flat
    }

    /** Logs Bork's armour when it changes; what hits on him lose comes from [Npc.armour] as each is rolled. */
    private fun EncounterContext.armour(bork: Npc) {
        val flat = bork.armour?.invoke(false) ?: 0
        val magic = bork.armour?.invoke(true) ?: 0
        if (flat != state.armour || magic != state.magicArmour) {
            state.armour = flat
            state.magicArmour = magic
            log("Bork's armour $flat, magic $magic")
        }
    }

    /**
     * His tank: the player he fights while they're in the fight, else the one he was set on (the first player in), else
     * the first in the fight.
     */
    private fun EncounterContext.tank(bork: Npc): Player? {
        val fighting = players
        state.tank?.get()?.let { held -> if (fighting.any { it === held }) return held }
        val pick = bork.target?.takeIf { held -> fighting.any { it === held } } ?: fighting.firstOrNull() ?: return null
        takeOn(bork, pick)
        return pick
    }

    private fun EncounterContext.takeOn(bork: Npc, pick: Player) {
        state.tank = WeakReference(pick)
        if (bork.target !== pick) {
            bork.target = null
            world.meleeCombat.aggress(bork, pick, world.tick)
        }
        log("Bork takes on ${pick.username}")
    }

    /** He walks after his tank till he's beside them, and stands while he is. */
    private fun EncounterContext.chase(bork: Npc, tank: Player) {
        if (beside(bork.x, bork.z, bork.type.size, tank.x, tank.z)) {
            if (bork.following === tank) world.npcs.stopFollowing(bork)
            bork.face(tank)
        } else if (bork.following !== tank) {
            world.npcs.follow(bork, tank)
        }
    }

    /** He stands where he is. */
    private fun EncounterContext.hold(bork: Npc) {
        if (bork.following != null) world.npcs.stopFollowing(bork)
        bork.destination = null
    }

    /** Whether ([x], [z]) is beside a [size] by [size] NPC at ([npcX], [npcZ]): along one of its sides, not off a corner. */
    fun beside(npcX: Int, npcZ: Int, size: Int, x: Int, z: Int): Boolean {
        val alongX = x in npcX until npcX + size && (z == npcZ - 1 || z == npcZ + size)
        val alongZ = z in npcZ until npcZ + size && (x == npcX - 1 || x == npcX + size)
        return alongX || alongZ
    }

    /** How many tiles ([x], [z]) is off a [size] by [size] NPC at ([npcX], [npcZ]): 0 on it, 1 round it. */
    fun distance(npcX: Int, npcZ: Int, size: Int, x: Int, z: Int): Int {
        val dx = max(max(npcX - x, x - (npcX + size - 1)), 0)
        val dz = max(max(npcZ - z, z - (npcZ + size - 1)), 0)
        return max(dx, dz)
    }

    /** Whether ([x], [z]) is within [reach] tiles of a [size] by [size] NPC at ([npcX], [npcZ]), counting its own tiles. */
    fun near(npcX: Int, npcZ: Int, size: Int, x: Int, z: Int, reach: Int): Boolean = distance(npcX, npcZ, size, x, z) <= reach

    /** Whether ([x], [z]) is on the floor of the room whose tower stands at ([towerX], [towerZ]): its corners aren't. */
    fun inArena(towerX: Int, towerZ: Int, x: Int, z: Int): Boolean {
        val dx = x - towerX
        val dz = z - towerZ
        if (dx !in ARENA_WEST..ARENA_EAST || dz !in ARENA_SOUTH..ARENA_NORTH) return false
        return !((dx == ARENA_WEST || dx == ARENA_EAST) && (dz == ARENA_SOUTH || dz == ARENA_NORTH))
    }

    /**
     * Whether the Smorks' tower, [towerSize] by [towerSize] at ([towerX], [towerZ]), shades ([x], [z]) from Bork, [size]
     * by [size] at ([fromX], [fromZ]): every line from the middles of his middle tiles (his middle 2 by 2) to the points
     * 0.3 of a tile in from the tile's corners crosses the tower. The tsps notes' fit to the captured shockwaves, "a tile
     * or two off at the edges"; the tower shading his gong's strikes too is guessed.
     */
    fun shadowed(towerX: Int, towerZ: Int, towerSize: Int, fromX: Int, fromZ: Int, size: Int, x: Int, z: Int): Boolean {
        if (x in towerX until towerX + towerSize && z in towerZ until towerZ + towerSize) return false
        val middle = ((size - 1) / 2)..(size / 2)
        val from = middle.flatMap { dx -> middle.map { dz -> (fromX + dx + 0.5) to (fromZ + dz + 0.5) } }
        val to = listOf(0.3, 0.7).flatMap { dx -> listOf(0.3, 0.7).map { dz -> (x + dx) to (z + dz) } }
        val box = doubleArrayOf(towerX.toDouble(), towerZ.toDouble(), (towerX + towerSize).toDouble(), (towerZ + towerSize).toDouble())
        return from.all { (fx, fz) -> to.all { (tx, tz) -> crosses(fx, fz, tx, tz, box) } }
    }

    /** Whether the line from ([x0], [z0]) to ([x1], [z1]) passes through [box] (min x, min z, max x, max z). */
    private fun crosses(x0: Double, z0: Double, x1: Double, z1: Double, box: DoubleArray): Boolean {
        var enter = 0.0
        var leave = 1.0
        for ((start, delta, lo, hi) in listOf(Quad(x0, x1 - x0, box[0], box[2]), Quad(z0, z1 - z0, box[1], box[3]))) {
            if (delta == 0.0) {
                if (start < lo || start > hi) return false
                continue
            }
            val a = (lo - start) / delta
            val b = (hi - start) / delta
            enter = max(enter, min(a, b))
            leave = min(leave, max(a, b))
            if (enter > leave) return false
        }
        return true
    }

    private data class Quad(val start: Double, val delta: Double, val lo: Double, val hi: Double)

    /**
     * His gong: two strikes [GONG_GAP] apart, the first out to his tank when they stand 2 or 3 tiles off, else round
     * him, the second round him.
     */
    private fun EncounterContext.gong(bork: Npc, tank: Player) {
        val off = distance(bork.x, bork.z, bork.type.size, tank.x, tank.z)
        bork.face(tank)
        bork.effects.anim(ANIM_BEAT)
        bork.effects.spotanim(0, SPOT_BEAT, 0)
        strike(bork, if (off in 2..WIDE_REACH) off else 1)
        after(GONG_GAP) {
            val now = liveBork
            if (now != null && phaseTick >= state.boundUntil) strike(now, 1, second = true)
        }
    }

    /** One strike of his gong, its first or its [second], on everyone within [reach] of him that the tower doesn't shade. */
    private fun EncounterContext.strike(bork: Npc, reach: Int, second: Boolean = false) {
        val size = bork.type.size
        val smorks = liveSmorks
        val inReach = players.filter { near(bork.x, bork.z, size, it.x, it.z, reach) }
        val struck = inReach.filter { smorks == null || !shadowed(smorks.x, smorks.z, smorks.type.size, bork.x, bork.z, size, it.x, it.z) }
        val behind = inReach - struck.toSet()
        val wide = reach > 1
        val what = if (wide) "wide beat" else if (second) "second beat" else "beat"
        log("$what on ${struck.joinToString { it.username }.ifEmpty { "no one" }}" + if (behind.isEmpty()) "" else ", the tower shading ${behind.joinToString { it.username }}")
        for (player in struck) {
            if (gone(bork)) return
            val damage = beatDamage(bork, player, wide, second)
            val dealt = world.meleeCombat.hitPlayer(player, bork, damage, world.tick)
            // What the strike would do, and what it did when that is less (`::god`, or their last hitpoints).
            log("$what hits ${player.username} for $damage" + if (dealt != damage) ", $dealt dealt" else "")
        }
    }

    /** A strike's damage on [player]: [strikeRange]'s roll, cut by Dinh's bulwark on Block as any NPC's melee is. */
    private fun EncounterContext.beatDamage(bork: Npc, player: Player, wide: Boolean, second: Boolean): Int {
        val max = world.combat.npcAttack(bork, player)?.maxHit ?: return 0
        val range = strikeRange(max, wide, second, world.prayers.protects(player, AttackType.CRUSH))
        val rolled = roll(range.first, range.last)
        return if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(rolled) else rolled
    }

    /** The Smorks' next move in their cycle, and when the one after it comes. */
    private fun EncounterContext.smorksMove(smorks: Npc, bork: Npc?) {
        if (state.frenzy && state.plan.isEmpty()) state.plan.addAll(frenzyRound())
        val shot = state.plan.removeFirstOrNull()
        if (shot == null) {
            state.smorksNext = Long.MAX_VALUE
            return
        }
        val spacing = if (state.frenzy) FRENZY_SPACING else spacing()
        when (shot) {
            Shot.VOLLEY -> {
                volley(smorks, bork)
                state.smorksNext = phaseTick + spacing
            }
            Shot.NAPALM -> {
                napalm(smorks)
                // Its cast a tick on, and the next volley 2 spacings less a tick after that; in the frenzy, 3 ticks after
                // the cast (tsps).
                state.smorksNext = phaseTick + if (state.frenzy) NAPALM_CAST + FRENZY_AFTER_NAPALM else 2 * spacing
            }
            Shot.CANNONBALL -> if (bork == null) {
                // No Bork to lure onto it: his death has stopped the cycle for the frenzy ([frenzyAhead]).
                state.smorksNext = Long.MAX_VALUE
            } else {
                cannonball(smorks)
                state.smorksNext = Long.MAX_VALUE
            }
        }
    }

    /** The Smorks' next cycle, its first move [delay] ticks from now; none once either has died (the frenzy, or no Smorks). */
    private fun EncounterContext.nextCycle(delay: Int) {
        if (state.borkDown || state.smorksDown) return
        state.plan.clear()
        state.plan.addAll(laterCycle(roll(2, 3)))
        state.smorksNext = phaseTick + delay
    }

    /** The players Bork isn't after; everyone once he is dead. */
    private fun EncounterContext.notTank(): List<Player> {
        val tank = if (liveBork != null) state.tank?.get() else null
        return players.filter { it !== tank }
    }

    /** Who napalm or the heavy cannonball goes at: a player Bork isn't after, else his tank. */
    private fun EncounterContext.aim(): Player? {
        val others = notTank()
        return if (others.isNotEmpty()) others[roll(0, others.size - 1)] else players.firstOrNull()
    }

    /** The Smorks' shots, all bones or all potions, at everyone Bork isn't after. */
    private fun EncounterContext.volley(smorks: Npc, bork: Npc?) {
        val tank = if (bork != null) state.tank?.get() else null
        val targets = players.filter { it !== tank }
        if (targets.isEmpty()) return
        val magic = roll(0, 1) == 1
        log("the Smorks fire ${if (magic) "potions" else "bones"} at ${targets.joinToString { it.username }}")
        for (player in targets) {
            if (gone(smorks)) return
            world.meleeCombat.fireAt(smorks, player, world.tick, if (magic) POTIONS else BONES, if (magic) AttackType.MAGIC else AttackType.RANGED)
        }
    }

    /**
     * Napalm: called now, cast at a player's tile a tick on, splashing [NAPALM_FLIGHT] ticks after that. In the frenzy
     * the call names who it goes at ([BorkLines.MINE]): Sam's recording of 10 November 2026 (clip 16-09-39 at 6:26) has
     * the Smorks naming two players, one after the other, as Bork died, and both dying within seconds. Guessed: that it
     * is the frenzy's napalm calling them, and that it goes at the one named while they are still there to hit.
     */
    private fun EncounterContext.napalm(smorks: Npc) {
        val named = if (state.frenzy) aim() else null
        if (named != null) {
            smorks.effects.say(text(BorkLines.MINE, "player" to named.username))
        } else {
            smorks.effects.say(text(BorkLines.NAPALM[roll(0, BorkLines.NAPALM.size - 1)]))
        }
        log("the Smorks call napalm${named?.let { " on ${it.username}" }.orEmpty()}")
        after(NAPALM_CAST) {
            val tower = liveSmorks ?: return@after
            val target = named?.takeIf { it in players } ?: aim() ?: return@after
            val at = Tile(target.x, target.z)
            tower.face(target)
            tower.effects.anim(ANIM_SMORKS_ATTACK)
            world.zones.projectile(towerShot(tower, at, SPOT_NAPALM, NAPALM_FLIGHT))
            world.zones.mapAnim(at.x, at.z, boss.level, SPOT_NAPALM_SHADOW)
            log("napalm at ${at.x},${at.z} (${target.username})")
            after(NAPALM_FLIGHT) { splashDown(tower.x, tower.z, tower.type.size, at) }
        }
    }

    /** A shot from the tower's middle to [at], landing [ticks] later. Its heights and arc are guessed. */
    private fun towerShot(tower: Npc, at: Tile, id: Int, ticks: Int): Projectile {
        val middle = tower.type.size / 2
        return Projectile(
            id = id, fromX = tower.x + middle, fromZ = tower.z + middle, level = tower.level, toX = at.x, toZ = at.z,
            startHeight = 120, endHeight = 0, startTime = 0, endTime = ticks * 30, angle = 16, progress = 64,
        )
    }

    /**
     * The tiles napalm aimed at ([x], [z]) splashes, for the tower [towerSize] by [towerSize] at ([towerX], [towerZ])
     * (tsps): the tile itself (its drip, guessed) and, two deep beyond it towards the tower, the 3 wide row when the
     * tower lies straight that way, or the 2 by 2 when it lies on a diagonal (more than twice as far along one way as
     * the other counting as straight, guessed). [away], in the frenzy next to the tower, it runs away from it instead
     * (tsps). Never the tower's own tiles.
     */
    fun splash(towerX: Int, towerZ: Int, towerSize: Int, x: Int, z: Int, away: Boolean = false): Set<Tile> {
        val middle = towerSize / 2
        val ax = towerX + middle - x
        val az = towerZ + middle - z
        val sx = if (away) -ax.sign else ax.sign
        val sz = if (away) -az.sign else az.sign
        val tiles = linkedSetOf(Tile(x, z))
        when {
            ax == 0 && az == 0 -> {}
            abs(ax) > 2 * abs(az) -> for (i in 1..2) for (j in -1..1) tiles += Tile(x + sx * i, z + j)
            abs(az) > 2 * abs(ax) -> for (i in 1..2) for (j in -1..1) tiles += Tile(x + j, z + sz * i)
            else -> for (i in 1..2) for (j in 1..2) tiles += Tile(x + sx * i, z + sz * j)
        }
        return tiles.filterTo(linkedSetOf()) { it.x !in towerX until towerX + towerSize || it.z !in towerZ until towerZ + towerSize }
    }

    /** Napalm splashing down round [at]: Bork under it is hurt, and a tick on its tiles lie in oil. */
    private fun EncounterContext.splashDown(towerX: Int, towerZ: Int, towerSize: Int, at: Tile) {
        val away = state.frenzy && distance(towerX, towerZ, towerSize, at.x, at.z) == 1
        val tiles = splash(towerX, towerZ, towerSize, at.x, at.z, away).filter { inArena(boss.x, boss.z, it.x, it.z) }
        world.zones.mapAnim(at.x, at.z, boss.level, SPOT_NAPALM_FIRE)
        for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_NAPALM_SPLAT)
        val bork = liveBork
        if (bork != null && tiles.any { near(bork.x, bork.z, bork.type.size, it.x, it.z, 0) }) {
            val dealt = world.meleeCombat.hit(bork, null, roll(OIL_MIN, OIL_MAX), world.tick)
            log("napalm burns Bork for $dealt")
        }
        after(OIL_AFTER_SPLASH) {
            // Tiles that had no oil: whoever stands on one is burnt this tick too, as the oil appears. Oil already there
            // burnt them earlier in this tick ([oil]).
            val fresh = tiles.filter { it !in state.oil }.toSet()
            for (tile in tiles) {
                state.oil.remove(tile)?.let { clearLoc(it.loc) }
                state.oil[tile] = Oil(placeLoc(LOC_OIL, tile.x, tile.z), phaseTick + OIL_TICKS)
            }
            log("oil on ${tiles.joinToString(" ") { "${it.x},${it.z}" }}")
            for (player in players) if (Tile(player.x, player.z) in fresh) oilBurns(player)
        }
    }

    /** The oil drying up, and burning whoever stands in it. */
    private fun EncounterContext.oil() {
        val oil = state.oil
        if (oil.isEmpty()) return
        val dry = oil.filterValues { it.until <= phaseTick }
        for ((tile, gone) in dry) {
            oil.remove(tile)
            clearLoc(gone.loc)
            world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_OIL_GONE)
        }
        for (player in players) if (Tile(player.x, player.z) in oil) oilBurns(player)
    }

    /** The oil under [player] burns them: 6 to 7, and their burn starts if it isn't running. */
    private fun EncounterContext.oilBurns(player: Player) {
        val dealt = fromTheRoom(player, roll(OIL_MIN, OIL_MAX))
        log("oil burns ${player.username} for $dealt")
        burn(player)
    }

    /**
     * [player] takes [damage] from the room (the oil, its burn): from no one, so they don't hit back, but in combat all
     * the same, as a boss's hazard holds them ([osrsps.game.encounter.Hazard]): a Home Teleport cut short, a logout held.
     */
    private fun EncounterContext.fromTheRoom(player: Player, damage: Int): Int {
        val dealt = world.meleeCombat.hitPlayer(player, null, damage, world.tick)
        if (!player.dead) player.attackedTick = world.tick
        return dealt
    }

    /** [player] catches fire: [BURN_DAMAGE] every [BURN_EVERY] ticks, [BURN_HITS] times; not again while it runs. */
    private fun EncounterContext.burn(player: Player) {
        if ((state.burning[player] ?: -1L) > phaseTick) return
        state.burning[player] = phaseTick + BURN_EVERY * BURN_HITS
        for (i in 1..BURN_HITS) {
            after(BURN_EVERY * i) {
                if (players.none { it === player }) return@after
                val dealt = fromTheRoom(player, BURN_DAMAGE)
                log("burn on ${player.username} for $dealt")
            }
        }
    }

    /** The heavy cannonball: called now, shot at a player's tile [CANNON_SHOT] ticks on, landing [CANNON_FLIGHT] after. */
    private fun EncounterContext.cannonball(smorks: Npc) {
        smorks.effects.say(text(BorkLines.BIG_BOMB))
        log("the Smorks call a heavy cannonball")
        after(CANNON_SHOT) {
            val tower = liveSmorks
            val target = aim()
            if (tower == null || target == null || liveBork == null) {
                nextCycle(spacing())
                return@after
            }
            val at = Tile(target.x, target.z)
            tower.face(target)
            tower.effects.anim(ANIM_BIG_CANNON)
            tower.effects.spotanim(0, SPOT_BIG_CANNON, 0)
            world.zones.mapAnim(at.x, at.z, boss.level, SPOT_CANNONBALL_SHADOW)
            log("heavy cannonball at ${at.x},${at.z} (${target.username})")
            after(CANNON_FLIGHT) { land(at) }
        }
    }

    /** The heavy cannonball landing on [at]: on Bork it dazes him; else he stomps all the same. */
    private fun EncounterContext.land(at: Tile) {
        world.zones.mapAnim(at.x, at.z, boss.level, SPOT_BIG_CANNON_LANDS)
        val bork = liveBork
        when {
            bork == null -> nextCycle(spacing())
            near(bork.x, bork.z, bork.type.size, at.x, at.z, 0) -> daze(bork)
            else -> {
                log("the heavy cannonball misses Bork")
                after(STOMP_AFTER_MISS) { stomp(missed = true) }
            }
        }
    }

    /** Bork dazed: [DAZE_TICKS] ticks standing, with no armour and melee always hitting him, then his stomp. */
    private fun EncounterContext.daze(bork: Npc) {
        state.boundUntil = Long.MAX_VALUE
        bork.meleeSureUntil = world.tick + DAZE_TICKS
        hold(bork)
        bork.effects.anim(ANIM_DAZE)
        bork.effects.spotanim(0, SPOT_DAZE, 0)
        bork.effects.say(text(BorkLines.OOF))
        liveSmorks?.effects?.say(text(BorkLines.SORRY[roll(0, BorkLines.SORRY.size - 1)]))
        log("Bork dazed")
        after(DAZE_TICKS) { stomp(missed = false) }
    }

    /**
     * His stomp: standing, he swings at [STOMP_SWING] ticks, sends a shockwave over the room at each of [STOMP_WAVES],
     * recovers at [STOMP_RECOVER], names his next tank at [STOMP_CALL] and gongs again at [STOMP_GONG]; the Smorks'
     * next cycle begins at [CYCLE_AFTER_STOMP].
     */
    private fun EncounterContext.stomp(missed: Boolean) {
        val bork = liveBork
        if (bork == null) {
            state.boundUntil = 0
            nextCycle(spacing())
            return
        }
        state.boundUntil = phaseTick + STOMP_CALL
        state.nextGong = phaseTick + STOMP_GONG
        nextCycle(CYCLE_AFTER_STOMP)
        hold(bork)
        bork.effects.say(text(if (missed) BorkLines.STOMP_TIME else BorkLines.VERY_ANGRY))
        bork.effects.anim(ANIM_STOMP_IN)
        log("Bork stomps")
        after(STOMP_SWING) { liveBork?.effects?.anim(ANIM_STOMP) }
        for (wave in STOMP_WAVES) after(wave) { shockwave() }
        after(STOMP_RECOVER) { liveBork?.effects?.anim(ANIM_STOMP_OUT) }
        after(STOMP_CALL) { callTank() }
    }

    /** One shockwave of his stomp, on everyone in the room but those in the tower's shadow. */
    private fun EncounterContext.shockwave() {
        val bork = liveBork ?: return
        val size = bork.type.size
        bork.effects.spotanim(0, SPOT_STOMP, 0)
        val smorks = liveSmorks
        val inRoom = players.filter { inArena(boss.x, boss.z, it.x, it.z) }
        val struck = inRoom.filter { smorks == null || !shadowed(smorks.x, smorks.z, smorks.type.size, bork.x, bork.z, size, it.x, it.z) }
        val shaded = inRoom - struck.toSet()
        log("shockwave on ${struck.joinToString { it.username }.ifEmpty { "no one" }}" + if (shaded.isEmpty()) "" else ", the tower shading ${shaded.joinToString { it.username }}")
        for (player in struck) {
            if (gone(bork)) return
            val dealt = world.meleeCombat.hitPlayer(player, bork, roll(STOMP_MIN, STOMP_MAX), world.tick)
            log("shockwave hits ${player.username} for $dealt")
        }
    }

    /** The end of his stomp: he names his next tank, another player when there is one, and goes after them. */
    private fun EncounterContext.callTank() {
        val bork = liveBork ?: return
        val current = state.tank?.get()
        val others = players.filter { it !== current }
        val pick = if (others.isNotEmpty()) others[roll(0, others.size - 1)] else players.firstOrNull() ?: return
        bork.effects.say(text(BorkLines.SMASH, "player" to pick.username))
        takeOn(bork, pick)
        // After them this tick: the fight's tick, before this call, had him chase the tank before.
        chase(bork, pick)
    }

    /**
     * How many ticks ago [npc] died, seen at this tick's death check: 0 for a hit before the fight's tick, 1 for one later
     * in the last tick (napalm splashing after the check, or a player's hit after the fight's tick), so what follows a
     * death keeps to the tick of the death itself.
     */
    private fun EncounterContext.sinceDeath(npc: Npc): Int =
        if (npc.diedTick < 0) 0 else (world.tick - npc.diedTick).toInt().coerceIn(0, BORK_GONE - 1)

    private fun EncounterContext.gone(npc: Npc): Boolean = boss.dead || npc.dead || !inWorld(npc)
}
