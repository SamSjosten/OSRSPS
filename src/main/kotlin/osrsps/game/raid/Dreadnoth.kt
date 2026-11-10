package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.DinhsBulwark
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
import osrsps.map.Tile

/**
 * What Dreadnoth says over his head. The words are those the tsps capture notes and the Wiki quote from live fights,
 * typed into the dialogue file like every other line.
 */
object DreadnothLines : Lines("tfa_dreadnoth", "The_Fractured_Archive/Strategies") {
    val GLUB = line("glub", "Dreadnoth as his enrage begins, at 25% of his hitpoints")
}

/**
 * Dreadnoth the Jellied (raid steps 20a and 20b), the third row's east boss, in his own room: a 5 by 5 demon in a shell of jelly
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
 * - At [ENRAGE_PERCENT] of his hitpoints, his enrage (20b, [enrage]): his line, no more swings or crawl, and [FIRST_JUMP]
 *   ticks on and every [JUMP_EVERY] after, a jump at a random player ([jump]), landing [JUMP_LANDS] ticks later where
 *   [landingFor] says. A player under him as he lands is slammed ([land]) and he heals by the damage; one not under him
 *   without Protect from Melee is hurt all the same; jellies under him are absorbed, healing him by what they had left.
 *   As he lands, jelly in his footprint, and at each player a shot and a splatter of jelly that lands on their tile
 *   [SPLATTER_LANDS] ticks on and lies there.
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
 * For the enrage (20b): from the Wiki's Strategies, the "*glub glub*" at 25%, jumping towards and slamming a random
 * player, large damage and a heal of about 30 to 40 for one underneath, damage regardless of place without Protect
 * from Melee, a ranged shot and a jelly splatter at the player after, jellies landed on absorbed and healing him, no
 * more melee; from the tsps captures, the first jump 5 ticks after the line and one every 8 after, `jump02`, the
 * 90-cycle move with `tfa_boss_jelly_5x5_jump` (the shadow) where he lands, landing centred on the player within 5
 * tiles, else 5 tiles the nearest of eight ways towards them, never west of x 4184, one to three damage tiles in his
 * footprint as he lands, and at each player a `ranged_proj01` (90 cycles) and a `ranged_proj02` (180, sound 12362) at
 * their tile, which becomes a damage tile 6 ticks later.
 *
 * Guessed, until a recording shows more (each also in the README): every damage figure ([SLAM_MAX], [SHOT_MAX], his
 * melee's type, crush) and both protection prayers blocking all of theirs; his target being the first player in, then
 * whoever he fights; his first swing [ATTACK_SPEED] ticks in, and the swing waiting for someone to be round him; his
 * standing still through a wave; the shots following the player they're thrown at, wherever they go, and gathering
 * jelly on the tile they were thrown at; one jelly for each tile where shots landed, rising [JELLIES_AFTER] ticks after
 * the last volley's shots; the three damage tiles' places; the jellies' walk at a tile a tick, their first hit a
 * [JELLY_SPEED] after they rise, the drain being the hit's roll, at least 1, through Protect from Melee too (which
 * blocks the hit itself); jelly on the floor lasting till the fight ends, and none from his crawl. The jellies' count
 * growing with the party comes only from the shots: three a player each volley. For the enrage: the jump's target
 * picked at random each time (the Wiki's; tsps say "its target"), its damage under him ([CRUSH_MIN] to [CRUSH_MAX], no
 * prayer helping) and off him ([STRAY_MAX]), the shot's as a wave's, the absorbed jellies' heal being their hitpoints
 * left, his landing kept wholly on his arena, the splatter hurting nobody as it lands, and the jump itself a teleport
 * as it lands (the 90-cycle exact move isn't sent: NPC exact moves aren't built), the shadow shown as he jumps.
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

    /** dreadnoth_jump02, an enrage jump. */
    const val ANIM_JUMP = 14780

    /** tfa_boss_jelly_5x5_jump, his shadow where he'll land; vfx_dreadnoth_ranged_proj02, the splatter. */
    const val SPOT_SHADOW = 4209
    const val SPOT_SPLATTER = 4212

    /** The splatter's sound (tsps). */
    const val SOUND_SPLATTER = 12362

    /** His enrage begins at this share of his hitpoints, in percent (the Wiki's 25%). */
    const val ENRAGE_PERCENT = 25

    /** His first jump this many ticks after his line, and one every this many after (tsps). */
    const val FIRST_JUMP = 5
    const val JUMP_EVERY = 8

    /** A jump lands this many ticks after it begins (tsps: a 90-cycle move). */
    const val JUMP_LANDS = 3

    /** He lands on a player this near his middle, else this far towards them (tsps). */
    const val JUMP_REACH = 5

    /** The slam on a player under him as he lands, no prayer helping. Guessed (the Wiki's "large amounts"). */
    const val CRUSH_MIN = 30
    const val CRUSH_MAX = 50

    /** The slam on a player not under him without Protect from Melee (the Wiki's "regardless of location"). Guessed. */
    const val STRAY_MAX = 20

    /** Tiles of jelly in his footprint as he lands: one to three (tsps). */
    const val LANDING_TILES_MIN = 1
    const val LANDING_TILES_MAX = 3

    /** The shot after a landing lands this many ticks on (tsps: 90 cycles), the splatter this many (180). */
    const val LANDING_SHOT_LANDS = 3
    const val SPLATTER_LANDS = 6

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
                    endsAtPercent = ENRAGE_PERCENT,
                ),
                PhaseDef(
                    "enrage",
                    cycle = listOf(CycleStep(1) { enrageTick(jelly) }),
                    onStart = { enrage() },
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

        /** The enrage's phase tick of his next jump. */
        var nextJump = Long.MAX_VALUE

        /** How many of [WAVES] have begun. */
        var waves = 0

        /** The tiles where jelly gathers for the next jellies, and the world tick they rise on (it can be in the enrage). */
        val gathering = LinkedHashSet<Tile>()
        var riseAt = Long.MAX_VALUE

        /** The jellies standing, each with the world tick of its next hit; one is let go of once its death is seen. */
        val jellies = WeakHashMap<Npc, Long>()

        /** Jelly on the floor, by tile. */
        val floor = LinkedHashMap<Tile, ZoneLoc>()
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    /** One tick of the fight. */
    private fun EncounterContext.tick(jelly: NpcType?) {
        val target = target()
        // Guessed: a wave waits for the last one's jellies to rise, so each rises on its own wave's tiles.
        if (state.waves < WAVES.size && phaseTick >= state.boundUntil && state.riseAt == Long.MAX_VALUE && boss.hitpoints * 100 <= boss.maxHitpoints * WAVES[state.waves]) {
            state.waves += 1
            wave(state.waves)
        }
        if (phaseTick < state.boundUntil) {
            hold()
        } else {
            if (target != null) crawl(target)
            if (phaseTick >= state.nextMelee) melee()
        }
        jelliesAndFloor(jelly)
    }

    /** What goes on in either phase: the jelly gathering and rising, the jellies, and jelly on the floor. */
    private fun EncounterContext.jelliesAndFloor(jelly: NpcType?) {
        gather()
        if (world.tick >= state.riseAt && jelly != null) rise(jelly)
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
            if (last) state.riseAt = world.tick + JELLIES_AFTER
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
                // Shown on the tick it lands, this tick's gathering already sent, then every tick with the rest.
                if (inArena(boss.spawn.x, boss.spawn.z, at.x, at.z) && state.gathering.add(at)) world.zones.mapAnim(at.x, at.z, boss.level, SPOT_GATHERING)
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
            state.jellies[npc] = world.tick + JELLY_SPEED
            risen++
        }
        log("$risen jellies rise")
    }

    /** Each jelly goes for the nearest player and hits them when beside them; one that died leaves jelly on its tile. */
    private fun EncounterContext.jellies() {
        val fighting = players
        // By index, so a seeded fight rolls the same for each jelly every time.
        for ((npc, next) in state.jellies.entries.sortedBy { it.key.index }) {
            if (gone()) return
            if (npc.dead || world.npcs[npc.index] !== npc) {
                state.jellies.remove(npc)
                if (inArena(boss.spawn.x, boss.spawn.z, npc.x, npc.z)) addFloor(Tile(npc.x, npc.z))
                log("a jelly falls at ${npc.x},${npc.z}")
                continue
            }
            // One a jelly killed this tick isn't the next one's.
            val near = nearest(npc, fighting.filter { !it.dead }) ?: continue
            if (npc.following !== near) {
                npc.target = near
                world.npcs.follow(npc, near)
            }
            if (Bork.beside(npc.x, npc.z, npc.type.size, near.x, near.z)) {
                npc.face(near)
                if (world.tick >= next) {
                    state.jellies[npc] = world.tick + JELLY_SPEED
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
        // Cut by Dinh's bulwark on Block, as any NPC's melee is; the drain stays the roll.
        val damage = when {
            world.prayers.protects(player, AttackType.CRUSH) -> 0
            world.combat.bulwarkBlocking(player, world.tick) -> DinhsBulwark.blocked(rolled)
            else -> rolled
        }
        val dealt = world.meleeCombat.hitPlayer(player, jelly, damage, world.tick)
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
            if (gone()) return
            if (Tile(player.x, player.z) !in state.floor) continue
            val dealt = world.meleeCombat.hitPlayer(player, null, roll(FLOOR_MIN, FLOOR_MAX), world.tick)
            // In combat all the same, as a boss's hazard holds them (osrsps.game.encounter.Hazard).
            if (!player.dead) player.attackedTick = world.tick
            log("jelly burns ${player.username} for $dealt")
        }
    }

    private fun EncounterContext.text(line: Line): String = world.dialogueText.get(line)

    /** His enrage begins: his line, and he stands, swinging no more, till his first jump [FIRST_JUMP] ticks on. */
    private fun EncounterContext.enrage() {
        hold()
        boss.effects.say(text(DreadnothLines.GLUB))
        state.nextJump = FIRST_JUMP.toLong()
        log("enrage at ${boss.hitpoints} hp")
    }

    /** The enrage's tick: his jumps, and the jellies and jelly on the floor as before. */
    private fun EncounterContext.enrageTick(jelly: NpcType?) {
        hold()
        state.target?.get()?.let { boss.face(it) }
        if (phaseTick >= state.nextJump) {
            state.nextJump = phaseTick + JUMP_EVERY
            jump()
        }
        jelliesAndFloor(jelly)
    }

    /**
     * Where a [size] by [size] Dreadnoth at ([x], [z]) jumping at a player on ([toX], [toZ]) lands, his south-west tile:
     * centred on them when they are within [JUMP_REACH] tiles of his middle, else [JUMP_REACH] tiles the nearest of
     * eight ways towards them (tsps), kept wholly on the arena of a Dreadnoth who began at ([spawnX], [spawnZ]), which
     * keeps him from west of x 4184 (tsps). Guessed: kept on the rest of the arena too.
     */
    fun landingFor(x: Int, z: Int, size: Int, toX: Int, toZ: Int, spawnX: Int, spawnZ: Int): Tile {
        val half = size / 2
        val cx = x + half
        val cz = z + half
        val dx = toX - cx
        val dz = toZ - cz
        val (mx, mz) = if (max(abs(dx), abs(dz)) <= JUMP_REACH) {
            toX to toZ
        } else {
            // The nearest of the eight ways: a diagonal when the lesser leg is past tan 22.5 degrees of the greater.
            val diagonal = 5 * minOf(abs(dx), abs(dz)) >= 2 * max(abs(dx), abs(dz))
            val sx = if (abs(dx) >= abs(dz) || diagonal) dx.sign else 0
            val sz = if (abs(dz) >= abs(dx) || diagonal) dz.sign else 0
            (cx + JUMP_REACH * sx) to (cz + JUMP_REACH * sz)
        }
        val minX = spawnX + (ARENA_ENTRY_X - X)
        val maxX = spawnX + (ARENA_MAX_X - X) - (size - 1)
        val minZ = spawnZ + (ARENA_MIN_Z - Z)
        val maxZ = spawnZ + (ARENA_MAX_Z - Z) - (size - 1)
        return Tile((mx - half).coerceIn(minX, maxX), (mz - half).coerceIn(minZ, maxZ))
    }

    /** A jump at a random player: his shadow where he'll land now, and the landing [JUMP_LANDS] ticks on. */
    private fun EncounterContext.jump() {
        val at = randomPlayer() ?: return
        val size = boss.type.size
        val to = landingFor(boss.x, boss.z, size, at.x, at.z, boss.spawn.x, boss.spawn.z)
        boss.face(at)
        boss.effects.anim(ANIM_JUMP)
        world.zones.mapAnim(to.x + size / 2, to.z + size / 2, boss.level, SPOT_SHADOW)
        log("jumps at ${at.username}, landing on ${to.x},${to.z}")
        after(JUMP_LANDS) { land(to) }
    }

    /**
     * He lands with his south-west tile on [to]: jellies under him absorbed, the slam on whoever is under him (healing
     * him by it) and on everyone else without Protect from Melee, jelly in his footprint, and a shot and a splatter at
     * each player.
     */
    private fun EncounterContext.land(to: Tile) {
        world.npcs.teleport(boss, to.x, to.z)
        log("lands on ${to.x},${to.z}")
        for (npc in adds.filter { it.type.id == JELLY && boss.covers(it.x, it.z) }) {
            val left = npc.hitpoints
            state.jellies.remove(npc)
            world.removeNpc(npc)
            heal(left, "absorbs a jelly")
        }
        for (player in players) {
            if (gone()) return
            if (boss.covers(player.x, player.z)) {
                val damage = roll(CRUSH_MIN, CRUSH_MAX)
                val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                log("lands on ${player.username} for $damage" + if (dealt != damage) ", $dealt dealt" else "")
                heal(dealt, "heals off ${player.username}")
            } else if (!world.prayers.protects(player, AttackType.CRUSH)) {
                val damage = roll(0, STRAY_MAX)
                val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
                log("slam hits ${player.username} for $damage" + if (dealt != damage) ", $dealt dealt" else "")
            }
        }
        if (gone()) return
        val size = boss.type.size
        val footprint = (0 until size).flatMap { dx -> (0 until size).map { dz -> Tile(boss.x + dx, boss.z + dz) } }
            .filter { it !in state.floor }.toMutableList()
        val placed = ArrayList<Tile>()
        repeat(minOf(roll(LANDING_TILES_MIN, LANDING_TILES_MAX), footprint.size)) { placed += footprint.removeAt(roll(0, footprint.size - 1)) }
        for (tile in placed) addFloor(tile)
        if (placed.isNotEmpty()) log("jelly under him on ${placed.joinToString(" ") { "${it.x},${it.z}" }}")
        for (player in players) splatter(player)
    }

    /** He heals by [amount], his health bar showing it. */
    private fun EncounterContext.heal(amount: Int, why: String) {
        if (amount <= 0) return
        val before = boss.hitpoints
        boss.hitpoints = minOf(boss.maxHitpoints, before + amount)
        // His health bar shows it: bars go out only as they are queued, as a hit queues one.
        boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        log("$why: heals ${boss.hitpoints - before}")
    }

    /**
     * After a landing, at [player]: a shot landing on them [LANDING_SHOT_LANDS] ticks on, Protect from Missiles blocking
     * it, and a splatter at their tile now that lies there as jelly [SPLATTER_LANDS] ticks on.
     */
    private fun EncounterContext.splatter(player: Player) {
        val at = Tile(player.x, player.z)
        val middle = boss.type.size / 2
        val fromX = boss.x + middle
        val fromZ = boss.z + middle
        world.zones.projectile(
            Projectile(
                id = SPOT_SHOT, fromX = fromX, fromZ = fromZ, level = boss.level, toX = at.x, toZ = at.z,
                startHeight = 100, endHeight = 0, startTime = SHOT_START[0], endTime = LANDING_SHOT_LANDS * 30, angle = 16, progress = 64,
            ),
        )
        world.zones.projectile(
            Projectile(
                id = SPOT_SPLATTER, fromX = fromX, fromZ = fromZ, level = boss.level, toX = at.x, toZ = at.z,
                startHeight = 100, endHeight = 0, startTime = SHOT_START[0], endTime = SPLATTER_LANDS * 30, angle = 16, progress = 64,
            ),
        )
        world.zones.soundArea(fromX, fromZ, boss.level, SOUND_SPLATTER, radius = 15)
        log("shoots and splatters at ${player.username} on ${at.x},${at.z}")
        after(LANDING_SHOT_LANDS) {
            if (player.dead || players.none { it === player }) return@after
            val damage = if (world.prayers.protects(player, AttackType.RANGED)) 0 else roll(0, SHOT_MAX)
            val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
            log("shot hits ${player.username} for $damage" + if (dealt != damage) ", $dealt dealt" else "")
        }
        after(SPLATTER_LANDS) {
            if (!inArena(boss.spawn.x, boss.spawn.z, at.x, at.z)) return@after
            addFloor(at)
            log("splatter lands on ${at.x},${at.z}")
        }
    }

    /** Whether the fight is over, the boss dead or gone: nothing lands after. */
    private fun EncounterContext.gone(): Boolean = boss.dead || world.npcs[boss.index] !== boss
}
