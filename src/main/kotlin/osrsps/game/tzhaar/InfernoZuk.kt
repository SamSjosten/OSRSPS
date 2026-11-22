package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.MeleeCombat
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.map.Tile
import java.util.WeakHashMap

/** The words as wave 69 starts (keys only; the words are in data/dialogue.txt). */
object InfernoZukLines : Lines("inferno_zuk", "Transcript:TzHaar-Ket-Keh, The Inferno") {
    val SHAKING = line("shaking", "wave 69's message box as the cutscene starts: a great power is shaking the cavern")
    val BREAKING = line("breaking", "TzHaar-Ket-Keh straight after: TzKal-Zuk's prison is breaking down, nothing he can do now")
}

/**
 * TzKal-Zuk's fight, wave 69 (Inferno step IN4; the Wiki's TzKal-Zuk, Ancestral Glyph, Jal-MejJak and Inferno/Strategies
 * pages, read 2026-11-17).
 *
 * - **TzKal-Zuk** (7706, 1200 hitpoints) stands behind the arena's north wall and can't be reached with melee. Every
 *   [ZUK_SPEED] ticks ([ZUK_ENRAGED_SPEED] once enraged) he fires one typeless shot, "a single roll between 0 and"
 *   148, whose accuracy is the average of his magic and ranged rolls against the average of the player's magic and
 *   ranged defence (Mod Ash). No prayer helps; only the **Ancestral Glyph** does.
 * - **The Ancestral Glyph** (7707, 600 hitpoints) floats on the lava in front of him, "moving on the east-west axis from
 *   one end to the other repeatedly" at a walking pace, "pausing for approximately 5 ticks once it hits the edge". A
 *   player "behind a 5x3 area of the shield" (its own 3 tiles and one either side, the first 3 rows along the northern
 *   edge) takes nothing from Zuk. Zuk's shots never hurt it; the sets and the Jad do, until the player attacks them.
 *   At 0 it falls and the player has no cover.
 * - **Sets**: a Jal-Xil (7702) and a Jal-Zek (7703) come in "after the shield has done a complete rotation", then every
 *   3:30 ([SET_TICKS]). At 600 hitpoints the timer pauses and gains 1:45 ([INTERLUDE_TICKS]); at 480 a JalTok-Jad (7704)
 *   comes in and the timer runs again. Each attacks the glyph until the player attacks it, then the player.
 * - **Enraged** at 240 or less: Zuk attacks 3 ticks faster and four **Jal-MejJak**s (7708) come in on the lava in front
 *   of him. Each heals him 15 to 24 every 3 ticks until attacked; attacked, it throws lava balls at the player's area
 *   instead, 5 to 10 each.
 * - "Any remaining minions when TzKal-Zuk is killed will automatically die with it" ([Inferno]'s end of the run).
 *
 * GUESSED: every tile ([GLYPH_Z], the glyph's ends, the sets', the Jad's and the MejJaks'); the 5x3 safe area's rows (the
 *   top three of the arena) and that it is checked as Zuk fires; which way the glyph sets off (World.random, kept for the
 *   run); Zuk's first shot [ZUK_FIRST_ATTACK] ticks into the wave and his shot's [ZUK_HIT_DELAY]; the sets' and Jad's
 *   swings at the glyph (their speeds, a plain 0-to-max roll, their attack animations, no projectile); a lava ball
 *   landing [LAVA_DELAY] ticks after it is thrown, on the player's tile then and the 8 round it, and flying as the
 *   lavabeast's bomb ([LAVA_PROJECTILE]); the MejJak's attack animation (the spinolyp's it shares a model with); that the
 *   Zuk-wave Jad has five healers.
 */
class InfernoZuk(private val world: World) {
    /** The fight in a run: the glyph, the timers, and who is still on the glyph. */
    class Fight internal constructor(val zuk: Npc, val glyph: Npc?, var glyphStep: Int) {
        /** The tick the glyph moves again after resting at an end. */
        var glyphRestUntil = 0L

        /** Ends the glyph has reached; the first set comes as it gets back to where it started (2). */
        var glyphTurns = 0

        var nextZukAttack = 0L

        /** The tick the next set comes in; -1 before the first. */
        var nextSet = -1L

        /** Ticks left on the set timer while it is paused (600 to 480 hitpoints); -1 when running. */
        var setPausedLeft = -1L

        var interlude = false
        var jadIn = false
        var enraged = false

        /** The Jad and the MejJaks' tiles due at their thresholds that had no room yet: tried each tick (Codex review on #851). */
        var jadOwed = false
        val mejJaksOwed: MutableList<Tile> = ArrayList()

        /** Monsters that attack the glyph until the player attacks them. */
        val onGlyph: MutableSet<Npc> = LinkedHashSet()
        val mejJaks: MutableList<Npc> = ArrayList()
        internal val lavaBalls: MutableList<LavaBall> = ArrayList()
    }

    internal class LavaBall(val from: Npc, val x: Int, val z: Int, val landTick: Long)

    private val fights = WeakHashMap<Inferno.Run, Fight>()

    /** Keeps players' attacks off the glyph, a burst or barrage at a MejJak beside it too: only the minions wear it down (Codex review on #851). */
    fun register() {
        val shutOut = world.combat.shutOut
        world.combat.shutOut = { player, npc -> npc.type.id == GLYPH || shutOut(player, npc) }
    }
    private val nextHeal = WeakHashMap<Npc, Long>()

    /** [run]'s fight, once wave 69 is in. */
    fun fightOf(run: Inferno.Run): Fight? = fights[run]

    /** Whether [npc] is still attacking the glyph (its turn is [tick]'s, not its own mechanics'). */
    fun onGlyph(run: Inferno.Run, npc: Npc): Boolean = fights[run]?.onGlyph?.contains(npc) == true

    /** Wave 69 is in with [zuk]: the glyph comes off the wall, the lines, and the timers. */
    fun waveIn(inferno: Inferno, run: Inferno.Run, player: Player, zuk: Npc) {
        val step = if (world.random.chance(1, 2)) 1 else -1
        val start = run.at(Tile(if (step > 0) GLYPH_WEST else GLYPH_EAST, GLYPH_Z))
        val glyph = inferno.spawn(player, run, GLYPH, start, fight = false)?.also {
            it.passesNpcs = true
            // The glyph isn't one of the wave's monsters to kill.
            run.monsters -= it
        }
        val fight = Fight(zuk, glyph, step)
        fight.nextZukAttack = world.tick + ZUK_FIRST_ATTACK
        fights[run] = fight
        val ketKeh = world.npcTypes[KetKeh.BEFORE_ZUK] ?: world.npcTypes[KetKeh.TZHAAR_KET_KEH]
        world.scripts.start(player) {
            world.dialogues.start(this) {
                mesbox(InfernoZukLines.SHAKING)
                if (ketKeh != null) chatNpc(ketKeh, ketKeh.name, ChatAnim.ANGRY, InfernoZukLines.BREAKING)
            }
        }
    }

    /** [run]'s fight ends (Zuk's death, a death, a walk out): the glyph goes with it. */
    fun end(run: Inferno.Run) {
        val fight = fights.remove(run) ?: return
        // A shot on its way would land outside after the run is over, where a death is no longer safe (Codex review on #851).
        zukShots.removeAll { it.zuk === fight.zuk }
        fight.glyph?.let { if (world.npcs[it.index] === it) world.removeNpc(it) }
    }

    /** The fight's turn in [run] against [player], before the monsters' own. */
    fun tick(inferno: Inferno, run: Inferno.Run, player: Player) {
        val fight = fights[run] ?: return
        val zuk = fight.zuk
        if (zuk.dead || player.dead || player.hitpoints <= 0) return
        moveGlyph(run, fight)
        thresholds(inferno, run, player, fight)
        if (fight.nextSet >= 0 && fight.setPausedLeft < 0 && world.tick >= fight.nextSet) {
            spawnSet(inferno, run, player, fight)
            fight.nextSet = world.tick + SET_TICKS
        }
        glyphAttackers(run, player, fight)
        mejJaks(player, fight)
        if (world.tick >= fight.nextZukAttack) {
            fight.nextZukAttack = world.tick + if (fight.enraged) ZUK_ENRAGED_SPEED else ZUK_SPEED
            zukAttack(run, player, fight)
        }
    }

    /** One tile a tick along the glyph's track, resting at each end. */
    private fun moveGlyph(run: Inferno.Run, fight: Fight) {
        val glyph = fight.glyph ?: return
        if (glyph.dead || world.npcs[glyph.index] !== glyph || world.tick < fight.glyphRestUntil) return
        val west = run.at(Tile(GLYPH_WEST, GLYPH_Z)).x
        val east = run.at(Tile(GLYPH_EAST, GLYPH_Z)).x
        val x = (glyph.x + fight.glyphStep).coerceIn(west, east)
        if (!world.npcs.stepTo(glyph, x, glyph.z)) world.npcs.teleport(glyph, x, glyph.z)
        if (x == west || x == east) {
            fight.glyphStep = -fight.glyphStep
            fight.glyphRestUntil = world.tick + GLYPH_REST
            fight.glyphTurns++
            // The first set, unless 600 came first: then it waits out the pause, which 480 ends (Codex review on #851).
            if (fight.glyphTurns == 2 && fight.nextSet < 0 && fight.setPausedLeft < 0) fight.nextSet = world.tick
        }
    }

    /** Whether [player] stands in the glyph's cover: the 5 columns round it, in the arena's top 3 rows. */
    fun covered(run: Inferno.Run, player: Player): Boolean {
        val glyph = fights[run]?.glyph ?: return false
        if (glyph.dead || world.npcs[glyph.index] !== glyph) return false
        val top = run.at(Tile(0, SAFE_TOP_Z)).z
        return player.x in glyph.x - 1..glyph.x + GLYPH_SIZE && player.z in top - SAFE_ROWS + 1..top
    }

    private fun zukAttack(run: Inferno.Run, player: Player, fight: Fight) {
        val zuk = fight.zuk
        zuk.effects.anim(ANIM_ZUK_ATTACK)
        zuk.fightTick = world.tick
        world.zones.projectile(world.meleeCombat.flight(zuk, player, PROJECTILE_ZUK, ZUK_HIT_DELAY))
        if (covered(run, player)) return
        val magic = world.combat.npcAttack(zuk, player, AttackType.MAGIC) ?: return
        val ranged = world.combat.npcAttack(zuk, player, AttackType.RANGED) ?: return
        val hits = CombatMath.rollHits(world.random, (magic.attackRoll + ranged.attackRoll) / 2, (magic.defenceRoll + ranged.defenceRoll) / 2)
        val damage = if (hits) CombatMath.rollDamage(world.random, ZUK_MAX_HIT) else 0
        zukShots += ZukShot(zuk, player, damage, world.tick + ZUK_HIT_DELAY)
    }

    private class ZukShot(val zuk: Npc, val player: Player, val damage: Int, val landTick: Long)

    private val zukShots = ArrayList<ZukShot>()

    /** Zuk's shots and the lava balls landing: once a tick, from [Inferno.process]. */
    fun process() {
        if (zukShots.isNotEmpty()) {
            val iterator = zukShots.iterator()
            while (iterator.hasNext()) {
                val shot = iterator.next()
                if (world.tick < shot.landTick) continue
                iterator.remove()
                if (shot.zuk.dead || shot.player.loggedOut || shot.player.dead) continue
                if (world.instances.at(shot.player.x, shot.player.z)?.contains(shot.zuk.x, shot.zuk.z) != true) continue
                world.meleeCombat.hitPlayer(shot.player, shot.zuk, shot.damage, world.tick)
            }
        }
        for ((run, fight) in fights.entries.toList()) {
            if (fight.lavaBalls.isEmpty()) continue
            val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner } ?: continue
            val iterator = fight.lavaBalls.iterator()
            while (iterator.hasNext()) {
                val ball = iterator.next()
                if (world.tick < ball.landTick) continue
                iterator.remove()
                if (player.dead || player.hitpoints <= 0) continue
                if (maxOf(kotlin.math.abs(player.x - ball.x), kotlin.math.abs(player.z - ball.z)) > LAVA_RADIUS) continue
                world.meleeCombat.hitPlayer(player, ball.from, world.random.between(LAVA_MIN, LAVA_MAX), world.tick)
            }
        }
    }

    /** Zuk's hitpoints thresholds: 600 pauses the set timer, 480 brings the Jad, 240 the enrage and the MejJaks. */
    private fun thresholds(inferno: Inferno, run: Inferno.Run, player: Player, fight: Fight) {
        val hp = fight.zuk.hitpoints
        if (!fight.interlude && hp <= INTERLUDE_HP) {
            fight.interlude = true
            // Before the first set is due (the glyph not yet round), the pause holds it back the 1:45 too.
            fight.setPausedLeft = (if (fight.nextSet >= 0) maxOf(0L, fight.nextSet - world.tick) else 0L) + INTERLUDE_TICKS
        }
        if (!fight.jadIn && hp <= JAD_HP) {
            fight.jadIn = true
            fight.jadOwed = true
            if (fight.setPausedLeft >= 0) {
                fight.nextSet = world.tick + fight.setPausedLeft
                fight.setPausedLeft = -1
            }
        }
        if (!fight.enraged && hp <= ENRAGE_HP) {
            fight.enraged = true
            fight.nextZukAttack = minOf(fight.nextZukAttack, world.tick + ZUK_ENRAGED_SPEED)
            fight.mejJaksOwed += MEJJAK_TILES
        }
        if (fight.jadOwed) {
            inferno.spawn(player, run, FINAL_JAD, run.at(JAD_TILE), fight = false)?.let {
                fight.onGlyph += it
                fight.jadOwed = false
            }
        }
        val owed = fight.mejJaksOwed.iterator()
        while (owed.hasNext()) {
            val healer = inferno.spawn(player, run, MEJJAK, run.at(owed.next()), fight = false) ?: break
            owed.remove()
            fight.mejJaks += healer
            nextHeal[healer] = world.tick + MEJJAK_SPEED
        }
    }

    private fun spawnSet(inferno: Inferno, run: Inferno.Run, player: Player, fight: Fight) {
        for ((type, tile) in listOf(FINAL_RANGER to RANGER_TILE, FINAL_MAGER to MAGER_TILE)) {
            inferno.spawn(player, run, type, run.at(tile), fight = false)?.let { fight.onGlyph += it }
        }
    }

    /** The sets and the Jad at the glyph, until the player attacks one: then it goes for them. */
    private fun glyphAttackers(run: Inferno.Run, player: Player, fight: Fight) {
        val glyph = fight.glyph?.takeIf { !it.dead && world.npcs[it.index] === it }
        val iterator = fight.onGlyph.iterator()
        while (iterator.hasNext()) {
            val npc = iterator.next()
            if (npc.dead || world.npcs[npc.index] !== npc) {
                iterator.remove()
                continue
            }
            if (npc.lastAttacker === player || glyph == null) {
                iterator.remove()
                world.meleeCombat.aggress(npc, player, world.tick)
                continue
            }
            if (world.tick < npc.nextAttackTick) continue
            val def = world.combat.npcCombat[npc.type] ?: continue
            npc.nextAttackTick = world.tick + def.attackSpeed
            npc.fightTick = world.tick
            if (def.anims.attack >= 0) npc.effects.anim(def.anims.attack)
            val damage = world.random.between(0, def.maxHit)
            world.meleeCombat.hit(glyph, null, damage, world.tick)
        }
    }

    /** The MejJaks: healing Zuk until attacked, then lava at the player's feet. */
    private fun mejJaks(player: Player, fight: Fight) {
        fight.mejJaks.removeAll { it.dead || world.npcs[it.index] !== it }
        val zuk = fight.zuk
        for (healer in fight.mejJaks) {
            if (world.tick < (nextHeal[healer] ?: 0L)) continue
            nextHeal[healer] = world.tick + MEJJAK_SPEED
            healer.effects.anim(ANIM_MEJJAK_ATTACK)
            if (healer.lastAttacker !== player) {
                if (zuk.hitpoints >= zuk.maxHitpoints) continue
                zuk.hitpoints = minOf(zuk.maxHitpoints, zuk.hitpoints + world.random.between(HEAL_MIN, HEAL_MAX))
                zuk.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(zuk.hitpoints, zuk.maxHitpoints))
                continue
            }
            healer.face(player)
            world.zones.projectile(world.meleeCombat.flight(healer, player, LAVA_PROJECTILE, LAVA_DELAY.toInt()))
            fight.lavaBalls += LavaBall(healer, player.x, player.z, world.tick + LAVA_DELAY)
        }
    }

    companion object {
        const val ZUK = 7706
        const val GLYPH = 7707
        const val MEJJAK = 7708
        const val FINAL_RANGER = 7702
        const val FINAL_MAGER = 7703
        const val FINAL_JAD = 7704
        const val FINAL_JAD_HEALER = 7705

        const val ZUK_MAX_HIT = 148
        const val ZUK_SPEED = 10
        const val ZUK_ENRAGED_SPEED = 7
        const val ZUK_FIRST_ATTACK = 14L
        const val ZUK_HIT_DELAY = 4
        const val ANIM_ZUK_ATTACK = 7566
        const val PROJECTILE_ZUK = 1375

        const val INTERLUDE_HP = 600
        const val JAD_HP = 480
        const val ENRAGE_HP = 240

        /** 3:30 between sets, and the 1:45 the interlude adds. */
        const val SET_TICKS = 350L
        const val INTERLUDE_TICKS = 175L

        const val GLYPH_SIZE = 3
        const val GLYPH_REST = 5L

        /** The glyph's south row, over the lava at the arena's north edge, and its track's ends (south-west tiles). Guessed. */
        const val GLYPH_Z = 5359
        const val GLYPH_WEST = 2257
        const val GLYPH_EAST = 2283

        /** The arena's top row, and how many rows from it the glyph covers. Guessed. */
        const val SAFE_TOP_Z = 5358
        const val SAFE_ROWS = 3

        /** Where the sets and the Jad come in, "behind" the player: the mager west, the ranger east. Guessed. */
        val MAGER_TILE = Tile(2257, 5347)
        val RANGER_TILE = Tile(2281, 5348)
        val JAD_TILE = Tile(2269, 5344)

        /** The MejJaks, on the lava in front of Zuk. Guessed. */
        val MEJJAK_TILES = listOf(Tile(2263, 5361), Tile(2265, 5360), Tile(2277, 5360), Tile(2279, 5361))
        const val MEJJAK_SPEED = 3L
        const val HEAL_MIN = 15
        const val HEAL_MAX = 24
        const val ANIM_MEJJAK_ATTACK = 2868

        /** The lavabeast's bomb, flying for a MejJak's lava ball. Guessed. */
        const val LAVA_PROJECTILE = 1403
        const val LAVA_DELAY = 3L
        const val LAVA_RADIUS = 1
        const val LAVA_MIN = 5
        const val LAVA_MAX = 10

        /** The Jal-Nib-Rek pet's item: 1 in 100 with Zuk's death, 1 in 75 on a Slayer task, 1 in 100 for an exchanged cape. */
        const val PET = 21291
        const val PET_ODDS = 100
        const val PET_ODDS_ON_TASK = 75
    }
}
