package osrsps.game.trail

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

object StashLines : Lines("stash", "STASH units") {
    val LEVEL = line("level", "Build on an inconspicuous bush, crate, hole or rocks below the tier's Construction level (no source seen)", "level")
    val TOOLS = line("tools", "Build without a hammer and a saw in the backpack (no source seen)")
    val MATERIALS = line("materials", "Build without the planks, ten nails of one metal or (master) the gold leaf; planks is the tier's plank name, plural (no source seen)", "planks")
    val BUILT = line("built", "after building a STASH unit (no source seen)")
    val STORED = line("stored", "Search on an empty STASH unit, with every item of its clue in the backpack: they go in (no source seen)")
    val MISSING = line("missing", "Search on an empty STASH unit without every item of its clue in the backpack (no source seen)")
    val TAKEN = line("taken", "Search on a filled STASH unit: the items come out (no source seen)")
    val NO_SPACE = line("no_space", "Search on a filled STASH unit without room for all its items (no source seen)")
}

/**
 * STASH units (step TT5): the Wiki's STASH page, read 2026-11-19, and the cache. Each emote step (and a few cryptic
 * ones) names its unit in the clue helper's hidey_hole_loc column. The unit is a multiloc (hh_beginner001 and so on)
 * on a one-bit varbit of the player's (hh_constructed_*): 0 shows the inconspicuous bush, crate, hole or rocks with
 * Build, 1 the built STASH with Search. Varps are saved, so the built units are too.
 *
 * - Build takes the tier's Construction level (boostable, and a crystal saw in the backpack adds three), a hammer and a saw, two of the tier's planks, ten nails of
 *   one metal (any but dragon) and for master a gold leaf, and gives the tier's experience: the Wiki's table, beginner
 *   12 and 150 with planks, easy 27 and 150, medium 42 and 250 with oak, hard 55 and 400 with teak, elite 77 and 600
 *   with mahogany, master 88 and 1,500 with mahogany and a gold leaf. Three ticks (the Wiki's recipe).
 * - Search on an empty unit stashes the full set of its clue's items from the backpack, never part of it (one item for
 *   each slot or item group of one of the clue's outfits); on a filled unit it gives them all back if they fit.
 *
 * Guessed: human_poh_build as the animation; the hammer and saw counted only in the backpack (an Imcando hammer, a
 *   crystal saw and Amy's saw count); the cheapest nails used first; one of a stackable item stashed. Left out: the
 *   "STASH units take equipped items" setting (varbit option_hidey_holes_equipped), the S.T.A.S.H chart and Watson's
 *   noticeboard, and units outside the built regions.
 */
class StashUnits(private val world: World, data: TrailData) {
    /** A unit: its loc, its tier and the outfits it stores, from every step that names it. */
    class Stash(val loc: Int, val tier: TrailTier, val outfits: List<TrailOutfit>)

    /** The units of the steps inside the built regions, by loc id. */
    val units: Map<Int, Stash> = units(data)

    /** Whether [player] has built unit [loc]: its multiloc varbit set. */
    fun built(player: Player, loc: Int): Boolean {
        val varbit = world.locs.types[loc]?.multiVarbit ?: return false
        return varbit >= 0 && player.vars.types.varbit(varbit) != null && player.vars.getVarbit(varbit) == 1
    }

    suspend fun build(context: ScriptContext, stash: Stash) {
        val player = context.player
        if (built(player, stash.loc)) return search(context, stash)
        val cost = COSTS.getValue(stash.tier)
        if (constructionLevel(player) < cost.level) return world.message(player, StashLines.LEVEL, "level" to cost.level)
        if (HAMMERS.none { player.inventory.count(it) > 0 } || SAWS.none { player.inventory.count(it) > 0 }) return world.message(player, StashLines.TOOLS)
        val nails = NAILS.firstOrNull { player.inventory.count(it) >= NAILS_EACH }
        if (player.inventory.count(cost.plank) < PLANKS_EACH || nails == null || (cost.goldLeaf && player.inventory.count(GOLD_LEAF) < 1)) {
            val planks = (world.objTypes[cost.plank]?.name ?: "planks").lowercase().let { if (it.endsWith("s")) it else "${it}s" }
            return world.message(player, StashLines.MATERIALS, "planks" to planks)
        }
        player.anim(ANIM_BUILD)
        context.delay(BUILD_TICKS)
        // Checked again after the wait, as the backpack may have changed.
        if (HAMMERS.none { player.inventory.count(it) > 0 } || SAWS.none { player.inventory.count(it) > 0 }) return
        if (built(player, stash.loc) || constructionLevel(player) < cost.level) return
        if (player.inventory.count(cost.plank) < PLANKS_EACH || player.inventory.count(nails) < NAILS_EACH || (cost.goldLeaf && player.inventory.count(GOLD_LEAF) < 1)) return
        removeId(player, cost.plank, PLANKS_EACH)
        removeId(player, nails, NAILS_EACH)
        if (cost.goldLeaf) removeId(player, GOLD_LEAF, 1)
        player.skills.addTenths(Skill.CONSTRUCTION, cost.xp * 10)
        val varbit = world.locs.types[stash.loc]?.multiVarbit ?: return
        player.vars.setVarbit(varbit, 1)
        world.message(player, StashLines.BUILT)
    }

    fun search(context: ScriptContext, stash: Stash) {
        val player = context.player
        if (!built(player, stash.loc)) return
        val stored = player.trails.stashes[stash.loc]
        if (stored != null) {
            if (player.inventory.freeSlots < stored.size) return world.message(player, StashLines.NO_SPACE)
            for (id in stored) world.objTypes[id]?.let { player.inventory.add(it, 1) }
            player.trails.stashes.remove(stash.loc)
            return world.message(player, StashLines.TAKEN)
        }
        val slots = pick(player, stash) ?: return world.message(player, StashLines.MISSING)
        val items = slots.map { player.inventory[it]!!.id }
        for (slot in slots) player.inventory.remove(slot, 1)
        player.trails.stashes[stash.loc] = items
        world.message(player, StashLines.STORED)
    }

    /** The backpack slots holding a full set of one of [stash]'s outfits, null if none is all there. */
    fun pick(player: Player, stash: Stash): List<Int>? {
        val held = player.inventory.contents()
        val groupOf = { id: Int -> world.objTypes[id]?.intParam(TrailOutfit.PARAM_ITEM_GROUP) }
        for (outfit in stash.outfits) {
            val used = LinkedHashSet<Int>()
            fun take(match: (Int) -> Boolean): Boolean {
                val slot = held.firstOrNull { (slot, obj) -> slot !in used && match(obj.id) }?.first ?: return false
                used += slot
                return true
            }
            val whole = outfit.items.values.all { ids -> take { it in ids } } &&
                outfit.groups.values.all { group -> take { groupOf(it) == group } } &&
                outfit.anyGroups.all { group -> take { groupOf(it) == group } }
            if (whole && used.isNotEmpty()) return used.toList()
        }
        return null
    }

    /**
     * The Construction level a build is checked against: the current (boosted) level, plus the crystal saw's three
     * invisible levels while one is in the backpack (the Wiki's Crystal saw page, read 2026-11-22: STASH units count,
     * and it stacks with visible boosts). Left out: the saw's 28 charges (56 with the medium Western Provinces diary),
     * one used when the boost is needed, as no charge store exists for it yet, so the boost never runs out here.
     */
    private fun constructionLevel(player: Player): Int =
        player.skills.currentLevel(Skill.CONSTRUCTION) + if (player.inventory.count(CRYSTAL_SAW) > 0) CRYSTAL_SAW_BOOST else 0

    private fun removeId(player: Player, id: Int, count: Int) {
        var left = count
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id == id) left -= player.inventory.remove(slot, left)
        }
    }

    /** What building a unit of a tier takes. */
    class Cost(val level: Int, val xp: Int, val plank: Int, val goldLeaf: Boolean = false)

    companion object {
        /** The units of [data]'s steps inside the built regions, by loc id. */
        fun units(data: TrailData): Map<Int, Stash> = data.steps.values
            .filter { it.stash >= 0 && it.outfits.isNotEmpty() && it.targets.firstOrNull()?.let { t -> TreasureTrails.inBuiltRegion(t.tile) } == true }
            .groupBy { it.stash }
            .mapValues { (loc, steps) -> Stash(loc, steps.first().tier, steps.flatMap { it.outfits }.distinct()) }

        const val ANIM_BUILD = 3676
        const val BUILD_TICKS = 3
        const val PLANKS_EACH = 2
        const val NAILS_EACH = 10
        const val GOLD_LEAF = 8784
        const val PLANK = 960
        const val OAK_PLANK = 8778
        const val TEAK_PLANK = 8780
        const val MAHOGANY_PLANK = 8782

        val HAMMERS = listOf(2347, 25644)
        val SAWS = listOf(8794, 9625, 24880)
        const val CRYSTAL_SAW = 9625
        const val CRYSTAL_SAW_BOOST = 3

        /** Bronze, iron, steel, black, mithril, adamantite and rune nails, cheapest first. */
        val NAILS = listOf(4819, 4820, 1539, 4821, 4822, 4823, 4824)

        val COSTS: Map<TrailTier, Cost> = mapOf(
            TrailTier.BEGINNER to Cost(12, 150, PLANK),
            TrailTier.EASY to Cost(27, 150, PLANK),
            TrailTier.MEDIUM to Cost(42, 250, OAK_PLANK),
            TrailTier.HARD to Cost(55, 400, TEAK_PLANK),
            TrailTier.ELITE to Cost(77, 600, MAHOGANY_PLANK),
            TrailTier.MASTER to Cost(88, 1500, MAHOGANY_PLANK, goldLeaf = true),
        )

        /** A unit's options: Build on the inconspicuous form, Search on the built one (the hh_ locs' op1 and op2). */
        const val BUILD = 1
        const val SEARCH = 2
    }
}
