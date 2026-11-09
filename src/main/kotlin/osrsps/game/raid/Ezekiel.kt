package osrsps.game.raid

import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.combat.SpecialAttacks
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.map.Tile

/**
 * Ezekiel (raid step 17a), one of the second row's two bosses, in his own room: a 3 by 3 justiciar who keeps to his
 * tank, the first player in, and walks after them.
 *
 * - Every [ATTACK_SPEED] ticks, with his tank beside him, a melee swing that cleaves almost all the way round him: it
 *   hits everyone beside him but those on his side opposite the tank ([behind]), the safe spot.
 * - With his tank out of his reach as an attack is due, lightning on every player in the room instead ([MAGIC_MAX]).
 * - After every second swing, a cross of lightning ([cross]) on each player, landing [CROSS_DELAY] ticks later; one
 *   still on it as it was about to land who steps off on the landing tick, a perfect dodge, gets [DODGE_ENERGY] of
 *   special attack energy back.
 *
 * From the OSRS Wiki (Ezekiel, read 2026-11-09): his stats and bonuses, 3 by 3, the 5-tick attack speed, max hit 77,
 * magic and melee, the 25% earth weakness, the poison and venom immunity, Defence drained to 120 at most, his
 * hitpoints by party size, and the enrage at 35% (step 17c). From guides (osrstoolkit, osrsbestinslot; read
 * 2026-11-09): the cleave round him with the safe spot directly behind, opposite the tank; the tank the first player in;
 * the lightning on everyone when he can't reach the tank; the cross-shaped burst after every two melee attacks, dodged
 * by a diagonal step; a perfect dodge (in its path, stepping out on the landing tick) giving 3% special attack energy.
 * From the cache: `tfa_ezekiel` (16652) and his `npc_ezekiel_*` animations and `ezekiel_*` spotanims.
 *
 * Guessed, until a recording shows the fight (each also in the README): where he stands as the fight begins
 * ([ArchiveRaids.EZEKIEL_X]), the first attack [ATTACK_SPEED] ticks in, the safe spot being his whole side opposite
 * the tank (its corner for a tank at a corner), the lightning's damage ([MAGIC_MAX]) and its landing a tick after the
 * cast, the cross's arms ([CROSS_ARM]), its delay and damage ([CROSS_MIN] to [CROSS_MAX]), and the melee's max hit
 * being the Wiki's 77. The flying swords (17b) and the enrage (17c) are not in yet.
 */
object Ezekiel {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "ezekiel"

    /** `tfa_ezekiel`. */
    const val BOSS = 16652

    /** npc_ezekiel_*: his swing, his lightning cast and his cross burst (special). */
    const val ANIM_MELEE = 14685
    const val ANIM_MAGIC = 14686
    const val ANIM_SPECIAL = 14687

    /** ezekiel_magic_attack_vfx01 and its impact; ezekiel_special_crack_vfx01; the star explosion's marker and burst. */
    const val SPOT_MAGIC = 4167
    const val SPOT_MAGIC_IMPACT = 4168
    const val SPOT_CRACK = 4169
    const val SPOT_CROSS_MARKER = 4171
    const val SPOT_CROSS = 4172

    /** The Wiki's attack speed. */
    const val ATTACK_SPEED = 5

    /** His lightning on everyone when he can't reach his tank: up to this, Protect from Magic blocking it. Guessed. */
    const val MAGIC_MAX = 30

    /** The cross: this many tiles each way from its middle, landing this many ticks after it is shown. Guessed. */
    const val CROSS_ARM = 2
    const val CROSS_DELAY = 3

    /** The cross's damage on whoever is on it as it lands. Guessed. */
    const val CROSS_MIN = 20
    const val CROSS_MAX = 40

    /** A perfect dodge's special attack energy: 3% (a guide's), in tenths of a percent. */
    const val DODGE_ENERGY = 30

    /** The fight, or null when the cache hasn't Ezekiel (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val type = types[BOSS] ?: return null
        return EncounterDef(
            NAME, type,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(
                        CycleStep(1) {
                            val tank = tank() ?: return@CycleStep
                            chase(tank)
                            if (phaseTick > 0 && phaseTick % ATTACK_SPEED == 0L) attack(tank)
                        },
                    ),
                    onStart = {
                        swings = 0
                        // He walks after his tank anywhere in his room, never giving up for being far from his spawn.
                        boss.unleashed = true
                        tank()
                    },
                ),
            ),
            scriptedAttacks = true,
        )
    }

    /** Swings since the fight began, by boss: every second one brings a cross. Held here, as the context keeps no state. */
    private val swingCounts = java.util.WeakHashMap<osrsps.game.npc.Npc, Int>()

    private var EncounterContext.swings: Int
        get() = swingCounts[boss] ?: 0
        set(value) {
            swingCounts[boss] = value
        }

    /**
     * His tank by boss, kept here as his combat target can go (they died, or left). Held weakly, so a boss whose fight
     * is over doesn't keep a logged out player.
     */
    private val tanks = java.util.WeakHashMap<osrsps.game.npc.Npc, java.lang.ref.WeakReference<Player>>()

    /**
     * His tank: the player he fights while they're in the fight, else the one he was set on (the first player in), else
     * the first in the fight.
     */
    private fun EncounterContext.tank(): Player? {
        val fighting = players
        tanks[boss]?.get()?.let { held -> if (fighting.any { it === held }) return held }
        val pick = boss.target?.takeIf { held -> fighting.any { it === held } } ?: fighting.firstOrNull() ?: return null
        tanks[boss] = java.lang.ref.WeakReference(pick)
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        log("takes on ${pick.username}")
        return pick
    }

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
    private fun EncounterContext.attack(tank: Player) {
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
        swings += 1
        if (swings % 2 == 0 && players.isNotEmpty()) crosses()
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

    /** [x], [z] and [CROSS_ARM] tiles each way along both axes: a cross. */
    fun cross(x: Int, z: Int): Set<Tile> {
        val tiles = LinkedHashSet<Tile>()
        tiles += Tile(x, z)
        for (d in 1..CROSS_ARM) {
            tiles += Tile(x + d, z)
            tiles += Tile(x - d, z)
            tiles += Tile(x, z + d)
            tiles += Tile(x, z - d)
        }
        return tiles
    }

    /** A cross of lightning on each player, and the special attack energy for whoever dodges theirs at the last tick. */
    private fun EncounterContext.crosses() {
        val tiles = players.flatMap { cross(it.x, it.z) }.toSet()
        // No one left in the room (the swing killed the last of them): nothing to aim at.
        if (tiles.isEmpty()) return
        boss.effects.anim(ANIM_SPECIAL)
        boss.effects.spotanim(0, SPOT_CRACK, 0)
        hazard(tiles, warning = SPOT_CROSS_MARKER, warnTicks = CROSS_DELAY, ticks = 1, minDamage = CROSS_MIN, maxDamage = CROSS_MAX, spotanim = SPOT_CROSS)
        // On it the tick before it lands, off it as it lands: a perfect dodge.
        var onIt: List<Player> = emptyList()
        after(CROSS_DELAY - 1) { onIt = players.filter { Tile(it.x, it.z) in tiles } }
        after(CROSS_DELAY) {
            for (player in onIt) {
                if (player.dead || player !in players || Tile(player.x, player.z) in tiles) continue
                val energy = world.specialAttacks.energy(player)
                world.specialAttacks.setEnergy(player, minOf(SpecialAttacks.FULL, energy + DODGE_ENERGY))
                log("${player.username} dodged the cross")
            }
        }
    }

    /** Whether the fight is over, the boss dead or gone: nothing lands after. */
    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss
}
