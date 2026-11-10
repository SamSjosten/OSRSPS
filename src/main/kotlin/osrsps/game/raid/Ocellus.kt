package osrsps.game.raid

import java.util.WeakHashMap
import kotlin.math.sign
import osrsps.game.NpcType
import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.HitsplatType
import osrsps.game.combat.MagicInteraction
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.MeleeInteraction
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.combat.RangedInteraction
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.Projectile
import osrsps.map.Tile

/**
 * What Ocellus says over his head, and what his room tells a player. The overhead words are those the tsps capture notes
 * quote and Sam's recordings of 2026-11-09 show; the ready lines are the game's own from those recordings. All of them
 * are typed into the dialogue file like every other line.
 */
object OcellusLines : Lines("tfa_ocellus", "The_Fractured_Archive/Strategies") {
    val ASCENSION = line("ascension", "Ocellus as his fight begins")
    val CORNER = (1..3).map { line("corner_$it", "Ocellus three ticks after his last crack at a corner: the ${ordinal(it)} of the three he picks from") }
    val READY = line("ready", "chat, to a player readying up at the hooded figure: how many of the party are ready", "ready", "total")
    val READY_WAIT = line("ready_wait", "chat, with the ready count: the fight begins once everyone else has come in and readied, or 2 minutes after the first ready")
    val DEATH_AWAITS = line("death_awaits", "Ocellus at the middle, eight ticks after his last side, his charge under 12: everyone is hit a little")
    val ANNIHILATION = line("annihilation", "Ocellus at the middle, eight ticks after his last side, his charge 12 or more: everyone is hit hard")
    val WILL_DISAPPEAR = line("will_disappear", "Ocellus with his first-phase bar run out, before he changes to his second phase")
    val YOUR_TIME = line("your_time", "Ocellus seven ticks into his second phase, as the Blade appears beside him")
    val THIS_IS_ENOUGH = line("this_is_enough", "Ocellus as his second phase ends, in the middle of the third quadrant's turn")
    val OBLITERATION = line("obliteration", "Ocellus in his third phase as his quadrant's barriers drop (at 75, 50 and 25%): one of the two he picks from")
    val RUN = line("run", "Ocellus in his third phase as his quadrant's barriers drop (at 75, 50 and 25%): the other of the two")
    val THIS_END = line("this_end", "Ocellus in his third phase, eleven ticks after his quadrant's barriers drop: the first of the three he picks from")
    val EVERYTHING_ENDS = line("everything_ends", "Ocellus in his third phase, eleven ticks after his quadrant's barriers drop: the second of the three")
    val CANT_ESCAPE = line("cant_escape", "Ocellus in his third phase, eleven ticks after his quadrant's barriers drop: the third of the three")
    val TURNS_TO = line("turns_to", "chat, to his party, as he takes a new tank in his third phase: the tank's name", "name")
    val WAY_BACK_CLOSED = line("way_back_closed", "chat, to a player trying to jump the stepping stones back the way the fight came")

    private fun ordinal(n: Int) = listOf("first", "second", "third")[n - 1]
}

/**
 * Ocellus (raid steps 24 to 26), the last boss of The Fractured Archive, in the vault's arena on its upper floor: his
 * first phase, the shielded one, whose blue bar is his first-phase hitpoints, his second, the Blade's, and his third.
 *
 * - The hooded figure ([INTRO]) waits in the arena; its options ready a player up, and the fight begins once every one
 *   of the party standing has come into the room and readied, or [READY_TIMEOUT] ticks after the first ready
 *   ([ArchiveRaids]). He becomes his first phase ([P1]) where the figure stood and says [OcellusLines.ASCENSION].
 * - He walks from corner to corner of the arena, anticlockwise ([CORNERS]), from a random first one, a tile a tick,
 *   with Protect from Melee up as he walks: melee does nothing to him then ([Npc.protectsFromMelee]). Every
 *   [SHOT_EVERY] ticks of walking he throws a fireball (ranged) or a water ball (magic) at everyone.
 * - The tick after he reaches a corner, [TENDRILS] cracks [TENDRIL_GAP] ticks apart ([tendril]): each [TENDRIL_LENGTH]
 *   tiles in a line from his footprint out, one of seven spots ([spots]), not aimed at anyone. A crack shows
 *   [TENDRIL_SHOWS] ticks after it is laid and its energy flows back into him [TENDRIL_FLOWS] after. Whoever stands on it
 *   then intercepts it ([intercept]): healed what they lost since their last interception, from the most they had since,
 *   then a small hit ([CRACK_MIN] to [CRACK_MAX]). With nobody on it, he charges [CHARGE_PER] on his second bar, up to
 *   [CHARGE_MAX].
 * - [CORNER_LINE] ticks after the last crack one of his three lines, [EXPLODE_AFTER_LINE] after that his explosion: the
 *   ring of tiles round him, then the ring outside it a tick later ([explode]). [WALK_AFTER_EXPLODE] after it he walks on.
 * - At two thirds and at a quarter of his bar (raid step 24b), whatever he is doing, he goes to the middle ([middleStep]):
 *   he teleports out, and [MIDDLE_IN] ticks later in at the arena's middle. Then four sides, clockwise from a random one,
 *   [SIDE_GAP] ticks apart: on each, a diagonal crack [SIDE_LENGTH] long from each end of that side, the second
 *   [SIDE_TENDRIL_GAP] ticks after the first, each shown at once and flowing back [SIDE_FLOWS] after it is laid, caught or
 *   charging him as a corner's cracks do; [SIDE_EXPLODES] ticks after a side begins, his explosion. [MIDDLE_LINE] ticks
 *   after the last side begins, [OcellusLines.DEATH_AWAITS], or from a charge of [ANNIHILATION_CHARGE]
 *   [OcellusLines.ANNIHILATION]; [EXPEND_AFTER_LINE] after it he expends his charge on everyone, unavoidably, and it
 *   empties; [WALK_AFTER_EXPEND] after that he walks on.
 * - His bar run out, he holds at it ([Npc.holdsAtZero]) and nothing hurts him; [HOLD_LINE] ticks on
 *   [OcellusLines.WILL_DISAPPEAR], and he teleports to the middle if he isn't there; [TRANSFORM_ANIM_AFTER] after the
 *   line his transformation, and [SECOND_PHASE_AFTER] after the line his second phase.
 * - His second phase (raid step 25a, [toSecondPhase]): he becomes [P2], with no Attack, on a bar of the remainder of his
 *   hitpoints for the party. [PORTALS_AT] ticks in, [PORTALS_PER_ARM] orange portals stand on each arm of the cross
 *   ([portalTiles]), blocking the way across. [BLADE_AT] ticks in, [OcellusLines.YOUR_TIME], and the Blade ([BLADE])
 *   stands with him: the party attacks it, and [BLADE_SHARE]% of its damage comes off his bar ([forwardBlade]).
 * - From then the quadrants take turns of [QUADRANT_TICKS] ticks, from the one most of the party stands in, clockwise
 *   ([quadrantStep]). In each, [VOLLEYS] volleys [VOLLEY_GAP] ticks apart: every portal on the two arms beside it turns
 *   purple but one of each, as [ORANGE] has it, and [VOLLEY_FIRES] ticks later each purple one shoots across the
 *   quadrant along its [STRIP_LENGTH]-tile strip ([strip]), hitting whoever it reaches ([PORTAL_MIN] to [PORTAL_MAX]).
 *   Where the two orange strips cross is safe.
 * - Later in a quadrant's turn (raid step 25b): [POOLS_AT] ticks in, purple pools on [POOL_TILES] of its tiles and on
 *   every player's ([pools]), bursting [POOL_LANDS] ticks later for [POOL_DAMAGE]; [ALL_PURPLE_AT] ticks in, every
 *   portal on its two arms purple; [FLOOR_PORTAL_AT] ticks in, an orange portal on its floor for [FLOOR_PORTAL_TICKS]
 *   ticks ([floorPortal]), which moves whoever steps on it to the same spot in the next quadrant clockwise; and
 *   [LAST_VOLLEY_AT] ticks in, every purple portal fires across the quadrant, killing whoever is still in it.
 * - [SECOND_PHASE_TICKS] ticks in, in the middle of the third quadrant's turn, [OcellusLines.THIS_IS_ENOUGH], and
 *   [THIRD_PHASE_AFTER] later his third phase (raid step 26a, [toThirdPhase]): he becomes [P3], with Attack, on the same
 *   bar, holding the quadrant the second phase ended in. The Blade drops, the portals go, and the cross falls
 *   ([thirdPhase]): the arms crack at [ARMS_CRACK_AT] (whoever is on them moved off), fall in at [ARMS_FALL_AT], and at
 *   [ARMS_EMPTY_AT] stand empty with three stepping stones each ([crossing]), when he takes his quadrant from its corner
 *   with its barriers up ([take]). His tank is the first to hit him there ([pickTank]); every [ATTACK_EVERY] ticks
 *   melee, melee and his slash on the tank, twice ([attack], [slash]), or a void shot at everyone with no tank in reach.
 *   At each of [FALLS_AT]% of his bar his quadrant falls ([startFall], [falling]): his line and the barriers down, so
 *   the party can Jump the stones clockwise to the next ([jump]); his second line; the fall, losing whoever is still on
 *   it; and he takes the next. STAND-IN until step 27: after the third fall, as his final stand would begin, he dies,
 *   and the room is done.
 *
 * From the OSRS Wiki (Ocellus, read 2026-11-10): his first phase's stats and bonuses (npc-combat.json), 3 by 3, the
 * 5-tick attack speed, magic and ranged, the max hit 36, the 60% Defence drain cap, poison and venom immunity, his
 * hitpoints by party size (party-scaling.json). From the tsps capture notes (RSPSApp/tsps docs/fractured-archive.md,
 * read 2026-11-10: spectator captures of three live kills on rev 241.1, so no damage on players): the hooded figure's
 * tile, his corners and their order, Protect from Melee while he walks, the shot every five ticks and its graphics,
 * the seven cracks at a corner, their spots, timings and length, the interception and the charge of 2 a crack on a bar
 * of 96, the line three ticks after the last crack and the explosion three after that, his walking on three after it,
 * the two rings, the middle at two thirds and a quarter and everything there (the teleport, the four sides and their
 * cracks, the line and the charge's threshold for the harder one, the expending, the walk on), the hold at the end of his
 * bar, his line then, his teleport to the middle and the transformation's timing, and every overhead line. From Sam's
 * recordings of 2026-11-09 (three runs, parties of three to five):
 * the arena on the upper floor (plane 1) and where a player comes in, the blue bar's hitpoints for four and five, the
 * first-phase level (1255), his line as it begins ([OcellusLines.ASCENSION]), the three corner lines, the thin bar under the blue one
 * rising in steps of about 2% of 96, players on a crack's line taking hits of 2 to 6, the ready lines, his softer middle line
 * ([OcellusLines.DEATH_AWAITS]) hitting everyone for 17 or 18 and emptying the charge bar with it, and the blue bar held for 13 ticks before the health
 * bar (so his line three ticks into the hold, the capture notes having ten from the line to the second phase). From the cache
 * (RuneLite's gameval tables for 242): the ids below. Step 25a: from the capture notes, the second phase's 123 ticks, the
 * portals' tiles and timing, the line and the Blade seven ticks in, the 43% of its damage that reaches his bar, the
 * quadrants' order and turns, the volleys, their purple and orange portals, the orange pattern, the strips and the
 * shots' speed, and their 20 to 25; from the Wiki, his remainder hitpoints by party size, the second phase's stats and
 * bonuses (the Blade's), its Defence drained to 0, the portal's max hit of 25, and a volley's shots hitting a player
 * once from each axis at most; from the 242 cache, the Blade's 1,000 hitpoints and 3 by 3, the second phase having no
 * Attack, and the portals' size and collision.
 *
 * Guessed, until a recording shows more (each also in the README): the order of the seven cracks (each spot once, in a
 * random order); the crack's small hit landing as its energy flows, after the heal; his first shot [SHOT_EVERY] ticks
 * after the phase begins and after he walks on from a corner; the explosion's damage
 * ([EXPLODE_MIN] to [EXPLODE_MAX]: the capture notes' "up to 35") with no prayer helping; the shots' landing by the
 * usual NPC flight times; which crack graphic is the straight one and which way each is turned; the figure's options
 * readying at once without the capture notes' estimated question first; the head icon's sprite (440:0); at the middle, his first
 * side a tick after he is in (as a corner's first crack is), on each side first the end a clockwise sweep round him reaches first (on
 * the north side the north-west end, then the north-east), no shots and Protect from
 * Melee down while he is there, the harder line's 80 to 90 landing as he expends the charge as
 * the softer one's does, and walking on to the corner he was going to (or from one, the next); a second middle
 * owed (both thresholds crossed at once) coming right after the first; while held, Protect from Melee down and no
 * cracks or shots, and the teleport to the middle at once with the line. Not sent: the fades of his teleports (the
 * engine has no NPC transparency yet) and the middle's walk-block loc (its id isn't in the capture notes).
 *
 * Guessed in step 25a: which portal id is orange and which purple, and which way round the north and south arms' are
 * turned; the first quadrant on a tie (the first clockwise from the north-west); a quadrant's first volley as its turn
 * begins; the shots' height and their hit landing the tick they reach a player; no prayer against them; the Blade
 * never hitting back, made whole each tick and its bar shown so, and never regaining hitpoints by itself; no
 * diminishing returns on its damage within a quadrant (the Wiki says there are, not how much). Not sent: the portals'
 * spawn and idle animations.
 *
 * Step 25b: from the capture notes, the pools' timing and their tiles (10 to 12 and every player's), every portal
 * purple 31 ticks in, the floor portal from 33 for seven ticks with the Blade's discharge and its graphics, the last
 * volley at 40 killing whoever is left, the line at the end of the phase; the notes' estimates, the pools' 25 and the
 * floor portal's destination (the same spot in the next quadrant). From the Wiki's changes, an immunity window after
 * the floor portal. Guessed: the pools' graphic, which tiles and no prayer against them, the floor portal at the
 * quadrant's middle, the immunity's [FLOOR_PORTAL_IMMUNE] ticks covering the pools as well as the shots.
 *
 * Step 26a: from the capture notes, the third phase on the same bar, the Blade's drop, the portals going, the arms'
 * cracking, falling and empty forms with their timings and graphics, the middle's void, the stones and the crossings'
 * ends, the quadrant the second phase ended in first and then clockwise from its corner with its barriers, his
 * rotation and his tank, the slash's wave, the falls at 75, 50 and 25% with his lines and their timings, the fallen
 * quadrant losing whoever is on it and the stones of the arms beside it; from the Wiki, the third phase's stats and
 * bonuses, his speed of 3 and max hits (melee 30, magic 36), and the void shot when he can't reach the tank; from the
 * 242 cache, the locs, their sizes and collision, and the animation and graphic ids by their names; from Sam's
 * recordings, the chat line naming his tank. Guessed: see the README (which arm loc is which, the stones' tiles, one
 * Jump over a whole arm, who is moved where off the cross, his corner in a quadrant, his first attack's timing, the
 * slash's roll and prayer, his lines picked at random, no attacks while a quadrant falls). Not sent: the barriers' locs
 * (tiles unknown), his fades, the fallen quadrant's floor locs.
 */
object Ocellus {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "ocellus"

    /** `tfa_ocellus_intro`: the hooded figure, a multinpc on `tfa_ocellus_met` (16672 not met, 16673 met). */
    const val INTRO = 16671
    const val INTRO_NOT_MET = 16672
    const val INTRO_MET = 16673

    /** `tfa_ocellus_p1`, his first phase, and `tfa_ocellus_p2`, his second (step 25). */
    const val P1 = 16674
    const val P2 = 16675

    /**
     * The figure's options that ready a player up: Talk-to (op 1) on the "Hooded Figure" a player who hasn't met him sees,
     * Talk-to or Proceed (op 3) on the "Ocellus" one who has sees (242 cache, `tfa_ocellus_intro_not_met` and `_met`).
     * That both ready up rather than Talk-to only talking is guessed.
     */
    val READY_OPS = listOf(1, 3)

    /** The fight begins this long after the first ready, whoever has readied: the game's "2 minutes" (Sam's recording). */
    const val READY_TIMEOUT = 200

    /** The hooded figure's south-west tile, where he becomes his first phase (tsps: 4126,5669). */
    const val FIGURE_X = 4126
    const val FIGURE_Z = 5669

    /** The arena's middle, his south-west tile there, this far from the figure's (tsps: 4126,5672). */
    const val MIDDLE_DZ = 3

    /**
     * His corners from the middle, anticlockwise from the south-east (tsps: 4132,5666, 4132,5678, 4120,5678, 4120,5666):
     * south-east, north-east, north-west, south-west.
     */
    val CORNERS: List<Pair<Int, Int>> = listOf(6 to -6, 6 to 6, -6 to 6, -6 to -6)

    /** npc_ocellus_phase1_*: his shots (the ranged one, the magic one), the cracks' charge, and the explosion. */
    const val ANIM_SHOT_RANGED = 14806
    const val ANIM_SHOT_MAGIC = 14807
    const val ANIM_CHARGE = 14809
    const val ANIM_EXPLODE = 14811

    /** npc_ocellus_phase1_expend, _teleport_out, _teleport_in and _transformation. */
    const val ANIM_EXPEND = 14810
    const val ANIM_TELEPORT_OUT = 14812
    const val ANIM_TELEPORT_IN = 14813
    const val ANIM_TRANSFORMATION = 14814

    /** vfx_ocellus_p1_teleport_out and _in, on him as he goes and comes. */
    const val SPOT_TELEPORT_OUT = 4266
    const val SPOT_TELEPORT_IN = 4267

    /** vfx_ocellus_fire_attack01_*: the fireball's cast, flight and impact. */
    const val SPOT_FIRE_CAST = 4231
    const val SPOT_FIRE = 4232
    const val SPOT_FIRE_HIT = 4233

    /** vfx_ocellus_water_attack01_*: the water ball's. */
    const val SPOT_WATER_CAST = 4228
    const val SPOT_WATER = 4229
    const val SPOT_WATER_HIT = 4230

    /**
     * vfx_ocellus_p1_floor_crack_01 and _02, each laid, shown and flowing (_start, plain, _end), the first straight and
     * the second diagonal (guessed), each turned four ways: by [crackSpot].
     */
    const val SPOT_CRACK_STRAIGHT_START = 4236
    const val SPOT_CRACK_STRAIGHT = 4240
    const val SPOT_CRACK_STRAIGHT_END = 4241
    const val SPOT_CRACK_DIAGONAL_START = 4248
    const val SPOT_CRACK_DIAGONAL = 4252
    const val SPOT_CRACK_DIAGONAL_END = 4253

    /** vfx_ocellus_p1_charge, on him as a crack's energy reaches him uncaught. */
    const val SPOT_CHARGE = 4260

    /** vfx_ocellus_p1_explode_npc and _floor: the explosion, on him and on the rings. */
    const val SPOT_EXPLODE = 4261
    const val SPOT_EXPLODE_FLOOR = 4262

    /** headicons_prayer (sprite group 440), Protect from Melee: index 0, inferred from the prayers' order. */
    const val HEAD_ICON_GROUP = 440
    const val HEAD_ICON_PROTECT_MELEE = 0

    /** The Wiki's attack speed: a shot every this many ticks while he walks. */
    const val SHOT_EVERY = 5

    /** The shots: the fireball, ranged, and the water ball, magic (tsps); their max hit is the Wiki's (npc-combat.json). */
    val FIREBALL = NpcRangedAttack(anim = ANIM_SHOT_RANGED, cast = SPOT_FIRE_CAST, projectile = SPOT_FIRE, impact = SPOT_FIRE_HIT)
    val WATER_BALL = NpcRangedAttack(anim = ANIM_SHOT_MAGIC, cast = SPOT_WATER_CAST, projectile = SPOT_WATER, impact = SPOT_WATER_HIT)

    /** The cracks at a corner (tsps): how many, how far apart, how long each is, and when one shows and flows. */
    const val TENDRILS = 7
    const val TENDRIL_GAP = 2
    const val TENDRIL_LENGTH = 8
    const val TENDRIL_SHOWS = 2
    const val TENDRIL_FLOWS = 5

    /** A crack's hit on whoever intercepts it: the 2 to 6 in Sam's recordings. */
    const val CRACK_MIN = 2
    const val CRACK_MAX = 6

    /** His charge: this much for each crack nobody catches, on a bar of this many (tsps). */
    const val CHARGE_PER = 2
    const val CHARGE_MAX = 96

    /** At a corner (tsps): his line this long after the last crack, the explosion this long after the line, and on. */
    const val CORNER_LINE = 3
    const val EXPLODE_AFTER_LINE = 3
    const val WALK_AFTER_EXPLODE = 3

    /** The explosion's damage, on each ring. The most is the capture notes' estimate; the least is guessed. */
    const val EXPLODE_MIN = 15
    const val EXPLODE_MAX = 35

    /** His bar's shares at which he goes to the middle (tsps): two thirds, then a quarter, as fractions. */
    val MIDDLE_AT: List<Pair<Int, Int>> = listOf(2 to 3, 1 to 4)

    /** At the middle (tsps): he comes in this long after he goes, and the sides are this many, this far apart. */
    const val MIDDLE_IN = 4
    const val SIDES = 4
    const val SIDE_GAP = 8

    /** His first side this long after he is in. Guessed, as a corner's first crack the tick after he arrives. */
    const val FIRST_SIDE = 1

    /** A side's cracks (tsps): how long, how far apart its two, when each flows back, and the explosion after it begins. */
    const val SIDE_LENGTH = 6
    const val SIDE_TENDRIL_GAP = 2
    const val SIDE_FLOWS = 4
    const val SIDE_EXPLODES = 6

    /** The line this long after the last side begins, the charge expended this long after it, and on (tsps). */
    const val MIDDLE_LINE = 8
    const val EXPEND_AFTER_LINE = 2
    const val WALK_AFTER_EXPEND = 7

    /**
     * The charge from which his line is [OcellusLines.ANNIHILATION] (the capture notes' estimate), and what each line's
     * expending deals everyone: 17 to 18 (Sam's recordings; the notes agree) or 80 to 90 (the notes' estimate).
     */
    const val ANNIHILATION_CHARGE = 12
    const val DEATH_MIN = 17
    const val DEATH_MAX = 18
    const val ANNIHILATION_MIN = 80
    const val ANNIHILATION_MAX = 90

    /** His bar run out: his line this long into the hold, his transformation and second phase this long after it. */
    const val HOLD_LINE = 3
    const val TRANSFORM_ANIM_AFTER = 3
    const val SECOND_PHASE_AFTER = 10

    /** `tfa_ocellus_p2_sword_hitbox`: the Blade beside him in his second phase, which the party attacks (242 cache). */
    const val BLADE = 16676

    /**
     * `Portal`, 2 by 1, blocking the way but not shots (242 cache): the orange one standing idle and the purple one about
     * to fire (`quadrant_wall_active`, tsps). Which id is which colour is guessed.
     */
    const val LOC_PORTAL = 62684
    const val LOC_PORTAL_ACTIVE = 62685

    /** vfx_ocellus_portal_projectile: a purple portal's shot across the quadrant. */
    const val SPOT_PORTAL_SHOT = 4269

    /** His second phase (tsps): the portals this long in, his line and the Blade this long in, and its end. */
    const val PORTALS_AT = 2
    const val BLADE_AT = 7
    const val SECOND_PHASE_TICKS = 123

    /** The share, in percent, of the Blade's damage that reaches his bar (tsps: 832 of 1,951 seen). */
    const val BLADE_SHARE = 43

    /** Portals on each arm of the cross, each 2 tiles long, and how far across the quadrant a portal's strip reaches (tsps). */
    const val PORTALS_PER_ARM = 6
    const val STRIP_LENGTH = 12

    /**
     * A quadrant's turn (tsps): this many ticks from the first at [BLADE_AT]; six volleys this far apart, each
     * firing this long after its portals turn purple, its shot crossing a tile in this many client cycles.
     */
    const val QUADRANT_TICKS = 42
    const val VOLLEYS = 6
    const val VOLLEY_GAP = 5
    const val VOLLEY_FIRES = 4
    const val SHOT_CYCLES_PER_TILE = 6

    /** Client cycles in a game tick (20 ms each). */
    private const val CYCLES_PER_TICK = 30

    /** A portal's shot on whoever is in its strip as it reaches them (tsps: 20-25; the Wiki's max hit 25). */
    const val PORTAL_MIN = 20
    const val PORTAL_MAX = 25

    /**
     * Later in a quadrant's turn (tsps): the pools this long in, on this many of its tiles (and every player's), bursting
     * this long after for this much (the notes' estimate); every portal on its arms purple this long in; the floor
     * portal this long in, for this many ticks; and the last volley, which kills, this long in.
     */
    const val POOLS_AT = 28
    val POOL_TILES = 10..12
    const val POOL_LANDS = 4
    const val POOL_DAMAGE = 25
    const val ALL_PURPLE_AT = 31
    const val FLOOR_PORTAL_AT = 33
    const val FLOOR_PORTAL_TICKS = 7
    const val LAST_VOLLEY_AT = 40

    /**
     * Ticks after going through the floor portal in which nothing of his hurts a player: the Wiki's changes speak of an
     * immunity window after using it, not how long. Guessed.
     */
    const val FLOOR_PORTAL_IMMUNE = 3

    /** Where the floor portal opens, this many tiles out from each arm of the quadrant: its middle. Guessed. */
    const val FLOOR_PORTAL_OUT = 6

    /** vfx_ocellus_p2_aoe_dodge: a pool on its tile, guessed to be the pools' graphic. */
    const val SPOT_POOL = 4270

    /** vfx_ocellus_p2_safe_portal_spawn, _idle and _despawn: the floor portal. */
    const val SPOT_FLOOR_PORTAL_SPAWN = 4271
    const val SPOT_FLOOR_PORTAL = 4272
    const val SPOT_FLOOR_PORTAL_DESPAWN = 4273

    /** vfx_ocellus_p2_sword_discharge: on the Blade as the floor portal opens (tsps). */
    const val SPOT_SWORD_DISCHARGE = 4305

    /** His third phase (raid step 26a): the 242 cache's `tfa_ocellus_p3`, with Attack, on the second phase's bar. */
    const val P3 = 16677

    /** Ticks after the second phase's line that the third begins. Guessed: the next. */
    const val THIRD_PHASE_AFTER = 1

    /**
     * Ticks into the third phase: the Blade's drop explodes, the portals go, the arms crack and anyone on them or the
     * middle is moved off, the arms fall in, and their empty forms stand with the stepping stones, when he takes his first
     * quadrant (tsps).
     */
    const val SWORD_DROP_AT = 1
    const val PORTALS_GO_AT = 2
    const val ARMS_CRACK_AT = 4
    const val ARMS_FALL_AT = 9
    const val ARMS_EMPTY_AT = 12

    /** The arms' explosions run out from the middle every [EXPLOSION_EVERY] tiles, [EXPLOSION_CYCLES] client cycles apart (tsps). */
    const val EXPLOSION_EVERY = 3
    const val EXPLOSION_CYCLES = 15

    /**
     * The arms of the cross as they fall (`tfa_wallkit_archive03_straight_collapse_01` to `_04`, 3 by 11 and 11 by 3,
     * blocking walking and shots; guessed: north, east, south, west in that order), their empty forms after (blocking only
     * walking), the middle's void (`tfa_ocellus_platform_void_noblockrange`), and the stepping stones ("Gap", Jump): the
     * middle one and each arm's outer ones.
     */
    val LOC_ARMS = listOf(62929, 62930, 62931, 62932)
    val LOC_ARMS_EMPTY = listOf(62933, 62934, 62935, 62936)
    const val LOC_VOID = 62692
    const val LOC_STONE_MIDDLE = 62686
    val LOC_STONES_OUTER = listOf(62687, 62688, 62689, 62690)

    /** Every stepping stone's loc type: their Jump is handled on the copies in a run's instance ([register]). */
    val GAPS: Set<Int> = (LOC_STONES_OUTER + LOC_STONE_MIDDLE).toSet()

    /** The Blade's drop and the arms' four explosions (tsps: `tfa_arena_collapsing_explosion02, 03, 04, 01`). */
    const val SPOT_SWORD_DROP = 4388
    val SPOT_ARM_EXPLOSIONS = listOf(4391, 4392, 4393, 4390)

    /** A hop onto a stone (`human_steppingstonejump`), a tick each (tsps); the exact move's cycles are guessed. */
    const val ANIM_JUMP = 769
    const val JUMP_CYCLES = 30

    /** His attacks: three ticks apart (Wiki: speed 3), melee, melee and a slash, twice (tsps). */
    const val ATTACK_EVERY = 3
    const val ROTATION = 6
    const val ANIM_MELEE = 14842
    const val ANIM_SLASH = 14846
    const val ANIM_VOID_SHOT = 14850

    /** His first attack in a quadrant, this many ticks after he takes it. Guessed. */
    const val FIRST_ATTACK_AFTER = 3

    /**
     * The slash: a wave [SLASH_DEPTH] tiles deep and his width in front of him towards the tank, its rows
     * [SLASH_ROW_CYCLES] cycles apart the tick after his swing, hitting [SLASH_LANDS] ticks after the swing (tsps).
     */
    const val SLASH_DEPTH = 3
    const val SLASH_ROW_CYCLES = 2
    const val SLASH_LANDS = 2
    const val SPOT_SLASH = 4335

    /** His void shot at everyone when he can't reach the tank (Wiki), his magic max 36 (Wiki); its graphics guessed. */
    const val VOID_SHOT_MAX = 36
    val VOID_SHOT = NpcRangedAttack(anim = ANIM_VOID_SHOT, projectile = 4297, impact = 4293, maxHit = VOID_SHOT_MAX)

    /**
     * A quadrant falls at 75, 50 and 25% of his bar (tsps): his first line and the barriers down, then by ticks since
     * then his second line, his fade out, the quadrant's fall, and his taking the next.
     */
    val FALLS_AT = listOf(75, 50, 25)
    const val FALL_LINE = 11
    const val FALL_FADE = 12
    const val FALL_FALLS = 13
    const val FALL_NEXT = 17

    /** A player lost with a fallen quadrant (`tfa_ocellus_void_player_death`). */
    const val ANIM_VOID_DEATH = 14893

    /** The quadrants, clockwise from the north-west, and the arms of the cross, clockwise from the north. */
    enum class Quadrant { NW, NE, SE, SW }
    enum class Arm { N, E, S, W }

    /**
     * Which portal of each arm beside a quadrant stays orange in each of its six volleys (tsps `ORANGE`, the same in
     * every captured kill): counted out from the middle from 0, the north or south arm's, then the east or west arm's.
     */
    val ORANGE: Map<Quadrant, List<Pair<Int, Int>>> = mapOf(
        Quadrant.NW to listOf(2 to 2, 2 to 3, 1 to 4, 2 to 2, 2 to 4, 3 to 3),
        Quadrant.NE to listOf(1 to 0, 1 to 3, 1 to 5, 3 to 3, 2 to 2, 3 to 0),
        Quadrant.SE to listOf(1 to 1, 3 to 1, 1 to 3, 1 to 1, 2 to 2, 4 to 1),
        Quadrant.SW to listOf(2 to 2, 2 to 3, 1 to 4, 2 to 2, 2 to 4, 3 to 3),
    )

    /** What he is doing in his first phase. */
    enum class Doing { WALKING, CORNER, MIDDLE }

    /** A crack: its tiles, and the phase tick its energy flows back. */
    class Crack(val tiles: List<Tile>, val flows: Long)

    /** What a fight keeps between ticks, by its boss. Holds its players only weakly. */
    private class State {
        var doing = Doing.WALKING

        /** The corner he walks to or stands at, an index into [CORNERS]. */
        var corner = 0

        /** The phase tick of his next shot as he walks, and of the next step at a corner. */
        var nextShot = 0L
        var arrived = 0L

        /** The seven spots' order at this corner, and the cracks laid whose energy hasn't flowed yet. */
        val order = ArrayList<Int>()
        val cracks = ArrayList<Crack>()

        /** His charge, 0 to [CHARGE_MAX]. */
        var charge = 0

        /** The middles begun ([MIDDLE_AT]), the phase tick the one now began, its first side, and the corner after it. */
        var middles = 0
        var middleStart = 0L
        var firstSide = 0
        var cornerAfter = 0

        /** Each player's most hitpoints since their last interception, for the heal. */
        val peak = WeakHashMap<Player, Int>()

        /** In his second phase: the Blade, the share of a hitpoint it has carried over, and the first quadrant. */
        var blade: Npc? = null
        var bladeCarry = 0
        var firstQuadrant = Quadrant.NW

        /** Each portal standing, by arm and index out from the middle, and whether it is purple. */
        val portals = HashMap<Pair<Arm, Int>, Boolean>()

        /** The floor portal's tile while it is open, and the phase tick each player who went through is safe until. */
        var floorPortal: Tile? = null
        val immune = WeakHashMap<Player, Long>()

        /** The portals' locs as placed, to take them away as his third phase begins. */
        val portalLocs = HashMap<Pair<Arm, Int>, ZoneLoc>()

        /** His third phase: the quadrant he holds, the phase tick he took it (-1 while he hasn't one). */
        var held = Quadrant.NW
        var heldSince = -1L

        /** The world tick he took it, for the hits on him since (his attacker's tick is a world tick). */
        var heldWorldTick = -1L

        /** Its two barriers up: nobody jumps. */
        var barriers = false

        /** The phase tick his quadrant began to fall (-1 when it isn't), and how many have fallen. */
        var fallStart = -1L
        val fallen = LinkedHashSet<Quadrant>()

        /** The tank, the next attack's tick and where in his rotation it is. */
        var tank: Player? = null
        var nextAttack = 0L
        var rotation = 0

        /** The arms' locs, the middle's void, and each arm's three stepping stones standing. */
        val armLocs = HashMap<Arm, ZoneLoc>()
        val stones = HashMap<Arm, List<ZoneLoc>>()
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    /** The charge of [boss], his first phase, for tests. */
    fun charge(boss: Npc): Int = states[boss]?.charge ?: 0

    /** What he is doing, and at or towards which corner, for tests. */
    fun doing(boss: Npc): Pair<Doing, Int>? = states[boss]?.let { it.doing to it.corner }

    /** How many times he has gone to the middle, and the side he began there with (0 north, clockwise), for tests. */
    fun middles(boss: Npc): Pair<Int, Int> = states[boss]?.let { it.middles to it.firstSide } ?: (0 to 0)

    /** The Blade beside [boss] in his second phase, for tests; null before it appears. */
    fun blade(boss: Npc): Npc? = states[boss]?.blade

    /** The portals standing in his second phase, by arm and index out from the middle: true for purple. For tests. */
    fun portals(boss: Npc): Map<Pair<Arm, Int>, Boolean> = states[boss]?.portals?.toMap().orEmpty()

    /** The quadrant he holds in his third phase, null before he takes one; for tests. */
    fun held(boss: Npc): Quadrant? = states[boss]?.takeIf { it.heldSince >= 0 }?.held

    /** The quadrants fallen in his third phase, in order; for tests. */
    fun fallen(boss: Npc): List<Quadrant> = states[boss]?.fallen?.toList().orEmpty()

    /** His tank in his third phase; for tests. */
    fun tank(boss: Npc): Player? = states[boss]?.tank

    /** The arena's middle tile, where the arms of the cross meet, for a boss who became his first phase at ([x], [z]). */
    fun centre(x: Int, z: Int): Tile = middle(x, z).let { Tile(it.x + 1, it.z + 1) }

    /** The quadrant ([x], [z]) is in around [centre]; null on an arm of the cross. */
    fun quadrant(centre: Tile, x: Int, z: Int): Quadrant? = when {
        x < centre.x && z > centre.z -> Quadrant.NW
        x > centre.x && z > centre.z -> Quadrant.NE
        x > centre.x && z < centre.z -> Quadrant.SE
        x < centre.x && z < centre.z -> Quadrant.SW
        else -> null
    }

    /**
     * The tile [outX] tiles east or west of the cross's north-south arm and [outZ] north or south of its east-west one,
     * in [quadrant] (1 is beside the arm).
     */
    fun quadrantTile(centre: Tile, quadrant: Quadrant, outX: Int, outZ: Int): Tile {
        val west = quadrant == Quadrant.NW || quadrant == Quadrant.SW
        val north = quadrant == Quadrant.NW || quadrant == Quadrant.NE
        return Tile(centre.x + if (west) -outX else outX, centre.z + if (north) outZ else -outZ)
    }

    /** Where the floor portal in [quadrant] takes a player at ([x], [z]): the same spot in the next quadrant clockwise. */
    fun throughFloorPortal(centre: Tile, quadrant: Quadrant, x: Int, z: Int): Tile =
        quadrantTile(centre, Quadrant.entries[(quadrant.ordinal + 1) % Quadrant.entries.size], kotlin.math.abs(x - centre.x), kotlin.math.abs(z - centre.z))

    /** The north or south arm and the east or west arm beside [quadrant]. */
    fun arms(quadrant: Quadrant): Pair<Arm, Arm> = when (quadrant) {
        Quadrant.NW -> Arm.N to Arm.W
        Quadrant.NE -> Arm.N to Arm.E
        Quadrant.SE -> Arm.S to Arm.E
        Quadrant.SW -> Arm.S to Arm.W
    }

    /**
     * The two tiles of portal [index] (from 0, out from the middle) on [arm] around [centre], the first its south-west
     * tile (tsps: E x 4128-4138 and W x 4115-4125 at the middle's z, N z 5674-5684 and S z 5661-5671 at its x, every two).
     */
    fun portalTiles(centre: Tile, arm: Arm, index: Int): List<Tile> = when (arm) {
        Arm.E -> (centre.x + 1 + 2 * index).let { listOf(Tile(it, centre.z), Tile(it + 1, centre.z)) }
        Arm.W -> (centre.x - 2 - 2 * index).let { listOf(Tile(it, centre.z), Tile(it + 1, centre.z)) }
        Arm.N -> (centre.z + 1 + 2 * index).let { listOf(Tile(centre.x, it), Tile(centre.x, it + 1)) }
        Arm.S -> (centre.z - 2 - 2 * index).let { listOf(Tile(centre.x, it), Tile(centre.x, it + 1)) }
    }

    /**
     * The tiles portal [index] on [arm] shoots across in [quadrant], [out] tiles from the arm (1 to [STRIP_LENGTH]): its
     * strip, as wide as the portal is long.
     */
    fun strip(centre: Tile, arm: Arm, index: Int, quadrant: Quadrant, out: Int): List<Tile> {
        val west = quadrant == Quadrant.NW || quadrant == Quadrant.SW
        val north = quadrant == Quadrant.NW || quadrant == Quadrant.NE
        return portalTiles(centre, arm, index).map {
            if (arm == Arm.N || arm == Arm.S) Tile(centre.x + if (west) -out else out, it.z) else Tile(it.x, centre.z + if (north) out else -out)
        }
    }

    /**
     * The way over [arm] once the cross has fallen: from the quadrant [from] to [to], the next clockwise, over its three
     * stones (the outer one nearer [from] first), from the edge tile [start] to the tile [land] (tsps: SE 4129,5663 to SW
     * 4125,5669, SW 4117,5671 to NW 4123,5675, NW 4125,5683 to NE 4129,5677, NE 4137,5675 to SE 4131,5671). Guessed: the
     * stones' tiles, evenly between those.
     */
    class Crossing(val arm: Arm, val from: Quadrant, val to: Quadrant, val start: Tile, val stones: List<Tile>, val land: Tile)

    fun crossing(centre: Tile, arm: Arm): Crossing {
        val (cx, cz) = centre.x to centre.z
        return when (arm) {
            Arm.S -> Crossing(arm, Quadrant.SE, Quadrant.SW, Tile(cx + 2, cz - 10), listOf(Tile(cx + 1, cz - 9), Tile(cx, cz - 7), Tile(cx - 1, cz - 5)), Tile(cx - 2, cz - 4))
            Arm.W -> Crossing(arm, Quadrant.SW, Quadrant.NW, Tile(cx - 10, cz - 2), listOf(Tile(cx - 9, cz - 1), Tile(cx - 7, cz), Tile(cx - 5, cz + 1)), Tile(cx - 4, cz + 2))
            Arm.N -> Crossing(arm, Quadrant.NW, Quadrant.NE, Tile(cx - 2, cz + 10), listOf(Tile(cx - 1, cz + 9), Tile(cx, cz + 7), Tile(cx + 1, cz + 5)), Tile(cx + 2, cz + 4))
            Arm.E -> Crossing(arm, Quadrant.NE, Quadrant.SE, Tile(cx + 10, cz + 2), listOf(Tile(cx + 9, cz + 1), Tile(cx + 7, cz), Tile(cx + 5, cz - 1)), Tile(cx + 4, cz - 2))
        }
    }

    /** The arm a player crosses to leave [quadrant] for the next clockwise. */
    fun armOut(quadrant: Quadrant): Arm = when (quadrant) {
        Quadrant.NW -> Arm.N
        Quadrant.NE -> Arm.E
        Quadrant.SE -> Arm.S
        Quadrant.SW -> Arm.W
    }

    /** The south-west tile of [arm]'s fallen loc, 3 wide and 11 long from beside the middle's 3 by 3 out (tsps). */
    fun armTile(centre: Tile, arm: Arm): Tile = when (arm) {
        Arm.N -> Tile(centre.x - 1, centre.z + 2)
        Arm.E -> Tile(centre.x + 2, centre.z - 1)
        Arm.S -> Tile(centre.x - 1, centre.z - 12)
        Arm.W -> Tile(centre.x - 12, centre.z - 1)
    }

    /**
     * Whether ([x], [z]) is on his arena's floor, within [STRIP_LENGTH] of the cross either way: his room reaches down
     * the corridor south of it too, where his third phase moves and loses nobody.
     */
    fun inArena(centre: Tile, x: Int, z: Int): Boolean = kotlin.math.abs(x - centre.x) <= STRIP_LENGTH && kotlin.math.abs(z - centre.z) <= STRIP_LENGTH

    /** Whether ([x], [z]) is on the cross: an arm, 3 wide, or the middle. */
    fun onCross(centre: Tile, x: Int, z: Int): Boolean = kotlin.math.abs(x - centre.x) <= 1 || kotlin.math.abs(z - centre.z) <= 1

    /** His corner in [quadrant] in his third phase: his first phase's corner there (guessed; tsps: "from its corner tile"). */
    fun quadrantCorner(middle: Tile, quadrant: Quadrant): Tile = corner(
        middle,
        when (quadrant) {
            Quadrant.SE -> 0
            Quadrant.NE -> 1
            Quadrant.NW -> 2
            Quadrant.SW -> 3
        },
    )

    /** The middle's south-west tile for a boss who became his first phase at ([x], [z]). */
    fun middle(x: Int, z: Int): Tile = Tile(x, z + MIDDLE_DZ)

    /** Corner [index]'s south-west tile for the middle at [middle]. */
    fun corner(middle: Tile, index: Int): Tile = CORNERS[index].let { (dx, dz) -> Tile(middle.x + dx, middle.z + dz) }

    /**
     * The seven spots a crack can take for a 3 by 3 boss at ([x], [z]) whose middle is towards ([towardsX], [towardsZ])
     * (tsps): straight out from his side nearer the middle along its two rows or columns nearer it (four), or diagonal
     * from his inner corner, from the tile off it or either tile beside that (three). Each [TENDRIL_LENGTH] long, from
     * next to him outwards.
     */
    fun spots(x: Int, z: Int, towardsX: Int, towardsZ: Int, size: Int = 3): List<List<Tile>> {
        val sx = (towardsX - x).sign.takeIf { it != 0 } ?: 1
        val sz = (towardsZ - z).sign.takeIf { it != 0 } ?: 1
        val startX = if (sx > 0) x + size else x - 1
        val startZ = if (sz > 0) z + size else z - 1
        val rows = if (sz > 0) listOf(z + size - 2, z + size - 1) else listOf(z, z + 1)
        val columns = if (sx > 0) listOf(x + size - 2, x + size - 1) else listOf(x, x + 1)
        val innerX = if (sx > 0) x + size - 1 else x
        val innerZ = if (sz > 0) z + size - 1 else z
        fun line(fromX: Int, fromZ: Int, dx: Int, dz: Int) = (0 until TENDRIL_LENGTH).map { Tile(fromX + dx * it, fromZ + dz * it) }
        return rows.map { line(startX, it, sx, 0) } +
            columns.map { line(it, startZ, 0, sz) } +
            listOf(line(innerX + sx, innerZ + sz, sx, sz), line(innerX + sx, innerZ, sx, sz), line(innerX, innerZ + sz, sx, sz))
    }

    /**
     * The two cracks of side [side] (0 north, then clockwise) for a boss of [size] at ([x], [z]), in the order they're laid
     * (tsps): each diagonal, [SIDE_LENGTH] long, out from a corner of his footprint at an end of that side; the
     * end a clockwise sweep round him reaches first comes first: on the north side the north-west one,
     * then the north-east (guessed).
     */
    fun sideTendrils(x: Int, z: Int, side: Int, size: Int = 3): List<List<Tile>> {
        // His footprint's corners clockwise from the north-west, each with the way out from it.
        val corners = listOf(-1 to 1, 1 to 1, 1 to -1, -1 to -1)
        return listOf(corners[side % 4], corners[(side + 1) % 4]).map { (dx, dz) ->
            val fromX = if (dx > 0) x + size - 1 else x
            val fromZ = if (dz > 0) z + size - 1 else z
            (1..SIDE_LENGTH).map { Tile(fromX + dx * it, fromZ + dz * it) }
        }
    }

    /** The fight, or null when the cache hasn't his first phase (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val first = types[P1] ?: return null
        val second = types[P2] ?: return null
        val blade = types[BLADE] ?: return null
        val third = types[P3] ?: return null
        return EncounterDef(
            NAME, first,
            listOf(
                PhaseDef(
                    "shield",
                    cycle = listOf(CycleStep(1) { firstPhase() }),
                    onStart = {
                        // He waits nowhere near a corner as he begins: the first is picked at random (tsps).
                        state.corner = roll(0, CORNERS.size - 1)
                        state.doing = Doing.WALKING
                        state.nextShot = SHOT_EVERY.toLong()
                        // His bar run out, he stays at it for his change to the second phase (tsps; Sam's recordings).
                        boss.holdsAtZero = true
                        protect(true)
                        boss.effects.say(world.dialogueText.get(OcellusLines.ASCENSION))
                        for (player in players) state.peak[player] = player.hitpoints
                        log("first phase; to the ${cornerName(state.corner)} corner")
                    },
                    endsWhen = { boss.hitpoints <= 0 },
                ),
                PhaseDef(
                    "held",
                    cycle = listOf(CycleStep(1) { held() }),
                    endsAfter = HOLD_LINE + SECOND_PHASE_AFTER,
                    onStart = {
                        // Guessed: nothing more from his first phase once his bar is out; what was laid is gone.
                        state.cracks.clear()
                        protect(false)
                        log("held at the end of his bar")
                    },
                ),
                PhaseDef(
                    "second",
                    cycle = listOf(CycleStep(1) { secondPhase(blade) }),
                    endsAfter = SECOND_PHASE_TICKS + THIRD_PHASE_AFTER,
                    onStart = { toSecondPhase(second) },
                ),
                PhaseDef(
                    "third",
                    cycle = listOf(CycleStep(1) { thirdPhase() }),
                    onStart = { toThirdPhase(third) },
                ),
            ),
            scriptedAttacks = true,
            status = {
                if (boss.type.id == P3) {
                    "Ocellus ${boss.hitpoints}/${boss.maxHitpoints} hp, third phase" +
                        (if (state.heldSince >= 0) ", quadrant ${state.held.name}" else "") + (if (state.fallStart >= 0) ", falling" else "")
                } else if (boss.type.id == P2) {
                    "Ocellus ${boss.hitpoints}/${boss.maxHitpoints} hp, second phase" + (quadrantNow()?.let { ", quadrant ${it.name}" } ?: "")
                } else {
                    val doing = when {
                        boss.hitpoints <= 0 -> "held"
                        state.doing == Doing.MIDDLE -> "at the middle"
                        else -> "${state.doing.name.lowercase()} ${cornerName(state.corner)}"
                    }
                    "Ocellus ${boss.hitpoints}/${boss.maxHitpoints} hp, charge ${state.charge}/$CHARGE_MAX, $doing"
                }
            },
        )
    }

    private fun cornerName(index: Int) = listOf("south-east", "north-east", "north-west", "south-west")[index]

    /** Protect from Melee up or down: melee does nothing to him while it is up, and his head shows it. */
    private fun EncounterContext.protect(on: Boolean) {
        if (boss.protectsFromMelee == on) return
        boss.protectsFromMelee = on
        val info = boss.avatar?.extendedInfo
        if (on) info?.setHeadIconChange(0, HEAD_ICON_GROUP, HEAD_ICON_PROTECT_MELEE) else info?.resetHeadIcon(0)
        log(if (on) "Protect from Melee up" else "Protect from Melee down")
    }

    /**
     * Whether his first phase is over: he is dead, or his bar has run out and he holds at it. A hit on a player can bring
     * either about mid-attack (their Retribution as they die), so each attack asks before each player it hurts; the
     * hold keeps him alive, so being dead isn't enough to ask (Codex, PR 559).
     */
    private val EncounterContext.shieldOut: Boolean get() = boss.dead || boss.hitpoints <= 0

    /** One tick of his first phase. */
    private fun EncounterContext.firstPhase() {
        // Each player's best since their last interception, for the heal; anyone new to the fight counts from now.
        for (player in players) {
            val best = state.peak[player]
            if (best == null || player.hitpoints > best) state.peak[player] = player.hitpoints
        }
        flowCracks()
        if (shieldOut) return
        // Guessed: both thresholds passed at once (one big hit), the second middle comes as soon as the first is over.
        val due = MIDDLE_AT.count { (num, den) -> boss.hitpoints * den <= boss.maxHitpoints * num }
        if (state.doing != Doing.MIDDLE && state.middles < due) {
            toTheMiddle()
            return
        }
        when (state.doing) {
            Doing.WALKING -> walk()
            Doing.CORNER -> atCorner()
            Doing.MIDDLE -> middleStep()
        }
    }

    /** He goes to the middle, from wherever he is and whatever he is doing (tsps: even mid-walk). */
    private fun EncounterContext.toTheMiddle() {
        // To the corner he was walking to afterwards, or from one he stood at, the next. Guessed.
        state.cornerAfter = if (state.doing == Doing.CORNER) (state.corner + 1) % CORNERS.size else state.corner
        state.doing = Doing.MIDDLE
        state.middles++
        state.middleStart = phaseTick
        state.firstSide = roll(0, SIDES - 1)
        // Guessed: no prayer and no shots at the middle, as at a corner, where he stands laying cracks too.
        protect(false)
        boss.destination = null
        boss.effects.anim(ANIM_TELEPORT_OUT)
        boss.effects.spotanim(0, SPOT_TELEPORT_OUT, 0)
        log("to the middle (${state.middles}); first side ${sideName(state.firstSide)}")
    }

    private fun sideName(side: Int) = listOf("north", "east", "south", "west")[side]

    /** At the middle, by ticks since he went: in, the four sides, his line, the charge expended, and on. */
    private fun EncounterContext.middleStep() {
        val at = (phaseTick - state.middleStart).toInt()
        val firstSide = MIDDLE_IN + FIRST_SIDE
        val lastSide = firstSide + SIDE_GAP * (SIDES - 1)
        val line = lastSide + MIDDLE_LINE
        val expend = line + EXPEND_AFTER_LINE
        val sideAt = at - firstSide
        when {
            at == MIDDLE_IN -> {
                val middle = middle(boss.spawn.x, boss.spawn.z)
                world.npcs.teleport(boss, middle.x, middle.z)
                boss.effects.anim(ANIM_TELEPORT_IN)
                boss.effects.spotanim(0, SPOT_TELEPORT_IN, 0)
                log("in at the middle")
            }
            sideAt >= 0 && at <= lastSide + SIDE_EXPLODES && sideAt % SIDE_GAP in setOf(0, SIDE_TENDRIL_GAP, SIDE_EXPLODES) -> {
                val side = (state.firstSide + sideAt / SIDE_GAP) % SIDES
                when (sideAt % SIDE_GAP) {
                    0 -> {
                        boss.effects.anim(ANIM_CHARGE)
                        sideTendril(side, 0)
                    }
                    SIDE_TENDRIL_GAP -> sideTendril(side, 1)
                    else -> explode()
                }
            }
            at == line -> {
                val lineKey = if (state.charge >= ANNIHILATION_CHARGE) OcellusLines.ANNIHILATION else OcellusLines.DEATH_AWAITS
                boss.effects.say(world.dialogueText.get(lineKey))
                log("${if (lineKey == OcellusLines.ANNIHILATION) "annihilation" else "death awaits"} at charge ${state.charge}")
            }
            at == expend -> expend()
            at >= expend + WALK_AFTER_EXPEND -> {
                state.corner = state.cornerAfter
                state.doing = Doing.WALKING
                // Guessed: his first shot a full spacing after he walks on, as from a corner.
                state.nextShot = phaseTick + SHOT_EVERY
                protect(true)
                log("on from the middle to the ${cornerName(state.corner)} corner")
                walk()
            }
        }
    }

    /** Crack [which] (0 or 1) of side [side]: shown at once, its energy back [SIDE_FLOWS] on (tsps). */
    private fun EncounterContext.sideTendril(side: Int, which: Int) {
        val tiles = sideTendrils(boss.x, boss.z, side, boss.type.size)[which]
        for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_CRACK_DIAGONAL)
        state.cracks += Crack(tiles, phaseTick + SIDE_FLOWS)
        log("side ${sideName(side)} crack ${which + 1} on ${tiles.first().x},${tiles.first().z} to ${tiles.last().x},${tiles.last().z}")
    }

    /**
     * He expends his charge on everyone, unavoidably (Wiki), as his line said: 17 to 18 below [ANNIHILATION_CHARGE], 80 to
     * 90 from it; and it empties (Sam's recordings: the hit and the empty bar together). Guessed: the harder one lands
     * as he expends the charge too.
     */
    private fun EncounterContext.expend() {
        val hard = state.charge >= ANNIHILATION_CHARGE
        boss.effects.anim(ANIM_EXPEND)
        log("expends charge ${state.charge}")
        state.charge = 0
        for (player in players) {
            if (shieldOut) return
            val dealt = world.meleeCombat.hitPlayer(player, boss, if (hard) roll(ANNIHILATION_MIN, ANNIHILATION_MAX) else roll(DEATH_MIN, DEATH_MAX), world.tick)
            log("charge hits ${player.username} for $dealt")
        }
    }

    /** One tick of the hold at the end of his bar, by ticks since it began: his line, the transformation, and on. */
    private fun EncounterContext.held() {
        val at = phaseTick.toInt()
        when (at) {
            HOLD_LINE -> {
                boss.effects.say(world.dialogueText.get(OcellusLines.WILL_DISAPPEAR))
                val middle = middle(boss.spawn.x, boss.spawn.z)
                if (boss.x != middle.x || boss.z != middle.z) {
                    // Guessed: at once, with the line; the capture notes say only that he teleports there.
                    boss.destination = null
                    world.npcs.teleport(boss, middle.x, middle.z)
                    boss.effects.spotanim(0, SPOT_TELEPORT_IN, 0)
                    log("to the middle")
                }
                log("will disappear")
            }
            HOLD_LINE + TRANSFORM_ANIM_AFTER -> {
                boss.effects.anim(ANIM_TRANSFORMATION)
                log("transformation")
            }
        }
    }

    /**
     * His second phase begins ([SECOND_PHASE_AFTER] after his line): he becomes [form], with no Attack, on the bar of
     * what is left of him, the Wiki's remainder for his party size (party-scaling.json); it holds at 0 as his first did.
     */
    private fun EncounterContext.toSecondPhase(form: NpcType) {
        world.npcs.transform(boss, form, Npcs.FOREVER)
        untarget(boss)
        val party = boss.scaledFor
        boss.scaledHitpoints = (if (party > 0) world.partyScaling.hitpoints(form, party) else null) ?: form.stats.hitpoints
        boss.hitpoints = boss.maxHitpoints
        boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        state.portals.clear()
        log("second phase: ${boss.hitpoints} hitpoints")
    }

    /**
     * [npc] has lost its Attack: whoever was attacking it stops, rather than swinging, shooting or casting on at a type
     * with no Attack (as the Guardian's inert guardians, Codex on #481). A hit already on its way still lands.
     */
    private fun EncounterContext.untarget(npc: Npc) {
        for (player in world.onlinePlayers) {
            val on = when (val fight = player.interaction) {
                is MeleeInteraction -> fight.npc
                is RangedInteraction -> fight.npc
                is MagicInteraction -> fight.npc
                else -> null
            }
            if (on !== npc) continue
            player.interaction = null
            world.npcInteractions.stopFacing(player)
        }
    }

    /** The quadrant whose turn it is in his second phase; null before the first, at [BLADE_AT]. */
    private fun EncounterContext.quadrantNow(): Quadrant? {
        val since = phaseTick - BLADE_AT
        if (since < 0) return null
        return Quadrant.entries[(state.firstQuadrant.ordinal + (since / QUADRANT_TICKS).toInt()) % Quadrant.entries.size]
    }

    /** One tick of his second phase: the Blade's damage to his bar, the portals, the Blade, the quadrants' volleys. */
    private fun EncounterContext.secondPhase(bladeType: NpcType) {
        forwardBlade()
        val at = phaseTick.toInt()
        if (at == PORTALS_AT) {
            for (arm in Arm.entries) for (index in 0 until PORTALS_PER_ARM) portal(arm, index, false)
            log("portals stand")
        }
        if (at == SECOND_PHASE_TICKS) {
            boss.effects.say(world.dialogueText.get(OcellusLines.THIS_IS_ENOUGH))
            log("this is enough")
            return
        }
        if (at > SECOND_PHASE_TICKS) return
        if (at == BLADE_AT) {
            boss.effects.say(world.dialogueText.get(OcellusLines.YOUR_TIME))
            val blade = spawnAdd(bladeType, boss.x, boss.z)
            if (blade != null) {
                // It takes the party's attacks and never hits back or dies of them: its damage goes to his bar.
                blade.scriptedAttacks = true
                blade.holdsAtZero = true
                // Nor does it win hitpoints back by itself: a point regained before this fight's part of the tick would
                // hide a point of damage from [forwardBlade] (Codex, PR 566).
                blade.hitpointsRegenTicks = Int.MAX_VALUE
                state.blade = blade
            }
            state.firstQuadrant = partyQuadrant()
            log("your time; the Blade; first quadrant ${state.firstQuadrant.name}")
        }
        if (at >= BLADE_AT) quadrantStep(at - BLADE_AT)
    }

    /**
     * The quadrant most of the party stands in (tsps); a tie, or nobody in one, goes to the first of them clockwise from
     * the north-west (guessed).
     */
    private fun EncounterContext.partyQuadrant(): Quadrant {
        val centre = centre(boss.spawn.x, boss.spawn.z)
        val counts = players.mapNotNull { quadrant(centre, it.x, it.z) }.groupingBy { it }.eachCount()
        return Quadrant.entries.maxBy { counts[it] ?: 0 }
    }

    /**
     * The Blade's damage since last tick: [BLADE_SHARE] of it comes off his bar, a hitpoint's share carried over, and
     * the Blade is whole again. Guessed: no diminishing returns within a quadrant (the Wiki says there are some, but
     * not how much), and the Blade's bar shown full again at once.
     */
    private fun EncounterContext.forwardBlade() {
        val blade = state.blade ?: return
        if (blade.dead || world.npcs[blade.index] !== blade) return
        val lost = blade.maxHitpoints - blade.hitpoints
        if (lost <= 0) return
        blade.hitpoints = blade.maxHitpoints
        blade.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(blade.hitpoints, blade.maxHitpoints))
        val total = lost * BLADE_SHARE + state.bladeCarry
        state.bladeCarry = total % 100
        val share = minOf(total / 100, boss.hitpoints)
        boss.hitpoints -= share
        boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        log("the Blade takes $lost: $share to his bar")
    }

    /**
     * Ticks [t] into the quadrants' turns: whose turn, its volleys' portals turning purple and their firing, the pools,
     * every portal purple, the floor portal, and the last volley.
     */
    private fun EncounterContext.quadrantStep(t: Int) {
        val quadrant = Quadrant.entries[(state.firstQuadrant.ordinal + t / QUADRANT_TICKS) % Quadrant.entries.size]
        val at = t % QUADRANT_TICKS
        if (at == 0) log("quadrant ${quadrant.name}")
        if (at % VOLLEY_GAP == 0 && at / VOLLEY_GAP < VOLLEYS) volley(quadrant, at / VOLLEY_GAP)
        val firing = at - VOLLEY_FIRES
        if (firing >= 0 && firing % VOLLEY_GAP == 0 && firing / VOLLEY_GAP < VOLLEYS) fire(quadrant, "volley ${firing / VOLLEY_GAP + 1}")
        when (at) {
            POOLS_AT -> pools(quadrant)
            ALL_PURPLE_AT -> {
                val (ns, ew) = arms(quadrant)
                for (arm in Arm.entries) for (index in 0 until PORTALS_PER_ARM) portal(arm, index, arm == ns || arm == ew)
                log("every portal purple")
            }
            LAST_VOLLEY_AT -> fire(quadrant, "last volley", lethal = true)
        }
        if (at in FLOOR_PORTAL_AT..FLOOR_PORTAL_AT + FLOOR_PORTAL_TICKS) floorPortal(quadrant, at - FLOOR_PORTAL_AT)
    }

    /** Whether [player] went through the floor portal lately, and nothing of his hurts them yet. */
    private fun EncounterContext.immune(player: Player): Boolean = (state.immune[player] ?: -1L) >= phaseTick

    /**
     * The pools in [quadrant] (tsps): on [POOL_TILES] of its tiles at random and on every player's tile, each player in
     * it or not, bursting [POOL_LANDS] ticks later on whoever stands on one. Guessed: the graphic, which tiles (any of
     * the quadrant's, each once), the burst landing on the tile rather than the player, and no prayer against it.
     */
    private fun EncounterContext.pools(quadrant: Quadrant) {
        val centre = centre(boss.spawn.x, boss.spawn.z)
        val all = (1..STRIP_LENGTH).flatMap { x -> (1..STRIP_LENGTH).map { z -> quadrantTile(centre, quadrant, x, z) } }.toMutableList()
        val count = roll(POOL_TILES.first, POOL_TILES.last)
        val tiles = LinkedHashSet<Tile>()
        repeat(count) { tiles += all.removeAt(roll(0, all.size - 1)) }
        for (player in players) tiles += Tile(player.x, player.z)
        for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_POOL)
        log("pools on ${tiles.size} tiles: ${tiles.joinToString(" ") { "${it.x},${it.z}" }}")
        after(POOL_LANDS) {
            if (boss.dead) return@after
            for (player in players) {
                if (Tile(player.x, player.z) !in tiles || immune(player)) continue
                val dealt = fromTheRoom(player, POOL_DAMAGE)
                log("pool bursts on ${player.username} for $dealt")
            }
        }
    }

    /**
     * The floor portal in [quadrant], [open] ticks after it opened (tsps): it opens with the Blade's discharge, shows
     * its idle graphic each tick, and goes after [FLOOR_PORTAL_TICKS]. Whoever stands on it while it is open goes to the
     * same spot in the next quadrant clockwise (the notes' estimate), safe from him for [FLOOR_PORTAL_IMMUNE] ticks.
     */
    private fun EncounterContext.floorPortal(quadrant: Quadrant, open: Int) {
        val centre = centre(boss.spawn.x, boss.spawn.z)
        val tile = quadrantTile(centre, quadrant, FLOOR_PORTAL_OUT, FLOOR_PORTAL_OUT)
        // The fight runs before players move in a tick, so whoever stepped on it in its last open tick is only on it
        // now, as it goes: they are taken on before it does (Codex, PR 572).
        for (player in players) {
            if (open == 0 || player.x != tile.x || player.z != tile.z) continue
            val to = throughFloorPortal(centre, quadrant, player.x, player.z)
            world.teleport(player, to.x, to.z, boss.level)
            state.immune[player] = phaseTick + FLOOR_PORTAL_IMMUNE
            log("${player.username} through the floor portal to ${to.x},${to.z}")
        }
        if (open == FLOOR_PORTAL_TICKS) {
            world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_FLOOR_PORTAL_DESPAWN)
            state.floorPortal = null
            log("floor portal gone")
            return
        }
        if (open == 0) {
            state.floorPortal = tile
            world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_FLOOR_PORTAL_SPAWN)
            state.blade?.takeIf { !it.dead }?.effects?.spotanim(0, SPOT_SWORD_DISCHARGE, 0)
            log("floor portal at ${tile.x},${tile.z}")
        } else {
            world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_FLOOR_PORTAL)
        }
    }

    /** Volley [volley] in [quadrant]: every portal on its two arms turns purple but the one of each the pattern keeps orange. */
    private fun EncounterContext.volley(quadrant: Quadrant, volley: Int) {
        val (ns, ew) = arms(quadrant)
        val (keepNs, keepEw) = ORANGE.getValue(quadrant)[volley]
        for (arm in Arm.entries) for (index in 0 until PORTALS_PER_ARM) {
            portal(arm, index, (arm == ns && index != keepNs) || (arm == ew && index != keepEw))
        }
        log("volley ${volley + 1}: orange ${ns.name} $keepNs, ${ew.name} $keepEw")
    }

    /**
     * Portal [index] on [arm], orange or [purple], placed only when it changes. The north and south arms' turned a
     * quarter so they lie along the arm (which way round is guessed).
     */
    private fun EncounterContext.portal(arm: Arm, index: Int, purple: Boolean) {
        if (state.portals[arm to index] == purple) return
        val at = portalTiles(centre(boss.spawn.x, boss.spawn.z), arm, index).first()
        state.portalLocs[arm to index] = placeLoc(if (purple) LOC_PORTAL_ACTIVE else LOC_PORTAL, at.x, at.z, if (arm == Arm.N || arm == Arm.S) 1 else 0)
        state.portals[arm to index] = purple
    }

    /**
     * A volley ([what]) fires: each purple portal on [quadrant]'s arms shoots across it, along both tiles of its strip,
     * crossing a tile every [SHOT_CYCLES_PER_TILE] client cycles. Whoever is on a strip's tile as the shot reaches it
     * is hit, once a volley by each arm's shots (the Wiki's changes: never twice from the same axis, but by both); the
     * last volley, [lethal], kills (tsps). Nobody just through the floor portal is hit. Guessed: no prayer helps, and
     * the hit lands on the tick the shot reaches them, by 30 cycles a tick.
     */
    private fun EncounterContext.fire(quadrant: Quadrant, what: String, lethal: Boolean = false) {
        val centre = centre(boss.spawn.x, boss.spawn.z)
        val (ns, ew) = arms(quadrant)
        val purple = state.portals.filter { (key, on) -> on && (key.first == ns || key.first == ew) }.keys.sortedWith(compareBy({ it.first }, { it.second }))
        val flight = STRIP_LENGTH * SHOT_CYCLES_PER_TILE
        for ((arm, index) in purple) {
            val from = portalTiles(centre, arm, index)
            val to = strip(centre, arm, index, quadrant, STRIP_LENGTH)
            for (lane in from.indices) {
                world.zones.projectile(
                    Projectile(
                        id = SPOT_PORTAL_SHOT, fromX = from[lane].x, fromZ = from[lane].z, level = boss.level, toX = to[lane].x, toZ = to[lane].z,
                        startHeight = 40, endHeight = 40, startTime = 0, endTime = flight, angle = 0, progress = 0,
                    ),
                )
            }
        }
        log("$what fires from ${purple.size} portals")
        val hitBy = mapOf(ns to HashSet<Player>(), ew to HashSet<Player>())
        for (tick in 1..(flight + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK) {
            after(tick) {
                if (boss.dead) return@after
                for ((arm, index) in purple) {
                    for (out in 1..STRIP_LENGTH) {
                        if ((out * SHOT_CYCLES_PER_TILE + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK != tick) continue
                        val tiles = strip(centre, arm, index, quadrant, out)
                        for (player in players) {
                            if (tiles.none { it.x == player.x && it.z == player.z } || immune(player) || !hitBy.getValue(arm).add(player)) continue
                            val dealt = fromTheRoom(player, if (lethal) maxOf(1, player.hitpoints) else roll(PORTAL_MIN, PORTAL_MAX))
                            log("${if (lethal) "last volley" else "portal shot"} from ${arm.name} $index hits ${player.username} for $dealt")
                        }
                    }
                }
            }
        }
    }

    /**
     * His third phase begins: he becomes [form], with Attack, on the same bar (tsps), as he holds the quadrant whose turn
     * the second phase ended in. The Blade's drop and the portals' going come a tick or two in ([thirdPhase]).
     */
    private fun EncounterContext.toThirdPhase(form: NpcType) {
        world.npcs.transform(boss, form, Npcs.FOREVER)
        boss.destination = null
        // The quadrant whose turn his second phase ended in (tsps: the portals' last).
        state.held = Quadrant.entries[(state.firstQuadrant.ordinal + (SECOND_PHASE_TICKS - BLADE_AT) / QUADRANT_TICKS) % Quadrant.entries.size]
        state.heldSince = -1
        state.barriers = false
        state.fallStart = -1
        state.tank = null
        log("third phase: ${boss.hitpoints} hitpoints; his quadrant ${state.held.name}")
    }

    /** One tick of his third phase: the cross falling, then his quadrant, his attacks and its fall. */
    private fun EncounterContext.thirdPhase() {
        // Hits on the Blade until it goes still reach his bar (Codex, PR 576).
        forwardBlade()
        val at = phaseTick.toInt()
        val centre = centre(boss.spawn.x, boss.spawn.z)
        when (at) {
            SWORD_DROP_AT -> {
                val blade = state.blade
                if (blade != null && !blade.dead && world.npcs[blade.index] === blade) {
                    world.zones.mapAnim(blade.x + 1, blade.z + 1, boss.level, SPOT_SWORD_DROP)
                    world.removeNpc(blade)
                }
                state.blade = null
                log("the Blade drops")
            }
            PORTALS_GO_AT -> {
                for (loc in state.portalLocs.values) clearLoc(loc)
                state.portalLocs.clear()
                state.portals.clear()
                log("portals go")
            }
            ARMS_CRACK_AT -> crackArms(centre)
            ARMS_FALL_AT -> {
                for (arm in Arm.entries) {
                    val (dx, dz) = when (arm) {
                        Arm.N -> 0 to 1
                        Arm.E -> 1 to 0
                        Arm.S -> 0 to -1
                        Arm.W -> -1 to 0
                    }
                    for ((i, spot) in SPOT_ARM_EXPLOSIONS.withIndex()) {
                        val out = 2 + EXPLOSION_EVERY * i
                        world.zones.mapAnim(centre.x + dx * out, centre.z + dz * out, boss.level, spot, delay = EXPLOSION_CYCLES * i)
                    }
                }
                log("the arms fall in")
            }
            ARMS_EMPTY_AT -> {
                for (arm in Arm.entries) {
                    val tile = armTile(centre, arm)
                    state.armLocs[arm] = placeLoc(LOC_ARMS_EMPTY[arm.ordinal], tile.x, tile.z)
                    val way = crossing(centre, arm)
                    state.stones[arm] = way.stones.mapIndexed { i, stone ->
                        placeLoc(if (i == 1) LOC_STONE_MIDDLE else LOC_STONES_OUTER[arm.ordinal], stone.x, stone.z)
                    }
                }
                log("stepping stones stand")
                take(state.held)
            }
        }
        if (state.heldSince < 0) return
        val fall = state.fallStart
        if (fall >= 0) {
            falling((phaseTick - fall).toInt())
            return
        }
        val due = FALLS_AT.getOrNull(state.fallen.size)
        if (due != null && boss.hitpoints * 100 <= boss.maxHitpoints * due) {
            startFall()
            return
        }
        pickTank()
        if (phaseTick >= state.nextAttack) {
            attack(state.rotation)
            state.rotation = (state.rotation + 1) % ROTATION
            state.nextAttack = phaseTick + ATTACK_EVERY
        }
    }

    /**
     * The arms crack and stand solid (tsps), with the middle's 3 by 3 blocked: whoever is on the cross is moved into
     * the quadrant beside them, or from the middle into his (the notes' estimate; which quadrant guessed).
     */
    private fun EncounterContext.crackArms(centre: Tile) {
        for (arm in Arm.entries) {
            val tile = armTile(centre, arm)
            state.armLocs[arm] = placeLoc(LOC_ARMS[arm.ordinal], tile.x, tile.z)
        }
        for (dx in -1..1) for (dz in -1..1) placeLoc(LOC_VOID, centre.x + dx, centre.z + dz)
        for (player in players) {
            if (!inArena(centre, player.x, player.z) || !onCross(centre, player.x, player.z)) continue
            val westOfHis = state.held == Quadrant.NW || state.held == Quadrant.SW
            val southOfHis = state.held == Quadrant.SE || state.held == Quadrant.SW
            val dx = player.x - centre.x
            val dz = player.z - centre.z
            val to = when {
                kotlin.math.abs(dx) <= 1 && kotlin.math.abs(dz) <= 1 -> quadrantTile(centre, state.held, 2, 2)
                kotlin.math.abs(dx) <= 1 -> Tile(centre.x + if (dx > 0 || (dx == 0 && !westOfHis)) 2 else -2, player.z)
                else -> Tile(player.x, centre.z + if (dz > 0 || (dz == 0 && !southOfHis)) 2 else -2)
            }
            world.teleport(player, to.x, to.z, boss.level)
            log("${player.username} moved off the cross to ${to.x},${to.z}")
        }
        log("the arms crack")
    }

    /** He takes [quadrant] from its corner, its barriers up, his tank to be found and his rotation from its start. */
    private fun EncounterContext.take(quadrant: Quadrant) {
        val corner = quadrantCorner(middle(boss.spawn.x, boss.spawn.z), quadrant)
        world.npcs.teleport(boss, corner.x, corner.z)
        boss.effects.spotanim(0, SPOT_TELEPORT_IN, 0)
        state.held = quadrant
        state.heldSince = phaseTick
        state.heldWorldTick = world.tick
        state.barriers = true
        state.fallStart = -1
        state.tank = null
        state.rotation = 0
        state.nextAttack = phaseTick + FIRST_ATTACK_AFTER
        log("takes quadrant ${quadrant.name} from ${corner.x},${corner.z}; barriers up")
    }

    /**
     * His tank (tsps): the first to hit him while they stay in his quadrant. When he has none, whoever in it first attacked
     * him since he took it. The party is told who he turns to ([OcellusLines.TURNS_TO]; Sam's recordings).
     */
    private fun EncounterContext.pickTank() {
        val centre = centre(boss.spawn.x, boss.spawn.z)
        val tank = state.tank
        if (tank != null && (tank.dead || players.none { it === tank } || quadrant(centre, tank.x, tank.z) != state.held)) {
            state.tank = null
            log("${tank.username} is no longer his tank")
        }
        if (state.tank != null) return
        // Each player's own record of attacking him, not his last attacker: of several who hit him in one tick, the
        // first stays first, and one hitting from outside his quadrant doesn't hide one inside it (Codex, PR 576).
        // World ticks: an attack from before he took this quadrant doesn't count. Ties go to the party's order.
        val attacker = players.filter {
            !it.dead && it.attackedNpc === boss && it.attackedNpcTick >= state.heldWorldTick && quadrant(centre, it.x, it.z) == state.held
        }.minByOrNull { it.attackedNpcTick } ?: return
        state.tank = attacker
        for (player in players) player.message(world.textFor(player)[OcellusLines.TURNS_TO, "name" to attacker.username])
        log("turns to ${attacker.username}")
    }

    /** Whether [player] stands beside his footprint, not at a corner of it: where he reaches with melee. */
    private fun EncounterContext.beside(player: Player): Boolean {
        val size = boss.type.size
        val alongX = player.x in boss.x until boss.x + size
        val alongZ = player.z in boss.z until boss.z + size
        return (alongX && (player.z == boss.z - 1 || player.z == boss.z + size)) || (alongZ && (player.x == boss.x - 1 || player.x == boss.x + size))
    }

    /**
     * Attack [step] of his rotation (tsps): a melee hit on the tank, or at steps 2 and 5 his slash. With no tank, or one
     * he can't reach, a void shot at everyone instead (Wiki). Guessed: no tank counting as one out of reach.
     */
    private fun EncounterContext.attack(step: Int) {
        val tank = state.tank
        if (tank == null || !beside(tank)) {
            log("void shot at ${players.joinToString { it.username }}")
            for (player in players) world.meleeCombat.fireAt(boss, player, world.tick, VOID_SHOT, AttackType.MAGIC)
            return
        }
        if (step % 3 == 2) {
            slash(tank)
            return
        }
        world.meleeCombat.swingAt(boss, tank, world.tick, ANIM_MELEE)
        log("melee at ${tank.username}")
    }

    /**
     * His slash towards [tank] (tsps): a wave [SLASH_DEPTH] deep across his width on that side, its rows shown the tick
     * after the swing, hitting a tick later whoever is on it. Guessed: a melee roll at his max, which Protect from Melee
     * blocks, as for his other swings.
     */
    private fun EncounterContext.slash(tank: Player) {
        val size = boss.type.size
        val (dx, dz) = when {
            tank.z >= boss.z + size -> 0 to 1
            tank.z < boss.z -> 0 to -1
            tank.x >= boss.x + size -> 1 to 0
            else -> -1 to 0
        }
        val rows = (1..SLASH_DEPTH).map { out ->
            (0 until size).map { across ->
                when {
                    dz > 0 -> Tile(boss.x + across, boss.z + size - 1 + out)
                    dz < 0 -> Tile(boss.x + across, boss.z - out)
                    dx > 0 -> Tile(boss.x + size - 1 + out, boss.z + across)
                    else -> Tile(boss.x - out, boss.z + across)
                }
            }
        }
        boss.face(tank)
        boss.effects.anim(ANIM_SLASH)
        log("slash towards ${tank.username}")
        after(1) {
            for ((row, tiles) in rows.withIndex()) for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_SLASH, delay = SLASH_ROW_CYCLES * row)
        }
        val wave = rows.flatten().toSet()
        after(SLASH_LANDS) {
            if (boss.dead) return@after
            for (player in players) {
                if (Tile(player.x, player.z) !in wave) continue
                val roll = world.combat.npcAttack(boss, player, AttackType.SLASH) ?: continue
                val rolled = if (CombatMath.rollHits(world.random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(world.random, roll.maxHit) else 0
                val prayed = world.prayers.protects(player, AttackType.SLASH)
                // Dinh's bulwark blocks it as it does his other swings (Codex, PR 576).
                val blocked = if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(rolled) else rolled
                val dealt = world.meleeCombat.hitPlayer(player, boss, if (prayed) 0 else blocked, world.tick)
                if (!player.dead) player.attackedTick = world.tick
                log("slash hits ${player.username} for $dealt" + if (prayed) ", prayed against" else "")
            }
        }
    }

    /** His bar reached the next of [FALLS_AT]: his line, and the barriers drop (tsps). */
    private fun EncounterContext.startFall() {
        state.fallStart = phaseTick
        state.barriers = false
        val line = if (roll(0, 1) == 0) OcellusLines.OBLITERATION else OcellusLines.RUN
        boss.effects.say(world.dialogueText.get(line))
        log("${state.held.name} to fall at ${boss.hitpoints}/${boss.maxHitpoints}; barriers down")
    }

    /**
     * [t] ticks into his quadrant's fall (tsps): his second line, his fade out, the quadrant's fall with whoever is still
     * on it and the stones of the arms beside it, and then the next quadrant. STAND-IN until step 27: after the third
     * fall, when his final stand would begin, he dies, and the room is done. Guessed: no attacks while it falls.
     */
    private fun EncounterContext.falling(t: Int) {
        when (t) {
            FALL_LINE -> {
                val line = listOf(OcellusLines.THIS_END, OcellusLines.EVERYTHING_ENDS, OcellusLines.CANT_ESCAPE)[roll(0, 2)]
                boss.effects.say(world.dialogueText.get(line))
                log("the end line")
            }
            FALL_FADE -> {
                boss.effects.spotanim(0, SPOT_TELEPORT_OUT, 0)
                log("fades out")
            }
            FALL_FALLS -> {
                val centre = centre(boss.spawn.x, boss.spawn.z)
                val quadrant = state.held
                state.fallen += quadrant
                val (ns, ew) = arms(quadrant)
                for (arm in listOf(ns, ew)) state.stones.remove(arm)?.forEach { clearLoc(it) }
                log("quadrant ${quadrant.name} falls")
                for (player in players) {
                    if (!inArena(centre, player.x, player.z) || quadrant(centre, player.x, player.z) != quadrant) continue
                    val dealt = fromTheRoom(player, maxOf(1, player.hitpoints))
                    // After the hit: a death sets its own animation, which this one replaces (Codex, PR 576).
                    player.anim(ANIM_VOID_DEATH)
                    log("${player.username} lost with it for $dealt")
                }
            }
            FALL_NEXT -> {
                if (state.fallen.size >= FALLS_AT.size) {
                    // STAND-IN until step 27: his final stand isn't built, so he dies as it would begin.
                    log("final stand: stand-in, he dies")
                    boss.holdsAtZero = false
                    boss.hitpoints = 0
                    world.meleeCombat.hit(boss, null, 0, world.tick)
                    return
                }
                take(Quadrant.entries[(state.held.ordinal + 1) % Quadrant.entries.size])
            }
        }
    }

    /**
     * The Jump option on a stepping stone (tsps): from the quadrant before its arm, clockwise, while his barriers are
     * down, the player hops onto each of the arm's three stones and off onto the next quadrant, a tick a hop. Back the
     * other way: [OcellusLines.WAY_BACK_CLOSED] (the notes' estimate). Guessed: one Jump carrying them all the way over (live
     * hops a stone a click), and nothing said while the barriers stand.
     */
    private suspend fun ScriptContext.jump(world: World, op: Op) {
        val loc = op.loc ?: return
        val fight = world.encounters.all.firstOrNull { it.def.name == NAME && !it.ended && it.covers(loc.x, loc.z) } ?: return
        val state = states[fight.boss] ?: return
        val centre = centre(fight.boss.spawn.x, fight.boss.spawn.z)
        val (arm, stones) = state.stones.entries.firstOrNull { (_, locs) -> locs.any { it.x == loc.x && it.z == loc.z } } ?: return
        val way = crossing(centre, arm)
        if (stones.isEmpty()) return
        if (quadrant(centre, player.x, player.z) != way.from) {
            player.message(world.textFor(player)[OcellusLines.WAY_BACK_CLOSED])
            fight.log("${player.username} can't jump the ${arm.name} arm back")
            return
        }
        if (state.barriers) {
            fight.log("${player.username} can't jump the ${arm.name} arm: barriers up")
            return
        }
        fight.log("${player.username} jumps the ${arm.name} arm")
        for (to in way.stones + way.land) {
            val angle = Agility.angle(to.x - player.x, to.z - player.z)
            player.effects.exactMove(PlayerEffects.ExactMove(player.x, player.z, to.x, to.z, 0, JUMP_CYCLES, angle))
            player.anim(ANIM_JUMP)
            delay(1)
            world.teleport(player, to.x, to.z, fight.boss.level)
        }
        fight.log("${player.username} over to ${way.to.name} at ${way.land.x},${way.land.z}")
    }

    /** His stepping stones' Jump, on their copies in a run's instance ([ArchiveRaids.INSTANCE_LOCS] has [GAPS]). */
    fun register(world: World) {
        for (gap in GAPS) world.handlers.onLoc(gap, 1) { op -> jump(world, op) }
    }

    /** Walking to his corner: a step a tick, a shot every [SHOT_EVERY], and the corner's cracks the tick after he's there. */
    private fun EncounterContext.walk() {
        val to = corner(middle(boss.spawn.x, boss.spawn.z), state.corner)
        if (boss.x != to.x || boss.z != to.z) world.npcs.stepTo(boss, to.x, to.z)
        if (phaseTick >= state.nextShot) {
            shoot()
            state.nextShot = phaseTick + SHOT_EVERY
        }
        if (boss.x == to.x && boss.z == to.z) {
            state.doing = Doing.CORNER
            state.arrived = phaseTick
            state.order.clear()
            state.order.addAll((0 until TENDRILS).toList())
            // Each spot once, in a random order. Guessed: the captures show seven at random, not whether one repeats.
            for (i in state.order.size - 1 downTo 1) {
                val j = roll(0, i)
                state.order[i] = state.order[j].also { state.order[j] = state.order[i] }
            }
            protect(false)
            log("at the ${cornerName(state.corner)} corner")
        }
    }

    /** A fireball or a water ball at everyone. */
    private fun EncounterContext.shoot() {
        val targets = players
        if (targets.isEmpty()) return
        val magic = roll(0, 1) == 1
        log("${if (magic) "water ball" else "fireball"} at ${targets.joinToString { it.username }}")
        for (player in targets) {
            if (shieldOut) return
            world.meleeCombat.fireAt(boss, player, world.tick, if (magic) WATER_BALL else FIREBALL, if (magic) AttackType.MAGIC else AttackType.RANGED)
        }
    }

    /** At a corner, by ticks since he got there: the seven cracks, his line, the explosion, and on to the next corner. */
    private fun EncounterContext.atCorner() {
        val at = (phaseTick - state.arrived).toInt()
        val lastCrack = 1 + TENDRIL_GAP * (TENDRILS - 1)
        val line = lastCrack + CORNER_LINE
        val explode = line + EXPLODE_AFTER_LINE
        when {
            at >= 1 && at <= lastCrack && (at - 1) % TENDRIL_GAP == 0 -> {
                if (at == 1) boss.effects.anim(ANIM_CHARGE)
                tendril(state.order[(at - 1) / TENDRIL_GAP])
            }
            at == line -> {
                val pick = roll(0, OcellusLines.CORNER.size - 1)
                boss.effects.say(world.dialogueText.get(OcellusLines.CORNER[pick]))
                log("corner line ${pick + 1}")
            }
            at == explode -> explode()
            at >= explode + WALK_AFTER_EXPLODE -> {
                state.corner = (state.corner + 1) % CORNERS.size
                state.doing = Doing.WALKING
                // Guessed: his first shot a full spacing after he walks on, as at the start of the phase.
                state.nextShot = phaseTick + SHOT_EVERY
                protect(true)
                log("on to the ${cornerName(state.corner)} corner")
                walk()
            }
        }
    }

    /** A crack laid on spot [spot]: it shows [TENDRIL_SHOWS] ticks on, and its energy flows back [TENDRIL_FLOWS] on. */
    private fun EncounterContext.tendril(spot: Int) {
        val middle = middle(boss.spawn.x, boss.spawn.z)
        val tiles = spots(boss.x, boss.z, middle.x, middle.z, boss.type.size)[spot]
        val diagonal = spot >= 4
        val start = if (diagonal) SPOT_CRACK_DIAGONAL_START else SPOT_CRACK_STRAIGHT_START
        for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, start)
        after(TENDRIL_SHOWS) {
            // Not once his bar is out: the hold keeps the fight's queue, and the crack went with his first phase.
            if (shieldOut) return@after
            val shown = if (diagonal) SPOT_CRACK_DIAGONAL else SPOT_CRACK_STRAIGHT
            for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, shown)
        }
        state.cracks += Crack(tiles, phaseTick + TENDRIL_FLOWS)
        log("crack ${spot + 1} on ${tiles.first().x},${tiles.first().z} to ${tiles.last().x},${tiles.last().z}")
    }

    /** The cracks whose energy flows back this tick: caught by whoever stands on one, or his charge. */
    private fun EncounterContext.flowCracks() {
        val due = state.cracks.filter { it.flows <= phaseTick }
        if (due.isEmpty()) return
        state.cracks.removeAll(due.toSet())
        for (crack in due) {
            // A catch can kill him (a player's Retribution as the nick kills them): nothing more once he is dead, as
            // with his explosion's rings (Codex, PR 552).
            if (shieldOut) return
            val end = if (crack.tiles.first().let { a -> crack.tiles.last().let { b -> a.x != b.x && a.z != b.z } }) SPOT_CRACK_DIAGONAL_END else SPOT_CRACK_STRAIGHT_END
            for (tile in crack.tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, end)
            val on = players.filter { p -> crack.tiles.any { it.x == p.x && it.z == p.z } }
            if (on.isEmpty()) {
                state.charge = minOf(CHARGE_MAX, state.charge + CHARGE_PER)
                boss.effects.spotanim(0, SPOT_CHARGE, 0)
                log("uncaught: charge ${state.charge}")
            } else {
                for (player in on) {
                    if (shieldOut) return
                    intercept(player)
                }
            }
        }
    }

    /**
     * [player] catches a crack's energy: healed what they lost since their last interception, from the most they had
     * since (guides; tsps' heal hitsplat), then its hit of [CRACK_MIN] to [CRACK_MAX] (Sam's recordings).
     */
    private fun EncounterContext.intercept(player: Player) {
        val most = maxOf(state.peak[player] ?: player.hitpoints, player.hitpoints)
        val healed = heal(player, most - player.hitpoints)
        val dealt = fromTheRoom(player, roll(CRACK_MIN, CRACK_MAX))
        state.peak[player] = player.hitpoints
        log("${player.username} catches a crack: healed $healed, hit for $dealt")
    }

    /** Heals [player] by [amount], with the heal hitsplat; never past their base Hitpoints or what they had. */
    private fun EncounterContext.heal(player: Player, amount: Int): Int {
        if (amount <= 0 || player.dead) return 0
        val cap = player.skills.baseLevel(Skill.HITPOINTS)
        val healed = amount.coerceAtMost((cap - player.hitpoints).coerceAtLeast(0))
        if (healed <= 0) return 0
        player.hitpoints += healed
        player.effects.hit(-1, HitsplatType.HEAL, HitsplatType.HEAL, HitsplatType.HEAL, healed)
        player.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(player.hitpoints, cap))
        return healed
    }

    /**
     * [player] takes [damage] from the room: from no one, so they don't hit back, but in combat all the same, as a boss's
     * hazard holds them ([osrsps.game.encounter.Hazard]).
     */
    private fun EncounterContext.fromTheRoom(player: Player, damage: Int): Int {
        val dealt = world.meleeCombat.hitPlayer(player, null, damage, world.tick)
        if (!player.dead) player.attackedTick = world.tick
        return dealt
    }

    /** His explosion: the ring round him now, the ring outside it a tick on. */
    private fun EncounterContext.explode() {
        boss.effects.anim(ANIM_EXPLODE)
        boss.effects.spotanim(0, SPOT_EXPLODE, 0)
        log("explodes")
        ring(1)
        after(1) { ring(2) }
    }

    /** One ring of the explosion, [out] tiles off his footprint: everyone on it is hit. */
    private fun EncounterContext.ring(out: Int) {
        // His bar run out between the two rings, the outer one is gone with the rest of his first phase: the hold
        // keeps him alive, so the fight's queue still holds it (Codex, PR 559).
        if (shieldOut) return
        val size = boss.type.size
        val tiles = ((boss.x - out)..(boss.x + size - 1 + out)).flatMap { x -> ((boss.z - out)..(boss.z + size - 1 + out)).map { z -> Tile(x, z) } }
            .filter { Bork.distance(boss.x, boss.z, size, it.x, it.z) == out }
        for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_EXPLODE_FLOOR)
        for (player in players) {
            if (shieldOut) return
            if (Bork.distance(boss.x, boss.z, size, player.x, player.z) != out) continue
            val dealt = world.meleeCombat.hitPlayer(player, boss, roll(EXPLODE_MIN, EXPLODE_MAX), world.tick)
            log("explosion ring $out hits ${player.username} for $dealt")
        }
    }
}
