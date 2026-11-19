package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.MeleeCombat
import osrsps.game.npc.Npc
import osrsps.map.LineOfSight
import osrsps.map.Tile
import java.util.WeakHashMap

/**
 * The Fight Cave's monsters' own mechanics (Fight Caves step FC2; the OSRS Wiki page named after each, read
 * 2026-11-17). Their levels, bonuses, max hits and animations are npc-combat.json's; what each does beyond an ordinary
 * melee or ranged monster is here:
 *
 * - **Tz-Kih** (3116, 3117): "Each hit it inflicts on a player will drain the player's Prayer points by 1, whether it
 *   inflicts damage or not." Guessed: Protect from Melee doesn't stop the drain (the page doesn't say).
 * - **Tz-Kek** (level 45, 3118, 3119): "if attacked with melee, it will reflect 1 point of damage (regardless of the
 *   damage inflicted and distance)", and "Upon death, it splits into two level 22 Tz-Keks" (3120), which "lack the
 *   recoil mechanic". Guessed: the recoil comes as the swing lands, the killing one too, and the two come in on the
 *   tile it died on and the one east of it, going for the cave's player.
 * - **Tok-Xil** (3121, 3122): spines (ranged, max 14) from up to 15 tiles, and a punch (crush, max 13) in reach.
 *   Inferred: it punches whenever it is in reach, and fires otherwise (the page doesn't say when it switches).
 * - **Yt-MejKot** (3123, 3124): a melee crush (max 25), and it can "restore up to 10 hitpoints to itself or any adjacent
 *   monster who's health has dropped below half", only "standing on a tile adjacent the player" and "only after 4 game
 *   ticks have passed since its last attack"; "Healing replaces an attack". Guessed: the heal is a roll of 0 to 10 for
 *   each monster below half (itself and those beside it), its lizard_cleric_heal animation, and tzhaar_heal (444) on
 *   each one healed.
 * - **Ket-Zek** (3125, 3126): magic (max 49) from up to 15 tiles, and a stab (max 55) "only triggered when a player
 *   gets too close": in reach, where it keeps its magic too (the Wiki's Fight Cave Strategies: it uses both Magic and
 *   Melee in melee range). Guessed: even odds between the two in reach.
 *
 * - **TzTok-Jad** (3127; Fight Caves step FC3; the Wiki's TzTok-Jad and Yt-HurKot): every 8 ticks a magic (max 95) or
 *   ranged (max 97) attack from up to 15 tiles, each with its own tell, and an instant melee (max 97) only "if the player
 *   is within melee distance", after which he "can attack with melee every 4 ticks". The magic and ranged hits land
 *   [JAD_HIT_DELAY] ticks after the tell, and the protection prayer counts as they land, so switching on seeing the tell
 *   works. Guessed: the 3-tick delay; the pick (even odds between the styles he can use); melee as crush; the tells'
 *   animations (lordmagmus_fire 2656 for magic, lordmagmus_smash 2652 for ranged, lordmagmus_attack 2655 for melee) and
 *   spotanims (tzhaar_fire_spit_launch/_travel 447/448 for the fireball, tzhaar_rock_smash 451 on the player).
 * - **Yt-HurKot** (3128): "Once TzTok-Jad is reduced to below 150 health, he will summon four Yt-HurKots". Each heals him
 *   5 every 4 ticks within 5 tiles of him, walking to him otherwise; attacked, it "stop[s] healing and immediately
 *   target[s] the player with melee", going back to him once it gives up. If they heal him to full they come again
 *   when he is next below 150 (ours: topped up to four). They go when he dies. Guessed: where they come in (around
 *   him, [HEALER_OFFSETS]); a distracted one doesn't heal even close by (the Jad page says it would, the Yt-HurKot page
 *   that it stops: the latter kept); the heal's animation and spotanim (a Yt-MejKot's).
 *
 * They fight through [Npc.scriptedAttacks], driven from [FightCaves.process] by [tick], so these choices are made where
 * the engine would swing or fire. The level 22 Tz-Kek has nothing of its own and fights as the engine's ordinary melee
 * monster.
 */
class FightCaveMonsters(private val world: World) {
    /** Hooks the recoil and the split into the world: once, from [FightCaves.register]. */
    fun register(caves: FightCaves) {
        val landed = world.meleeCombat.meleeLanded
        world.meleeCombat.meleeLanded = { npc, player, dealt ->
            landed(npc, player, dealt)
            if (npc.type.id in TZ_KEK_45) recoil(npc, player)
        }
        world.npcScripts.onDeath(TZ_KEK_45) { split(caves, it) }
        world.npcScripts.onDeath(listOf(TZTOK_JAD)) { jadDied(caves, it) }
    }

    /** Jad's healers and when each next heals. */
    private class JadState {
        val healers: MutableList<Npc> = ArrayList()
        var healersOut = false
    }

    private val jads = WeakHashMap<Npc, JadState>()
    private val nextHeal = WeakHashMap<Npc, Long>()

    /** A magic or ranged hit of Jad's on its way, the prayer counted as it lands. */
    private class JadHit(val jad: Npc, val player: Player, val type: AttackType, val damage: Int, val landTick: Long)

    private val jadHits = ArrayList<JadHit>()

    /** How many of Jad's hits are on their way, for the tests. */
    val jadHitsInFlight: Int get() = jadHits.size

    /** The cave monsters' part of a tick with no one monster to it: Jad's hits landing. */
    fun process() {
        if (jadHits.isEmpty()) return
        val iterator = jadHits.iterator()
        while (iterator.hasNext()) {
            val hit = iterator.next()
            if (world.tick < hit.landTick) continue
            iterator.remove()
            val player = hit.player
            if (player.loggedOut || player.dead || player.hitpoints <= 0 || hit.jad.dead) continue
            // Out of Jad's cave (teleported, or the cave gone) before it landed: it doesn't follow them.
            if (world.instances.at(player.x, player.z)?.contains(hit.jad.x, hit.jad.z) != true) continue
            if (hit.type == AttackType.RANGED) player.effects.spotanim(0, SPOTANIM_JAD_ROCK, 0, 0)
            val damage = if (world.prayers.protects(player, hit.type)) 0 else hit.damage
            world.meleeCombat.hitPlayer(player, hit.jad, damage, world.tick)
        }
    }

    /** Drops Jad's hits on their way from inside [area], a run's cave, as the run ends. */
    fun dropHitsWithin(area: (x: Int, z: Int) -> Boolean) {
        jadHits.removeAll { area(it.jad.x, it.jad.z) }
    }

    /** Jad's healers' turn, and his healers coming when he is low: for [jad] in [run], fought by [player]. */
    fun jadTick(caves: FightCaves, run: FightCaves.Run, jad: Npc, player: Player) {
        if (jad.dead) return
        val state = jads.getOrPut(jad) { JadState() }
        state.healers.removeAll { it.dead || world.npcs[it.index] !== it }
        if (jad.hitpoints >= jad.maxHitpoints) state.healersOut = false
        if (!state.healersOut && jad.hitpoints < HEALERS_BELOW) {
            state.healersOut = true
            for (offset in HEALER_OFFSETS.drop(state.healers.size)) {
                val healer = caves.spawn(player, run, YT_HURKOT, Tile(jad.x + offset.x, jad.z + offset.z), fight = false) ?: continue
                state.healers += healer
                nextHeal[healer] = world.tick + HEAL_TICKS
            }
        }
        for (healer in state.healers) healerTick(healer, jad)
    }

    private fun healerTick(healer: Npc, jad: Npc) {
        // Attacked, it fights the player until it gives up on them.
        if (healer.target != null || healer.dead) return
        val near = jadDistance(healer, jad)
        if (healer.leader !== jad) world.npcs.followNpc(healer, jad)
        if (near > HEAL_RANGE || world.tick < (nextHeal[healer] ?: 0L)) return
        nextHeal[healer] = world.tick + HEAL_TICKS
        if (jad.hitpoints >= jad.maxHitpoints) return
        healer.effects.anim(ANIM_MEJKOT_HEAL)
        jad.hitpoints = minOf(jad.maxHitpoints, jad.hitpoints + HURKOT_HEAL)
        jad.effects.spotanim(0, SPOTANIM_HEAL, 0)
        jad.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(jad.hitpoints, jad.maxHitpoints))
    }

    private fun jadDistance(healer: Npc, jad: Npc): Int =
        maxOf(gap(healer.x, healer.type.size, jad.x, jad.type.size), gap(healer.z, healer.type.size, jad.z, jad.type.size))

    /** Jad died: his healers go with him, and whether the kill was on a Slayer task is kept for the pet's odds. */
    private fun jadDied(caves: FightCaves, jad: Npc) {
        val state = jads.remove(jad)
        state?.healers?.forEach { if (world.npcs[it.index] === it) world.removeNpc(it) }
        jadHits.removeAll { it.jad === jad }
        val run = caves.runAt(jad.x, jad.z) ?: return
        run.monsters.removeAll { it.type.id == YT_HURKOT }
        val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner } ?: return
        run.jadOnTask = world.slayer.onTask(player, jad)
    }

    /** Jad's attack at [player]: [reaches] says whether melee is one he can pick. */
    private fun jadAttack(jad: Npc, player: Player, reaches: Boolean) {
        val styles = if (reaches) JAD_STYLES_IN_REACH else JAD_STYLES_AFAR
        val type = styles[world.random.between(0, styles.size - 1)]
        if (type.melee) {
            world.meleeCombat.swingAt(jad, player, world.tick, ANIM_JAD_MELEE, type, JAD_MELEE_MAX)
            jad.nextAttackTick = world.tick + JAD_MELEE_TICKS
            return
        }
        val def = world.combat.npcCombat[jad.type] ?: return
        jad.nextAttackTick = world.tick + def.attackSpeed
        jad.fightTick = world.tick
        jad.face(player)
        val magic = type == AttackType.MAGIC
        jad.effects.anim(if (magic) ANIM_JAD_MAGIC else ANIM_JAD_RANGED)
        if (magic) {
            jad.effects.spotanim(0, SPOTANIM_JAD_FIRE_LAUNCH, 0)
            world.zones.projectile(world.meleeCombat.flight(jad, player, SPOTANIM_JAD_FIRE_TRAVEL, JAD_HIT_DELAY))
        }
        val roll = world.combat.npcAttack(jad, player, type) ?: return
        val max = if (magic) JAD_MAGIC_MAX else JAD_RANGED_MAX
        val damage = if (CombatMath.rollHits(world.random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(world.random, max) else 0
        jadHits += JadHit(jad, player, type, damage, world.tick + JAD_HIT_DELAY)
    }

    /** Whether a monster of [typeId] fights through [tick] rather than the engine's own swings. */
    fun scripted(typeId: Int): Boolean = typeId in SCRIPTED

    /** [npc]'s turn against [player], who it holds: walking, and an attack when one is due. */
    fun tick(npc: Npc, player: Player) {
        if (npc.dead || npc.hidden || player.dead || player.hitpoints <= 0) return
        val reaches = world.meleeCombat.reaches(npc, player)
        val shoots = npc.type.id in TOK_XIL || npc.type.id in KET_ZEK || npc.type.id == TZTOK_JAD
        npc.face(player)
        when {
            reaches -> world.npcs.stopFollowing(npc)
            shoots && world.meleeCombat.inShot(npc, player) -> world.npcs.stopFollowing(npc)
            else -> {
                if (npc.following !== player) world.npcs.follow(npc, player)
                return
            }
        }
        if (world.tick < npc.nextAttackTick) return
        val id = npc.type.id
        when {
            id in TZ_KIH -> {
                world.meleeCombat.swingAt(npc, player, world.tick)
                drainPrayer(player)
            }
            id in TOK_XIL -> if (reaches) {
                world.meleeCombat.swingAt(npc, player, world.tick, ANIM_TOK_XIL_PUNCH, AttackType.CRUSH, TOK_XIL_MELEE_MAX)
            } else {
                world.meleeCombat.fireAt(npc, player, world.tick)
            }
            id in YT_MEJKOT -> if (!heal(npc)) world.meleeCombat.swingAt(npc, player, world.tick)
            id == TZTOK_JAD -> jadAttack(npc, player, reaches)
            id in KET_ZEK -> if (reaches && world.random.chance(1, 2)) {
                world.meleeCombat.swingAt(npc, player, world.tick, ANIM_KET_ZEK_MELEE, AttackType.STAB, KET_ZEK_MELEE_MAX)
            } else {
                world.meleeCombat.fireAt(npc, player, world.tick)
            }
        }
    }

    /** A Tz-Kih's swing took a prayer point, hit or miss. */
    private fun drainPrayer(player: Player) {
        if (player.dead) return
        val points = player.skills.currentLevel(Skill.PRAYER)
        if (points > 0) player.skills.setCurrentLevel(Skill.PRAYER, points - 1)
    }

    /** One point back at [player] for their melee on a level 45 Tz-Kek. */
    private fun recoil(npc: Npc, player: Player) {
        if (player.dead || player.hitpoints <= 0) return
        world.meleeCombat.hitPlayer(player, npc, RECOIL, world.tick)
    }

    /**
     * A level 45 Tz-Kek died in a cave: two level 22s in its place, going for the cave's player. One that can't come in
     * now (the world full of NPCs) waits in [FightCaves.Run.splitsOwed], and the wave with it, until it can.
     */
    private fun split(caves: FightCaves, npc: Npc) {
        val run = caves.runAt(npc.x, npc.z) ?: return
        val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner } ?: return
        for (tile in listOf(Tile(npc.x, npc.z), Tile(npc.x + 1, npc.z))) {
            if (caves.spawn(player, run, TZ_KEK_22, tile) == null) run.splitsOwed += tile
        }
    }

    /**
     * A Yt-MejKot's heal in place of its attack: itself and every monster beside it below half its hitpoints get 0 to
     * [HEAL_MAX] back. False, with no heal, when none is below half.
     */
    private fun heal(npc: Npc): Boolean {
        val hurt = (sequenceOf(npc) + world.npcs.all.asSequence().filter { it !== npc && beside(npc, it) })
            .filter { !it.dead && !it.outOfPlay && it.hitpoints * 2 < it.maxHitpoints && world.combat.npcCombat[it.type] != null }
            .toList()
        if (hurt.isEmpty()) return false
        npc.nextAttackTick = world.tick + (world.combat.npcCombat[npc.type]?.attackSpeed ?: HEAL_TICKS)
        npc.fightTick = world.tick
        npc.effects.anim(ANIM_MEJKOT_HEAL)
        for (monster in hurt) {
            monster.hitpoints = minOf(monster.maxHitpoints, monster.hitpoints + world.random.between(0, HEAL_MAX))
            monster.effects.spotanim(0, SPOTANIM_HEAL, 0)
            monster.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(monster.hitpoints, monster.maxHitpoints))
        }
        return true
    }

    /** Whether [other] stands on a tile beside [npc]'s, edge to edge, diagonals too. */
    private fun beside(npc: Npc, other: Npc): Boolean {
        if (npc.level != other.level) return false
        val dx = gap(npc.x, npc.type.size, other.x, other.type.size)
        val dz = gap(npc.z, npc.type.size, other.z, other.type.size)
        return maxOf(dx, dz) <= 1
    }

    private fun gap(a: Int, aSize: Int, b: Int, bSize: Int): Int = when {
        b >= a + aSize -> b - (a + aSize - 1)
        a >= b + bSize -> a - (b + bSize - 1)
        else -> 0
    }

    companion object {
        val TZ_KIH = setOf(3116, 3117)
        val TZ_KEK_45 = setOf(3118, 3119)
        const val TZ_KEK_22 = 3120
        val TOK_XIL = setOf(3121, 3122)
        val YT_MEJKOT = setOf(3123, 3124)
        val KET_ZEK = setOf(3125, 3126)
        const val TZTOK_JAD = 3127
        const val YT_HURKOT = 3128
        private val SCRIPTED = TZ_KIH + TOK_XIL + YT_MEJKOT + KET_ZEK + TZTOK_JAD

        const val JAD_MELEE_MAX = 97
        const val JAD_RANGED_MAX = 97
        const val JAD_MAGIC_MAX = 95
        const val JAD_MELEE_TICKS = 4
        const val JAD_HIT_DELAY = 3
        private val JAD_STYLES_IN_REACH = listOf(AttackType.CRUSH, AttackType.RANGED, AttackType.MAGIC)
        private val JAD_STYLES_AFAR = listOf(AttackType.RANGED, AttackType.MAGIC)
        const val ANIM_JAD_MAGIC = 2656
        const val ANIM_JAD_RANGED = 2652
        const val ANIM_JAD_MELEE = 2655
        const val SPOTANIM_JAD_FIRE_LAUNCH = 447
        const val SPOTANIM_JAD_FIRE_TRAVEL = 448
        const val SPOTANIM_JAD_ROCK = 451

        const val HEALERS_BELOW = 150
        const val HURKOT_HEAL = 5
        const val HEAL_RANGE = 5

        /** Where his four healers come in, from his south-west tile (guessed: one off each side of his 5 by 5). */
        val HEALER_OFFSETS = listOf(Tile(-2, 2), Tile(6, 2), Tile(2, -2), Tile(2, 6))

        const val RECOIL = 1
        const val TOK_XIL_MELEE_MAX = 13
        const val KET_ZEK_MELEE_MAX = 55
        const val HEAL_MAX = 10
        const val HEAL_TICKS = 4

        /** magmaquris_punch, igniferum_attack, lizard_cleric_heal; tzhaar_heal (spotanim, guessed). */
        const val ANIM_TOK_XIL_PUNCH = 2628
        const val ANIM_KET_ZEK_MELEE = 2644
        const val ANIM_MEJKOT_HEAL = 2639
        const val SPOTANIM_HEAL = 444
    }
}
