package osrsps.game.raid

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.map.Tile

/**
 * The Archive Guardian (raid steps 16a and 16b), the first boss of every Archive run: three phases on the cache's two forms.
 *
 * - **Shield**: levitating (`tfa_guardian`, 16659), its hitpoints the shield's (the Wiki's "Flying" row, 550 to 1,950).
 *   It shoots its tank, the first player it took on, every 5 ticks (ranged, max hit 25), and slams whoever stands under
 *   it instead. Only ranged, magic and halberds reach it ([osrsps.game.combat.NpcCombatDef.flying]). When the shield is
 *   gone it drops.
 * - **Grounded**: `tfa_guardian_unshielded` (16660) with the Wiki's "Grounded" hitpoints, 1,100 to 3,910, full; its
 *   fall blasts whoever is under or beside it. It swings at a player beside it every 5 ticks (crush, max hit 25).
 * - **Airborne**: at 30% it lifts off again, without its shield, for the rest of the fight: a laser at a random
 *   player every [LASER_EVERY] ticks, its damage shared by everyone standing on its line.
 *
 * From the OSRS Wiki (Archive Guardian, read 2026-11-03): its stats, bonuses, both forms' hitpoints by party size, the
 * 5-tick attack speed, max hits 25 and 99+ (the laser), the 80% fire weakness, the poison and venom immunity, and the
 * 60% Defence drain floor (21 October 2026). From The Fractured Archive/Strategies, as a guide site quotes it (the
 * Wiki's own page cuts off before the bosses): the levitating phase hit only by ranged, magic and halberds, the
 * shots at the player it chose and the slam on whoever steps under it, the shield breaking to ground it, the blast on
 * those under or next to it as it falls, 30% for the last phase with no shield, and the laser that "is fatal if more
 * than two players are alive and it isn't reduced", shared by standing in line with its target and "slightly"
 * reduced by Protect from Magic. From the 241.4 cache: both forms (Attack on each, size 5, the slash bonus 90 on the
 * grounded one, 110 levitating), the dormant `tfa_guardian_start` ("Inert Guardian", Touch) and the animations and
 * spotanims named `npc_archive_guardian_*` and `vfx_guardian_*`.
 *
 * Guessed, until a recording shows the fight (each also in the README): every timing past the attack speed (the first
 * attack 5 ticks into each phase, the laser every [LASER_EVERY] ticks landing [LASER_DELAY] after it is aimed); the
 * slam's and the blast's damage ([SLAM_MAX], [BLAST_MIN] to [BLAST_MAX]); the laser's ([laserDamage]) and its line, from
 * the Guardian's middle through its target to [LASER_LENGTH] tiles; Protect from Magic taking a tenth off it; the
 * grounded form's 40% fire weakness (a guide's); that it never moves, and keeps one tank till they fall.
 *
 * Step 16b, from guides (osrstoolkit, runescapeguides, bald.gg; read 2026-11-03) and the cache's mushroom locs and
 * `archive_guardian_tentacle` Vine: mushrooms ([mushrooms]) in the shield phase and between lasers at the end, bursting
 * on a player who steps beside one for 3 to 10 with poison and weariness ([Player.wearyTicks]), splashing those two
 * tiles off; vines ([vines]) from [VINES_FROM] ticks into the grounded phase and every [VINES_EVERY] after, each on its
 * player's side away from the boss, swiping the 3x3 round it [VINE_SWIPES] times, Protect from Melee holding a swipe to
 * [VINE_PROTECTED_MAX]. Guessed: [MUSHROOMS_EVERY], [MUSHROOM_GROW], [MUSHROOM_FUSE], the splash, [MUSHROOM_POISON],
 * [WEARY_TICKS] and [VINE_MAX]. The inert guardians are step 16c.
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

    /** npc_archive_guardian_*: its fall and lift-off, its slam (swoosh), the laser (special) and waking (spawn). */
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

    /** At this share of its grounded hitpoints, in percent, it takes off for good (a guide's 30%). */
    const val AIRBORNE_AT = 30

    /** The slam on whoever stands under it as a shot is due: up to this, Protect from Melee blocking it. Guessed. */
    const val SLAM_MAX = 25

    /** The blast on whoever is under or beside it as it falls. Guessed. */
    const val BLAST_MIN = 10
    const val BLAST_MAX = 20

    /** Ticks between lasers in the last phase, and from aiming one to its landing. Guessed. */
    const val LASER_EVERY = 10
    const val LASER_DELAY = 3

    /** How far its laser reaches past its middle, in tiles. Guessed: across the hall. */
    const val LASER_LENGTH = 32

    /** `tfa_guardian_mushroom_spawn`, `_active` and `_despawn`: a mushroom growing, ready, and bursting. */
    const val MUSHROOM_SPAWN = 62578
    const val MUSHROOM_ACTIVE = 62579
    const val MUSHROOM_DESPAWN = 62580

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

    /** The laser's damage, before it is shared: [LASER_PER_PLAYER] for each player in the fight, [LASER_MIN] at least. */
    const val LASER_MIN = 80
    const val LASER_PER_PLAYER = 40

    /**
     * The laser's damage with [alive] players in the fight, shared out among those in its line: 80 for one or two, which a
     * player alone can live through at high hitpoints or with a tick-eat, and 120 and up from three, which kills one
     * player alone. Guessed, to fit the Strategies page's words.
     */
    fun laserDamage(alive: Int): Int = max(LASER_MIN, LASER_PER_PLAYER * alive)

    /** The fight, or null when the cache hasn't the Guardian's two forms (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val flying = types[FLYING] ?: return null
        val grounded = types[GROUNDED] ?: return null
        // A cache without the vine (a test world's) has the fight without them.
        val vine = types[VINE]
        return EncounterDef(
            NAME, flying,
            listOf(
                PhaseDef(
                    "shield",
                    cycle = listOf(
                        CycleStep(1) {
                            if (phaseTick % ATTACK_SPEED == 0L) shoot()
                            if (phaseTick % MUSHROOMS_EVERY == 0L) mushrooms()
                        },
                    ),
                    onStart = {
                        boss.holdsAtZero = true
                        form(flying)
                        tank()
                    },
                    endsWhen = { boss.hitpoints <= 0 },
                ),
                PhaseDef(
                    "grounded",
                    cycle = listOf(
                        CycleStep(1) {
                            if (phaseTick % ATTACK_SPEED == 0L) strike()
                            if (vine != null && phaseTick >= VINES_FROM && (phaseTick - VINES_FROM) % VINES_EVERY == 0L) vines(vine)
                        },
                    ),
                    endsAtPercent = AIRBORNE_AT,
                    onStart = {
                        boss.holdsAtZero = false
                        form(grounded)
                        groundedHitpoints(grounded)
                        boss.hitpoints = boss.maxHitpoints
                        boss.effects.anim(ANIM_TO_GROUND)
                        boss.effects.spotanim(0, SPOT_TO_GROUND, 0)
                        val size = boss.type.size
                        val around = (boss.x - 1..boss.x + size).flatMap { x -> (boss.z - 1..boss.z + size).map { z -> Tile(x, z) } }
                        hazard(around, warning = -1, warnTicks = 0, ticks = 1, minDamage = BLAST_MIN, maxDamage = BLAST_MAX)
                        log("grounded with ${boss.hitpoints} hp")
                    },
                ),
                PhaseDef(
                    "airborne",
                    // Its laser and its mushrooms by turns (a guide's "alternates mushrooms with his laser").
                    cycle = listOf(CycleStep(LASER_EVERY) { laser() }, CycleStep(LASER_EVERY) { mushrooms() }),
                    onStart = {
                        boss.holdsAtZero = false
                        // Jumped straight here with ::raid: the grounded hitpoints, down to where this phase begins.
                        val fromGround = boss.type.id == grounded.id
                        groundedHitpoints(grounded)
                        if (!fromGround) boss.hitpoints = boss.maxHitpoints * AIRBORNE_AT / 100
                        form(flying)
                        boss.effects.anim(ANIM_TO_FLYING)
                        boss.effects.spotanim(0, SPOT_TO_FLYING, 0)
                        log("airborne with ${boss.hitpoints} hp")
                    },
                ),
            ),
            scriptedAttacks = true,
        )
    }

    /** Shows the boss as [type] for good, unless it is that already. */
    private fun EncounterContext.form(type: NpcType) {
        if (boss.type.id != type.id) world.npcs.transform(boss, type, Npcs.FOREVER)
    }

    /**
     * Its most hitpoints from here on: the grounded form's for its party size ([PartyScaling]), or the form's own when
     * it isn't scaled. They stay its most through the last phase, back in the levitating form.
     */
    private fun EncounterContext.groundedHitpoints(grounded: NpcType) {
        val party = boss.scaledFor
        boss.scaledHitpoints = (if (party > 0) world.partyScaling.hitpoints(grounded, party) else null) ?: grounded.stats.hitpoints
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
        val tank = tank() ?: return
        // Out of its range (10) or sight, it holds its fire: it never moves to get them in it. Guessed.
        if (!world.meleeCombat.inShot(boss, tank)) {
            log("can't reach ${tank.username}")
            return
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

    /** The last phase's laser: aimed at a random player now, landing [LASER_DELAY] ticks later on its line. */
    private fun EncounterContext.laser() {
        val target = randomPlayer() ?: return
        boss.effects.anim(ANIM_LASER)
        boss.effects.spotanim(0, SPOT_LASER, 0)
        boss.face(target)
        log("aims its laser at ${target.username}")
        after(LASER_DELAY) {
            val fighting = players
            if (fighting.none { it === target }) {
                log("laser has nobody to land on")
                return@after
            }
            val line = line(boss.x, boss.z, boss.type.size, Tile(target.x, target.z))
            val struck = fighting.filter { it === target || Tile(it.x, it.z) in line }
            val total = laserDamage(fighting.size)
            val share = (total + struck.size - 1) / struck.size
            for (player in struck) {
                // A hit can end the fight (Retribution killing the boss); nothing lands after that.
                if (gone()) return@after
                val damage = if (world.prayers.protects(player, AttackType.MAGIC)) share * 9 / 10 else share
                val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                log("laser hits ${player.username} for $dealt")
            }
        }
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
            // Run goes off either way; poison immunity keeps the poison and the weariness off, so it can go straight
            // back on (a guide's word on the Serpentine helm; immunity from a potion counted the same, guessed).
            player.runEnabled = false
            if (world.poison.poison(player, MUSHROOM_POISON)) player.wearyTicks = WEARY_TICKS
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
     * The laser's tiles: from the middle of a boss of [size] whose south-west tile is [bossX], [bossZ], through
     * [through] and on, [LASER_LENGTH] of them; [through] alone when it is the middle.
     */
    internal fun line(bossX: Int, bossZ: Int, size: Int, through: Tile): Set<Tile> {
        val cx = bossX + size / 2
        val cz = bossZ + size / 2
        val dx = through.x - cx
        val dz = through.z - cz
        val steps = max(abs(dx), abs(dz))
        if (steps == 0) return setOf(through)
        return (1..LASER_LENGTH).mapTo(LinkedHashSet()) { k ->
            Tile(cx + (dx.toDouble() * k / steps).roundToInt(), cz + (dz.toDouble() * k / steps).roundToInt())
        }
    }
}
