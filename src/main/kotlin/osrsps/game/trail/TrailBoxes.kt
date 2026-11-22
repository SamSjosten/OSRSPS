package osrsps.game.trail

import osrsps.game.FrameSlot
import osrsps.game.GameLines
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.sendInventory

/** A light box's state: each switch's lights (25 bits, row by row) and the lights lit now. */
class LightBoxState(val switches: IntArray, var lit: Int) {
    val solved: Boolean get() = lit == TrailBoxes.ALL_LIT

    fun copy() = LightBoxState(switches.copyOf(), lit)
}

/**
 * Puzzle boxes and light boxes (step TT6), which a step's NPC hands over before its clue when the clue helper's
 * challenge column names a box (cluehelper_challenge_box: "A puzzle box.", "A light box." or either). Sources: the
 * Wiki's Puzzle box and Light box pages, read 2026-11-19; RuneLite's puzzle solver for the interfaces (trail_slidepuzzle
 * 306 drawing inv trail_puzzleinv 140 in its pieces layer, light_puzzle 322 with switches BUTTON1 to BUTTON8) and the
 * order of a solved puzzle (piece ids ascending, the gap last).
 *
 * - A puzzle box opens trail_slidepuzzle. Clicking a piece next to the gap slides it in. The box is scrambled from the
 *   solved state by 101 random slides (Mod Ash, 2 September 2020, quoted on the Wiki). Talking to the NPC with the box
 *   solved takes it and gives the next clue; unsolved, the NPC says so.
 * - A light box opens light_puzzle: 25 lights and 8 switches, each switch flipping its own set of lights; all lit is
 *   solved (the Wiki's Light box page). The lights are varp trail_lights (1356).
 * - The scramble and the switches are kept on the player and saved, so a box is the same after logging out.
 *
 * Which puzzle a hard box is: the Wiki's item ids for the castle, tree and troll boxes (2749, 3619 and 3643 start their
 * Sliding piece pages); the elite box is the tree (the Wiki's "Tree (elite)"). Guessed: a hard box the Wiki doesn't
 * list is the castle; a master step that takes either box hands over one of the five at random; the light box's
 * switches are random sets with a random set of them solving it; bit n of varp 1356 is light n, row by row, set when
 * lit; no message on solving; a box with no state (from `::item`) is scrambled when first opened.
 */
class TrailBoxes(private val world: World) {
    /** The puzzle box each player last opened, which the slide puzzle's clicks move. Not saved: reopening sets it. */
    private val opened = java.util.WeakHashMap<Player, Int>()

    /** The box [step]'s NPC hands over for [box]: the step's own puzzle box, or for a shared clue one of its tier's. */
    fun boxFor(step: ClueStep, box: TrailChallenge.Box): Int? {
        if (box.light && !box.puzzle) return LIGHT_BOX
        ownBox(step)?.let { return it }
        if (step.tier == TrailTier.ELITE) return ELITE_BOX
        if (step.tier != TrailTier.MASTER) return null
        val choices = MASTER_BOXES + if (box.light) listOf(LIGHT_BOX) else emptyList()
        return choices[world.random.nextInt(choices.size)]
    }

    /** Every box [step]'s NPC takes back for [box]: the one it hands over, or any of the master ones it picks from. */
    fun accepts(step: ClueStep, box: TrailChallenge.Box): Set<Int> {
        if (box.light && !box.puzzle) return setOf(LIGHT_BOX)
        val puzzles = ownBox(step)?.let(::setOf) ?: when (step.tier) {
            TrailTier.ELITE -> setOf(ELITE_BOX)
            TrailTier.MASTER -> MASTER_BOXES.toSet()
            else -> emptySet()
        }
        return if (box.light) puzzles + LIGHT_BOX else puzzles
    }

    /** [step]'s own puzzle box: the clue's `_puzzlebox` obj, or the obj after it named as one. Not for elite shared clues. */
    private fun ownBox(step: ClueStep): Int? {
        if (step.tier == TrailTier.ELITE && step.items.isEmpty()) return ELITE_BOX
        return step.items.firstNotNullOfOrNull { clue ->
            val name = world.objTypes[clue]?.internalName
            name?.let { world.objTypes.find("${it}_puzzlebox")?.id } ?: (clue + 1).takeIf { world.objTypes[it]?.name?.startsWith(PUZZLE_BOX) == true }
        }
    }

    /** Whether [player] has solved box [item], the puzzle or the lights. */
    fun solved(player: Player, item: Int): Boolean =
        if (item == LIGHT_BOX) player.trails.lightBox?.solved == true else player.trails.puzzles[item]?.let(::isSolved) == true

    /** Hands [player] box [item], freshly scrambled; false with no room. */
    fun give(player: Player, item: Int): Boolean {
        val type = world.objTypes[item] ?: return false
        if (player.inventory.add(type, 1) == 0) {
            world.message(player, GameLines.NO_SPACE)
            return false
        }
        scramble(player, item)
        return true
    }

    /** Takes box [item] from [player] and forgets its state. */
    fun take(player: Player, item: Int) {
        val slot = player.inventory.contents().firstOrNull { it.second.id == item }?.first ?: return
        player.inventory.remove(slot, 1)
        forget(player, item)
    }

    fun forget(player: Player, item: Int) {
        if (item == LIGHT_BOX) player.trails.lightBox = null else player.trails.puzzles.remove(item)
    }

    private fun scramble(player: Player, item: Int) {
        if (item == LIGHT_BOX) {
            player.trails.lightBox = newLightBox()
            return
        }
        val first = PUZZLES[item] ?: return
        player.trails.puzzles[item] = newPuzzle(first)
    }

    /** A puzzle starting at piece [first], scrambled by [SCRAMBLE_SLIDES] random slides of the gap from solved. */
    fun newPuzzle(first: Int): IntArray {
        while (true) {
            val slots = IntArray(SLOTS) { if (it == GAP_START) GAP else first + it }
            var gap = GAP_START
            repeat(SCRAMBLE_SLIDES) {
                val next = neighbours(gap).let { it[world.random.nextInt(it.size)] }
                slots[gap] = slots[next]
                slots[next] = GAP
                gap = next
            }
            if (!isSolved(slots)) return slots
        }
    }

    /** Eight switches of random lights, and lights that a random set of them turns all on. */
    fun newLightBox(): LightBoxState {
        while (true) {
            val switches = IntArray(SWITCHES) { 1 + world.random.nextInt(ALL_LIT) }
            var lit = ALL_LIT
            for (switch in switches) if (world.random.nextInt(2) == 1) lit = lit xor switch
            if (lit != ALL_LIT) return LightBoxState(switches, lit)
        }
    }

    fun open(context: ScriptContext, slot: Int) {
        val player = context.player
        val item = player.inventory[slot]?.id ?: return
        if (item == LIGHT_BOX) {
            val state = player.trails.lightBox ?: newLightBox().also { player.trails.lightBox = it }
            player.vars[LIGHTS_VARP] = state.lit
            world.interfaces.openModal(player, FrameSlot.MAINMODAL, LIGHT_PUZZLE)
            return
        }
        val first = PUZZLES[item] ?: return
        val slots = player.trails.puzzles.getOrPut(item) { newPuzzle(first) }
        opened[player] = item
        sendPuzzle(player, slots)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, SLIDE_PUZZLE)
    }

    /** A click on the puzzle's piece in [slot]: it slides into the gap beside it, if there is one. */
    fun slide(context: ScriptContext, slot: Int) {
        val player = context.player
        if (slot !in 0 until SLOTS) return
        if (player.modals[FrameSlot.MAINMODAL] != SLIDE_PUZZLE) return
        // The box that was opened, still in the backpack: with two boxes held, clicks go to the one on screen.
        val item = opened[player]?.takeIf { player.inventory.count(it) > 0 } ?: return
        val slots = player.trails.puzzles[item] ?: return
        if (slots[slot] == GAP) return
        val gap = neighbours(slot).firstOrNull { slots[it] == GAP } ?: return
        slots[gap] = slots[slot]
        slots[slot] = GAP
        sendPuzzle(player, slots)
    }

    /** Switch [index] (0 to 7) of the light box flips its lights. */
    fun flip(context: ScriptContext, index: Int) {
        val player = context.player
        val state = player.trails.lightBox ?: return
        if (player.modals[FrameSlot.MAINMODAL] != LIGHT_PUZZLE || player.inventory.count(LIGHT_BOX) == 0L || index !in 0 until SWITCHES) return
        state.lit = state.lit xor state.switches[index]
        player.vars[LIGHTS_VARP] = state.lit
    }

    private fun sendPuzzle(player: Player, slots: IntArray) {
        val inv = Inventory(PUZZLE_INV, SLOTS)
        for ((slot, piece) in slots.withIndex()) if (piece != GAP) inv[slot] = InvObj(piece, 1)
        sendInventory(player, inv) { p, message -> p.session.queue(message) }
    }

    companion object {
        const val SLIDE_PUZZLE = 306
        const val SLIDE_PIECES = 4
        const val PUZZLE_INV = 140
        const val LIGHT_PUZZLE = 322
        /** light_puzzle's BUTTON1 to BUTTON8. */
        val LIGHT_SWITCHES = 8..15
        const val LIGHTS_VARP = 1356

        const val SLOTS = 25
        const val GAP = -1
        const val GAP_START = 24
        const val SCRAMBLE_SLIDES = 101
        const val SWITCHES = 8
        const val ALL_LIT = (1 shl 25) - 1

        const val PUZZLE_BOX = "Puzzle box"
        const val LIGHT_BOX = 20355
        const val ELITE_BOX = 12161

        const val CASTLE = 2749
        const val TREE = 3619
        const val TROLL = 3643

        /** The master puzzle boxes (Zulrah, Cerberus, gnome child, Theatre of Blood) and their first pieces. */
        val MASTER_BOXES = listOf(20280, 20281, 20282, 23417)

        /** Each puzzle box's first piece: the 24 pieces follow it in id order. */
        val PUZZLES: Map<Int, Int> = buildMap {
            for (box in listOf(2795, 2798, 2800, 12579, 12588, 19891, 19911, 23171, 24494, 28917)) put(box, CASTLE)
            for (box in listOf(3565, 3567, 3578, 12582, 19887, 23173, ELITE_BOX)) put(box, TREE)
            for (box in listOf(3569, 3571, 3576, 12585, 19895, 19897, 19903)) put(box, TROLL)
            put(20280, 20283)
            put(20281, 20307)
            put(20282, 20331)
            put(23417, 23418)
        }

        fun isSolved(slots: IntArray): Boolean {
            if (slots.size != SLOTS || slots[GAP_START] != GAP) return false
            val first = slots[0]
            return (0 until GAP_START).all { slots[it] == first + it }
        }

        /** The slots beside [slot] on the 5 by 5 grid. */
        fun neighbours(slot: Int): List<Int> = buildList {
            if (slot % 5 != 0) add(slot - 1)
            if (slot % 5 != 4) add(slot + 1)
            if (slot >= 5) add(slot - 5)
            if (slot < 20) add(slot + 5)
        }
    }
}
