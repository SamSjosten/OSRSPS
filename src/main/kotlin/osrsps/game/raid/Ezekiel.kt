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
 * Ezekiel (raid steps 17a and 17b), one of the second row's two bosses, in his own room: a 3 by 3 justiciar who keeps
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
 * animations and `ezekiel_*` spotanims.
 *
 * Guessed, until a recording shows the fight (each also in the README): where he stands as the fight begins
 * ([ArchiveRaids.EZEKIEL_X]), the first attack [ATTACK_SPEED] ticks in, the safe spot being his whole side opposite
 * the tank (its corner for a tank at a corner), the lightning's damage ([MAGIC_MAX]) and its landing a tick after the
 * cast, the cross's reach ([CROSS_REACH]), its width (his), its timings and damage ([CROSS_MIN] to [CROSS_MAX]), the melee's
 * max hit being the Wiki's 77; for the swords, the arena ([ARENA_RADIUS] round his spawn), their lanes (his width, at
 * random, from a random end), the count picked at random, one volley out at a time, all of a volley flying together and
 * at once along the whole lane (live's fly across), their timer, damage, charge, the stun's and the heal's size, and
 * the sword's own type (16653 rather than the `_noop` one) and air weakness. The enrage (17c) is not in yet.
 */
object Ezekiel {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "ezekiel"

    /** `tfa_ezekiel`. */
    const val BOSS = 16652

    /** `tfa_ezekiel_sword`: a flying sword, waiting at its lane's end to fly. */
    const val SWORD = 16653

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

    /** ezekiel_magic_attack_vfx01 and its impact; ezekiel_special_crack_vfx01; the star explosion's marker and burst. */
    const val SPOT_MAGIC = 4167
    const val SPOT_MAGIC_IMPACT = 4168
    const val SPOT_CRACK = 4169
    const val SPOT_CROSS_MARKER = 4171
    const val SPOT_CROSS = 4172

    /** ezekiel_heal_vfx01, his heal's glow. */
    const val SPOT_HEAL = 4173

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

    /** The swords fly across this many tiles each way of his spawn's middle, along both axes. Guessed. */
    const val ARENA_RADIUS = 10

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

    /** The fight, or null when the cache hasn't Ezekiel (a test world's). Without the sword's type, no swords fly. */
    fun def(types: NpcTypes): EncounterDef? {
        val type = types[BOSS] ?: return null
        val sword = types[SWORD]
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
                    onStart = {
                        state.swings = 0
                        // He walks after his tank anywhere in his room, never giving up for being far from his spawn.
                        boss.unleashed = true
                        tank()
                    },
                ),
            ),
            scriptedAttacks = true,
        )
    }

    /** What a fight keeps between ticks, by boss: the context keeps none. Holds no NPC, and its tank only weakly. */
    private class State {
        /** Swings since the fight began: every second one brings a cross. */
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

    /** His attack, as it's due: the cleave with his tank beside him, else lightning on everyone. */
    private fun EncounterContext.attack(tank: Player, sword: NpcType?) {
        if (!beside(tank.x, tank.z)) {
            lightning()
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
        state.swings += 1
        // Not when his swing left no one in the room.
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
    private fun EncounterContext.perfectDodge(tiles: Set<Tile>, ticks: Int, what: String) {
        var onIt: List<Player> = emptyList()
        if (ticks > 1) after(ticks - 1) { onIt = players.filter { Tile(it.x, it.z) in tiles } } else onIt = players.filter { Tile(it.x, it.z) in tiles }
        after(ticks) {
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

    /** How much of its [CHARGE_HITPOINTS] [sword] has taken, from 0 to 1. */
    private fun charge(sword: Npc): Double = (CHARGE_HITPOINTS - sword.hitpoints.coerceIn(0, CHARGE_HITPOINTS)).toDouble() / CHARGE_HITPOINTS

    /** A sword's hit on him: [SWORD_HIT] uncharged, [SWORD_HIT_FULL] at full charge, between in step with its charge. */
    fun swordDamage(charge: Double): Int = SWORD_HIT + ((SWORD_HIT_FULL - SWORD_HIT) * charge.coerceIn(0.0, 1.0)).toInt()

    /** A sword's hit on a player in its lane: [LANE_MIN] to [LANE_MAX] uncharged, up to twice that at full charge. */
    fun laneDamage(charge: Double): IntRange {
        val times = 1.0 + charge.coerceIn(0.0, 1.0)
        return (LANE_MIN * times).toInt()..(LANE_MAX * times).toInt()
    }

    /** A volley of swords at the arena's edge, each at the end of a lane across it, flying [SWORD_LAUNCH] ticks on. */
    private fun EncounterContext.volley(type: NpcType) {
        val size = boss.type.size
        val cx = boss.spawn.x + size / 2
        val cz = boss.spawn.z + size / 2
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
                val centre = if (alongX) cz else cx
                offset = roll(centre - ARENA_RADIUS, centre + ARENA_RADIUS - size + 1)
            } while (!taken.add(alongX to offset) && ++tries < 20)
            val (min, max) = if (alongX) cx - ARENA_RADIUS to cx + ARENA_RADIUS else cz - ARENA_RADIUS to cz + ARENA_RADIUS
            // From either end, its middle at the edge.
            val end = if (roll(0, 1) == 0) min else max
            val (x, z) = if (alongX) end to offset + size / 2 else offset + size / 2 to end
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
        for (sword in swords) perfectDodge(sword.lane, SWORD_LAUNCH, "a sword")
        after(SWORD_LAUNCH) { fly(swords) }
    }

    /** The volley flies: each hits whoever is in its lane, and him if he's lined up with it; none hitting him heals him. */
    private fun EncounterContext.fly(swords: List<Sword>) {
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
            after(1) { if (stunned()) boss.effects.anim(ANIM_WEAKEN) }
        }
        val until = state.stunnedUntil
        after(STUN_TICKS) { if (state.stunnedUntil == until) boss.effects.anim(ANIM_WEAKEN_END) }
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

    /** Whether the fight is over, the boss dead or gone: nothing lands after. */
    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss
}
