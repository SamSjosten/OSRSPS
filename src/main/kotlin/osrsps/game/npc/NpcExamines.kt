package osrsps.game.npc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * NPC examine texts by type id. The cache has none (live sends them from the server when Examine is clicked), so they
 * come from `npc-examines.json` in the resources, each with its source:
 *
 * ```
 * { "examines": [ { "npc": 3105, "text": "Servant of the Duke of Lumbridge.", "source": "OSRS Wiki, Hans" } ] }
 * ```
 *
 * Keyed by id rather than name, as types that share a name can have different texts (the wiki lists three cows).
 */
class NpcExamines(private val texts: Map<Int, String>) {
    operator fun get(id: Int): String? = texts[id]

    val size: Int get() = texts.size

    companion object {
        val NONE = NpcExamines(emptyMap())

        fun load(): NpcExamines {
            val text = NpcExamines::class.java.getResource("/npc-examines.json")?.readText()
                ?: error("No npc-examines.json in the resources")
            return parse(text)
        }

        fun parse(text: String): NpcExamines {
            val texts = HashMap<Int, String>()
            Json.parseToJsonElement(text).jsonObject["examines"]!!.jsonArray.forEachIndexed { i, element ->
                val entry = element.jsonObject
                val id = entry["npc"]?.jsonPrimitive?.int
                val examine = entry["text"]?.jsonPrimitive?.content
                val source = entry["source"]?.jsonPrimitive?.content
                require(id != null && !examine.isNullOrBlank() && !source.isNullOrBlank()) {
                    "npc-examines.json: entry ${i + 1} needs an npc id, a text and a source"
                }
                require(texts.put(id, examine) == null) { "npc-examines.json: NPC $id is listed twice" }
            }
            return NpcExamines(texts)
        }
    }
}
