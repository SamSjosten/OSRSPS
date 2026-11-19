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

/** A player's trails under way, one per tier at most. Saved. */
class TrailProgress {
    private val trails = HashMap<TrailTier, Trail>()

    operator fun get(tier: TrailTier): Trail? = trails[tier]

    operator fun set(tier: TrailTier, trail: Trail?) {
        if (trail == null) trails.remove(tier) else trails[tier] = trail
    }

    val isEmpty: Boolean get() = trails.isEmpty()

    fun all(): Map<TrailTier, Trail> = trails.toSortedMap()

    fun replaceWith(other: TrailProgress) {
        trails.clear()
        for ((tier, trail) in other.trails) trails[tier] = trail.copy()
    }

    fun copy(): TrailProgress = TrailProgress().also { it.replaceWith(this) }
}

/** A save's `trails`: `{"easy": {"step": "map:107", "length": 3, "done": ["cryptic:52"]}}`. */
object TrailSaves {
    /** The most steps any trail has: a save claiming more is cut to this. */
    private const val MAX_LENGTH = 8

    fun toJson(progress: TrailProgress): JsonObject = buildJsonObject {
        for ((tier, trail) in progress.all()) {
            putJsonObject(tier.key) {
                put("step", trail.step)
                put("length", trail.length)
                putJsonArray("done") { trail.done.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }
    }

    fun fromJson(json: JsonObject): TrailProgress {
        val progress = TrailProgress()
        for ((key, value) in json) {
            val tier = TrailTier.byKey(key) ?: continue
            val entry = value.jsonObject
            val done = entry["done"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.take(MAX_LENGTH).toMutableList()
            progress[tier] = Trail(entry.getValue("step").jsonPrimitive.content, entry.getValue("length").jsonPrimitive.int.coerceIn(1, MAX_LENGTH), done)
        }
        return progress
    }
}
