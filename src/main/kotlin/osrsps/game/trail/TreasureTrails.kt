package osrsps.game.trail

import osrsps.game.GameLines
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.FrameSlot

/** Treasure Trails' messages and item boxes. The clue words themselves come from the cache ([TrailLoader]). */
object TrailLines : Lines("trail", "Treasure Trails") {
    val BOX_REFUSES = line("box_refuses", "Open on a scroll box while holding a clue of its tier (the Wiki's Scroll box (easy) page)")
    val BOX_NO_SPACE = line("box_no_space", "OUR OWN: Open on one of several scroll boxes with a full backpack")
    val ANOTHER_CLUE = line("another_clue", "the item box showing the next clue scroll after a step is solved")
    val CASKET = line("casket", "the item box showing the reward casket after the last step")
    val CHECK_STEPS = line("check_steps", "Check steps on a clue scroll", "count")
    val COORDINATE = line("coordinate", "a coordinate clue's scroll: two lines, latitude then longitude", "lat_deg", "lat_min", "lat_dir", "lon_deg", "lon_min", "lon_dir")
    val NORTH = line("north", "a coordinate clue: the word for north")
    val SOUTH = line("south", "a coordinate clue: the word for south")
    val EAST = line("east", "a coordinate clue: the word for east")
    val WEST = line("west", "a coordinate clue: the word for west")
    val SUSPICION_BEGINNER = line("suspicion_beginner", "a beginner scroll box dropped while the player holds as many as they may (the Wiki's Treasure Trails page)")
    val SUSPICION_EASY = line("suspicion_easy", "an easy scroll box likewise")
    val SUSPICION_MEDIUM = line("suspicion_medium", "a medium scroll box likewise")
    val SUSPICION_HARD = line("suspicion_hard", "a hard scroll box likewise")
    val SUSPICION_ELITE = line("suspicion_elite", "an elite scroll box likewise")
    val SUSPICION_MASTER = line("suspicion_master", "a master scroll box likewise")

    fun suspicion(tier: TrailTier) = when (tier) {
        TrailTier.BEGINNER -> SUSPICION_BEGINNER
        TrailTier.EASY -> SUSPICION_EASY
        TrailTier.MEDIUM -> SUSPICION_MEDIUM
        TrailTier.HARD -> SUSPICION_HARD
        TrailTier.ELITE -> SUSPICION_ELITE
        TrailTier.MASTER -> SUSPICION_MASTER
    }
}

/**
 * Treasure Trails step TT1: scroll boxes, clue scrolls, reading them, a spade's Dig and the dig steps. Sources: the OSRS
 * Wiki's Treasure Trails, Scroll box (easy), Clue scroll (easy) and Casket (discontinued) pages, read 2026-11-19, and
 * the cache's clue helper tables ([TrailLoader]), which give every step's words and tiles.
 *
 * - Every quest counts as done, X Marks the Spot included, so every clue scroll a monster drops comes as its tier's
 *   scroll box. A player holds at most [BOX_CAP] boxes and scrolls of a tier together (the Wiki: the cap starts at 2;
 *   scroll cases raise it, step TT2); a box past that is not dropped, and the killer is told so.
 * - Open on a box takes one and gives a clue scroll of its tier on a new trail, unless a clue of the tier is already in
 *   the backpack or the bank. The trail's length is rolled in the tier's range ([TrailTier.minSteps]); each step is a
 *   built step of the tier ([built]) not yet done on this trail.
 * - Easy to elite steps are each their own item, so the item says which step it is; a scroll picked up that isn't the
 *   trail's step starts a new trail there, as dropping a clue loses its steps (the Wiki's Clue scroll (easy) page).
 *   Beginner and master steps share one item, and the step is kept on the player.
 * - Read shows the clue: its words on trail_cluetext, a coordinate as degrees and minutes, or the map's own interface.
 *   Check steps tells the player how many steps they have done.
 * - Dig (a spade's op1) plays human_dig and, within a tile of a dig step's spot (2004Scape's spade script, after
 *   videos of the live game), solves it; elsewhere nothing interesting happens. A solved step swaps the scroll for the
 *   next one, shown in an item box; the last gives the tier's reward casket instead (opened in step TT2).
 *
 * Steps are built region by region: [built] names the area and the kinds of step that work so far. Until other regions
 * exist, every trail stays inside Misthalin, and a trail is never longer than the steps built for its tier (Sam's
 * choice, 19 Nov 2026: marked temporary, so elite and master trails run as long as there are steps).
 *
 * Guessed: the trail length is uniform in its range and every built step equally likely (the Wiki gives neither); no
 * message on opening a box; one item box per solved step; a box that would pass the cap being kept from the drop (the
 * Wiki's warning setting on); the first item of a step with several. Left out until their steps: a second clue picked
 * up past the cap, the clue box's death rule.
 */
class TreasureTrails(private val world: World, val data: TrailData) {
    /** Steps of each tier that can come up now. */
    private val pools: Map<TrailTier, List<ClueStep>> = TrailTier.entries.associateWith { tier -> data.tier(tier).filter(::built) }

    fun pool(tier: TrailTier): List<ClueStep> = pools.getValue(tier)

    /**
     * The tier of clue scroll [obj] is, null if it isn't one: a step's own item, a shared scroll, or any other item
     * with a tier's param that is read (a step outside the built regions). The old caskets, keys and puzzle boxes carry
     * the param too but aren't read, so they aren't clue scrolls.
     */
    fun tierOf(obj: Int): TrailTier? {
        data.byItem[obj]?.let { return it.tier }
        TrailTier.entries.firstOrNull { it.sharedClue == obj }?.let { return it }
        val type = world.objTypes[obj] ?: return null
        if (type.iop(1) != READ) return null
        return TrailTier.entries.firstOrNull { type.intParam(it.param) == 1 }
    }

    /** The item a clue scroll on [step] is. */
    fun item(step: ClueStep): Int = step.items.firstOrNull() ?: step.tier.sharedClue

    private fun Inventory.hasClue(tier: TrailTier) = contents().any { (_, obj) -> obj.count > 0 && tierOf(obj.id) == tier }

    /** Whether [player] holds a clue scroll of [tier] in the backpack or the bank. */
    fun holdsClue(player: Player, tier: TrailTier): Boolean = player.inventory.hasClue(tier) || player.bank.hasClue(tier)

    /** Scroll boxes and clue scrolls of [tier] the player holds, against [BOX_CAP]. */
    fun held(player: Player, tier: TrailTier): Long =
        player.inventory.count(tier.scrollBox) + player.bank.count(tier.scrollBox) + if (holdsClue(player, tier)) 1 else 0

    /**
     * What a monster's drop of clue scroll [obj] becomes for [killer]: its tier's scroll box, or null when the killer
     * holds as many as they may (told so). With no killer the box drops anyway.
     */
    fun dropFor(killer: Player?, obj: ObjType): ObjType? {
        val tier = tierOf(obj.id) ?: return obj
        val box = world.objTypes[tier.scrollBox] ?: return obj
        if (killer != null && held(killer, tier) >= BOX_CAP) {
            world.message(killer, TrailLines.suspicion(tier))
            return null
        }
        return box
    }

    /** Starts a new trail of [tier] for [player] at [first] (a random built step when null), returning its clue item. */
    fun start(player: Player, tier: TrailTier, first: ClueStep? = null): Int? {
        val pool = pool(tier)
        val step = first ?: pool.randomOrNull() ?: return null
        // Temporary (Sam, 19 Nov 2026): no longer than the steps built, so a trail never repeats a step.
        val length = world.random.between(tier.minSteps, tier.maxSteps).coerceAtMost(maxOf(pool.size, 1))
        player.trails[tier] = Trail(step.key, length)
        return item(step)
    }

    private fun List<ClueStep>.randomOrNull(): ClueStep? = if (isEmpty()) null else this[world.random.nextInt(size)]

    /**
     * The step the clue scroll [obj] (of [tier]) is on. An own-step item that isn't the trail's step starts a new trail
     * there.
     */
    fun stepOf(player: Player, tier: TrailTier, obj: Int): ClueStep? {
        val own = data.byItem[obj]
        val trail = player.trails[tier]
        if (own != null) {
            if (trail == null || trail.step != own.key) start(player, tier, own)
            return own
        }
        if (obj != tier.sharedClue) return null
        return trail?.let { data[it.step] } ?: run {
            // A shared scroll with no trail behind it (from before trails, or `::item`): it starts one.
            start(player, tier)
            player.trails[tier]?.let { data[it.step] }
        }
    }

    fun openBox(context: ScriptContext, slot: Int, tier: TrailTier) {
        val player = context.player
        if (player.inventory[slot]?.id != tier.scrollBox) return
        if (holdsClue(player, tier)) return world.message(player, TrailLines.BOX_REFUSES)
        val stack = player.inventory[slot]!!.count
        if (stack > 1 && player.inventory.freeSlots == 0) return world.message(player, TrailLines.BOX_NO_SPACE)
        val clue = start(player, tier) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val type = world.objTypes[clue] ?: return
        player.inventory.remove(slot, 1)
        if (player.inventory[slot] == null) player.inventory[slot] = InvObj(clue, 1) else player.inventory.add(type, 1)
    }

    fun read(context: ScriptContext, slot: Int) {
        val player = context.player
        val obj = player.inventory[slot]?.id ?: return
        val tier = tierOf(obj) ?: return
        val step = stepOf(player, tier, obj) ?: return
        val map = mapInterface(step)
        if (map != null) {
            world.interfaces.openModal(player, FrameSlot.MAINMODAL, map)
            return
        }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, CLUE_TEXT)
        world.interfaces.setText(player, CLUE_TEXT, CLUE_TEXT_WORDS, words(player, step))
    }

    /** The scroll's words: the cache's clue text, or a coordinate written out. */
    fun words(player: Player, step: ClueStep): String {
        val text = step.text.orEmpty()
        if (step.type != StepType.COORDINATE) return text
        val match = COORDINATE.matchEntire(text.trim()) ?: return text
        val (latDeg, latMin, ns, lonDeg, lonMin, ew) = match.destructured
        val words = world.textFor(player)
        return words.get(
            TrailLines.COORDINATE,
            "lat_deg" to latDeg, "lat_min" to latMin, "lat_dir" to words[if (ns == "N") TrailLines.NORTH else TrailLines.SOUTH],
            "lon_deg" to lonDeg, "lon_min" to lonMin, "lon_dir" to words[if (ew == "E") TrailLines.EAST else TrailLines.WEST],
        )
    }

    fun checkSteps(context: ScriptContext, slot: Int) {
        val player = context.player
        val obj = player.inventory[slot]?.id ?: return
        val tier = tierOf(obj) ?: return
        stepOf(player, tier, obj) ?: return
        world.message(player, TrailLines.CHECK_STEPS, "count" to (player.trails[tier]?.done?.size ?: 0))
    }

    /** A spade's Dig: solves every carried clue whose dig spot is within a tile. */
    suspend fun dig(context: ScriptContext) {
        val player = context.player
        player.anim(ANIM_DIG)
        for ((slot, obj) in player.inventory.contents()) {
            val tier = tierOf(obj.id) ?: continue
            val step = stepOf(player, tier, obj.id) ?: continue
            if (!isDig(step) || step !in pool(tier)) continue
            val spot = step.targets.first().tile
            if (spot.level != player.level || maxOf(kotlin.math.abs(spot.x - player.x), kotlin.math.abs(spot.z - player.z)) > DIG_RANGE) continue
            solve(context, slot, tier, step)
            return
        }
        world.message(player, GameLines.NOTHING_INTERESTING)
    }

    /** [step] of the clue in [slot] is done: the next clue, or the reward casket after the last. */
    suspend fun solve(context: ScriptContext, slot: Int, tier: TrailTier, step: ClueStep) {
        val player = context.player
        val trail = player.trails[tier] ?: return
        if (trail.step != step.key) return
        trail.done += step.key
        val next = pool(tier).filter { it.key !in trail.done }
        if (trail.done.size >= trail.length || next.isEmpty()) {
            player.trails[tier] = null
            player.inventory.remove(slot)
            val casket = world.objTypes[tier.rewardCasket] ?: return
            if (player.inventory.count(casket.id) == 0L) player.inventory[slot] = InvObj(casket.id, 1) else player.inventory.add(casket, 1)
            world.dialogues.start(context) { objbox(casket.id, TrailLines.CASKET) }
            return
        }
        val following = next[world.random.nextInt(next.size)]
        trail.step = following.key
        player.inventory[slot] = InvObj(item(following), 1)
        world.dialogues.start(context) { objbox(item(following), TrailLines.ANOTHER_CLUE) }
    }

    /** The map interface a map step shows, from the step's item (or the beginner step's id). */
    fun mapInterface(step: ClueStep): Int? = if (step.type != StepType.MAP) null else
        if (step.tier == TrailTier.BEGINNER) BEGINNER_MAPS[step.id] else step.items.firstNotNullOfOrNull { MAPS[it] }

    companion object {
        /** The scroll boxes and clue scrolls of a tier a player may hold together, before scroll cases. */
        const val BOX_CAP = 2

        /** `trail_cluetext` and its words component (`text`). */
        const val CLUE_TEXT = 203
        const val CLUE_TEXT_WORDS = 2

        /** `human_dig`. */
        const val ANIM_DIG = 830
        const val SPADE = 952
        const val DIG_RANGE = 1

        /** Every clue scroll's op1. */
        private const val READ = "Read"

        val DIG_TYPES = setOf(StepType.MAP, StepType.COORDINATE, StepType.CRYPTIC)

        /** A coordinate step's text in the cache: `00.20S 23.15E`. */
        private val COORDINATE = Regex("""(\d+)\.(\d+)([NS]) (\d+)\.(\d+)([EW])""")

        /**
         * Map clue items and the interface each shows. 2713, 2716, 2827: 2004Scape's trail scripts (trail_map01, 02,
         * 03). 3518 (Wizards' Tower): inferred, trail_map11 being the beginner map RuneLite places there.
         */
        val MAPS: Map<Int, Int> = mapOf(2713 to 346, 2716 to 347, 2827 to 348, 3518 to 356)

        /**
         * Beginner map steps (the clue helper's id) and their interfaces, from RuneLite's BeginnerMapClue: Champions'
         * Guild trail_map01, Varrock East Mine 02, Draynor bank 03, Falador stones 06, Wizards' Tower 11.
         */
        val BEGINNER_MAPS: Map<Int, Int> = mapOf(101 to 346, 102 to 347, 103 to 348, 104 to 351, 105 to 356)

        /**
         * The regions built so far: Misthalin as Sam outlined it (x 3070-3400, z 3140-3523, without the desert corner
         * east of x 3274 and south of z 3328), its upper floors and the dungeons beneath it (z + 6400).
         */
        /**
         * Whether [step] can come up yet: inside the regions built so far and of a kind built so far. TT1 builds the
         * dig steps (a map, a coordinate without a fight, a cryptic dig) in Misthalin.
         */
        fun built(step: ClueStep): Boolean {
            val target = step.targets.firstOrNull() ?: return false
            if (!inBuiltRegion(target.tile)) return false
            return isDig(step) && !step.combat
        }

        fun isDig(step: ClueStep): Boolean =
            step.type in DIG_TYPES && step.targets.size == 1 && step.targets.first() is TrailTarget.Dig

        fun inBuiltRegion(tile: TrailTile): Boolean {
            val z = if (tile.z >= 6400) tile.z - 6400 else tile.z
            if (tile.x !in 3070..3400 || z !in 3140..3523) return false
            return !(tile.x > 3274 && z < 3328)
        }
    }
}

/** Scroll boxes, clue scrolls and the spade. */
fun registerTreasureTrails(world: World) {
    val trails = world.trails
    for (tier in TrailTier.entries) {
        world.handlers.onObj(tier.scrollBox, 1) { op -> trails.openBox(this, op.sub, tier) }
        if (tier.sharedClue >= 0) {
            world.handlers.onObj(tier.sharedClue, 1) { op -> trails.read(this, op.sub) }
            world.handlers.onObj(tier.sharedClue, 2) { op -> trails.checkSteps(this, op.sub) }
        }
    }
    for (item in trails.data.byItem.keys) {
        world.handlers.onObj(item, 1) { op -> trails.read(this, op.sub) }
        world.handlers.onObj(item, 2) { op -> trails.checkSteps(this, op.sub) }
    }
    world.handlers.onObj(TreasureTrails.SPADE, 1) { trails.dig(this) }
}
