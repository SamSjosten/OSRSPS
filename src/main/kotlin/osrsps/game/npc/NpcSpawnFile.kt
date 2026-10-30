package osrsps.game.npc

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.NpcTypes

private val logger = KotlinLogging.logger {}

/**
 * NPC spawn files: JSON in the resources' `npc-spawns` folder, one file per area. NPC spawns aren't in the cache, so
 * each one says where it came from. A file looks like:
 *
 * ```
 * { "spawns": [
 *   { "npc": "cow", "x": 3254, "z": 3255, "wander": 5, "source": "OSRS Wiki, Cow, Lumbridge cow field" },
 *   { "npc": 3105, "x": 3221, "z": 3219, "level": 0, "direction": "S", "patrol": [[3221, 3219], [3221, 3228, 10]],
 *     "source": "..." }
 * ] }
 * ```
 *
 * `npc` is a type id, a gameval name (`cow2_calf`) or a menu name. `level` defaults to 0, `direction` to south and
 * `wander` to 0 (standing still). `patrol` lists stops walked to in turn instead of wandering: `[x, z]`, or
 * `[x, z, wait]` to stand `wait` ticks there on arriving. `restrict` is `indoors`, `outdoors` or `blocked` (see [MoveRestrict]).
 * `source` is required.
 * Other keys, such as a file-level `about`, are ignored.
 */
object NpcSpawnFile {
    /** The spawn files the server loads, in this order. Add an area's file here. */
    val FILES = listOf("lumbridge.json", "lumbridge-castle.json", "lumbridge-farms.json", "lumbridge-ducks.json", "lumbridge-east.json")

    /** What a file gave: the spawns, and the NPC names the cache doesn't know (skipped). */
    class Result(val spawns: List<NpcSpawn>, val unknown: List<String>)

    /** Reads every file in [FILES] from the resources. */
    fun loadAll(types: NpcTypes): List<NpcSpawn> {
        val spawns = ArrayList<NpcSpawn>()
        for (file in FILES) {
            val text = NpcSpawnFile::class.java.getResource("/npc-spawns/$file")?.readText()
                ?: error("No NPC spawn file $file in the resources")
            val result = parse(text, types, file)
            if (result.unknown.isNotEmpty()) {
                logger.warn { "$file: skipped ${result.unknown.size} spawns of NPCs the cache doesn't have: ${result.unknown.distinct().joinToString()}" }
            }
            spawns += result.spawns
        }
        return spawns
    }

    /** Parses one file. A malformed entry throws, naming [fileName] and the entry; an unknown NPC is only skipped. */
    fun parse(text: String, types: NpcTypes, fileName: String = "spawn file"): Result {
        val root = Json.parseToJsonElement(text).jsonObject
        val entries = root["spawns"]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"spawns\" list")
        val spawns = ArrayList<NpcSpawn>()
        val unknown = ArrayList<String>()
        entries.forEachIndexed { i, element ->
            try {
                val entry = element.jsonObject
                val npc = entry.required("npc").jsonPrimitive.content
                val type = types.lookup(npc)
                if (type == null) {
                    unknown += npc
                    return@forEachIndexed
                }
                spawns += NpcSpawn(
                    type = type,
                    x = entry.required("x").jsonPrimitive.int,
                    z = entry.required("z").jsonPrimitive.int,
                    level = entry["level"]?.jsonPrimitive?.int ?: 0,
                    direction = entry["direction"]?.jsonPrimitive?.content?.let { text ->
                        Direction.parse(text) ?: throw IllegalArgumentException("unknown direction '$text'")
                    } ?: Direction.SOUTH,
                    wander = entry["wander"]?.jsonPrimitive?.int ?: 0,
                    patrol = entry["patrol"]?.jsonArray?.map(::stop).orEmpty(),
                    restrict = entry["restrict"]?.jsonPrimitive?.content?.let { text ->
                        MoveRestrict.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                            ?: throw IllegalArgumentException("unknown restrict '$text'")
                    } ?: MoveRestrict.NORMAL,
                    source = entry.required("source").jsonPrimitive.content.ifBlank {
                        throw IllegalArgumentException("blank source")
                    },
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("$fileName: spawn ${i + 1}: ${e.message}", e)
            }
        }
        return Result(spawns, unknown)
    }

    private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")

    private fun stop(element: JsonElement): PatrolStop {
        val numbers = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull }
        require(numbers != null && numbers.size in 2..3 && numbers.all { it != null }) {
            "a patrol stop must be [x, z] or [x, z, wait], not $element"
        }
        return PatrolStop(numbers[0]!!, numbers[1]!!, numbers.getOrNull(2) ?: 0)
    }
}
