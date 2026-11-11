package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.combat.HitsplatType
import osrsps.game.combat.MagicInteraction
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.MeleeInteraction
import osrsps.game.combat.RangedInteraction
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.map.Tile

/**
 * The Archive Guardian's words: the menu on Touch on the waiting Inert Guardian, and the chat line as it picks who its
 * next beam follows. Seen in Sam's recordings of 9 and 10 November 2026 (clip notes).
 */
object ArchiveGuardianLines : Lines("tfa_guardian", "The_Fractured_Archive/Strategies") {
    val BEGIN = line("begin", "the title of the Yes/No menu on Touch on the waiting Inert Guardian")
    val YES = line("yes", "that menu, option 1: its fight begins")
    val NO = line("no", "that menu, option 2")
    val FOCUS_YOU = line("focus_you", "chat, to the player its next beam follows, as it picks them")
    val FOCUS = line("focus", "chat, to everyone else in the fight then, with that player's name", "name")
}

/**
 * The Archive Guardian (raid steps 16a to 16c), the first boss of every Archive run: four phases on the cache's two
 * forms, the first two by turns.
 *
 * - **Shield**: levitating (`tfa_guardian`, 16659), its hitpoints the shield's (the Wiki's "Flying" row, 550 to 1,950).
 *   It shoots its tank, the first player it took on, every 5 ticks (ranged, max hit 25), and slams whoever stands under
 *   it instead. Only ranged, magic and halberds reach it ([osrsps.game.combat.NpcCombatDef.flying]). When the shield is
 *   gone it holds at 0 for [SHIELD_DOWN_TICKS] ticks, then drops.
 * - **Grounded**: `tfa_guardian_unshielded` (16660) with the Wiki's "Grounded" hitpoints, 1,100 to 3,910: full the
 *   first time, and after that what it had left as it last lifted off. Its fall blasts whoever is under or beside it.
 *   It swings at a player beside it every 5 ticks (crush, max hit 25). After [GROUNDED_FIRST] ticks, and
 *   [GROUNDED_LATER] each time after its first, it lifts off again behind a whole shield: the shield phase once more.
 * - **Beams**: [LOW_DELAY] ticks after its hitpoints fall to [LOW_PERCENT]% it lifts off behind one more whole shield,
 *   and from then to its death it sends a beam (the game's word is its "breath") at a player every [BEAM_EVERY] ticks:
 *   a chat line names them ([ArchiveGuardianLines.FOCUS]) [BEAM_WARNING] ticks before it shows, and it reaches its
 *   line [BEAM_TRAVEL] tick after. The beam runs from its middle through that player and on. With an inert guardian
 *   behind them it spends that one, which takes all it has ([statueSplat], by party size) and goes dark, and those on its line take [BEAM_BLOCKED_MIN] to [BEAM_BLOCKED_MAX]; [BEAM_CHARGE_DELAY] ticks later
 *   the shield loses [BATTERY_SHIELD_PERCENT]% of its most, or with no more than that left breaks at once, with no
 *   ticks at 0. With none behind them the beam's whole damage ([beamDamage]) is shared by those on its line, and the
 *   shield is untouched. There is no timer on this shield.
 * - **Final**: down for the last time, with the hitpoints it had left, until it dies; the beams go on by the same
 *   count.
 *
 * From Sam's recordings of three live fights (2026-11-09 and 10, parties of four and five, the bars read tick by tick,
 * clip notes "tick pass", "full-frame pass" and H1 to H6): the shield and the hitpoints by turns, the shield whole again
 * each time and the hitpoints carried over; the broken shield's 2 to 3 ticks at 0; its first time down lasting 34 to 36
 * ticks whatever its hitpoints (55%, 76% and 64% left), its second 40 to 41; a time down ending about 4 ticks after its
 * hitpoints went under 30% (three fights); beams only in the shield after that and in the time down that follows, none
 * before; a beam 19 to 21 ticks into that shield and each 20 ticks after it (seven beams), the same count carrying on
 * once it is down; the inert guardian behind a beam's target taking 400 (five players, three times) or 330 (four,
 * twice) and going dark; 40% of the
 * shield's most gone in one tick 4 to 6 ticks after such a beam (504 of 1,260 and 412 of 1,030), and the three last
 * shields ending 4 to 5 ticks after one with less than that left (103, 123 and 66), the hitpoints back on that tick;
 * 8, 8 and 10 on players on such a beam's line, and 10 on each of three players on one; a beam with no inert guardian behind its target killing him, the
 * shield untouched; the last time down lasting to the death or the wipe (22, about 29 and 63 ticks), with no shield
 * after it; and "Begin the challenge?", Yes or No, on Touch.
 *
 * Read again by eye on 10 November 2026 at 10 frames a second with the tick metronome in frame (three beams, the
 * same each time): the chat line, 9 ticks to the beam showing, 1 tick to its reaching the players and the inert
 * guardian, 4 ticks to the 40% coming off the shield, 14 in all, and the lines 20 ticks apart; a charge with less
 * than 40% left taking just what was left (a 348 splat) and the bar going straight to the hitpoints.
 *
 * Inferred from them: that the beams begin with the shield that follows 30%, not with a count of shields; that the
 * inert guardian's splat grows with the party as the shield does.
 *
 * From the OSRS Wiki (Archive Guardian, read 2026-11-03): its stats, bonuses, both forms' hitpoints by party size, the
 * 5-tick attack speed, max hits 25 and 99+ (the beam), the 80% fire weakness, the poison and venom immunity, and the
 * 60% Defence drain floor (21 October 2026). From The Fractured Archive/Strategies, as a guide site quotes it (the
 * Wiki's own page cuts off before the bosses): the levitating phase hit only by ranged, magic and halberds, the
 * shots at the player it chose and the slam on whoever steps under it, the shield breaking to ground it, the blast on
 * those under or next to it as it falls, 30% for the last phase, and the beam that "is fatal if more than two players
 * are alive and it isn't reduced", shared by standing in line with its target and "slightly" reduced by Protect from
 * Magic. Its "no shield" in the last phase is not what the recordings show. From the 241.4 cache: both forms (Attack on
 * each, size 5, the slash bonus 90 on the grounded one, 110 levitating), the dormant `tfa_guardian_start` ("Inert
 * Guardian", Touch) and the animations and spotanims named `npc_archive_guardian_*` and `vfx_guardian_*`.
 *
 * Guessed, until a recording shows it (each also in the README): the first attack 5 ticks into each phase; the slam's
 * and the blast's damage ([SLAM_MAX], [BLAST_MIN] to [BLAST_MAX]); the whole beam's ([beamDamage]) and its line, from
 * the Guardian's middle through its target to [BEAM_LENGTH] tiles, aimed where they stood as it showed; Protect from
 * Magic taking a tenth off it; the inert guardian's splat for parties other than four and five; what a spent inert guardian does once the Guardian is down for
 * the last time ([BATTERY_BEAM_PERCENT] of its hitpoints, never into kill range, [KILL_RANGE_PERCENT]: a guide's "the
 * beam damages the boss's health", its figures guessed); that its shots, mushrooms and the inert guardians awake go on
 * under the beams' shield as under the others; the grounded form's 40% fire weakness (a guide's); that it never moves,
 * and keeps one tank till they fall or are out of its shot, when it turns on someone in it. Not in: the 5 or so ticks
 * from Yes to its rise (it wakes at once), and the chat line to a player taking the whole beam, whose words weren't
 * legible.
 *
 * Step 16b, from guides (osrstoolkit, runescapeguides, bald.gg; read 2026-11-03) and the cache's mushroom locs and
 * `archive_guardian_tentacle` Vine: mushrooms ([mushrooms]) while a shield is up, bursting on a player who steps beside
 * one for 3 to 10 with poison and weariness ([Player.wearyTicks]), splashing those two tiles off; vines ([vines]) from
 * [VINES_FROM] ticks into each time down and every [VINES_EVERY] after, each on its player's side away from the boss,
 * swiping the 3x3 round it [VINE_SWIPES] times, Protect from Melee holding a swipe to [VINE_PROTECTED_MAX]. Guessed:
 * [MUSHROOMS_EVERY], [MUSHROOM_GROW], [MUSHROOM_FUSE], the splash, [MUSHROOM_POISON], [WEARY_TICKS] and [VINE_MAX].
 *
 * Step 16c, from guides (runescapeguides, osrstoolkit; read 2026-11-03) and the cache's `tfa_guardian_battery_*`: four
 * inert guardians round the hall ([BATTERY_SPOTS], guessed), three awake under a shield; one beaten to 0 is charged
 * ([charge]), taking [BATTERY_SHIELD_PERCENT] of the shield off, and spent (the recordings show players attacking
 * them under the first two shields, one at 0). The south-east one never wakes, so only a beam spends it ([behind]).
 */
object ArchiveGuardian {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "guardian"

    /** `tfa_guardian_start`, "Inert Guardian" with Touch: the Guardian waiting in the hall till a member wakes it. */
    const val DORMANT = 16657
    const val TOUCH = 1

    /** `tfa_guardian` and `tfa_guardian_unshielded`: the levitating form and the grounded one. */
    const val FLYING = 16659
    const val GROUNDED = 16660

    /** npc_archive_guardian_*: its fall and lift-off, its slam (swoosh), the beam (special) and waking (spawn). */
    const val ANIM_TO_GROUND = 14747
    const val ANIM_TO_FLYING = 14748
    const val ANIM_SLAM = 14750
    const val ANIM_LASER = 14753
    const val ANIM_WAKE = 14755

    /** vfx_guardian_*: the same moments' spotanims on it. */
    const val SPOT_TO_GROUND = 4202
    const val SPOT_TO_FLYING = 4203
    const val SPOT_LASER = 4205
    const val SPOT_WAKE = 4206

    /** The Wiki's attack speed, for every phase's attacks. */
    const val ATTACK_SPEED = 5

    /** At this share of its grounded hitpoints, in percent, its next shield is the beams' (a guide's 30%; Sam's recordings: 30.3%, 29.6% and 29.4%). */
    const val LOW_PERCENT = 30

    /** How long after its hitpoints fall that far it lifts off (Sam's recordings: about 4 ticks, three fights). */
    const val LOW_DELAY = 4

    /** A broken shield holds at 0 this long before it drops (Sam's recordings: 2 to 3 ticks, four times). */
    const val SHIELD_DOWN_TICKS = 2

    /**
     * Grounded, it lifts off again behind a whole shield after this long: its first time down (Sam's recordings: 34, 36
     * and 35 ticks, with 55%, 76% and 64% of its hitpoints left) and each time after (41 and 40 to 41).
     */
    const val GROUNDED_FIRST = 35
    const val GROUNDED_LATER = 41

    /** The slam on whoever stands under it as a shot is due: up to this, Protect from Melee blocking it. Guessed. */
    const val SLAM_MAX = 25

    /** The blast on whoever is under or beside it as it falls. Guessed. */
    const val BLAST_MIN = 10
    const val BLAST_MAX = 20

    /**
     * Ticks from the beams' shield going up to its first beam, and between beams from then to its death (Sam's
     * recordings: first beams 19, 20 and 21 ticks in, each next one 20 on, seven beams in three fights).
     */
    const val BEAM_EVERY = 20

    /**
     * Ticks from the chat line naming a beam's target to the beam showing. Sam's recordings of 10 November 2026, read
     * by eye at 10 frames a second with the tick metronome in frame: 9 ticks, the same in three beams.
     */
    const val BEAM_WARNING = 9

    /**
     * Ticks from the beam showing to its reaching those on its line and the inert guardian behind them (the same three
     * beams: 1, the statue's splat on that tick).
     */
    const val BEAM_TRAVEL = 1

    /**
     * Ticks from a beam spending an inert guardian to that one's charge reaching the Guardian (Sam's recordings: 4, 4,
     * 4 to 5, 4 to 5 and 5 to 6; read again by the metronome on 10 November, 4 in two beams: 14 ticks from the chat line).
     */
    const val BEAM_CHARGE_DELAY = 4

    /**
     * What a beam leaves for each player on its line when an inert guardian behind its target takes it (Sam's
     * recordings: 8, 8 and 10 on 9 November; 10 on each of three players on one line and 10 on one on another on 10 November).
     */
    const val BEAM_BLOCKED_MIN = 8
    const val BEAM_BLOCKED_MAX = 10

    /**
     * The hitsplat on an inert guardian a beam spends, by party size from 1: what it has, all taken. Sam's recordings:
     * 400 with five players (9 November, twice legible), 330 with four (10 November, both statues, read at full size).
     * Inferred: that it grows with the party as the Guardian's shield does (400 / 330 is the shield's 1,260 / 1,030).
     * Guessed: the other sizes, as the same share of the shield (32%).
     */
    val STATUE_SPLATS: List<Int> = listOf(176, 176, 256, 330, 400, 477, 550, 624)

    /** [STATUE_SPLATS] for a party of [party]; the most for a bigger one, the least for none. */
    fun statueSplat(party: Int): Int = STATUE_SPLATS[(party - 1).coerceIn(0, STATUE_SPLATS.size - 1)]

    /** How far its beam reaches past its middle, in tiles. Guessed: across the hall. */
    const val BEAM_LENGTH = 32

    /** `tfa_guardian_mushroom_spawn`, `_active` and `_despawn`: a mushroom growing, ready, and bursting. */
    const val MUSHROOM_SPAWN = 62578
    const val MUSHROOM_ACTIVE = 62579
    const val MUSHROOM_DESPAWN = 62580
    private val MUSHROOM_LOCS = setOf(MUSHROOM_SPAWN, MUSHROOM_ACTIVE, MUSHROOM_DESPAWN)

    /** `tfa_archive_guardian_mushroom_spawn`, played on a mushroom as it grows. */
    const val ANIM_MUSHROOM_SPAWN = 14756

    /** Ticks between the shield phase's mushrooms, one by each player. Guessed. */
    const val MUSHROOMS_EVERY = 15

    /** Ticks a mushroom takes to grow, and the most it waits ready before bursting by itself. Guessed. */
    const val MUSHROOM_GROW = 2
    const val MUSHROOM_FUSE = 20

    /**
     * A burst's damage on whoever is within a tile of it (a guide's "3-10"), with poison of this severity and
     * [WEARY_TICKS] of weariness; the splash on whoever is two tiles off. The rest guessed.
     */
    const val MUSHROOM_MIN = 3
    const val MUSHROOM_MAX = 10
    const val MUSHROOM_POISON = 20
    const val SPLASH_MIN = 1
    const val SPLASH_MAX = 5
    const val WEARY_TICKS = 10

    /** `archive_guardian_tentacle`, "Vine": the tendrils of the grounded phase. */
    const val VINE = 16664

    /** npc_guardian_archive_tentacle_*: a vine coming up, swiping and going down; vfx_guardian_tentacle_intro on its tile. */
    const val ANIM_VINE_SPAWN = 14771
    const val ANIM_VINE_SWIPE = 14768
    const val ANIM_VINE_DESPAWN = 14769
    const val SPOT_VINE_SPAWN = 4208

    /**
     * Grounded, the first vines come [VINES_FROM] ticks in and more every [VINES_EVERY], one beside each player on the
     * side away from the Guardian, each swiping the 3 by 3 round it [VINE_SWIPES] times, [VINES_EVERY] ticks apart (a
     * guide's 6 ticks, every 3, three swipes).
     */
    const val VINES_FROM = 6
    const val VINES_EVERY = 3
    const val VINE_SWIPES = 3

    /** A swipe's damage: up to [VINE_MAX], or [VINE_PROTECTED_MAX] under Protect from Melee (a guide's 11). VINE_MAX guessed. */
    const val VINE_MAX = 22
    const val VINE_PROTECTED_MAX = 11

    /** `tfa_guardian_battery_inactive`, `_active` and `_destroyed`: an inert guardian round the hall, asleep, chargeable, spent. */
    const val BATTERY_INACTIVE = 16661
    const val BATTERY_ACTIVE = 16662
    const val BATTERY_SPENT = 16663

    /**
     * A charged inert guardian, or one a beam spends, takes this share of the shield's most hitpoints off it, in percent
     * (a guide's ~40%, 596 of 1,490; Sam's recordings: 504 of 1,260 and 412 of 1,030 after a beam).
     */
    const val BATTERY_SHIELD_PERCENT = 40

    /**
     * Down for the last time, an inert guardian a beam spends hits the Guardian for this share of its most hitpoints, in
     * percent, unless that would take it into kill range, [KILL_RANGE_PERCENT] or less, where they stop hurting it.
     * Guessed: the guides say only that it is damaged.
     */
    const val BATTERY_BEAM_PERCENT = 10
    const val KILL_RANGE_PERCENT = 10

    /**
     * The four inert guardians, north-west, north-east, south-west and south-east, by their south-west tiles from the
     * Guardian's middle; the south-east one never wakes, so only a beam reaches it (a guide's). Where they stand is
     * guessed: level with it and ten tiles south, clear of the hall's pillars and doors.
     */
    val BATTERY_SPOTS: List<Pair<Int, Int>> = listOf(-9 to 1, 8 to 1, -9 to -9, 8 to -9)
    const val SOUTH_EAST = 3

    /** A whole beam's damage, before it is shared: [BEAM_PER_PLAYER] for each player in the fight, [BEAM_MIN] at least. */
    const val BEAM_MIN = 80
    const val BEAM_PER_PLAYER = 40

    /**
     * A beam's damage with [alive] players in the fight and no inert guardian behind its target, shared out among those
     * in its line: 80 for one or two, which a player alone can live through at high hitpoints or with a tick-eat, and
     * 120 and up from three, which kills one player alone (as it did Sam, in a party of four). Guessed, to fit the
     * Strategies page's words.
     */
    fun beamDamage(alive: Int): Int = max(BEAM_MIN, BEAM_PER_PLAYER * alive)

    /** The fight, or null when the cache hasn't the Guardian's two forms (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val flying = types[FLYING] ?: return null
        val grounded = types[GROUNDED] ?: return null
        // A cache without the vine or the inert guardians (a test world's) has the fight without them.
        val vine = types[VINE]
        val batteries = Batteries.of(types)
        return EncounterDef(
            NAME, flying,
            listOf(
                PhaseDef(
                    "shield",
                    cycle = listOf(CycleStep(1) { shielded(batteries) }),
                    onStart = { raise(flying, grounded, batteries) },
                    endsWhen = { shieldBroken() },
                ),
                PhaseDef(
                    "grounded",
                    cycle = listOf(CycleStep(1) { downed(vine) }),
                    endsWhen = { liftsOff() },
                    // To its shield again, or with its hitpoints down to the last phases', to the shield with the beams.
                    next = { if (state.low >= 0) "beams" else "shield" },
                    onStart = { ground(grounded, batteries) },
                ),
                PhaseDef(
                    "beams",
                    cycle = listOf(
                        CycleStep(1) {
                            shielded(batteries)
                            beams(batteries)
                        },
                    ),
                    onStart = {
                        val state = state
                        // Jumped straight here with ::raid: the grounded hitpoints, down to where this phase begins.
                        if (state.health < 0 && boss.type.id != grounded.id) {
                            pool(grounded)
                            state.health = boss.maxHitpoints * LOW_PERCENT / 100
                            pool(flying)
                            boss.hitpoints = boss.maxHitpoints
                        }
                        raise(flying, grounded, batteries)
                        state.beamsFrom = world.tick
                        state.chargeDue = -1
                    },
                    // No timer on this shield: the party's damage breaks it, or a spent inert guardian's charge.
                    endsWhen = { chargeBreaks() || shieldBroken() },
                ),
                PhaseDef(
                    "final",
                    cycle = listOf(
                        CycleStep(1) {
                            downed(vine)
                            beams(batteries)
                            chargeOnHealth()
                        },
                    ),
                    onStart = {
                        val state = state
                        // Jumped straight here with ::raid: as from the beams' shield, with the beams' count from now.
                        if (state.health < 0 && boss.type.id != grounded.id) {
                            pool(grounded)
                            state.health = boss.maxHitpoints * LOW_PERCENT / 100
                        }
                        if (state.beamsFrom < 0) state.beamsFrom = world.tick
                        ground(grounded, batteries)
                        // A phase's first tick runs none of its cycle: a target due to be named on it is named here,
                        // or that beam would be skipped and the count broken.
                        beams(batteries)
                    },
                ),
            ),
            scriptedAttacks = true,            // Their damage to it counts for whoever beat them to 0 (R28A-3), counting hits that land on one while it
            // sleeps, which it keeps when it wakes (Codex on #649).
            charged = setOf(BATTERY_ACTIVE, BATTERY_INACTIVE),
        )
    }

    /** A tick behind a shield: a shot or a slam every [ATTACK_SPEED] ticks, mushrooms, and the inert guardians charged; nothing once it is broken. */
    private fun EncounterContext.shielded(batteries: Batteries?) {
        if (state.shieldDown >= 0) return
        if (phaseTick % ATTACK_SPEED == 0L) shoot()
        if (phaseTick % MUSHROOMS_EVERY == 0L) mushrooms()
        if (batteries != null) charge(batteries)
    }

    /** A tick on the ground: a swing every [ATTACK_SPEED] ticks, and its vines. */
    private fun EncounterContext.downed(vine: NpcType?) {
        if (phaseTick % ATTACK_SPEED == 0L) strike()
        if (vine != null && phaseTick >= VINES_FROM && (phaseTick - VINES_FROM) % VINES_EVERY == 0L) vines(vine)
    }

    /** Behind a whole shield: as the fight begins, or up from the ground, when what it has left is kept for its next time down. */
    private fun EncounterContext.raise(flying: NpcType, grounded: NpcType, batteries: Batteries?) {
        val state = state
        state.shieldDown = -1
        if (boss.type.id == grounded.id) {
            state.health = boss.hitpoints
            form(flying)
            pool(flying)
            boss.hitpoints = boss.maxHitpoints
            boss.effects.anim(ANIM_TO_FLYING)
            boss.effects.spotanim(0, SPOT_TO_FLYING, 0)
            log("lifts off with ${state.health} hp left, its shield whole: ${boss.hitpoints}")
        }
        boss.holdsAtZero = true
        form(flying)
        if (batteries != null) placeBatteries(batteries, awake = true)
        tank()
    }

    /** Down to the ground, blasting whoever is under or beside it: its hitpoints full the first time, after that what it had left as it last lifted off. */
    private fun EncounterContext.ground(grounded: NpcType, batteries: Batteries?) {
        val state = state
        state.groundings++
        state.low = -1
        boss.holdsAtZero = false
        form(grounded)
        pool(grounded)
        boss.hitpoints = state.health.takeIf { it > 0 }?.coerceAtMost(boss.maxHitpoints) ?: boss.maxHitpoints
        // The bar shows the hitpoints at once: the shield's last hit left it empty (Codex on #641; Sam's recording of
        // 10 November 2026 has the bar go from the shield straight to the hitpoints).
        boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        boss.effects.anim(ANIM_TO_GROUND)
        boss.effects.spotanim(0, SPOT_TO_GROUND, 0)
        // No shield to charge now: those not charged fall asleep again, till a shield or a beam. Guessed.
        if (batteries != null) placeBatteries(batteries, awake = false)
        val size = boss.type.size
        val around = (boss.x - 1..boss.x + size).flatMap { x -> (boss.z - 1..boss.z + size).map { z -> Tile(x, z) } }
        hazard(around, warning = -1, warnTicks = 0, ticks = 1, minDamage = BLAST_MIN, maxDamage = BLAST_MAX)
        log("grounded with ${boss.hitpoints} hp")
    }

    /** Shows the boss as [type] for good, unless it is that already. */
    private fun EncounterContext.form(type: NpcType) {
        if (boss.type.id != type.id) world.npcs.transform(boss, type, Npcs.FOREVER)
    }

    /**
     * Its most hitpoints from here on: [form]'s for its party size ([PartyScaling]), or the form's own when it isn't
     * scaled. The grounded form's stay its most through the last phase, back in the levitating form.
     */
    private fun EncounterContext.pool(form: NpcType) {
        val party = boss.scaledFor
        boss.scaledHitpoints = (if (party > 0) world.partyScaling.hitpoints(form, party) else null) ?: form.stats.hitpoints
    }

    /** What the fight keeps between its phases. */
    private class State {
        /** The tick its shield reached 0 this time up; -1 while it holds. */
        var shieldDown = -1L

        /** What it had left as it last lifted off behind its shield; -1 before that. */
        var health = -1

        /** How many times it has come down. */
        var groundings = 0

        /** The tick its hitpoints were first seen at the last phases' this time down, kept though they rise again; -1 before. */
        var low = -1L

        /** The tick the beams' shield went up, which its beams are counted from to its death; -1 before. */
        var beamsFrom = -1L

        /** The tick a spent inert guardian's charge reaches it; -1 with none on its way. */
        var chargeDue = -1L

        /** Whose beam spent the inert guardian whose charge is on its way: its damage counts for them (R28A-3). */
        var chargeBy: WeakReference<Player>? = null
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    /** Whether its shield has been at 0 for [SHIELD_DOWN_TICKS] ticks: broken the tick it is first seen there. */
    private fun EncounterContext.shieldBroken(): Boolean {
        val state = state
        if (state.shieldDown < 0) {
            if (boss.hitpoints > 0) return false
            state.shieldDown = world.tick
            log("shield broken")
        }
        return world.tick - state.shieldDown >= SHIELD_DOWN_TICKS
    }

    /** Whether its hitpoints are down to the last phases'. */
    private fun EncounterContext.low(): Boolean = boss.hitpoints * 100 <= boss.maxHitpoints * LOW_PERCENT

    /**
     * Whether its time on the ground is up: [GROUNDED_FIRST] ticks the first time and [GROUNDED_LATER] after, or
     * [LOW_DELAY] ticks after its hitpoints fell to the last phases'.
     */
    private fun EncounterContext.liftsOff(): Boolean {
        val state = state
        if (state.low < 0 && low()) {
            state.low = world.tick
            log("down to $LOW_PERCENT%: lifts off in $LOW_DELAY")
        }
        // Guessed: once it has been that low the count runs its course, though its time on the ground would be up
        // sooner or a hitpoint has come back since.
        if (state.low >= 0) return world.tick - state.low >= LOW_DELAY
        return phaseTick >= if (state.groundings <= 1) GROUNDED_FIRST else GROUNDED_LATER
    }

    /** Its tank: the player it fights while they're in the fight, or one picked at random and taken on now. */
    private fun EncounterContext.tank(): Player? {
        val fighting = players
        boss.target?.let { held -> if (fighting.any { it === held }) return held }
        val pick = randomPlayer() ?: return null
        // Its old tank has left the fight (out of the hall, say): let go of them, or the boss would keep them as its
        // target ([MeleeCombat.aggress] keeps a scripted boss's living target) and pick afresh at every shot.
        boss.target = null
        world.meleeCombat.aggress(boss, pick, world.tick)
        log("takes on ${pick.username}")
        return pick
    }

    /** Levitating: a shot at its tank, or a slam on whoever stands under it. */
    private fun EncounterContext.shoot() {
        val under = players.filter { boss.covers(it.x, it.z) }
        if (under.isNotEmpty()) {
            boss.effects.anim(ANIM_SLAM)
            for (player in under) {
                // A hit can end the fight (Retribution killing the boss); nothing lands after that.
                if (gone()) return
                val damage = if (world.prayers.protects(player, AttackType.CRUSH)) 0 else roll(0, SLAM_MAX)
                val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                log("slams ${player.username} for $dealt")
            }
            return
        }
        var tank = tank() ?: return
        // Its tank out of its range (10) or sight, it turns on someone in it at once, and holds its fire only when nobody
        // is: it never moves to get them in it. Guessed; without the turn, a tank hiding in the hall let the rest fight it
        // for nothing (exploit review round 13, R13A-1).
        if (!world.meleeCombat.inShot(boss, tank)) {
            val inShot = players.filter { it !== tank && world.meleeCombat.inShot(boss, it) }
            if (inShot.isEmpty()) {
                log("can't reach ${tank.username}")
                return
            }
            val pick = inShot[roll(0, inShot.size - 1)]
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
            log("turns from ${tank.username} to ${pick.username}")
            tank = pick
        }
        world.meleeCombat.fireAt(boss, tank, world.tick)
        log("shoots ${tank.username}")
    }

    /** Grounded: a swing at its tank if they are beside it, else at someone who is; nothing when nobody is. */
    private fun EncounterContext.strike() {
        val near = players.filter { inReach(it) }
        if (near.isEmpty()) return
        val held = boss.target
        val target = near.firstOrNull { it === held } ?: near[roll(0, near.size - 1)]
        world.meleeCombat.swingAt(boss, target, world.tick)
        log("strikes ${target.username}")
    }

    /** Whether the boss is dead or out of the world, as a hit mid-attack can leave it. */
    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss

    /** Under it, or beside it but not at a corner, as a melee monster's reach goes. Guessed: walls aren't asked. */
    private fun EncounterContext.inReach(player: Player): Boolean {
        val size = boss.type.size
        val dx = max(0, max(boss.x - player.x, player.x - (boss.x + size - 1)))
        val dz = max(0, max(boss.z - player.z, player.z - (boss.z + size - 1)))
        return (dx == 0 && dz <= 1) || (dz == 0 && dx <= 1)
    }

    /** The inert guardians' three forms. */
    class Batteries(val asleep: NpcType, val awake: NpcType, val spent: NpcType) {
        val ids = setOf(asleep.id, awake.id, spent.id)

        companion object {
            /** Null when the cache hasn't all three (a test world's). */
            fun of(types: NpcTypes): Batteries? {
                return Batteries(types[BATTERY_INACTIVE] ?: return null, types[BATTERY_ACTIVE] ?: return null, types[BATTERY_SPENT] ?: return null)
            }
        }
    }

    /** The fight's inert guardians, in [BATTERY_SPOTS]'s order. */
    private fun EncounterContext.batteryList(batteries: Batteries): List<Npc> = adds.filter { it.spawn.type.id in batteries.ids }

    /**
     * The four inert guardians, put round the hall the first time a phase asks; after that, those not spent are woken
     * ([awake], the shield phase's, all but the south-east one) or put to sleep. They never fight back or move, and hold
     * at 0 hitpoints, charged, till the fight sees to them.
     */
    private fun EncounterContext.placeBatteries(batteries: Batteries, awake: Boolean) {
        var list = batteryList(batteries)
        if (list.isEmpty()) {
            val cx = boss.x + boss.type.size / 2
            val cz = boss.z + boss.type.size / 2
            list = BATTERY_SPOTS.mapNotNull { (dx, dz) ->
                spawnAdd(batteries.asleep, cx + dx, cz + dz)?.also {
                    it.scriptedAttacks = true
                    it.holdsAtZero = true
                    // The asleep form has 1 hitpoint in the cache, and waking keeps what it has: the awake form's
                    // hitpoints from the start, in every form, so waking it gives a full 180 to beat down.
                    it.scaledHitpoints = batteries.awake.stats.hitpoints
                    it.hitpoints = batteries.awake.stats.hitpoints
                }
            }
        }
        for ((i, battery) in list.withIndex()) {
            if (battery.type.id == batteries.spent.id) continue
            val type = if (awake && i != SOUTH_EAST) batteries.awake else batteries.asleep
            if (battery.type.id == type.id) continue
            world.npcs.transform(battery, type, Npcs.FOREVER)
            if (type.id == batteries.asleep.id) untarget(battery)
        }
    }

    /**
     * [battery] has lost its Attack (asleep or spent): whoever was attacking it stops, as they would if it had gone,
     * rather than swinging, shooting or casting on at a target that holds at 0 (Codex on #481). A hit already on its
     * way still lands.
     */
    private fun EncounterContext.untarget(battery: Npc) {
        for (player in world.onlinePlayers) {
            val on = when (val fight = player.interaction) {
                is MeleeInteraction -> fight.npc
                is RangedInteraction -> fight.npc
                is MagicInteraction -> fight.npc
                else -> null
            }
            if (on !== battery) continue
            player.interaction = null
            world.npcInteractions.stopFacing(player)
        }
    }

    /**
     * An awake inert guardian beaten to 0 is charged: it takes [BATTERY_SHIELD_PERCENT] of the shield's most hitpoints off
     * the Guardian and is spent.
     */
    private fun EncounterContext.charge(batteries: Batteries) {
        for (battery in batteryList(batteries)) {
            if (battery.type.id != batteries.awake.id || battery.hitpoints > 0) continue
            world.npcs.transform(battery, batteries.spent, Npcs.FOREVER)
            untarget(battery)
            val dealt = world.meleeCombat.hit(boss, null, boss.maxHitpoints * BATTERY_SHIELD_PERCENT / 100, world.tick)
            // For whoever beat it to 0 (exploit review R28A-3).
            world.archiveRaids.creditFrom(battery, boss, dealt)
            log("inert guardian at ${battery.x},${battery.z} charged: shield takes $dealt")
            if (gone()) return
        }
    }

    /**
     * The first inert guardian not spent behind [target] on a beam's [line] (past them, away from the Guardian), or
     * null, as it is when [target] stands past the line's end. A 2 by 2 guardian is behind them when the line crosses
     * any of its tiles.
     */
    private fun EncounterContext.behind(batteries: Batteries, line: Set<Tile>, target: Tile): Npc? {
        // A target past the beam's reach isn't on its line: nothing is behind them, only between.
        val tiles = line.toList()
        val at = tiles.indexOf(target)
        if (at < 0) return null
        val after = tiles.drop(at + 1)
        val standing = batteryList(batteries).filter { it.type.id != batteries.spent.id }
        for (tile in after) {
            standing.firstOrNull { it.covers(tile.x, tile.z) }?.let { return it }
        }
        return null
    }

    /**
     * The beams' count, from the tick their shield went up to the Guardian's death: every [BEAM_EVERY] ticks a beam,
     * its target picked at random and named in chat [BEAM_WARNING] ticks before.
     */
    private fun EncounterContext.beams(batteries: Batteries?) {
        val since = world.tick - state.beamsFrom + BEAM_WARNING
        if (since < BEAM_EVERY || since % BEAM_EVERY != 0L) return
        val target = randomPlayer() ?: return
        for (player in players) {
            // Each in their own words: a player with ::keys on reads the line's key.
            val words = world.textFor(player)
            player.message(if (player === target) words[ArchiveGuardianLines.FOCUS_YOU] else words[ArchiveGuardianLines.FOCUS, "name" to target.username])
        }
        log("focuses on ${target.username}")
        after(BEAM_WARNING) { beam(batteries, target) }
    }

    /** Its beam showing, aimed through [target] where they stand, reaching that line [BEAM_TRAVEL] ticks later ([beamLands]). */
    private fun EncounterContext.beam(batteries: Batteries?, target: Player) {
        if (players.none { it === target }) {
            log("beam has nobody to land on")
            return
        }
        boss.effects.anim(ANIM_LASER)
        boss.effects.spotanim(0, SPOT_LASER, 0)
        boss.face(target)
        val at = Tile(target.x, target.z)
        log("beam fires at ${target.username}")
        after(BEAM_TRAVEL) { beamLands(batteries, target, at) }
    }

    /**
     * Its beam reaching its line, from its middle through [at], where [target] stood as it showed, and on: those on the
     * line now, and [target] wherever they are. An inert guardian behind it takes it and is spent, its charge on its way
     * to the Guardian, and those on the line take little; with none there they share the whole of it.
     */
    private fun EncounterContext.beamLands(batteries: Batteries?, target: Player, at: Tile) {
        if (gone()) return
        val fighting = players
        val line = line(boss.x, boss.z, boss.type.size, at)
        val struck = fighting.filter { it === target || Tile(it.x, it.z) in line }
        val statue = batteries?.let { behind(it, line, at) }
        if (batteries != null && statue != null) {
            spend(batteries, statue)
            state.chargeDue = world.tick + BEAM_CHARGE_DELAY
            state.chargeBy = WeakReference(target)
            log("beam spends the inert guardian at ${statue.x},${statue.z}")
            for (player in struck) {
                // A hit can end the fight (Retribution killing the boss); nothing lands after that.
                if (gone()) return
                val dealt = world.meleeCombat.hitPlayer(player, boss, roll(BEAM_BLOCKED_MIN, BEAM_BLOCKED_MAX), world.tick)
                log("beam hits ${player.username} for $dealt")
            }
            return
        }
        // Its target fell or left in the tick it took, with nobody else on the line: the whole beam has nobody to share
        // it (Codex on #641). An inert guardian behind still takes it, above.
        if (struck.isEmpty()) {
            log("beam lands on nobody")
            return
        }
        val total = beamDamage(fighting.size)
        val share = (total + struck.size - 1) / struck.size
        for (player in struck) {
            if (gone()) return
            val damage = if (world.prayers.protects(player, AttackType.MAGIC)) share * 9 / 10 else share
            val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
            log("beam hits ${player.username} for $dealt")
        }
    }

    /** [statue] takes a beam: [statueSplat] for the party on it over an empty bar, and it is spent, whatever it had left and awake or not. */
    private fun EncounterContext.spend(batteries: Batteries, statue: Npc) {
        statue.hitpoints = 0
        statue.effects.hit(-1, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER, statueSplat(boss.scaledFor))
        statue.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(0, statue.maxHitpoints))
        world.npcs.transform(statue, batteries.spent, Npcs.FOREVER)
        untarget(statue)
    }

    /**
     * Behind the beams' shield, a spent inert guardian's charge reaching the Guardian: [BATTERY_SHIELD_PERCENT] of the
     * shield's most hitpoints off it. Whether that breaks it: with no more than that left it does, at once, the
     * Guardian down on this tick with no hold at 0, its splat what was left (Sam's recording of 10 November 2026: a
     * 348 splat with 348 of 1,030 left, then the bar straight to the health).
     */
    private fun EncounterContext.chargeBreaks(): Boolean {
        val state = state
        if (state.chargeDue < 0 || world.tick < state.chargeDue) return false
        state.chargeDue = -1
        val share = boss.maxHitpoints * BATTERY_SHIELD_PERCENT / 100
        if (boss.hitpoints <= share) {
            val left = boss.hitpoints
            if (left > 0) chargedBy(world.meleeCombat.hit(boss, null, left, world.tick))
            log("the spent inert guardian's charge breaks its shield, $left left")
            return true
        }
        val dealt = world.meleeCombat.hit(boss, null, share, world.tick)
        chargedBy(dealt)
        log("the spent inert guardian's charge: shield takes $dealt")
        return false
    }

    /**
     * Down for the last time, that charge hurts the Guardian itself: [BATTERY_BEAM_PERCENT] of its most hitpoints, but
     * never into kill range (guides: the beam's inert guardian "damages the boss's health"; in kill range "they stop
     * dealing damage to the boss"). The figures are guessed.
     */
    private fun EncounterContext.chargeOnHealth() {
        val state = state
        if (state.chargeDue < 0 || world.tick < state.chargeDue) return
        state.chargeDue = -1
        val floor = boss.maxHitpoints * KILL_RANGE_PERCENT / 100
        val damage = minOf(boss.maxHitpoints * BATTERY_BEAM_PERCENT / 100, boss.hitpoints - floor).coerceAtLeast(0)
        val dealt = if (damage > 0) world.meleeCombat.hit(boss, null, damage, world.tick) else 0
        chargedBy(dealt)
        log("the spent inert guardian's charge: Guardian takes $dealt")
    }

    /** A beam-spent inert guardian's charge dealt [dealt]: it counts for whoever the beam went through (R28A-3). */
    private fun EncounterContext.chargedBy(dealt: Int) {
        state.chargeBy?.get()?.let { world.archiveRaids.creditTo(it, boss, dealt) }
        state.chargeBy = null
    }

    /**
     * A mushroom by each player, on a free tile two from them, not under the Guardian: growing for [MUSHROOM_GROW] ticks,
     * then ready till someone comes within a tile or [MUSHROOM_FUSE] ticks pass, when it bursts the tick after. Running
     * up to one and on two tiles pops it harmlessly (a guide's); walking off leaves a player in the splash.
     */
    private fun EncounterContext.mushrooms() {
        val taken = HashSet<Tile>()
        for (player in players) {
            val tiles = (-2..2).flatMap { dx -> (-2..2).map { dz -> Tile(player.x + dx, player.z + dz) } }
                .filter { max(abs(it.x - player.x), abs(it.z - player.z)) == 2 }
                .filter { it !in taken && !boss.covers(it.x, it.z) && world.canStand(it.x, it.z, boss.level) }
                // Nor on one whose last mushroom hasn't burst yet: a wave comes every 15 ticks, and a mushroom lasts
                // up to 22. Its burst would clear the tile, hiding the new one while it still went off.
                .filter { tile -> world.locs.at(tile.x, tile.z, boss.level).none { it.id in MUSHROOM_LOCS } }
                .filter { tile -> players.none { it.x == tile.x && it.z == tile.z } }
            if (tiles.isEmpty()) continue
            val tile = tiles[roll(0, tiles.size - 1)]
            taken += tile
            val growing = placeLoc(MUSHROOM_SPAWN, tile.x, tile.z)
            world.zones.locAnim(tile.x, tile.z, boss.level, growing.shape, growing.angle, ANIM_MUSHROOM_SPAWN)
            log("mushroom at ${tile.x},${tile.z} by ${player.username}")
            after(MUSHROOM_GROW) {
                placeLoc(MUSHROOM_ACTIVE, tile.x, tile.z)
                watch(tile, 0)
            }
        }
    }

    private fun EncounterContext.watch(tile: Tile, waited: Int) {
        after(1) {
            val near = players.any { max(abs(it.x - tile.x), abs(it.z - tile.z)) <= 1 }
            if (!near && waited + 1 < MUSHROOM_FUSE) return@after watch(tile, waited + 1)
            val bursting = placeLoc(MUSHROOM_DESPAWN, tile.x, tile.z)
            after(1) {
                burst(tile)
                clearLoc(bursting)
            }
        }
    }

    /** A mushroom bursting: poison and weariness within a tile of it ([Player.wearyTicks]), a splash two tiles off. */
    private fun EncounterContext.burst(tile: Tile) {
        for (player in players) {
            if (gone()) return
            val distance = max(abs(player.x - tile.x), abs(player.z - tile.z))
            if (distance > 2) continue
            if (distance == 2) {
                val dealt = world.meleeCombat.hitPlayer(player, boss, roll(SPLASH_MIN, SPLASH_MAX), world.tick)
                log("mushroom splashes ${player.username} for $dealt")
                continue
            }
            val dealt = world.meleeCombat.hitPlayer(player, boss, roll(MUSHROOM_MIN, MUSHROOM_MAX), world.tick)
            log("mushroom bursts on ${player.username} for $dealt")
            if (player.dead || player.hitpoints <= 0) continue
            // Weary whether or not the poison takes: an antipoison drunk beforehand "avoids the poison damage only"
            // (Strategies, its equipment table), and a player poisoned as strongly already, or envenomed, is wearied
            // again all the same. The Serpentine helm shortening it isn't in: there is no helm yet.
            player.runEnabled = false
            world.poison.poison(player, MUSHROOM_POISON)
            player.wearyTicks = WEARY_TICKS
        }
    }

    /** A vine by each player, on the tile beside them away from the Guardian's middle; none for one under it. */
    private fun EncounterContext.vines(type: NpcType) {
        val cx = boss.x + boss.type.size / 2
        val cz = boss.z + boss.type.size / 2
        for (player in players) {
            if (boss.covers(player.x, player.z)) continue
            val x = player.x + (player.x - cx).sign
            val z = player.z + (player.z - cz).sign
            val vine = spawnAdd(type, x, z) ?: continue
            vine.effects.anim(ANIM_VINE_SPAWN)
            world.zones.mapAnim(x, z, boss.level, SPOT_VINE_SPAWN)
            swipe(vine, 1)
        }
    }

    private fun EncounterContext.swipe(vine: Npc, swipe: Int) {
        after(VINES_EVERY) {
            if (world.npcs[vine.index] !== vine) return@after
            vine.effects.anim(ANIM_VINE_SWIPE)
            for (player in players) {
                if (gone()) return@after
                if (max(abs(player.x - vine.x), abs(player.z - vine.z)) > 1) continue
                val damage = if (world.prayers.protects(player, AttackType.CRUSH)) roll(0, VINE_PROTECTED_MAX) else roll(0, VINE_MAX)
                val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                log("vine hits ${player.username} for $dealt")
            }
            if (swipe < VINE_SWIPES) return@after swipe(vine, swipe + 1)
            after(1) { vine.effects.anim(ANIM_VINE_DESPAWN) }
            after(2) { if (world.npcs[vine.index] === vine) world.removeNpc(vine) }
        }
    }

    /**
     * A beam's tiles: from the middle of a boss of [size] whose south-west tile is [bossX], [bossZ], through
     * [through] and on, [BEAM_LENGTH] of them; [through] alone when it is the middle.
     */
    internal fun line(bossX: Int, bossZ: Int, size: Int, through: Tile): Set<Tile> {
        val cx = bossX + size / 2
        val cz = bossZ + size / 2
        val dx = through.x - cx
        val dz = through.z - cz
        val steps = max(abs(dx), abs(dz))
        if (steps == 0) return setOf(through)
        return (1..BEAM_LENGTH).mapTo(LinkedHashSet()) { k ->
            Tile(cx + (dx.toDouble() * k / steps).roundToInt(), cz + (dz.toDouble() * k / steps).roundToInt())
        }
    }
}
