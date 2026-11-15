package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.Encounter
import osrsps.game.instance.Instance
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Giant bones' lines (keys only; the words are in data/dialogue.txt, from the Wiki's Giant bones page). */
object GiantBonesLines : Lines("giant_bones", "Giant bones") {
    val NO_FIT = line("no_fit", "Take on giant bones on the ground: they can't be picked up (Wiki, Giant bones)")
}

/**
 * The giant bosses' lairs: Obor's off the Edgeville Dungeon's hill giants ([Obor]) and, after it, Bryophyta's. Each lair
 * is a private copy of its part of the map ([Instance]), made for one player as they go in and gone once nobody is in
 * it, so the boss is there afresh each time: "Obor will also respawn immediately upon leaving and re-entering the lair,
 * as it is instanced" (OSRS Wiki, Obor, read 2026-11-15). Logging out or dying inside leaves by the instance's exit, as
 * every instance does ([World.endInstance]).
 *
 * - **The gate in.** Locked without its key ([Lair.key]: the giant key, the mossy key); with it, a menu whose yes
 *   unlocks it for good and keeps the key; after that the same menu with no key at all (OSRS Wiki, Gate (Obor) and its
 *   transcript). Ours keeps the unlock in a varp of our own ([CustomVars.giantLairsVarp]); where live keeps it is guessed.
 *   The way out asks first on Open (transcript); Quick-exit leaves at once (guessed from its name).
 * - **The chest.** Once the boss is dead, one opening with the key takes the key and puts the chest's loot on the floor
 *   under the player (OSRS Wiki, Chest (Bryophyta's lair): "Opening the chest places loot directly on the ground"; Obor's
 *   is taken to do the same, inferred), and counts a kill ([Lair.killCountVarp], the cache's `total_hillgiant_boss_kills`
 *   and `total_bryophyta_kills`; the kill counts only through the chest, OSRS Wiki, Obor). Whatever the opening gives,
 *   the boss comes back [RESPAWN_TICKS] later, key or none ("Bryophyta respawns 15 ticks (9 seconds) after attempting to
 *   open her chest, regardless of whether the player has a mossy key", OSRS Wiki, Bryophyta; Obor "respawn shortly
 *   after", taken as the same 15, guessed). One kill opens the chest once.
 * - **Giant bones**, the bosses' members drop, can't be taken ([GiantBonesLines.NO_FIT]) and
 *   bury from the floor for 150 Prayer XP (OSRS Wiki, Giant bones). Left out: live's confirmation before the bury (shared
 *   with bull bones, which can be turned off); ours buries at once.
 *
 * The chest's messages follow Bryophyta's chest's transcript (the stir, the key needed, the loot on the floor, the
 * count); Obor's chest has no transcript, so his are inferred from hers. Guessed: that the chest does nothing more while
 * a respawn is on its way.
 */
class GiantLairs(private val world: World) {
    /** One lair's map and pieces, in the coordinates of the real map; [Lair.inside] turns them into an instance's. */
    class Lair(
        val name: String,
        /** The bit in [CustomVars.giantLairsVarp] that says it is unlocked. */
        val bit: Int,
        val key: Int,
        val boss: Int,
        val bossSpawn: Tile,
        val chest: Int,
        val chestTable: String,
        val killCountVarp: Int,
        /** The zones copied: [widthZones] by [heightZones] from [fromX], [fromZ] (zone-aligned). */
        val fromX: Int,
        val fromZ: Int,
        val widthZones: Int,
        val heightZones: Int,
        /** Where going in puts the player, and where leaving does (outside the gate in). */
        val entry: Tile,
        val exit: Tile,
        /** The boss's fight: who is in it ([inFight], real-map coordinates) and the fight itself. */
        val inFight: (Int, Int) -> Boolean,
        val lines: LairLines,
        val fight: (World, Npc, (Int, Int) -> Boolean) -> Encounter?,
    ) {
        init {
            require(fromX % 8 == 0 && fromZ % 8 == 0) { "$name's lair isn't zone-aligned" }
        }
    }

    /** The lines a lair says, each its own lair's key. */
    class LairLines(
        val gateLocked: Line,
        /** The box before the gate's menu, if it has one (Bryophyta's instance warning). */
        val gateWarning: Line? = null,
        val gateTitle: Line,
        val gateYes: Line,
        val gateNo: Line,
        /** What the menu's no says, if anything. */
        val gateDeclined: Line? = null,
        val gateOpened: Line,
        /** The way out's menu; null where it has none. */
        val exitTitle: Line? = null,
        val exitYes: Line? = null,
        val exitNo: Line? = null,
        val chestAlive: Line,
        val chestStir: Line,
        val chestNoKey: Line,
        val chestLoot: Line,
        val killCount: Line,
    )

    /** One player's lair as it stands: the boss in it, whether its last kill has opened the chest, and a respawn due. */
    class Run(val lair: Lair, val instance: Instance) {
        var boss: Npc? = null
        var unlooted = false
        var respawning = false

        /** [x], [z] on the real map, inside this run's instance. */
        fun inside(x: Int, z: Int) = Tile(instance.baseX + x - lair.fromX, instance.baseZ + z - lair.fromZ)

        /** [x], [z] inside the instance, back on the real map. */
        fun outside(x: Int, z: Int) = Tile(x - instance.baseX + lair.fromX, z - instance.baseZ + lair.fromZ)
    }

    private val runs = HashMap<Int, Run>()

    /** The run of the lair [player] is in, if any. */
    fun runOf(player: Player): Run? = runAt(player.x, player.z)

    fun runAt(x: Int, z: Int): Run? {
        val instance = world.instances.at(x, z) ?: return null
        val run = runs[instance.id] ?: return null
        return run.takeIf { it.instance === instance }
    }

    fun unlocked(player: Player, lair: Lair): Boolean {
        val varp = player.vars.types.namedVarps[CustomVars.GIANT_LAIRS] ?: return false
        return player.vars[varp] and (1 shl lair.bit) != 0
    }

    private fun unlock(player: Player, lair: Lair) {
        val varp = player.vars.types.namedVarps[CustomVars.GIANT_LAIRS] ?: return
        player.vars[varp] = player.vars[varp] or (1 shl lair.bit)
    }

    /**
     * Open on the gate in: refused without the key until it is unlocked; else a menu, and yes unlocks it for good (the key
     * stays), says the key fits and takes [player] into a lair of their own, the boss in it. Once unlocked the gate does
     * the same with no key ("If the player has unlocked the gate: above", Transcript:Gate (Obor)), message and all.
     */
    suspend fun ScriptContext.enter(lair: Lair) {
        arriveDelay()
        val lines = lair.lines
        if (!unlocked(player, lair) && player.inventory.count(lair.key) == 0L) return world.message(player, lines.gateLocked)
        var yes = false
        world.dialogues.start(this) {
            lines.gateWarning?.let { mesbox(it) }
            yes = choice(lines.gateYes, lines.gateNo, title = text(lines.gateTitle)) == 1
        }
        if (!yes) return lines.gateDeclined?.let { world.message(player, it) } ?: Unit
        if (!unlocked(player, lair)) {
            if (player.inventory.count(lair.key) == 0L) return world.message(player, lines.gateLocked)
            unlock(player, lair)
        }
        world.message(player, lines.gateOpened)
        val run = open(lair) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val to = run.inside(lair.entry.x, lair.entry.z)
        world.teleport(player, to.x, to.z, 0)
    }

    /** A new lair for one player, its boss in it; null when the dynamic area has no room. */
    fun open(lair: Lair): Run? {
        // Ended lairs are let go of here, as nothing else tells this when an instance ends.
        runs.values.removeAll { world.instances.all.none { instance -> instance === it.instance } }
        val instance = world.instances.create(lair.fromX, lair.fromZ, lair.widthZones, lair.heightZones, lair.exit.x, lair.exit.z, 0) ?: return null
        val run = Run(lair, instance)
        runs[instance.id] = run
        spawnBoss(run)
        return run
    }

    private fun alive(run: Run) = runs[run.instance.id] === run && world.instances.all.any { it === run.instance }

    private fun spawnBoss(run: Run) {
        val type = world.npcTypes[run.lair.boss] ?: return
        val at = run.inside(run.lair.bossSpawn.x, run.lair.bossSpawn.z)
        val boss = world.spawnNpc(NpcSpawn(type, at.x, at.z, 0, source = "lair ${run.lair.name}", respawns = false)) ?: return
        run.boss = boss
        run.lair.fight(world, boss) { x, z -> run.instance.contains(x, z) && run.outside(x, z).let { run.lair.inFight(it.x, it.z) } }
    }

    /** The boss of a lair was killed: its chest opens once for the key. */
    fun killed(npc: Npc) {
        val run = runAt(npc.x, npc.z) ?: return
        if (run.boss === npc) run.unlooted = true
    }

    /**
     * Open on the chest: refused while the boss lives; else, a kill not yet looted and the key held, the key goes, the
     * loot goes on the floor under the player and the kill counts. Either way the boss comes back [RESPAWN_TICKS] later.
     */
    suspend fun ScriptContext.openChest(loc: ZoneLoc) {
        arriveDelay()
        val run = runOf(player)
        if (run == null || run.lair.chest != loc.id) return world.message(player, GameLines.NOTHING_INTERESTING)
        val boss = run.boss
        if (boss != null && !boss.dead && world.npcs[boss.index] === boss) return world.message(player, run.lair.lines.chestAlive)
        if (run.respawning) return
        world.message(player, run.lair.lines.chestStir)
        if (player.inventory.count(run.lair.key) == 0L) {
            world.message(player, run.lair.lines.chestNoKey)
        } else if (run.unlooted && takeKey(player, run.lair.key)) {
            run.unlooted = false
            world.message(player, run.lair.lines.chestLoot)
            world.npcDrops.chests[run.lair.chestTable]?.roll(world.random)?.forEach { drop ->
                world.groundObjs.addPrivate(drop.type, drop.count, player.x, player.z, player.level, player.saveKey, osrsps.game.GroundObjs.DROP_PRIVATE_TICKS, osrsps.game.GroundObjs.LOOT_DESPAWN_TICKS)
            }
            val varp = run.lair.killCountVarp
            if (varp in 0 until player.vars.types.varpCount) {
                player.vars[varp] = player.vars[varp] + 1
                world.message(player, run.lair.lines.killCount, "count" to player.vars[varp])
            }
        }
        run.respawning = true
        run.boss = null
        world.npcScripts.later(RESPAWN_TICKS) {
            if (!alive(run)) return@later
            run.respawning = false
            spawnBoss(run)
        }
    }

    private fun takeKey(player: Player, key: Int): Boolean {
        for ((slot, obj) in player.inventory.contents()) {
            if (obj.id != key) continue
            player.inventory.remove(slot, 1)
            return true
        }
        return false
    }

    /** The way out's main option: its menu where it has one, leaving on its first option (Transcript:Gate (Obor)). */
    suspend fun ScriptContext.leaveAsking() {
        val run = runOf(player) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        arriveDelay()
        val lines = run.lair.lines
        val title = lines.exitTitle
        if (title == null || lines.exitYes == null || lines.exitNo == null) return leave(player)
        var yes = false
        world.dialogues.start(this) { yes = choice(lines.exitYes, lines.exitNo, title = text(title)) == 1 }
        if (yes) leave(player)
    }

    /** Leaving by the lair's way out: out beside the gate in. */
    fun leave(player: Player) {
        val run = runOf(player) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        world.scripts.interrupt(player)
        world.teleport(player, run.lair.exit.x, run.lair.exit.z, 0)
    }

    companion object {
        const val RESPAWN_TICKS = 15

        /** The lairs' locs whose handlers work in their copies, the instances: the way out, the rocks and the chest. */
        val INSTANCE_LOCS: Set<Int> = setOf(Obor.EXIT_LEFT, Obor.EXIT_RIGHT, Obor.ROCKS, Obor.CHEST, Bryophyta.EXIT, Bryophyta.LOGS, Bryophyta.CHEST)

        const val GIANT_BONES = 30898
        const val BURY = 3
        const val TAKE = 4
        const val GIANT_BONES_XP_TENTHS = 1_500
    }
}

/** Giant bones on the floor: Bury, at once (no confirmation yet); Take is refused. See [GiantLairs]. */
fun registerGiantBones(world: World) {
    world.handlers.onGroundObj(GiantLairs.GIANT_BONES, GiantLairs.TAKE) { world.message(player, GiantBonesLines.NO_FIT) }
    world.handlers.onGroundObj(GiantLairs.GIANT_BONES, GiantLairs.BURY) { op ->
        val obj = op.groundObj!!
        // As a bury from the backpack does: the message, the animation, a tick, then the bones go and the XP comes.
        world.message(player, BonesLines.DIG)
        player.anim(Bones.ANIM_BURY)
        player.waypoints.clear()
        delay(1)
        if (!world.groundObjs.remove(obj)) return@onGroundObj
        player.skills.addTenths(Skill.PRAYER, GiantLairs.GIANT_BONES_XP_TENTHS)
        world.message(player, BonesLines.BURIED)
    }
}
