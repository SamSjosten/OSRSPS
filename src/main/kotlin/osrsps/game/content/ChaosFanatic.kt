package osrsps.game.content

import kotlin.math.abs
import osrsps.game.EquipResult
import osrsps.game.Player
import osrsps.game.WearPos
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
import osrsps.map.LineOfSight
import osrsps.map.Tile

/** The Chaos Fanatic's words (keys only; the words are in data/dialogue.txt). */
object ChaosFanaticLines : Lines("chaos_fanatic", "Chaos Fanatic") {
    val BURN = line("burn", "one of his six overhead shouts as he attacks (Transcript:Chaos Fanatic)")
    val WEUGH = line("weugh", "one of his six overhead shouts as he attacks")
    val OXEN = line("oxen", "one of his six overhead shouts as he attacks")
    val WILDERNESS = line("wilderness", "one of his six overhead shouts as he attacks")
    val LAUGH = line("laugh", "one of his six overhead shouts as he attacks")
    val SQUIDGY = line("squidgy", "one of his six overhead shouts as he attacks")

    val SHOUTS = listOf(BURN, WEUGH, OXEN, WILDERNESS, LAUGH, SQUIDGY)
}

/**
 * The Chaos Fanatic (Wilderness step W4.3; the Wiki's Chaos Fanatic, its Strategies page and its transcript, read
 * 2026-11-15): `chaos_fanatic` 6619 west of the Lava Maze at level 42, single-way, combat 202, 225 hitpoints, aggressive,
 * every 2 ticks. His attack is a magic bolt (max 21) that Protect from Magic stops. Two specials: a green bolt that
 * disarms the player, putting their weapon in the backpack, unless the backpack is full; and three slow green bolts
 * that explode where they land, one on the player's tile, up to 31 (the Wiki's infobox; its text says 31 for the bolt,
 * the infobox 21, and the infobox is taken), which no prayer stops. After the explosions come [COOLDOWN] plain bolts;
 * otherwise each attack is a bolt [BOLT_WEIGHT] in 15, the explosions [EXPLOSION_WEIGHT] in 15 and the disarm 1 in 15.
 * He shouts one of six lines as he attacks. His drops (bones, the main table, shard 1 of the wards as a pre-roll) are in
 * npc-drops.json and his kill count in `total_chaosfanatic_kills`.
 *
 * His fight runs on the encounter framework from the tick he is in the world, against whoever he has taken on within
 * [AREA_RADIUS] tiles of where he spawned; he walks after them when they are out of his bolts' [RANGE].
 *
 * GUESSED until recorded: a shout with every attack; the disarm doing no damage and saying nothing, and taking only the
 * weapon; each explosion hitting its own tile only, the other two bolts landing within [SCATTER] of the player; the
 * explosion bolts' [EXPLOSION_TICKS] in the air and the disarm's landing time; explosions of 0 to 31; the graphics (the
 * chaos elemental's red and green bolts, 554 and 551, and the crazy archaeologist's explosion, 157); his cast
 * animation (human_caststrike); the 10-tile range; his fight's area. No pet.
 */
object ChaosFanatic {
    const val BOSS = 6619
    const val KILL_COUNT_VARP = 1519

    const val ATTACK_SPEED = 2
    const val MAX_HIT = 21
    const val SPECIAL_MAX = 31
    const val RANGE = 10

    const val ANIM_CAST = 711
    const val SPOT_BOLT = 554
    const val SPOT_GREEN = 551
    const val SPOT_EXPLOSION = 157

    const val BOLT_WEIGHT = 10
    const val EXPLOSION_WEIGHT = 4
    const val COOLDOWN = 12
    const val EXPLOSION_TICKS = 5
    const val SCATTER = 2
    const val AREA_RADIUS = 15

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "chaosfanatic", type,
            // Unleashed, so he follows anyone in his area past the spawn leash (exploit review round 39: from the area's
            // edge a leashed boss never reached them).
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = { boss.unleashed = true })),
            scriptedAttacks = true,
        )
    }

    /** When his next attack may come, and how many plain bolts are left before a special may, by boss. */
    private val cooldown = java.util.WeakHashMap<Npc, Int>()

    private fun EncounterContext.tick() {
        val target = heldTarget()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val far = LineOfSight.distance(boss.x, boss.z, boss.type.size, target.x, target.z, 1, 1) > RANGE ||
            !world.meleeCombat.sees(boss, target)
        if (far) world.npcs.follow(boss, target) else world.npcs.stopFollowing(boss)
        if (far || world.tick < boss.nextAttackTick) return
        // The NPC's own cooldown, so a strike made before his fight began holds his first one here too.
        boss.nextAttackTick = world.tick + ATTACK_SPEED
        boss.effects.say(world.dialogueText[ChaosFanaticLines.SHOUTS[roll(0, ChaosFanaticLines.SHOUTS.size - 1)]])
        val left = cooldown[boss] ?: 0
        val pick = if (left > 0) 1 else roll(1, 15)
        when {
            pick <= BOLT_WEIGHT -> {
                if (left > 0) cooldown[boss] = left - 1
                world.meleeCombat.fireAt(boss, target, world.tick, NpcRangedAttack(anim = ANIM_CAST, projectile = SPOT_BOLT, maxHit = MAX_HIT), AttackType.MAGIC)
                log("casts at ${target.username}")
            }
            pick <= BOLT_WEIGHT + EXPLOSION_WEIGHT -> {
                cooldown[boss] = COOLDOWN
                explosions(target)
            }
            else -> disarm(target)
        }
    }

    /** Three green bolts, one on [target]'s tile and two near it, each exploding where it lands. */
    private fun EncounterContext.explosions(target: Player) {
        boss.effects.anim(ANIM_CAST)
        val centre = Tile(target.x, target.z)
        val landings = listOf(centre, near(centre), near(centre))
        log("explosions on ${landings.joinToString(" ") { "${it.x},${it.z}" }}")
        for (at in landings) {
            world.zones.projectile(bolt(SPOT_GREEN, at.x, at.z, EXPLOSION_TICKS))
            // Only the player he aims at: since 2 April 2025 the explosions hurt nobody else (OSRS Wiki, Chaos Fanatic, Changes).
            hazard(listOf(at), warning = -1, warnTicks = EXPLOSION_TICKS, ticks = 1, minDamage = 0, maxDamage = SPECIAL_MAX, spotanim = SPOT_EXPLOSION, only = target)
        }
    }

    /** The green bolt that takes [target]'s weapon off, if their backpack has room for it. */
    private fun EncounterContext.disarm(target: Player) {
        boss.effects.anim(ANIM_CAST)
        val distance = LineOfSight.distance(boss.x, boss.z, boss.type.size, target.x, target.z, 1, 1)
        // A magic bolt's flight, as the combat code times one.
        val ticks = 1 + (1 + distance) / 3
        world.zones.projectile(bolt(SPOT_GREEN, target.x, target.z, ticks))
        log("disarms ${target.username}")
        after(ticks) {
            if (target.dead || target.level != boss.level || players.none { it === target }) return@after
            // An attack that lands whether or not it takes anything: the player is in combat (no logout) and hits back,
            // as with any other shot of his.
            world.meleeCombat.attackedWithoutHit(target, boss, world.tick)
            val weapon = target.equipment[WearPos.WEAPON] ?: return@after
            if (target.inventory.freeSlots == 0) return@after
            // Onto its stack if it stacks (knives, darts), as taking it off would.
            // An open trade ends first: the backpack holds still while one is open (exploit review round 39).
            world.trades.decline(target)
            if (world.equipment.remove(target, WearPos.WEAPON) != EquipResult.Done) return@after
            log("took ${target.username}'s weapon ${weapon.id}")
        }
    }

    private fun EncounterContext.bolt(id: Int, toX: Int, toZ: Int, ticks: Int) = Projectile(
        id = id, fromX = boss.x, fromZ = boss.z, level = boss.level, toX = toX, toZ = toZ,
        startHeight = 40, endHeight = 0, startTime = 0, endTime = ticks * 30, angle = 16, progress = 64,
    )

    /** A tile within [SCATTER] of [centre], not [centre] itself. */
    private fun EncounterContext.near(centre: Tile): Tile {
        while (true) {
            val dx = roll(-SCATTER, SCATTER)
            val dz = roll(-SCATTER, SCATTER)
            if (dx != 0 || dz != 0) return Tile(centre.x + dx, centre.z + dz)
        }
    }

    /** Whether [x], [z] is within [AREA_RADIUS] of [npc]'s spawn. */
    fun inArea(npc: Npc, x: Int, z: Int): Boolean = abs(x - npc.spawn.x) <= AREA_RADIUS && abs(z - npc.spawn.z) <= AREA_RADIUS
}

fun registerChaosFanatic(world: World) {
    with(ChaosFanatic) {
        // Not in a cache without him (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss("chaos fanatic", setOf(BOSS), KILL_COUNT_VARP))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, { x, z -> inArea(npc, x, z) })
        }
    }
}
