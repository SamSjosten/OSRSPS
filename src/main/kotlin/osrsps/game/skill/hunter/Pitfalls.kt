package osrsps.game.skill.hunter

import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.content.WildernessDitch
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.skill.SuccessRoll
import osrsps.game.zone.ZoneLoc
import kotlin.math.abs

/**
 * A creature caught in a pitfall: NPC type [npc], the Hunter [level] it needs, the XP in tenths, the catch pair for
 * [SuccessRoll] (null for one that is always caught), its most damage a hit while teased, and what dismantling the full
 * pit gives: [loot] always, and [fur] or, failing the fur roll, [tattyFur] (none for the antelopes, whose fur is in
 * [loot]).
 */
class PitCreature(
    val npc: Int,
    val level: Int,
    val xpTenths: Int,
    val catchPair: Pair<Int, Int>?,
    val maxHit: Int,
    val attackAnim: Int,
    val loot: List<CreatureLoot>,
    val fur: Int = -1,
    val tattyFur: Int = -1,
)

/** One of the map's pits: the multiloc [id] (2 by 2) whose varbit [varbit] picks its look for each player, and its [creature]. */
class Pit(val id: Int, val varbit: Int, val creature: PitCreature)

/** A pit [owner] (a save key) has trapped, showing [value] ([Pitfalls.SET] and on) to them alone. */
class LaidPit(val owner: String, val pit: Pit, val loc: ZoneLoc) {
    var value: Int = Pitfalls.SET
        internal set
    var stateTick: Long = 0
        internal set

    /** Whether the creature fell in heading east or north (the full look), rather than west or south (turned 180). */
    internal var headingUp: Boolean = true
}

/** A creature chasing the player who teased it: hits them while beside them, and follows them over a pit they jump. */
class Tease(val player: Player, val npc: Npc, val creature: PitCreature) {
    internal var activeTick: Long = 0
    internal var nextHitTick: Long = 0
    internal var jumped: LaidPit? = null
    internal var jumpTick: Long = -1
}

/**
 * Pitfall trapping (Hunter step 5): Trap on one of the map's pits with a knife and logs makes a spiked pit; Tease a big
 * cat or antelope with a teasing stick (or an equipped hunter's spear) and it chases the player, hitting them; Jump over
 * the spiked pit and the creature, following, falls in or leaps over it; Dismantle the full pit for the loot and XP.
 *
 * Every pit is a 2 by 2 multiloc of the map's own with a varbit of its own, so each player sees their own pits: 0 the
 * bare pit (Trap), 1 the spiked pit (Jump, Dismantle), 2 a creature falling in, 3 and 4 the full pit (Dismantle) the
 * two ways round. The corner decorations follow the same varbit.
 *
 * Sources: the OSRS Wiki's Pitfall page (read 2026-11-15) for the pits' places and ops, a knife or fletching knife and
 * any logs but redwood and arctic pine (the lowest tier first), the teasing stick or equipped hunter's spear, a creature
 * that misses leaping over and not taking the same pit twice in a row, the trap counts and the creature table's levels
 * and XP; each creature's page for its drops, its max hit (5 for larupia and the antelopes, 7 for graahk and kyatt,
 * every 4 ticks) and where it lives, and that antelopes are always caught; Hunter's spear's page (Varlamore: Part One's
 * overview) for its 5% better chance. Ids from the rev 242 cache: hunting_pitfall_1 to _25 (19253 to 19268, 51673 to
 * 51681) and their varbits and forms, hunting_teasing_animal, hunter_big_cat_bite, _leap_npc and _fall_in_pit, the
 * NPCs' Tease op.
 *
 * Guessed: the catch pair for larupia, graahk and kyatt (128 to 256, so about two in three at their levels) as no
 * figures are known, with the spear adding 5 in 100 to it; the undamaged fur's chance (the Wiki says it grows with the
 * level; here half at the creature's level, rising evenly to always at 99); teasing needs the creature's Hunter level;
 * a teased creature hits for 0 to its max hit with no accuracy roll, nothing through Protect from Melee, and gives up
 * after [TEASE_TICKS] without a tease or jump or when the player goes [Traps.OWNER_RANGE] away; it falls in or leaps
 * on reaching the pit's far edge within [JUMP_WINDOW] ticks of the jump; the full look (3) is for one heading east or
 * north; trapping takes 4 ticks with human_laytrap and always uses the log; Dismantle on a spiked pit gives nothing
 * back; a spiked pit stands until its owner leaves or logs out, a full one goes after [Traps.TRAP_TICKS]; the jump is
 * the Wilderness ditch's animation and takes 2 ticks. Left out: the damaged-fur drop rate formula, hunter gear's
 * damage cut, the wild broav (While Guthix Sleeps), the rumour drops (Hunters' Rumours).
 */
class Pitfalls(private val world: World, private val traps: Traps) {
    private val laid = ArrayList<LaidPit>()
    private val teases = ArrayList<Tease>()

    /** NPC to the pit (by its owner and loc) it last leapt over, which it won't be caught in next. */
    private val leapt = HashMap<Npc, LaidPit>()

    private class Respawn(val npc: Npc, val tick: Long)

    private val respawns = ArrayList<Respawn>()

    /** The pits [owner] has trapped. */
    fun of(owner: String): List<LaidPit> = laid.filter { it.owner == owner }

    /** Every trapped pit and every teased creature, for tests. */
    val all: List<LaidPit> get() = laid
    val teased: List<Tease> get() = teases

    fun register() {
        for (pit in PITS.values) {
            world.handlers.onLoc(pit.id, TRAP) { op -> trap(pit, op.loc ?: return@onLoc) }
            world.handlers.onLoc(pit.id, JUMP) { op -> jump(pit, op.loc ?: return@onLoc) }
            world.handlers.onLoc(pit.id, DISMANTLE) { op -> dismantle(pit, op.loc ?: return@onLoc) }
        }
        for (creature in CREATURES) world.handlers.onNpc(creature.npc, TEASE) { op -> tease(creature, op.npc ?: return@onNpc) }
        traps.otherLaid = { owner -> laid.count { it.owner == owner } }
        // A spiked pit's log is spent, so a run's pits just come down (exploit review round 35, C-3).
        traps.otherTakeDown = { owner -> for (pit in of(owner.saveKey)) remove(pit, owner) }
    }

    /** [player] logs in: no pit of theirs stands, whatever their save's varbits held. */
    fun forget(player: Player) {
        for (pit in PITS.values) setVarbit(player, pit.varbit, EMPTY)
    }

    /** Call once a tick, in the NPC phase before NPCs move. */
    fun process() {
        val tick = world.tick
        respawns.removeAll { respawn ->
            if (respawn.tick > tick) return@removeAll false
            val npc = respawn.npc
            if (npc.hidden) {
                world.npcs.teleport(npc, npc.spawn.x, npc.spawn.z)
                world.show(npc)
            }
            true
        }
        for (pit in laid.toList()) {
            val owner = world.playerBySaveKey(pit.owner)
            if (owner == null || owner.level != pit.loc.level || abs(owner.x - pit.loc.x) > Traps.OWNER_RANGE || abs(owner.z - pit.loc.z) > Traps.OWNER_RANGE) {
                remove(pit, owner)
                continue
            }
            val age = tick - pit.stateTick
            when (pit.value) {
                CATCHING -> if (age >= Traps.CATCHING_TICKS) change(pit, owner, if (pit.headingUp) FULL else FULL_TURNED)
                FULL, FULL_TURNED -> if (age >= Traps.TRAP_TICKS) remove(pit, owner)
            }
        }
        for (tease in teases.toList()) {
            val player = tease.player
            val npc = tease.npc
            if (player.loggedOut || player.dead || npc.hidden || npc.dead || player.level != npc.level ||
                abs(player.x - npc.spawn.x) > Traps.OWNER_RANGE || abs(player.z - npc.spawn.z) > Traps.OWNER_RANGE ||
                tick - tease.activeTick > TEASE_TICKS
            ) {
                end(tease)
                continue
            }
            // The same reach as an NPC's own melee: no hit across a corner or through a wall edge.
            if (tick >= tease.nextHitTick && world.meleeCombat.reaches(npc, player)) {
                npc.face(player)
                npc.effects.anim(tease.creature.attackAnim)
                val damage = if (world.prayers.protects(player, AttackType.CRUSH)) 0 else world.random.between(0, tease.creature.maxHit)
                world.meleeCombat.hitPlayer(player, npc, damage, tick)
                tease.nextHitTick = tick + ATTACK_TICKS
            }
        }
    }

    /** Call once a tick, straight after NPCs move: a chasing creature that reached the pit its player jumped falls in or leaps. */
    fun arrivals() {
        val tick = world.tick
        for (tease in teases.toList()) {
            val pit = tease.jumped ?: continue
            if (tick - tease.jumpTick > JUMP_WINDOW || pit !in laid || pit.value != SET) {
                tease.jumped = null
                continue
            }
            val npc = tease.npc
            val axis = crossing(pit.loc, npc, tease.player) ?: continue
            tease.jumped = null
            val owner = tease.player
            if (leapt[npc] === pit) continue
            val creature = tease.creature
            val pair = creature.catchPair
            val hunter = owner.skills.currentLevel(Skill.HUNTER)
            val caught = pair == null || hunter >= creature.level && rolls(owner, pair, hunter)
            if (caught) {
                pit.headingUp = axis.headingUp
                npc.effects.anim(FALL_ANIM)
                end(tease)
                world.hide(npc)
                respawns += Respawn(npc, tick + Traps.RESPAWN_TICKS)
                change(pit, owner, CATCHING)
            } else {
                npc.effects.anim(LEAP_ANIM)
                leapt[npc] = pit
                world.npcs.teleport(npc, axis.landX, axis.landZ)
                tease.activeTick = tick
            }
        }
    }

    private fun rolls(player: Player, pair: Pair<Int, Int>, level: Int): Boolean {
        val chance = SuccessRoll.chance(pair.first, pair.second, level)
        if (!spear(player)) return world.random.chance(chance, SuccessRoll.DENOMINATOR)
        // The spear's 5 in 100 more, out of 256 * 100.
        return world.random.chance(minOf(chance * 100 + SPEAR_BONUS * SuccessRoll.DENOMINATOR, SuccessRoll.DENOMINATOR * 100), SuccessRoll.DENOMINATOR * 100)
    }

    private fun spear(player: Player): Boolean = player.equipment[WearPos.WEAPON]?.id == HUNTERS_SPEAR

    private fun canTease(player: Player): Boolean = player.inventory.count(TEASING_STICK) > 0L || spear(player)

    /** Which way [npc] crosses [pit] to [player], if it stands against the edge across the pit from them. */
    private fun crossing(pit: ZoneLoc, npc: Npc, player: Player): Crossing? {
        val px = pit.x
        val pz = pit.z
        val size = npc.type.size
        val overlapsZ = npc.z <= pz + 1 && npc.z + size - 1 >= pz
        val overlapsX = npc.x <= px + 1 && npc.x + size - 1 >= px
        return when {
            player.x > px + 1 && overlapsZ && npc.x + size - 1 == px - 1 -> Crossing(true, px + 2, npc.z)
            player.x < px && overlapsZ && npc.x == px + 2 -> Crossing(false, px - size, npc.z)
            player.z > pz + 1 && overlapsX && npc.z + size - 1 == pz - 1 -> Crossing(true, npc.x, pz + 2)
            player.z < pz && overlapsX && npc.z == pz + 2 -> Crossing(false, npc.x, pz - size)
            else -> null
        }
    }

    private class Crossing(val headingUp: Boolean, val landX: Int, val landZ: Int)

    private fun end(tease: Tease) {
        teases.remove(tease)
        if (tease.npc.following === tease.player) world.npcs.stopFollowing(tease.npc)
    }

    private fun change(pit: LaidPit, owner: Player, value: Int) {
        pit.value = value
        pit.stateTick = world.tick
        setVarbit(owner, pit.pit.varbit, value)
    }

    private fun remove(pit: LaidPit, owner: Player?) {
        laid.remove(pit)
        leapt.values.removeAll { it === pit }
        if (owner != null) setVarbit(owner, pit.pit.varbit, EMPTY)
    }

    private fun setVarbit(player: Player, varbit: Int, value: Int) {
        if (player.vars.types.varbit(varbit) != null) player.vars.setVarbit(varbit, value)
    }

    private fun mine(player: Player, loc: ZoneLoc): LaidPit? =
        laid.firstOrNull { it.owner == player.saveKey && it.loc.x == loc.x && it.loc.z == loc.z && it.loc.level == loc.level }

    /** Trap on a bare pit: the creature's level, a knife and logs, and room under the trap limit. The log goes as it stands. */
    private suspend fun ScriptContext.trap(pit: Pit, loc: ZoneLoc) {
        if (mine(player, loc) != null) return
        val creature = pit.creature
        if (player.skills.currentLevel(Skill.HUNTER) < creature.level) return world.message(player, HunterLines.PIT_LEVEL, "level" to creature.level)
        if (Deadfalls.KNIVES.none { player.inventory.count(it) > 0L }) return world.message(player, HunterLines.NO_KNIFE)
        if (Deadfalls.LOGS.none { player.inventory.count(it) > 0L }) return world.message(player, HunterLines.NO_LOGS)
        if (!underLimit()) return
        player.anim(LAY_ANIM)
        world.message(player, HunterLines.LAYING)
        player.actionDelay = tick + Traps.LAY_TICKS - 1
        delay(Traps.LAY_TICKS - 1)
        if (mine(player, loc) != null || !underLimit()) return
        // The lowest tier first.
        val log = Deadfalls.LOGS.firstOrNull { player.inventory.count(it) > 0L } ?: return world.message(player, HunterLines.NO_LOGS)
        val slot = player.inventory.contents().firstOrNull { it.second.id == log }?.first ?: return
        if (player.inventory.remove(slot, 1) != 1) return
        val laidPit = LaidPit(player.saveKey, pit, loc)
        laid += laidPit
        change(laidPit, player, SET)
    }

    private fun ScriptContext.underLimit(): Boolean {
        val max = Traps.maxTrapsAt(player.skills.currentLevel(Skill.HUNTER))
        if (traps.of(player.saveKey).size + of(player.saveKey).size >= max) {
            world.message(player, HunterLines.TOO_MANY, "count" to max)
            return false
        }
        return true
    }

    /** Jump on the player's spiked pit: over it to the far side, the creature chasing them following. */
    private suspend fun ScriptContext.jump(pit: Pit, loc: ZoneLoc) {
        arriveDelay()
        val mine = mine(player, loc)?.takeIf { it.value == SET } ?: return
        val from = osrsps.map.Tile(player.x, player.z)
        val to = landing(loc, from.x, from.z) ?: return world.message(player, osrsps.game.GameLines.CANT_REACH)
        val angle = Agility.angle(to.x - from.x, to.z - from.z)
        player.effects.exactMove(PlayerEffects.ExactMove(from.x, from.z, to.x, to.z, WildernessDitch.JUMP_START_CYCLES, JUMP_TICKS * 30, angle))
        player.anim(WildernessDitch.ANIM_JUMP)
        delay(JUMP_TICKS)
        world.teleport(player, to.x, to.z, loc.level)
        val tease = teases.firstOrNull { it.player === player } ?: return
        tease.jumped = mine
        tease.jumpTick = world.tick
        tease.activeTick = world.tick
    }

    /** The tile across the 2 by 2 pit at [loc] from [x], [z], which must be square to one of its sides. */
    private fun landing(loc: ZoneLoc, x: Int, z: Int): osrsps.map.Tile? {
        val px = loc.x
        val pz = loc.z
        val alongZ = z in pz..pz + 1
        val alongX = x in px..px + 1
        return when {
            alongZ && x == px - 1 -> osrsps.map.Tile(px + 2, z)
            alongZ && x == px + 2 -> osrsps.map.Tile(px - 1, z)
            alongX && z == pz - 1 -> osrsps.map.Tile(x, pz + 2)
            alongX && z == pz + 2 -> osrsps.map.Tile(x, pz - 1)
            else -> null
        }
    }

    /** Dismantle on the player's spiked pit (gone, nothing back) or full pit (the loot and XP). */
    private suspend fun ScriptContext.dismantle(pit: Pit, loc: ZoneLoc) {
        val mine = mine(player, loc) ?: return
        when (mine.value) {
            SET -> {
                remove(mine, player)
                player.anim(Traps.DISMANTLE_ANIM)
                world.message(player, HunterLines.DISMANTLED)
                delay(Traps.PICK_UP_TICKS)
            }
            FULL, FULL_TURNED -> {
                val creature = pit.creature
                val drops = creature.loot.map { it.obj to it.countMax } + if (creature.fur >= 0) listOf(creature.fur to 1) else emptyList()
                if (player.inventory.freeSlots < slotsNeeded(player, drops)) return world.message(player, HunterLines.NO_SPACE)
                remove(mine, player)
                player.anim(Traps.CHECK_ANIM)
                for (drop in creature.loot) {
                    val type = world.objTypes[drop.obj] ?: continue
                    world.groundObjs.give(player, type, world.random.between(drop.count, drop.countMax))
                }
                if (creature.fur >= 0) {
                    val hunter = player.skills.currentLevel(Skill.HUNTER)
                    val fur = if (world.random.chance(perfectFurChance(creature.level, hunter), FUR_DENOMINATOR)) creature.fur else creature.tattyFur
                    world.objTypes[fur]?.let { world.groundObjs.give(player, it, 1) }
                }
                player.skills.addTenths(Skill.HUNTER, creature.xpTenths)
                val name = world.npcTypes[creature.npc]?.name?.lowercase() ?: "creature"
                world.message(player, HunterLines.CAUGHT, "creature" to name)
                delay(Traps.PICK_UP_TICKS)
            }
        }
    }

    private fun slotsNeeded(player: Player, gains: List<Pair<Int, Int>>): Int = gains.sumOf { (id, count) ->
        val type = world.objTypes[id]
        when {
            type == null -> 0
            type.stackable -> if (player.inventory.count(id) > 0) 0 else 1
            else -> count
        }
    }

    /** Tease: the creature turns on the player and chases them. */
    private suspend fun ScriptContext.tease(creature: PitCreature, npc: Npc) {
        if (npc.hidden || npc.dead) return
        if (!canTease(player)) return world.message(player, HunterLines.NO_TEASING_STICK)
        if (player.skills.currentLevel(Skill.HUNTER) < creature.level) return world.message(player, HunterLines.TEASE_LEVEL, "level" to creature.level)
        player.anim(TEASE_ANIM)
        // One player at a time: a second tease takes it over (guessed).
        // Each displaced tease ends, so its creature stops following too.
        for (old in teases.filter { it.npc === npc || it.player === player }) end(old)
        val tease = Tease(player, npc, creature)
        tease.activeTick = world.tick
        tease.nextHitTick = world.tick + 1
        teases += tease
        world.npcs.follow(npc, player)
    }

    companion object {
        /** The pit's forms by varbit value. */
        const val EMPTY = 0
        const val SET = 1
        const val CATCHING = 2
        const val FULL = 3
        const val FULL_TURNED = 4

        /** The forms' ops: Jump (op1, spiked), Dismantle (op2, spiked and full), Trap (op3, bare). The NPCs' op1 Tease. */
        const val JUMP = 1
        const val DISMANTLE = 2
        const val TRAP = 3
        const val TEASE = 1

        const val TEASE_ANIM = 5236 // hunting_teasing_animal
        const val BITE_ANIM = 5228 // hunter_big_cat_bite
        const val LEAP_ANIM = 5231 // hunter_bigcat_leap_npc
        const val FALL_ANIM = 5234 // hunter_bigcat_fall_in_pit
        const val SUNLIGHT_ATTACK_ANIM = 10927 // antelope_update_sunlight_attack
        const val MOONLIGHT_ATTACK_ANIM = 10926 // antelope_update_moonlight_attack

        /** Guessed: human_laytrap. */
        const val LAY_ANIM = 5208

        /** Guessed. */
        const val JUMP_TICKS = 2

        /** The creature pages: a hit every 4 ticks. */
        const val ATTACK_TICKS = 4

        /** Guessed: how long a teased creature chases without a new tease or jump. */
        const val TEASE_TICKS = 100L

        /** Guessed: how long after the jump the creature may reach the pit and be judged. */
        const val JUMP_WINDOW = 10L

        /** Hunter's spear: 5 in 100 more. */
        const val SPEAR_BONUS = 5

        const val TEASING_STICK = 10029
        const val HUNTERS_SPEAR = 29305

        const val FUR_DENOMINATOR = 1000

        /** Guessed: the undamaged fur's chance in 1000, half at [required] rising evenly to always at 99. */
        fun perfectFurChance(required: Int, level: Int): Int {
            if (level >= 99 || required >= 99) return FUR_DENOMINATOR
            val over = (level - required).coerceAtLeast(0)
            return FUR_DENOMINATOR / 2 + over * (FUR_DENOMINATOR / 2) / (99 - required)
        }

        const val SPINED_LARUPIA = 2908 // hunting_jaguar
        const val HORNED_GRAAHK = 2909 // hunting_leopard
        const val SABRE_TOOTHED_KYATT = 2907 // hunting_snow_tiger
        const val SUNLIGHT_ANTELOPE = 13133
        const val MOONLIGHT_ANTELOPE = 13132

        const val BIG_BONES = 532

        /** Guessed: no catch figures are known. */
        private val CAT_PAIR = 128 to 256

        val LARUPIA = PitCreature(
            SPINED_LARUPIA, 31, 1800, CAT_PAIR, 5, BITE_ANIM,
            listOf(CreatureLoot(BIG_BONES, 1), CreatureLoot(29122, 1)), fur = 10095, tattyFur = 10093,
        )
        val GRAAHK = PitCreature(
            HORNED_GRAAHK, 41, 2400, CAT_PAIR, 7, BITE_ANIM,
            listOf(CreatureLoot(BIG_BONES, 1), CreatureLoot(29119, 1)), fur = 10099, tattyFur = 10097,
        )
        val KYATT = PitCreature(
            SABRE_TOOTHED_KYATT, 55, 3000, CAT_PAIR, 7, BITE_ANIM,
            listOf(CreatureLoot(BIG_BONES, 1), CreatureLoot(29125, 1)), fur = 10103, tattyFur = 10101,
        )
        val SUNLIGHT = PitCreature(
            SUNLIGHT_ANTELOPE, 72, 3800, null, 5, SUNLIGHT_ATTACK_ANIM,
            listOf(
                CreatureLoot(BIG_BONES, 1), CreatureLoot(29168, 1), CreatureLoot(29177, 1), CreatureLoot(29116, 1),
                CreatureLoot(28924, 2, 6),
            ),
        )
        val MOONLIGHT = PitCreature(
            MOONLIGHT_ANTELOPE, 91, 4500, null, 5, MOONLIGHT_ATTACK_ANIM,
            listOf(CreatureLoot(BIG_BONES, 1), CreatureLoot(29171, 1), CreatureLoot(29174, 1), CreatureLoot(29113, 1)),
        )

        val CREATURES = listOf(LARUPIA, GRAAHK, KYATT, SUNLIGHT, MOONLIGHT)

        /**
         * The map's pits, from the rev 242 cache: each hunting_pitfall_N multiloc, its varbit, and whose pit it is by its
         * full form (hunter_pitfall_full_kyatt, _larupia, _graahk; 53024 for the antelopes) and where the map puts it
         * (Rellekka, Feldip, Karamja; the Avium Savannah for sunlight antelopes, the Burrow for moonlight ones).
         */
        val PITS: Map<Int, Pit> = buildMap {
            fun add(first: Int, varbit: Int, count: Int, creature: PitCreature) {
                for (i in 0 until count) put(first + i, Pit(first + i, varbit + i, creature))
            }
            add(19253, 2958, 6, KYATT)
            add(19259, 2964, 5, LARUPIA)
            add(19264, 2969, 5, GRAAHK)
            add(51673, 9767, 5, SUNLIGHT)
            add(51678, 9772, 4, MOONLIGHT)
        }
    }
}
