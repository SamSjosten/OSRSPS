package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.max
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/**
 * Dreadnoth the Jellied (raid step 20a), the third row's east boss, in his own room: a 5 by 5 demon in a shell of jelly
 * who crawls after his target and hits everyone round him, and his jellies.
 *
 * - He crawls a tile every [CRAWL_EVERY] ticks after his target, the first player onto his arena ([crawl]).
 * - Every [ATTACK_SPEED] ticks, with anyone round him (under him too), a melee swing at each of them ([melee]).
 * - At each of [WAVES] of his hitpoints, a wave ([wave]): one, two and then three volleys [VOLLEY_GAP] ticks apart. Each
 *   volley is a jump, and a tick later a slam over the whole arena ([slam]), [VOLLEY_TILES] tiles of jelly in or by his
 *   footprint and [SHOTS] ranged shots at every player ([shoot]), landing [SHOT_LANDS] ticks after they are thrown.
 *   Where a shot lands, jelly gathers, and [JELLIES_AFTER] ticks after the last volley's shots a jelly rises on each such
 *   tile ([rise]), at most [MAX_JELLIES] in all. He stands still through a wave.
 * - The jellies ([jellies]) always go for the nearest player, walking through other NPCs and each other, and every
 *   [JELLY_SPEED] ticks hit whoever they are beside, draining their prayer ([jellyHit]). A jelly that dies leaves jelly on
 *   its tile.
 * - Jelly on the floor ([floor]) hurts whoever stands in it [FLOOR_MIN] to [FLOOR_MAX] a tick, for the rest of the fight.
 *
 * From the OSRS Wiki (Dreadnoth the Jellied and Jelly (Dreadnoth), read 2026-11-10): his stats and bonuses, 5 by 5,
 * the 4-tick attack speed, max hit 45, the 85% fire weakness, poison immunity, the 50% cap on his offensive stats'
 * drain (28 October 2026), his hitpoints by party size; a jelly's stats, its 5-tick crush attack, max hit 17, the 60%
 * fire weakness, its attacks draining prayer "much like the Tz-Kihs", and bigger parties getting more of them. From The
 * Fractured Archive/Strategies (read 2026-11-10): his crawl of about a tile every two ticks; his melee hitting everyone
 * round him; the waves at 85%, 65% and 45%, "an arena-wide slam, followed by three ranged attacks", Protect from Melee
 * for the slam and Protect from Missiles for the shots, one more slam each wave up to three; the jellies they bring;
 * jelly on tiles dealing "6-8 damage per tick" where jellies die. From the tsps capture notes (RSPSApp/tsps
 * docs/fractured-archive.md, read 2026-11-10: five spectator captures of live fights on rev 241.1, so no damage on
 * players): where he stands as it begins, his arena, the crawl's tile every two ticks, his facing his target, the waves'
 * thresholds, their volleys 6 ticks apart, each a `jump01` with three damage tiles and three `ranged_proj01` at each
 * player a tick later (90, 120 and 150 client cycles), the splat and `jelly_spawning_idle` where they land, sent every
 * tick till the jellies rise 8 ticks after the last volley, at most 12 of them, the jellies' 75 hitpoints, going for the
 * nearest fighter and walking through NPCs, and the jellies' `jelly_attack` and `jelly_death`. From the cache: the ids
 * below, and a jelly's 75 hitpoints (the Wiki's infobox says 61). From Sam's recordings of 2026-11-09: his 3,930
 * hitpoints for a party of four and 4,810 for five, as the Wiki has them; a 38 and a 28 on Sam as the first wave
 * landed; mostly 8 to 10 later, beside jellies.
 *
 * Guessed, until a recording shows more (each also in the README): every damage figure ([SLAM_MAX], [SHOT_MAX], his
 * melee's type, crush) and both protection prayers blocking all of theirs; his target being the first player in, then
 * whoever he fights; his first swing [ATTACK_SPEED] ticks in, and the swing waiting for someone to be round him; his
 * standing still through a wave; the shots following the player they're thrown at, wherever they go, and gathering
 * jelly on the tile they were thrown at; one jelly for each tile where shots landed, rising [JELLIES_AFTER] ticks after
 * the last volley's shots; the three damage tiles' places; the jellies' walk at a tile a tick, their first hit a
 * [JELLY_SPEED] after they rise, the drain being the hit's roll, at least 1, through Protect from Melee too (which
 * blocks the hit itself); jelly on the floor lasting till the fight ends, and none from his crawl. The jellies' count
 * growing with the party comes only from the shots: three a player each volley.
 */
object Dreadnoth {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "dreadnoth"

    /** `tfa_jelly_boss`. */
    const val BOSS = 16665

    /** `tfa_jelly_minion`: a jelly. */
    const val JELLY = 16666

    /** dreadnoth_melee01, his swing; dreadnoth_jump01, a wave's jump. */
    const val ANIM_MELEE = 14779
    const val ANIM_WAVE = 14777

    /** jelly_spawn, a jelly rising. Picked by name: the captures don't name what a jelly plays as it rises. */
    const val ANIM_JELLY_RISE = 15162

    /** jelly_attack, a jelly's hit (tsps). */
    const val ANIM_JELLY_ATTACK = 1586

    /** vfx_dreadnoth_ranged_proj01, a wave's shot. */
    const val SPOT_SHOT = 4211

    /** jelly_spawning_idle, the jelly gathering where a shot landed, sent every tick till the jellies rise (tsps). */
    const val SPOT_GATHERING = 4210

    /** tfa_jelly_boss_damage_tile: jelly on the floor. */
    const val LOC_JELLY = 62609

    /** The sound of a volley's three damage tiles (tsps). */
    const val SOUND_VOLLEY = 12361

    /** His south-west tile as the fight begins, and his arena (tsps: 4189,5661 in 4181-4198 x 5656-5670). */
    const val X = 4189
    const val Z = 5661
    const val ARENA_MIN_X = 4181
    const val ARENA_MAX_X = 4198
    const val ARENA_MIN_Z = 5656
    const val ARENA_MAX_Z = 5670

    /** Where his room's transporter puts a player: the west edge of his arena (tsps). */
    const val ARENA_ENTRY_X = 4184
    const val ARENA_ENTRY_Z = 5663

    /** His crawl: a tile every this many ticks (tsps, and the Wiki's "around one tile every two ticks"). */
    const val CRAWL_EVERY = 2

    /** The Wiki's attack speed. */
    const val ATTACK_SPEED = 4

    /** The waves, at these shares of his hitpoints in percent: one volley at the first, two, then three (tsps, Wiki). */
    val WAVES = listOf(85, 65, 45)

    /** A wave's volleys this many ticks apart (tsps). */
    const val VOLLEY_GAP = 6

    /** A volley's slam, tiles and shots this many ticks after its jump (tsps). */
    const val VOLLEY_LANDS = 1

    /** Tiles of jelly a volley leaves in or by his footprint (tsps). */
    const val VOLLEY_TILES = 3

    /** A volley's shots at each player, and the ticks each takes to land (tsps: 90, 120 and 150 client cycles). */
    const val SHOTS = 3
    val SHOT_LANDS = listOf(3, 4, 5)

    /** The shots' client cycles: when each sets off and lands (tsps). */
    private val SHOT_START = listOf(10, 12, 14)

    /** The jellies rise this many ticks after the last volley's shots (tsps), at most this many of them in all (tsps). */
    const val JELLIES_AFTER = 8
    const val MAX_JELLIES = 12

    /** The slam over the whole arena: up to this, Protect from Melee blocking it. Guessed (Sam took a 38 as one landed). */
    const val SLAM_MAX = 40

    /** A shot: up to this, Protect from Missiles blocking it. Guessed. */
    const val SHOT_MAX = 15

    /** A jelly's attack speed (the Wiki's). */
    const val JELLY_SPEED = 5

    /** Jelly on the floor, each tick to whoever stands in it (the Wiki's "6-8 damage per tick"). */
    const val FLOOR_MIN = 6
    const val FLOOR_MAX = 8

    /** The fight, or null when the cache hasn't Dreadnoth (a test world's). Without the jelly's type, no jellies rise. */
    fun def(types: NpcTypes): EncounterDef? {
        val type = types[BOSS] ?: return null
        val jelly = types[JELLY]
        return EncounterDef(
            NAME, type,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(CycleStep(1) { tick(jelly) }),
                    onStart = {
                        // He crawls after his target anywhere in his room, never giving up for being far from his spawn,
                        // and through his jellies.
                        boss.unleashed = true
                        boss.passesNpcs = true
                        // Never at a walk: taken on before his fight began (the raid's aggress), he'd follow at a tile a tick.
                        world.npcs.stopFollowing(boss)
                        state.nextMelee = ATTACK_SPEED.toLong()
                        target()
                    },
                ),
            ),
            scriptedAttacks = true,
            status = { "${boss.type.name} ${boss.hitpoints}/${boss.maxHitpoints} hp, ${adds.count { it.type.id == JELLY }} jellies, ${state.floor.size} tiles of jelly" },
        )
    }

    /** What a fight keeps between ticks, by boss. Holds its NPCs and players only weakly. */
    private class State {
        /** His target, kept here as his combat target can go (they died, or left). */
        var target: WeakReference<Player>? = null

        /** The phase tick of his next swing, and until when he stands still (a wave). */
        var nextMelee = 0L
        var boundUntil = 0L

        /** How many of [WAVES] have begun. */
        var waves = 0

        /** The tiles where jelly gathers for the next jellies, and the phase tick they rise on. */
        val gathering = LinkedHashSet<Tile>()
        var riseAt = Long.MAX_VALUE

        /** The jellies standing, each with the phase tick of its next hit; one is let go of once its death is seen. */
        val jellies = WeakHashMap<Npc, Long>()

        /** Jelly on the floor, by tile. */
        val floor = LinkedHashMap<Tile, ZoneLoc>()
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    /** One tick of the fight. */
    private fun EncounterContext.tick(jelly: NpcType?) {
        val target = target()
        if (state.waves < WAVES.size && phaseTick >= state.boundUntil && boss.hitpoints * 100 <= boss.maxHitpoints * WAVES[state.waves]) {
            state.waves += 1
            wave(state.waves)
        }
        if (phaseTick < state.boundUntil) {
            hold()
        } else {
            if (target != null) crawl(target)
            if (phaseTick >= state.nextMelee) melee()
        }
        gather()
        if (phaseTick >= state.riseAt && jelly != null) rise(jelly)
        jellies()
        floor()
    }

    /**
     * His target: the player he fights while they're in the fight, else the one he was set on (the first player in),
     * else the nearest in the fight.
     */
    private fun EncounterContext.target(): Player? {
        val fighting = players
        state.target?.get()?.let { held -> if (fighting.any { it === held }) return held }
        // Guessed: the captures show only that he faces his target as he crawls, not how he picks one.
        val pick = boss.target?.takeIf { held -> fighting.any { it === held } } ?: nearest(boss, fighting) ?: return null
        state.target = WeakReference(pick)
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        log("takes on ${pick.username}")
        return pick
    }

    /** The one of [players] nearest [npc], edge to edge; the first in [Player.index] order of those as near. */
    private fun nearest(npc: Npc, players: List<Player>): Player? =
        players.minByOrNull { Bork.distance(npc.x, npc.z, npc.type.size, it.x, it.z) }

    /** He crawls: a step after [target] on every [CRAWL_EVERY]th tick, facing them all the while. */
    private fun EncounterContext.crawl(target: Player) {
        if (boss.following != null) world.npcs.stopFollowing(boss)
        boss.face(target)
        if (phaseTick % CRAWL_EVERY != 0L) return
        val from = Tile(boss.x, boss.z)
        world.npcs.stepTowards(boss, target)
        if (boss.x != from.x || boss.z != from.z) log("crawls to ${boss.x},${boss.z}")
    }

    /** He stands where he is. */
    private fun EncounterContext.hold() {
        if (boss.following != null) world.npcs.stopFollowing(boss)
        boss.destination = null
    }

    /** Whether ([x], [z]) is round him or under him, where his swing reaches. */
    fun inReach(bossX: Int, bossZ: Int, size: Int, x: Int, z: Int): Boolean = Bork.near(bossX, bossZ, size, x, z, 1)

    /** His swing, at each player round him; with nobody there it waits for someone to come. */
    private fun EncounterContext.melee() {
        val struck = players.filter { inReach(boss.x, boss.z, boss.type.size, it.x, it.z) }
        // Guessed: with nobody round him the swing waits, and is due the tick someone comes.
        if (struck.isEmpty()) return
        state.nextMelee = phaseTick + ATTACK_SPEED
        for (player in struck) {
            if (gone()) return
            world.meleeCombat.swingAt(boss, player, world.tick, ANIM_MELEE)
            log("swings at ${player.username}")
        }
        // He faces his target again after swinging round.
        state.target?.get()?.let { boss.face(it) }
    }

    /** Wave [number]: that many volleys, [VOLLEY_GAP] apart; he stands till the last one's shots are thrown. */
    private fun EncounterContext.wave(number: Int) {
        log("wave $number at ${boss.hitpoints} hp: $number volleys")
        // Guessed: he neither crawls nor swings through a wave, standing till the last volley's shots are thrown.
        hold()
        state.boundUntil = phaseTick + VOLLEY_GAP * (number - 1) + VOLLEY_LANDS + 1
        volley(last = number == 1)
        for (i in 1 until number) after(VOLLEY_GAP * i) { volley(last = i == number - 1) }
    }

    /** A volley: his jump, then a tick on the slam, the jelly by him and the shots, the [last] of a wave's. */
    private fun EncounterContext.volley(last: Boolean) {
        boss.effects.anim(ANIM_WAVE)
        log("jumps")
        after(VOLLEY_LANDS) {
            slam()
            volleyTiles()
            for (player in players) shoot(player)
            // The jellies rise this long after the last volley's shots are thrown.
            if (last) state.riseAt = phaseTick + JELLIES_AFTER
        }
    }

    /** The slam over the whole arena: everyone in the fight, Protect from Melee blocking it. */
    private fun EncounterContext.slam() {
        for (player in players) {
            if (gone()) return
            // Guessed: the prayer blocking all of it, as it does an ordinary monster's melee.
            val damage = if (world.prayers.protects(player, AttackType.CRUSH)) 0 else roll(0, SLAM_MAX)
            val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
            log("slam hits ${player.username} for $damage" + if (dealt != damage) ", $dealt dealt" else "")
        }
    }

    /** [VOLLEY_TILES] tiles of jelly in or by his footprint, on his arena's floor. */
    private fun EncounterContext.volleyTiles() {
        val size = boss.type.size
        val around = (boss.x - 1..boss.x + size).flatMap { x -> (boss.z - 1..boss.z + size).map { Tile(x, it) } }
            .filter { inArena(boss.spawn.x, boss.spawn.z, it.x, it.z) && it !in state.floor }
            .toMutableList()
        // Guessed: which of the tiles round him; the captures show only that they're in or by his footprint.
        val placed = ArrayList<Tile>()
        repeat(minOf(VOLLEY_TILES, around.size)) { placed += around.removeAt(roll(0, around.size - 1)) }
        val middle = size / 2
        world.zones.soundArea(boss.x + middle, boss.z + middle, boss.level, SOUND_VOLLEY, radius = 15)
        for (tile in placed) addFloor(tile)
        log("jelly by him on ${placed.joinToString(" ") { "${it.x},${it.z}" }}")
    }

    /**
     * Whether ([x], [z]) is on the floor of the arena of a Dreadnoth who began with his south-west tile at ([spawnX],
     * [spawnZ]) (tsps: 4181-4198 x 5656-5670 round 4189,5661).
     */
    fun inArena(spawnX: Int, spawnZ: Int, x: Int, z: Int): Boolean =
        x - spawnX in ARENA_MIN_X - X..ARENA_MAX_X - X && z - spawnZ in ARENA_MIN_Z - Z..ARENA_MAX_Z - Z

    /**
     * His [SHOTS] shots at [player], thrown now at their tile: each lands [SHOT_LANDS] ticks on, on them wherever they
     * are then, Protect from Missiles blocking it, and gathers jelly on the tile it was thrown at.
     */
    private fun EncounterContext.shoot(player: Player) {
        val at = Tile(player.x, player.z)
        val middle = boss.type.size / 2
        for (i in 0 until SHOTS) {
            world.zones.projectile(
                Projectile(
                    id = SPOT_SHOT, fromX = boss.x + middle, fromZ = boss.z + middle, level = boss.level, toX = at.x, toZ = at.z,
                    startHeight = 100, endHeight = 0, startTime = SHOT_START[i], endTime = SHOT_LANDS[i] * 30, angle = 16, progress = 64,
                ),
            )
            // Guessed: the shot follows its player (the Wiki's Protect from Missiles for them), and the jelly gathers on
            // the tile it was thrown at, where the projectile lands.
            after(SHOT_LANDS[i]) {
                if (inArena(boss.spawn.x, boss.spawn.z, at.x, at.z)) state.gathering += at
                if (player.dead || players.none { it === player }) return@after
                val damage = if (world.prayers.protects(player, AttackType.RANGED)) 0 else roll(0, SHOT_MAX)
                val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                log("shot hits ${player.username} for $damage" + if (dealt != damage) ", $dealt dealt" else "")
            }
        }
        log("shoots at ${player.username} on ${at.x},${at.z}")
    }

    /** The jelly gathering where shots landed, shown every tick till the jellies rise (tsps). */
    private fun EncounterContext.gather() {
        for (tile in state.gathering) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_GATHERING)
    }

    /** The jellies rise, one on each tile where jelly gathered, while there are fewer than [MAX_JELLIES]. */
    private fun EncounterContext.rise(type: NpcType) {
        state.riseAt = Long.MAX_VALUE
        val tiles = state.gathering.toList()
        state.gathering.clear()
        var risen = 0
        // Guessed: one jelly for each tile where shots landed; the Wiki says only that bigger parties get more.
        for (tile in tiles) {
            if (adds.count { it.type.id == JELLY } >= MAX_JELLIES) break
            val npc = spawnAdd(type, tile.x, tile.z) ?: break
            // They fight only as this fight says, never giving up on whoever they're after, and through each other.
            npc.scriptedAttacks = true
            npc.unleashed = true
            npc.passesNpcs = true
            npc.effects.anim(ANIM_JELLY_RISE)
            state.jellies[npc] = phaseTick + JELLY_SPEED
            risen++
        }
        log("$risen jellies rise")
    }

    /** Each jelly goes for the nearest player and hits them when beside them; one that died leaves jelly on its tile. */
    private fun EncounterContext.jellies() {
        val fighting = players
        for ((npc, next) in state.jellies.entries.toList()) {
            if (npc.dead || world.npcs[npc.index] !== npc) {
                state.jellies.remove(npc)
                if (inArena(boss.spawn.x, boss.spawn.z, npc.x, npc.z)) addFloor(Tile(npc.x, npc.z))
                log("a jelly falls at ${npc.x},${npc.z}")
                continue
            }
            val near = nearest(npc, fighting) ?: continue
            if (npc.following !== near) {
                npc.target = near
                world.npcs.follow(npc, near)
            }
            if (Bork.beside(npc.x, npc.z, npc.type.size, near.x, near.z)) {
                npc.face(near)
                if (phaseTick >= next) {
                    state.jellies[npc] = phaseTick + JELLY_SPEED
                    jellyHit(npc, near)
                }
            }
        }
    }

    /**
     * A jelly's hit on [player]: its melee roll, Protect from Melee blocking the hit; their prayer drained by what it
     * rolled, at least 1, whatever the prayer.
     */
    private fun EncounterContext.jellyHit(jelly: Npc, player: Player) {
        jelly.effects.anim(ANIM_JELLY_ATTACK)
        val roll = world.combat.npcAttack(jelly, player) ?: return
        val rolled = if (CombatMath.rollHits(world.random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(world.random, roll.maxHit) else 0
        val damage = if (world.prayers.protects(player, AttackType.CRUSH)) 0 else rolled
        val dealt = world.meleeCombat.hitPlayer(player, null, damage, world.tick)
        if (!player.dead) player.attackedTick = world.tick
        // Guessed: the drain is the roll, at least 1, whatever the prayer; the Wiki says only "much like the Tz-Kihs".
        val drain = max(1, rolled)
        val prayer = player.skills.currentLevel(Skill.PRAYER)
        player.skills.setCurrentLevel(Skill.PRAYER, max(0, prayer - drain))
        log("a jelly hits ${player.username} for $damage" + (if (dealt != damage) ", $dealt dealt" else "") + ", draining ${prayer - max(0, prayer - drain)} prayer")
    }

    /** Puts jelly on [tile] of the floor, if there is none there. Guessed: it lies till the fight ends. */
    private fun EncounterContext.addFloor(tile: Tile) {
        if (tile in state.floor) return
        state.floor[tile] = placeLoc(LOC_JELLY, tile.x, tile.z)
    }

    /** Jelly on the floor hurts whoever stands in it, from the room. */
    private fun EncounterContext.floor() {
        if (state.floor.isEmpty()) return
        for (player in players) {
            if (Tile(player.x, player.z) !in state.floor) continue
            val dealt = world.meleeCombat.hitPlayer(player, null, roll(FLOOR_MIN, FLOOR_MAX), world.tick)
            // In combat all the same, as a boss's hazard holds them (osrsps.game.encounter.Hazard).
            if (!player.dead) player.attackedTick = world.tick
            log("jelly burns ${player.username} for $dealt")
        }
    }

    /** Whether the fight is over, the boss dead or gone: nothing lands after. */
    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss
}
