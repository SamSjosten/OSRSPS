package osrsps.game.content

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.combat.SureHit
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Venenatis's fight and Spindel's, its weaker copy (Wilderness step W4.10; the Wiki's Spindel, Venenatis, their
 * Strategies pages, which say the two fights' mechanics are "identical", and Spindel's Spiderling, read 2026-11-15).
 *
 * - **Her cycle**: 8 ranged attacks then 8 magic ones, over and over; anyone she can reach takes her melee instead. She
 *   moves to another spot in her lair every 4 attacks.
 * - **Spiderlings** on the first ranged attack of each cycle: while one lives, her max hits go up and her attacks get
 *   partly through protection prayers; each spiderling's attack drains a prayer point, hit or not.
 * - **Sticky web** on the third magic attack of each cycle: a web flung at the target's tile spreads into a 7 by 5;
 *   whoever stands in it takes 3, loses 3 prayer points and 10% run energy, again and again.
 *
 * GUESSED until recorded: [Config.spiderlings] spiderlings a time, beside her west side (never east, per the Wiki); each living one adds [MAX_PER_SPIDERLING]
 * to her max hits and lets [PASS_PER_SPIDERLING]% through a prayer; the web's [WEB_TICKS] on the floor, a hit every
 * [WEB_HIT_TICKS], and its [WEB_FLIGHT] ticks in the air, its edges and corners turned to face out; her melee taking a
 * slot of the cycle as the attack it stands for would have; where she moves (a random open spot of her lair at least
 * [MOVE_MIN] tiles away), running 2 tiles a tick and not attacking till she is there or [MOVE_TICKS] have passed; the
 * cast and impact graphics by name. Left out: more than 10 players moving her more often, the spiderlings cutting the
 * players' damage, singles-plus combat (the lair is single-way), the teleport delay on entering.
 */
class SpiderQueen(val config: Config) {
    class Config(
        val name: String,
        val boss: Int,
        val spiderling: Int,
        val meleeMax: Int,
        val rangedMax: Int,
        val magicMax: Int,
        val speed: Int,
        val spiderlings: Int,
        val inArena: (Int, Int) -> Boolean,
    )

    private class Fight {
        /** Attacks made: the slot in her 16-attack cycle is this mod 16. */
        var attacks = 0
        var moveTo: Tile? = null
        var moveUntil = -1L
        val spiderlings = ArrayList<Npc>()
        /** Each spiderling's next attack tick as last seen, to drain a point when it swings. */
        val swings = java.util.IdentityHashMap<Npc, Long>()
        val web = HashSet<Tile>()
        val webLocs = ArrayList<ZoneLoc>()
        var webEnd = -1L
        var webNextHit = -1L
    }

    private val fights = java.util.WeakHashMap<Npc, Fight>()

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[config.boss] ?: return null
        return EncounterDef(config.name, type, listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }))), scriptedAttacks = true)
    }

    fun spiderlings(npc: Npc): List<Npc> = fights[npc]?.spiderlings.orEmpty()

    fun web(npc: Npc): Set<Tile> = fights[npc]?.web.orEmpty()

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(boss) { Fight() }
        fight.spiderlings.removeAll { it.dead || it.hidden || world.npcs.all.none { npc -> npc === it } }
        fight.swings.keys.retainAll(fight.spiderlings.toSet())
        drainForSwings(fight)
        web(fight)

        val goal = fight.moveTo
        if (goal != null) {
            repeat(2) { world.npcs.stepTo(boss, goal.x, goal.z) }
            if ((boss.x == goal.x && boss.z == goal.z) || world.tick >= fight.moveUntil) fight.moveTo = null else return
        }

        val target = heldTarget()
        if (target == null) return
        boss.face(target)
        // Stood under, she steps out first, as a chasing NPC does: her fight never has her follow, so nothing else
        // would and they could hold her still (Codex review on #832).
        if (boss.covers(target.x, target.z)) return world.npcs.stepTowards(boss, target)
        if (world.tick < boss.nextAttackTick || !world.meleeCombat.sees(boss, target)) return
        boss.nextAttackTick = world.tick + config.speed
        val slot = fight.attacks % CYCLE
        val alive = fight.spiderlings.size
        val passes = min(100, alive * PASS_PER_SPIDERLING)
        val bonus = alive * MAX_PER_SPIDERLING
        when {
            // The web is a special: standing beside her doesn't trade it for a bite (Codex review on #832).
            slot == WEB_SLOT -> flingWeb(fight, target)
            world.meleeCombat.reaches(boss, target) -> {
                world.meleeCombat.swingAt(boss, target, world.tick, ANIM_MELEE, maxHit = config.meleeMax + bonus, prayerPasses = passes)
                log("bites ${target.username}")
            }
            slot < CYCLE / 2 -> shoot(target, AttackType.RANGED, config.rangedMax + bonus, passes)
            else -> shoot(target, AttackType.MAGIC, config.magicMax + bonus, passes)
        }
        if (slot == 0) callSpiderlings(fight, target)
        fight.attacks++
        if (fight.attacks % MOVE_EVERY == 0) startMove(fight)
    }

    private fun EncounterContext.shoot(target: Player, type: AttackType, max: Int, passes: Int) {
        val shot = if (type == AttackType.RANGED) {
            NpcRangedAttack(anim = ANIM_RANGED, projectile = SPOT_RANGED, impact = SPOT_RANGED_IMPACT, maxHit = max)
        } else {
            NpcRangedAttack(anim = ANIM_MAGIC, projectile = SPOT_MAGIC, impact = SPOT_MAGIC_IMPACT, maxHit = max)
        }
        world.meleeCombat.fireAt(boss, target, world.tick, shot, type, passes)
        log("${if (type == AttackType.RANGED) "spits" else "casts"} at ${target.username} (max $max, $passes% through prayer)")
    }

    private fun EncounterContext.callSpiderlings(fight: Fight, target: Player) {
        val type = world.npcTypes[config.spiderling] ?: return
        val size = boss.type.size
        // Never east of her (the Wiki's strategy page, Codex review on #832): her west corners, then the middle of her west side.
        val spots = listOf(Tile(boss.x - 1, boss.z - 1), Tile(boss.x - 1, boss.z + size), Tile(boss.x - 1, boss.z + size / 2))
        var called = 0
        for (at in spots) {
            // No more than ten alive at once, the Wiki's cap (Codex review on #832).
            if (called >= config.spiderlings || fight.spiderlings.size >= MAX_SPIDERLINGS) break
            val spiderling = spawnAdd(type, at.x, at.z) ?: continue
            fight.spiderlings += spiderling
            world.meleeCombat.aggress(spiderling, target, world.tick)
            // Its swings are counted from now: melee runs before the fight's tick, so its first one comes before
            // drainForSwings next looks (Codex review on #832).
            fight.swings[spiderling] = spiderling.nextAttackTick
            called++
        }
        log("calls $called spiderlings")
    }

    private fun EncounterContext.drainForSwings(fight: Fight) {
        for (spiderling in fight.spiderlings) {
            val seen = fight.swings[spiderling]
            val now = spiderling.nextAttackTick
            fight.swings[spiderling] = now
            if (seen == null || now == seen) continue
            val target = heldTarget(spiderling) ?: continue
            drainPrayer(target, 1)
        }
    }

    private fun drainPrayer(player: Player, points: Int) {
        val now = player.skills.currentLevel(Skill.PRAYER)
        player.skills.setCurrentLevel(Skill.PRAYER, max(0, now - points))
    }

    /** The web flung at [target]'s tile, spreading into a 7 by 5 as it lands. */
    private fun EncounterContext.flingWeb(fight: Fight, target: Player) {
        boss.effects.anim(ANIM_MAGIC)
        val centre = Tile(target.x, target.z)
        world.zones.mapAnim(centre.x, centre.z, boss.level, SPOT_WEB_IMPACT, delay = WEB_FLIGHT * 30)
        log("flings a web at ${centre.x},${centre.z}")
        after(WEB_FLIGHT) { spreadWeb(fight, centre) }
    }

    private fun EncounterContext.spreadWeb(fight: Fight, centre: Tile) {
        clearWeb(fight)
        for (dx in -WEB_HALF_WIDTH..WEB_HALF_WIDTH) for (dz in -WEB_HALF_LENGTH..WEB_HALF_LENGTH) {
            val at = Tile(centre.x + dx, centre.z + dz)
            if (!config.inArena(at.x, at.z)) continue
            fight.web += at
            val east = dx == WEB_HALF_WIDTH
            val west = dx == -WEB_HALF_WIDTH
            val north = dz == WEB_HALF_LENGTH
            val south = dz == -WEB_HALF_LENGTH
            val (id, rotation) = when {
                (west || east) && (north || south) -> WEB_CORNER to when {
                    west && north -> 0
                    east && north -> 1
                    east -> 2
                    else -> 3
                }
                north -> WEB_EDGE to 0
                east -> WEB_EDGE to 1
                south -> WEB_EDGE to 2
                west -> WEB_EDGE to 3
                else -> WEB_CENTRE to 0
            }
            fight.webLocs += placeLoc(id, at.x, at.z, rotation, LocShape.GROUND_DECOR)
        }
        fight.webEnd = world.tick + WEB_TICKS
        fight.webNextHit = world.tick
        log("web over ${fight.web.size} tiles")
        // Its first hit on the tick it lands: the cycle's web() has already run this tick (Codex review on #832).
        web(fight)
    }

    private fun EncounterContext.clearWeb(fight: Fight) {
        for (loc in fight.webLocs) clearLoc(loc)
        fight.webLocs.clear()
        fight.web.clear()
    }

    /** Whoever stands in the web: 3, 3 prayer points and 10% run energy, every [WEB_HIT_TICKS]. */
    private fun EncounterContext.web(fight: Fight) {
        if (fight.web.isEmpty()) return
        if (world.tick >= fight.webEnd) return clearWeb(fight)
        if (world.tick < fight.webNextHit) return
        fight.webNextHit = world.tick + WEB_HIT_TICKS
        for (player in players.toList()) {
            // A hit can end the fight (Retribution killing her); the web hurts no one after that, as hazards don't.
            if (boss.dead || world.npcs[boss.index] !== boss) return
            if (player.dead || player.level != boss.level || Tile(player.x, player.z) !in fight.web) continue
            hit(player, WEB_DAMAGE)
            drainPrayer(player, WEB_PRAYER)
            player.runEnergy = max(0, player.runEnergy - WEB_ENERGY)
            log("${player.username} caught in the web")
        }
    }

    private fun EncounterContext.startMove(fight: Fight) {
        val cx = boss.x
        val cz = boss.z
        repeat(30) {
            val at = Tile(cx + roll(-MOVE_SPAN, MOVE_SPAN), cz + roll(-MOVE_SPAN, MOVE_SPAN))
            if (abs(at.x - cx) + abs(at.z - cz) < MOVE_MIN || !config.inArena(at.x, at.z) || !world.npcs.canStand(boss, at.x, at.z)) return@repeat
            fight.moveTo = at
            fight.moveUntil = world.tick + MOVE_TICKS
            log("moves to ${at.x},${at.z}")
            return
        }
    }

    fun died(world: World, npc: Npc) {
        val fight = fights.remove(npc) ?: return
        for (spiderling in fight.spiderlings) if (!spiderling.dead) world.removeNpc(spiderling)
    }

    fun register(world: World, boss: WildernessBoss) {
        val def = def(world) ?: return
        world.wildernessBosses.register(boss)
        world.npcScripts.onDeath(listOf(config.boss)) { npc -> died(world, npc) }
        // Players hit spiderlings at their max whatever their gear, as the Wiki has it (Codex review on #832).
        val sureHit = world.combat.sureHit
        world.combat.sureHit = { player, npc, type -> if (npc.type.id == config.spiderling) SureHit.MAX else sureHit(player, npc, type) }
        world.npcScripts.onTick(listOf(config.boss)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, config.inArena)
        }
    }

    companion object {
        const val CYCLE = 16
        /** The third magic attack of the cycle. */
        const val WEB_SLOT = 10
        const val MOVE_EVERY = 4
        const val MOVE_SPAN = 8
        const val MOVE_MIN = 4
        const val MOVE_TICKS = 8
        const val MAX_PER_SPIDERLING = 2
        /** The most alive at once (Wiki). */
        const val MAX_SPIDERLINGS = 10
        const val PASS_PER_SPIDERLING = 20

        const val WEB_HALF_WIDTH = 3
        const val WEB_HALF_LENGTH = 2
        const val WEB_FLIGHT = 2
        const val WEB_TICKS = 15
        const val WEB_HIT_TICKS = 2
        const val WEB_DAMAGE = 3
        const val WEB_PRAYER = 3
        /** 10% of the run energy's 10,000. */
        const val WEB_ENERGY = 1_000

        const val WEB_CENTRE = 47084
        const val WEB_EDGE = 47085
        const val WEB_CORNER = 47086

        const val ANIM_RANGED = 9989
        const val ANIM_MAGIC = 9990
        const val ANIM_MELEE = 9991
        const val SPOT_RANGED = 2356
        const val SPOT_RANGED_IMPACT = 2357
        const val SPOT_MAGIC = 2358
        const val SPOT_MAGIC_IMPACT = 2359
        const val SPOT_WEB_IMPACT = 2361
    }
}

/**
 * Spindel and the Web Chasm (Wilderness step W4.10): `venenatis_singles` 11998, size 4, combat 302, 515 hitpoints, every
 * 4 ticks, max hits 14 melee, 31 ranged, 24 magic; her spiderlings `spindel_spiderling` 12001. Her fight is
 * [SpiderQueen]'s.
 *
 * The chasm: Enter on its way in (`wild_venenatis_singles_entrance01` 47078 at 3183,3744, level 29, north of the
 * Graveyard of Shadows) takes the lairs' fee and puts the player in the chasm's north, map square 25_180 on level 2;
 * Peek and Check-Fee as at every lair ([WildernessLairLines]); its way out (`wild_venanatis_exit` 47000 at 1630,11527)
 * puts them back outside. Live asks for the hard Wilderness Diary or a Venenatis task; ours is open (Sam, 9 Nov). Her
 * drops are in npc-drops.json and her kill count in `total_spindel_kills`; she is a lair boss, so her top damage dealer
 * gets 10,000 off the fee.
 *
 * GUESSED: where Enter puts the player in the north (the Wiki: players "appear to the north") and where Exit puts them
 * (south of the way in); where she spawns (the chasm's middle).
 */
object Spindel {
    const val BOSS = 11998
    const val SPIDERLING = 12001
    const val KILL_COUNT_VARP = 3762

    const val ENTRANCE = 47078
    const val EXIT = 47000
    const val ENTER = 1
    const val PEEK = 2
    const val CHECK_FEE = 3
    const val PEEK_KILLS = 20
    const val CHASM_LEVEL = 2

    val ARRIVAL = Tile(1631, 11557)
    val OUTSIDE = Tile(3184, 3743)
    val SPAWN = Tile(1630, 11545)

    /** The chasm: its map square. */
    fun inChasm(x: Int, z: Int): Boolean = x shr 6 == 25 && z shr 6 == 180

    val FIGHT = SpiderQueen(
        SpiderQueen.Config("spindel", BOSS, SPIDERLING, meleeMax = 14, rangedMax = 31, magicMax = 24, speed = 4, spiderlings = 2, inArena = ::inChasm),
    )
}

fun registerSpindel(world: World) {
    with(Spindel) {
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, CHASM_LEVEL)
        }
        world.handlers.onLoc(ENTRANCE, PEEK) {
            arriveDelay()
            if (KILL_COUNT_VARP < player.vars.types.varpCount && player.vars[KILL_COUNT_VARP] < PEEK_KILLS) {
                return@onLoc world.message(player, WildernessLairLines.PEEK_KILLS)
            }
            world.message(player, WildernessLairLines.PEEK, "count" to world.onlinePlayers.count { inChasm(it.x, it.z) && it.level == CHASM_LEVEL })
        }
        world.handlers.onLoc(ENTRANCE, CHECK_FEE) {
            arriveDelay()
            if (WildernessLairFee.paid(player) || WildernessLairFee.fee(player) == 0) world.message(player, WildernessLairLines.FEE_PAID)
            else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
        }
        // The Silk Chasm has the same way out; Venenatis's step gives it its own place to put the player.
        world.handlers.onLoc(EXIT, 1) { op ->
            val loc = op.loc!!
            if (!inChasm(loc.x, loc.z)) return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        }
        FIGHT.register(world, WildernessBoss("spindel", setOf(BOSS), KILL_COUNT_VARP, lair = true))
    }
}
