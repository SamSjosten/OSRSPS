package osrsps.game.content

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.Encounter
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.skill.Agility
import osrsps.map.Tile

/** Obor's lair's lines (keys only; the words are in data/dialogue.txt). */
object OborLines : Lines("obor", "Obor's lair") {
    val GATE_LOCKED = line("gate_locked", "Open on the lair's gate, still locked, with no giant key (Transcript:Gate (Obor))")
    val GATE_TITLE = line("gate_title", "the gate's menu, with a giant key or once unlocked (Transcript:Gate (Obor))")
    val GATE_YES = line("gate_yes", "the gate's menu: go in")
    val GATE_NO = line("gate_no", "the gate's menu: stay out")
    val GATE_OPENED = line("gate_opened", "the gate's menu answered yes: in (Transcript:Gate (Obor))")
    val EXIT_TITLE = line("exit_title", "Open on the way out: the menu (Transcript:Gate (Obor))")
    val EXIT_YES = line("exit_yes", "the way out's menu: leave")
    val EXIT_NO = line("exit_no", "the way out's menu: stay")
    val CHEST_ALIVE = line("chest_alive", "Open on the chest while Obor lives: inferred from Bryophyta's chest, Obor's transcript is empty")
    val CHEST_STIR = line("chest_stir", "Open on the chest once Obor is dead, key or none: he comes back shortly (Bryophyta's says the leaves stir; Obor's words not found)")
    val CHEST_NO_KEY = line("chest_no_key", "Open on the chest with no giant key: inferred from Bryophyta's chest")
    val CHEST_LOOT = line("chest_loot", "the chest opened with a giant key: the loot on the floor (inferred from Bryophyta's chest)")
    val KILL_COUNT = line("kill_count", "the chest opened with a giant key: the kill count (inferred from Bryophyta's chest)", "count")
}

/**
 * Obor, the hill giant boss (Misthalin bosses step 1), in his lair off the west wall of the Edgeville Dungeon's hill
 * giants, as the 242 cache's map has it (map square 48,153). The lair is a [GiantLairs] instance:
 *
 * - **The gate in** (`hillgiant_boss_entrance_l`/`_r` 29487 and 29486 at 3095,9832 and 9833) takes the giant key, kept,
 *   the first time, and opens for good after (OSRS Wiki, Gate (Obor)). It puts the player inside the lair's own gate,
 *   at [ENTRY] (guessed tile: the first one south of the gate's west leaf). **The gate out** (`hillgiant_boss_exit_l`/`_r`
 *   29489 and 29488 at 3091 and 3092,9815), Open or Quick-exit, puts them back on the hill giants' side of the gate in,
 *   at [EXIT].
 * - **The rocks** (`hillgiant_boss_climbingrocks` 29491, four of them at 3091 and 3092,9805 and 9806) Climb from the safe
 *   ledge down into his pit and back: "When the player first enters Obor's lair, they will be in a safe zone, where they
 *   must climb down the rocks in order to fight him" (OSRS Wiki, Obor). Guessed: the climbing animation
 *   ([ANIM_CLIMB], human_climbing), the [CLIMB_TICKS], and landing on the far side of the rocks in the same column.
 * - **Obor** (`hillgiant_boss` 7416, size 2) stands at [SPAWN] in the pit (x 3084 to 3099, z 9797 to 9804) and fights
 *   whoever is down there with him ([inPit]); nobody on the ledge can hit him or be hit (Combat.shutOut). His combat
 *   and drops are npc-combat.json's and npc-drops.json's, his chest's `chests.obor`.
 * - **The chest** (`giantkey_chest_closed` 56380 at 3085,9797) is [GiantLairs]'s.
 *
 * His fight, every [ATTACK_SPEED] ticks (OSRS Wiki, Obor, Mechanics, read 2026-11-15):
 *
 * - Beside the player, praying Protect from Melee he swings or throws, even chances; praying Protect from Missiles he
 *   throws one time in three and swings otherwise. Praying neither, he swings (inferred: the Wiki names only the two
 *   prayers).
 * - Not beside them he throws, from any distance, and then walks after them ("approaches"): always when they pray
 *   Protect from Missiles, one time in three when they don't. Inferred: an approach is walking after them until his
 *   next attack. Not built: the Wiki's "after 4 ticks, he will additionally attempt to step a single tile towards the
 *   player", and that the approach fails beside a wall.
 * - His swing (crush, max 22) knocks the player back one time in four, and a knock-back knocks them down one time in
 *   three. Guessed: the knock-back's [KNOCKBACK_TILES] straight away from him (stopped by walls), and the knock-down's
 *   [KNOCKDOWN_TICKS] stun with human_knockdown and no birds.
 * - His throw (ranged, max 26) does half its damage through Protect from Missiles (Mod Ash, 26 January 2021, cited on
 *   the Wiki page); Protect from Melee blocks his swing outright (inferred: only the throw is said to get through).
 *   Guessed: the throw's animation (his swing's, giant_update_basic_attack) and its rock ([PROJECTILE_ROCK],
 *   troll_rock_travel).
 *
 * Guessed too: that he takes on the player nearest him when nobody holds him, and first attacks the tick after.
 */
object Obor {
    const val BOSS = 7416
    const val ENTRANCE_LEFT = 29487
    const val ENTRANCE_RIGHT = 29486
    const val EXIT_LEFT = 29489
    const val EXIT_RIGHT = 29488
    const val ROCKS = 29491
    const val CHEST = 56380
    const val GIANT_KEY = 20754
    const val OPEN = 1
    const val QUICK_EXIT = 3
    const val CLIMB = 1

    /** The cache's `total_hillgiant_boss_kills` varp. */
    const val KILL_COUNT_VARP = 1529

    /** On the hill giants' side of the gate in; inside the lair's gate; and where Obor stands. */
    val EXIT = Tile(3095, 9832)
    val ENTRY = Tile(3091, 9814)
    val SPAWN = Tile(3091, 9798)

    /** The rocks' rows, and the tiles north and south of them that climbing lands on. */
    const val ROCKS_SOUTH = 9805
    const val ROCKS_NORTH = 9806

    const val ANIM_CLIMB = 737
    const val CLIMB_TICKS = 2

    const val ATTACK_SPEED = 6
    const val ANIM_ATTACK = 4652
    const val PROJECTILE_ROCK = 276
    const val THROW_MAX = 26
    const val THROW_THROUGH_PRAYER = 50
    const val KNOCKBACK_TILES = 2
    const val KNOCKDOWN_TICKS = 3
    const val ANIM_KNOCKDOWN = 7210

    /** Whether [x], [z] (on the real map) is in his pit. */
    fun inPit(x: Int, z: Int) = x in 3084..3099 && z in 9797..9804

    val LAIR = GiantLairs.Lair(
        name = "obor", bit = 0, key = GIANT_KEY, boss = BOSS, bossSpawn = SPAWN, chest = CHEST, chestTable = "obor",
        killCountVarp = KILL_COUNT_VARP, fromX = 3072, fromZ = 9792, widthZones = 4, heightZones = 3, entry = ENTRY, exit = EXIT,
        inFight = ::inPit,
        lines = with(OborLines) {
            GiantLairs.LairLines(
                gateLocked = GATE_LOCKED, gateTitle = GATE_TITLE, gateYes = GATE_YES, gateNo = GATE_NO, gateOpened = GATE_OPENED,
                exitTitle = EXIT_TITLE, exitYes = EXIT_YES, exitNo = EXIT_NO,
                chestAlive = CHEST_ALIVE, chestStir = CHEST_STIR, chestNoKey = CHEST_NO_KEY, chestLoot = CHEST_LOOT, killCount = KILL_COUNT,
            )
        },
        fight = ::fight,
    )

    /** His fight against [boss], with whoever [inArea] says is in the pit; null without his type in the cache. */
    fun fight(world: World, boss: Npc, inArea: (Int, Int) -> Boolean): Encounter? {
        val def = def(world) ?: return null
        return world.encounters.start(def, boss, inArea)
    }

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "obor", type,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(CycleStep(1) { tick() }),
                    onStart = { boss.unleashed = true },
                ),
            ),
            scriptedAttacks = true,
        )
    }

    private class State {
        var target: WeakReference<Player>? = null
        var nextAttack = Long.MIN_VALUE
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    private fun EncounterContext.tick() {
        val target = target()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        if (world.tick < state.nextAttack) return
        state.nextAttack = world.tick + ATTACK_SPEED
        attack(target)
    }

    /** Who he fights: the one he holds while they are in the pit, else the nearest there; he attacks from the next tick. */
    private fun EncounterContext.target(): Player? {
        val inPit = players
        state.target?.get()?.let { held -> if (inPit.any { it === held }) return held }
        val pick = boss.target?.takeIf { held -> inPit.any { it === held } }
            ?: inPit.minByOrNull { maxOf(distance(boss.x, it.x, boss.type.size), distance(boss.z, it.z, boss.type.size)) }
            ?: return null.also { state.target = null }
        state.target = WeakReference(pick)
        state.nextAttack = maxOf(state.nextAttack, world.tick + 1)
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        log("takes on ${pick.username}")
        return pick
    }

    /** Tiles from an NPC [size] tiles across starting at [from] to [to], along one axis; 0 when within it. */
    private fun distance(from: Int, to: Int, size: Int): Int = when {
        to < from -> from - to
        to > from + size - 1 -> to - (from + size - 1)
        else -> 0
    }

    /** What he does on an attack: swing or throw, and after a throw whether he walks after the player. */
    enum class Move { SWING, THROW_AND_STAND, THROW_AND_APPROACH }

    /**
     * His move, by whether the player is [beside] him and which protection prayer they pray; [roll] gives a number from
     * its first to its second, both included (the fight's World.random). See [Obor].
     */
    fun decide(beside: Boolean, protectMelee: Boolean, protectMissiles: Boolean, roll: (Int, Int) -> Int): Move {
        if (beside) {
            val swing = when {
                protectMelee -> roll(1, 2) == 1
                protectMissiles -> roll(1, 3) != 1
                else -> true
            }
            // Beside the player he stays where he is either way: he is there already.
            return if (swing) Move.SWING else Move.THROW_AND_STAND
        }
        return if (protectMissiles || roll(1, 3) == 1) Move.THROW_AND_APPROACH else Move.THROW_AND_STAND
    }

    private fun EncounterContext.attack(target: Player) {
        val prayers = world.prayers
        val move = decide(world.meleeCombat.reaches(boss, target), prayers.protects(target, AttackType.CRUSH), prayers.protects(target, AttackType.RANGED)) { min, max -> roll(min, max) }
        if (move == Move.THROW_AND_APPROACH) world.npcs.follow(boss, target) else world.npcs.stopFollowing(boss)
        if (move == Move.SWING) slam(target) else throwRock(target)
        if (move == Move.THROW_AND_APPROACH) log("approaches")
    }

    private fun EncounterContext.slam(target: Player) {
        world.meleeCombat.swingAt(boss, target, world.tick, ANIM_ATTACK)
        log("swings at ${target.username}")
        if (target.dead || roll(1, 4) != 1) return
        knockBack(target)
        if (target.dead || roll(1, 3) != 1) return
        world.statusEffects.stun(target, KNOCKDOWN_TICKS, spotanim = -1)
        target.anim(ANIM_KNOCKDOWN)
        log("knocks ${target.username} down")
    }

    /** Pushes [target] up to [KNOCKBACK_TILES] straight away from his middle, as far as walls let them go. */
    private fun EncounterContext.knockBack(target: Player) {
        val size = boss.type.size
        // Doubled, so a 2-tile giant's middle falls between tiles.
        val dx = Integer.signum(target.x * 2 - (boss.x * 2 + size - 1))
        val dz = Integer.signum(target.z * 2 - (boss.z * 2 + size - 1))
        if (dx == 0 && dz == 0) return
        var x = target.x
        var z = target.z
        repeat(KNOCKBACK_TILES) {
            if (!world.canStep(target.level, x, z, dx, dz)) return@repeat
            x += dx
            z += dz
        }
        if (x == target.x && z == target.z) return
        world.scripts.interrupt(target)
        val angle = Agility.angle(target.x - x, target.z - z)
        target.effects.exactMove(PlayerEffects.ExactMove(target.x, target.z, x, z, 0, 30, angle))
        world.teleport(target, x, z, target.level)
        log("knocks ${target.username} back to $x,$z")
    }

    private fun EncounterContext.throwRock(target: Player) {
        val rock = NpcRangedAttack(anim = ANIM_ATTACK, projectile = PROJECTILE_ROCK, maxHit = THROW_MAX)
        world.meleeCombat.fireAt(boss, target, world.tick, rock, AttackType.RANGED, prayerPasses = THROW_THROUGH_PRAYER)
        log("throws at ${target.username}")
    }

    /**
     * Climb on the rocks: from the ledge down into the pit, or back up, landing in the same column on the far side of
     * the rocks; see [Obor].
     */
    fun climbTo(run: GiantLairs.Run, player: Player): Tile {
        val at = run.outside(player.x, player.z)
        val x = at.x.coerceIn(3091, 3092)
        val z = if (at.z > ROCKS_NORTH) ROCKS_SOUTH - 1 else ROCKS_NORTH + 1
        return run.inside(x, z)
    }
}

fun registerObor(world: World, lairs: GiantLairs) {
    with(Obor) {
        for (leaf in listOf(ENTRANCE_LEFT, ENTRANCE_RIGHT)) {
            world.handlers.onLoc(leaf, OPEN) { with(lairs) { enter(LAIR) } }
        }
        for (leaf in listOf(EXIT_LEFT, EXIT_RIGHT)) {
            world.handlers.onLoc(leaf, OPEN) { with(lairs) { leaveAsking() } }
            // Guessed: Quick-exit skips the menu, as its name says.
            world.handlers.onLoc(leaf, QUICK_EXIT) { lairs.leave(player) }
        }
        world.handlers.onLoc(ROCKS, CLIMB) {
            val run = lairs.runOf(player)?.takeIf { it.lair === LAIR } ?: return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            arriveDelay()
            player.anim(ANIM_CLIMB)
            delay(CLIMB_TICKS)
            val to = climbTo(run, player)
            world.teleport(player, to.x, to.z, 0)
        }
        world.handlers.onLoc(CHEST, OPEN) { op -> with(lairs) { openChest(op.loc!!) } }
        world.npcScripts.onDeath(listOf(BOSS)) { lairs.killed(it) }
        // Nobody off the pit's floor hits him: the ledge above is his lair's safe zone.
        val shutOut = world.combat.shutOut
        world.combat.shutOut = { player, npc ->
            shutOut(player, npc) || (npc.type.id == BOSS && lairs.runAt(npc.x, npc.z)?.let { run -> run.outside(player.x, player.z).let { !inPit(it.x, it.z) } } == true)
        }
    }
}
