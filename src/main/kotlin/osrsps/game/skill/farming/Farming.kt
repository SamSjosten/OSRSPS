package osrsps.game.skill.farming

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.skill.ClockStep
import osrsps.game.skill.Making
import osrsps.game.skill.SkillClock
import osrsps.game.skill.SuccessRoll

/** Farming's words, in the dialogue file. Not checked against live yet: the Words thread sources them. */
object FarmingLines : Lines("farming", "Farming") {
    val NEED_RAKE = line("need_rake", "Rake on a weedy patch without a rake")
    val RAKED = line("raked", "the last weeds raked from a patch")
    val NEEDS_WEEDING = line("needs_weeding", "seeds used on a patch that has weeds")
    val OCCUPIED = line("occupied", "seeds used on a patch that already has a crop")
    val WRONG_PATCH = line("wrong_patch", "seeds used on a patch they don't grow in", "patch")
    val LEVEL = line("level", "seeds that need a higher Farming level", "level")
    val NEED_DIBBER = line("need_dibber", "seeds used on a patch without a seed dibber")
    val NEED_SEEDS = line("need_seeds", "too few seeds for the patch", "count", "patch")
    val PLANTED = line("planted", "seeds planted", "count", "seeds", "patch")
    val WATERED = line("watered", "a patch watered", "crop")
    val NO_WATER_NEEDED = line("no_water_needed", "a watering can on a patch that can't be watered")
    val CAN_EMPTY = line("can_empty", "an empty watering can used on a patch")
    val NEED_SPADE = line("need_spade", "Harvest without a spade")
    val HARVEST_START = line("harvest_start", "Harvest clicked on a grown allotment", "patch")
    val HARVESTED = line("harvested", "each crop harvested", "produce")
    val EMPTY = line("empty", "the last crop harvested from a patch", "patch")
    val INVENTORY_FULL = line("inventory_full", "raking or harvesting with a full inventory")

    /** Inspect's message is made of these: what the patch is, then its soil, then what is in it. */
    val INSPECT = line("inspect", "Inspect: the first sentence", "patch")
    val SOIL_UNTREATED = line("soil_untreated", "Inspect: the soil sentence, no compost")
    val STATE_WEEDS = line("state_weeds", "Inspect: a patch with weeds")
    val STATE_EMPTY = line("state_empty", "Inspect: a raked patch with nothing planted")
    val STATE_GROWING = line("state_growing", "Inspect: a crop growing", "crop")
    val STATE_GROWN = line("state_grown", "Inspect: a crop ready to harvest", "crop")

    /** The patch kinds' names, for the lines above. */
    val PATCH_ALLOTMENT = line("patch_allotment", "an allotment's name in messages")
}

/**
 * The Farming skill, step 1 (OSRS Wiki, Farming, Allotment patch, Weeds and each seed's page, read 2026-11-14): the
 * growth clock ([FarmingClock]), each player's patches ([FarmingState], saved), the patch varbits the client draws
 * them from, and allotments: Rake, seeds used on a raked patch with a seed dibber, watering, growing on the clock, and
 * Harvest with a spade until the patch's lives run out.
 *
 * Every patch is a multiloc the map places, drawn by one of the `farming_transmit` varbits ([FarmingPatches]); each tick
 * the player's patches grow to the clock and the varbits of the area they stand in are set (only changes are sent).
 * The client's op1 is whatever the patch shows (Rake, Harvest), so op1 is answered by the patch's state; op2 is
 * Inspect. Op4, Guide, isn't built (the Farming skill guide).
 *
 * Sourced: allotment levels, seeds, XP, stages and 10-minute cycles; the planting XP given when the last crop is
 * harvested, not on planting (the Allotment patch page's footnote); 4 XP per weed and weeds growing back every 5
 * minutes; three harvest lives; the chance to keep a life (each seed's page) and magic secateurs' 10% on it (Variable
 * crop yield). The animations are the cache's farming_raking, farming_seed_dibbing, farming_watering and human_dig.
 * Guessed: a rake roll every 4 ticks that always takes one weed; planting and watering landing 3 ticks after the
 * click; a crop every 4 ticks while harvesting; raking and harvesting stopping with a full inventory; that the
 * offset is drawn from the account rather than at random ([offset]).
 */
class Farming(private val world: World, private val clock: () -> Long) {
    fun register() {
        for (loc in FarmingPatches.LOCS) {
            world.handlers.onLoc(loc, OP_ACTION) { op -> action(op) }
            world.handlers.onLoc(loc, OP_INSPECT) { op -> inspect(op) }
            world.handlers.onObjOnLoc(loc) { op -> useObj(op) }
        }
    }

    /**
     * Grows [player]'s patches to the clock and sets the varbits of the area they stand in. Every tick, so a step into
     * an area or a farming tick shows at once; PlayerVars only sends what changed.
     */
    fun process(player: Player) {
        grow(player)
        refresh(player)
    }

    /** Moves [player]'s farming clock on [ticks] farming ticks (`::farmtick`), growing their patches to it. */
    fun skip(player: Player, ticks: Int) {
        require(ticks >= 0) { "Can't skip $ticks ticks" }
        player.farming.skipped += ticks
        process(player)
    }

    /**
     * The account's offset in minutes, 0 to 31 (Mod Ash). Live's is fixed per account; how it is drawn isn't known, so
     * it comes from the account's name, which keeps [World.random]'s rolls for the game.
     */
    fun offset(player: Player): Int = Math.floorMod(player.saveKey.hashCode(), OFFSETS)

    /** The farming tick [player] is on now. */
    fun tickOf(player: Player): Long = FarmingClock.tick(clock(), offset(player), player.farming.skipped)

    private fun grow(player: Player) {
        val state = player.farming
        val now = tickOf(player)
        if (state.lastTick < 0 || now <= state.lastTick) {
            // A clock that went back (a server moved to another clock) waits for it rather than growing twice.
            if (state.lastTick < 0) state.lastTick = now
            return
        }
        val first = maxOf(state.lastTick + 1, now - FarmingClock.MAX_CATCH_UP + 1)
        state.lastTick = now
        if (state.patches.isEmpty()) return
        for (tick in first..now) growTick(state, tick)
    }

    /** One farming tick for every patch the player has touched. */
    private fun growTick(state: FarmingState, tick: Long) {
        val iterator = state.patches.entries.iterator()
        while (iterator.hasNext()) {
            val (key, patch) = iterator.next()
            val kind = FarmingPatches.byKey(key)?.kind ?: continue
            val crop = patch.crop
            if (crop == null) {
                if (patch.weeds < kind.weeds) patch.weeds++
                // An overgrown patch is what an untouched one is: no need to keep it.
                if (patch.weeds >= kind.weeds) iterator.remove()
                continue
            }
            if (patch.stage >= crop.stages || tick % kind.cycleTicks != 0L) continue
            patch.stage++
            patch.watered = false
            if (patch.stage >= crop.stages) patch.lives = BASE_LIVES
        }
    }

    /** Sets the varbits of the patches around [player]. */
    fun refresh(player: Player) {
        val area = FarmingPatches.areaAt(player.x, player.z) ?: return
        for (patch in area.patches) player.vars.setVarbitIfKnown(patch.varbit, player.farming.value(patch))
    }

    private fun patchOf(op: Op): FarmingPatch? {
        val loc = op.loc ?: return null
        return FarmingPatches.at(loc.id, loc.x, loc.z)
    }

    private suspend fun ScriptContext.action(op: Op) {
        val patch = patchOf(op) ?: return
        val state = player.farming.patch(patch)
        when {
            state.crop == null && state.weeds > 0 -> rake(op, patch, state)
            state.grown -> harvest(op, patch, state)
            // The client shows op1 only on weeds and grown crops, so anything else is a click from before a change.
            else -> stop()
        }
    }

    private fun ScriptContext.rake(op: Op, patch: FarmingPatch, state: PatchState) {
        if (op.repeat && player.modalOpen) return stop()
        if (state.crop != null || state.weeds <= 0) return stop()
        if (player.inventory.count(RAKE) == 0L) {
            world.message(player, FarmingLines.NEED_RAKE)
            return stop()
        }
        if (player.inventory.freeSlots == 0) {
            world.message(player, FarmingLines.INVENTORY_FULL)
            return stop()
        }
        if (!op.repeat) player.anim(ANIM_RAKE)
        if (SkillClock.step(player, tick, RAKE_CYCLE) == ClockStep.DUE) {
            player.anim(ANIM_RAKE)
            state.weeds--
            world.objTypes[WEEDS]?.let { player.inventory.add(it, 1) }
            player.skills.addTenths(Skill.FARMING, WEED_XP_TENTHS)
            refresh(player)
            if (state.weeds <= 0) {
                world.message(player, FarmingLines.RAKED)
                return stop()
            }
        }
        world.repeatOp(player, op)
    }

    private fun ScriptContext.harvest(op: Op, patch: FarmingPatch, state: PatchState) {
        if (op.repeat && player.modalOpen) return stop()
        val crop = state.crop
        if (crop == null || !state.grown) return stop()
        if (player.inventory.count(SPADE) == 0L) {
            world.message(player, FarmingLines.NEED_SPADE)
            return stop()
        }
        if (player.inventory.freeSlots == 0) {
            world.message(player, FarmingLines.INVENTORY_FULL)
            return stop()
        }
        val text = world.textFor(player)
        if (!op.repeat) {
            world.message(player, FarmingLines.HARVEST_START, "patch" to text[patchName(patch.kind)])
            player.anim(ANIM_DIG)
        }
        if (SkillClock.step(player, tick, HARVEST_CYCLE) == ClockStep.DUE) {
            player.anim(ANIM_DIG)
            val produce = world.objTypes[crop.produce] ?: error("No obj type ${crop.produce} for ${crop.key}")
            player.inventory.add(produce, 1)
            player.skills.addTenths(Skill.FARMING, crop.harvestXpTenths)
            world.message(player, FarmingLines.HARVESTED, "produce" to produce.name.lowercase())
            if (!keepsLife(crop, player)) state.lives--
            if (state.lives <= 0) {
                // The planting XP comes now, with the last crop (the Wiki's Allotment patch: on fully harvesting it).
                player.skills.addTenths(Skill.FARMING, crop.plantXpTenths)
                state.clear()
                refresh(player)
                world.message(player, FarmingLines.EMPTY, "patch" to text[patchName(patch.kind)])
                return stop()
            }
            refresh(player)
        }
        world.repeatOp(player, op)
    }

    /** Whether a harvest keeps its life: the crop's chance at the player's level, magic secateurs' 10% on both ends. */
    fun keepsLife(crop: Crop, player: Player): Boolean {
        val secateurs = player.inventory.count(MAGIC_SECATEURS) > 0 || player.equipment.count(MAGIC_SECATEURS) > 0
        val (low, high) = lifeChances(crop, secateurs)
        return SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.FARMING))
    }

    private suspend fun ScriptContext.useObj(op: Op) {
        val patch = patchOf(op) ?: return
        val state = player.farming.patch(patch)
        val obj = op.obj
        when {
            obj == RAKE -> if (state.crop == null && state.weeds > 0) rake(op.asAction(), patch, state) else nothing()
            obj == SPADE -> if (state.grown) harvest(op.asAction(), patch, state) else nothing()
            obj in WATERING_CANS -> water(op, state)
            obj == WATERING_CAN_EMPTY -> world.message(player, FarmingLines.CAN_EMPTY)
            Crops.bySeed(obj) != null -> plant(op, patch, state, Crops.bySeed(obj)!!)
            else -> nothing()
        }
    }

    /** An item used on a patch, run as the option it stands for: the same action as clicking it. */
    private fun Op.asAction() = Op(OpTarget.LOC, id, OP_ACTION, loc = loc)

    private fun ScriptContext.nothing() = world.message(player, GameLines.NOTHING_INTERESTING)

    private suspend fun ScriptContext.plant(op: Op, patch: FarmingPatch, state: PatchState, crop: Crop) {
        val text = world.textFor(player)
        val name = text[patchName(patch.kind)]
        if (crop.kind != patch.kind) return world.message(player, FarmingLines.WRONG_PATCH, "patch" to name)
        if (state.crop != null) return world.message(player, FarmingLines.OCCUPIED)
        if (state.weeds > 0) return world.message(player, FarmingLines.NEEDS_WEEDING)
        if (player.skills.currentLevel(Skill.FARMING) < crop.level) return world.message(player, FarmingLines.LEVEL, "level" to crop.level)
        if (player.inventory.count(SEED_DIBBER) == 0L) return world.message(player, FarmingLines.NEED_DIBBER)
        val seeds = patch.kind.seeds
        if (player.inventory.count(crop.seed) < seeds) {
            return world.message(player, FarmingLines.NEED_SEEDS, "count" to seeds, "patch" to name)
        }
        player.anim(ANIM_DIBBER)
        delay(PLANT_DELAY)
        // Checked again: the seeds or the patch may have gone in the meantime.
        if (state.crop != null || state.weeds > 0 || player.inventory.count(crop.seed) < seeds) return
        Making.removeObj(player.inventory, crop.seed, seeds)
        state.crop = crop
        state.stage = 0
        state.watered = false
        state.lives = 0
        val seedName = world.objTypes[crop.seed]?.name?.lowercase() ?: crop.key
        world.message(player, FarmingLines.PLANTED, "count" to seeds, "seeds" to seedName, "patch" to name)
        refresh(player)
    }

    private suspend fun ScriptContext.water(op: Op, state: PatchState) {
        val crop = state.crop
        if (crop == null || !state.growing || state.watered || crop.watered.isEmpty()) {
            return world.message(player, FarmingLines.NO_WATER_NEEDED)
        }
        player.anim(ANIM_WATER)
        delay(WATER_DELAY)
        val slot = op.sub
        val can = player.inventory[slot]
        if (can == null || can.id !in WATERING_CANS || state.crop !== crop || !state.growing || state.watered) return
        player.inventory[slot] = can.copy(id = wateringCan(WATERING_CANS.indexOf(can.id)))
        state.watered = true
        world.message(player, FarmingLines.WATERED, "crop" to world.objTypes[crop.produce]?.name?.lowercase().orEmpty())
        refresh(player)
    }

    private fun ScriptContext.inspect(op: Op) {
        val patch = patchOf(op) ?: return
        val state = player.farming.patch(patch)
        val text = world.textFor(player)
        val crop = state.crop
        val cropName = crop?.let { world.objTypes[it.produce]?.name?.lowercase() ?: it.key }.orEmpty()
        val what = when {
            crop == null && state.weeds > 0 -> text[FarmingLines.STATE_WEEDS]
            crop == null -> text[FarmingLines.STATE_EMPTY]
            state.grown -> text[FarmingLines.STATE_GROWN, "crop" to cropName]
            else -> text[FarmingLines.STATE_GROWING, "crop" to cropName]
        }
        val message = listOf(text[FarmingLines.INSPECT, "patch" to text[patchName(patch.kind)]], text[FarmingLines.SOIL_UNTREATED], what)
        player.message(message.joinToString(" "))
    }

    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    companion object {
        /** Op1, whatever the patch shows (Rake, Harvest), and op2, Inspect, on every patch's shown locs. */
        const val OP_ACTION = 1
        const val OP_INSPECT = 2

        const val OFFSETS = 32

        /** Harvest lives a crop starts with (the Wiki's Farming: three, before compost). */
        const val BASE_LIVES = 3

        const val RAKE = 5341
        const val SEED_DIBBER = 5343
        const val SPADE = 952
        const val WEEDS = 6055
        const val MAGIC_SECATEURS = 7409

        /** watering_can_0 (empty) and watering_can_1 to _8, by doses left: 5333 to 5340. */
        const val WATERING_CAN_EMPTY = 5331
        val WATERING_CANS = (1..8).map { 5332 + it }

        /** The can with [doses] left. */
        fun wateringCan(doses: Int): Int = if (doses <= 0) WATERING_CAN_EMPTY else 5332 + doses

        const val WEED_XP_TENTHS = 40

        /** farming_raking, farming_seed_dibbing, farming_watering and human_dig. */
        const val ANIM_RAKE = 2273
        const val ANIM_DIBBER = 2291
        const val ANIM_WATER = 2293
        const val ANIM_DIG = 830

        const val RAKE_CYCLE = 4
        const val HARVEST_CYCLE = 4
        const val PLANT_DELAY = 3
        const val WATER_DELAY = 3

        fun patchName(kind: PatchKind) = when (kind) {
            PatchKind.ALLOTMENT -> FarmingLines.PATCH_ALLOTMENT
        }

        /** A crop's chance pair, low and high, with magic secateurs' 10% when [secateurs] (rounded down, the Wiki's order). */
        fun lifeChances(crop: Crop, secateurs: Boolean): Pair<Int, Int> =
            if (secateurs) crop.ctsLow * 11 / 10 to crop.ctsHigh * 11 / 10 else crop.ctsLow to crop.ctsHigh
    }
}
