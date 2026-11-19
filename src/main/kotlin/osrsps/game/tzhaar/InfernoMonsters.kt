package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.Movement
import osrsps.game.combat.AttackType
import osrsps.game.npc.Npc
import osrsps.map.Tile
import java.util.WeakHashMap

/**
 * The Inferno's monsters' own mechanics (Inferno step IN2; the OSRS Wiki page named after each, read 2026-11-17). Their
 * levels, bonuses, max hits and animations are npc-combat.json's; what each does beyond an ordinary monster is here:
 *
 * - **Jal-MejRah** (7692): ranged from up to 4 tiles. Unless the player prays Protect from Missiles, each attack drains
 *   3 run energy ("They drain 3 run energy per hit") and has a chance to lower every combat stat by 1. GUESSED: the
 *   drains come as it fires (the prayer counted then, as for the hit); the stat drain's chance, 1 in [BAT_STAT_DRAIN_ODDS];
 *   the Wiki's boost to its own stats is left out (our NPCs keep fixed levels).
 * - **Jal-Ak** (7693): attacks every 6 ticks, its style picked 3 ticks before from the player's protection prayer:
 *   ranged against Protect from Magic, magic against Protect from Missiles, a random one of the two with no prayer or
 *   Protect from Melee; in reach and without Protect from Melee "there is a random chance" of melee instead. The
 *   prayer counts as it fires, so switching first blocks it. On death it splits into Jal-AkRek-Xil, -Mej and -Ket
 *   (7695, 7694, 7696), which the wave waits for. GUESSED: the melee chance, 1 in [BLOB_MELEE_ODDS]; where the three come in
 *   (its tile and the ones east and north of it).
 * - **Jal-ImKot** (7697): melee only. It digs 50 ticks after the wave starts and every 40 to 60 after, unless it has
 *   been fighting in reach, which puts the next dig off (not in the wave's first 30 ticks); it comes up beside the
 *   player and swings 6 ticks after. It can't be hit while under. GUESSED: its jalimkot_digdown animation shown for
 *   [DIG_TICKS] before it is gone from sight (it does nothing else meanwhile), under for [UNDER_TICKS], coming up
 *   south of the player (else north, west or east, the first it can stand on), and "enters combat" read as swinging
 *   in reach.
 * - **Jal-Xil** (7698): "exactly the same as the Tok-Xil": ranged, and in reach a crush (max 19) on any tile beside it,
 *   diagonals too.
 * - **Jal-Zek** (7699): magic, and in reach a stab (max 52) on any tile beside it, diagonals too. Instead of an attack,
 *   1 in 10 (Mod Ash), it revives a monster of the wave that died, once each, at half its hitpoints, in the arena's
 *   centre-east; it stands still for it and attacks again 8 ticks later, not moving till then. GUESSED: Nibblers and other Jal-Zeks aren't
 *   revived; the revived monster's short wait ([REVIVED_WAIT]); where it comes back ([REVIVE_TILE]).
 *
 * They fight through [Npc.scriptedAttacks], driven from [Inferno.process] by [tick]. The Jal-AkRek have nothing of
 * their own and fight as the engine's ordinary monsters.
 */
class InfernoMonsters(private val world: World) {
    /** Hooks the split and the record of the dead into the world: once, from [Inferno.register]. */
    fun register(inferno: Inferno) {
        world.npcScripts.onDeath(listOf(InfernoMonster.BLOB.type)) { split(inferno, it) }
        world.npcScripts.onDeath(REVIVABLE) { npc ->
            val run = inferno.runAt(npc.x, npc.z) ?: return@onDeath
            if (npc !in revived) run.slain += npc.type.id
        }
    }

    /** A Jal-Ak's style for its next attack, picked 3 ticks before it. */
    private val blobStyles = WeakHashMap<Npc, AttackType>()

    /** When each Jal-ImKot next digs, when one digging goes from sight, and when one under comes up. */
    private val nextDig = WeakHashMap<Npc, Long>()
    private val hideAt = WeakHashMap<Npc, Long>()
    private val surfaceAt = WeakHashMap<Npc, Long>()

    /** The style of each scripted monster's last attack, for tests. */
    internal val lastStyle = WeakHashMap<Npc, AttackType>()

    /** How many times each Jal-ImKot has dug, for tests. */
    internal val digs = WeakHashMap<Npc, Int>()

    /** Each Jal-Zek reviving, and the tick it may move again. */
    private val revivingUntil = WeakHashMap<Npc, Long>()

    /** Monsters a Jal-Zek has brought back: never again. */
    private val revived = WeakHashMap<Npc, Boolean>()

    /** Whether a monster of [typeId] fights through [tick] rather than the engine's own swings. */
    fun scripted(typeId: Int): Boolean = typeId in SCRIPTED

    /** [npc]'s turn in [run] against [player], who it holds: walking, and an attack when one is due. */
    fun tick(inferno: Inferno, run: Inferno.Run, npc: Npc, player: Player) {
        if (npc.dead || player.dead || player.hitpoints <= 0) return
        if (npc.type.id == InfernoMonster.MELEE.type && imKot(run, npc, player)) return
        if (npc.hidden || npc.target !== player) return
        // A Jal-Zek bringing one back stands still until it's done (Codex review on #833).
        val still = revivingUntil[npc]
        if (still != null) {
            if (world.tick < still) return world.npcs.stopFollowing(npc)
            revivingUntil.remove(npc)
        }
        val id = npc.type.id
        // The Jal-Xil's and Jal-Zek's melee reaches diagonals too, but not round a corner (Codex review on #833).
        val inReach = if (id == InfernoMonster.RANGER.type || id == InfernoMonster.MAGE.type) besideAny(npc, player) && world.meleeCombat.sees(npc, player) else world.meleeCombat.reaches(npc, player)
        val shoots = id != InfernoMonster.MELEE.type
        npc.face(player)
        when {
            inReach -> world.npcs.stopFollowing(npc)
            shoots && world.meleeCombat.inShot(npc, player) -> world.npcs.stopFollowing(npc)
            else -> {
                if (npc.following !== player) world.npcs.follow(npc, player)
                return
            }
        }
        if (id == InfernoMonster.BLOB.type) {
            if (world.tick == npc.nextAttackTick - BLOB_READS_AHEAD) blobStyles[npc] = blobStyle(player, inReach)
            // Due with no read made (its first attack, or it only now came in shot): it reads now and attacks 3 ticks
            // on, so a prayer switch can always answer it (Codex review on #833).
            if (world.tick >= npc.nextAttackTick && npc !in blobStyles) {
                blobStyles[npc] = blobStyle(player, inReach)
                npc.nextAttackTick = world.tick + BLOB_READS_AHEAD
                return
            }
        }
        if (world.tick < npc.nextAttackTick) return
        when (id) {
            InfernoMonster.BAT.type -> {
                world.meleeCombat.fireAt(npc, player, world.tick)
                batDrain(player, protected = world.prayers.protects(player, AttackType.RANGED))
            }
            InfernoMonster.BLOB.type -> {
                val style = blobStyles.remove(npc) ?: blobStyle(player, inReach)
                lastStyle[npc] = when {
                    style.melee && inReach -> AttackType.CRUSH.also { world.meleeCombat.swingAt(npc, player, world.tick, ANIM_BLOB_MELEE, it) }
                    style == AttackType.RANGED -> AttackType.RANGED.also { world.meleeCombat.fireAt(npc, player, world.tick, BLOB_RANGED, it) }
                    else -> AttackType.MAGIC.also { world.meleeCombat.fireAt(npc, player, world.tick, BLOB_MAGIC, it) }
                }
            }
            InfernoMonster.MELEE.type -> {
                world.meleeCombat.swingAt(npc, player, world.tick)
                // In combat: the next dig is put off, except in the wave's first 30 ticks.
                if (world.tick >= run.waveStartTick + DIG_NO_RESET_TICKS) nextDig[npc] = world.tick + digGap()
            }
            InfernoMonster.RANGER.type -> if (inReach) {
                world.meleeCombat.swingAt(npc, player, world.tick, ANIM_XIL_MELEE, AttackType.CRUSH, XIL_MELEE_MAX)
                lastStyle[npc] = AttackType.CRUSH
            } else {
                world.meleeCombat.fireAt(npc, player, world.tick)
                lastStyle[npc] = AttackType.RANGED
            }
            InfernoMonster.MAGE.type -> when {
                world.random.chance(1, ZEK_REVIVE_ODDS) && revive(inferno, run, npc, player) -> Unit
                inReach -> {
                    world.meleeCombat.swingAt(npc, player, world.tick, ANIM_ZEK_MELEE, AttackType.STAB, ZEK_MELEE_MAX)
                    lastStyle[npc] = AttackType.STAB
                }
                else -> {
                    world.meleeCombat.fireAt(npc, player, world.tick)
                    lastStyle[npc] = AttackType.MAGIC
                }
            }
        }
    }

    /** A Jal-Ak's style against [player]'s prayer, 3 ticks ahead of its attack. */
    private fun blobStyle(player: Player, inReach: Boolean): AttackType {
        val prayers = world.prayers
        return blobStyle(prayers.protects(player, AttackType.MAGIC), prayers.protects(player, AttackType.RANGED), prayers.protects(player, AttackType.CRUSH), inReach)
    }

    /** A Jal-Ak's style against the protection prayers up: magic, missiles, melee. */
    internal fun blobStyle(magic: Boolean, missiles: Boolean, melee: Boolean, inReach: Boolean): AttackType {
        if (inReach && !melee && world.random.chance(1, BLOB_MELEE_ODDS)) return AttackType.CRUSH
        return when {
            magic -> AttackType.RANGED
            missiles -> AttackType.MAGIC
            world.random.chance(1, 2) -> AttackType.MAGIC
            else -> AttackType.RANGED
        }
    }

    /** The style a Jal-Ak has read for its next attack, null before it reads; for tests. */
    internal fun readStyle(blob: Npc): AttackType? = blobStyles[blob]

    /** Has a Jal-ImKot dig as soon as it is out of reach; for tests. */
    internal fun digNow(imKot: Npc) {
        nextDig[imKot] = world.tick
    }

    /**
     * A Jal-MejRah's drains on [player] as it fires: 3 run energy, and now and then a level off each combat stat; none
     * through Protect from Missiles ([protected]).
     */
    internal fun batDrain(player: Player, protected: Boolean) {
        if (protected) return
        player.runEnergy = maxOf(0, player.runEnergy - BAT_RUN_DRAIN)
        if (!world.random.chance(1, BAT_STAT_DRAIN_ODDS)) return
        for (skill in COMBAT_STATS) {
            val current = player.skills.currentLevel(skill)
            if (current > 1) player.skills.setCurrentLevel(skill, current - 1)
        }
    }

    /**
     * A Jal-Ak dies: its three smaller selves come in where it was, going for the run's player. One that can't come in
     * now (the world full of NPCs) waits in [Inferno.Run.splitsOwed], and the wave with it, until it can.
     */
    private fun split(inferno: Inferno, blob: Npc) {
        val run = inferno.runAt(blob.x, blob.z) ?: return
        val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner } ?: return
        for ((i, type) in SPLITS.withIndex()) {
            val (dx, dz) = SPLIT_OFFSETS[i]
            val tile = Tile(blob.x + dx, blob.z + dz)
            if (inferno.spawn(player, run, type, tile) == null) run.splitsOwed += type to tile
        }
    }

    /**
     * A Jal-ImKot's digging: true while it is under or going under, so it does nothing else this tick. Due and out of
     * reach, it goes under; its time up, it comes up beside [player].
     */
    private fun imKot(run: Inferno.Run, npc: Npc, player: Player): Boolean {
        // Going under: its dig-down shows for a few ticks before it is gone from sight (Codex review on #833).
        val hide = hideAt[npc]
        if (hide != null) {
            if (world.tick < hide) return true
            hideAt.remove(npc)
            world.hide(npc)
            surfaceAt[npc] = world.tick + UNDER_TICKS
            return true
        }
        val up = surfaceAt[npc]
        if (up != null) {
            if (world.tick < up) return true
            surfaceAt.remove(npc)
            val size = npc.type.size
            // Beside the player where it can stand, not in a support or a wall (Codex review on #833).
            val sides = listOf(player.x - 1 to player.z - size, player.x - 1 to player.z + 1, player.x - size to player.z - 1, player.x + 1 to player.z - 1)
            val (x, z) = sides.firstOrNull { (x, z) -> world.npcs.canStand(npc, x, z) } ?: sides.first()
            world.npcs.teleport(npc, x, z)
            world.show(npc)
            npc.effects.anim(ANIM_DIG_UP)
            npc.nextAttackTick = world.tick + SURFACE_WAIT
            nextDig[npc] = world.tick + digGap()
            world.meleeCombat.aggress(npc, player, world.tick)
            return true
        }
        val due = nextDig.getOrPut(npc) { run.waveStartTick + FIRST_DIG_TICKS }
        if (world.tick < due || world.meleeCombat.reaches(npc, player)) return false
        npc.effects.anim(ANIM_DIG_DOWN)
        world.npcs.stopFollowing(npc)
        npc.target = null
        hideAt[npc] = world.tick + DIG_TICKS
        digs[npc] = (digs[npc] ?: 0) + 1
        return true
    }

    private fun digGap(): Long = world.random.between(DIG_GAP_MIN, DIG_GAP_MAX).toLong()

    /**
     * A Jal-Zek brings back a monster of [run]'s wave that died, at half its hitpoints, instead of attacking; false,
     * attacking as usual, when there is none to bring back.
     */
    private fun revive(inferno: Inferno, run: Inferno.Run, zek: Npc, player: Player): Boolean {
        val typeId = run.slain.firstOrNull() ?: return false
        run.slain.removeAt(0)
        world.npcs.stopFollowing(zek)
        zek.effects.anim(ANIM_ZEK_REVIVE)
        zek.nextAttackTick = world.tick + ZEK_AFTER_REVIVE
        revivingUntil[zek] = world.tick + ZEK_AFTER_REVIVE
        // One that can't come back now (the world full of NPCs) stays first in line for the next revive.
        val back = inferno.spawn(player, run, typeId, run.at(REVIVE_TILE), fight = false) ?: return true.also { run.slain.add(0, typeId) }
        revived[back] = true
        back.hitpoints = maxOf(1, back.maxHitpoints / 2)
        back.nextAttackTick = world.tick + REVIVED_WAIT
        world.meleeCombat.aggress(back, player, world.tick)
        return true
    }

    /** Whether [npc] stands on a tile beside [player], diagonals counted (the Jal-Xil's and Jal-Zek's melee reach). */
    private fun besideAny(npc: Npc, player: Player): Boolean {
        if (npc.level != player.level) return false
        val gx = gap(npc.x, npc.type.size, player.x)
        val gz = gap(npc.z, npc.type.size, player.z)
        return maxOf(gx, gz) == 1
    }

    private fun gap(a: Int, aSize: Int, b: Int): Int = when {
        b >= a + aSize -> b - (a + aSize - 1)
        a >= b + 1 -> a - b
        else -> 0
    }

    companion object {
        val SCRIPTED: Set<Int> = setOf(
            InfernoMonster.BAT.type, InfernoMonster.BLOB.type, InfernoMonster.MELEE.type, InfernoMonster.RANGER.type, InfernoMonster.MAGE.type,
        )

        /** What a Jal-Zek can bring back: the wave's monsters but the Nibblers and Jal-Zeks (guessed). */
        val REVIVABLE: List<Int> = listOf(
            InfernoMonster.BAT.type, InfernoMonster.BLOB.type, InfernoMonster.MELEE.type, InfernoMonster.RANGER.type,
            JAL_AKREK_MEJ, JAL_AKREK_XIL, JAL_AKREK_KET,
        )

        const val JAL_AKREK_MEJ = 7694
        const val JAL_AKREK_XIL = 7695
        const val JAL_AKREK_KET = 7696

        /** The three a Jal-Ak splits into, ranged, magic and melee. */
        val SPLITS: List<Int> = listOf(JAL_AKREK_XIL, JAL_AKREK_MEJ, JAL_AKREK_KET)
        private val SPLIT_OFFSETS = listOf(0 to 0, 1 to 0, 0 to 1)

        const val BAT_RUN_DRAIN = 3 * Movement.MAX_ENERGY / 100
        const val BAT_STAT_DRAIN_ODDS = 4
        private val COMBAT_STATS = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC)

        const val BLOB_READS_AHEAD = 3
        const val BLOB_MELEE_ODDS = 3
        const val ANIM_BLOB_MELEE = 7582
        val BLOB_MAGIC = osrsps.game.combat.NpcRangedAttack(anim = 7581, projectile = 1380)
        val BLOB_RANGED = osrsps.game.combat.NpcRangedAttack(anim = 7583, projectile = 1378)

        const val FIRST_DIG_TICKS = 50L
        const val DIG_NO_RESET_TICKS = 30L
        const val DIG_GAP_MIN = 40
        const val DIG_GAP_MAX = 60
        const val DIG_TICKS = 4L
        const val UNDER_TICKS = 2L
        const val SURFACE_WAIT = 6L
        const val ANIM_DIG_DOWN = 7600
        const val ANIM_DIG_UP = 7601

        const val XIL_MELEE_MAX = 19
        const val ANIM_XIL_MELEE = 7604

        const val ZEK_MELEE_MAX = 52
        const val ANIM_ZEK_MELEE = 7612
        const val ANIM_ZEK_REVIVE = 7611
        const val ZEK_REVIVE_ODDS = 10
        const val ZEK_AFTER_REVIVE = 8L
        const val REVIVED_WAIT = 2L

        /** Where a revived monster comes back: the arena's centre-east. Guessed tile. */
        val REVIVE_TILE = Tile(2279, 5341)
    }
}
