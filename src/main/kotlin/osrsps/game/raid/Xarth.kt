package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import osrsps.game.NpcStat
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.HitDelay
import osrsps.game.combat.MeleeCombat
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.game.zone.ZoneLoc
import osrsps.map.LineOfSight
import osrsps.map.Tile

/**
 * What Xarth's fight says. The chat message's words are those Sam's recordings of 2026-11-09 show, typed into the
 * dialogue file like every other line.
 */
object XarthLines : Lines("tfa_xarth", "The_Fractured_Archive/Strategies") {
    val SET_ALIGHT = line("set_alight", "the chat message as one of Xarth's fireballs sets a player alight (Sam's recordings of 2026-11-09)")
}

/**
 * Xarth (raid steps 19a, then 19b and 19c), the fourth room's east boss: a 3 by 3 tormented demon who never moves, in
 * the middle of his arena, with three 2 by 2 rubble heaps round him that the party hides behind.
 *
 * Phase one (19a), [CYCLES] cycles of [CYCLE] ticks, the first [FIRST_CYCLE] ticks in:
 * - [MAGIC_ATTACKS] fireballs [MAGIC_GAP] ticks apart ([fireball]), each at the player nearest him. One that hits sets
 *   them alight ([burn]): a burn that starts again, a stack hotter, with each fireball that hits while it runs.
 * - [AOE_AT] ticks into the cycle his AoE ([aoe]): every player is bound where they stand for [BIND_TICKS] ticks while a
 *   projectile comes down on their tile.
 * - [BLAST_AFTER_AOE] ticks later the flame blast ([flameBlast]), and [WAVE_AFTER_BLAST] after that the wave
 *   ([wave]), rolling out of his footprint a client cycle per tile: in the first two cycles a 90 degree wedge
 *   ([inWedge]) towards the heap that falls, all round him in the third. The falling heap shelters the tiles behind it
 *   ([shelter]): none of the wave behind it as seen from his middle tile, only the smaller explosion where it hides the
 *   tile from his nearest footprint tile alone. A player stood between him and another takes the wave's full force
 *   and shields the one behind ([SCREENED_MIN] to [SCREENED_MAX]).
 * - The heaps fall in the order [fallOrder] picks, south or north first, then east, then the other; a fallen one's
 *   NPC goes and its collapse plays for [COLLAPSE_TICKS] ticks where it stood.
 *
 * Phase two (19b), from the tick after the third wave:
 * - [TWO_FIRES] arena fires ([LOC_ARENA_FIRE]) [FIRE_OUT_MIN] to [FIRE_OUT_MAX] tiles from his footprint, and
 *   [SPREAD_FIRES] more next to those already burning every cycle ([spread]). They never go out. A player on one is
 *   hurt for [FIRE_MIN] to [FIRE_MAX] and set alight, at most once every [FIRE_EVERY] ticks ([fires]).
 * - No more magic: his ranged ([shoot]) at [TWO_SHOTS_AT] ticks in, then in each [TWO_CYCLE]-tick cycle from
 *   [TWO_CYCLE_FROM] [TWO_SHOTS] shots [TWO_SHOT_GAP] ticks apart from [TWO_SHOT_FROM], Protect from Missiles
 *   blocking them all; a melee swing instead at a player beside him.
 * - At the start of each cycle he raises [SUMMONS] Corpses ([SUMMONS_BIG] for a party of five or more) [SUMMON_GAP]
 *   ticks apart ([summon]): each rises at an edge tile of the arena [CORPSE_AFTER] ticks on and walks a tile a tick to
 *   him ([corpses]). One that gets beside him is absorbed ([absorb]), healing him twice the hitpoints it has left, and
 *   every second absorbed sets off the blast ([blast]): the flame blast, then a wave all round him that sets those it
 *   hits alight.
 *
 * From the tsps capture notes (RSPSApp/tsps docs/fractured-archive.md, read 2026-11-10: five spectator captures of live
 * fights on rev 241.1, so they show no damage on players): where he and the heaps stand, the three 31-tick cycles of
 * five fireballs four ticks apart, the AoE with a projectile at each player's tile, the flame blast three ticks after
 * it and the wave three after that, the wave's wedge towards the falling heap and its all-round third, the order the
 * heaps fall in (south or north at random, then east), the shelter by projectile line of sight from his middle tile and
 * from his nearest footprint tile (3,892 of 3,920 captured tiles), the heap's collapse for three ticks, and his
 * footprint's nine blocking tiles. From the OSRS Wiki (Xarth, The Fractured Archive/Strategies; read 2026-11-10): his
 * stats and bonuses (npc-combat.json), his hitpoints by party size (party-scaling.json), magic only in phase one at the
 * player closest to him, a fireball that hits setting a burn that "will reset and stack on successive hits", the bind
 * on everyone before the blast, the wave's ~45 behind no cover, and a player acting as a pillar taking the full ~45
 * while those behind take ~7. From Sam's recordings of 2026-11-09: the set-alight message ([XarthLines.SET_ALIGHT]) twice early in each
 * fight; the burn's 7 or 8 every 4 ticks; a fireball's 1 to 6 through Protect from Missiles, and its flight; the AoE's
 * flames reaching the party 2 ticks on and hitting for 23; a wave's 100 on a player in the open, and a player south of a heap taking none
 * (the Review thread's dense pass of the clips, 2026-11-10). From the cache (RuneLite's gamevals): the ids below.
 *
 * Guessed, until a recording shows more (each also in the README): the first cycle's start ([FIRST_CYCLE]); the AoE
 * four ticks after the last fireball ([AOE_AT]); the bind's length (to the flame blast); which prayers cut a fireball; the target's tie broken by the lowest player index; the fireball reaching anywhere in the room
 * with no line of sight needed; a prayed fireball setting no one alight; the AoE's range of damage;
 * the burn stopping at [BURN_HITS] and its stacks ([BURN_STACK], [BURN_MAX_STACKS]); the
 * wave's damage ([WAVE_MIN] to [WAVE_MAX]: the Wiki's ~45, a 100 twice and a 60 in Sam's clip), no prayer helping and its landing on the wave's own tick; the smaller explosion doing no
 * damage (the tsps notes' reading); the screened damage; the arena's floor, his room's as Bork's is round its middle;
 * the heaps blocking the way until they fall. In phase two (19b): every damage figure but the Wiki's 34s; where on the
 * edge a Corpse rises and the summon's projectile there; the fires' damage, how often they hurt, and where the new
 * ones go (next to burning ones, as Sam's clips show the field filling); his shot's target (a random player) and its
 * flight; a melee swing only at a player beside him, in place of a shot; the blast after every second Corpse absorbed
 * (the Wiki: "within a short period of time", unclarified), with the wave's damage. Not built: the "shield" the Wiki
 * says the first explosion takes off, the blast on everyone when all hide too soon, the AoE's shadow, the blockrange
 * the fallen heap's tiles get, the eight corpse piles catching fire, a Corpse doused by water spells, fire spells
 * raising more, his death's graphic, and him and the Corpses lingering six ticks after he dies.
 */
object Xarth {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "xarth"

    /** `tfa_xarth`. */
    const val BOSS = 16684

    /** `tfa_xarth_rubble_npc` and `tfa_xarth_rubble_npc_destroyed`: a heap, and a heap falling. */
    const val HEAP = 16685
    const val HEAP_DOWN = 16686

    /** xarth_attack_magic_01, and luc2_undead_demon_fireball_proj_big, the fireball's flight (tsps: 4034). */
    const val ANIM_MAGIC = 14984
    const val SPOT_FIREBALL = 4034

    /** xarth_attack_aoe_01 with vfx_xarth_attack_aoe_01; vfx_xarth_aoe_stunanim_01 on the bound; its projectile and landing. */
    const val ANIM_AOE = 14992
    const val SPOT_AOE = 4376
    const val SPOT_BIND = 4377
    const val SPOT_AOE_PROJECTILE = 4378
    const val SPOT_AOE_LANDS = 4379

    /** xarth_attack_flame_blast_01 and vfx_xarth_attack_flame_blast_01. */
    const val ANIM_FLAME_BLAST = 14989
    const val SPOT_FLAME_BLAST = 4380

    /** vfx_vmq4_ennius_fire_explosion, the wave on a tile, and its _smaller, where a heap half shelters it (tsps). */
    const val SPOT_WAVE = 3310
    const val SPOT_WAVE_SMALL = 4030

    /** loc_xarth_rubble_collapse_01 and vfx_xarth_rubble_collapse_01: a heap falling. */
    const val ANIM_COLLAPSE = 14997
    const val SPOT_COLLAPSE = 4382

    /** invisible_type8_blocking: each of his footprint's nine tiles (tsps). */
    const val LOC_BLOCKING = 26209

    /** invisible_type8_blocking_size2: a standing heap's 2 by 2. Guessed: the tsps notes say only that a fallen one is walkable. */
    const val LOC_HEAP_BLOCKING = 47348

    /** `tfa_xarth_corpse`: a Corpse he raises in phase two. */
    const val CORPSE = 16687

    /** tfa_xarth_arenafire: a fire on the arena's floor in phase two. */
    const val LOC_ARENA_FIRE = 62713

    /** xarth_attack_ranged_01, and its vfx on him, its projectile and its landing (gamevals). */
    const val ANIM_RANGED = 14985
    const val SPOT_RANGED = 4373
    const val SPOT_RANGED_PROJECTILE = 4374
    const val SPOT_RANGED_LANDS = 4375

    /** xarth_attack_melee_01. */
    const val ANIM_MELEE = 14983

    /** vfx_xarth_corpse_walk_fire_01: a Corpse's flames as it walks. */
    const val SPOT_CORPSE_FIRE = 4383

    /** xarth_corpse_absorbed_01 with vfx_xarth_corpse_absorbed_01, and vfx_xarth_absorb_corpse_fire_01 on him. */
    const val ANIM_CORPSE_ABSORBED = 15005
    const val SPOT_CORPSE_ABSORBED = 4386
    const val SPOT_ABSORB_FIRE = 4387

    const val SIZE = 3
    const val HEAP_SIZE = 2

    /** The heaps' south-west tiles from his (tsps: him at 4190,5534; the heaps at 4193,5530, 4196,5536 and 4193,5540). */
    enum class Heap(val dx: Int, val dz: Int, val side: Side) {
        SOUTH(3, -4, Side.SOUTH),
        EAST(6, 2, Side.EAST),
        NORTH(3, 6, Side.NORTH),
    }

    /** A way out of his footprint. */
    enum class Side(val dx: Int, val dz: Int) { SOUTH(0, -1), EAST(1, 0), NORTH(0, 1), WEST(-1, 0) }

    /** The arena's floor round his south-west tile, without its 4 corners: as Bork's round his room's middle. Guessed. */
    const val ARENA_WEST = -6
    const val ARENA_EAST = 8
    const val ARENA_SOUTH = -7
    const val ARENA_NORTH = 9

    /** Phase one: this many cycles, each this long (tsps), the first this many ticks in (guessed). */
    const val CYCLES = 3
    const val CYCLE = 31
    const val FIRST_CYCLE = 2

    /** Fireballs in a cycle, and the ticks between them (tsps). */
    const val MAGIC_ATTACKS = 5
    const val MAGIC_GAP = 4

    /** The AoE this many ticks into a cycle (guessed: four after the last fireball), the flame blast and the wave after it (tsps). */
    const val AOE_AT = MAGIC_ATTACKS * MAGIC_GAP
    const val BLAST_AFTER_AOE = 3
    const val WAVE_AFTER_BLAST = 3

    /** How long the AoE binds everyone: till the flame blast, when the Wiki has them run for cover. Guessed. */
    const val BIND_TICKS = BLAST_AFTER_AOE

    /**
     * A fireball through a protection prayer: never a miss, the chip Sam took through Protect from Missiles in his clip
     * 16-09-17 (1 to 6, twelve of them; the Review thread's dense pass, 2026-11-10).
     */
    const val PRAYED_MIN = 1
    const val PRAYED_MAX = 6

    /**
     * The AoE's projectile: down on its tile this many ticks after the AoE, hurting whoever is on it. Sam's clip has the
     * flames reach the party the second tick and hit him for 23. Guessed: the range round it, and no prayer helping.
     */
    const val AOE_FLIGHT = 2
    const val AOE_MIN = 15
    const val AOE_MAX = 30

    /** A heap's collapse, before its NPC goes (tsps). */
    const val COLLAPSE_TICKS = 3

    /**
     * The burn a fireball sets: a hit every [BURN_EVERY] ticks, [BURN_HITS] of them, each [BURN_MIN] to [BURN_MAX] and
     * [BURN_STACK] more for every stack past the first, up to [BURN_MAX_STACKS]. Sam's clip 16-09-17 (the Review
     * thread's dense pass, 2026-11-10) has one after the set-alight message: 7, 7, 7, 7, 7 and 8, exactly 4 ticks
     * apart, at least 6. Guessed: that it stops at 6, and the stacks' step and most.
     */
    const val BURN_EVERY = 4
    const val BURN_HITS = 6
    const val BURN_MIN = 7
    const val BURN_MAX = 8
    const val BURN_STACK = 3
    const val BURN_MAX_STACKS = 3

    /**
     * The wave's full force, and what a player screened by another takes (the Wiki's ~7). The Wiki says ~45; Sam's clip
     * 16-09-17 has a 100 on a player out in the open in each of two waves, and a 60 on Sam in the first (perhaps half
     * covered). Guessed: the range between, no prayer helping.
     */
    const val WAVE_MIN = 40
    const val WAVE_MAX = 100
    const val SCREENED_MIN = 5
    const val SCREENED_MAX = 9

    /** Phase two's first fires, and how far from his footprint they land: mostly [FIRE_OUT_MOSTLY] (tsps). */
    const val TWO_FIRES = 7
    const val FIRE_OUT_MIN = 1
    const val FIRE_OUT_MAX = 7
    val FIRE_OUT_MOSTLY = 2..5

    /** His shots before the cycles begin, the cycle's length and its first tick (tsps). */
    val TWO_SHOTS_AT = listOf(7, 11)
    const val TWO_CYCLE = 21
    const val TWO_CYCLE_FROM = 15

    /** Each cycle: shots from this tick of it, this many, this many ticks apart (tsps). */
    const val TWO_SHOT_FROM = 5
    const val TWO_SHOTS = 4
    const val TWO_SHOT_GAP = 4

    /** Each cycle: Corpses raised from its first tick, this many ticks apart, more for a party of five (tsps). */
    const val SUMMONS = 2
    const val SUMMONS_BIG = 3
    const val SUMMON_GAP = 2

    /** A Corpse rises this many ticks after he raises it (tsps). */
    const val CORPSE_AFTER = 3

    /** Each cycle on this tick, this many more fires next to burning ones (tsps; next to them, Sam's clips). */
    const val SPREAD_AT = 12
    const val SPREAD_FIRES = 3

    /** His phase-two attack bonuses and max hits (OSRS Wiki, Xarth: ranged +250, melee +150, both 34). */
    const val RANGED_BONUS = 250
    const val MELEE_BONUS = 150
    const val RANGED_MAX = 34
    const val MELEE_MAX = 34

    /** His melee and ranged strength bonuses (Wiki: +30 each), for how much a drain to Strength or Ranged takes off [MELEE_MAX] and [RANGED_MAX]. */
    const val STRENGTH_BONUS = 30

    /**
     * [max] less what a drain to [stat] takes off, the way [osrsps.game.combat.Combat] lowers a drained NPC's max hit:
     * the drop in the max hit his [STRENGTH_BONUS] gives at his level now against at full.
     */
    fun drainedMax(max: Int, full: Int, now: Int): Int =
        if (now >= full) max else (max - (CombatMath.npcMaxHit(full, STRENGTH_BONUS) - CombatMath.npcMaxHit(now, STRENGTH_BONUS))).coerceAtLeast(0)

    /** A fire under a player: this much, and set alight, at most once every [FIRE_EVERY] ticks. Guessed. */
    const val FIRE_MIN = 5
    const val FIRE_MAX = 10
    const val FIRE_EVERY = 4

    /** A Corpse absorbed heals him this many times what it has left, and is gone this many ticks later (tsps). */
    const val CORPSE_HEALS = 2
    const val ABSORBED_TICKS = 2

    /** His heal's hitsplat (tsps: hitsplat 6). */
    const val HITSPLAT_HEAL = 6

    /** How much of the wave reaches a tile: all of it, the smaller explosion, or none. */
    enum class Shelter { NONE, PARTLY, FULLY }

    /** The order the heaps fall in: south or north first ([southFirst]), then east, then the other (tsps). */
    fun fallOrder(southFirst: Boolean): List<Heap> =
        if (southFirst) listOf(Heap.SOUTH, Heap.EAST, Heap.NORTH) else listOf(Heap.NORTH, Heap.EAST, Heap.SOUTH)

    /**
     * Whether ([x], [z]) is in the wave's 90 degree wedge out of a [size] by [size] footprint at ([fromX], [fromZ]) towards
     * [side]: a row out it is the footprint plus a tile each side, and a tile wider each side every row after (tsps).
     */
    fun inWedge(fromX: Int, fromZ: Int, size: Int, side: Side, x: Int, z: Int): Boolean {
        val (out, across, start, end) = when (side) {
            Side.SOUTH -> Quad(fromZ - z, x, fromX, fromX + size - 1)
            Side.NORTH -> Quad(z - (fromZ + size - 1), x, fromX, fromX + size - 1)
            Side.WEST -> Quad(fromX - x, z, fromZ, fromZ + size - 1)
            Side.EAST -> Quad(x - (fromX + size - 1), z, fromZ, fromZ + size - 1)
        }
        return out >= 1 && across in start - out..end + out
    }

    private data class Quad(val out: Int, val across: Int, val start: Int, val end: Int)

    /** Whether ([x], [z]) is on the floor of the arena round his south-west tile ([bossX], [bossZ]): its corners aren't. */
    fun inArena(bossX: Int, bossZ: Int, x: Int, z: Int): Boolean {
        val dx = x - bossX
        val dz = z - bossZ
        if (dx !in ARENA_WEST..ARENA_EAST || dz !in ARENA_SOUTH..ARENA_NORTH) return false
        return !((dx == ARENA_WEST || dx == ARENA_EAST) && (dz == ARENA_SOUTH || dz == ARENA_NORTH))
    }

    /**
     * The tiles a wave covers, in the arena round his footprint at ([bossX], [bossZ]): the wedge towards [side], or all
     * round him when it is null. Never his own tiles.
     */
    fun waveTiles(bossX: Int, bossZ: Int, side: Side?): List<Tile> {
        val tiles = ArrayList<Tile>()
        for (x in bossX + ARENA_WEST..bossX + ARENA_EAST) {
            for (z in bossZ + ARENA_SOUTH..bossZ + ARENA_NORTH) {
                if (!inArena(bossX, bossZ, x, z)) continue
                if (x - bossX in 0 until SIZE && z - bossZ in 0 until SIZE) continue
                if (side == null || inWedge(bossX, bossZ, SIZE, side, x, z)) tiles += Tile(x, z)
            }
        }
        return tiles
    }

    /**
     * How a heap ([heapX], [heapZ], [HEAP_SIZE] square) shelters ([x], [z]) from a wave out of his footprint at
     * ([bossX], [bossZ]) (tsps): fully where it stands on the line of sight from his middle tile, partly where it stands
     * only on the one from his footprint tile nearest the tile. The heap's own tiles count as fully sheltered.
     */
    fun shelter(bossX: Int, bossZ: Int, heapX: Int, heapZ: Int, x: Int, z: Int): Shelter {
        val onHeap = { tx: Int, tz: Int -> tx - heapX in 0 until HEAP_SIZE && tz - heapZ in 0 until HEAP_SIZE }
        if (onHeap(x, z)) return Shelter.FULLY
        val middle = SIZE / 2
        if (blocked(bossX + middle, bossZ + middle, x, z, onHeap)) return Shelter.FULLY
        val nearX = x.coerceIn(bossX, bossX + SIZE - 1)
        val nearZ = z.coerceIn(bossZ, bossZ + SIZE - 1)
        return if (blocked(nearX, nearZ, x, z, onHeap)) Shelter.PARTLY else Shelter.NONE
    }

    /**
     * Whether the line from ([fromX], [fromZ]) to ([toX], [toZ]) enters a tile [blocks] says stands in the way, short of
     * the end tile: [LineOfSight.trace]'s line, a step at a time along the longer axis in 16.16 fixed point, with a
     * tile in the way blocking as a projectile-blocking loc does.
     */
    fun blocked(fromX: Int, fromZ: Int, toX: Int, toZ: Int, blocks: (Int, Int) -> Boolean): Boolean {
        val dx = toX - fromX
        val dz = toZ - fromZ
        if (dx == 0 && dz == 0) return false
        val inWay = { x: Int, z: Int -> !(x == toX && z == toZ) && blocks(x, z) }
        if (abs(dx) > abs(dz)) {
            val step = if (dx < 0) -1 else 1
            val slope = (dz shl 16) / abs(dx)
            var fixedZ = (fromZ shl 16) + HALF_TILE - (if (dz < 0) 1 else 0)
            var x = fromX
            while (x != toX) {
                x += step
                val z = fixedZ ushr 16
                if (inWay(x, z)) return true
                fixedZ += slope
                val nextZ = fixedZ ushr 16
                if (nextZ != z && inWay(x, nextZ)) return true
            }
        } else {
            val step = if (dz < 0) -1 else 1
            val slope = (dx shl 16) / abs(dz)
            var fixedX = (fromX shl 16) + HALF_TILE - (if (dx < 0) 1 else 0)
            var z = fromZ
            while (z != toZ) {
                z += step
                val x = fixedX ushr 16
                if (inWay(x, z)) return true
                fixedX += slope
                val nextX = fixedX ushr 16
                if (nextX != x && inWay(nextX, z)) return true
            }
        }
        return false
    }

    private const val HALF_TILE = 0x8000

    /** One burn tick's damage with [stacks] stacks: [BURN_MIN] to [BURN_MAX], [BURN_STACK] more for each stack past the first. */
    fun burnRange(stacks: Int): IntRange {
        val extra = BURN_STACK * (stacks.coerceIn(1, BURN_MAX_STACKS) - 1)
        return BURN_MIN + extra..BURN_MAX + extra
    }

    /** The fight, or null when the cache hasn't him or his heaps (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val type = types[BOSS] ?: return null
        val heapType = types[HEAP] ?: return null
        val downType = types[HEAP_DOWN]
        val corpseType = types[CORPSE]
        return EncounterDef(
            NAME, type,
            listOf(
                PhaseDef(
                    "one",
                    cycle = listOf(CycleStep(1) { phaseOne(downType) }),
                    onStart = {
                        footprint()
                        state.heaps.clear()
                        state.heapLocs.clear()
                        for (heap in Heap.entries) {
                            val npc = spawnAdd(heapType, boss.x + heap.dx, boss.z + heap.dz) ?: continue
                            state.heaps[heap] = WeakReference(npc)
                            state.heapLocs[heap] = placeLoc(LOC_HEAP_BLOCKING, npc.x, npc.z)
                        }
                        state.order = fallOrder(roll(0, 1) == 0)
                        state.waves = 0
                        log("Xarth at ${boss.x},${boss.z}; the heaps fall ${state.order.joinToString { it.name.lowercase() }}")
                    },
                    endsWhen = { state.waves >= CYCLES },
                ),
                PhaseDef(
                    "two",
                    cycle = listOf(CycleStep(1) { phaseTwo(corpseType) }),
                    onStart = { startPhaseTwo() },
                ),
            ),
            scriptedAttacks = true,
            status = {
                val standing = state.heaps.values.count { alive(it.get()) != null }
                val corpses = state.corpses.count { alive(it.get()) != null }
                "Xarth ${boss.hitpoints}/${boss.maxHitpoints} hp, $standing heaps standing, ${state.waves} waves, ${state.fires.size} fires, $corpses corpses"
            },
        )
    }

    /** What a fight keeps between ticks, by boss. Holds its NPCs and players only weakly. */
    private class State {
        val heaps = LinkedHashMap<Heap, WeakReference<Npc>>()
        val heapLocs = LinkedHashMap<Heap, ZoneLoc>()

        /** The order the heaps fall in, this fight. */
        var order: List<Heap> = emptyList()

        /** Waves so far: phase one ends with the [CYCLES]th. */
        var waves = 0

        /** Each player's burn: its stacks, and the tick it was last set or set again. */
        val burns = WeakHashMap<Player, Burn>()

        /** Phase two's fires, by tile. */
        val fires = LinkedHashMap<Tile, ZoneLoc>()

        /** The tick each player may next be hurt by a fire under them. */
        val nextFireHurt = WeakHashMap<Player, Long>()

        /** The Corpses walking to him, in the order they rose. */
        val corpses = ArrayList<WeakReference<Npc>>()

        /** Corpses absorbed so far: every second sets off the blast. */
        var absorbed = 0

        /** Whether his footprint's blocking tiles are down. */
        var footprint = false
    }

    private class Burn(var stacks: Int, var since: Long) {
        /** The tick it last hurt, so each of its hits lands once however it's reached. */
        var lastHit = -1L
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    private fun EncounterContext.alive(npc: Npc?): Npc? = npc?.takeIf { !it.dead && world.npcs[it.index] === it }

    private fun EncounterContext.text(line: Line): String = world.dialogueText.get(line)

    /** One tick of phase one: where in its cycle it is says what he does. */
    private fun EncounterContext.phaseOne(downType: NpcType?) {
        val since = phaseTick - FIRST_CYCLE
        if (since < 0) return
        val cycle = (since / CYCLE).toInt()
        if (cycle >= CYCLES) return
        val at = (since % CYCLE).toInt()
        when {
            at < AOE_AT && at % MAGIC_GAP == 0 -> nearest()?.let { fireball(it) }
            at == AOE_AT -> aoe()
            at == AOE_AT + BLAST_AFTER_AOE -> flameBlast()
            at == AOE_AT + BLAST_AFTER_AOE + WAVE_AFTER_BLAST -> wave(cycle, downType)
        }
    }

    /** The player nearest his footprint, the lowest index on a tie. */
    private fun EncounterContext.nearest(): Player? =
        players.minByOrNull { LineOfSight.distance(boss.x, boss.z, SIZE, it.x, it.z) }

    /**
     * A fireball at [player]: rolled now with his Magic against theirs, landing after a spell's flight (Sam's clip:
     * launched the tick after the wind-up, landing 2 after that from 6 or 7 tiles, as here). Through a protection prayer
     * it never misses and does [PRAYED_MIN] to [PRAYED_MAX], setting no one alight; without one, one that hits sets them
     * alight ([burn]).
     */
    private fun EncounterContext.fireball(player: Player) {
        boss.face(player)
        boss.effects.anim(ANIM_MAGIC)
        val distance = LineOfSight.distance(boss.x, boss.z, SIZE, player.x, player.z).coerceAtLeast(1)
        val delay = HitDelay.MAGIC.ticks(distance)
        world.zones.projectile(
            Projectile(
                id = SPOT_FIREBALL, fromX = boss.x + SIZE / 2, fromZ = boss.z + SIZE / 2, level = boss.level, toX = player.x, toZ = player.z,
                // Launched the tick after the wind-up, landing with the hit (Sam's clip); at once from right beside him,
                // where the hit lands a tick on.
                startHeight = 100, endHeight = 30, startTime = if (delay > 1) CYCLES_PER_TICK else 0, endTime = delay * CYCLES_PER_TICK, angle = 16, progress = 64,
                sourceIndex = boss.index + 1, targetIndex = -(player.index + 1),
            ),
        )
        val roll = world.combat.npcAttack(boss, player, AttackType.MAGIC) ?: return
        val hit = CombatMath.rollHits(world.random, roll.attackRoll, roll.defenceRoll)
        // Sam's clip shows the chip through Protect from Missiles; the Wiki calls the fireball magic, so Protect from
        // Magic counts too. Guessed.
        val prayed = world.prayers.protects(player, AttackType.MAGIC) || world.prayers.protects(player, AttackType.RANGED)
        val rolled = if (prayed) roll(PRAYED_MIN, PRAYED_MAX) else if (hit) CombatMath.rollDamage(world.random, roll.maxHit) else 0
        val damage = if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(rolled) else rolled
        log("fireball at ${player.username}" + if (prayed) " for $damage, prayed against" else if (hit) " for $damage" else ", missing")
        after(delay) {
            if (players.none { it === player }) return@after
            when {
                prayed -> world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                !hit -> {
                    player.effects.spotanim(0, MeleeCombat.SPLASH, MeleeCombat.IMPACT_HEIGHT, 0)
                    // A splash still counts as his attack: auto-retaliate turns them on him, as for any NPC's magic.
                    world.meleeCombat.attackedWithoutHit(player, boss, world.tick)
                }
                else -> {
                    val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                    log("fireball hits ${player.username} for $dealt")
                    if (!player.dead) burn(player)
                }
            }
            if (!player.dead) player.attackedTick = world.tick
        }
    }

    /** [player] set alight, or a stack hotter if they burn already: the burn starts again from now. */
    private fun EncounterContext.burn(player: Player) {
        // By the world's tick: a burn set in phase one runs on into phase two.
        val running = state.burns[player]
        // The running burn's hit due this very tick still lands: set again on that tick, its callback would skip it,
        // and a fire under the player every [FIRE_EVERY] ticks would stop the burn ever hurting.
        if (running != null) {
            val since = world.tick - running.since
            if (since in 1..BURN_EVERY.toLong() * BURN_HITS && since % BURN_EVERY == 0L && running.lastHit != world.tick) burnHit(player, running)
        }
        val burn = running?.takeIf { world.tick - it.since < BURN_EVERY * BURN_HITS }
        val stacks = minOf(BURN_MAX_STACKS, (burn?.stacks ?: 0) + 1)
        val now = Burn(stacks, world.tick)
        state.burns[player] = now
        player.message(text(XarthLines.SET_ALIGHT))
        log("${player.username} burns, $stacks stack${if (stacks == 1) "" else "s"}")
        for (i in 1..BURN_HITS) {
            after(BURN_EVERY * i) {
                // A later fireball has set it again: that burn's ticks run instead.
                if (state.burns[player] !== now || now.lastHit == world.tick || players.none { it === player }) return@after
                burnHit(player, now)
            }
        }
    }

    /** One tick of [burn] on [player]. */
    private fun EncounterContext.burnHit(player: Player, burn: Burn) {
        if (player.dead) return
        burn.lastHit = world.tick
        val range = burnRange(burn.stacks)
        val dealt = world.meleeCombat.hitPlayer(player, null, roll(range.first, range.last), world.tick)
        if (!player.dead) player.attackedTick = world.tick
        log("burn on ${player.username} for $dealt")
    }

    /**
     * His AoE: everyone bound where they stand, and a projectile down on each of their tiles, landing [AOE_FLIGHT]
     * ticks on for [AOE_MIN] to [AOE_MAX] on whoever is on it.
     */
    private fun EncounterContext.aoe() {
        boss.effects.anim(ANIM_AOE)
        boss.effects.spotanim(0, SPOT_AOE, 0)
        val flight = AOE_FLIGHT
        for (player in players) {
            world.statusEffects.stun(player, BIND_TICKS, spotanim = SPOT_BIND)
            world.zones.projectile(
                Projectile(
                    id = SPOT_AOE_PROJECTILE, fromX = boss.x + SIZE / 2, fromZ = boss.z + SIZE / 2, level = boss.level, toX = player.x, toZ = player.z,
                    startHeight = 120, endHeight = 0, startTime = 0, endTime = flight * CYCLES_PER_TICK, angle = 16, progress = 64,
                ),
            )
            val at = Tile(player.x, player.z)
            after(flight) {
                world.zones.mapAnim(at.x, at.z, boss.level, SPOT_AOE_LANDS)
                for (struck in players.filter { it.x == at.x && it.z == at.z }) {
                    // A Retribution from the one before may have killed him.
                    if (boss.dead) break
                    val dealt = world.meleeCombat.hitPlayer(struck, boss, roll(AOE_MIN, AOE_MAX), world.tick)
                    log("AoE hits ${struck.username} for $dealt")
                }
            }
        }
        log("AoE binds ${players.joinToString { it.username }.ifEmpty { "no one" }}")
    }

    private fun EncounterContext.flameBlast() {
        boss.effects.anim(ANIM_FLAME_BLAST)
        boss.effects.spotanim(0, SPOT_FLAME_BLAST, 0)
        log("flame blast")
    }

    /**
     * The wave of cycle [cycle] (from 0): out towards the heap that falls, all round in the last, sheltered behind that
     * heap; then the heap falls.
     */
    private fun EncounterContext.wave(cycle: Int, downType: NpcType?) {
        state.waves = cycle + 1
        val heap = state.order.getOrNull(cycle)
        val heapNpc = heap?.let { alive(state.heaps[it]?.get()) }
        val side = if (cycle >= CYCLES - 1) null else heap?.side
        val shelterOf = { x: Int, z: Int -> if (heapNpc == null) Shelter.NONE else shelter(boss.x, boss.z, heapNpc.x, heapNpc.z, x, z) }
        val tiles = waveTiles(boss.x, boss.z, side)
        for (tile in tiles) {
            val shelter = shelterOf(tile.x, tile.z)
            if (shelter == Shelter.FULLY) continue
            val out = Bork.distance(boss.x, boss.z, SIZE, tile.x, tile.z)
            world.zones.mapAnim(tile.x, tile.z, boss.level, if (shelter == Shelter.PARTLY) SPOT_WAVE_SMALL else SPOT_WAVE, delay = out)
        }
        log("wave ${cycle + 1} ${side?.name?.lowercase() ?: "all round"}, ${heap?.name?.lowercase() ?: "no"} heap falling")
        strike(tiles, shelterOf, "wave", setsAlight = false)
        if (boss.dead) return
        if (heap != null && heapNpc != null) fall(heap, heapNpc, downType)
    }

    /**
     * The wave's hit on everyone on [tiles] whom [shelterOf] leaves in the open: its full force, or [SCREENED_MIN] to
     * [SCREENED_MAX] for one with another player on the line from his middle (the Wiki's pillar). With [setsAlight],
     * those it hits are set alight too, as the blast's are (Wiki).
     */
    private fun EncounterContext.strike(tiles: List<Tile>, shelterOf: (Int, Int) -> Shelter, what: String, setsAlight: Boolean) {
        val struck = tiles.toSet()
        val inWave = players.filter { Tile(it.x, it.z) in struck && shelterOf(it.x, it.z) == Shelter.NONE }
        val occupied = inWave.map { Tile(it.x, it.z) }
        for (player in inWave) {
            if (boss.dead) return
            val middle = SIZE / 2
            // Another player on the line from his middle stands in front, taking the full force for them (Wiki).
            val screened = occupied.any { it != Tile(player.x, player.z) } &&
                blocked(boss.x + middle, boss.z + middle, player.x, player.z) { x, z -> occupied.any { it.x == x && it.z == z && !(x == player.x && z == player.z) } }
            val damage = if (screened) roll(SCREENED_MIN, SCREENED_MAX) else roll(WAVE_MIN, WAVE_MAX)
            val dealt = world.meleeCombat.hitPlayer(player, null, damage, world.tick)
            if (!player.dead) player.attackedTick = world.tick
            log("$what hits ${player.username} for $dealt" + if (screened) ", screened" else "")
            if (setsAlight && !player.dead) burn(player)
        }
    }

    /** [heap] falls: its NPC goes, its collapse plays where it stood for [COLLAPSE_TICKS] ticks, and its tiles open. */
    private fun EncounterContext.fall(heap: Heap, npc: Npc, downType: NpcType?) {
        val x = npc.x
        val z = npc.z
        world.removeNpc(npc)
        state.heaps.remove(heap)
        state.heapLocs.remove(heap)?.let { clearLoc(it) }
        val down = downType?.let { spawnAdd(it, x, z) }
        if (down != null) {
            down.effects.anim(ANIM_COLLAPSE)
            down.effects.spotanim(0, SPOT_COLLAPSE, 0)
            after(COLLAPSE_TICKS) { if (world.npcs[down.index] === down) world.removeNpc(down) }
        }
        log("the ${heap.name.lowercase()} heap falls")
    }

    /** He never moves: his footprint blocks the way, as live's nine blocking tiles do. */
    private fun EncounterContext.footprint() {
        if (state.footprint) return
        for (dx in 0 until SIZE) for (dz in 0 until SIZE) placeLoc(LOC_BLOCKING, boss.x + dx, boss.z + dz)
        state.footprint = true
    }

    /** Phase two begins: the first fires, and nothing absorbed yet; his footprint too, for a fight `::raid` starts here. */
    private fun EncounterContext.startPhaseTwo() {
        footprint()
        state.absorbed = 0
        val placed = ArrayList<Tile>()
        repeat(TWO_FIRES) { firstFireTile()?.let { placeFire(it); placed += it } }
        log("phase two: ${placed.size} fires, on ${placed.joinToString(" ") { "${it.x},${it.z}" }}")
    }

    /** One tick of phase two: where in its cycle it is says what he does; the Corpses walk and the fires burn. */
    private fun EncounterContext.phaseTwo(corpseType: NpcType?) {
        val t = phaseTick
        if (t in TWO_SHOTS_AT.map { it.toLong() }) attack()
        if (t >= TWO_CYCLE_FROM) {
            val at = ((t - TWO_CYCLE_FROM) % TWO_CYCLE).toInt()
            val summons = if (players.size >= 5) SUMMONS_BIG else SUMMONS
            if (at % SUMMON_GAP == 0 && at / SUMMON_GAP < summons) summon(corpseType)
            val shot = at - TWO_SHOT_FROM
            if (shot >= 0 && shot % TWO_SHOT_GAP == 0 && shot / TWO_SHOT_GAP < TWO_SHOTS) attack()
            if (at == SPREAD_AT) spread()
        }
        if (boss.dead) return
        corpses()
        fires()
    }

    /** Whether ([x], [z]) is a tile a fire can burn on: the arena's floor, not under him. */
    private fun EncounterContext.fireCan(x: Int, z: Int): Boolean =
        inArena(boss.x, boss.z, x, z) && !(x - boss.x in 0 until SIZE && z - boss.z in 0 until SIZE) && Tile(x, z) !in state.fires

    /**
     * Where one of phase two's first fires lands: a free tile [FIRE_OUT_MIN] to [FIRE_OUT_MAX] tiles off his footprint,
     * rolled again once outside [FIRE_OUT_MOSTLY] so most land there (tsps: "mostly two to five"). Guessed: the roll.
     */
    private fun EncounterContext.firstFireTile(): Tile? {
        var out = roll(FIRE_OUT_MIN, FIRE_OUT_MAX)
        if (out !in FIRE_OUT_MOSTLY) out = roll(FIRE_OUT_MIN, FIRE_OUT_MAX)
        val choices = waveTiles(boss.x, boss.z, null).filter { Bork.distance(boss.x, boss.z, SIZE, it.x, it.z) == out && fireCan(it.x, it.z) }
        return choices.getOrNull(if (choices.isEmpty()) 0 else roll(0, choices.size - 1))
    }

    private fun EncounterContext.placeFire(tile: Tile) {
        state.fires[tile] = placeLoc(LOC_ARENA_FIRE, tile.x, tile.z)
    }

    /** [SPREAD_FIRES] more fires, each on a free tile next to one burning (Wiki: "spread to adjacent tiles"). */
    private fun EncounterContext.spread() {
        val placed = ArrayList<Tile>()
        repeat(SPREAD_FIRES) {
            val next = state.fires.keys.flatMap { f -> (-1..1).flatMap { dx -> (-1..1).map { dz -> Tile(f.x + dx, f.z + dz) } } }
                .distinct().filter { fireCan(it.x, it.z) }
            if (next.isEmpty()) return@repeat
            val tile = next[roll(0, next.size - 1)]
            placeFire(tile)
            placed += tile
        }
        if (placed.isNotEmpty()) log("the fires spread to ${placed.joinToString(" ") { "${it.x},${it.z}" }}")
    }

    /** Whoever stands on a fire is hurt and set alight, at most once every [FIRE_EVERY] ticks. */
    private fun EncounterContext.fires() {
        for (player in players) {
            if (boss.dead) return
            if (Tile(player.x, player.z) !in state.fires) continue
            if (world.tick < (state.nextFireHurt[player] ?: 0L)) continue
            state.nextFireHurt[player] = world.tick + FIRE_EVERY
            val dealt = world.meleeCombat.hitPlayer(player, null, roll(FIRE_MIN, FIRE_MAX), world.tick)
            if (!player.dead) player.attackedTick = world.tick
            log("fire under ${player.username} for $dealt")
            if (!player.dead) burn(player)
        }
    }

    /**
     * His attack in phase two: a melee swing at a player beside him if there is one, else a shot at a random player
     * (guessed: the Wiki says he uses both, the tsps notes count only shots).
     */
    private fun EncounterContext.attack() {
        val beside = players.filter { Bork.beside(boss.x, boss.z, SIZE, it.x, it.z) }
        if (beside.isNotEmpty()) swing(beside[roll(0, beside.size - 1)]) else randomPlayer()?.let { shoot(it) }
    }

    /** What his roll at [player] with an attack of [type] and [bonus] lands, up to [max]: 0 on a miss. */
    private fun EncounterContext.rolled(player: Player, type: AttackType, level: Int, bonus: Int, max: Int): Int {
        val defence = world.combat.npcAttack(boss, player, type)?.defenceRoll ?: return 0
        val hit = CombatMath.rollHits(world.random, CombatMath.npcAttackRoll(level, bonus), defence)
        return if (hit) CombatMath.rollDamage(world.random, max) else 0
    }

    /**
     * A shot at [player], landing on impact (Wiki) after a bow's flight: Protect from Missiles blocks all of it (Wiki:
     * "fully blocked").
     */
    private fun EncounterContext.shoot(player: Player) {
        boss.face(player)
        boss.effects.anim(ANIM_RANGED)
        boss.effects.spotanim(0, SPOT_RANGED, 0)
        val distance = LineOfSight.distance(boss.x, boss.z, SIZE, player.x, player.z).coerceAtLeast(1)
        // Guessed: a bow's flight.
        val delay = HitDelay.BOW.ticks(distance)
        world.zones.projectile(
            Projectile(
                id = SPOT_RANGED_PROJECTILE, fromX = boss.x + SIZE / 2, fromZ = boss.z + SIZE / 2, level = boss.level, toX = player.x, toZ = player.z,
                startHeight = 100, endHeight = 30, startTime = 0, endTime = delay * CYCLES_PER_TICK, angle = 16, progress = 64,
                sourceIndex = boss.index + 1, targetIndex = -(player.index + 1),
            ),
        )
        log("shoots at ${player.username}")
        after(delay) {
            if (players.none { it === player }) return@after
            player.effects.spotanim(0, SPOT_RANGED_LANDS, MeleeCombat.IMPACT_HEIGHT, 0)
            // On impact, so the prayer counts as the shot lands (Wiki).
            val prayed = world.prayers.protects(player, AttackType.RANGED)
            val rolled = if (prayed) 0 else rolled(player, AttackType.RANGED, boss.level(NpcStat.RANGED), RANGED_BONUS, drainedMax(RANGED_MAX, boss.type.stats.ranged, boss.level(NpcStat.RANGED)))
            val damage = if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(rolled) else rolled
            val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
            if (!player.dead) player.attackedTick = world.tick
            log("shot hits ${player.username} for $dealt" + if (prayed) ", prayed against" else "")
        }
    }

    /** A slash at [player] beside him, landing at once; Protect from Melee blocks it. Guessed: that the prayer blocks all of it. */
    private fun EncounterContext.swing(player: Player) {
        boss.face(player)
        boss.effects.anim(ANIM_MELEE)
        val prayed = world.prayers.protects(player, AttackType.SLASH)
        val rolled = if (prayed) 0 else rolled(player, AttackType.SLASH, boss.level(NpcStat.ATTACK), MELEE_BONUS, drainedMax(MELEE_MAX, boss.type.stats.strength, boss.level(NpcStat.STRENGTH)))
        val damage = if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(rolled) else rolled
        val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
        if (!player.dead) player.attackedTick = world.tick
        log("swings at ${player.username} for $dealt" + if (prayed) ", prayed against" else "")
    }

    /** The arena's edge: its floor tiles beside one that isn't floor. */
    private fun EncounterContext.edge(): List<Tile> =
        waveTiles(boss.x, boss.z, null).filter { t -> Side.entries.any { !inArena(boss.x, boss.z, t.x + it.dx, t.z + it.dz) } }

    /**
     * He raises a Corpse (the Wiki: shown by his magic attack animation): it rises at an edge tile [CORPSE_AFTER] ticks
     * on (tsps). Guessed: which edge tile, and his fireball's flight there.
     */
    private fun EncounterContext.summon(corpseType: NpcType?) {
        val edge = edge()
        if (edge.isEmpty()) return
        val at = edge[roll(0, edge.size - 1)]
        boss.effects.anim(ANIM_MAGIC)
        world.zones.projectile(
            Projectile(
                id = SPOT_FIREBALL, fromX = boss.x + SIZE / 2, fromZ = boss.z + SIZE / 2, level = boss.level, toX = at.x, toZ = at.z,
                startHeight = 100, endHeight = 0, startTime = 0, endTime = CORPSE_AFTER * CYCLES_PER_TICK, angle = 16, progress = 64,
            ),
        )
        log("raises a corpse at ${at.x},${at.z}")
        after(CORPSE_AFTER) {
            val corpse = corpseType?.let { spawnAdd(it, at.x, at.z) } ?: return@after
            // It walks only as the fight walks it, fought or not, and never fights back.
            corpse.scriptedAttacks = true
            corpse.effects.spotanim(0, SPOT_CORPSE_FIRE, 0)
            state.corpses += WeakReference(corpse)
            log("a corpse rises at ${at.x},${at.z}")
        }
    }

    /**
     * The tile beside him a Corpse on ([x], [z]) heads for: along his nearest side, never off a corner, as it must stand
     * beside him to be absorbed. Guessed: the way it goes.
     */
    fun besideTile(bossX: Int, bossZ: Int, x: Int, z: Int): Tile {
        var tx = x.coerceIn(bossX - 1, bossX + SIZE)
        var tz = z.coerceIn(bossZ - 1, bossZ + SIZE)
        val cornerX = tx == bossX - 1 || tx == bossX + SIZE
        val cornerZ = tz == bossZ - 1 || tz == bossZ + SIZE
        if (cornerX && cornerZ) {
            // Off a corner: to the side it is further out along.
            if (abs(x - tx) >= abs(z - tz)) tz = tz.coerceIn(bossZ, bossZ + SIZE - 1) else tx = tx.coerceIn(bossX, bossX + SIZE - 1)
        }
        return Tile(tx, tz)
    }

    /** Each Corpse: gone if killed, absorbed if beside him and free to move, else a step towards him (tsps: a tile a tick). */
    private fun EncounterContext.corpses() {
        val walking = state.corpses.mapNotNull { it.get() }.sortedBy { it.index }
        state.corpses.removeAll { ref -> ref.get().let { it == null || alive(it) == null } }
        for (corpse in walking) {
            if (boss.dead) return
            if (alive(corpse) == null) continue
            if (Bork.beside(boss.x, boss.z, SIZE, corpse.x, corpse.z)) {
                // Beside him it is absorbed the tick after it arrives, unless it is frozen there: the Wiki has players
                // bind them a tile out "so that they will be bound next to him".
                if (corpse.freezeTicks == 0) absorb(corpse)
                continue
            }
            val to = besideTile(boss.x, boss.z, corpse.x, corpse.z)
            world.npcs.stepToward(corpse, to.x, to.z)
        }
    }

    /**
     * [corpse] is absorbed: it goes [ABSORBED_TICKS] ticks on, he heals [CORPSE_HEALS] times what it had left (tsps),
     * and every second one absorbed sets off the blast (Wiki, Corpse (Xarth)).
     */
    private fun EncounterContext.absorb(corpse: Npc) {
        state.corpses.removeAll { it.get() === corpse }
        val left = corpse.hitpoints
        corpse.effects.anim(ANIM_CORPSE_ABSORBED)
        corpse.effects.spotanim(0, SPOT_CORPSE_ABSORBED, 0)
        boss.effects.spotanim(0, SPOT_ABSORB_FIRE, 0)
        after(ABSORBED_TICKS) { if (world.npcs[corpse.index] === corpse) world.removeNpc(corpse) }
        heal(left * CORPSE_HEALS)
        state.absorbed++
        log("absorbs a corpse with $left left, ${state.absorbed} absorbed")
        if (state.absorbed % 2 == 0) blast()
    }

    /** He heals by [amount], the heal's hitsplat and his health bar showing it. */
    private fun EncounterContext.heal(amount: Int) {
        if (amount <= 0) return
        val before = boss.hitpoints
        boss.hitpoints = minOf(boss.maxHitpoints, before + amount)
        val healed = boss.hitpoints - before
        boss.effects.hit(-1, HITSPLAT_HEAL, HITSPLAT_HEAL, healed)
        // Bars go out only as they are queued, as a hit queues one.
        boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        log("heals $healed")
    }

    /**
     * The blast after a second Corpse absorbed (Wiki): the flame blast, then [WAVE_AFTER_BLAST] ticks on a wave all
     * round him, sheltered by nothing but a player in front, setting those it hits alight. Guessed: its timing, as
     * phase one's flame blast and wave.
     */
    private fun EncounterContext.blast() {
        flameBlast()
        after(WAVE_AFTER_BLAST) {
            val tiles = waveTiles(boss.x, boss.z, null)
            for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_WAVE, delay = Bork.distance(boss.x, boss.z, SIZE, tile.x, tile.z))
            log("the blast all round")
            strike(tiles, { _, _ -> Shelter.NONE }, "blast", setsAlight = true)
        }
    }

    private const val CYCLES_PER_TICK = 30
}
