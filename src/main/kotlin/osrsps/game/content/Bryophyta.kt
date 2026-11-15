package osrsps.game.content

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import osrsps.game.Player
import osrsps.game.Weapons
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.combat.SureHit
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.Encounter
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.Handlers
import osrsps.map.Tile

/** Bryophyta's lair's lines (keys only; the words are in data/dialogue.txt). */
object BryophytaLines : Lines("bryophyta", "Bryophyta's lair") {
    val GATE_LOCKED = line("gate_locked", "Open on the lair's gate, still locked, with no mossy key (Transcript:Gate (Bryophyta))")
    val GATE_WARNING = line("gate_warning", "the box before the gate's menu: the instance warning (Transcript:Gate (Bryophyta))")
    val GATE_TITLE = line("gate_title", "the gate's menu (Transcript:Gate (Bryophyta))")
    val GATE_YES = line("gate_yes", "the gate's menu: go in")
    val GATE_NO = line("gate_no", "the gate's menu: not yet")
    val GATE_DECLINED = line("gate_declined", "the gate's menu answered no (Transcript:Gate (Bryophyta))")
    val GATE_OPENED = line("gate_opened", "the gate's menu answered yes: in (Transcript:Gate (Bryophyta), its typo kept)")
    val CHECK_COUNT = line("check_count", "Check-count on the gate: the chests opened (Transcript:Gate (Bryophyta))", "count")
    val CHEST_ALIVE = line("chest_alive", "Open on the chest while Bryophyta fights (Transcript:Chest (Bryophyta's lair))")
    val CHEST_STIR = line("chest_stir", "Open on the chest once she is dead, key or none (Transcript:Chest (Bryophyta's lair))")
    val CHEST_NO_KEY = line("chest_no_key", "Open on the chest with no mossy key (Transcript:Chest (Bryophyta's lair))")
    val CHEST_LOOT = line("chest_loot", "the chest opened with a mossy key: the loot on the floor (Transcript:Chest (Bryophyta's lair))")
    val KILL_COUNT = line("kill_count", "the chest opened with a mossy key: the count (Transcript:Chest (Bryophyta's lair))", "count")
    val AXE_TAKEN = line("axe_taken", "Take-axe on her lair's logs: the box (Transcript:Logs (bronze axe))")
    val HAVE_AXE = line("have_axe", "Take-axe on her lair's logs with an axe already (Transcript:Logs (bronze axe))")
}

/**
 * Bryophyta, the moss giantess (Misthalin bosses step 2), in her lair off the Varrock Sewers east of the moss giants,
 * on [GiantLairs] as Obor's is (OSRS Wiki, Bryophyta, Growthling, Gate (Bryophyta), Chest (Bryophyta's lair), Logs
 * (bronze axe) and their transcripts, read 2026-11-15).
 *
 * - **The gate in** (`gb_moss_door_in` 32534 at 3174,9901): locked without a mossy key; with one, the instance warning,
 *   then the menu, whose yes unlocks it for good (the key kept) and whose no says the player decided not to. Check-count
 *   tells the chests opened (the cache's `total_bryophyta_kills`). Into a lair of the player's own, at [ENTRY] (guessed:
 *   beside the rock pile out); out to [EXIT], the sewer side of the gate.
 * - **The rock pile** (`gb_moss_caverock_exit` 32535): Clamber or Quick-exit leaves at once. Its transcript is empty, so
 *   no menu is guessed for Clamber.
 * - **The logs** (`gb_log_withaxe` 32536): Take-axe gives a bronze axe, refused while the player holds or wields an axe
 *   (the transcript's two boxes); the logs go plain for 30 seconds as every bronze-axe log's do ([AxeLogs]).
 * - **The chest** (`mossykey_chest_closed` 56378 at 3229,9934) is [GiantLairs]'s, with the transcript's words.
 *
 * Her fight (attack speed 6; melee crush max 16, magic max 10; prayers block her fully; she never walks):
 *
 * - With the player in her reach she swings or casts; out of it she casts ("won't use melee attacks if they are out
 *   of her reach"). Guessed: even chances between the two in reach.
 * - Her swing poisons (8, members), through the shared NPC poison chance.
 * - Once she is down ~25% ([GROWTH_BELOW_PERCENT] of her hitpoints left or fewer), each of her attacks at a player who
 *   is attacking her, and each of that player's hits on her ([damaged]), has a 1/5 chance to bring three growthlings ([GROWTHLINGS]), one batch at a time, none for
 *   [GROWTH_COOLDOWN_TICKS] after the last of a batch dies (Mod Ash, cited on the Growthling page). While any lives she
 *   takes no damage but poison and venom, checked as each hit lands ([osrsps.game.combat.Combat.immune]). Her lair is
 *   multi-way (inferred), so they fight the player alongside her and can be fought while she attacks.
 * - **Growthlings** (`gb_growthling` 8194, 10 hitpoints, slash max 5, speed 4, aggressive) stop at 1 hitpoint for any
 *   hit but an axe's or magic secateurs' wielded, which kill one outright; an axe or secateurs used on one kills it too.
 *   Inferred: "instantly killed" as a sure hit for all its hitpoints. Guessed: "attacking her" as her having been hit by
 *   the player in the last [ATTACKING_TICKS]; where the three appear (the free tiles around her, west first); that
 *   they come along with her attack, not instead of it. Those out when she dies stay to be fought (Trivia).
 *
 * Guessed too: her magic's look (her swing's animation and roving_mossgiant_impact on the player, no projectile).
 */
object Bryophyta {
    const val BOSS = 8195
    const val GROWTHLING = 8194
    const val GATE = 32534
    const val EXIT = 32535
    const val LOGS = 32536
    const val LOGS_EMPTY = 5582
    const val CHEST = 56378
    const val MOSSY_KEY = 22374
    const val BRONZE_AXE = 1351
    const val SECATEURS = 5329
    const val MAGIC_SECATEURS = 7409
    const val OPEN = 1
    const val CHECK_COUNT = 2
    const val CLAMBER = 1
    const val QUICK_EXIT = 3
    const val TAKE_AXE = 1

    /** The cache's `total_bryophyta_kills` varp. */
    const val KILL_COUNT_VARP = 1733

    /** The sewer side of the gate in; inside the lair, beside the rock pile out (guessed); and where she stands. */
    val EXIT_TILE = Tile(3174, 9900)
    val ENTRY = Tile(3214, 9937)
    val SPAWN = Tile(3220, 9933)

    const val ATTACK_SPEED = 6
    const val ANIM_ATTACK = 4658
    const val IMPACT_MAGIC = 339
    const val MAGIC_MAX = 10

    const val GROWTHLINGS = 3
    const val GROWTH_CHANCE = 5
    const val GROWTH_BELOW_PERCENT = 75
    const val GROWTH_COOLDOWN_TICKS = 20
    const val ATTACKING_TICKS = 10

    val LAIR = GiantLairs.Lair(
        name = "bryophyta", bit = 1, key = MOSSY_KEY, boss = BOSS, bossSpawn = SPAWN, chest = CHEST, chestTable = "bryophyta",
        killCountVarp = KILL_COUNT_VARP, fromX = 3208, fromZ = 9920, widthZones = 3, heightZones = 3, entry = ENTRY, exit = EXIT_TILE,
        inFight = { _, _ -> true },
        lines = with(BryophytaLines) {
            GiantLairs.LairLines(
                gateLocked = GATE_LOCKED, gateWarning = GATE_WARNING, gateTitle = GATE_TITLE, gateYes = GATE_YES, gateNo = GATE_NO,
                gateDeclined = GATE_DECLINED, gateOpened = GATE_OPENED,
                chestAlive = CHEST_ALIVE, chestStir = CHEST_STIR, chestNoKey = CHEST_NO_KEY, chestLoot = CHEST_LOOT, killCount = KILL_COUNT,
            )
        },
        fight = ::fight,
    )

    /** One fight's growthlings and when the next batch may come. */
    class State {
        var target: WeakReference<Player>? = null
        var nextAttack = Long.MIN_VALUE
        // Held weakly: the world holds them while they live, and a lair that ends with them out (World.endInstance)
        // mustn't keep them, nor through their targets the player and her, in [states].
        internal val batch = ArrayList<WeakReference<Npc>>()

        /** The growthlings of the batch still held, living or not ([living] prunes them). */
        val growthlings: List<Npc> get() = batch.mapNotNull { it.get() }
        var growthAfter = Long.MIN_VALUE

        /** Whether a batch is out whose end hasn't started the cooldown yet. */
        var batchOut = false

        /** The tick the last growthling of the batch went: its death tick, or the tick it was found gone. */
        var lastGone = Long.MIN_VALUE
    }

    private val states = WeakHashMap<Npc, State>()

    internal val MELEE_TYPES = setOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH)

    fun state(boss: Npc): State = states.getOrPut(boss) { State() }

    /** Whether [boss]'s growthlings are out, so she takes nothing but poison and venom. */
    fun shielded(world: World, boss: Npc): Boolean = living(world, state(boss)).isNotEmpty()

    private fun living(world: World, state: State): List<Npc> {
        state.batch.removeAll { ref ->
            val npc = ref.get()
            val gone = npc == null || npc.dead || world.npcs[npc.index] !== npc
            if (gone) state.lastGone = maxOf(state.lastGone, if (npc != null && npc.dead && npc.diedTick >= 0) npc.diedTick else world.tick)
            gone
        }
        // The batch's end starts the cooldown wherever it is first seen (her tick, or a hit landing before it), from the
        // tick the last one died, which may be a tick before (killed by an item).
        if (state.batchOut && state.batch.isEmpty()) {
            state.batchOut = false
            state.growthAfter = state.lastGone + GROWTH_COOLDOWN_TICKS
        }
        return state.growthlings
    }

    fun fight(world: World, boss: Npc, inArea: (Int, Int) -> Boolean): Encounter? {
        val def = def(world) ?: return null
        return world.encounters.start(def, boss, inArea)
    }

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "bryophyta", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = { boss.unleashed = true })),
            scriptedAttacks = true,
        )
    }

    private val EncounterContext.state: State get() = state(boss)

    private fun EncounterContext.tick() {
        val state = state
        val out = state.batchOut
        living(world, state)
        if (out && !state.batchOut) log("growthlings gone")
        val target = target() ?: return
        boss.face(target)
        if (world.tick < state.nextAttack) return
        // Neither reach nor sight (under her, or behind a wall): she holds her attack until she has one. Guessed.
        if (!world.meleeCombat.reaches(boss, target) && !world.meleeCombat.sees(boss, target)) return
        state.nextAttack = world.tick + ATTACK_SPEED
        attack(target)
    }

    /** Who she fights: the one she holds while they are in her lair, else the nearest; she attacks from the next tick. */
    private fun EncounterContext.target(): Player? {
        val inLair = players
        state.target?.get()?.let { held -> if (inLair.any { it === held } && !held.dead) return held }
        val pick = inLair.filter { !it.dead }.minByOrNull { maxOf(kotlin.math.abs(it.x - boss.x), kotlin.math.abs(it.z - boss.z)) }
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

    private fun EncounterContext.attack(target: Player) {
        val melee = world.meleeCombat.reaches(boss, target) && roll(1, 2) == 1
        if (melee) {
            world.meleeCombat.swingAt(boss, target, world.tick, ANIM_ATTACK)
            log("swings at ${target.username}")
        } else {
            world.meleeCombat.fireAt(boss, target, world.tick, NpcRangedAttack(anim = ANIM_ATTACK, impact = IMPACT_MAGIC, maxHit = MAGIC_MAX), AttackType.MAGIC)
            log("casts at ${target.username}")
        }
        if (mayGrow(target, hitting = false) && roll(1, GROWTH_CHANCE) == 1) grow()
    }

    /**
     * [player]'s hit took [boss] some hitpoints: the other chance at growthlings ("upon attacking or being attacked", OSRS
     * Wiki, Bryophyta), after the hit, so the rest of a multi-hit special does nothing (its Trivia). Inferred: "being
     * attacked" as a hit of hers landing with damage.
     */
    fun damaged(world: World, boss: Npc, player: Player) {
        if (boss.type.id != BOSS) return
        val encounter = world.encounters.all.firstOrNull { it.boss === boss && !it.ended } ?: return
        with(encounter) { if (mayGrow(player, hitting = true) && roll(1, GROWTH_CHANCE) == 1) grow() }
    }

    /** [hitting] when [target]'s own hit is landing now, which counts as their attacking her before her fields say so. */
    private fun EncounterContext.mayGrow(target: Player, hitting: Boolean): Boolean =
        // Alive: a hit can kill her mid-attack (Retribution off her own swing) or be the killing blow.
        !boss.dead && boss.hitpoints > 0 && boss.hitpoints * 100 <= boss.maxHitpoints * GROWTH_BELOW_PERCENT &&
            living(world, state).isEmpty() && world.tick >= state.growthAfter &&
            (hitting || (boss.attacker === target && world.tick - boss.attackedTick <= ATTACKING_TICKS))

    /** Three growthlings on the free tiles around her, each going for whoever she fights. */
    private fun EncounterContext.grow() {
        val type = world.npcTypes[GROWTHLING] ?: return
        val target = state.target?.get()
        val run = world.giantLairs.runAt(boss.x, boss.z)
        for (tile in around(world, boss).take(GROWTHLINGS)) {
            val npc = world.spawnNpc(NpcSpawn(type, tile.x, tile.z, boss.level, source = "lair bryophyta growthling", respawns = false)) ?: continue
            npc.unleashed = true
            state.batch += WeakReference(npc)
            state.batchOut = true
            if (target != null && run?.instance?.contains(target.x, target.z) == true) world.meleeCombat.aggress(npc, target, world.tick)
        }
        log("brings ${state.growthlings.size} growthlings")
    }

    /** The tiles just outside [boss]'s footprint that a step out of it reaches, west side first, then south, east, north. */
    fun around(world: World, boss: Npc): List<Tile> {
        val size = boss.type.size
        val out = ArrayList<Tile>()
        fun tryAdd(fromX: Int, fromZ: Int, dx: Int, dz: Int) {
            if (world.canStep(boss.level, fromX, fromZ, dx, dz)) out += Tile(fromX + dx, fromZ + dz)
        }
        for (i in 0 until size) tryAdd(boss.x, boss.z + i, -1, 0)
        for (i in 0 until size) tryAdd(boss.x + i, boss.z, 0, -1)
        for (i in 0 until size) tryAdd(boss.x + size - 1, boss.z + i, 1, 0)
        for (i in 0 until size) tryAdd(boss.x + i, boss.z + size - 1, 0, 1)
        return out
    }

    /**
     * Whether [obj] is an axe that cuts growthlings: a woodcutting axe with the Axe attack type. "Although the blessed
     * axe is a woodcutting axe, it will not work ... other types of axes, such as battleaxes or the zombie axe, cannot
     * be used" (OSRS Wiki, Bryophyta).
     */
    fun axe(world: World, obj: Int): Boolean =
        world.woodcutting.data.axes.any { it.tool.obj == obj } && Weapons.category(world.objTypes[obj]) == Weapons.AXE

    /** Whether [player] wields what kills a growthling outright: such an axe, or magic secateurs. */
    fun wieldsCutter(world: World, player: Player): Boolean {
        val weapon = world.combat.weapon(player) ?: return false
        return weapon.id == MAGIC_SECATEURS || axe(world, weapon.id)
    }

    /** Whether [obj] used on a growthling kills it: such an axe, secateurs or magic secateurs. */
    fun cutter(world: World, obj: Int): Boolean = obj == SECATEURS || obj == MAGIC_SECATEURS || axe(world, obj)
}

fun registerBryophyta(world: World, lairs: GiantLairs) {
    with(Bryophyta) {
        world.handlers.onLoc(GATE, OPEN) { with(lairs) { enter(LAIR) } }
        world.handlers.onLoc(GATE, CHECK_COUNT) {
            arriveDelay()
            val count = player.vars[KILL_COUNT_VARP]
            world.dialogues.start(this) { mesbox(BryophytaLines.CHECK_COUNT, "count" to count) }
        }
        world.handlers.onLoc(EXIT, CLAMBER) { with(lairs) { leaveAsking() } }
        world.handlers.onLoc(EXIT, QUICK_EXIT) { lairs.leave(player) }
        world.handlers.onLoc(LOGS, TAKE_AXE) { op ->
            val loc = op.loc!!
            arriveDelay()
            // "provided they do not currently have one equipped or in their inventory, or any other item with the "Axe"
            // attack type" (OSRS Wiki, Logs (bronze axe)).
            val hasAxe = player.inventory.contents().any { (_, obj) -> Weapons.category(world.objTypes[obj.id]) == Weapons.AXE } ||
                world.combat.weaponCategory(player) == Weapons.AXE
            if (hasAxe) return@onLoc world.dialogues.start(this) { mesbox(BryophytaLines.HAVE_AXE) }
            if (player.inventory.freeSlots == 0) return@onLoc world.message(player, AxeLogsLines.NO_ROOM)
            if (!world.locs.exists(loc)) return@onLoc
            val axe = world.objTypes[BRONZE_AXE] ?: return@onLoc
            world.locs.replace(loc, LOGS_EMPTY, revertAfter = AxeLogs.RESPAWN_TICKS)
            player.inventory.add(axe, 1)
            world.dialogues.start(this) { objbox(BRONZE_AXE, BryophytaLines.AXE_TAKEN) }
        }
        world.handlers.onLoc(CHEST, OPEN) { op -> with(lairs) { openChest(op.loc!!) } }
        world.npcScripts.onDeath(listOf(BOSS)) { boss ->
            lairs.killed(boss)
            // Growthlings out as she dies stay to be fought (OSRS Wiki, Bryophyta, Trivia) until killed or the lair ends;
            // let go of here so the next of her starts without them.
            val state = state(boss)
            state.batch.clear()
            state.batchOut = false
            state.growthAfter = Long.MIN_VALUE
        }

        // Growthlings: held at 1 hitpoint (npc-combat.json) but for a wielded cutter, which kills one outright.
        // Only a melee hit: not a spell, nor damage of no attack (Retribution, recoil), even with a cutter wielded.
        world.combat.finishers[GROWTHLING] = { player, type -> type in MELEE_TYPES && wieldsCutter(world, player) }
        val sureHit = world.combat.sureHit
        world.combat.sureHit = { player, npc, type ->
            if (npc.type.id == GROWTHLING && type != AttackType.MAGIC && wieldsCutter(world, player)) SureHit.MAX else sureHit(player, npc, type)
        }
        // While her growthlings live she takes nothing but poison and venom, checked as each hit lands.
        val immune = world.combat.immune
        world.combat.immune = { npc -> (npc.type.id == BOSS && shielded(world, npc)) || immune(npc) }
        // Inferred: her lair is multi-way, as her growthlings fight the player alongside her and are fought with an axe
        // while she still attacks (OSRS Wiki, Bryophyta, Growthling).
        val multi = world.multiCombat.dynamic
        world.multiCombat.dynamic = { level, x, z -> lairs.runAt(x, z)?.lair === LAIR || multi(level, x, z) }
        val rolledOn = world.combat.rolledOn
        world.combat.rolledOn = { player, npc, type, rolled ->
            when {
                // Nothing rolled against her shield either, so no XP for it.
                npc.type.id == BOSS && shielded(world, npc) -> 0
                npc.type.id == GROWTHLING && type != AttackType.MAGIC && wieldsCutter(world, player) -> npc.hitpoints
                else -> rolledOn(player, npc, type, rolled)
            }
        }
        world.handlers.onObjOnNpc(GROWTHLING, Handlers.ANY_OBJ) { op ->
            val npc = op.npc ?: return@onObjOnNpc
            val obj = op.obj
            if (npc.dead || world.npcs[npc.index] !== npc) return@onObjOnNpc
            if (!cutter(world, obj)) return@onObjOnNpc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            world.meleeCombat.hit(npc, player, npc.hitpoints, world.tick, finishing = true)
        }
    }
}
