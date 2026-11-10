package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
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
 * STAND-IN until step 19b: phase two (the arena fires, the burning corpse piles, his ranged and melee, the Corpses that
 * walk to him and heal him) is a phase in which he stands and does nothing, so a party can still kill him.
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
 * the heaps blocking the way until they fall. Not built: the "shield" the Wiki says the first explosion takes off,
 * the blast on everyone when all hide too soon, the AoE's shadow, and the blockrange the fallen heap's tiles get.
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
        return EncounterDef(
            NAME, type,
            listOf(
                PhaseDef(
                    "one",
                    cycle = listOf(CycleStep(1) { phaseOne(downType) }),
                    onStart = {
                        // He never moves: his footprint blocks the way, as live's nine blocking tiles do.
                        for (dx in 0 until SIZE) for (dz in 0 until SIZE) placeLoc(LOC_BLOCKING, boss.x + dx, boss.z + dz)
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
                // STAND-IN until step 19b: he stands, and the party can finish him.
                PhaseDef("two", onStart = { log("phase two (stand-in)") }),
            ),
            scriptedAttacks = true,
            status = {
                val standing = state.heaps.values.count { alive(it.get()) != null }
                "Xarth ${boss.hitpoints}/${boss.maxHitpoints} hp, $standing heaps standing, ${state.waves} waves"
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

        /** Each player's burn: its stacks, and the phase tick it was last set or set again. */
        val burns = WeakHashMap<Player, Burn>()
    }

    private class Burn(var stacks: Int, var since: Long)

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
        val burn = state.burns[player]?.takeIf { phaseTick - it.since < BURN_EVERY * BURN_HITS }
        val stacks = minOf(BURN_MAX_STACKS, (burn?.stacks ?: 0) + 1)
        val now = Burn(stacks, phaseTick)
        state.burns[player] = now
        player.message(text(XarthLines.SET_ALIGHT))
        log("${player.username} burns, $stacks stack${if (stacks == 1) "" else "s"}")
        for (i in 1..BURN_HITS) {
            after(BURN_EVERY * i) {
                // A later fireball has set it again: that burn's ticks run instead.
                if (state.burns[player] !== now || players.none { it === player }) return@after
                val range = burnRange(now.stacks)
                val dealt = world.meleeCombat.hitPlayer(player, null, roll(range.first, range.last), world.tick)
                if (!player.dead) player.attackedTick = world.tick
                log("burn on ${player.username} for $dealt")
            }
        }
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
            log("wave hits ${player.username} for $dealt" + if (screened) ", screened" else "")
        }
        if (heap != null && heapNpc != null) fall(heap, heapNpc, downType)
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

    private const val CYCLES_PER_TICK = 30
}
