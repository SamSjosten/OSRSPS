package osrsps.game.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * The examine texts of NPCs and locs, a key each (`examine.npc_<id>`, `examine.loc_<id>`), and of notes ([NOTE]). Live sends them from the
 * server, as the cache has none, and they are Jagex's words, so like the dialogue they are in the dialogue file, outside
 * git (Sam, 2026-10-25); one with no words shows its key. Which types have one is in `examines.json` in the resources,
 * by type id, as types that share a name can have different texts (the Wiki lists three cows).
 */
object Examines : Lines("examine", "Examine") {
    private val npcs = HashMap<Int, Line>()
    private val locs = HashMap<Int, Line>()

    /** Every noted item's examine: the cache has items' own texts but none for notes. */
    val NOTE = line("note", "Any noted item: Bank note page, infobox")

    init {
        val json = Examines::class.java.getResource("/examines.json")?.readText() ?: error("No examines.json in the resources")
        val root = Json.parseToJsonElement(json).jsonObject
        for (entry in root.entries("npcs")) {
            val id = entry.id("npc")
            val also = entry["also"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty()
            val name = entry.name()
            val ids = (listOf(id) + also).joinToString(", ")
            val line = line("npc_$id", "$name (NPC $ids): ${entry.wiki(name)}")
            for (each in listOf(id) + also) require(npcs.put(each, line) == null) { "examines.json: NPC $each is listed twice" }
        }
        for (entry in root.entries("locs")) {
            val id = entry.id("loc")
            val also = entry["also"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty()
            val name = entry.name()
            val ids = (listOf(id) + also).joinToString(", ")
            val line = line("loc_$id", "$name (loc $ids): ${entry.wiki(name)}")
            for (each in listOf(id) + also) require(locs.put(each, line) == null) { "examines.json: loc $each is listed twice" }
        }
    }

    /** The examine line of NPC type [id], the one the client sends, or null if it has none yet. */
    fun npc(id: Int): Line? = npcs[id]

    /** The examine line of loc type [id], the one the player sees, or null if it has none yet. */
    fun loc(id: Int): Line? = locs[id]

    private fun JsonObject.entries(name: String) = this[name]?.jsonArray?.map { it.jsonObject } ?: error("examines.json: no $name")

    private fun JsonObject.id(key: String) = this[key]?.jsonPrimitive?.int ?: error("examines.json: an entry with no $key id")

    private fun JsonObject.name() = this["name"]?.jsonPrimitive?.content ?: error("examines.json: an entry with no name")

    /** Where on the Wiki the text is: the infobox of the page named after it, unless the entry says. */
    private fun JsonObject.wiki(name: String) = this["wiki"]?.jsonPrimitive?.content ?: "$name page, infobox"
}
