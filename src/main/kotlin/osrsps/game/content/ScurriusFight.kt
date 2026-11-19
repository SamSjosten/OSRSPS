package osrsps.game.content

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.combat.SureHit
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.Tile

/**
 * Scurrius's fight (Scurrius step 2; the OSRS Wiki's Scurrius, Scurrius/Strategies, Giant rat (Scurrius) and Perfect
 * Scurrius, read 2026-11-19; animations and graphics from the 242 cache by name). The same in both lairs, but for the
 * group version's harder shots and bricks ([ScurriusLairs]).
 *
 * - **Phase one** (above [FEED_AT_PERCENT]% of his hitpoints): his tail swipe at whoever is beside him (max 13, the
 *   NPC file's; Protect from Melee "negates all damage"), following them otherwise, and now and then his Falling
 *   Bricks. No warning for the swipe.
 * - **The food piles** (from [FEED_AT_PERCENT]% down to [CENTRE_AT_PERCENT]%): he breaks free of any bind and goes to
 *   a food pile, "random, though usually one close to him", and eats: ten eating animations of which five heal him
 *   ([HEALS]), 5 hitpoints per player in the lair in a private lair and 10 in the public one (Wiki: "5 or 10"), while
 *   he takes [EATING_TAKES_PERCENT]% of each hit ("a slight damage reduction") and attacks with magic, ranged, the
 *   bricks and his rats only. After the tenth he fights again with his tail too, and goes back to a pile now and then
 *   ([FEED_AFTER_ATTACKS]).
 * - **Phase three** (at [CENTRE_AT_PERCENT]% or below): to the centre of the lair, then magic and ranged only, every
 *   [CENTRE_SPEED] ticks ("at a faster rate"), the bricks (several in a row allowed) and his rats. Healed back over
 *   30%, he goes back to the piles.
 * - **Bolts of Electricity** (magic, max 8 solo, 14 group) and **Flying Fur** (ranged, max 7 solo, 13 group): rolled
 *   as thrown, landing after the flight; the protection prayer counts on landing ("they will only deal damage on
 *   impact, so players can pray against them even after the projectile is launched", Perfect Scurrius).
 * - **Falling Bricks**: he jumps; a shadow falls on every player's tile and on [BRICKS] more open tiles of the lair,
 *   and [BRICK_LANDS] ticks later the bricks land, 15 to 22 (solo) or 15 to 24 (group) to whoever still stands there.
 * - **Giant rats** (`rat_boss_giant_rat` 7223): with a screech, six around the lair, up to [WAVES] waves a fight.
 *   Slash max 4, every 4 ticks, aggressive; any hit kills one ("They will always die in a single hit from any
 *   weapon"), and Protect from Melee lets a quarter of their damage through ("reduces their overall damage by 75%",
 *   their NPC file's prayerPasses). They stay when he dies.
 *
 * GUESSED until recorded (each marked where it is used): when he picks each attack ([BRICK_ODDS], [SUMMON_ODDS], magic
 * and ranged at even odds, the swipe whenever he reaches in the phases that have it); the phases with rats (from the
 * piles on, none in phase one, which the Wiki describes as swipes and bricks alone); [CENTRE_SPEED]; [BRICKS],
 * [BRICK_LANDS] and the shadow being the debris graphic's start (the cache has no other); the eating rhythm
 * ([EAT_TICKS] a heal: inferred from his two eating animations, 3 and 2 ticks long, taken in turn); the pile he picks
 * (the nearest two times in three); his walk (never a run) and his not attacking on the way; the rats' tiles
 * (anywhere open in the lair) and their first target (the nearest player); his tail swipe as slash. Left out: the
 * smoke ancient sceptre's cut to his healing, the music unlock.
 */
object ScurriusFight {
    const val GIANT_RAT = 7223

    const val ATTACK_SPEED = 4
    const val CENTRE_SPEED = 3

    const val FEED_AT_PERCENT = 80
    const val CENTRE_AT_PERCENT = 30
    const val HEALS = 5
    const val EAT_TICKS = 5
    const val HEAL_SOLO = 5
    const val HEAL_GROUP = 10
    const val EATING_TAKES_PERCENT = 80
    const val FEED_AFTER_ATTACKS = 8

    const val MAGIC_SOLO = 8
    const val MAGIC_GROUP = 14
    const val RANGED_SOLO = 7
    const val RANGED_GROUP = 13
    const val BRICK_MIN = 15
    const val BRICK_SOLO = 22
    const val BRICK_GROUP = 24
    const val BRICKS = 6
    const val BRICK_LANDS = 4

    const val BRICK_ODDS = 5
    const val CENTRE_BRICK_ODDS = 3
    const val SUMMON_ODDS = 8
    const val WAVES = 12
    const val RATS = 6

    const val ANIM_MELEE = 10692
    const val ANIM_RANGED = 10694
    const val ANIM_FEED_RANGED = 10695
    const val ANIM_MAGIC = 10696
    const val ANIM_FEED_MAGIC = 10697
    const val ANIM_STOMP = 10698
    const val ANIM_FEED_STOMP = 10699
    const val ANIM_SUMMON = 10700
    const val ANIM_FEED_SUMMON = 10702
    const val ANIM_EAT_1 = 10688
    const val ANIM_EAT_2 = 10689
    const val ANIM_ENRAGE = 10704

    const val SPOT_MAGIC_CAST = 2638
    const val SPOT_FEED_MAGIC_CAST = 2639
    const val SPOT_MAGIC = 2640
    const val SPOT_MAGIC_IMPACT = 2641
    const val SPOT_RANGED = 2642
    const val SPOT_RANGED_IMPACT = 2643
    const val SPOT_DEBRIS = 2644

    /** Where he stands once at the centre (south-west tile): his centre on the lair's middle, 3298,9867. */
    val CENTRE = Tile(3297, 9866)

    /** The food piles (each 2 by 2, south-west tile), on the real map. */
    val PILES = listOf(Tile(3298, 9876), Tile(3298, 9858), Tile(3307, 9867))

    enum class Stage { MELEE, TO_PILE, FEEDING, FIGHT, TO_CENTRE, CENTRE }

    /** One fight's state, by boss. */
    class Fight {
        var stage = Stage.MELEE
        /** The pile he goes to or eats from, real map. */
        var pile: Tile? = null
        var heals = 0
        var nextHeal = 0L
        var attacksSinceFeed = 0
        var waves = 0
        internal val rats = ArrayList<WeakReference<Npc>>()
    }

    private val fights = WeakHashMap<Npc, Fight>()

    fun fight(boss: Npc): Fight = fights.getOrPut(boss) { Fight() }

    fun group(boss: Npc) = boss.type.id == Scurrius.BOSS_PUBLIC

    fun isBoss(id: Int) = id == Scurrius.BOSS_PUBLIC || id == Scurrius.BOSS_PRIVATE

    fun def(world: World, id: Int): EncounterDef? {
        val type = world.npcTypes[id] ?: return null
        return EncounterDef(
            "scurrius", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = {
                boss.unleashed = true
                // Guessed: his own rats never stop him on his way to a pile or the centre, so he walks through NPCs.
                boss.passesNpcs = true
            })),
            scriptedAttacks = true,
        )
    }

    /** [x], [z] of the lair [boss] is in, on the real map, turned into that lair's (the same for the public one). */
    private fun EncounterContext.here(x: Int, z: Int): Tile {
        val run = world.scurriusLairs.runAt(boss.x, boss.z) ?: return Tile(x, z)
        return run.inside(x, z)
    }

    /** Whether he is at or below [percent] of his hitpoints, compared without rounding (as Encounter.phaseOver does). */
    private fun EncounterContext.atOrBelow(percent: Int): Boolean = boss.hitpoints * 100 <= boss.maxHitpoints * percent

    private fun EncounterContext.tick() {
        val fight = fight(boss)
        when (fight.stage) {
            Stage.MELEE -> if (atOrBelow(FEED_AT_PERCENT)) toPile(fight)
            Stage.TO_PILE, Stage.FEEDING, Stage.FIGHT -> if (atOrBelow(CENTRE_AT_PERCENT)) toCentre(fight)
            Stage.TO_CENTRE, Stage.CENTRE -> if (!atOrBelow(CENTRE_AT_PERCENT)) toPile(fight)
        }
        if (fight.stage == Stage.FIGHT && fight.attacksSinceFeed >= FEED_AFTER_ATTACKS) toPile(fight)

        val target = heldTarget() ?: nearest()
        when (fight.stage) {
            Stage.TO_PILE -> return walkToPile(fight)
            Stage.TO_CENTRE -> return walkToCentre(fight)
            Stage.FEEDING -> eat(fight)
            else -> {}
        }
        target ?: return
        boss.face(target)
        val melee = fight.stage == Stage.MELEE || fight.stage == Stage.FIGHT
        val reaches = world.meleeCombat.reaches(boss, target)
        if (melee) {
            if (reaches) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        }
        if (world.tick < boss.nextAttackTick) return
        val speed = if (fight.stage == Stage.CENTRE) CENTRE_SPEED else ATTACK_SPEED
        attack(fight, target, reaches && melee, speed)
    }

    /** The nearest living player in the lair, whom he takes on. */
    private fun EncounterContext.nearest(): Player? {
        val pick = players.filter { !it.dead }.minByOrNull { abs(it.x - boss.x) + abs(it.z - boss.z) } ?: return null
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        return pick
    }

    private fun EncounterContext.attack(fight: Fight, target: Player, swipe: Boolean, speed: Int) {
        val stage = fight.stage
        val feeding = stage == Stage.FEEDING
        val rats = stage != Stage.MELEE && fight.waves < WAVES
        val brickOdds = if (stage == Stage.CENTRE) CENTRE_BRICK_ODDS else BRICK_ODDS
        when {
            rats && roll(1, SUMMON_ODDS) == 1 -> summon(fight, feeding)
            roll(1, brickOdds) == 1 -> bricks(feeding)
            stage == Stage.MELEE -> if (swipe) swipeAt(target) else return // Nothing done: still ready when he reaches.
            swipe && roll(1, 2) == 1 -> swipeAt(target)
            roll(1, 2) == 1 -> shoot(target, AttackType.MAGIC, feeding)
            else -> shoot(target, AttackType.RANGED, feeding)
        }
        if (stage == Stage.FIGHT) fight.attacksSinceFeed++
        // After the attack, as swingAt and fireAt set his NPC definition's speed, not this stage's.
        boss.nextAttackTick = world.tick + speed
    }

    private fun EncounterContext.swipeAt(target: Player) {
        world.meleeCombat.swingAt(boss, target, world.tick, ANIM_MELEE)
        log("swipes at ${target.username}")
    }

    private fun EncounterContext.shoot(target: Player, type: AttackType, feeding: Boolean) {
        val solo = !group(boss)
        val shot = if (type == AttackType.MAGIC) {
            NpcRangedAttack(
                anim = if (feeding) ANIM_FEED_MAGIC else ANIM_MAGIC, cast = if (feeding) SPOT_FEED_MAGIC_CAST else SPOT_MAGIC_CAST,
                projectile = SPOT_MAGIC, impact = SPOT_MAGIC_IMPACT, maxHit = if (solo) MAGIC_SOLO else MAGIC_GROUP,
            )
        } else {
            NpcRangedAttack(
                anim = if (feeding) ANIM_FEED_RANGED else ANIM_RANGED, projectile = SPOT_RANGED, impact = SPOT_RANGED_IMPACT,
                maxHit = if (solo) RANGED_SOLO else RANGED_GROUP,
            )
        }
        world.meleeCombat.fireAt(boss, target, world.tick, shot, type, prayedOnLanding = true)
        log("${if (type == AttackType.MAGIC) "bolts" else "fur"} at ${target.username}")
    }

    /** Falling Bricks: a shadow on every player's tile and on [BRICKS] open tiles more, landing [BRICK_LANDS] ticks on. */
    private fun EncounterContext.bricks(feeding: Boolean) {
        boss.effects.anim(if (feeding) ANIM_FEED_STOMP else ANIM_STOMP)
        val tiles = LinkedHashSet<Tile>()
        for (player in players) if (!player.dead) tiles += Tile(player.x, player.z)
        var tries = 0
        val wanted = tiles.size + BRICKS
        while (tiles.size < wanted && tries++ < BRICKS * 20) {
            val at = here(Scurrius.LAIR_X + roll(0, Scurrius.LAIR_ZONES * 8 - 1), Scurrius.LAIR_Z + roll(0, Scurrius.LAIR_ZONES * 8 - 1))
            if (open(at) && !underBoss(at)) tiles += at
        }
        if (tiles.isEmpty()) return
        val max = if (group(boss)) BRICK_GROUP else BRICK_SOLO
        hazard(tiles, SPOT_DEBRIS, BRICK_LANDS, 1, BRICK_MIN, max)
        log("bricks on ${tiles.size} tiles")
    }

    private fun EncounterContext.underBoss(at: Tile): Boolean {
        val size = boss.type.size
        return at.x in boss.x until boss.x + size && at.z in boss.z until boss.z + size
    }

    /** Whether a player could stand on [at]: some way onto it is open. */
    private fun EncounterContext.open(at: Tile): Boolean =
        world.canStep(boss.level, at.x - 1, at.z, 1, 0) || world.canStep(boss.level, at.x + 1, at.z, -1, 0) ||
            world.canStep(boss.level, at.x, at.z - 1, 0, 1) || world.canStep(boss.level, at.x, at.z + 1, 0, -1)

    /** Six giant rats on open tiles of the lair, each going for the player nearest it. */
    private fun EncounterContext.summon(fight: Fight, feeding: Boolean) {
        val type = world.npcTypes[GIANT_RAT] ?: return
        boss.effects.anim(if (feeding) ANIM_FEED_SUMMON else ANIM_SUMMON)
        fight.waves++
        var placed = 0
        var tries = 0
        while (placed < RATS && tries++ < RATS * 20) {
            val at = here(Scurrius.LAIR_X + roll(0, Scurrius.LAIR_ZONES * 8 - 2), Scurrius.LAIR_Z + roll(0, Scurrius.LAIR_ZONES * 8 - 2))
            if (!world.npcs.canStand(boss, at.x, at.z) || !open(at) || !world.scurriusLairs.inLair(boss.level, at.x, at.z)) continue
            val rat = world.spawnNpc(NpcSpawn(type, at.x, at.z, boss.level, source = "Scurrius's giant rat", respawns = false)) ?: continue
            rat.unleashed = true
            fight.rats += WeakReference(rat)
            players.filter { !it.dead }.minByOrNull { abs(it.x - at.x) + abs(it.z - at.z) }?.let { world.meleeCombat.aggress(rat, it, world.tick) }
            placed++
        }
        log("summons $placed giant rats (wave ${fight.waves})")
    }

    private fun EncounterContext.toPile(fight: Fight) {
        val nearest = PILES.minBy { abs(here(it.x, it.z).x - boss.x) + abs(here(it.x, it.z).z - boss.z) }
        fight.pile = if (roll(1, 3) <= 2) nearest else PILES.filter { it != nearest }[roll(0, PILES.size - 2)]
        fight.stage = Stage.TO_PILE
        boss.freezeTicks = 0
        world.npcs.stopFollowing(boss)
        log("goes to the food pile at ${fight.pile!!.x},${fight.pile!!.z}")
    }

    private fun EncounterContext.toCentre(fight: Fight) {
        fight.stage = Stage.TO_CENTRE
        fight.pile = null
        world.npcs.stopFollowing(boss)
        boss.effects.anim(ANIM_ENRAGE)
        log("goes to the centre")
    }

    /** The tile his south-west corner stands on to eat from [pile]: beside it, toward the middle of the lair. */
    private fun standAt(pile: Tile): Tile = when (pile) {
        PILES[0] -> Tile(pile.x - 1, pile.z - 3)
        PILES[1] -> Tile(pile.x - 1, pile.z + 2)
        else -> Tile(pile.x - 3, pile.z - 1)
    }

    private fun EncounterContext.walkToPile(fight: Fight) {
        val pile = fight.pile ?: return
        val at = here(standAt(pile).x, standAt(pile).z)
        if (boss.x == at.x && boss.z == at.z) {
            fight.stage = Stage.FEEDING
            fight.heals = 0
            fight.nextHeal = world.tick + EAT_TICKS
            boss.effects.anim(ANIM_EAT_1)
            log("eats")
            return
        }
        world.npcs.stepToward(boss, at.x, at.z)
    }

    private fun EncounterContext.walkToCentre(fight: Fight) {
        val at = here(CENTRE.x, CENTRE.z)
        if (boss.x == at.x && boss.z == at.z) {
            fight.stage = Stage.CENTRE
            log("at the centre")
            return
        }
        world.npcs.stepToward(boss, at.x, at.z)
    }

    /** Eating: an animation of the two in turn, and a heal at the end of each pair, five in all. */
    private fun EncounterContext.eat(fight: Fight) {
        val left = fight.nextHeal - world.tick
        if (left == EAT_TICKS - 3L && world.tick < boss.nextAttackTick - 1) boss.effects.anim(ANIM_EAT_2)
        if (world.tick < fight.nextHeal) return
        val each = if (group(boss)) HEAL_GROUP else HEAL_SOLO
        val healed = minOf(boss.maxHitpoints - boss.hitpoints, each * maxOf(1, players.size))
        if (healed > 0 && boss.hitpoints > 0) {
            boss.hitpoints += healed
            // His health bar shows it: bars go out only as they are queued, as a hit queues one.
            boss.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(boss.hitpoints, boss.maxHitpoints))
        }
        fight.heals++
        log("heals $healed (${fight.heals})")
        if (fight.heals >= HEALS) {
            fight.stage = Stage.FIGHT
            fight.attacksSinceFeed = 0
            fight.pile = null
            return
        }
        fight.nextHeal = world.tick + EAT_TICKS
        boss.effects.anim(ANIM_EAT_1)
    }

    /** Whether [boss] is eating now, so takes less of each hit. */
    fun eating(boss: Npc): Boolean = fights[boss]?.stage == Stage.FEEDING

    /** Giant rats of [boss]'s fight still in, for tests. */
    fun rats(boss: Npc): List<Npc> = fights[boss]?.rats?.mapNotNull { it.get() }?.filter { !it.dead } ?: emptyList()
}

fun registerScurriusFight(world: World) {
    with(ScurriusFight) {
        val defs = listOf(Scurrius.BOSS_PUBLIC, Scurrius.BOSS_PRIVATE).mapNotNull { id -> def(world, id)?.let { id to it } }.toMap()
        if (defs.isNotEmpty()) {
            world.npcScripts.onTick(defs.keys.toList()) { npc ->
                if (npc.dead || world.encounters.all.any { it.boss === npc && !it.ended }) return@onTick
                val def = defs[npc.type.id] ?: return@onTick
                // His own lair only: the public one, or the private instance he is in.
                val run = world.scurriusLairs.runAt(npc.x, npc.z)
                val level = npc.level
                world.encounters.start(def, npc, { x, z ->
                    world.scurriusLairs.inLair(level, x, z) && world.scurriusLairs.runAt(x, z) === run
                })
            }
            world.npcScripts.onDeath(defs.keys.toList()) { boss -> fight(boss).stage = ScurriusFight.Stage.MELEE }
        }
        // Any hit kills one of his giant rats, whatever the style.
        val sureHit = world.combat.sureHit
        world.combat.sureHit = { player, npc, type -> if (npc.type.id == GIANT_RAT) SureHit.MAX else sureHit(player, npc, type) }
        val rolledOn = world.combat.rolledOn
        world.combat.rolledOn = { player, npc, type, rolled ->
            when {
                npc.type.id == GIANT_RAT -> npc.hitpoints
                isBoss(npc.type.id) && eating(npc) -> rolledOn(player, npc, type, rolled) * EATING_TAKES_PERCENT / 100
                else -> rolledOn(player, npc, type, rolled)
            }
        }
    }
}
