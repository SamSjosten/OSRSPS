package osrsps.game.content

import kotlin.math.abs
import osrsps.game.Player
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

/** The crazy archaeologist's words (keys only; the words are in data/dialogue.txt). */
object CrazyArchaeologistLines : Lines("crazy_archaeologist", "Crazy archaeologist") {
    val RAIN = line("rain", "his overhead shout as his special begins (the Wiki's Crazy archaeologist page)")
}

/**
 * The crazy archaeologist, Bellock (Wilderness step W4.2; the Wiki's Crazy archaeologist and its Strategies page, read
 * 2026-11-15): `crazy_archaeologist` 6618 in the Ruins at level 23, single-way, combat 204, 225 hitpoints, aggressive,
 * every 3 ticks. He throws books (ranged, max 14) and only hits with melee (max 14) when the player is beside him; the
 * protection prayers stop either. His special, after his shout, throws three books that land in an arc on and around the
 * player and explode over 3x3s; one splits on landing into two more that explode nearby after a moment; each
 * explosion up to 24, which no prayer stops. His drops (bones, the main table, shard 2 of the wards as a pre-roll) are in
 * npc-drops.json and his kill count in `total_crazyarchaeologist_kills`.
 *
 * His fight runs on the encounter framework from the tick he is in the world, against whoever he has taken on within
 * [AREA_RADIUS] tiles of where he spawned; he walks after them when they are out of his books' [RANGE].
 *
 * GUESSED until recorded: the special one attack in [SPECIAL_CHANCE]; melee or books beside the player half and half;
 * where the books land (the player's tile and two within 2 of it) and the split's two (within 2 of the book that
 * split); the books' [BOOK_TICKS] and the split's [SPLIT_TICKS] in the air; explosions of 0 to 24; the explosion
 * graphic; his melee animation (the unarmed punch); the books' 7-tile range; his fight's area. No pet.
 */
object CrazyArchaeologist {
    const val BOSS = 6618
    const val KILL_COUNT_VARP = 1521

    const val ATTACK_SPEED = 3
    const val MAX_HIT = 14
    const val SPECIAL_MAX = 24
    const val RANGE = 7

    const val ANIM_THROW = 3353
    const val ANIM_PUNCH = 422
    const val SPOT_BOOK = 1259
    const val SPOT_SPECIAL_BOOK = 1260
    const val SPOT_EXPLOSION = 157

    const val SPECIAL_CHANCE = 5
    const val BOOK_TICKS = 4
    const val SPLIT_TICKS = 3
    const val SCATTER = 2
    const val AREA_RADIUS = 15

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "crazyarchaeologist", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }))),
            scriptedAttacks = true,
        )
    }

    /** When his next attack may come, by boss. */
    private val nextAttack = java.util.WeakHashMap<Npc, Long>()

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
        if (far || world.tick < (nextAttack[boss] ?: Long.MIN_VALUE)) return
        nextAttack[boss] = world.tick + ATTACK_SPEED
        when {
            roll(1, SPECIAL_CHANCE) == 1 -> rain(target)
            world.meleeCombat.reaches(boss, target) && roll(1, 2) == 1 -> {
                world.meleeCombat.swingAt(boss, target, world.tick, ANIM_PUNCH)
                log("punches ${target.username}")
            }
            else -> {
                world.meleeCombat.fireAt(boss, target, world.tick, NpcRangedAttack(anim = ANIM_THROW, projectile = SPOT_BOOK, maxHit = MAX_HIT), AttackType.RANGED)
                log("throws a book at ${target.username}")
            }
        }
    }

    /** Rain of knowledge: three books on and around [target], and one of them splitting into two more. */
    private fun EncounterContext.rain(target: Player) {
        boss.effects.say(world.dialogueText[CrazyArchaeologistLines.RAIN])
        boss.effects.anim(ANIM_THROW)
        val centre = Tile(target.x, target.z)
        val landings = listOf(centre, near(centre), near(centre))
        log("rain of knowledge on ${landings.joinToString(" ") { "${it.x},${it.z}" }}")
        for (at in landings) book(boss.x, boss.z, at, BOOK_TICKS)
        val splitting = landings[roll(0, landings.size - 1)]
        after(BOOK_TICKS) {
            repeat(2) { book(splitting.x, splitting.z, near(splitting), SPLIT_TICKS) }
        }
    }

    /** A book thrown from [fromX], [fromZ] to [at], exploding over the 3x3 round it [ticks] later. */
    private fun EncounterContext.book(fromX: Int, fromZ: Int, at: Tile, ticks: Int) {
        world.zones.projectile(
            Projectile(
                id = SPOT_SPECIAL_BOOK, fromX = fromX, fromZ = fromZ, level = boss.level, toX = at.x, toZ = at.z,
                startHeight = 40, endHeight = 0, startTime = 0, endTime = ticks * 30, angle = 16, progress = 64,
            ),
        )
        val tiles = (-1..1).flatMap { dx -> (-1..1).map { dz -> Tile(at.x + dx, at.z + dz) } }
        hazard(tiles, warning = -1, warnTicks = ticks, ticks = 1, minDamage = 0, maxDamage = SPECIAL_MAX, spotanim = SPOT_EXPLOSION)
    }

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

fun registerCrazyArchaeologist(world: World) {
    with(CrazyArchaeologist) {
        // Not in a cache without him (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss("crazy archaeologist", setOf(BOSS), KILL_COUNT_VARP))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, { x, z -> inArea(npc, x, z) })
        }
    }
}
