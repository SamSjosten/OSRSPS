package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile
import java.util.WeakHashMap

/**
 * The Inferno's three rocky supports and the Jal-Nibs that go for them (Inferno step IN1; the Wiki's Rocky support,
 * Jal-Nib and Inferno pages, read 2026-11-17).
 *
 * - Each support has 255 hitpoints. On the map it is the cache's multiloc `inferno_safespot1` to `_3` (30353-30355),
 *   which shows its 100%, 75%, 50% or 25% look by the varbits `inferno_safespot1_health` to `_3` (5655-5657): 0 to 63
 *   is the 100% look and 192 to 255 the 25% one, so the varbit counts the damage taken (inferred from the multilocs). Its
 *   hitpoints are an `inferno_invisible_3x3` NPC (7709, "Rocky support") on the same 3 by 3, which the Nibblers hit and
 *   whose health bar the player sees. The Wiki's map pins put the three at 2257,5349, 2274,5351 and 2267,5335.
 * - "When it reaches 0 health, it collapses, dealing damage to all monsters and players near it", "on adjacent tiles",
 *   and "Any remaining pillars collapse when the final enemy is slain [in wave 66], and deal half the player's current
 *   hp if the player is within one tile." The collapse shows `inferno_safespot_dying` (7710) playing
 *   `safe_spot_distructible_pillar_collapse` (7561).
 * - Jal-Nibs "spawn near each other about midway between the three pillars. They will then move towards one of the
 *   pillars to attack it, ignoring the player. If all pillars are destroyed, they will attack the player instead", their
 *   hits on a player landing every time ("100% accuracy"), max 4, every 4 ticks.
 *
 * GUESSED: a collapse's damage when the Nibblers bring a support down, the same half of current hitpoints as wave 66's,
 * to monsters as to the player; the Nibblers' hits on a support, 0 to 4 like their hits on a player; which support each
 * Nibbler picks (World.random, again when its support falls); the collapse shown for [COLLAPSE_TICKS]; a protection
 * prayer blocking a Nibbler's hit as it does any monster's melee.
 */
class InfernoPillars(private val world: World) {
    /** One of a run's supports while it stands. */
    class Pillar internal constructor(val index: Int, val npc: Npc, val loc: ZoneLoc, damage: Int) {
        /** The damage it has taken, which only grows: a support doesn't heal. */
        var damage: Int = damage
            internal set
    }

    private val targets = WeakHashMap<Npc, Pillar>()

    /** Collapses still shown, and the tick each goes. */
    private val collapses = LinkedHashMap<Npc, Long>()

    /** Hooks a support's death into its collapse: once, from [Inferno.register]. */
    fun register(inferno: Inferno) {
        world.npcScripts.onDeath(listOf(SUPPORT)) { npc ->
            val run = inferno.runAt(npc.x, npc.z) ?: return@onDeath
            val pillar = run.pillars.firstOrNull { it.npc === npc } ?: return@onDeath
            collapse(run, pillar)
        }
        // A burst, a barrage, a chinchompa's blast or a bow's bounce at a Nibbler beside a support passes it by, as a
        // click on it does: only the Nibblers bring it down (it has no Attack option; Codex review on #829).
        val shutOut = world.combat.shutOut
        world.combat.shutOut = { player, npc -> npc.type.id == SUPPORT || shutOut(player, npc) }
    }

    /**
     * Puts up [run]'s supports still standing by [player]'s varbits (all three on a new run). One that can't come in
     * for want of room (the world full of NPCs) is owed ([Inferno.Run.pillarsOwed]) and comes in on a later tick
     * ([tick]), so the run never goes on short of its cover (Codex review on #829).
     */
    fun raise(run: Inferno.Run, player: Player) {
        for (index in TILES.indices) if (damage(player, index) < HITPOINTS) run.pillarsOwed += index
        raiseOwed(run, player)
    }

    private fun raiseOwed(run: Inferno.Run, player: Player) {
        val type = world.npcTypes[SUPPORT] ?: return run.pillarsOwed.clear()
        for (index in run.pillarsOwed.toList()) {
            val damage = damage(player, index)
            if (damage >= HITPOINTS) {
                run.pillarsOwed -= index
                continue
            }
            val at = run.at(TILES[index])
            val npc = world.spawnNpc(NpcSpawn(type, at.x, at.z, 0, source = Inferno.SOURCE, respawns = false)) ?: continue
            run.pillarsOwed -= index
            npc.scaledHitpoints = HITPOINTS
            npc.hitpoints = HITPOINTS - damage
            // A support doesn't heal: no hitpoint comes back to it ever, so none of a Nibbler's damage is lost before
            // [tick] counts it (Codex review on #829).
            npc.hitpointsRegenTicks = Int.MAX_VALUE
            val loc = ZoneLoc(LOCS[index], LocShape.CENTREPIECE_STRAIGHT, 0, at.x, at.z, 0)
            world.locs.add(loc)
            run.pillars += Pillar(index, npc, loc, damage)
        }
    }

    /** Sets every support of [player]'s back to full, as a new run starts. */
    fun reset(player: Player) {
        for (index in TILES.indices) setDamage(player, index, 0)
    }

    /** The damage support [index] (0 to 2) has taken in [player]'s run, by its varbit; 0 without it. */
    fun damage(player: Player, index: Int): Int {
        val varbit = VARBITS[index]
        return if (player.vars.types.varbit(varbit) != null) player.vars.getVarbit(varbit) else 0
    }

    private fun setDamage(player: Player, index: Int, damage: Int) {
        val varbit = VARBITS[index]
        if (player.vars.types.varbit(varbit) != null) player.vars.setVarbit(varbit, damage.coerceIn(0, HITPOINTS))
    }

    /** The supports' and Nibblers' part of [run]'s tick, fought by [player]: the varbits kept up, each Nibbler's turn. */
    fun tick(run: Inferno.Run, player: Player) {
        run.pillars.removeAll { it.npc.dead || world.npcs[it.npc.index] !== it.npc }
        if (run.pillarsOwed.isNotEmpty()) raiseOwed(run, player)
        save(run, player)
        for (npc in run.monsters) if (npc.type.id == InfernoMonster.NIBBLER.type && !npc.dead) nibbler(run, npc, player)
    }

    /**
     * [run]'s supports' damage kept in [player]'s varbits: each tick, and as they log out, so a Nibbler's hit on their
     * last tick isn't lost (Codex review on #829).
     */
    fun save(run: Inferno.Run, player: Player) {
        for (pillar in run.pillars) {
            // Hitpoints given back by anything else don't count either: the support keeps its damage.
            pillar.damage = maxOf(pillar.damage, HITPOINTS - pillar.npc.hitpoints)
            pillar.npc.hitpoints = HITPOINTS - pillar.damage
            if (pillar.damage != damage(player, pillar.index)) setDamage(player, pillar.index, pillar.damage)
        }
    }

    /** The shown collapses going once their time is up. */
    fun process() {
        if (collapses.isEmpty()) return
        val iterator = collapses.iterator()
        while (iterator.hasNext()) {
            val (npc, until) = iterator.next()
            if (world.tick < until) continue
            iterator.remove()
            if (world.npcs[npc.index] === npc) world.removeNpc(npc)
        }
    }

    private fun nibbler(run: Inferno.Run, nibbler: Npc, player: Player) {
        if (run.pillars.isEmpty()) {
            // One owed is coming in: wait for it.
            if (run.pillarsOwed.isNotEmpty()) return
            // No support left: the player is the target, chased and hit by [nibblerHits].
            targets.remove(nibbler)
            if (nibbler.target !== player) world.meleeCombat.aggress(nibbler, player, world.tick)
            return nibblerHits(nibbler, player)
        }
        var pillar = targets[nibbler]
        if (pillar == null || pillar !in run.pillars) {
            pillar = run.pillars[world.random.nextInt(run.pillars.size)]
            targets[nibbler] = pillar
            world.npcs.followNpc(nibbler, pillar.npc)
        }
        // A player's hit or splash makes it turn on them, which would hold it before the supports for good: it goes on
        // ignoring the player while any support stands (Codex review on #829).
        if (nibbler.target != null) {
            nibbler.target = null
            world.npcs.stopFollowing(nibbler)
        }
        // Nor does being hit stand it still: the hold an attack puts on it would keep it from its support.
        nibbler.holdTicks = 0
        if (!beside(nibbler, pillar.npc)) {
            if (nibbler.leader !== pillar.npc) world.npcs.followNpc(nibbler, pillar.npc)
            return
        }
        if (world.tick < nibbler.nextAttackTick) return
        nibbler.nextAttackTick = world.tick + NIBBLER_SPEED
        nibbler.effects.anim(ANIM_NIBBLER_ATTACK)
        world.meleeCombat.hit(pillar.npc, null, world.random.between(0, NIBBLER_MAX), world.tick)
    }

    /** A Nibbler's swing at [player], once nothing else is left to hit: it never misses, but a prayer stops it. */
    fun nibblerHits(nibbler: Npc, player: Player) {
        if (player.dead || player.hitpoints <= 0) return
        nibbler.face(player)
        if (!world.meleeCombat.reaches(nibbler, player)) {
            if (nibbler.following !== player) world.npcs.follow(nibbler, player)
            return
        }
        world.npcs.stopFollowing(nibbler)
        if (world.tick < nibbler.nextAttackTick) return
        nibbler.nextAttackTick = world.tick + NIBBLER_SPEED
        nibbler.fightTick = world.tick
        nibbler.effects.anim(ANIM_NIBBLER_ATTACK)
        val damage = world.random.between(0, NIBBLER_MAX)
        world.meleeCombat.hitPlayer(player, nibbler, if (world.prayers.protects(player, AttackType.CRUSH)) 0 else damage, world.tick)
    }

    /** Every support of [run]'s still standing falls, as wave 66 ends. */
    fun collapseAll(run: Inferno.Run) {
        run.pillarsOwed.clear()
        for (pillar in run.pillars.toList()) collapse(run, pillar)
    }

    /**
     * [pillar] falls: its loc and hitpoints go, the collapse shows, and the player and every monster within a tile of it
     * lose half their hitpoints left.
     */
    private fun collapse(run: Inferno.Run, pillar: Pillar) {
        run.pillars.remove(pillar)
        world.locs.remove(pillar.loc)
        val tile = Tile(pillar.npc.x, pillar.npc.z)
        if (world.npcs[pillar.npc.index] === pillar.npc) world.removeNpc(pillar.npc)
        val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner }
        if (player != null) setDamage(player, pillar.index, HITPOINTS)
        world.npcTypes[COLLAPSING]?.let { type ->
            world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = Inferno.SOURCE, respawns = false))?.let { npc ->
                npc.effects.anim(ANIM_COLLAPSE)
                collapses[npc] = world.tick + COLLAPSE_TICKS
            }
        }
        if (player != null && !player.dead && near(tile, player.x, player.z, 1)) {
            world.meleeCombat.hitPlayer(player, null, player.hitpoints / 2, world.tick)
        }
        for (npc in run.monsters.toList()) {
            if (npc.dead || world.npcs[npc.index] !== npc) continue
            if (!near(tile, npc.x, npc.z, npc.type.size)) continue
            world.meleeCombat.hit(npc, null, npc.hitpoints / 2, world.tick)
        }
    }

    /** Whether something of [size] at [x], [z] is within a tile of the support whose south-west tile is [tile]. */
    private fun near(tile: Tile, x: Int, z: Int, size: Int): Boolean =
        gap(x, size, tile.x, SIZE) <= 1 && gap(z, size, tile.z, SIZE) <= 1

    /** Whether [nibbler] stands beside [support], not on a corner (melee reach). */
    private fun beside(nibbler: Npc, support: Npc): Boolean {
        val gx = gap(nibbler.x, nibbler.type.size, support.x, SIZE)
        val gz = gap(nibbler.z, nibbler.type.size, support.z, SIZE)
        return (gx == 1 && gz == 0) || (gx == 0 && gz == 1)
    }

    private fun gap(a: Int, aSize: Int, b: Int, bSize: Int): Int = when {
        b >= a + aSize -> b - (a + aSize - 1)
        a >= b + bSize -> a - (b + bSize - 1)
        else -> 0
    }

    companion object {
        /** `inferno_invisible_3x3`, the support's hitpoints. */
        const val SUPPORT = 7709

        /** `inferno_safespot_dying`, the support falling. */
        const val COLLAPSING = 7710
        const val ANIM_COLLAPSE = 7561
        const val ANIM_NIBBLER_ATTACK = 7574

        const val HITPOINTS = 255
        const val SIZE = 3
        const val NIBBLER_SPEED = 4
        const val NIBBLER_MAX = 4

        /** How long a collapse shows. Guessed. */
        const val COLLAPSE_TICKS = 4

        /** The supports' south-west tiles on the real map, west, north-east and south (the Wiki's Rocky support map). */
        val TILES: List<Tile> = listOf(Tile(2257, 5349), Tile(2274, 5351), Tile(2267, 5335))

        /** `inferno_safespot1` to `_3`, the multilocs each support shows. */
        val LOCS: List<Int> = listOf(30353, 30354, 30355)

        /** `inferno_safespot1_health` to `_3`: the damage each support has taken. */
        val VARBITS: List<Int> = listOf(5655, 5656, 5657)
    }
}
