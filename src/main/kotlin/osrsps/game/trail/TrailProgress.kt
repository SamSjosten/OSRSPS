package osrsps.game.trail

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A trail under way: the step the clue scroll is on ([ClueStep.key]), how many steps the whole trail has, and the steps
 * done so far, which won't come up again on it.
 */
class Trail(var step: String, val length: Int, val done: MutableList<String> = ArrayList()) {
    fun copy() = Trail(step, length, ArrayList(done))
}

/**
 * A player's trails under way, one per tier at most, the caskets they have opened of each tier, and the scroll cases
 * they have opened (each raising the tier's cap by one), what their STASH units hold, and their puzzle and light boxes
 * under way. Saved.
 */
class TrailProgress {
    private val trails = HashMap<TrailTier, Trail>()
    private val completed = HashMap<TrailTier, Int>()
    /** The scroll cases opened, by item id. */
    val cases: MutableSet<Int> = HashSet()
    /** What each filled STASH unit holds, by the unit's loc id. Which units are built is the units' own varbits. */
    val stashes: MutableMap<Int, List<Int>> = HashMap()
    /** Each puzzle box's pieces by slot (-1 for the gap), by the box's item id. */
    val puzzles: MutableMap<Int, IntArray> = HashMap()
    /** The light box's switches and lights, null with none under way. */
    var lightBox: LightBoxState? = null

    /** Caskets of [tier] opened: the Wiki's completed count. */
    fun completed(tier: TrailTier): Int = completed[tier] ?: 0

    fun setCompleted(tier: TrailTier, count: Int) {
        if (count <= 0) completed.remove(tier) else completed[tier] = count
    }

    operator fun get(tier: TrailTier): Trail? = trails[tier]

    operator fun set(tier: TrailTier, trail: Trail?) {
        if (trail == null) trails.remove(tier) else trails[tier] = trail
    }

    val isEmpty: Boolean get() = trails.isEmpty() && completed.isEmpty() && cases.isEmpty() && stashes.isEmpty() && puzzles.isEmpty() && lightBox == null

    fun all(): Map<TrailTier, Trail> = trails.toSortedMap()

    fun replaceWith(other: TrailProgress) {
        trails.clear()
        for ((tier, trail) in other.trails) trails[tier] = trail.copy()
        completed.clear()
        completed.putAll(other.completed)
        cases.clear()
        cases.addAll(other.cases)
        stashes.clear()
        stashes.putAll(other.stashes)
        puzzles.clear()
        for ((box, slots) in other.puzzles) puzzles[box] = slots.copyOf()
        lightBox = other.lightBox?.copy()
    }

    fun copy(): TrailProgress = TrailProgress().also { it.replaceWith(this) }
}

/**
 * A save's `trails`: `{"easy": {"step": "map:107", "length": 3, "done": ["cryptic:52"]}, "completed": {"easy": 12},
 * "cases": [30906], "stashes": {"34737": [1635, 1654]}, "puzzles": {"19887": [3620, -1, ...]}, "lightbox": {"switches":
 * [...], "lit": 123}}`.
 */
object TrailSaves {
    /** The most steps any trail has: a save claiming more is cut to this. */
    private const val MAX_LENGTH = 8

    /** The most items a STASH unit holds: one per equipment slot. */
    private const val MAX_STASH = TrailOutfit.EQUIPMENT_SLOTS

    fun toJson(progress: TrailProgress): JsonObject = buildJsonObject {
        for ((tier, trail) in progress.all()) {
            putJsonObject(tier.key) {
                put("step", trail.step)
                put("length", trail.length)
                putJsonArray("done") { trail.done.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }
        val counts = TrailTier.entries.filter { progress.completed(it) > 0 }
        if (counts.isNotEmpty()) putJsonObject(COMPLETED) { for (tier in counts) put(tier.key, progress.completed(tier)) }
        if (progress.cases.isNotEmpty()) putJsonArray(CASES) { progress.cases.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        if (progress.puzzles.isNotEmpty()) putJsonObject(PUZZLES) {
            for ((box, slots) in progress.puzzles.toSortedMap()) putJsonArray(box.toString()) { slots.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
        progress.lightBox?.let { box ->
            putJsonObject(LIGHT_BOX) {
                putJsonArray("switches") { box.switches.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                put("lit", box.lit)
            }
        }
        if (progress.stashes.isNotEmpty()) putJsonObject(STASHES) {
            for ((loc, items) in progress.stashes.toSortedMap()) putJsonArray(loc.toString()) { items.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
    }

    private const val COMPLETED = "completed"
    private const val CASES = "cases"
    private const val STASHES = "stashes"
    private const val PUZZLES = "puzzles"
    private const val LIGHT_BOX = "lightbox"

    fun fromJson(json: JsonObject): TrailProgress {
        val progress = TrailProgress()
        json[COMPLETED]?.jsonObject?.forEach { (key, value) ->
            TrailTier.byKey(key)?.let { progress.setCompleted(it, value.jsonPrimitive.int) }
        }
        json[CASES]?.jsonArray?.forEach { progress.cases += it.jsonPrimitive.int }
        json[PUZZLES]?.jsonObject?.forEach { (box, slots) ->
            val id = box.toIntOrNull() ?: return@forEach
            val pieces = slots.jsonArray.map { it.jsonPrimitive.int }
            // A save with a wrong count is dropped, and the box scrambled afresh when next opened.
            if (pieces.size == TrailBoxes.SLOTS) progress.puzzles[id] = pieces.toIntArray()
        }
        json[LIGHT_BOX]?.jsonObject?.let { box ->
            val switches = box["switches"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty()
            val lit = box["lit"]?.jsonPrimitive?.int
            if (switches.size == TrailBoxes.SWITCHES && lit != null) progress.lightBox = LightBoxState(switches.toIntArray(), lit and TrailBoxes.ALL_LIT)
        }
        json[STASHES]?.jsonObject?.forEach { (loc, items) ->
            val id = loc.toIntOrNull() ?: return@forEach
            progress.stashes[id] = items.jsonArray.map { it.jsonPrimitive.int }.take(MAX_STASH)
        }
        for ((key, value) in json) {
            val tier = TrailTier.byKey(key) ?: continue
            val entry = value.jsonObject
            val done = entry["done"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.take(MAX_LENGTH).toMutableList()
            progress[tier] = Trail(entry.getValue("step").jsonPrimitive.content, entry.getValue("length").jsonPrimitive.int.coerceIn(1, MAX_LENGTH), done)
        }
        return progress
    }
}
