package osrsps.game.skill.farming

import osrsps.game.GameLines
import osrsps.game.GroundObjs
import osrsps.game.InvObj
import osrsps.game.Inventory
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
import osrsps.game.skill.ToolCheck
import osrsps.game.skill.Woodcutting
import osrsps.game.skill.WoodcuttingLines

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
    val HARVEST_START = line("harvest_start", "Harvest or Pick clicked on a grown allotment or herb patch", "patch")
    val HARVESTED = line("harvested", "each crop harvested", "produce")
    val EMPTY = line("empty", "the last crop harvested from a patch", "patch")
    val PICKED = line("picked", "a grown flower picked", "produce")
    val INVENTORY_FULL = line("inventory_full", "raking or harvesting with a full inventory")
    val NEED_CURE = line("need_cure", "Cure on a diseased patch without a plant cure")
    val NOT_DISEASED = line("not_diseased", "a plant cure used on a patch that isn't diseased")
    val CURED = line("cured", "a diseased patch cured", "crop")
    val CLEARED = line("cleared", "a dead patch cleared with a spade")
    val COMPOSTED = line("composted", "compost put on a patch", "compost")
    val ALREADY_COMPOSTED = line("already_composted", "compost used on a patch that already has some")
    val COMPOST_NOT_NOW = line("compost_not_now", "compost used on a grown, diseased or dead crop")
    val NEED_TROWEL = line("need_trowel", "a tree seed used on a filled plant pot without a gardening trowel")
    val POT_NEEDS_EMPTY_PATCH = line("pot_needs_empty_patch", "an empty plant pot used on a patch with weeds or a crop")
    val POT_FILLED = line("pot_filled", "a plant pot filled with soil from a patch")
    val SEEDLING_PLANTED = line("seedling_planted", "a tree seed planted in a filled plant pot", "seed")
    val SEEDLING_WATERED = line("seedling_watered", "a seedling watered")
    val SAPLING_PLANTED = line("sapling_planted", "a sapling planted in a tree patch", "sapling", "patch")
    val CHECKED = line("checked", "Check-health on a grown tree")
    val NEED_SECATEURS = line("need_secateurs", "Prune on a diseased tree without secateurs")
    val PRUNED = line("pruned", "a diseased tree pruned well again")
    val PRUNE_LEFT = line("prune_left", "a prune that leaves some diseased leaves")
    val CHOP_FIRST = line("chop_first", "a spade on a standing tree, or the stump grown back while digging it up")
    val STUMP_CLEARED = line("stump_cleared", "a tree stump dug up")

    /** Inspect's message is made of these: what the patch is, then its soil, then what is in it. */
    val INSPECT = line("inspect", "Inspect: the first sentence", "patch")
    val SOIL_UNTREATED = line("soil_untreated", "Inspect: the soil sentence, no compost")
    val SOIL_COMPOST = line("soil_compost", "Inspect: the soil sentence, compost")
    val SOIL_SUPERCOMPOST = line("soil_supercompost", "Inspect: the soil sentence, supercompost")
    val SOIL_ULTRACOMPOST = line("soil_ultracompost", "Inspect: the soil sentence, ultracompost")
    val STATE_WEEDS = line("state_weeds", "Inspect: a patch with weeds")
    val STATE_EMPTY = line("state_empty", "Inspect: a raked patch with nothing planted")
    val STATE_GROWING = line("state_growing", "Inspect: a crop growing", "crop")
    val STATE_GROWN = line("state_grown", "Inspect: a crop ready to harvest", "crop")
    val STATE_DISEASED = line("state_diseased", "Inspect: a diseased crop", "crop")
    val STATE_DEAD = line("state_dead", "Inspect: a dead crop", "crop")
    val STATE_CHECK = line("state_check", "Inspect: a grown tree whose health hasn't been checked", "crop")
    val STATE_STUMP = line("state_stump", "Inspect: a tree chopped down to its stump", "crop")

    /** The patch kinds' names, for the lines above. */
    val PATCH_ALLOTMENT = line("patch_allotment", "an allotment's name in messages")
    val PATCH_HERB = line("patch_herb", "a herb patch's name in messages")
    val PATCH_FLOWER = line("patch_flower", "a flower patch's name in messages")
    val PATCH_TREE = line("patch_tree", "a tree patch's name in messages")
}

/**
 * The Farming skill, steps 1 and 2 (OSRS Wiki, Farming, Allotment patch, Herb patch, Flower patch, Weeds and each
 * seed's page, read 2026-11-14): the growth clock ([FarmingClock]), each player's patches ([FarmingState], saved), the
 * patch varbits the client draws them from, and allotments, herbs and flowers: Rake, seeds used on a raked patch with a
 * seed dibber, watering (not herbs), growing on the clock, Harvest (allotments) or Pick (herbs) with a spade until the
 * patch's lives run out, and Pick on a flower, once, by hand. Step 3: compost, supercompost and ultracompost on a
 * patch (fewer diseases, more harvest lives), crops falling ill as they grow and dying if not cured with a plant cure,
 * dead crops cleared with a spade, and a grown flower keeping the allotments beside it well.
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
 * offset is drawn from the account rather than at random ([offset]); herbs and flowers picked with picking_low; a
 * herb every 4 ticks as a crop is; a flower picked 2 ticks after the click; flower produce that doesn't fit falling
 * to the floor; magic secateurs' 10% on limpwurt as 10% more numbers to draw from (its page gives no formula);
 * curing, composting and clearing landing 3 ticks after the click; a diseased crop always dying at its next disease
 * tick; that compost can't go on a grown, diseased or dead crop.
 * Sourced too: clicking again two ticks into a harvest picks two crops at each roll from then (the Wiki's Herb patch,
 * "similar to allotments"); guessed that a click on the first or second tick counts (the page: "spam-clicking during
 * the first two ticks").
 *
 * Step 4, trees (the Wiki's Tree patch, Seedling, each tree's page, Disease and Roots, read 2026-11-14): an empty plant
 * pot used on a bare patch is filled (no trowel needed); a tree seed on the filled pot (trowel, the tree's level)
 * makes a seedling, watered it grows into a sapling at the next farming tick, in the pack or the bank; the sapling is
 * planted in a tree patch with a spade (the pot back); the tree grows every 40 minutes (a yew's first stage 5 minutes
 * from planting), is checked once for its check-health XP and the planting XP (the Wiki's Farming info: planting XP
 * "upon checking the health ... or upon clearing the patch if it died"), chopped with Woodcutting's rates for logs (1/8 a log to fall,
 * the trees' pages), and its stump dug up for roots (one at the tree's level, one more every 8, four at most), a dig
 * that can fail below 99 and never at 99; a diseased tree is pruned with secateurs, 75% a try (the Disease page), for
 * leaves, each try made only when a Farming skill roll passes (Mod Lenny: "a normal skill roll to just check whether or
 * not it should do anything"). Guessed: a stump growing back at the tree's next 40-minute tick; the chance to dig up a
 * stump, 129/256 at level 1 rising to always at 99; the pruning skill roll, the same 129/256 to always at 99; a try at
 * the stump or at pruning every 4 ticks; one leaf a prune; filling a pot and planting a sapling landing 3 ticks after
 * the click, a seedling made and watered at once; Check-health giving its XP at once with no animation; the Lumbridge,
 * Varrock, Falador and Taverley tree patches only (the Gnome Stronghold's waits with the fruit trees).
 */
class Farming(private val world: World, private val clock: () -> Long, private val diseases: Boolean = true) {
    fun register() {
        for (loc in FarmingPatches.LOCS) {
            world.handlers.onLoc(loc, OP_ACTION) { op -> action(op) }
            world.handlers.onLoc(loc, OP_INSPECT) { op -> inspect(op) }
            world.handlers.onObjOnLoc(loc) { op -> useObj(op) }
        }
        for (tree in Trees.ALL) {
            world.handlers.onObjOnObj(tree.seed, Trees.POT_FILLED) { op -> plantSeedling(op, tree) }
            for (can in WATERING_CANS) world.handlers.onObjOnObj(tree.seedling, can) { op -> waterSeedling(op, tree) }
            world.handlers.onObjOnObj(tree.seedling, WATERING_CAN_EMPTY) { world.message(player, FarmingLines.CAN_EMPTY) }
        }
    }

    /**
     * Grows [player]'s patches to the clock and sets the varbits of the area they stand in. Every tick, so a step into
     * an area or a farming tick shows at once; PlayerVars only sends what changed.
     */
    fun process(player: Player) {
        grow(player)
        growFirstStages(player)
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

    /** The farming tick [player] is on now: the real one, or where `::farmclock pause` held it, plus `::farmtick`'s. */
    fun tickOf(player: Player): Long = (player.farming.pausedAt ?: realTick(player)) + player.farming.skipped

    private fun realTick(player: Player): Long = FarmingClock.tick(clock(), offset(player), 0)

    /**
     * [player]'s farming clock in milliseconds: [tickOf]'s tick, plus the time into it when the clock runs. A farming
     * tick t starts at t x [TICK_MILLIS].
     */
    fun millisOf(player: Player): Long {
        val into = if (player.farming.pausedAt != null) 0L else Math.floorMod(clock() + offset(player) * 60_000L, TICK_MILLIS)
        return tickOf(player) * TICK_MILLIS + into
    }

    /**
     * A yew whose first stage has run its five minutes from planting grows, checked every game tick so it doesn't wait
     * for a farming tick (the Wiki's Growth timing: the first stage "does not follow normal growth timing").
     */
    private fun growFirstStages(player: Player) {
        val now = millisOf(player)
        for ((key, patch) in player.farming.patches) {
            val crop = patch.crop ?: continue
            if (crop.firstStageMillis <= 0 || patch.stage != 0 || !patch.growing || patch.plantedMillis < 0) continue
            if (now < patch.plantedMillis + crop.firstStageMillis) continue
            if (FarmingPatches.byKey(key) == null) continue
            patch.stage = 1
            patch.watered = false
            patch.plantedMillis = -1
        }
    }

    /**
     * Holds [player]'s farming clock where it is (`::farmclock pause`), so only `::farmtick` grows their crops, as a
     * test that checks each stage needs; [paused] false runs it again, the crops catching up on the time it was held.
     */
    fun pause(player: Player, paused: Boolean) {
        grow(player)
        player.farming.pausedAt = if (paused) player.farming.pausedAt ?: realTick(player) else null
        process(player)
    }

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
        grow(state, first, now)
        growSeedlings(player)
    }

    /**
     * Watered seedlings become saplings at a farming tick, in the pack and in the bank (the Wiki's Seedling: "within the
     * next five minutes", banked or logged out too). In the bank they stack with saplings already there.
     */
    private fun growSeedlings(player: Player) {
        for ((slot, obj) in player.inventory.contents()) {
            val tree = Trees.byWatered(obj.id) ?: continue
            player.inventory[slot] = obj.copy(id = tree.sapling)
        }
        val bank = player.bank
        val tabs = world.bankScreen.tabs(player)
        // Last slot first, so a slot closed up behind doesn't move the ones still to come.
        for ((slot, obj) in bank.contents().reversed()) {
            val tree = Trees.byWatered(obj.id) ?: continue
            // The sapling's stack, or its placeholder, which is filled as a deposit fills it.
            val placeholder = world.objTypes[tree.sapling]?.placeholderlink?.let { world.objTypes[it] }?.takeIf { it.isPlaceholder && it.placeholderlink == tree.sapling }?.id
            val stack = (0 until bank.capacity).firstOrNull { bank[it]?.id == tree.sapling || (placeholder != null && bank[it]?.id == placeholder) }
            if (stack == null) {
                bank[slot] = obj.copy(id = tree.sapling)
                continue
            }
            if (bank[stack]!!.id == placeholder) {
                bank[stack] = InvObj(tree.sapling, obj.count)
                world.bank.discard(bank, tabs, slot, any = true)
                continue
            }
            // As many as the stack has room for; any left over stay watered seedlings until there is room.
            val have = bank[stack]!!
            val moved = minOf(obj.count.toLong(), Inventory.MAX_STACK.toLong() - have.count).toInt()
            if (moved <= 0) continue
            bank[stack] = have.copy(count = have.count + moved)
            if (moved == obj.count) world.bank.discard(bank, tabs, slot, any = true) else bank[slot] = obj.copy(count = obj.count - moved)
        }
    }

    /** Runs farming ticks [first] to [last] on [state]'s patches. */
    fun grow(state: FarmingState, first: Long, last: Long) {
        if (state.patches.isEmpty()) return
        for (tick in first..last) growTick(state, tick)
    }

    /** One farming tick for every patch the player has touched. */
    private fun growTick(state: FarmingState, tick: Long) {
        // The flowers grown at the start of the tick, so a flower growing up this tick protects every allotment beside
        // it from the next tick on, whichever order the patches come in.
        val flowers = grownFlowers(state)
        val iterator = state.patches.entries.iterator()
        while (iterator.hasNext()) {
            val (key, patch) = iterator.next()
            val kind = FarmingPatches.byKey(key)?.kind ?: continue
            val crop = patch.crop
            if (crop == null) {
                if (patch.weeds < kind.weeds) patch.weeds++
                // An overgrown patch is what an untouched one is: no need to keep it, unless it holds compost or an action
                // under way (a rake started on it) would lose track of itself.
                if (patch.weeds >= kind.weeds && patch.compost == 0 && patch.doing == DO_NOTHING) iterator.remove()
                continue
            }
            if (patch.dead || patch.stage >= crop.stages) {
                // A chopped tree grows back from its stump at its next tick. Guessed: the Wiki says only "eventually".
                if (patch.stump && tick % kind.cycleTicks == 0L) patch.stump = false
                continue
            }
            if (patch.diseased) {
                if (tick % kind.diseaseCycleTicks == 0L) {
                    patch.diseased = false
                    patch.dead = true
                }
                continue
            }
            if (patch.stage == 0 && crop.firstStageMillis > 0) {
                // A yew's first stage lasts five minutes from planting, off the usual clock (the Wiki's Growth timing).
                // Online [growFirstStages] ends it on time; caught up offline, it ended before this tick began, so the
                // tick's own growth can follow it, as it would have for a player online.
                if (patch.plantedMillis >= 0 && tick * TICK_MILLIS < patch.plantedMillis + crop.firstStageMillis) continue
                patch.stage = 1
                patch.watered = false
                patch.plantedMillis = -1
            }
            if (tick % kind.cycleTicks != 0L) continue
            if (diseases && patch.stage >= 1 && !isProtected(flowers, key, crop) && world.random.nextInt(DISEASE_DENOMINATOR) < Compost.diseaseChance(crop.disease, patch.compost, patch.watered)) {
                patch.diseased = true
                patch.watered = false
                continue
            }
            patch.stage++
            patch.watered = false
            if (patch.stage >= crop.stages) patch.lives = kind.lives + if (kind.composting) patch.compost else 0
        }
    }

    /**
     * Whether [crop] in patch [key] is kept well: an allotment crop with its flower fully grown in the flower patch of
     * the same area ([Crops.protects]).
     */
    fun isProtected(state: FarmingState, key: String, crop: Crop): Boolean = isProtected(grownFlowers(state), key, crop)

    private fun isProtected(flowers: Map<String, Crop>, key: String, crop: Crop): Boolean {
        if (crop.kind != PatchKind.ALLOTMENT) return false
        val flowerPatch = FarmingPatches.areaOf(key)?.patches?.firstOrNull { it.kind == PatchKind.FLOWER } ?: return false
        val flower = flowers[flowerPatch.key] ?: return false
        return Crops.protects(flower, crop)
    }

    /** The fully grown flowers in [state], by patch key. */
    private fun grownFlowers(state: FarmingState): Map<String, Crop> = state.patches.entries
        .filter { (key, patch) -> patch.grown && FarmingPatches.byKey(key)?.kind == PatchKind.FLOWER }
        .associate { (key, patch) -> key to patch.crop!! }

    /** Makes the crop in [patch] diseased now (`::farmdisease`), if it is at a stage that can be; false if not. */
    fun makeDiseased(player: Player, patch: FarmingPatch): Boolean {
        val state = player.farming.patches[patch.key] ?: return false
        if (!state.growing || state.stage < 1) return false
        state.diseased = true
        state.watered = false
        refresh(player)
        return true
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
        val tree = patch.kind == PatchKind.TREE
        val next = when {
            state.dead -> DO_CLEAR_DEAD
            state.diseased -> if (tree) DO_PRUNE else DO_CURE
            state.crop == null && state.weeds > 0 -> DO_RAKE
            state.grown && tree -> if (!state.checked) DO_CHECK else if (state.stump) DO_DIG_STUMP else DO_CHOP
            state.grown && patch.kind == PatchKind.FLOWER -> DO_PICK
            state.grown -> DO_HARVEST
            else -> DO_NOTHING
        }
        if (op.repeat && next != state.doing) {
            // The patch changed under a repeating action: a stump that grew back while being dug up, a tree that died
            // while being pruned. The action ends rather than turning into another.
            if (state.doing == DO_DIG_STUMP && next == DO_CHOP) world.message(player, FarmingLines.CHOP_FIRST)
            state.doing = DO_NOTHING
            return stop()
        }
        state.doing = DO_NOTHING
        when (next) {
            DO_CLEAR_DEAD -> clearDead(patch, state)
            DO_PRUNE -> prune(op, state)
            DO_CURE -> cure(state, null)
            DO_RAKE -> rake(op, patch, state)
            DO_CHECK -> checkHealth(state)
            DO_DIG_STUMP -> digStump(op, state)
            DO_CHOP -> chop(op, state)
            DO_PICK -> pick(patch, state)
            DO_HARVEST -> harvest(op, patch, state)
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
        state.doing = DO_RAKE
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
        val anim = if (patch.kind == PatchKind.HERB) ANIM_PICK_LOW else ANIM_DIG
        if (!op.repeat) {
            // A click again 1 or 2 ticks into a harvest still going picks two a time from then (the Wiki's Herb patch).
            val underway = state.harvestStart >= 0 && state.harvestTick >= tick - 1 && tick - state.harvestStart in 1..FAST_HARVEST_TICKS
            if (underway) {
                state.fastHarvest = true
            } else {
                state.harvestStart = tick
                state.fastHarvest = false
                world.message(player, FarmingLines.HARVEST_START, "patch" to text[patchName(patch.kind)])
                player.anim(anim)
            }
        }
        state.harvestTick = tick
        if (SkillClock.step(player, tick, HARVEST_CYCLE) == ClockStep.DUE) {
            player.anim(anim)
            val produce = world.objTypes[crop.produce] ?: error("No obj type ${crop.produce} for ${crop.key}")
            repeat(if (state.fastHarvest) 2 else 1) { pick ->
                if (pick > 0 && player.inventory.freeSlots == 0) return@repeat
                player.inventory.add(produce, 1)
                player.skills.addTenths(Skill.FARMING, crop.harvestXpTenths)
                world.message(player, FarmingLines.HARVESTED, "produce" to produce.name.lowercase())
                if (!keepsLife(crop, player)) state.lives--
                if (state.lives <= 0) {
                    // The planting XP comes now, with the last crop (the Wiki's Allotment and Herb patch: on the final harvest).
                    player.skills.addTenths(Skill.FARMING, crop.plantXpTenths)
                    state.clear()
                    refresh(player)
                    world.message(player, FarmingLines.EMPTY, "patch" to text[patchName(patch.kind)])
                    return stop()
                }
            }
            refresh(player)
        }
        state.doing = DO_HARVEST
        world.repeatOp(player, op)
    }

    /**
     * Pick on a grown flower: its produce all at once, then both its XPs (the Wiki's Flower patch: "Both plant and
     * harvest experience is awarded once when the patch is cleared"), and the patch is bare again.
     */
    private suspend fun ScriptContext.pick(patch: FarmingPatch, state: PatchState) {
        val crop = state.crop ?: return
        if (player.inventory.freeSlots == 0) return world.message(player, FarmingLines.INVENTORY_FULL)
        player.anim(ANIM_PICK_LOW)
        delay(PICK_DELAY)
        if (state.crop !== crop || !state.grown) return
        val produce = world.objTypes[crop.produce] ?: error("No obj type ${crop.produce} for ${crop.key}")
        giveOrDrop(crop.produce, flowerYield(crop, player.skills.currentLevel(Skill.FARMING), hasMagicSecateurs(player)))
        player.skills.addTenths(Skill.FARMING, crop.plantXpTenths + crop.harvestXpTenths)
        world.message(player, FarmingLines.PICKED, "produce" to produce.name.lowercase())
        state.clear()
        refresh(player)
    }

    /** Puts [count] of [id] in the pack, and what doesn't fit on the floor under the player for them. */
    private fun ScriptContext.giveOrDrop(id: Int, count: Int) {
        val type = world.objTypes[id] ?: error("No obj type $id")
        val added = player.inventory.add(type, count)
        if (added >= count) return
        val stacks = if (type.stackable) listOf(count - added) else List(count - added) { 1 }
        for (n in stacks) {
            world.groundObjs.addPrivate(type, n, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
        }
    }

    /**
     * How many of a flower one Pick gives: its [Crop.yield], and for limpwurt a random number from 0 to level - 1,
     * divided by ten, more (Mod Ash on the Limpwurt seed page: "a random number between 0 and (stat - 1) inclusive").
     * Magic secateurs "increase the chances by 10%" (the same page). Guessed: by drawing from 10% more numbers.
     */
    fun flowerYield(crop: Crop, level: Int, secateurs: Boolean): Int {
        if (!crop.levelBonus) return crop.yield
        val numbers = if (secateurs) level * 11 / 10 else level
        return crop.yield + world.random.nextInt(maxOf(numbers, 1)) / 10
    }

    private fun hasMagicSecateurs(player: Player) = player.inventory.count(MAGIC_SECATEURS) > 0 || player.equipment.count(MAGIC_SECATEURS) > 0

    /** Whether a harvest keeps its life: the crop's chance at the player's level, magic secateurs' 10% on both ends. */
    fun keepsLife(crop: Crop, player: Player): Boolean {
        val (low, high) = lifeChances(crop, hasMagicSecateurs(player))
        return SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.FARMING))
    }

    private suspend fun ScriptContext.useObj(op: Op) {
        val patch = patchOf(op) ?: return
        val state = player.farming.patch(patch)
        val obj = op.obj
        val tree = patch.kind == PatchKind.TREE
        val standing = tree && state.grown && state.checked && !state.stump
        when {
            obj == RAKE -> if (state.crop == null && state.weeds > 0) rake(op.asAction(), patch, state) else nothing()
            obj == SPADE && state.dead -> clearDead(patch, state)
            obj == SPADE && tree && state.stump -> digStump(op.asAction(), state)
            obj == SPADE && standing -> world.message(player, FarmingLines.CHOP_FIRST)
            obj == SPADE -> if (state.grown && patch.kind.spade && !tree) harvest(op.asAction(), patch, state) else nothing()
            (obj == SECATEURS || obj == MAGIC_SECATEURS) && tree && state.diseased -> prune(op.asAction(), state)
            obj in world.woodcutting.data.tools && standing -> chop(op.asAction(), state)
            obj == Trees.POT_EMPTY -> fillPot(op, state)
            obj == PLANT_CURE && tree -> nothing()
            obj == PLANT_CURE -> cure(state, op.sub)
            obj in Compost.BUCKETS -> compost(op, state, Compost.BUCKETS.getValue(obj))
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
        // A sapling goes in with a spade (the Wiki's Tree patch), a seed with a dibber.
        val sapling = crop.kind == PatchKind.TREE
        if (sapling && player.inventory.count(SPADE) == 0L) return world.message(player, FarmingLines.NEED_SPADE)
        if (!sapling && player.inventory.count(SEED_DIBBER) == 0L) return world.message(player, FarmingLines.NEED_DIBBER)
        val seeds = patch.kind.seeds
        if (player.inventory.count(crop.seed) < seeds) {
            return world.message(player, FarmingLines.NEED_SEEDS, "count" to seeds, "patch" to name)
        }
        player.anim(if (sapling) ANIM_DIG else ANIM_DIBBER)
        delay(PLANT_DELAY)
        // Checked again: the seeds or the patch may have gone in the meantime.
        if (state.crop != null || state.weeds > 0 || player.inventory.count(crop.seed) < seeds) return
        state.crop = crop
        state.stage = 0
        state.watered = false
        state.lives = 0
        state.plantedMillis = millisOf(player)
        val seedName = world.objTypes[crop.seed]?.name?.lowercase() ?: crop.key
        if (sapling) {
            // The pot comes back where the sapling was; the planting XP waits for Check-health or clearing a dead tree.
            val slot = op.sub.takeIf { player.inventory[it]?.id == crop.seed } ?: player.inventory.contents().first { it.second.id == crop.seed }.first
            player.inventory[slot] = InvObj(Trees.POT_EMPTY, 1)
            world.message(player, FarmingLines.SAPLING_PLANTED, "sapling" to seedName, "patch" to name)
        } else {
            Making.removeObj(player.inventory, crop.seed, seeds)
            world.message(player, FarmingLines.PLANTED, "count" to seeds, "seeds" to seedName, "patch" to name)
        }
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

    /** A plant cure on a diseased crop: well again, growing on from where it stopped, and the vial left. */
    private suspend fun ScriptContext.cure(state: PatchState, slot: Int?) {
        val crop = state.crop
        if (crop == null || !state.diseased) return world.message(player, FarmingLines.NOT_DISEASED)
        if (player.inventory.count(PLANT_CURE) == 0L) return world.message(player, FarmingLines.NEED_CURE)
        player.anim(ANIM_CURE)
        delay(CURE_DELAY)
        val at = slot?.takeIf { player.inventory[it]?.id == PLANT_CURE } ?: player.inventory.contents().firstOrNull { it.second.id == PLANT_CURE }?.first ?: -1
        if (at < 0 || state.crop !== crop || !state.diseased) return
        player.inventory[at] = InvObj(VIAL, 1)
        state.diseased = false
        world.message(player, FarmingLines.CURED, "crop" to world.objTypes[crop.produce]?.name?.lowercase().orEmpty())
        refresh(player)
    }

    /**
     * A dead crop dug up with a spade: the planting XP after all (the Wiki's Herb patch and Flower patch: given when a
     * dead patch is cleared) and bare soil.
     */
    private suspend fun ScriptContext.clearDead(patch: FarmingPatch, state: PatchState) {
        val crop = state.crop ?: return
        if (player.inventory.count(SPADE) == 0L) return world.message(player, FarmingLines.NEED_SPADE)
        player.anim(ANIM_DIG)
        delay(CLEAR_DELAY)
        if (state.crop !== crop || !state.dead) return
        player.skills.addTenths(Skill.FARMING, crop.plantXpTenths)
        state.clear()
        world.message(player, FarmingLines.CLEARED)
        refresh(player)
    }

    /** A bucket of compost on a patch with none: before planting or while the crop grows (the Wiki: any growth stage). */
    private suspend fun ScriptContext.compost(op: Op, state: PatchState, level: Int) {
        if (state.compost > 0) return world.message(player, FarmingLines.ALREADY_COMPOSTED)
        if (state.crop == null && state.weeds > 0) return world.message(player, FarmingLines.NEEDS_WEEDING)
        if (state.crop != null && !state.growing) return world.message(player, FarmingLines.COMPOST_NOT_NOW)
        val bucket = player.inventory[op.sub]?.id ?: return
        player.anim(ANIM_COMPOST)
        delay(COMPOST_DELAY)
        if (player.inventory[op.sub]?.id != bucket || state.compost > 0 || (state.crop == null && state.weeds > 0) || (state.crop != null && !state.growing)) return
        player.inventory[op.sub] = InvObj(Compost.EMPTY_BUCKET, 1)
        state.compost = level
        player.skills.addTenths(Skill.FARMING, Compost.xpTenths(level))
        world.message(player, FarmingLines.COMPOSTED, "compost" to world.objTypes[bucket]?.name?.lowercase().orEmpty())
    }

    /**
     * An empty plant pot used on a bare patch: filled with soil. No trowel needed (the Wiki's Fruit tree patch: "works
     * just as well if you don't have a trowel with you").
     */
    private suspend fun ScriptContext.fillPot(op: Op, state: PatchState) {
        if (state.crop != null || state.weeds > 0) return world.message(player, FarmingLines.POT_NEEDS_EMPTY_PATCH)
        player.anim(ANIM_FILL_POT)
        delay(FILL_POT_DELAY)
        if (player.inventory[op.sub]?.id != Trees.POT_EMPTY || state.crop != null || state.weeds > 0) return
        player.inventory[op.sub] = InvObj(Trees.POT_FILLED, 1)
        world.message(player, FarmingLines.POT_FILLED)
    }

    /** The two slots of an item used on another: [a]'s and the other's, whichever way round it was used. */
    private fun ScriptContext.slots(op: Op, a: Int): Pair<Int, Int>? {
        val (aSlot, bSlot) = if (op.obj == a) op.objSlot to op.sub else op.sub to op.objSlot
        if (player.inventory[aSlot]?.id != a || player.inventory[bSlot] == null || aSlot == bSlot) return null
        return aSlot to bSlot
    }

    /** A tree seed on a filled plant pot, with a trowel and the tree's level (the Wiki's Seedling): the seedling. */
    private fun ScriptContext.plantSeedling(op: Op, tree: TreeInfo) {
        val (potSlot, _) = slots(op, Trees.POT_FILLED) ?: return
        if (player.inventory.count(TROWEL) == 0L) return world.message(player, FarmingLines.NEED_TROWEL)
        if (player.skills.currentLevel(Skill.FARMING) < tree.crop.level) {
            return world.message(player, FarmingLines.LEVEL, "level" to tree.crop.level)
        }
        player.anim(ANIM_TROWEL)
        Making.removeObj(player.inventory, tree.seed, 1)
        player.inventory[potSlot] = InvObj(tree.seedling, 1)
        world.message(player, FarmingLines.SEEDLING_PLANTED, "seed" to world.objTypes[tree.seed]?.name?.lowercase().orEmpty())
    }

    /** A watering can on a seedling: watered, to grow into a sapling at the next farming tick. */
    private fun ScriptContext.waterSeedling(op: Op, tree: TreeInfo) {
        val (seedlingSlot, canSlot) = slots(op, tree.seedling) ?: return
        val can = player.inventory[canSlot]?.takeIf { it.id in WATERING_CANS } ?: return
        player.anim(ANIM_WATER)
        player.inventory[canSlot] = can.copy(id = wateringCan(WATERING_CANS.indexOf(can.id)))
        player.inventory[seedlingSlot] = InvObj(tree.watered, 1)
        world.message(player, FarmingLines.SEEDLING_WATERED)
    }

    /**
     * Check-health on a grown tree: its check-health XP and the planting XP (the Wiki's Farming info), once, and then it
     * can be chopped (the Wiki's Tree patch).
     */
    private fun ScriptContext.checkHealth(state: PatchState) {
        val crop = state.crop ?: return
        if (!state.grown || state.checked) return
        state.checked = true
        player.skills.addTenths(Skill.FARMING, crop.plantXpTenths + crop.harvestXpTenths)
        world.message(player, FarmingLines.CHECKED)
        refresh(player)
    }

    /**
     * Chop down on a checked tree: Woodcutting's roll every 4 ticks with the best axe, as a wild tree of its kind, and
     * a 1 in 8 chance a log that the tree falls to its stump (the trees' pages: "a 1/8 chance to be depleted per log").
     */
    private fun ScriptContext.chop(op: Op, state: PatchState) {
        if (op.repeat && player.modalOpen) return stop()
        val crop = state.crop ?: return stop()
        val info = Trees.of(crop) ?: return stop()
        if (!state.grown || !state.checked || state.stump) return stop()
        val data = world.woodcutting.data
        val tree = data.trees.values.first { it.key == info.woodcutting }
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.WOODCUTTING) < tree.level) {
            player.message(text[WoodcuttingLines.LEVEL, "level" to tree.level])
            return stop()
        }
        val axe = (data.tools.best(player) as? ToolCheck.Found)?.tool ?: run {
            player.message(text[WoodcuttingLines.NO_AXE])
            return stop()
        }
        if (player.inventory.freeSlots == 0) {
            player.message(text[WoodcuttingLines.INVENTORY_FULL])
            return stop()
        }
        if (!op.repeat) {
            player.message(text[WoodcuttingLines.SWING])
            player.anim(axe.anim)
        }
        if (SkillClock.step(player, tick, Woodcutting.CYCLE) == ClockStep.DUE) {
            player.anim(axe.anim)
            val (low, high) = tree.rates.getValue(data.tier(axe))
            if (SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.WOODCUTTING))) {
                val logs = world.objTypes[tree.logs] ?: error("No obj type ${tree.logs} for ${tree.key} logs")
                player.inventory.add(logs, 1)
                player.skills.addTenths(Skill.WOODCUTTING, tree.xpTenths)
                player.message(text[WoodcuttingLines.GET_LOGS, "logs" to logs.name.lowercase()])
                if (tree.birdNests) world.birdNests.roll(player)
                if (world.random.nextInt(TREE_DEPLETE) == 0) {
                    state.stump = true
                    refresh(player)
                    return stop()
                }
            }
        }
        state.doing = DO_CHOP
        world.repeatOp(player, op)
    }

    /**
     * Clear on a stump, or a spade used on it: a try every [DIG_CYCLE] ticks that can fail below 99 (Mod Kieren: "At 99
     * you never fail"), and the roots ([Trees.roots]) and bare soil when it works.
     */
    private fun ScriptContext.digStump(op: Op, state: PatchState) {
        if (op.repeat && player.modalOpen) return stop()
        val crop = state.crop ?: return stop()
        val info = Trees.of(crop) ?: return stop()
        if (!state.stump) return stop()
        if (player.inventory.count(SPADE) == 0L) {
            world.message(player, FarmingLines.NEED_SPADE)
            return stop()
        }
        if (player.inventory.freeSlots == 0) {
            world.message(player, FarmingLines.INVENTORY_FULL)
            return stop()
        }
        if (!op.repeat) player.anim(ANIM_DIG)
        if (SkillClock.step(player, tick, DIG_CYCLE) == ClockStep.DUE) {
            player.anim(ANIM_DIG)
            val level = player.skills.currentLevel(Skill.FARMING)
            if (SuccessRoll.roll(world.random, STUMP_LOW, STUMP_HIGH, level)) {
                giveOrDrop(info.roots, Trees.roots(crop, level))
                world.message(player, FarmingLines.STUMP_CLEARED)
                state.clear()
                refresh(player)
                return stop()
            }
        }
        state.doing = DO_DIG_STUMP
        world.repeatOp(player, op)
    }

    /** Prune on a diseased tree, or secateurs used on it: a try every [PRUNE_CYCLE] ticks, 3 in 4 to cure it, and leaves. */
    private fun ScriptContext.prune(op: Op, state: PatchState) {
        if (op.repeat && player.modalOpen) return stop()
        val crop = state.crop ?: return stop()
        val info = Trees.of(crop) ?: return stop()
        if (!state.diseased) return stop()
        val secateurs = player.inventory.count(SECATEURS) > 0 || player.inventory.count(MAGIC_SECATEURS) > 0 || player.equipment.count(MAGIC_SECATEURS) > 0
        if (!secateurs) {
            world.message(player, FarmingLines.NEED_SECATEURS)
            return stop()
        }
        if (!op.repeat) player.anim(ANIM_PRUNE)
        if (SkillClock.step(player, tick, PRUNE_CYCLE) == ClockStep.DUE) {
            player.anim(ANIM_PRUNE)
            // Mod Lenny: a skill roll decides whether anything happens; then "modulo(random(100),4) > 0" succeeds, 3 in 4.
            if (!SuccessRoll.roll(world.random, PRUNE_LOW, PRUNE_HIGH, player.skills.currentLevel(Skill.FARMING))) {
                state.doing = DO_PRUNE
                return world.repeatOp(player, op)
            }
            if (world.random.nextInt(PRUNE_ODDS) > 0) {
                state.diseased = false
                giveOrDrop(info.leaves, 1)
                world.message(player, FarmingLines.PRUNED)
                refresh(player)
                return stop()
            }
            world.message(player, FarmingLines.PRUNE_LEFT)
        }
        state.doing = DO_PRUNE
        world.repeatOp(player, op)
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
            state.dead -> text[FarmingLines.STATE_DEAD, "crop" to cropName]
            state.diseased -> text[FarmingLines.STATE_DISEASED, "crop" to cropName]
            state.stump -> text[FarmingLines.STATE_STUMP, "crop" to cropName]
            state.grown && crop.kind == PatchKind.TREE && !state.checked -> text[FarmingLines.STATE_CHECK, "crop" to cropName]
            state.grown -> text[FarmingLines.STATE_GROWN, "crop" to cropName]
            else -> text[FarmingLines.STATE_GROWING, "crop" to cropName]
        }
        val soil = when (state.compost) {
            Compost.COMPOST -> FarmingLines.SOIL_COMPOST
            Compost.SUPER -> FarmingLines.SOIL_SUPERCOMPOST
            Compost.ULTRA -> FarmingLines.SOIL_ULTRACOMPOST
            else -> FarmingLines.SOIL_UNTREATED
        }
        val message = listOf(text[FarmingLines.INSPECT, "patch" to text[patchName(patch.kind)]], text[soil], what)
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

        /** A farming tick, 5 minutes, in milliseconds. */
        const val TICK_MILLIS = FarmingClock.TICK_MINUTES * 60_000L

        /** Harvest lives a crop starts with (the Wiki's Farming: three, before compost). */
        const val BASE_LIVES = 3

        const val RAKE = 5341
        const val SEED_DIBBER = 5343
        const val SPADE = 952
        const val WEEDS = 6055
        const val MAGIC_SECATEURS = 7409
        const val PLANT_CURE = 6036
        const val VIAL = 229
        const val TROWEL = 5325
        const val SECATEURS = 5329

        /** What a patch's repeating action is doing ([PatchState.doing]), so a change under it ends it. */
        const val DO_NOTHING = 0
        const val DO_RAKE = 1
        const val DO_HARVEST = 2
        const val DO_CHOP = 3
        const val DO_DIG_STUMP = 4
        const val DO_PRUNE = 5
        const val DO_CLEAR_DEAD = 6
        const val DO_CURE = 7
        const val DO_CHECK = 8
        const val DO_PICK = 9

        /** A farmed tree falls at 1 log in 8 (the trees' pages). */
        const val TREE_DEPLETE = 8

        /** Guessed: the chance to dig up a stump, 129/256 at level 1 to every time at 99 (the Wiki: it scales, never fails at 99). */
        const val STUMP_LOW = 128
        const val STUMP_HIGH = 255

        /** Guessed: the skill roll before a prune, 129/256 at level 1 to every time at 99 (Mod Lenny gives none). */
        const val PRUNE_LOW = 128
        const val PRUNE_HIGH = 255

        /** Pruning works 3 times in 4 (the Disease page). */
        const val PRUNE_ODDS = 4

        /** Disease chances are in 128ths (the Wiki's Disease page). */
        const val DISEASE_DENOMINATOR = 128

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

        /** farming_plant_cure and farming_pour_water (the compost bucket's pour). */
        const val ANIM_CURE = 2288
        const val ANIM_COMPOST = 2283

        /** picking_low: herbs and flowers. */
        const val ANIM_PICK_LOW = 2282

        /** farming_filling_plantpot, farming_trowel_digging (a seed into a pot) and pruning_mid. */
        const val ANIM_FILL_POT = 2287
        const val ANIM_TROWEL = 2272
        const val ANIM_PRUNE = 2278

        const val RAKE_CYCLE = 4
        const val HARVEST_CYCLE = 4

        /** How many ticks into a harvest a second click makes it pick two a time ("waiting 2 game ticks, then clicking again"). */
        const val FAST_HARVEST_TICKS = 2
        const val PLANT_DELAY = 3
        const val WATER_DELAY = 3
        const val PICK_DELAY = 2
        const val CURE_DELAY = 3
        const val CLEAR_DELAY = 3
        const val COMPOST_DELAY = 3
        const val FILL_POT_DELAY = 3
        const val DIG_CYCLE = 4
        const val PRUNE_CYCLE = 4

        fun patchName(kind: PatchKind) = when (kind) {
            PatchKind.ALLOTMENT -> FarmingLines.PATCH_ALLOTMENT
            PatchKind.HERB -> FarmingLines.PATCH_HERB
            PatchKind.FLOWER -> FarmingLines.PATCH_FLOWER
            PatchKind.TREE -> FarmingLines.PATCH_TREE
        }

        /** A crop's chance pair, low and high, with magic secateurs' 10% when [secateurs] (rounded down, the Wiki's order). */
        fun lifeChances(crop: Crop, secateurs: Boolean): Pair<Int, Int> =
            if (secateurs) crop.ctsLow * 11 / 10 to crop.ctsHigh * 11 / 10 else crop.ctsLow to crop.ctsHigh
    }
}
