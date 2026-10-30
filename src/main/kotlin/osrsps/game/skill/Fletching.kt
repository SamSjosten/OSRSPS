package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Fletching's words, in the dialogue file. */
object FletchingLines : Lines("fletching", "Fletching") {
    val SHAFTS = line("shafts", "Arrow shaft: the message on cutting logs into them", "amount")
    val JAVELIN_SHAFTS = line("javelin_shafts", "Javelin shafts: the message on cutting logs into them", "amount")
    val BOW = line("bow", "Shortbow (u), Longbow (u) and the other unstrung bows: the message on cutting one", "item")
    val STOCK = line("stock", "Wooden stock and the other crossbow stocks: the message on cutting one", "item")
    val LEVEL = line("level", "the message when the item needs a higher level", "level", "item")

    /** The line called [name] in fletching.json. */
    fun named(name: String): Line = lines.firstOrNull { it.key == "$prefix.$name" } ?: error("No fletching line '$name'")
}

/** fletching.json: what a knife makes from each kind of log, by log, in the Make-X box's order. */
class FletchingData(val knife: Int, val knifeOnLogs: Map<Int, List<MakeRecipe>>) {
    companion object {
        const val RESOURCE = "/skills/fletching.json"

        fun load(): FletchingData {
            val text = FletchingData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): FletchingData {
            val root = Json.parseToJsonElement(text).jsonObject
            val knife = root.int("knife")
            val anim = root.int("anim")
            val first = root.int("first")
            val interval = root.int("interval")
            val logs = LinkedHashMap<Int, List<MakeRecipe>>()
            for (log in root.getValue("knifeOnLogs").jsonArray.map { it.jsonObject }) {
                val id = log.int("log")
                val products = log.getValue("products").jsonArray.map { it.jsonObject }.map { product ->
                    MakeRecipe(
                        key = "${log.getValue("name").jsonPrimitive.content}:${product.getValue("name").jsonPrimitive.content}",
                        skill = Skill.FLETCHING,
                        product = product.int("obj"),
                        amount = product.int("amount"),
                        level = product.int("level"),
                        xpTenths = product.int("xpTenths"),
                        inputs = listOf(ObjCount(id, 1)),
                        tools = listOf(knife),
                        anim = anim,
                        first = first,
                        interval = interval,
                        made = FletchingLines.named(product.getValue("message").jsonPrimitive.content),
                        tooLow = FletchingLines.LEVEL,
                    )
                }
                require(logs.put(id, products) == null) { "Log $id is listed twice" }
            }
            return FletchingData(knife, logs)
        }

        private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
    }
}

/**
 * Fletching: a knife on logs (or logs on a knife) opens the Make-X box over what those logs make, and the choice is
 * made through [Making]. Later steps add stringing, arrows and darts.
 *
 * Sources: the OSRS Wiki's Fletching, Arrow shaft, Javelin shafts, bow (u) and stock pages (read 2026-10-30) for the
 * levels, XP, amounts and ticks (see fletching.json). Guessed: the box always opening, even for one log, and its order.
 * Out: the redwood shield, and the crossbows and javelins the stocks and javelin shafts go into (the knife makes them,
 * as on live, so the box matches).
 */
class Fletching(private val world: World, val data: FletchingData) {
    fun register() {
        for ((log, recipes) in data.knifeOnLogs) {
            world.handlers.onObjOnObj(data.knife, log) { world.making.choose(this, recipes) }
        }
    }
}
