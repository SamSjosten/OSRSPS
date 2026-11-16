package osrsps.game.content

import kotlin.math.max
import kotlin.math.min
import osrsps.game.GameLines
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.MeleeCombat
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.game.zone.Projectile
import osrsps.map.LineOfSight
import osrsps.map.Tile

/**
 * Scorpia (Wilderness step W4.4; the Wiki's Scorpia, its Strategies page, Scorpia's guardian and Scorpia's offspring
 * (monster), read 2026-11-15): `scorpia` 6615, size 5, in her cave under the Scorpion Pit at levels 53 to 55, multi-way,
 * combat 225, 200 hitpoints, aggressive. She stabs (max 16, every 4 ticks) and poisons from 20; each attack, landed or
 * not, drains 2 prayer from a player praying Protect from Melee. Once she is on [GUARDIANS_AT] hitpoints or fewer, the
 * next attack on her brings two of her guardians (`scorpia_guardian` 6617), once a fight: each heals her [HEAL] every
 * [HEAL_TICKS] ticks from within [HEAL_RANGE] tiles, not from under her, and leaves if it hasn't healed her for
 * [LEAVE_TICKS] ticks; killed, they don't come back. Her offspring (6616) about the cave shoot (max 2) and poison from 6,
 * from npc-combat.json. Her drops (shard 3 of the wards as a pre-roll, the main table) are in npc-drops.json and her
 * kill count in `total_scorpia_kills`.
 *
 * The cave: Enter on the Scorpion Pit's three caverns (`wilderness_scorpion_entrance` 26762) and Use on the cave's
 * three crevices (`wilderness_scorpion_exit` 26763), each cavern paired with the crevice under it, as the 242 map
 * places them.
 *
 * GUESSED until recorded: where each cavern and crevice put the player (the open side of the one they come out of);
 * no animation going through; where the guardians come in (two tiles off her west and east sides); their heal's
 * graphic (curse_travel 109); the encounter's area (her cave's map square).
 */
object Scorpia {
    const val BOSS = 6615
    const val OFFSPRING = 6616
    const val GUARDIAN = 6617
    const val KILL_COUNT_VARP = 1520

    const val ATTACK_SPEED = 4
    const val PRAYER_DRAIN = 2
    const val ANIM_ATTACK = 6254

    const val GUARDIANS_AT = 99
    const val HEAL = 8
    const val HEAL_TICKS = 3
    const val HEAL_RANGE = 3
    const val LEAVE_TICKS = 25
    const val SPOT_HEAL = 109

    const val ENTRANCE = 26762
    const val EXIT = 26763
    const val ENTER = 1
    const val USE = 1

    /** A cavern on the surface, the crevice under it, and where each puts the player. */
    class Way(val cavern: Tile, val crevice: Tile, val below: Tile, val above: Tile)

    val WAYS = listOf(
        Way(Tile(3231, 3936), Tile(3233, 10331), below = Tile(3233, 10332), above = Tile(3232, 3938)),
        Way(Tile(3231, 3951), Tile(3232, 10352), below = Tile(3232, 10351), above = Tile(3232, 3950)),
        Way(Tile(3241, 3949), Tile(3243, 10352), below = Tile(3243, 10351), above = Tile(3242, 3948)),
    )

    /** The cave: its map square. */
    fun inCave(x: Int, z: Int): Boolean = x shr 6 == 50 && z shr 6 == 161

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "scorpia", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }))),
            scriptedAttacks = true,
        )
    }

    /** One fight's state, by boss. */
    private class Fight {
        var nextAttack = Long.MIN_VALUE
        /** The tick she was first on [GUARDIANS_AT] or fewer, or -1. */
        var lowTick = -1L
        var summoned = false
        val guardians = HashMap<Npc, Long>()
    }

    /**
     * By fight, not by boss: a guardian follows her ([Npc.leader]), so a fight held by her would hold her too and never
     * be let go (Codex on W4.4). The encounter is dropped once it ends, and its state with it.
     */
    private val fights = java.util.WeakHashMap<EncounterContext, Fight>()

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(this) { Fight() }
        guardians(fight)
        if (fight.lowTick < 0 && boss.hitpoints <= GUARDIANS_AT) fight.lowTick = world.tick
        if (!fight.summoned && fight.lowTick >= 0 && boss.attackedTick > fight.lowTick) summon(fight)

        val target = heldTarget()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val reaches = world.meleeCombat.reaches(boss, target)
        if (reaches) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        if (!reaches || world.tick < fight.nextAttack) return
        fight.nextAttack = world.tick + ATTACK_SPEED
        world.meleeCombat.swingAt(boss, target, world.tick, ANIM_ATTACK)
        if (world.prayers.protects(target, AttackType.STAB)) {
            val prayer = target.skills.currentLevel(Skill.PRAYER)
            target.skills.setCurrentLevel(Skill.PRAYER, max(0, prayer - PRAYER_DRAIN))
            log("stabs ${target.username}, draining ${min(prayer, PRAYER_DRAIN)} prayer")
        } else {
            log("stabs ${target.username}")
        }
    }

    /** Her two guardians, two tiles off her west and east sides. */
    private fun EncounterContext.summon(fight: Fight) {
        fight.summoned = true
        val type = world.npcTypes[GUARDIAN] ?: return
        val size = boss.type.size
        for (at in listOf(Tile(boss.x - 2, boss.z + size / 2), Tile(boss.x + size + 1, boss.z + size / 2))) {
            val guardian = spawnAdd(type, at.x, at.z) ?: continue
            world.npcs.followNpc(guardian, boss)
            fight.guardians[guardian] = world.tick
        }
        log("guardians come")
    }

    /** Each guardian heals her from close by, or leaves when it hasn't for a while. */
    private fun EncounterContext.guardians(fight: Fight) {
        val each = fight.guardians.entries.iterator()
        while (each.hasNext()) {
            val (guardian, healed) = each.next()
            if (guardian.dead || guardian.hidden || world.npcs.all.none { it === guardian }) {
                each.remove()
                continue
            }
            if (world.tick - healed >= LEAVE_TICKS) {
                // As every NPC leaves the world: its avatar and whoever was fighting it let go too.
                world.removeNpc(guardian)
                each.remove()
                log("a guardian leaves")
                continue
            }
            if ((world.tick - guardian.spawnTick) % HEAL_TICKS != 0L) continue
            val distance = LineOfSight.distance(boss.x, boss.z, boss.type.size, guardian.x, guardian.z, 1, 1)
            if (distance !in 1..HEAL_RANGE || boss.hitpoints >= boss.maxHitpoints) continue
            boss.hitpoints = min(boss.maxHitpoints, boss.hitpoints + HEAL)
            // Her health bar shows the heal, as a hit's would.
            boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
            fight.guardians[guardian] = world.tick
            world.zones.projectile(
                Projectile(
                    id = SPOT_HEAL, fromX = guardian.x, fromZ = guardian.z, level = boss.level,
                    toX = boss.x + boss.type.size / 2, toZ = boss.z + boss.type.size / 2,
                    startHeight = 20, endHeight = 40, startTime = 0, endTime = 30, angle = 16, progress = 0,
                ),
            )
            log("a guardian heals her to ${boss.hitpoints}")
        }
    }
}

fun registerScorpia(world: World) {
    with(Scorpia) {
        world.handlers.onLoc(ENTRANCE, ENTER) { op ->
            val loc = op.loc!!
            val to = WAYS.firstOrNull { it.cavern.x == loc.x && it.cavern.z == loc.z }?.below
                ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.teleport(player, to.x, to.z, 0)
        }
        world.handlers.onLoc(EXIT, USE) { op ->
            val loc = op.loc!!
            val to = WAYS.firstOrNull { it.crevice.x == loc.x && it.crevice.z == loc.z }?.above
                ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.teleport(player, to.x, to.z, 0)
        }
        // Not in a cache without her (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss("scorpia", setOf(BOSS), KILL_COUNT_VARP))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, { x, z -> inCave(x, z) })
        }
    }
}
