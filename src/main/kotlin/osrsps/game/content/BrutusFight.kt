package osrsps.game.content

import java.util.WeakHashMap
import kotlin.math.abs
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.map.Tile

/**
 * Brutus's fight (Brutus step 2; the OSRS Wiki's Brutus, Brutus/Strategies and Transcript:Brutus, read 2026-11-19;
 * animations and graphics from the 242 cache by name).
 *
 * - **His butt** (crush, max 3, every 5 ticks: the NPC file's), which Protect from Melee stops.
 * - **A special after four to five butts** ("After four to five basic attacks, Brutus will use one of two special
 *   attacks; which one he chooses initially is random, but it will alternate between the two afterward"). Both deal
 *   15 to 19 to a player with the Hitpoints for it, less to a low-levelled one ("Low level players can expect to be hit
 *   for around 30-40% of their health"), through any protection prayer.
 * - **Charge**: "*growls*", then a 4-tile dash the way he faces after "a 3 tick (1.8s) window to move out of the way",
 *   hurting whoever he runs over. Never into a wall ("Brutus will not use this attack if charging would result in him
 *   hitting a wall"): he stomps instead.
 * - **Stomp**: "*snorts*", then a stomp landing 4 ticks on ("There is a 4 tick (2.4s) window"), 1 to 3 times ("Normally
 *   stomps between 1 and 3 times"), each on the 3 tiles along the side the player is beside, or the corner's L when
 *   they stand diagonal to him, and the tiles of his own under it ("Both slam attacks also deal damage on adjacent
 *   tiles beneath Brutus").
 * - **Safespotted one tile away**, he stomps ("he now uses his stomp special attack if he's safespotted while the
 *   player is only one tile away", the Wiki's 4 March 2026 change).
 * - **\*huff\***: out of his reach for [OUT_OF_REACH_TICKS] ticks ("if the player is out of Brutus' attack range for 15
 *   ticks"), he charges through whatever is between them ("He can pass through obstacles with this charge").
 *
 * GUESSED until recorded (each marked where it is used): the specials' damage as [SPECIAL_MIN_PERCENT] to
 * [SPECIAL_MAX_PERCENT] of the player's Hitpoints level, capped at [SPECIAL_MIN] to [SPECIAL_MAX]; the charge's dash
 * as two tiles a tick on the third and fourth ticks after the growl, his charge animation's 4 ticks; each stomp aimed
 * where the player stands as it starts, the stomps one after another every [STOMP_TICKS] ticks (his stomp animation's
 * length) with the cache's three impact graphics in turn, and the tiles under him being his edge on that side (the
 * corner tile for the L); his next butt [ATTACK_SPEED] ticks after a special ends; the *huff* charge as his fading out
 * (`cow_boss_charge_start_fade_out`, 3 ticks), landing beside the player and fading in (`cow_boss_charge_end_fade_in`),
 * hurting nobody; the safespot stomp after [STUCK_TICKS] ticks held in place, when his next attack is due, outside
 * the specials' turns. Left out: his moving round an obstacle instead of charging through it after the *huff* ("can either
 * charge towards the player through obstacles or move around them"), and his "Moo" (no trigger known).
 */
object BrutusFight {
    const val ATTACK_SPEED = 5
    const val BASIC_MAX = 3
    const val BASICS_MIN = 4
    const val BASICS_MAX = 5

    // Guessed: the Wiki's 15 to 19 and its "30-40% of their health" for low levels, as one rule.
    const val SPECIAL_MIN = 15
    const val SPECIAL_MAX = 19
    const val SPECIAL_MIN_PERCENT = 30
    const val SPECIAL_MAX_PERCENT = 40

    const val CHARGE_WINDOW = 3
    const val CHARGE_TILES = 4
    const val STOMP_TICKS = 4
    const val STOMPS_MIN = 1
    const val STOMPS_MAX = 3
    const val OUT_OF_REACH_TICKS = 15
    // Guessed: how far from the player he may land when there is no room beside them.
    const val NEAR_TILES = 3
    // Guessed: held two ticks with the player one tile off before the safespot stomp.
    const val STUCK_TICKS = 2
    const val FADE_OUT_TICKS = 3
    const val FADE_IN_TICKS = 2

    const val ANIM_CHARGE = 13778
    const val ANIM_ATTACK = 13783
    const val ANIM_STOMP = 13785
    const val ANIM_FADE_OUT = 13795
    const val ANIM_FADE_IN = 13797
    val SPOT_STOMP = listOf(3588, 3589, 3590)

    enum class Special { CHARGE, STOMP }

    /** One fight's state, by boss. */
    class Fight {
        var basicsLeft = -1
        var last: Special? = null
        /** The tick his special (or *huff*) is over; he does nothing else until then. */
        var busyUntil = -1L
        var outOfReach = 0
        /** Each special so far, for tests. */
        val specials = ArrayList<Special>()
        var huffs = 0
        /** Stomps on a player he couldn't land beside after a *huff*. */
        var farStomps = 0
        var safespotStomps = 0
        /** Ticks he has stood where he stood, out of reach; where he stood the tick before. */
        var stuck = 0
        var lastX = -1
        var lastZ = -1
    }

    private val fights = WeakHashMap<Npc, Fight>()

    fun fight(boss: Npc): Fight = fights.getOrPut(boss) { Fight() }

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[Brutus.BOSS] ?: return null
        return EncounterDef(
            "brutus", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = { boss.unleashed = true })),
            scriptedAttacks = true,
        )
    }

    /** The damage range of his specials against [player], from their Hitpoints level. */
    fun specialDamage(player: Player): IntRange {
        val level = player.skills.baseLevel(Skill.HITPOINTS)
        val max = minOf(SPECIAL_MAX, level * SPECIAL_MAX_PERCENT / 100).coerceAtLeast(1)
        val min = minOf(SPECIAL_MIN, level * SPECIAL_MIN_PERCENT / 100).coerceIn(1, max)
        return min..max
    }

    private fun EncounterContext.tick() {
        val fight = fight(boss)
        if (fight.basicsLeft < 0) fight.basicsLeft = roll(BASICS_MIN, BASICS_MAX)
        if (world.tick < fight.busyUntil) {
            // The 15 ticks out of reach run on through a special too, so running off while he stomps doesn't put off
            // the *huff*; it comes once the special is over.
            val held = heldTarget()
            if (held != null) fight.outOfReach = if (world.meleeCombat.reaches(boss, held)) 0 else fight.outOfReach + 1
            return
        }
        val target = heldTarget() ?: nearest() ?: return
        boss.face(target)
        val reaches = world.meleeCombat.reaches(boss, target)
        if (!reaches) {
            fight.stuck = if (boss.x == fight.lastX && boss.z == fight.lastZ) fight.stuck + 1 else 0
            fight.lastX = boss.x
            fight.lastZ = boss.z
            world.npcs.follow(boss, target)
            if (++fight.outOfReach >= OUT_OF_REACH_TICKS) return huff(fight, target)
            if (fight.stuck >= STUCK_TICKS && oneTileAway(target) && world.tick >= boss.nextAttackTick) {
                // Not one of his turns: the specials' order and the butts before the next are left as they were.
                fight.outOfReach = 0
                fight.stuck = 0
                fight.safespotStomps++
                log("safespotted one tile away")
                stomp(fight, target)
            }
            return
        }
        fight.outOfReach = 0
        fight.stuck = 0
        world.npcs.stopFollowing(boss)
        if (world.tick < boss.nextAttackTick) return
        if (fight.basicsLeft > 0) {
            world.meleeCombat.swingAt(boss, target, world.tick, ANIM_ATTACK)
            fight.basicsLeft--
            log("butts ${target.username}")
            return
        }
        val pick = when (fight.last) {
            null -> if (roll(0, 1) == 0) Special.CHARGE else Special.STOMP
            Special.CHARGE -> Special.STOMP
            Special.STOMP -> Special.CHARGE
        }
        val done = if (pick == Special.CHARGE && charge(fight, target)) Special.CHARGE else stomp(fight, target)
        fight.last = done
        fight.specials += done
        fight.basicsLeft = roll(BASICS_MIN, BASICS_MAX)
    }

    /** The nearest living player in his field, whom he takes on. */
    private fun EncounterContext.nearest(): Player? {
        val pick = players.filter { !it.dead }.minByOrNull { abs(it.x - boss.x) + abs(it.z - boss.z) } ?: return null
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        return pick
    }

    /** Which way [player] is from him: -1, 0 or 1 on each axis, 0 where they are within his width. */
    private fun EncounterContext.side(player: Player): Pair<Int, Int> {
        val size = boss.type.size
        val sx = if (player.x < boss.x) -1 else if (player.x >= boss.x + size) 1 else 0
        val sz = if (player.z < boss.z) -1 else if (player.z >= boss.z + size) 1 else 0
        return sx to sz
    }

    /** Whether [player] stands one tile off his footprint, a corner's diagonal included. */
    private fun EncounterContext.oneTileAway(player: Player): Boolean {
        val size = boss.type.size
        return player.x in boss.x - 1..boss.x + size && player.z in boss.z - 1..boss.z + size && !covers(player)
    }

    private fun EncounterContext.covers(player: Player): Boolean {
        val size = boss.type.size
        return player.x in boss.x until boss.x + size && player.z in boss.z until boss.z + size
    }

    /** Charge: false, doing nothing, when the dash would take him into a wall or the player is under him. */
    private fun EncounterContext.charge(fight: Fight, target: Player): Boolean {
        val (dx, dz) = side(target)
        if (dx == 0 && dz == 0) return false
        if (!world.npcs.clearLine(boss, dx, dz, CHARGE_TILES)) return false
        boss.effects.say(world.dialogueText[BrutusLines.GROWLS])
        boss.effects.anim(ANIM_CHARGE)
        world.npcs.stopFollowing(boss)
        val endX = boss.x + dx * CHARGE_TILES
        val endZ = boss.z + dz * CHARGE_TILES
        val hit = HashSet<Player>()
        // Guessed: two tiles a tick, on the third and fourth ticks after the growl.
        val dash: EncounterContext.() -> Unit = {
            repeat(CHARGE_TILES / 2) {
                if (!boss.dead && world.npcs.stepTo(boss, endX, endZ)) {
                    for (player in players) {
                        if (!player.dead && covers(player) && hit.add(player)) {
                            val range = specialDamage(player)
                            hit(player, roll(range.first, range.last))
                        }
                    }
                }
            }
        }
        after(CHARGE_WINDOW, dash)
        after(CHARGE_WINDOW + 1, dash)
        val end = world.tick + CHARGE_WINDOW + 1
        fight.busyUntil = end + 1
        // Guessed: his attack speed after the special ends.
        boss.nextAttackTick = end + ATTACK_SPEED
        log("charges ${directionName(dx, dz)} at ${target.username}")
        return true
    }

    /** Stomp: 1 to 3 stomps, each aimed where the player stands as it starts. Always made. */
    private fun EncounterContext.stomp(fight: Fight, target: Player): Special {
        boss.effects.say(world.dialogueText[BrutusLines.SNORTS])
        world.npcs.stopFollowing(boss)
        // Guessed: 1 to 3 at even odds.
        val count = roll(STOMPS_MIN, STOMPS_MAX)
        stompAt(target, 0)
        for (i in 1 until count) {
            after(STOMP_TICKS * i) { if (!boss.dead) stompAt(target, i) }
        }
        val end = world.tick + STOMP_TICKS * count
        fight.busyUntil = end
        boss.nextAttackTick = end + ATTACK_SPEED
        log("stomps $count times at ${target.username}")
        return Special.STOMP
    }

    private fun EncounterContext.stompAt(target: Player, nth: Int) {
        boss.effects.anim(ANIM_STOMP)
        if (!target.dead) boss.face(target)
        val tiles = stompTiles(boss.x, boss.z, boss.type.size, side(target))
        val range = specialDamage(target)
        hazard(tiles, -1, STOMP_TICKS, 1, range.first, range.last, SPOT_STOMP[nth % SPOT_STOMP.size])
    }

    /**
     * The tiles a stomp lands on with his south-west tile on [x], [z]: toward [side] (from [side]'s player), the 3
     * beside that side and his own edge under it, or for a corner the L round it and his corner tile. A player under him
     * gets the side south of him (guessed).
     */
    fun stompTiles(x: Int, z: Int, size: Int, side: Pair<Int, Int>): List<Tile> {
        val (sx, sz) = if (side == (0 to 0)) 0 to -1 else side
        val outX = if (sx < 0) x - 1 else x + size
        val outZ = if (sz < 0) z - 1 else z + size
        val edgeX = if (sx < 0) x else x + size - 1
        val edgeZ = if (sz < 0) z else z + size - 1
        return when {
            sx != 0 && sz != 0 -> listOf(Tile(outX, outZ), Tile(outX - sx, outZ), Tile(outX, outZ - sz), Tile(edgeX, edgeZ))
            sx != 0 -> (z until z + size).flatMap { tz -> listOf(Tile(outX, tz), Tile(edgeX, tz)) }
            else -> (x until x + size).flatMap { tx -> listOf(Tile(tx, outZ), Tile(tx, edgeZ)) }
        }
    }

    /** *huff*: out of reach too long, he fades out and comes back beside the player, through anything in between. */
    private fun EncounterContext.huff(fight: Fight, target: Player) {
        fight.outOfReach = 0
        boss.effects.say(world.dialogueText[BrutusLines.HUFF])
        boss.effects.anim(ANIM_FADE_OUT)
        world.npcs.stopFollowing(boss)
        fight.huffs++
        fight.busyUntil = world.tick + FADE_OUT_TICKS + FADE_IN_TICKS
        log("huffs at ${target.username}")
        after(FADE_OUT_TICKS) {
            if (boss.dead || target.dead || players.none { it === target }) return@after
            // Guessed: with no room beside the player (a tile hemmed in by fences or troughs), he lands as near as he
            // can, and if he still can't reach them he stomps them where they stand, so no tile keeps a player out of
            // his reach for good (exploit review round 56, R56A-2).
            val at = landing(target) ?: nearby(target)
            if (at != null) {
                world.npcs.teleport(boss, at.x, at.z)
                boss.effects.anim(ANIM_FADE_IN)
                boss.face(target)
                log("lands at ${at.x},${at.z}")
            } else {
                log("finds no room beside ${target.username}")
            }
            if (!world.meleeCombat.reaches(boss, target)) farStomp(fight, target)
        }
    }

    /** Where he lands with no room beside [target]: a tile he can stand on, his body within [NEAR_TILES] of them. */
    private fun EncounterContext.nearby(target: Player): Tile? {
        val size = boss.type.size
        val candidates = ArrayList<Tile>()
        for (x in target.x - size - NEAR_TILES + 1..target.x + NEAR_TILES) {
            for (z in target.z - size - NEAR_TILES + 1..target.z + NEAR_TILES) {
                val inside = target.x in x until x + size && target.z in z until z + size
                if (!inside && world.npcs.canStand(boss, x, z)) candidates += Tile(x, z)
            }
        }
        return candidates.minWithOrNull(compareBy<Tile> { gap(it, target) }.thenBy { abs(it.x - boss.x) + abs(it.z - boss.z) })
    }

    /** Tiles between his body with its south-west tile on [at] and [target]: 0 beside them. */
    private fun EncounterContext.gap(at: Tile, target: Player): Int {
        val size = boss.type.size
        val dx = maxOf(at.x - target.x, target.x - (at.x + size - 1), 0)
        val dz = maxOf(at.z - target.z, target.z - (at.z + size - 1), 0)
        return maxOf(dx, dz) - 1
    }

    /** Out of reach after a *huff*: one stomp that lands on [target] where they stand, a special's damage. */
    private fun EncounterContext.farStomp(fight: Fight, target: Player) {
        boss.effects.say(world.dialogueText[BrutusLines.SNORTS])
        boss.effects.anim(ANIM_STOMP)
        world.zones.mapAnim(target.x, target.z, target.level, SPOT_STOMP[0])
        val range = specialDamage(target)
        hit(target, roll(range.first, range.last))
        fight.farStomps++
        fight.busyUntil = maxOf(fight.busyUntil, world.tick + STOMP_TICKS)
        boss.nextAttackTick = world.tick + STOMP_TICKS + ATTACK_SPEED
        log("stomps ${target.username} from afar")
    }

    /** Where he lands beside [target]: a tile he can stand on and reach them from, the nearest to him. */
    private fun EncounterContext.landing(target: Player): Tile? {
        val size = boss.type.size
        val candidates = ArrayList<Tile>()
        for (offset in 0 until size) {
            candidates += Tile(target.x - size, target.z - offset)
            candidates += Tile(target.x + 1, target.z - offset)
            candidates += Tile(target.x - offset, target.z - size)
            candidates += Tile(target.x - offset, target.z + 1)
        }
        return candidates
            .filter { world.npcs.canStand(boss, it.x, it.z) && world.meleeCombat.reachesFrom(boss, it.x, it.z, target) }
            .minByOrNull { abs(it.x - boss.x) + abs(it.z - boss.z) }
    }

    private fun directionName(dx: Int, dz: Int): String =
        (if (dz > 0) "north" else if (dz < 0) "south" else "") + (if (dx > 0) "east" else if (dx < 0) "west" else "")
}

fun registerBrutusFight(world: World) {
    val def = BrutusFight.def(world) ?: return
    world.npcScripts.onTick(listOf(Brutus.BOSS)) { npc ->
        if (npc.dead || world.encounters.all.any { it.boss === npc && !it.ended }) return@onTick
        // His own field only: the instance he is in.
        val run = world.brutusPens.runAt(npc.x, npc.z) ?: return@onTick
        world.encounters.start(def, npc, { x, z -> world.brutusPens.runAt(x, z) === run })
    }
    world.npcScripts.onDeath(listOf(Brutus.BOSS)) { boss -> BrutusFight.fight(boss).apply { basicsLeft = -1; last = null; busyUntil = -1; outOfReach = 0 } }
}
