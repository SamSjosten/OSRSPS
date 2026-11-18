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
 * Tracking (Hunter step 8): polar kebbits (Rellekka), feldip weasels (Feldip), common and razor-backed kebbits
 * (Piscatoris) and desert devils (Uzer). Inspect on a den shows the first track segment of a trail of the player's own; at the node
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
 * inspected, the old one wiped; which noose animation each creature takes (by its area's name); a trail lasting until it ends or the player logs out; footprints going at once rather
 * than fading; Search on a hiding spot finding nothing; the polar kebbits' tunnels as hubs. Left out: the ring of
 * pursuit, the old boot from searching a hiding spot (the Wiki gives no rate), Hunters' Rumours' kebbity tufts, and the
 * herbiboar.
 */
class Tracking(private val world: World) {
    /** A segment of a trail, followed from its [TrackSegment.from] node if [forward]. */
    class Step(val segment: TrackSegment, val forward: Boolean)

    private class Run(val area: TrackingArea, val start: Int, val steps: List<Step>) {
        var shown = 0

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

    /** [player] logs out: their trail goes, so runs are kept only for players online (Codex review on #787). */
    fun loggedOut(player: Player) {
        runs.remove(player.saveKey)
    }

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
        const val FELDIP_WEASEL_FUR = 10119
        const val COMMON_KEBBIT_FUR = 10121
        const val DESERT_DEVIL_FUR = 10123
        const val LONG_KEBBIT_SPIKE = 10107

        /** A segment's varbit: no footprints, or footprints the way its pieces are turned or the other way. */
        const val HIDDEN = 0
        const val FORWARD = 3
        const val BACKWARD = 4

        const val INSPECT_ANIM = 5216 // hunting_searching_bushes
        const val NOOSE_POLAR_ANIM = 5256 // hunting_noose_polar
        const val NOOSE_DESERT_ANIM = 5257 // hunting_noose_desert
        const val NOOSE_JUNGLE_ANIM = 5258 // hunting_noose_jungle
        const val NOOSE_WOOD_ANIM = 5259 // hunting_noose_wood

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
        val COMMON_KEBBIT = TrackedCreature("common kebbit", 3, 360, COMMON_KEBBIT_FUR, NOOSE_WOOD_ANIM)
        val FELDIP_WEASEL = TrackedCreature("feldip weasel", 7, 480, FELDIP_WEASEL_FUR, NOOSE_JUNGLE_ANIM)
        val DESERT_DEVIL = TrackedCreature("desert devil", 13, 660, DESERT_DEVIL_FUR, NOOSE_DESERT_ANIM)
        val RAZOR_BACKED_KEBBIT = TrackedCreature("razor-backed kebbit", 49, 3485, LONG_KEBBIT_SPIKE, NOOSE_WOOD_ANIM)

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

        /**
         * The Feldip Hunter area's feldip weasels: two burrows, jungle plants, six bushes, ten nodes (the Wiki's trail map and tile markers).
         */
        val FELDIP = TrackingArea(
            FELDIP_WEASEL, level = 0, dens = listOf(19479, 19593), hides = listOf(19427), minNodes = 5,
            nodes = listOf(
                TrackNode(den = Tile(2525, 2889), segments = listOf(3036, 3037, 3038)),
                TrackNode(hide = Tile(2525, 2882), segments = listOf(3036, 3037, 3040)),
                TrackNode(segments = listOf(3040, 3041, 3045)),
                TrackNode(hide = Tile(2553, 2888), segments = listOf(3048, 3050, 3051)),
                TrackNode(hide = Tile(2540, 2886), segments = listOf(3044, 3046, 3047)),
                TrackNode(hide = Tile(2531, 2890), segments = listOf(3038, 3039, 3043)),
                TrackNode(segments = listOf(3043, 3047, 3048)),
                TrackNode(hide = Tile(2542, 2881), segments = listOf(3045, 3046, 3049)),
                TrackNode(den = Tile(2554, 2882), segments = listOf(3049, 3050, 3051)),
                TrackNode(hide = Tile(2533, 2885), segments = listOf(3039, 3041, 3044)),
            ),
            segments = listOf(
                TrackSegment(3036, listOf(19403), 1, 0),
                TrackSegment(3037, listOf(19404), 0, 1),
                TrackSegment(3038, listOf(19405), 0, 5),
                TrackSegment(3039, listOf(19406), 5, 9),
                TrackSegment(3040, listOf(19407), 2, 1),
                TrackSegment(3041, listOf(19408), 2, 9),
                TrackSegment(3043, listOf(19409), 5, 6),
                TrackSegment(3044, listOf(19410), 9, 4),
                TrackSegment(3045, listOf(19411), 7, 2),
                TrackSegment(3046, listOf(19412), 7, 4),
                TrackSegment(3047, listOf(19413), 4, 6),
                TrackSegment(3048, listOf(19414), 6, 3),
                TrackSegment(3049, listOf(19415), 8, 7),
                TrackSegment(3050, listOf(19416), 3, 8),
                TrackSegment(3051, listOf(19417), 3, 8),
            ),
        )

        /**
         * The Piscatoris Hunter area's common kebbits, in its south: three burrows, plants, eight bushes, eleven nodes (the Wiki's trail map and tile markers).
         */
        val PISCATORIS_COMMON = TrackingArea(
            COMMON_KEBBIT, level = 0, dens = listOf(19492, 19707, 19710), hides = listOf(19429), minNodes = 5,
            nodes = listOf(
                TrackNode(den = Tile(2331, 3562), segments = listOf(2995, 2996, 2997)),
                TrackNode(hide = Tile(2323, 3563), segments = listOf(2995, 2999, 3000)),
                TrackNode(hide = Tile(2322, 3570), segments = listOf(2999, 3004, 3007)),
                TrackNode(hide = Tile(2332, 3578), segments = listOf(3010, 3011, 3013)),
                TrackNode(hide = Tile(2337, 3565), segments = listOf(2997, 2998, 3001)),
                TrackNode(den = Tile(2341, 3577), segments = listOf(3003, 3012, 3013)),
                TrackNode(hide = Tile(2327, 3573), segments = listOf(3007, 3008, 3009)),
                TrackNode(hide = Tile(2343, 3568), segments = listOf(2998, 3002, 3003)),
                TrackNode(den = Tile(2322, 3576), segments = listOf(3004, 3009, 3010)),
                TrackNode(hide = Tile(2336, 3571), segments = listOf(3001, 3002, 3012, 3011)),
                TrackNode(hide = Tile(2332, 3568), segments = listOf(2996, 3000, 3008)),
            ),
            segments = listOf(
                TrackSegment(2995, listOf(19339), 0, 1),
                TrackSegment(2996, listOf(19340), 0, 10),
                TrackSegment(2997, listOf(19341), 0, 4),
                TrackSegment(2998, listOf(19342), 7, 4),
                TrackSegment(2999, listOf(19343), 1, 2),
                TrackSegment(3000, listOf(19344), 10, 1),
                TrackSegment(3001, listOf(19345), 4, 9),
                TrackSegment(3002, listOf(19346), 7, 9),
                TrackSegment(3003, listOf(19347), 5, 7),
                TrackSegment(3004, listOf(19348), 8, 2),
                TrackSegment(3007, listOf(19349), 2, 6),
                TrackSegment(3008, listOf(19350), 6, 10),
                TrackSegment(3009, listOf(19351), 8, 6),
                TrackSegment(3010, listOf(19352), 8, 3),
                TrackSegment(3011, listOf(19353), 3, 9),
                TrackSegment(3012, listOf(19354), 5, 9),
                TrackSegment(3013, listOf(19355), 3, 5),
            ),
        )

        /**
         * The Piscatoris Hunter area's razor-backed kebbits, north of the common kebbits: three burrows, plants, eight bushes, twelve nodes (the Wiki's trail map and tile markers).
         */
        val PISCATORIS_RAZOR = TrackingArea(
            RAZOR_BACKED_KEBBIT, level = 0, dens = listOf(19438, 19579, 19580), hides = listOf(19428), minNodes = 5,
            nodes = listOf(
                TrackNode(den = Tile(2353, 3595), segments = listOf(2974, 2975, 2976)),
                TrackNode(hide = Tile(2355, 3601), segments = listOf(2975, 2977, 2978)),
                TrackNode(hide = Tile(2349, 3604), segments = listOf(2976, 2978, 2988, 2983)),
                TrackNode(hide = Tile(2360, 3602), segments = listOf(2974, 2977, 2979, 2980)),
                TrackNode(hide = Tile(2357, 3607), segments = listOf(2979, 2981, 2982)),
                TrackNode(den = Tile(2360, 3611), segments = listOf(2980, 2981, 2992)),
                TrackNode(hide = Tile(2354, 3609), segments = listOf(2982, 2983, 2989)),
                TrackNode(segments = listOf(2988, 2989, 2990)),
                TrackNode(hide = Tile(2351, 3619), segments = listOf(2990, 2991, 2986)),
                TrackNode(den = Tile(2357, 3624), segments = listOf(2986, 2985, 2987)),
                TrackNode(hide = Tile(2358, 3620), segments = listOf(2987, 2991, 2993)),
                TrackNode(hide = Tile(2362, 3615), segments = listOf(2985, 2993, 2992)),
            ),
            segments = listOf(
                TrackSegment(2974, listOf(19356), 3, 0),
                TrackSegment(2975, listOf(19357), 1, 0),
                TrackSegment(2976, listOf(19358), 2, 0),
                TrackSegment(2977, listOf(19359), 1, 3),
                TrackSegment(2978, listOf(19360), 2, 1),
                TrackSegment(2979, listOf(19361), 4, 3),
                TrackSegment(2980, listOf(19362), 5, 3),
                TrackSegment(2981, listOf(19363), 5, 4),
                TrackSegment(2982, listOf(19364), 6, 4),
                TrackSegment(2983, listOf(19365), 2, 6),
                TrackSegment(2985, listOf(19372), 11, 9),
                TrackSegment(2986, listOf(19373), 9, 8),
                TrackSegment(2987, listOf(19374), 9, 10),
                TrackSegment(2988, listOf(19375), 2, 7),
                TrackSegment(2989, listOf(19376), 7, 6),
                TrackSegment(2990, listOf(19377), 7, 8),
                TrackSegment(2991, listOf(19378), 8, 10),
                TrackSegment(2992, listOf(19379), 5, 11),
                TrackSegment(2993, listOf(19380), 11, 10),
            ),
        )

        /**
         * The Uzer Hunter area's desert devils: two burrows, cacti and rockslides, six patches of disturbed sand, ten nodes (the Wiki counts the eight with a burrow or sand; two more are where three segments meet).
         */
        val UZER = TrackingArea(
            DESERT_DEVIL, level = 0, dens = listOf(19526, 19552), hides = listOf(19430), minNodes = 5,
            nodes = listOf(
                TrackNode(segments = listOf(3025, 3030, 3031)),
                TrackNode(hide = Tile(3414, 3121), segments = listOf(3018, 3022, 3026)),
                TrackNode(den = Tile(3402, 3131), segments = listOf(3016, 3017, 3018)),
                TrackNode(hide = Tile(3411, 3108), segments = listOf(3026, 3031, 3032)),
                TrackNode(hide = Tile(3405, 3124), segments = listOf(3017, 3020, 3021)),
                TrackNode(hide = Tile(3393, 3122), segments = listOf(3016, 3019, 3023)),
                TrackNode(segments = listOf(3019, 3020, 3024)),
                TrackNode(den = Tile(3396, 3106), segments = listOf(3023, 3027, 3032)),
                TrackNode(hide = Tile(3407, 3121), segments = listOf(3021, 3022, 3025)),
                TrackNode(hide = Tile(3400, 3114), segments = listOf(3024, 3027, 3030)),
            ),
            segments = listOf(
                TrackSegment(3016, listOf(19388), 5, 2),
                TrackSegment(3017, listOf(19389), 4, 2),
                TrackSegment(3018, listOf(19390), 2, 1),
                TrackSegment(3019, listOf(19391), 6, 5),
                TrackSegment(3020, listOf(19392), 4, 6),
                TrackSegment(3021, listOf(19393), 8, 4),
                TrackSegment(3022, listOf(19394), 8, 1),
                TrackSegment(3023, listOf(19395), 7, 5),
                TrackSegment(3024, listOf(19396), 6, 9),
                TrackSegment(3025, listOf(19397), 0, 8),
                TrackSegment(3026, listOf(19398), 1, 3),
                TrackSegment(3027, listOf(19399), 9, 7),
                TrackSegment(3030, listOf(19400), 9, 0),
                TrackSegment(3031, listOf(19401), 3, 0),
                TrackSegment(3032, listOf(19402), 3, 7),
            ),
        )

        val AREAS = listOf(POLAR, FELDIP, PISCATORIS_COMMON, PISCATORIS_RAZOR, UZER)
    }
}
