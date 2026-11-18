package osrsps.game.skill.hunter

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/**
 * A creature caught by tracking: the Hunter [level] to start a trail, the XP in tenths, the [item] it gives besides bones
 * and raw beast meat, and the noose animation [catchAnim] the player plays on its hiding spot.
 */
class TrackedCreature(val name: String, val level: Int, val xpTenths: Int, val item: Int, val catchAnim: Int)

/**
 * A node of a trail: a tile where track segments meet, holding a [den] a trail can start from, a [hide] the creature can
 * hide in, both or neither. [segments] are the track segments' varbits that meet there. A [hub] is where tunnels meet
 * underground (the polar kebbits'): a trail passes through it to another tunnel without it counting as a node.
 */
class TrackNode(val den: Tile? = null, val hide: Tile? = null, val segments: List<Int>, val hub: Boolean = false)

/**
 * A track segment: the player varbit [varbit] showing its footprints (the cache's `hunting_trail_stateN_M`), the loc
 * types of its [connectors] (`hunting_trail_clueN_M`), and the nodes it runs between, [from] to [to] being the way its
 * footprints are placed in the map.
 */
class TrackSegment(val varbit: Int, val connectors: List<Int>, val from: Int, val to: Int) {
    fun other(node: Int): Int = if (node == from) to else from
}

/**
 * One hunting area's trails: the [creature], the map [level] they're on, the [dens] and [hides] loc types, and the node
 * graph. A trail ends at a hiding spot once it has passed [minNodes] nodes, counting the den's.
 */
class TrackingArea(
    val creature: TrackedCreature,
    val level: Int,
    val dens: List<Int>,
    val hides: List<Int>,
    val minNodes: Int,
    val nodes: List<TrackNode>,
    val segments: List<TrackSegment>,
) {
    private val byVarbit = segments.associateBy { it.varbit }

    fun segment(varbit: Int): TrackSegment = byVarbit.getValue(varbit)

    fun denAt(x: Int, z: Int): Int? = nodes.indexOfFirst { it.den?.x == x && it.den.z == z }.takeIf { it >= 0 }

    fun hideAt(x: Int, z: Int): Int? = nodes.indexOfFirst { it.hide?.x == x && it.hide.z == z }.takeIf { it >= 0 }

    fun owns(connector: Int): Boolean = segments.any { connector in it.connectors }

    init {
        for ((i, node) in nodes.withIndex()) {
            for (varbit in node.segments) {
                val segment = byVarbit[varbit] ?: error("${creature.name}: node $i names a segment ($varbit) the area has none of")
                require(segment.from == i || segment.to == i) { "${creature.name}: segment $varbit doesn't run to node $i" }
            }
        }
        for (segment in segments) {
            require(segment.varbit in nodes[segment.from].segments && segment.varbit in nodes[segment.to].segments) {
                "${creature.name}: segment ${segment.varbit} isn't listed on both its nodes"
            }
        }
    }
}

/**
 * Tracking (Hunter step 8): Inspect on a den shows the first track segment of a trail of the player's own; at the node
 * it leads to, Inspect on the connector of the next segment shows that one, and any other connector shows nothing. Once
 * the trail has passed its area's minimum number of nodes it ends at the first hiding spot it reaches, where Attack
 * with a noose wand wielded catches the creature for bones, raw beast meat and its fur (a razor-backed kebbit's long
 * spike). Each player follows a trail of their own, shown by their own varbits, so no two compete.
 *
 * Sources: the OSRS Wiki's Tracking page (read 2026-11-18) for the dens, connectors, hiding spots, that a path uses a
 * segment once but may pass a node twice, that it ends at the first hiding spot at or beyond its minimum node count,
 * and the table of creatures, levels, XP and minimums (polar kebbits 3, the rest 5); each creature's page for its loot
 * and the polar kebbit's for its catch chance (about 7% missed at level 1, none from about level 3, Mod Ash); the Noose
 * wand page for a kebbit scared off when attacked without one. The rev 242 cache for everything placed: each area's
 * dens (`hunting_trail_spawn*`), connectors (`hunting_trail_clueN_M`, which share their N_M with the segment
 * `hunting_trail_stateN_M` they show), hiding spots (`hunting_trail_end_*`) and the footprint locs of each segment,
 * multilocs on player varbits (3 and 4 show the footprints one way or the other, 5 and 6 fade them). The nodes, which
 * segments meet where, are read off the placed footprints and the Wiki's trail maps and tile markers.
 *
 * Guessed: which way the footprints point (varbit 3 when the trail goes the way the segment's pieces are turned); the
 * hunting_searching_bushes animation and [INSPECT_TICKS] on every Inspect; [CATCH_TICKS]; a miss chance for every
 * creature as the polar kebbit's, by levels over its requirement ([missChance]); a trail starting afresh from any den
 * inspected, the old one wiped; a trail lasting until it ends or the player logs out; footprints going at once rather
 * than fading; Search on a hiding spot finding nothing; the polar kebbits' tunnels as hubs. Left out: the ring of
 * pursuit, the old boot from searching a hiding spot (the Wiki gives no rate), Hunters' Rumours' kebbity tufts, and the
 * herbiboar.
 */
class Tracking(private val world: World) {
    /** A segment of a trail, followed from its [TrackSegment.from] node if [forward]. */
    class Step(val segment: TrackSegment, val forward: Boolean)

    private class Run(val area: TrackingArea, val start: Int, val steps: List<Step>) {
        var shown = 0

        /** The node the trail has been followed to. */
        val at: Int
            get() {
                var node = start
                for (i in 0 until shown) node = steps[i].segment.other(node)
                return node
            }

        val done: Boolean get() = shown == steps.size

        val end: Int get() {
            var node = start
            for (step in steps) node = step.segment.other(node)
            return node
        }
    }

    private val runs = HashMap<String, Run>()

    fun register() {
        val dens = AREAS.flatMap { area -> area.dens.map { it to area } }
        for ((den, _) in dens.distinctBy { it.first }) world.handlers.onLoc(den, INSPECT) { op -> inspectDen(op.loc ?: return@onLoc) }
        for (connector in AREAS.flatMap { area -> area.segments.flatMap { it.connectors } }.distinct()) {
            world.handlers.onLoc(connector, INSPECT) { op -> inspectConnector(op.loc ?: return@onLoc) }
        }
        for (hide in AREAS.flatMap { it.hides }.distinct()) {
            world.handlers.onLoc(hide, SEARCH) { op -> search(op.loc ?: return@onLoc) }
            world.handlers.onLoc(hide, ATTACK) { op -> attack(op.loc ?: return@onLoc) }
        }
    }

    /** Whether [player] is following a trail; for tests. */
    fun tracking(player: Player): Boolean = player.saveKey in runs

    /**
     * [player] logs in, or a scenario starts or ends: no trail of theirs goes on, and no footprint shows whatever their
     * varbits held.
     */
    fun forget(player: Player) {
        runs.remove(player.saveKey)
        for (area in AREAS) clear(player, area)
    }

    private suspend fun ScriptContext.inspectDen(loc: ZoneLoc) {
        val area = AREAS.firstOrNull { loc.id in it.dens && it.level == loc.level && it.denAt(loc.x, loc.z) != null } ?: return
        val start = area.denAt(loc.x, loc.z) ?: return
        if (player.skills.currentLevel(Skill.HUNTER) < area.creature.level) {
            return world.message(player, HunterLines.TRACK_LEVEL, "level" to area.creature.level)
        }
        inspecting()
        runs.remove(player.saveKey)?.let { clear(player, it.area) }
        val steps = path(area, start, world.random) ?: return world.message(player, HunterLines.TRACK_NOTHING)
        val run = Run(area, start, steps)
        runs[player.saveKey] = run
        show(run)
    }

    private suspend fun ScriptContext.inspectConnector(loc: ZoneLoc) {
        val run = runs[player.saveKey]
        inspecting()
        if (run == null || !run.area.owns(loc.id) || run.done || run !== runs[player.saveKey]) {
            return world.message(player, HunterLines.TRACK_NOTHING)
        }
        val next = run.steps[run.shown]
        if (loc.id !in next.segment.connectors) return world.message(player, HunterLines.TRACK_NOTHING)
        show(run)
    }

    private fun ScriptContext.show(run: Run) {
        val step = run.steps[run.shown]
        setVarbit(player, step.segment.varbit, if (step.forward) FORWARD else BACKWARD)
        run.shown++
        world.message(player, HunterLines.TRACK_FOUND)
    }

    private suspend fun ScriptContext.inspecting() {
        player.anim(INSPECT_ANIM)
        player.actionDelay = tick + INSPECT_TICKS
        delay(INSPECT_TICKS)
    }

    private fun ScriptContext.hiding(loc: ZoneLoc): Run? {
        val run = runs[player.saveKey] ?: return null
        if (!run.done || loc.id !in run.area.hides || loc.level != run.area.level) return null
        return run.takeIf { run.area.hideAt(loc.x, loc.z) == run.end }
    }

    private fun ScriptContext.search(loc: ZoneLoc) {
        world.message(player, if (hiding(loc) != null) HunterLines.TRACK_HIDING else HunterLines.TRACK_EMPTY)
    }

    private suspend fun ScriptContext.attack(loc: ZoneLoc) {
        val run = hiding(loc) ?: return world.message(player, HunterLines.TRACK_EMPTY)
        val creature = run.area.creature
        // The Noose wand page: without one wielded the creature is scared off, and the trail with it.
        if (player.equipment[WearPos.WEAPON]?.id != NOOSE_WAND) {
            end(player, run)
            return world.message(player, HunterLines.TRACK_NO_NOOSE, "creature" to creature.name)
        }
        player.anim(creature.catchAnim)
        player.actionDelay = tick + CATCH_TICKS
        end(player, run)
        delay(CATCH_TICKS)
        val (missed, out) = missChance(player.skills.currentLevel(Skill.HUNTER) - creature.level)
        if (world.random.chance(missed, out)) return world.message(player, HunterLines.TRACK_ESCAPED, "creature" to creature.name)
        player.skills.addTenths(Skill.HUNTER, creature.xpTenths)
        for (id in listOf(BONES, RAW_BEAST_MEAT, creature.item)) world.objTypes[id]?.let { world.groundObjs.give(player, it, 1) }
        world.message(player, HunterLines.TRACK_CAUGHT, "creature" to creature.name)
    }

    private fun end(player: Player, run: Run) {
        if (runs[player.saveKey] === run) runs.remove(player.saveKey)
        clear(player, run.area)
    }

    private fun clear(player: Player, area: TrackingArea) {
        for (segment in area.segments) setVarbit(player, segment.varbit, HIDDEN)
    }

    private fun setVarbit(player: Player, varbit: Int, value: Int) {
        if (player.vars.types.varbit(varbit) != null) player.vars.setVarbit(varbit, value)
    }

    companion object {
        /** Dens' and connectors' op1 Inspect; hiding spots' op1 Search and op2 Attack (the 242 cache). */
        const val INSPECT = 1
        const val SEARCH = 1
        const val ATTACK = 2

        const val NOOSE_WAND = 10150
        const val BONES = 526
        const val RAW_BEAST_MEAT = 9986
        const val POLAR_KEBBIT_FUR = 10117

        /** A segment's varbit: no footprints, or footprints the way its pieces are turned or the other way. */
        const val HIDDEN = 0
        const val FORWARD = 3
        const val BACKWARD = 4

        const val INSPECT_ANIM = 5216 // hunting_searching_bushes
        const val NOOSE_POLAR_ANIM = 5256 // hunting_noose_polar

        /** Guessed. */
        const val INSPECT_TICKS = 2
        const val CATCH_TICKS = 3
        private const val PATH_TRIES = 50

        /**
         * A trail from [start]: a random segment not yet used from each node, counting each node reached but a hub, until it
         * reaches a hiding spot at or beyond the area's minimum; one that runs out of segments first is drawn again.
         */
        fun path(area: TrackingArea, start: Int, random: GameRandom): List<Step>? {
            repeat(PATH_TRIES) {
                val steps = ArrayList<Step>()
                val used = HashSet<Int>()
                var node = start
                var count = 1
                while (true) {
                    if (count >= area.minNodes && area.nodes[node].hide != null) return steps
                    val open = area.nodes[node].segments.filter { it !in used }
                    if (open.isEmpty()) break
                    val segment = area.segment(random.pick(open))
                    used += segment.varbit
                    steps += Step(segment, forward = segment.from == node)
                    node = segment.other(node)
                    if (!area.nodes[node].hub) count++
                }
            }
            return null
        }

        /**
         * The chance of a miss [levelsOver] the creature's requirement, as numerator and denominator: Mod Ash's 7% at
         * a polar kebbit's level 1, falling to none by about level 3; 3.5% one level over is guessed, as is the same
         * for every creature.
         */
        fun missChance(levelsOver: Int): Pair<Int, Int> = when {
            levelsOver <= 0 -> 7 to 100
            levelsOver == 1 -> 7 to 200
            else -> 0 to 1
        }

        val POLAR_KEBBIT = TrackedCreature("polar kebbit", 1, 300, POLAR_KEBBIT_FUR, NOOSE_POLAR_ANIM)

        /**
         * The Rellekka Hunter area's polar kebbits, on level 1 (the cache's map): two holes, nine connectors (three
         * hollow logs and six tunnels), four snow drifts. A hollow log's segment runs from node to node; a tunnel's runs
         * from a node to its tunnel, and the tunnels meet in two hubs of three, so a trail into one tunnel comes out of
         * another (the Wiki's polar kebbit strategy).
         */
        val POLAR = TrackingArea(
            POLAR_KEBBIT, level = 1, dens = listOf(19640, 19641), hides = listOf(19435), minNodes = 3,
            nodes = listOf(
                TrackNode(den = Tile(2717, 3819), hide = Tile(2718, 3820), segments = listOf(3053, 3055, 3056)),
                TrackNode(hide = Tile(2708, 3819), segments = listOf(3053, 3054, 3057)),
                TrackNode(den = Tile(2711, 3830), hide = Tile(2712, 3831), segments = listOf(3057, 3060, 3061)),
                TrackNode(hide = Tile(2716, 3827), segments = listOf(3058, 3059, 3060)),
                TrackNode(segments = listOf(3054, 3055, 3058), hub = true),
                TrackNode(segments = listOf(3056, 3059, 3061), hub = true),
            ),
            segments = listOf(
                TrackSegment(3053, listOf(19418), 0, 1),
                TrackSegment(3054, listOf(19419), 4, 1),
                TrackSegment(3055, listOf(19420), 4, 0),
                TrackSegment(3056, listOf(19421), 5, 0),
                TrackSegment(3057, listOf(19422), 1, 2),
                TrackSegment(3058, listOf(19423), 4, 3),
                TrackSegment(3059, listOf(19424), 3, 5),
                TrackSegment(3060, listOf(19425), 2, 3),
                TrackSegment(3061, listOf(19426), 5, 2),
            ),
        )

        val AREAS = listOf(POLAR)
    }
}
