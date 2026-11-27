package osrsps.game.content

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.map.Tile

/** The Corporeal Beast's lair's words (keys only; the words are in data/dialogue.txt). */
object CorporealBeastLines : Lines("corporeal_beast", "Corporeal Beast") {
    val PEEK = line("peek", "Peek on the Passage to the Corporeal Beast: how many players are in its room (the Wiki's Changes: 25 June 2015; no words yet)", "count")
    val IN_COMBAT = line("in_combat", "Enter on the cave in the Wilderness while in combat (the Wiki's Corporeal Beast's Lair: players can't; no words yet)")
}

/**
 * The Corporeal Beast and its lair (Wilderness step W4.15; the Wiki's Corporeal Beast, its Strategies page, Corpbane
 * weapons and the Corporeal Beast's Lair, read 2026-11-15): `corp_beast` 319, size 5, combat 785, 2,000 hitpoints,
 * aggressive, every 4 ticks.
 *
 * - **The lair** (map square 46_66 on level 2, not in the Wilderness, multi-way): Enter on the cave in the Wilderness
 *   (`corp_cave_entrance` 678, 5 by 5 at 3201,3679, level 20) puts the player in the safe room, unless they are in
 *   combat; the Cave exit (`corp_cave_exit` 679 at 2963,4254) puts them back outside. The Passage (`corp_beast_entrance`
 *   677, 3 by 5 at 2971,4254) takes the player through to the other side; Peek says how many are in its room.
 * - **Its attacks**: beside its target, melee (up to [MELEE_MAX], crush) 2 attacks in 5 and each magic one 1 in 5; out
 *   of reach each magic one 1 in 3. Protect from Melee blocks the melee; Protect from Magic takes only a third off the
 *   magic. Its magic: a plain bolt up to [MAGIC_MAX]; a draining one up to [DRAIN_MAX] that, when it hits, lowers Magic or
 *   Prayer by 1 or 2 and heals it half the damage; and a splash at the target's tile that hits up to 40 there and 30
 *   beside, then breaks into six that land in the 7 by 7 around it, each up to 30 on its tile and 20 beside.
 * - **The stomp**: every [STOMP_TICKS], everyone under it takes 30 to 51, no prayer blocking it.
 * - **Its armour**: every hit but magic and a stab from a corpbane weapon (the cache's param 701: spears, halberds,
 *   Osmumten's fang and the like) does half ([cut]).
 * - **Healing**: drained stats come back a level every [RESTORE_TICKS] (NpcDrains.restoreTicksByType); with no one in its room it heals 75 every
 *   [STOMP_TICKS] and 10 more each time after; left alone [RESET_TICKS] it is whole again; with 8 or more players in its
 *   room it heals 5 for each every [STOMP_TICKS].
 * - Only its top damage dealer gets its loot and the kill count (`total_corp_kills`), as [WildernessBosses] gives a
 *   [WildernessBoss.topDamage] boss.
 *
 * GUESSED until recorded: the splash's flight ([SPLASH_FLIGHT] ticks, then [SPLASH_SPREAD] more for the six) and every
 * splash hit rolling 0 to its max without an accuracy roll; which projectile and animation go with which spell (by
 * name: the strong one for the plain bolt, the mid one for the drain, the weak one and corp_magic_splash for the splash);
 * where the cave and the exit put the player; the Passage's tiles either side. Left out: the dark energy core (step
 * W4.15b); the attack sometimes coming a tick early; the ironman room, the 200,000-coin instance, the brazier; no pets
 * in the lair; the 100 cap on a corpbane hit.
 */
object CorporealBeast {
    const val NAME = "corporeal beast"
    const val BOSS = 319
    const val KILL_COUNT_VARP = 1517

    const val ENTRANCE = 678
    const val EXIT = 679
    const val PASSAGE = 677
    const val ENTER = 1
    const val GO_THROUGH = 1
    const val PEEK = 2
    const val LAIR_LEVEL = 2

    val OUTSIDE = Tile(3202, 3678)
    val ARRIVAL = Tile(2965, 4254)
    val SPAWN = Tile(2993, 4254)
    /** The Passage's west and east sides, and the rows it spans. */
    const val PASSAGE_WEST_X = 2970
    const val PASSAGE_EAST_X = 2974
    val PASSAGE_ROWS = 4254..4258

    /** Its room, east of the Passage. */
    fun inRoom(x: Int, z: Int): Boolean = x in PASSAGE_EAST_X..3000 && z in 4240..4270

    const val MELEE_MAX = 33
    const val MAGIC_MAX = 65
    const val DRAIN_MAX = 55
    /** Protect from Magic takes a third off its magic. */
    const val MAGIC_PASSES = 67
    const val SPLASH_MAX = 40
    const val SPLASH_BESIDE_MAX = 30
    const val SHARD_MAX = 30
    const val SHARD_BESIDE_MAX = 20
    const val SHARDS = 6
    const val SHARD_SPREAD = 3
    const val SPLASH_FLIGHT = 3
    const val SPLASH_SPREAD = 2
    const val STOMP_TICKS = 7
    const val STOMP_MIN = 30
    const val STOMP_MAX = 51
    const val RESTORE_TICKS = 20
    const val EMPTY_HEAL = 75
    const val EMPTY_HEAL_STEP = 10
    const val CROWD = 8
    const val CROWD_HEAL = 5
    /** Three minutes. */
    const val RESET_TICKS = 300

    /** Item param 701 (`corpbane`), 1 on the weapons that hit it in full on stab. */
    const val PARAM_CORPBANE = 701

    const val ANIM_MELEE = 1683
    const val ANIM_STOMP = 1682
    const val ANIM_DRAIN = 1679
    const val ANIM_MAGIC = 1680
    const val ANIM_SPLASH = 1681
    const val SPOT_SPLASH = 314
    const val SPOT_DRAIN = 315
    const val SPOT_MAGIC = 316
    const val SPOT_STOMP = 318
    const val SPOT_SHARD = 1836

    private class Fight {
        var nextStomp = Long.MIN_VALUE
        var emptyHeals = 0
        var lastHitpoints = -1
        var lastFought = Long.MIN_VALUE
    }

    private val fights = java.util.WeakHashMap<Npc, Fight>()

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef("corporeal_beast", type, listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }))), scriptedAttacks = true)
    }

    /**
     * What [damage] of a hit of [type] with [weapon] does to it: all of a spell or a stab from a corpbane weapon, half of the
     * rest (the Wiki: "a 50% damage reduction against any melee and ranged weapon that is not a Corpbane weapon").
     */
    fun cut(weapon: ObjType?, damage: Int, type: AttackType): Int {
        if (type == AttackType.MAGIC) return damage
        if (type == AttackType.STAB && weapon?.intParam(PARAM_CORPBANE) == 1) return damage
        return damage / 2
    }

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(boss) { Fight().also { it.lastHitpoints = boss.hitpoints; it.lastFought = world.tick } }
        if (boss.hitpoints < fight.lastHitpoints) fight.lastFought = world.tick
        heal(fight)
        fight.lastHitpoints = boss.hitpoints

        val target = heldTarget()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        fight.lastFought = world.tick
        boss.face(target)
        val reaches = world.meleeCombat.reaches(boss, target)
        if (reaches) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        if (world.tick < boss.nextAttackTick || !world.meleeCombat.sees(boss, target)) return
        boss.nextAttackTick = world.tick + world.combat.npcCombat[boss.type]!!.attackSpeed
        val pick = if (reaches) roll(1, 5) else roll(3, 5)
        when (pick) {
            1, 2 -> {
                world.meleeCombat.swingAt(boss, target, world.tick, ANIM_MELEE, maxHit = MELEE_MAX)
                log("swipes at ${target.username}")
            }
            3 -> {
                world.meleeCombat.fireAt(boss, target, world.tick, NpcRangedAttack(anim = ANIM_MAGIC, projectile = SPOT_MAGIC, maxHit = MAGIC_MAX), AttackType.MAGIC, MAGIC_PASSES)
                log("casts at ${target.username}")
            }
            4 -> drainingBolt(target)
            else -> splash(target)
        }
    }

    /** Its stomp and its healing, on their timers; its stats come back by NpcDrains, at [RESTORE_TICKS]. */
    private fun EncounterContext.heal(fight: Fight) {
        val now = world.tick
        if (now - fight.lastFought >= RESET_TICKS && boss.hitpoints < boss.maxHitpoints) {
            boss.hitpoints = boss.maxHitpoints
            boss.drained.fill(0)
            log("whole again")
        }
        if (now < fight.nextStomp) return
        val first = fight.nextStomp == Long.MIN_VALUE
        fight.nextStomp = now + STOMP_TICKS
        if (first) return
        val inRoom = players.filter { !it.dead && it.level == boss.level }
        val under = inRoom.filter { boss.covers(it.x, it.z) }
        if (under.isNotEmpty()) {
            boss.effects.anim(ANIM_STOMP)
            boss.effects.spotanim(0, SPOT_STOMP, 0)
            for (player in under) {
                val dealt = hit(player, roll(STOMP_MIN, STOMP_MAX))
                log("stomps on ${player.username} for $dealt")
            }
        }
        val healed = when {
            inRoom.isEmpty() -> EMPTY_HEAL + EMPTY_HEAL_STEP * fight.emptyHeals++
            inRoom.size >= CROWD -> CROWD_HEAL * inRoom.size
            else -> 0
        }
        if (inRoom.isNotEmpty()) fight.emptyHeals = 0
        if (healed > 0 && boss.hitpoints < boss.maxHitpoints) {
            boss.hitpoints = min(boss.maxHitpoints, boss.hitpoints + healed)
            log("heals $healed")
        }
    }

    /** The draining bolt: when it hits, Magic or Prayer down 1 or 2 and half the damage back to it. */
    private fun EncounterContext.drainingBolt(target: Player) {
        val world = world
        val boss = boss
        val shot = NpcRangedAttack(anim = ANIM_DRAIN, projectile = SPOT_DRAIN, maxHit = DRAIN_MAX)
        world.meleeCombat.fireAt(boss, target, world.tick, shot, AttackType.MAGIC, MAGIC_PASSES) { dealt ->
            if (dealt <= 0) return@fireAt
            val skill = if (world.random.nextInt(2) == 0) Skill.MAGIC else Skill.PRAYER
            val by = 1 + world.random.nextInt(2)
            target.skills.setCurrentLevel(skill, max(0, target.skills.currentLevel(skill) - by))
            if (!boss.dead) boss.hitpoints = min(boss.maxHitpoints, boss.hitpoints + dealt / 2)
        }
        log("drains at ${target.username}")
    }

    /** The splash at [target]'s tile, breaking into [SHARDS] that land around it. */
    private fun EncounterContext.splash(target: Player) {
        boss.effects.anim(ANIM_SPLASH)
        boss.face(target)
        val centre = Tile(target.x, target.z)
        val level = boss.level
        val middle = boss.x + boss.type.size / 2
        val centreZ = boss.z + boss.type.size / 2
        world.zones.projectile(projectile(SPOT_SPLASH, middle, centreZ, level, centre, SPLASH_FLIGHT))
        log("splashes at ${centre.x},${centre.z}")
        after(SPLASH_FLIGHT) {
            land(centre, SPLASH_MAX, SPLASH_BESIDE_MAX)
            val shards = List(SHARDS) { Tile(centre.x + roll(-SHARD_SPREAD, SHARD_SPREAD), centre.z + roll(-SHARD_SPREAD, SHARD_SPREAD)) }
            for (at in shards) world.zones.projectile(projectile(SPOT_SHARD, centre.x, centre.z, level, at, SPLASH_SPREAD))
            log("shards on ${shards.joinToString(" ") { "${it.x},${it.z}" }}")
            after(SPLASH_SPREAD) { for (at in shards) land(at, SHARD_MAX, SHARD_BESIDE_MAX) }
        }
    }

    /** Everyone on [at] or beside it: up to [onMax] on it, [besideMax] beside, a third off under Protect from Magic. */
    private fun EncounterContext.land(at: Tile, onMax: Int, besideMax: Int) {
        for (player in players.toList()) {
            if (player.dead || player.level != boss.level) continue
            val dx = abs(player.x - at.x)
            val dz = abs(player.z - at.z)
            if (dx > 1 || dz > 1) continue
            val rolled = roll(0, if (dx == 0 && dz == 0) onMax else besideMax)
            val damage = if (world.prayers.protects(player, AttackType.MAGIC)) rolled * MAGIC_PASSES / 100 else rolled
            val dealt = hit(player, damage)
            log("splash on ${player.username} for $dealt")
        }
    }

    private fun projectile(id: Int, fromX: Int, fromZ: Int, level: Int, to: Tile, ticks: Int) = Projectile(
        id = id, fromX = fromX, fromZ = fromZ, level = level, toX = to.x, toZ = to.z,
        startHeight = 43, endHeight = 0, startTime = 0, endTime = ticks * 30, angle = 16, progress = 64,
    )

    fun register(world: World) {
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss(NAME, setOf(BOSS), KILL_COUNT_VARP, topDamage = true))
        world.npcScripts.onDeath(listOf(BOSS)) { npc -> fights.remove(npc) }
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, ::inRoom)
        }
    }
}

fun registerCorporealBeast(world: World) {
    with(CorporealBeast) {
        world.meleeCombat.damageCuts[BOSS] = { player, damage, type -> cut(world.combat.weapon(player), damage, type) }
        world.npcDrains.restoreTicksByType[BOSS] = RESTORE_TICKS
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            if (world.inCombat(player)) return@onLoc world.message(player, CorporealBeastLines.IN_COMBAT)
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, LAIR_LEVEL)
        }
        world.handlers.onLoc(EXIT, 1) {
            arriveDelay()
            world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        }
        world.handlers.onLoc(PASSAGE, GO_THROUGH) {
            arriveDelay()
            val z = player.z.coerceIn(PASSAGE_ROWS)
            val x = if (player.x <= PASSAGE_WEST_X) PASSAGE_EAST_X else PASSAGE_WEST_X
            world.teleport(player, x, z, LAIR_LEVEL)
        }
        world.handlers.onLoc(PASSAGE, PEEK) {
            arriveDelay()
            world.message(player, CorporealBeastLines.PEEK, "count" to world.onlinePlayers.count { it.level == LAIR_LEVEL && inRoom(it.x, it.z) })
        }
        register(world)
    }
}
