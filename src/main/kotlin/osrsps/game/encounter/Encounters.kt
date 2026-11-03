package osrsps.game.encounter

import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.Tile

/**
 * One fight in progress: [def] against [boss], with whoever [inArea] says is in it. See [Encounters].
 */
class Encounter internal constructor(
    val id: Int,
    val def: EncounterDef,
    override val boss: Npc,
    private val world: World,
    private val inArea: (Int, Int) -> Boolean,
    startPhase: Int,
) : EncounterContext {
    /** The phase it is in, an index into [EncounterDef.phases]. */
    var phase: Int = startPhase
        private set

    /** The tick its phase began on. */
    private var phaseStart: Long = world.tick

    /** The next step of the phase's cycle, and the tick it is due on. */
    private var step = 0
    private var stepDue = 0L

    private val hazards = ArrayList<Hazard>()
    private val addList = ArrayList<Npc>()

    private val startTick = world.tick

    /**
     * What happened, oldest first: phases, attacks, hazards and their hits, each led by the ticks since the fight began
     * and the phase. Kept to the last [LOG_LINES].
     */
    val lines = ArrayDeque<String>()

    /** How many of its oldest lines [lines] has let go of; a digest from the start can't be made once any have gone. */
    var droppedLines: Int = 0
        private set

    /** True once it is over. */
    var ended: Boolean = false
        private set

    val activeHazards: List<Hazard> get() = hazards

    override val phaseTick: Long get() = world.tick - phaseStart

    override val players: List<Player>
        get() = world.onlinePlayers.filter { !it.dead && it.level == boss.level && inArea(it.x, it.z) }.sortedBy { it.index }

    override val adds: List<Npc> get() = addList.filter { !it.dead && world.npcs[it.index] === it }

    override fun roll(min: Int, max: Int): Int = world.random.between(min, max)

    override fun randomPlayer(): Player? {
        val list = players
        return if (list.isEmpty()) null else list[world.random.nextInt(list.size)]
    }

    override fun hit(player: Player, damage: Int): Int {
        val dealt = world.meleeCombat.hitPlayer(player, boss, damage, world.tick)
        log("hit ${player.username} for $dealt")
        return dealt
    }

    override fun hazard(tiles: Collection<Tile>, warning: Int, warnTicks: Int, ticks: Int, minDamage: Int, maxDamage: Int, spotanim: Int): Hazard {
        require(warnTicks >= 0 && ticks > 0) { "a hazard warned $warnTicks ticks ahead for $ticks ticks" }
        require(minDamage in 0..maxDamage) { "hazard damage $minDamage to $maxDamage" }
        val set = tiles.toCollection(LinkedHashSet())
        require(set.isNotEmpty()) { "a hazard on no tiles" }
        val start = world.tick + warnTicks
        val hazard = Hazard(set, boss.level, start, start + ticks - 1, minDamage, maxDamage, spotanim)
        hazards += hazard
        if (warning >= 0) for (tile in set) world.zones.mapAnim(tile.x, tile.z, boss.level, warning)
        log("hazard on ${set.joinToString(" ") { "${it.x},${it.z}" }} from tick ${start - world.tick} for $ticks")
        return hazard
    }

    override fun spawnAdd(type: NpcType, x: Int, z: Int): Npc? {
        val npc = world.spawnNpc(NpcSpawn(type, x, z, boss.level, source = "encounter ${def.name}", respawns = false)) ?: return null
        addList += npc
        log("add ${type.name} at $x,$z")
        return npc
    }

    override fun log(text: String) {
        lines.addLast("${world.tick - startTick} ${def.phases[phase].name}: $text")
        while (lines.size > LOG_LINES) {
            lines.removeFirst()
            droppedLines++
        }
    }

    /** Ticks since the fight began. */
    val ticks: Long get() = world.tick - startTick

    /** The last tick the fight has run, its start included. */
    private var lastTick = -1L

    /** How many ticks after its start the fight has run to; its log is complete up to here. */
    val processedTicks: Long get() = lastTick - startTick

    /** Whether [x], [z] is in the fight's area. */
    fun covers(x: Int, z: Int): Boolean = inArea(x, z)

    /**
     * Starts the fight and runs its first tick now, steps with no delay and hazards due at once included, so it plays
     * the same whether it starts while the world runs or while it is paused. The NPC phase of the same tick then
     * leaves it be.
     */
    internal fun begin() {
        startPhase(phase)
        runTick()
    }

    /** Ends the fight, and says so, when its boss has died or left the world, as a hit's side effect can make it. */
    private fun bossLeft(): Boolean {
        if (ended) return true
        val gone = world.npcs[boss.index] !== boss
        if (!boss.dead && !gone) return false
        end(if (boss.dead) "boss dead" else "boss gone")
        return true
    }

    private fun startPhase(index: Int) {
        phase = index
        phaseStart = world.tick
        step = 0
        val def = this.def.phases[index]
        stepDue = world.tick + (def.cycle.firstOrNull()?.delay ?: 0)
        log("phase starts")
        def.onStart(this)
    }

    /** One tick of the fight, in the NPC phase; nothing on the tick it started, which [begin] ran. */
    internal fun process() {
        if (lastTick == world.tick) return
        runTick()
    }

    private fun runTick() {
        lastTick = world.tick
        // Adds that have left the world, dead or removed, are let go of; one dying still counts until it is gone.
        addList.removeAll { world.npcs[it.index] !== it }
        if (bossLeft()) return
        val current = def.phases[phase]
        if (phase < def.phases.size - 1 && phaseOver(current)) startPhase(phase + 1)
        if (bossLeft()) return
        runCycle()
        if (bossLeft()) return
        landHazards()
        bossLeft()
    }

    private fun phaseOver(phase: PhaseDef): Boolean {
        val percent = phase.endsAtPercent
        if (percent != null && boss.hitpoints * 100 <= boss.maxHitpoints * percent) return true
        val after = phase.endsAfter
        return after != null && phaseTick >= after
    }

    private fun runCycle() {
        val cycle = def.phases[phase].cycle
        if (cycle.isEmpty()) return
        // Steps with no delay run on the same tick as the one before.
        while (world.tick >= stepDue) {
            val due = cycle[step]
            due.action(this)
            if (ended || boss.dead || world.npcs[boss.index] !== boss) return
            step = (step + 1) % cycle.size
            stepDue += cycle[step].delay
        }
    }

    private fun landHazards() {
        val now = world.tick
        hazards.removeAll { it.endTick < now }
        for (hazard in hazards.toList()) {
            if (boss.dead || world.npcs[boss.index] !== boss) return
            if (hazard.startTick > now) continue
            if (hazard.startTick == now && hazard.spotanim >= 0) {
                for (tile in hazard.tiles) world.zones.mapAnim(tile.x, tile.z, hazard.level, hazard.spotanim)
            }
            for (player in players) {
                // A hit can end the fight (Retribution killing the boss); nothing lands after that.
                if (boss.dead || world.npcs[boss.index] !== boss) return
                if (player.level != hazard.level || Tile(player.x, player.z) !in hazard.tiles) continue
                // From no one, so standing in one neither starts a fight nor makes the player hit back. Guessed.
                val dealt = world.meleeCombat.hitPlayer(player, null, roll(hazard.minDamage, hazard.maxDamage), now)
                // Still the boss's attack: the player counts as in combat, which cuts a Home Teleport or lodestone cast
                // short and holds a logout, as a hit from the boss itself would (exploit review round 11, ENC-1).
                // Guessed: live's boss mechanics put the player in combat; nothing on the Wiki says otherwise.
                if (!player.dead) player.attackedTick = now
                log("hazard hit ${player.username} for $dealt")
            }
        }
    }

    internal fun end(why: String) {
        if (ended) return
        log("ends: $why")
        ended = true
        hazards.clear()
        for (add in addList) if (world.npcs[add.index] === add) world.removeNpc(add)
        addList.clear()
    }

    override fun toString() = "${def.name} #$id"

    companion object {
        const val LOG_LINES = 200
    }
}

/**
 * The boss fights in progress (raid step 12). [start] begins one; [process] runs each a tick at a time in the NPC phase,
 * after NPC scripts: the phase changes when the boss's hitpoints fall to its threshold or its time is up, then the due
 * steps of the phase's cycle, then the hazards. A fight ends when its boss dies or leaves the world, and takes its adds
 * and hazards with it. Every roll comes from the world's random, so `::seed` before a fight makes it play the same way
 * each time. Where in the tick it runs, and hazards hurting from no one, are guessed until a recording shows a boss.
 */
class Encounters(private val world: World, defs: List<EncounterDef> = emptyList()) {
    private val list = ArrayList<Encounter>()
    private var nextId = 1

    /** The fights content has written, by lower-case name. */
    val defs: Map<String, EncounterDef> = defs.associateBy { it.name.lowercase() }.also {
        require(it.size == defs.size) { "two encounters with the same name" }
    }

    val all: List<Encounter> get() = list

    /**
     * Starts [def] against [boss], with the players [inArea] says are in the fight, from the phase at [startPhase]. The
     * fight's first tick runs now: the phase's start, steps with no delay and hazards due at once.
     */
    fun start(def: EncounterDef, boss: Npc, inArea: (Int, Int) -> Boolean, startPhase: Int = 0): Encounter {
        require(startPhase in def.phases.indices) { "${def.name} has no phase $startPhase" }
        require(list.none { it.boss === boss }) { "$boss is already in a fight" }
        val encounter = Encounter(nextId++, def, boss, world, inArea, startPhase)
        list += encounter
        encounter.begin()
        return encounter
    }

    /** Ends [encounter] now. */
    fun stop(encounter: Encounter) {
        encounter.end("stopped")
        list -= encounter
    }

    fun process() {
        for (encounter in list.toList()) {
            encounter.process()
            if (encounter.ended) list -= encounter
        }
    }
}
