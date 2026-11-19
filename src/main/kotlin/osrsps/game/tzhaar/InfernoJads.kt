package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.MeleeCombat
import osrsps.game.npc.Npc
import osrsps.map.Tile
import java.util.WeakHashMap

/**
 * The Inferno's JalTok-Jads and their Yt-HurKots (Inferno step IN3; the Wiki's JalTok-Jad, Yt-HurKot and Inferno
 * pages, read 2026-11-17).
 *
 * - **JalTok-Jad** (7700) "attacks in all three forms of combat where the player must use the appropriate protection
 *   prayers before the attack hits", max 113 each, every 8 ticks, and "can attack with melee every 4 ticks" in reach.
 *   Wave 67 is one; wave 68 three, which "attack three ticks apart", each "slightly slower (9 ticks instead of the
 *   normal 8)". As for the Fight Cave's TzTok-Jad ([FightCaveMonsters]), the magic and ranged hits land [HIT_DELAY]
 *   ticks after the tell and the prayer counts as they land.
 * - **Yt-HurKot** (7701, level 141): "upon reaching half health" a Jad summons five (wave 67) or three (wave 68), which
 *   "spawn next to their respective Jad". Each heals its Jad 15 to 24 every 4 ticks (Mod Ash) "when in melee distance".
 *   Attacked, it stops healing and fights the player; losing them, it goes back to its Jad. "Inferno healers do not
 *   respawn." They go when their Jad dies.
 *
 * GUESSED: [HIT_DELAY] and the even pick of styles (TzTok-Jad's); melee as stab (the infobox's style); the tells'
 *   animations (jaltokjad_attack_magic 7592, _ranged 7593, _melee 7590) and TzTok-Jad's spotanims; where the healers come
 *   in ([HEALER_OFFSETS], one off each side and corner of the Jad); that "half health" is below 175.
 */
class InfernoJads(private val world: World) {
    /** Hooks a Jad's death: his healers go with him. Once, from [Inferno.register]. */
    fun register(inferno: Inferno) {
        world.npcScripts.onDeath(listOf(InfernoMonster.JAD.type)) { jad -> died(inferno, jad) }
    }

    private class JadState(val attackSpeed: Int) {
        val healers: MutableList<Npc> = ArrayList()
        var summoned = false

        /** The tick of its first attack as its wave came in. */
        var firstAttack = 0L
    }

    private val jads = WeakHashMap<Npc, JadState>()
    private val nextHeal = WeakHashMap<Npc, Long>()
    private val healerOf = WeakHashMap<Npc, Npc>()

    /** A magic or ranged hit on its way, the prayer counted as it lands. */
    private class Hit(val jad: Npc, val player: Player, val type: AttackType, val damage: Int, val landTick: Long)

    private val hits = ArrayList<Hit>()

    /** Sets up the Jads of [run]'s wave as they come in: their speed, and wave 68's three ticks apart. */
    fun waveIn(run: Inferno.Run, jads: List<Npc>) {
        val speed = if (run.wave == InfernoWaves.TRIPLE_JAD_WAVE) TRIPLE_SPEED else ATTACK_SPEED
        for ((i, jad) in jads.withIndex()) {
            val state = jads(jad, speed)
            if (run.wave == InfernoWaves.TRIPLE_JAD_WAVE) jad.nextAttackTick = world.tick + 1 + i * TRIPLE_APART
            state.firstAttack = jad.nextAttackTick
        }
    }

    private fun jads(jad: Npc, speed: Int) = this.jads.getOrPut(jad) { JadState(speed) }

    /** The hits landing: once a tick, from [Inferno.process]. */
    fun process() {
        if (hits.isEmpty()) return
        val iterator = hits.iterator()
        while (iterator.hasNext()) {
            val hit = iterator.next()
            if (world.tick < hit.landTick) continue
            iterator.remove()
            val player = hit.player
            if (player.loggedOut || player.dead || player.hitpoints <= 0 || hit.jad.dead) continue
            // A player who left the run (or was moved out) is not hit by a Jad still inside it.
            if (world.instances.at(player.x, player.z)?.contains(hit.jad.x, hit.jad.z) != true) continue
            if (hit.type == AttackType.RANGED) player.effects.spotanim(0, FightCaveMonsters.SPOTANIM_JAD_ROCK, 0, 0)
            // Dinh's bulwark counts as it lands, as the prayer does.
            val bulwarked = if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(hit.damage) else hit.damage
            val damage = if (world.prayers.protects(player, hit.type)) 0 else bulwarked
            world.meleeCombat.hitPlayer(player, hit.jad, damage, world.tick)
        }
    }

    /** [jad]'s turn in [run] against [player]: his healers, then walking and an attack when one is due. */
    fun tick(inferno: Inferno, run: Inferno.Run, jad: Npc, player: Player) {
        if (jad.dead) return
        val state = jads(jad, ATTACK_SPEED)
        state.healers.removeAll { it.dead || world.npcs[it.index] !== it }
        if (!state.summoned && jad.hitpoints < jad.maxHitpoints / 2) {
            state.summoned = true
            val count = if (run.wave == InfernoWaves.TRIPLE_JAD_WAVE) TRIPLE_HEALERS else HEALERS
            for (offset in HEALER_OFFSETS.take(count)) {
                val healer = inferno.spawn(player, run, HEALER, Tile(jad.x + offset.x, jad.z + offset.z), fight = false) ?: continue
                state.healers += healer
                healerOf[healer] = jad
                nextHeal[healer] = world.tick + HEAL_TICKS
            }
        }
        for (healer in state.healers) healerTick(healer, jad)
        if (player.dead || player.hitpoints <= 0 || jad.target !== player) return
        val reaches = world.meleeCombat.reaches(jad, player)
        jad.face(player)
        when {
            reaches || world.meleeCombat.inShot(jad, player) -> world.npcs.stopFollowing(jad)
            else -> {
                if (jad.following !== player) world.npcs.follow(jad, player)
                return
            }
        }
        if (world.tick < jad.nextAttackTick) return
        attack(jad, state, player, reaches)
    }

    private fun attack(jad: Npc, state: JadState, player: Player, reaches: Boolean) {
        val styles = if (reaches) STYLES_IN_REACH else STYLES_AFAR
        val type = styles[world.random.between(0, styles.size - 1)]
        if (type.melee) {
            world.meleeCombat.swingAt(jad, player, world.tick, ANIM_MELEE, type, MAX_HIT)
            jad.nextAttackTick = world.tick + MELEE_TICKS
            return
        }
        jad.nextAttackTick = world.tick + state.attackSpeed
        jad.fightTick = world.tick
        jad.face(player)
        val magic = type == AttackType.MAGIC
        jad.effects.anim(if (magic) ANIM_MAGIC else ANIM_RANGED)
        if (magic) {
            jad.effects.spotanim(0, FightCaveMonsters.SPOTANIM_JAD_FIRE_LAUNCH, 0)
            world.zones.projectile(world.meleeCombat.flight(jad, player, FightCaveMonsters.SPOTANIM_JAD_FIRE_TRAVEL, HIT_DELAY))
        }
        val roll = world.combat.npcAttack(jad, player, type) ?: return
        val damage = if (CombatMath.rollHits(world.random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(world.random, MAX_HIT) else 0
        hits += Hit(jad, player, type, damage, world.tick + HIT_DELAY)
    }

    private fun healerTick(healer: Npc, jad: Npc) {
        // Attacked, it fights the player until it loses them.
        if (healer.target != null || healer.dead) return
        if (healer.leader !== jad) world.npcs.followNpc(healer, jad)
        if (!beside(healer, jad) || world.tick < (nextHeal[healer] ?: 0L)) return
        nextHeal[healer] = world.tick + HEAL_TICKS
        if (jad.hitpoints >= jad.maxHitpoints) return
        healer.effects.anim(FightCaveMonsters.ANIM_MEJKOT_HEAL)
        jad.hitpoints = minOf(jad.maxHitpoints, jad.hitpoints + world.random.between(HEAL_MIN, HEAL_MAX))
        jad.effects.spotanim(0, FightCaveMonsters.SPOTANIM_HEAL, 0)
        jad.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(jad.hitpoints, jad.maxHitpoints))
    }

    /** The tick [jad]'s first attack was due as his wave came in, for tests. */
    fun firstAttack(jad: Npc): Long? = jads[jad]?.firstAttack

    /** Each Jad's attack speed, for tests. */
    fun attackSpeed(jad: Npc): Int? = jads[jad]?.attackSpeed

    /** The healers [jad] summoned so far, for tests. */
    fun healersOf(jad: Npc): List<Npc> = jads[jad]?.healers?.toList() ?: emptyList()

    /** Drops the hits and state of the Jads and healers inside [area]: from [Inferno.end]. */
    fun dropHitsWithin(area: (x: Int, z: Int) -> Boolean) {
        hits.removeAll { area(it.jad.x, it.jad.z) }
        jads.keys.removeAll { area(it.x, it.z) }
        nextHeal.keys.removeAll { area(it.x, it.z) }
        healerOf.keys.removeAll { area(it.x, it.z) }
    }

    private fun died(inferno: Inferno, jad: Npc) {
        val state = jads.remove(jad)
        state?.healers?.forEach { if (world.npcs[it.index] === it) world.removeNpc(it) }
        hits.removeAll { it.jad === jad }
        val run = inferno.runAt(jad.x, jad.z) ?: return
        state?.healers?.let { healers -> run.monsters.removeAll { it in healers } }
    }

    private fun beside(healer: Npc, jad: Npc): Boolean {
        val dx = gap(healer.x, healer.type.size, jad.x, jad.type.size)
        val dz = gap(healer.z, healer.type.size, jad.z, jad.type.size)
        return maxOf(dx, dz) <= 1
    }

    private fun gap(a: Int, aSize: Int, b: Int, bSize: Int): Int = when {
        b >= a + aSize -> b - (a + aSize - 1)
        a >= b + bSize -> a - (b + bSize - 1)
        else -> 0
    }

    companion object {
        const val HEALER = 7701
        const val MAX_HIT = 113
        const val ATTACK_SPEED = 8
        const val TRIPLE_SPEED = 9
        const val TRIPLE_APART = 3
        const val MELEE_TICKS = 4
        const val HIT_DELAY = 3
        const val HEALERS = 5
        const val TRIPLE_HEALERS = 3
        const val HEAL_MIN = 15
        const val HEAL_MAX = 24
        const val HEAL_TICKS = 4L

        const val ANIM_MAGIC = 7592
        const val ANIM_RANGED = 7593
        const val ANIM_MELEE = 7590

        private val STYLES_IN_REACH = listOf(AttackType.STAB, AttackType.RANGED, AttackType.MAGIC)
        private val STYLES_AFAR = listOf(AttackType.RANGED, AttackType.MAGIC)

        /** Where the healers come in, from the Jad's south-west tile: one off each side, then the corners. Guessed. */
        val HEALER_OFFSETS = listOf(Tile(-1, 2), Tile(5, 2), Tile(2, -1), Tile(2, 5), Tile(-1, -1))
    }
}
