package osrsps.game.skill.farming

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * One patch as one player has it. With no [crop] the patch holds [weeds] weed stages, from 0 (raked clear) to its
 * kind's [PatchKind.weeds] (overgrown, as every patch starts). With a crop it is at growth [stage], 0 when planted, and
 * fully grown at the crop's [Crop.stages], with [lives] harvests left before the patch is empty again (counted from
 * when it finishes growing). [watered]: watered since it last grew. [compost]: the soil's treatment, 0 none, 1 compost,
 * 2 supercompost, 3 ultracompost ([Compost]). [diseased] stops it growing until it is cured or dies; [dead] waits to
 * be cleared. A grown tree is [checked] once (Check-health) before it can be chopped, and is a [stump] once chopped
 * down until it grows back or is dug up. A checked fruit tree's [lives] are the fruit on it, 0 to 6.
 */
class PatchState(
    var weeds: Int,
    var crop: Crop? = null,
    var stage: Int = 0,
    var watered: Boolean = false,
    var lives: Int = 0,
    var compost: Int = 0,
    var diseased: Boolean = false,
    var dead: Boolean = false,
    var checked: Boolean = false,
    var stump: Boolean = false,
) {
    /**
     * The game tick the harvest under way started on, the last tick it ran, and whether a second click early in it made
     * it pick two at a time ([Farming]'s harvest). Not saved.
     */
    var harvestStart: Long = -1
    var harvestTick: Long = -1
    var fastHarvest: Boolean = false

    /** The farming clock's time in milliseconds ([Farming.millisOf]) the crop was planted at, or -1 if not known: a yew's first stage counts from it. */
    var plantedMillis: Long = -1

    /** The repeating action under way on the patch ([Farming.DO_RAKE] and on), not saved: one that finds it changed ends. */
    var doing: Int = 0

    val grown: Boolean get() = crop.let { it != null && stage >= it.stages && !dead }

    /** Growing, not yet ready to harvest, and neither diseased nor dead. */
    val growing: Boolean get() = crop.let { it != null && stage < it.stages && !diseased && !dead }

    /** Back to bare soil, which grows weeds again from the next farming tick; the compost goes with the crop. */
    fun clear() {
        crop = null
        stage = 0
        watered = false
        lives = 0
        weeds = 0
        compost = 0
        diseased = false
        dead = false
        checked = false
        stump = false
        plantedMillis = -1
    }

    /**
     * The patch's varbit value: what the client draws. In the cache's numbering an overgrown patch is 0 and a raked one
     * 3, so a player who has never farmed (every varbit 0) sees every patch overgrown, as a new account does on live.
     * Guessed: the three fully grown values of allotments and herbs are the crop's last three lives counting down (the
     * cache draws all three the same; RuneLite counts them as one state). A flower has one.
     */
    fun value(kind: PatchKind): Int {
        val crop = crop ?: return kind.weeds - weeds
        // Disease and death only come after the first stage, so stage 1 is the first of their values.
        if (dead) return crop.dead[(stage - 1).coerceIn(0, crop.dead.size - 1)]
        if (diseased) return crop.diseased[(stage - 1).coerceIn(0, crop.diseased.size - 1)]
        // A grown tree: Check-health, then the tree to chop, then its stump (the cache's tree multiloc).
        if (stage >= crop.stages && kind == PatchKind.TREE) return crop.harvest + if (stump) 2 else if (checked) 1 else 0
        // A grown fruit tree: Check-health, then the tree with its fruit (0 to 6), or its stump; a bush the same with 0
        // to 4 berries and no stump.
        if (stage >= crop.stages && kind.bears) return if (stump) crop.stumpValue else if (!checked) crop.checkValue else crop.harvest + lives.coerceIn(0, kind.lives)
        if (stage >= crop.stages) return crop.harvest + (kind.harvestValues - lives.coerceIn(1, kind.harvestValues))
        return if (watered && crop.watered.isNotEmpty()) crop.watered[stage] else crop.growing[stage]
    }

    fun copy() = PatchState(weeds, crop, stage, watered, lives, compost, diseased, dead, checked, stump).also { it.plantedMillis = plantedMillis }

    companion object {
        fun overgrown(kind: PatchKind) = PatchState(kind.weeds)
    }
}

/**
 * A player's farming: their patches by key ([FarmingPatch.key]; a patch they've never touched is overgrown and has no
 * entry), the farming tick their patches were last grown to and the ticks `::farmtick` has moved them on ([skipped]).
 * Saved with the player.
 */
class FarmingState {
    val patches = HashMap<String, PatchState>()

    /** The player's compost bins by key ([CompostBin.key]); an empty one has no entry. */
    val bins = HashMap<String, BinState>()

    fun bin(bin: CompostBin): BinState = bins.getOrPut(bin.key) { BinState() }

    /** The value [bin]'s varbit shows. */
    fun value(bin: CompostBin): Int = bins[bin.key]?.value() ?: 0

    /** The farming tick ([FarmingClock.tick]) the patches have grown to; -1 until the first tick is seen. */
    var lastTick: Long = -1

    /** Farming ticks `::farmtick` has added, on top of the clock. */
    var skipped: Long = 0

    /**
     * The real farming tick `::farmclock pause` held the clock at, or null while it runs. Not saved: a login runs the
     * clock again, and the crops catch up on the time it was held.
     */
    var pausedAt: Long? = null

    /** `::farmdisease off`: the player's crops don't fall ill on their own. Not saved: a login turns diseases back on. */
    var noDisease: Boolean = false

    fun patch(patch: FarmingPatch): PatchState = patches.getOrPut(patch.key) { PatchState.overgrown(patch.kind) }

    /** The value [patch]'s varbit shows, without making an entry for an untouched patch. */
    fun value(patch: FarmingPatch): Int = (patches[patch.key] ?: PatchState.overgrown(patch.kind)).value(patch.kind)

    /** Puts [other]'s state in place of this one's. */
    fun replaceWith(other: FarmingState) {
        patches.clear()
        for ((key, state) in other.patches) patches[key] = state.copy()
        bins.clear()
        for ((key, state) in other.bins) bins[key] = state.copy()
        lastTick = other.lastTick
        skipped = other.skipped
        pausedAt = other.pausedAt
        noDisease = other.noDisease
    }

    /** Whether nothing has happened: nothing worth saving. */
    val isUntouched: Boolean get() = patches.isEmpty() && bins.isEmpty() && lastTick < 0 && skipped == 0L
}

/**
 * Farming's clock. Crops grow on live's real-time farming ticks: the OSRS Wiki's Farming (read 2026-11-14) quotes the
 * 2018 update, "the game has clocks that tick every 5 minutes, every 10 minutes, every 20 minutes ...", so a crop
 * whose cycle is n ticks grows at every tick numbered a multiple of n, whoever planted it and when (a seed planted just
 * before a tick grows sooner). Each account's ticks are shifted by an offset that Mod Ash puts at 0 to 31 minutes (the
 * same page), fixed for the account. Crops grow while the player is logged out: the ticks missed are run at login.
 */
object FarmingClock {
    const val TICK_MINUTES = 5

    /** The most farming ticks run at once, about two weeks; every crop of this step is long settled by then. */
    const val MAX_CATCH_UP = 4_096

    /** The farming tick a player with [offset] minutes and [skipped] ticks is on at [epochMillis]. */
    fun tick(epochMillis: Long, offset: Int, skipped: Long): Long =
        Math.floorDiv(Math.floorDiv(epochMillis, 60_000L) + offset, TICK_MINUTES.toLong()) + skipped
}

/**
 * A [FarmingState] in a save: `{"tick": 5, "skipped": 0, "patches": {"falador_allotment_nw": {"crop": "potato",
 * "stage": 2, "watered": true, "compost": 2}}}`. A patch or crop this server doesn't know (one from a newer build) is dropped with
 * the rest kept, as a lost patch is better than a refused login.
 */
object FarmingSaves {
    fun toJson(state: FarmingState): JsonObject = buildJsonObject {
        put("tick", state.lastTick)
        if (state.skipped != 0L) put("skipped", state.skipped)
        putJsonObject("patches") {
            for ((key, patch) in state.patches.toSortedMap()) {
                putJsonObject(key) {
                    val crop = patch.crop
                    if (crop == null) {
                        put("weeds", patch.weeds)
                    } else {
                        put("crop", crop.key)
                        put("stage", patch.stage)
                        if (patch.watered) put("watered", true)
                        if (patch.lives > 0) put("lives", patch.lives)
                        if (patch.diseased) put("diseased", true)
                        if (patch.dead) put("dead", true)
                        if (patch.checked) put("checked", true)
                        if (patch.stump) put("stump", true)
                        if (patch.stage == 0 && patch.plantedMillis >= 0) put("planted", patch.plantedMillis)
                    }
                    if (patch.compost > 0) put("compost", patch.compost)
                }
            }
        }
        val bins = state.bins.filterValues { !it.empty }
        if (bins.isNotEmpty()) putJsonObject("bins") {
            for ((key, bin) in bins.toSortedMap()) {
                putJsonObject(key) {
                    put("kind", bin.kind)
                    put("count", bin.count)
                    if (bin.closed) put("closed", true)
                    if (bin.rot > 0) put("rot", bin.rot)
                    if (bin.open) put("open", true)
                }
            }
        }
    }

    fun fromJson(json: JsonObject): FarmingState {
        val state = FarmingState()
        state.lastTick = json["tick"]?.jsonPrimitive?.long ?: -1
        state.skipped = json["skipped"]?.jsonPrimitive?.long ?: 0
        require(state.skipped >= 0) { "Farming ticks skipped ${state.skipped} is negative" }
        for ((key, value) in json["patches"]?.jsonObject.orEmpty()) {
            val patch = FarmingPatches.byKey(key) ?: continue
            val entry = value.jsonObject
            val compost = entry["compost"]?.jsonPrimitive?.int ?: 0
            require(compost in 0..Compost.ULTRA) { "Farming patch $key has compost $compost" }
            val cropKey = entry["crop"]?.jsonPrimitive?.content
            if (cropKey == null) {
                val weeds = entry["weeds"]?.jsonPrimitive?.int ?: patch.kind.weeds
                require(weeds in 0..patch.kind.weeds) { "Farming patch $key has $weeds weeds" }
                state.patches[key] = PatchState(weeds, compost = compost)
                continue
            }
            val crop = Crops.byKey(cropKey)?.takeIf { it.kind == patch.kind } ?: continue
            val stage = entry["stage"]?.jsonPrimitive?.int ?: 0
            require(stage in 0..crop.stages) { "Farming patch $key is at stage $stage of ${crop.stages}" }
            val lives = entry["lives"]?.jsonPrimitive?.int ?: 0
            require(lives >= 0) { "Farming patch $key has $lives lives" }
            // A grown crop always has a life left; a save that says otherwise has lost it, so it gets the base back. A
            // fruit tree's or bush's lives are its fruit or berries, which can be none.
            val livesNow = when {
                crop.kind.bears -> lives.coerceAtMost(patch.kind.lives)
                stage >= crop.stages && lives == 0 -> patch.kind.lives
                else -> lives
            }
            val dead = entry["dead"]?.jsonPrimitive?.boolean ?: false
            // A crop is only ill or dead after its first stage and before it is grown; a save that says otherwise is healthy.
            val ill = stage in 1 until crop.stages
            val grownTree = crop.kind.checks && stage >= crop.stages
            val stumps = crop.kind.tree
            val checked = entry["checked"]?.jsonPrimitive?.boolean ?: false
            state.patches[key] = PatchState(
                0, crop, stage, entry["watered"]?.jsonPrimitive?.boolean ?: false, livesNow, compost,
                diseased = ill && !dead && (entry["diseased"]?.jsonPrimitive?.boolean ?: false), dead = ill && dead,
                // Only a grown tree or bush is checked, and only a checked tree can be a stump.
                checked = grownTree && checked, stump = stumps && grownTree && checked && (entry["stump"]?.jsonPrimitive?.boolean ?: false),
            ).also { if (stage == 0) it.plantedMillis = entry["planted"]?.jsonPrimitive?.long ?: -1 }
        }
        for ((key, value) in json["bins"]?.jsonObject.orEmpty()) {
            if (FarmingPatches.binByKey(key) == null) continue
            val entry = value.jsonObject
            val kind = entry["kind"]?.jsonPrimitive?.int ?: continue
            val count = entry["count"]?.jsonPrimitive?.int ?: 0
            // A bin this build can't draw is dropped, as an unknown patch is.
            if (kind !in Compostables.COMPOST..Compostables.ULTRA || count !in 0..Compostables.CAPACITY) continue
            val closed = entry["closed"]?.jsonPrimitive?.boolean ?: false
            val open = !closed && (entry["open"]?.jsonPrimitive?.boolean ?: false)
            val rot = (entry["rot"]?.jsonPrimitive?.int ?: 0).coerceIn(0, Compostables.ROT_STAGES)
            // An open bin has buckets left; only an open one holds ultracompost; a closed one is full.
            if ((open && count == 0) || (kind == Compostables.ULTRA && !open)) continue
            val bin = BinState(kind, if (closed) Compostables.CAPACITY else count, closed, if (closed) rot else 0, open)
            if (!bin.empty) state.bins[key] = bin
        }
        return state
    }
}
