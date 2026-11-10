package osrsps.game.raid

import java.util.WeakHashMap
import kotlin.math.sign
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.combat.AttackType
import osrsps.game.combat.HitsplatType
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
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

    private fun ordinal(n: Int) = listOf("first", "second", "third")[n - 1]
}

/**
 * Ocellus (raid step 24a), the last boss of The Fractured Archive, in the vault's arena on its upper floor: in this step
 * his first phase, the shielded one, whose blue bar is his first-phase hitpoints.
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
 * - STAND-IN until step 24b: his move to the middle at two thirds and a quarter, his charge's discharge, the shield held
 *   at 1 and his change to the second phase aren't built. His first-phase bar run out, he dies, and the room is done.
 *
 * From the OSRS Wiki (Ocellus, read 2026-11-10): his first phase's stats and bonuses (npc-combat.json), 3 by 3, the
 * 5-tick attack speed, magic and ranged, the max hit 36, the 60% Defence drain cap, poison and venom immunity, his
 * hitpoints by party size (party-scaling.json). From the tsps capture notes (RSPSApp/tsps docs/fractured-archive.md,
 * read 2026-11-10: spectator captures of three live kills on rev 241.1, so no damage on players): the hooded figure's
 * tile, his corners and their order, Protect from Melee while he walks, the shot every five ticks and its graphics,
 * the seven cracks at a corner, their spots, timings and length, the interception and the charge of 2 a crack on a bar
 * of 96, the line three ticks after the last crack and the explosion three after that, his walking on three after it,
 * the two rings, and every overhead line. From Sam's recordings of 2026-11-09 (three runs, parties of three to five):
 * the arena on the upper floor (plane 1) and where a player comes in, the blue bar's hitpoints for four and five, the
 * first-phase level (1255), his line as it begins ([OcellusLines.ASCENSION]), the three corner lines, the thin bar under the blue one
 * rising in steps of about 2% of 96, players on a crack's line taking hits of 2 to 6, and the ready lines. From the cache
 * (RuneLite's gameval tables for 242): the ids below.
 *
 * Guessed, until a recording shows more (each also in the README): the order of the seven cracks (each spot once, in a
 * random order); the crack's small hit landing as its energy flows, after the heal; his first shot [SHOT_EVERY] ticks
 * after the phase begins and after he walks on from a corner; the explosion's damage
 * ([EXPLODE_MIN] to [EXPLODE_MAX]: the capture notes' "up to 35") with no prayer helping; the shots' landing by the
 * usual NPC flight times; which crack graphic is the straight one and which way each is turned; the figure's options
 * readying at once without the "Begin the challenge?" question; the head icon's sprite (440:0).
 */
object Ocellus {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "ocellus"

    /** `tfa_ocellus_intro`: the hooded figure, a multinpc on `tfa_ocellus_met` (16672 not met, 16673 met). */
    const val INTRO = 16671
    const val INTRO_NOT_MET = 16672
    const val INTRO_MET = 16673

    /** `tfa_ocellus_p1`, his first phase. */
    const val P1 = 16674

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

    /** What he is doing in his first phase. */
    enum class Doing { WALKING, CORNER }

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

        /** Each player's most hitpoints since their last interception, for the heal. */
        val peak = WeakHashMap<Player, Int>()
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    /** The charge of [boss], his first phase, for tests. */
    fun charge(boss: Npc): Int = states[boss]?.charge ?: 0

    /** What he is doing, and at or towards which corner, for tests. */
    fun doing(boss: Npc): Pair<Doing, Int>? = states[boss]?.let { it.doing to it.corner }

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

    /** The fight, or null when the cache hasn't his first phase (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val first = types[P1] ?: return null
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
                        protect(true)
                        boss.effects.say(world.dialogueText.get(OcellusLines.ASCENSION))
                        for (player in players) state.peak[player] = player.hitpoints
                        log("first phase; to the ${cornerName(state.corner)} corner")
                    },
                ),
            ),
            scriptedAttacks = true,
            status = { "Ocellus ${boss.hitpoints}/${boss.maxHitpoints} hp, charge ${state.charge}/$CHARGE_MAX, ${state.doing.name.lowercase()} ${cornerName(state.corner)}" },
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

    /** One tick of his first phase. */
    private fun EncounterContext.firstPhase() {
        // Each player's best since their last interception, for the heal; anyone new to the fight counts from now.
        for (player in players) {
            val best = state.peak[player]
            if (best == null || player.hitpoints > best) state.peak[player] = player.hitpoints
        }
        flowCracks()
        when (state.doing) {
            Doing.WALKING -> walk()
            Doing.CORNER -> atCorner()
        }
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
            if (boss.dead) return
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
            val end = if (crack.tiles.first().let { a -> crack.tiles.last().let { b -> a.x != b.x && a.z != b.z } }) SPOT_CRACK_DIAGONAL_END else SPOT_CRACK_STRAIGHT_END
            for (tile in crack.tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, end)
            val on = players.filter { p -> crack.tiles.any { it.x == p.x && it.z == p.z } }
            if (on.isEmpty()) {
                state.charge = minOf(CHARGE_MAX, state.charge + CHARGE_PER)
                boss.effects.spotanim(0, SPOT_CHARGE, 0)
                log("uncaught: charge ${state.charge}")
            } else {
                for (player in on) intercept(player)
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
        val size = boss.type.size
        val tiles = ((boss.x - out)..(boss.x + size - 1 + out)).flatMap { x -> ((boss.z - out)..(boss.z + size - 1 + out)).map { z -> Tile(x, z) } }
            .filter { Bork.distance(boss.x, boss.z, size, it.x, it.z) == out }
        for (tile in tiles) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_EXPLODE_FLOOR)
        for (player in players) {
            if (boss.dead) return
            if (Bork.distance(boss.x, boss.z, size, player.x, player.z) != out) continue
            val dealt = world.meleeCombat.hitPlayer(player, boss, roll(EXPLODE_MIN, EXPLODE_MAX), world.tick)
            log("explosion ring $out hits ${player.username} for $dealt")
        }
    }
}
