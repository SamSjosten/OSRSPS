package osrsps.game.combat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjType
import osrsps.game.ObjTypes

/** Stand-ins for every item the drop file names, so tests can read it without the cache. */
object DropTestItems {
    /** Stackable in the cache: coins, runes, bolts, arrows and javelins. */
    private val stackable = setOf(995, 554, 555, 557, 558, 559, 561, 562, 825, 877, 882)

    fun objTypes(): ObjTypes {
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val items = HashMap<Int, ObjType>()
        fun collect(obj: JsonObject) {
            obj["item"]?.let { id ->
                val i = id.jsonPrimitive.int
                items.getOrPut(i) { ObjType(i, obj["name"]!!.jsonPrimitive.content, stackable = i in stackable, tradeable = true) }
            }
            for ((_, value) in obj) {
                when (value) {
                    is JsonObject -> collect(value)
                    else -> runCatching { value.jsonArray }.getOrNull()?.forEach { runCatching { it.jsonObject }.getOrNull()?.let(::collect) }
                }
            }
        }
        collect(root)
        return ObjTypes.of(*items.values.toTypedArray())
    }
}
