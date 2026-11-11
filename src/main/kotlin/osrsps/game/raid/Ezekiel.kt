package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.SpecialAttacks
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.map.Tile

/**
 * Ezekiel (raid steps 17a, 17b and 17c), one of the second row's two bosses, in his own room: a 3 by 3 justiciar who keeps
 * to his tank, the first player in, and walks after them.
 *
 * - Every [ATTACK_SPEED] ticks, with his tank beside him, a melee swing that cleaves almost all the way round him: it
 *   hits everyone beside him but those on his side opposite the tank ([behind]), the safe spot.
 * - With his tank out of his reach as an attack is due, lightning on every player in the room instead ([MAGIC_MAX]).
 * - After every second swing, a cross of lightning ([cross]) centred on him, his width wide, bursting twice: [CROSS_DELAY]
 *   ticks after it shows and [CROSS_GAP] after that. One on it the tick before a burst who steps off as it lands, a
 *   perfect dodge, gets [DODGE_ENERGY] of special attack energy back.
 * - With the cross, unless some are out already, [SWORDS_MIN] to [SWORDS_MAX] flying swords ([volley]) at the arena's
 *   edge, each at the end of a lane his width wide ([lane]) straight across it. [SWORD_LAUNCH] ticks on they fly: one
 *   that finds him wholly in its lane hits him and stuns him ([STUN_TICKS]: he neither walks nor attacks); if none
 *   does, he heals [HEAL_PERCENT] of his hitpoints. Whoever is in a lane is hit, and a perfect dodge of one counts too.
 *   Damage dealt to a sword charges it, up to [CHARGE_HITPOINTS]: a charged one hits him harder ([swordDamage]), and
 *   the players in its lane too ([laneDamage]). They're weak to air spells and resist ranged (npc-combat.json).
 * - At [ENRAGE_PERCENT] of his hitpoints, the enrage ([enrage]): the swords out go, and [ENRAGE_TRANSITION] ticks on he
 *   stands west in his arena, a fence of swords shuts every player into the three by three east of him ([arena]), and
 *   volleys of [ENRAGE_STRIKES] strikes follow every [ENRAGE_VOLLEY_EVERY] ticks till he dies: two of the three marks on
 *   his tiles struck, then lightning on the whole three by three but its far tile on the unstruck mark's row ([strike]).
 *
 * From the OSRS Wiki (Ezekiel, read 2026-11-09): his stats and bonuses, 3 by 3, the 5-tick attack speed, max hit 77,
 * magic and melee, the 25% earth weakness, the poison and venom immunity, Defence drained to 120 at most, his
 * hitpoints by party size, and the enrage at 35% (step 17c); The Fractured Archive/Strategies' inventory table: wind
 * spells "empower the Animated Swords, dealing additional damage if they strike the boss". From guides (osrstoolkit,
 * osrsbestinslot, runescapeguides, bald.gg; read 2026-11-09): the cleave round him with the safe spot directly behind,
 * opposite the tank; the tank the first player in; the lightning on everyone when he can't reach the tank; the
 * "cross-shaped AoE lightning attack centered on him" after every two melee attacks, which "bursts twice", dodged by a
 * diagonal step; a perfect dodge (in the path of a sword or a strike, stepping out on the tick) giving 3% special attack
 * energy; three to four swords after the cross starts, flying in a straight line once their timer fills, a sword lined
 * up with him stunning him "for a few seconds" and none doing so healing him "a significant amount"; hitting a sword
 * charging it, which raises its damage to him (80 at full charge, bald.gg) and to the player; swords very weak to air
 * magic and resisting ranged. From the cache: `tfa_ezekiel` (16652), `tfa_ezekiel_sword` (16653), his `npc_ezekiel_*`
 * animations and `ezekiel_*` spotanims. From the tsps capture notes: his arena ([ARENA_WEST] to [ARENA_NORTH]), the swords
 * flying across it edge to edge, where he stands for the enrage ([ENRAGE_DX], [ENRAGE_DZ]) with its three by three in
 * front of him, east, three swords on his own tiles, and the spared tile being in the three by three's east column.
 *
 * Guessed, until a recording shows the fight (each also in the README): where he stands as the fight begins
 * ([ArchiveRaids.EZEKIEL_X]), the first attack [ATTACK_SPEED] ticks in, the safe spot being his whole side opposite
 * the tank (its corner for a tank at a corner), the lightning's damage ([MAGIC_MAX]) and its landing a tick after the
 * cast, the cross's reach ([CROSS_REACH]), its width (his), its timings and damage ([CROSS_MIN] to [CROSS_MAX]), the melee's
 * max hit being the Wiki's 77; for the swords, their lanes (his width, at
 * random, from a random end), the count picked at random, one volley out at a time, all of a volley flying together and
 * at once along the whole lane (live's fly across), their timer, damage, charge, the stun's and the heal's size, and
 * the sword's own type (16653 rather than the `_noop` one) and air weakness; for the enrage, its fence's shape, the fence holding them by putting back whoever steps off ([hold]), where each
 * player lands, the transition's length, the strikes' gap, delay and
 * damage, Protect from Magic halving it, the marks' and impacts' spotanims picked by name, and that he neither moves
 * nor swings in it. From the Wiki (Ezekiel): the 35%, the 3x3 arena, the volleys of 3 lightning attacks shown by which
 * swords are struck, dodged on the tile of the sword not struck; its changes (28 October and 3 November 2026): no
 * sword of the fight before hurting anyone through the enrage, and no stun animation over the enrage's.
 */
object Ezekiel {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "ezekiel"

    /** `tfa_ezekiel`. */
    const val BOSS = 16652

    /** `tfa_ezekiel_sword`: a flying sword, waiting at its lane's end to fly. */
    const val SWORD = 16653

    /** `tfa_ezekiel_sword_noop`: a sword that only stands, the enrage's fence round the players and its three marks. */
    const val SWORD_NOOP = 16654

    /** npc_ezekiel_*: his swing, his lightning cast and his cross burst (special). */
    const val ANIM_MELEE = 14685
    const val ANIM_MAGIC = 14686
    const val ANIM_SPECIAL = 14687

    /** npc_ezekiel_heal01, and his stun (a sword's hit): into it, held, and out. */
    const val ANIM_HEAL = 14688
    const val ANIM_WEAKEN_START = 14691
    const val ANIM_WEAKEN = 14692
    const val ANIM_WEAKEN_END = 14693

    /** The sword's spin as it flies. */
    const val ANIM_SWORD_SPIN = 14707

    /** The enrage: npc_ezekiel_finale01 as it begins, then npc_ezekiel_finale_loop01 as he casts from his place. */
    const val ANIM_FINALE = 14694
    const val ANIM_FINALE_LOOP = 14696

    /** vfx_ezekiel_sword_fence_drop01 on each sword of the fence, vfx_ezekiel_finale_sword_drop01 on the three marks. */
    const val ANIM_FENCE_DROP = 14731
    const val ANIM_MARK_DROP = 14710

    /** ezekiel_magic_attack_vfx01 and its impact; ezekiel_special_crack_vfx01; the star explosion's marker and burst. */
    const val SPOT_MAGIC = 4167
    const val SPOT_MAGIC_IMPACT = 4168
    const val SPOT_CRACK = 4169
    const val SPOT_CROSS_MARKER = 4171
    const val SPOT_CROSS = 4172

    /** ezekiel_heal_vfx01, his heal's glow. */
    const val SPOT_HEAL = 4173

    /**
     * vfx_ezekiel_sword_telegraph03, the lightning on a struck mark, and vfx_ezekiel_finale_impact_large01, the enrage's
     * lightning landing on a player.
     */
    const val SPOT_MARK_STRUCK = 4190
    const val SPOT_FINALE_IMPACT = 4196

    /** The Wiki's attack speed. */
    const val ATTACK_SPEED = 5

    /** His lightning on everyone when he can't reach his tank: up to this, Protect from Magic blocking it. Guessed. */
    const val MAGIC_MAX = 30

    /** The cross: its arms reach this many tiles out from his sides. Guessed ("large"). */
    const val CROSS_REACH = 6

    /** The cross's first burst this many ticks after it shows, its second this many after the first. Guessed. */
    const val CROSS_DELAY = 3
    const val CROSS_GAP = 2

    /** The cross's damage on whoever is on it as a burst lands. Guessed. */
    const val CROSS_MIN = 20
    const val CROSS_MAX = 40

    /** A perfect dodge's special attack energy: 3% (a guide's), in tenths of a percent. */
    const val DODGE_ENERGY = 30

    /**
     * His arena, from his spawn's south-west tile: 4056-4068 x 5528-5540 round 4062,5537 (tsps capture notes). The swords
     * fly across it, edge to edge. It was a guessed 10 tiles each way of him until exploit review round 21 (R21A-3):
     * swords then stood where nobody on the arena could reach them, one end at the room's way in.
     */
    const val ARENA_WEST = -6
    const val ARENA_EAST = 6
    const val ARENA_SOUTH = -9
    const val ARENA_NORTH = 3

    /** Swords in a volley (a guide's three to four, picked at random here). */
    const val SWORDS_MIN = 3
    const val SWORDS_MAX = 4

    /**
     * Ticks from a volley showing to its flight: the guides' red bar. Guessed. The bar itself isn't shown, as its cache
     * head-bar id isn't known; nor is the yellow charge bar, a sword showing only its health bar falling as it charges.
     */
    const val SWORD_LAUNCH = 15

    /** Damage a sword can take, its charge: the guides' yellow bar. Guessed. */
    const val CHARGE_HITPOINTS = 100

    /** A sword's hit on him, uncharged and at full charge (bald.gg's 80). The uncharged one guessed. */
    const val SWORD_HIT = 20
    const val SWORD_HIT_FULL = 80

    /** A sword's hit on a player in its lane, uncharged; at full charge twice that. Guessed. */
    const val LANE_MIN = 10
    const val LANE_MAX = 25

    /** Ticks a sword's hit stuns him. Guessed ("a few seconds"). */
    const val STUN_TICKS = 8

    /** His heal when no sword of a volley hit him, in percent of his most hitpoints. Guessed ("a significant amount"). */
    const val HEAL_PERCENT = 10

    /** The enrage begins at this share of his hitpoints, in percent (the Wiki's 35%; osrstoolkit says 33%). */
    const val ENRAGE_PERCENT = 35

    /**
     * Where he stands for the enrage, his south-west tile from his spawn's: 4059,5534 from 4062,5537, the three by
     * three in front of him 4062-4064 x 5534-5536 (tsps capture notes).
     */
    const val ENRAGE_DX = -3
    const val ENRAGE_DZ = -3

    /**
     * Ticks from the enrage beginning to the players brought into its arena. Guessed; longer than a cross's two bursts
     * take, so a cross already shown lands before anyone is shut in where they couldn't dodge it.
     */
    const val ENRAGE_TRANSITION = 6

    /** The enrage's volleys of [ENRAGE_STRIKES] strikes, one every this many ticks (bald.gg's 11). */
    const val ENRAGE_VOLLEY_EVERY = 11

    /** Strikes in a volley (the Wiki's "volleys of 3 lightning attacks"), this many ticks apart. The gap guessed. */
    const val ENRAGE_STRIKES = 3
    const val ENRAGE_STRIKE_GAP = 3

    /** Ticks from the marks being struck to the lightning landing. Guessed (a guide's dodge: a tick's wait, then a step). */
    const val ENRAGE_STRIKE_DELAY = 2

    /** A strike's damage on whoever is on one of its tiles, halved by Protect from Magic (a guide's "reduces"). Guessed. */
    const val ENRAGE_MIN = 30
    const val ENRAGE_MAX = 60

    /** The fight, or null when the cache hasn't Ezekiel (a test world's). Without the sword's type, no swords fly. */
    fun def(types: NpcTypes): EncounterDef? {
        val type = types[BOSS] ?: return null
        val sword = types[SWORD]
        val mark = types[SWORD_NOOP]
        return EncounterDef(
            NAME, type,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(
                        CycleStep(1) {
                            val tank = tank() ?: return@CycleStep
                            if (stunned()) return@CycleStep
                            chase(tank)
                            if (phaseTick > 0 && phaseTick % ATTACK_SPEED == 0L) attack(tank, sword)
                        },
                    ),
                    endsAtPercent = ENRAGE_PERCENT,
                    onStart = {
                        state.swings = 0
                        // He walks after his tank anywhere in his room, never giving up for being far from his spawn.
                        boss.unleashed = true
                        tank()
                    },
                ),
                PhaseDef(
                    "enrage",
                    cycle = listOf(CycleStep(1) { enrageTick(mark) }),
                    onStart = { enrage() },
                ),
            ),
            scriptedAttacks = true,
        )
    }

    /** What a fight keeps between ticks, by boss: the context keeps none. Holds no NPC, and its tank only weakly. */
    private class State {
        /** Attacks since the fight began, swings and lightning alike: every second one brings a cross. */
        var swings = 0

        /** His tank, kept here as his combat target can go (they died, or left). */
        var tank: WeakReference<Player>? = null

        /** The tick his stun wears off; he's stunned before it. */
        var stunnedUntil = 0L

        /**
         * Whether a volley is out, till it flies. Only the flag: the swords themselves stay in the fight's own callbacks,
         * which its end lets go of, so nothing here can hold a sword, its attacker and through them the boss.
         */
        var volleyOut = false

        /** Whether the enrage has begun: nothing of the fight before it lands after. */
        var enraged = false

        /** The enrage's arena's south-west tile, once the players are in it; its three by three east of him. */
        var arena: Tile? = null
    }

    /** A flying sword at the end of its [lane], across the arena [alongX] or along z, at [offset]. */
    private class Sword(val npc: Npc, val alongX: Boolean, val offset: Int, val lane: Set<Tile>)

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    /**
     * His tank: the player he fights while they're in the fight, else the one he was set on (the first player in), else
     * the first in the fight.
     */
    private fun EncounterContext.tank(): Player? {
        val fighting = players
        state.tank?.get()?.let { held -> if (fighting.any { it === held }) return held }
        val pick = boss.target?.takeIf { held -> fighting.any { it === held } } ?: fighting.firstOrNull() ?: return null
        state.tank = WeakReference(pick)
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        log("takes on ${pick.username}")
        return pick
    }

    /** Whether a sword's hit has him stunned: he stands, neither walking nor attacking. */
    private fun EncounterContext.stunned(): Boolean = world.tick < state.stunnedUntil

    /** He walks after his tank till he's beside them, and stands while he is. */
    private fun EncounterContext.chase(tank: Player) {
        if (beside(tank.x, tank.z)) {
            if (boss.following === tank) world.npcs.stopFollowing(boss)
            boss.face(tank)
        } else if (boss.following !== tank) {
            world.npcs.follow(boss, tank)
        }
    }

    /** Whether ([x], [z]) is beside him, where his swing reaches: a tile along one of his sides, not off a corner. */
    private fun EncounterContext.beside(x: Int, z: Int): Boolean {
        val size = boss.type.size
        val alongX = x in boss.x until boss.x + size && (z == boss.z - 1 || z == boss.z + size)
        val alongZ = z in boss.z until boss.z + size && (x == boss.x - 1 || x == boss.x + size)
        return alongX || alongZ
    }

    /** Whether ([x], [z]) is round him, a tile off a side or a corner: where his cleave reaches. */
    private fun EncounterContext.round(x: Int, z: Int): Boolean {
        val size = boss.type.size
        val inBox = x in boss.x - 1..boss.x + size && z in boss.z - 1..boss.z + size
        return inBox && !boss.covers(x, z)
    }

    /**
     * Whether ([x], [z]) is his safe spot, behind him opposite [tank]: the tiles round him on the side away from them, or
     * the corner away from them for a tank off his corner. Guessed: the guides say only "directly behind him".
     */
    fun behind(bossX: Int, bossZ: Int, size: Int, tank: Tile, x: Int, z: Int): Boolean {
        val dx = (tank.x - bossX).let { if (it < 0) -1 else if (it >= size) 1 else 0 }
        val dz = (tank.z - bossZ).let { if (it < 0) -1 else if (it >= size) 1 else 0 }
        if (dx == 0 && dz == 0) return false
        val sx = (x - bossX).let { if (it < 0) -1 else if (it >= size) 1 else 0 }
        val sz = (z - bossZ).let { if (it < 0) -1 else if (it >= size) 1 else 0 }
        if (dx != 0 && dz != 0) return sx == -dx && sz == -dz
        return if (dx != 0) sx == -dx else sz == -dz
    }

    /**
     * His attack, as it's due: the cleave with his tank beside him, else lightning on everyone. Either counts toward the
     * cross, so kiting him out of reach still brings it and the swords (Sam's call, 2026-11-09: no source says either way).
     */
    private fun EncounterContext.attack(tank: Player, sword: NpcType?) {
        if (!beside(tank.x, tank.z)) {
            lightning()
            counted(sword)
            return
        }
        val tankTile = Tile(tank.x, tank.z)
        val cleaved = players.filter { it !== tank && round(it.x, it.z) && !behind(boss.x, boss.z, boss.type.size, tankTile, it.x, it.z) }
        // The others first, so he ends facing his tank.
        for (player in cleaved) {
            if (gone()) return
            world.meleeCombat.swingAt(boss, player, world.tick, ANIM_MELEE)
            log("cleaves ${player.username}")
        }
        if (gone()) return
        world.meleeCombat.swingAt(boss, tank, world.tick, ANIM_MELEE)
        log("swings at ${tank.username}")
        counted(sword)
    }

    /** One more attack, swing or lightning: every second brings the cross, and the swords unless some are out. */
    private fun EncounterContext.counted(sword: NpcType?) {
        state.swings += 1
        // Not when his attack left no one in the room.
        if (state.swings % 2 == 0 && players.isNotEmpty()) {
            crosses()
            if (sword != null && !state.volleyOut) volley(sword)
        }
    }

    /** Lightning on every player in the fight, landing next tick. */
    private fun EncounterContext.lightning() {
        boss.effects.anim(ANIM_MAGIC)
        boss.effects.spotanim(0, SPOT_MAGIC, 0)
        val struck = players
        log("can't reach his tank: lightning on ${struck.joinToString { it.username }}")
        after(1) {
            for (player in struck) {
                if (gone()) return@after
                if (player.dead || player !in players) continue
                player.effects.spotanim(0, SPOT_MAGIC_IMPACT, 0, 0)
                val damage = if (world.prayers.protects(player, AttackType.MAGIC)) 0 else roll(0, MAGIC_MAX)
                hit(player, damage)
            }
        }
    }

    /**
     * The cross centred on a [size] by [size] boss at ([x], [z]): two bands his width wide through him, along each axis,
     * reaching [CROSS_REACH] tiles out from his sides; his own tiles aren't in it. Off his corners is off it.
     */
    fun cross(x: Int, z: Int, size: Int): Set<Tile> {
        val tiles = LinkedHashSet<Tile>()
        for (along in -CROSS_REACH until size + CROSS_REACH) {
            for (across in 0 until size) {
                if (along in 0 until size) continue
                tiles += Tile(x + along, z + across)
                tiles += Tile(x + across, z + along)
            }
        }
        return tiles
    }

    /** The cross on him now, bursting twice, and the special attack energy for whoever dodges a burst at its last tick. */
    private fun EncounterContext.crosses() {
        val tiles = cross(boss.x, boss.z, boss.type.size)
        boss.effects.anim(ANIM_SPECIAL)
        boss.effects.spotanim(0, SPOT_CRACK, 0)
        // Its own line, as the hazard's, naming its 72 tiles, is too long for `::raid log`'s chat.
        log("cross on him at ${boss.x},${boss.z}")
        hazard(tiles, warning = SPOT_CROSS_MARKER, warnTicks = CROSS_DELAY, ticks = 1, minDamage = CROSS_MIN, maxDamage = CROSS_MAX, spotanim = SPOT_CROSS)
        hazard(tiles, warning = -1, warnTicks = CROSS_DELAY + CROSS_GAP, ticks = 1, minDamage = CROSS_MIN, maxDamage = CROSS_MAX, spotanim = SPOT_CROSS)
        perfectDodge(tiles, CROSS_DELAY, "the cross")
        perfectDodge(tiles, CROSS_DELAY + CROSS_GAP, "the cross")
    }

    /**
     * Whoever is on [tiles] the tick before a strike lands on them [ticks] ticks from now and off them as it lands, a
     * perfect dodge, gets [DODGE_ENERGY] of special attack energy back.
     */
    private fun EncounterContext.perfectDodge(tiles: Set<Tile>, ticks: Int, what: String, lands: EncounterContext.() -> Boolean = { true }) {
        var onIt: List<Player> = emptyList()
        if (ticks > 1) after(ticks - 1) { onIt = players.filter { Tile(it.x, it.z) in tiles } } else onIt = players.filter { Tile(it.x, it.z) in tiles }
        after(ticks) {
            // Nothing to dodge when what was coming never came.
            if (!lands()) return@after
            for (player in onIt) {
                if (player.dead || player !in players || Tile(player.x, player.z) in tiles) continue
                val energy = world.specialAttacks.energy(player)
                world.specialAttacks.setEnergy(player, minOf(SpecialAttacks.FULL, energy + DODGE_ENERGY))
                log("${player.username} dodged $what")
            }
        }
    }

    /**
     * The tiles of a sword's lane: [width] wide from [offset] (z for one across the arena [alongX], x for one along z),
     * from [min] to [max] the other way, both included.
     */
    fun lane(alongX: Boolean, offset: Int, width: Int, min: Int, max: Int): Set<Tile> {
        val tiles = LinkedHashSet<Tile>()
        for (along in min..max) for (across in offset until offset + width) tiles += if (alongX) Tile(along, across) else Tile(across, along)
        return tiles
    }

    /** Whether a boss at ([x], [z]) is lined up with the lane [alongX] at [offset], however far along it. */
    fun linedUp(alongX: Boolean, offset: Int, x: Int, z: Int): Boolean = if (alongX) z == offset else x == offset

    /**
     * Whether a [size] by [size] boss at ([x], [z]) is wholly in [lane]: lined up with it and between its ends, where its
     * sword hits him. Past an end of the lane, where it can't hit a player either, it misses him.
     */
    fun inLane(lane: Set<Tile>, x: Int, z: Int, size: Int): Boolean =
        (0 until size).all { dx -> (0 until size).all { dz -> Tile(x + dx, z + dz) in lane } }

    /** Whether ([x], [z]) is on the arena of an Ezekiel who began with his south-west tile at ([spawnX], [spawnZ]). */
    fun inArena(spawnX: Int, spawnZ: Int, x: Int, z: Int): Boolean =
        x - spawnX in ARENA_WEST..ARENA_EAST && z - spawnZ in ARENA_SOUTH..ARENA_NORTH

    /** How much of its [CHARGE_HITPOINTS] [sword] has taken, from 0 to 1. */
    private fun charge(sword: Npc): Double = (CHARGE_HITPOINTS - sword.hitpoints.coerceIn(0, CHARGE_HITPOINTS)).toDouble() / CHARGE_HITPOINTS

    /** A sword's hit on him: [SWORD_HIT] uncharged, [SWORD_HIT_FULL] at full charge, between in step with its charge. */
    fun swordDamage(charge: Double): Int = SWORD_HIT + ((SWORD_HIT_FULL - SWORD_HIT) * charge.coerceIn(0.0, 1.0)).toInt()

    /** A sword's hit on a player in its lane: [LANE_MIN] to [LANE_MAX] uncharged, up to twice that at full charge. */
    fun laneDamage(charge: Double): IntRange {
        val times = 1.0 + charge.coerceIn(0.0, 1.0)
        return (LANE_MIN * times).toInt()..(LANE_MAX * times).toInt()
    }

    /**
     * A volley of swords, each at an end of a lane across the arena and wholly on it, flying [SWORD_LAUNCH] ticks on.
     */
    private fun EncounterContext.volley(type: NpcType) {
        val size = boss.type.size
        val west = boss.spawn.x + ARENA_WEST
        val east = boss.spawn.x + ARENA_EAST
        val south = boss.spawn.z + ARENA_SOUTH
        val north = boss.spawn.z + ARENA_NORTH
        val count = roll(SWORDS_MIN, SWORDS_MAX)
        val swords = ArrayList<Sword>()
        val taken = HashSet<Pair<Boolean, Int>>()
        repeat(count) {
            // Each its own lane: two on one would hit as one, twice. Guessed: live's paths look distinct.
            var alongX: Boolean
            var offset: Int
            var tries = 0
            do {
                alongX = roll(0, 1) == 0
                offset = if (alongX) roll(south, north - size + 1) else roll(west, east - size + 1)
            } while (!taken.add(alongX to offset) && ++tries < 20)
            val (min, max) = if (alongX) west to east else south to north
            // At either end of its lane, across the lane's width and inside the arena's edge.
            val end = if (roll(0, 1) == 0) min else max - size + 1
            val (x, z) = if (alongX) end to offset else offset to end
            val npc = spawnAdd(type, x, z) ?: return@repeat
            npc.scriptedAttacks = true
            npc.holdsAtZero = true
            npc.scaledHitpoints = CHARGE_HITPOINTS
            npc.hitpoints = CHARGE_HITPOINTS
            swords += Sword(npc, alongX, offset, lane(alongX, offset, size, min, max))
            log("sword at $x,$z across ${if (alongX) "x" else "z"} at $offset")
        }
        if (swords.isEmpty()) return
        state.volleyOut = true
        for (sword in swords) perfectDodge(sword.lane, SWORD_LAUNCH, "a sword") { !state.enraged }
        after(SWORD_LAUNCH) { fly(swords) }
    }

    /** The volley flies: each hits whoever is in its lane, and him if he's lined up with it; none hitting him heals him. */
    private fun EncounterContext.fly(swords: List<Sword>) {
        // The enrage took them away: none flies, so none hurts anyone (the Wiki's changes, 28 October 2026), nor heals him.
        if (state.enraged) return
        state.volleyOut = false
        var struck = false
        for (sword in swords) {
            if (gone()) return
            val npc = sword.npc
            if (world.npcs[npc.index] !== npc) continue
            val charge = charge(npc)
            npc.effects.anim(ANIM_SWORD_SPIN)
            val damage = laneDamage(charge)
            // Lands this tick, after this, on whoever is in the lane.
            hazard(sword.lane, warning = -1, warnTicks = 0, ticks = 1, minDamage = damage.first, maxDamage = damage.last)
            if (inLane(sword.lane, boss.x, boss.z, boss.type.size)) {
                struck = true
                val dealt = world.meleeCombat.hit(boss, null, swordDamage(charge), world.tick)
                // For whoever charged it (exploit review R28A-3).
                world.archiveRaids.creditFrom(npc, boss, dealt)
                log("sword hits him for $dealt")
                if (gone()) return
                stun()
            }
            // Gone the tick after, so its spin is sent first: NPC effects go out at the end of the tick, to those in the world.
            after(1) { if (world.npcs[npc.index] === npc) world.removeNpc(npc) }
        }
        if (!struck) heal()
    }

    /** A sword's hit: he stands, neither walking nor attacking, for [STUN_TICKS]. */
    private fun EncounterContext.stun() {
        val fresh = !stunned()
        state.stunnedUntil = world.tick + STUN_TICKS
        world.npcs.stopFollowing(boss)
        if (fresh) {
            boss.effects.anim(ANIM_WEAKEN_START)
            after(1) { if (stunned() && !state.enraged) boss.effects.anim(ANIM_WEAKEN) }
        }
        val until = state.stunnedUntil
        // Not over the enrage's own animation (the Wiki's changes, 3 November 2026).
        after(STUN_TICKS) { if (state.stunnedUntil == until && !state.enraged) boss.effects.anim(ANIM_WEAKEN_END) }
        log("stunned for $STUN_TICKS ticks")
    }

    /** No sword hit him: he heals [HEAL_PERCENT] of his most hitpoints. */
    private fun EncounterContext.heal() {
        val before = boss.hitpoints
        boss.hitpoints = minOf(boss.maxHitpoints, before + boss.maxHitpoints * HEAL_PERCENT / 100)
        // His health bar shows it: bars go out only as they are queued, as a hit queues one.
        boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        boss.effects.anim(ANIM_HEAL)
        boss.effects.spotanim(0, SPOT_HEAL, 0)
        log("no sword hit him: heals ${boss.hitpoints - before}")
    }

    /**
     * The enrage begins: he stops where he is, out of any stun, the swords out are taken away unflown, and his finale
     * begins. [ENRAGE_TRANSITION] ticks on, [arena].
     */
    private fun EncounterContext.enrage() {
        state.enraged = true
        state.stunnedUntil = 0
        state.volleyOut = false
        world.npcs.stopFollowing(boss)
        for (add in adds.toList()) if (add.type.id == SWORD) world.removeNpc(add)
        boss.effects.anim(ANIM_FINALE)
        log("enrage at ${boss.hitpoints} hp")
    }

    /** The enrage's tick: the arena once its transition is over, then the volleys of strikes, till he dies. */
    private fun EncounterContext.enrageTick(mark: NpcType?) {
        if (phaseTick == ENRAGE_TRANSITION.toLong()) arena(mark)
        val arena = state.arena ?: return
        hold(arena)
        val since = phaseTick - ENRAGE_TRANSITION - 1
        if (since < 0) return
        val inVolley = (since % ENRAGE_VOLLEY_EVERY).toInt()
        if (inVolley % ENRAGE_STRIKE_GAP == 0 && inVolley / ENRAGE_STRIKE_GAP < ENRAGE_STRIKES) strike(arena)
    }

    /**
     * The fence holds every player in the fight in [arena]: one who stepped off it last tick is put back on its nearest
     * tile, before any strike lands. Guessed: live's fence presumably stops the step itself, but NPCs don't block a
     * player's walk here, and how live's does isn't known.
     */
    private fun EncounterContext.hold(arena: Tile) {
        val tiles = arenaTiles(arena)
        for (player in players) {
            if (Tile(player.x, player.z) in tiles) continue
            val back = tiles.minBy { maxOf(kotlin.math.abs(it.x - player.x), kotlin.math.abs(it.z - player.z)) }
            world.teleport(player, back.x, back.z, boss.level)
            log("${player.username} back into the arena")
        }
    }

    /** The enrage's three by three, east of him standing with his south-west tile at ([x], [z]): its south-west tile. */
    fun arenaFor(x: Int, z: Int): Tile = Tile(x + 3, z)

    /** The arena's nine tiles, from its south-west tile [arena]. */
    fun arenaTiles(arena: Tile): Set<Tile> = (0 until 3).flatMap { dx -> (0 until 3).map { dz -> Tile(arena.x + dx, arena.z + dz) } }.toSet()

    /**
     * The fence round [arena]: a column of five swords along its east side, and three along each of its north and south
     * sides, him its west side, the marks ([mark]) on his tiles there.
     */
    fun fence(arena: Tile): List<Tile> {
        val tiles = ArrayList<Tile>()
        for (dz in -1..3) tiles += Tile(arena.x + 3, arena.z + dz)
        for (dx in 0 until 3) {
            tiles += Tile(arena.x + dx, arena.z - 1)
            tiles += Tile(arena.x + dx, arena.z + 3)
        }
        return tiles
    }

    /**
     * The mark of [row] (0 the south): one of the three swords on his own tiles (tsps capture notes: "three finale
     * swords on his footprint"). Guessed: on his column beside [arena].
     */
    fun mark(arena: Tile, row: Int): Tile = Tile(arena.x - 1, arena.z + row)

    /**
     * The tile a strike leaving [row]'s mark unstruck spares: the arena's east column, the one furthest from him, on
     * that row (tsps capture notes: "the east row of the 3x3 at that sword's row", itself from guides).
     */
    fun safeTile(arena: Tile, row: Int): Tile = Tile(arena.x + 2, arena.z + row)

    /**
     * He goes to his place ([ENRAGE_DX], [ENRAGE_DZ] from his spawn), three swords drop on his own tiles, a fence of
     * swords goes up round the three by three in front of him, east, and every player in the fight is brought into it
     * (tsps capture notes). Guessed: the fence's shape (the notes count seven swords, not where they stand) and where
     * in the three by three each player lands. He was a guessed 6 tiles east of his spawn, two columns off his arena,
     * with the three by three west of him, until exploit review round 21 (R21A-3).
     */
    private fun EncounterContext.arena(mark: NpcType?) {
        val x = boss.spawn.x + ENRAGE_DX
        val z = boss.spawn.z + ENRAGE_DZ
        world.npcs.teleport(boss, x, z)
        boss.effects.anim(ANIM_FINALE_LOOP)
        val arena = arenaFor(x, z)
        val marks = (0 until 3).map { mark(arena, it) }
        if (mark != null) {
            for (tile in marks + fence(arena)) {
                val sword = spawnAdd(mark, tile.x, tile.z) ?: continue
                sword.scriptedAttacks = true
                sword.effects.anim(if (tile in marks) ANIM_MARK_DROP else ANIM_FENCE_DROP)
            }
        }
        // The middle first, then round it.
        val tiles = arenaTiles(arena).sortedBy { (it.x - arena.x - 1) * (it.x - arena.x - 1) + (it.z - arena.z - 1) * (it.z - arena.z - 1) }
        for ((i, player) in players.withIndex()) {
            val tile = tiles[i % tiles.size]
            world.teleport(player, tile.x, tile.z, boss.level)
        }
        state.arena = arena
        log("arena at ${arena.x},${arena.z}")
    }

    /**
     * A strike: two of the three marks struck, then, [ENRAGE_STRIKE_DELAY] ticks on, lightning on every tile of the arena
     * but the far column's on the unstruck mark's row ([safeTile]). A perfect dodge of it counts, as of the cross.
     */
    private fun EncounterContext.strike(arena: Tile) {
        val safeRow = roll(0, 2)
        for (row in 0 until 3) {
            if (row == safeRow) continue
            val at = mark(arena, row)
            adds.firstOrNull { it.type.id == SWORD_NOOP && it.x == at.x && it.z == at.z }?.effects?.spotanim(0, SPOT_MARK_STRUCK, 0)
        }
        boss.effects.anim(ANIM_MAGIC)
        val tiles = arenaTiles(arena) - safeTile(arena, safeRow)
        log("lightning, safe row $safeRow")
        perfectDodge(tiles, ENRAGE_STRIKE_DELAY, "the lightning")
        after(ENRAGE_STRIKE_DELAY) {
            for (player in players) {
                if (gone()) return@after
                if (Tile(player.x, player.z) !in tiles || player.level != boss.level) continue
                player.effects.spotanim(0, SPOT_FINALE_IMPACT, 0, 0)
                val roll = roll(ENRAGE_MIN, ENRAGE_MAX)
                val dealt = hit(player, if (world.prayers.protects(player, AttackType.MAGIC)) roll / 2 else roll)
                log("lightning hits ${player.username} for $dealt")
            }
        }
    }

    /** Whether the fight is over, the boss dead or gone: nothing lands after. */
    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss
}
