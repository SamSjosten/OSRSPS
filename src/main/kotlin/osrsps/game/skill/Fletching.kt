package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.Player
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
    val STRING = line("string", "Shortbow and the other bows: the message on stringing one", "item")
    val SETS_TITLE = line("sets_title", "Headless arrow and the arrows: the Make-X box's title, in sets of 15")
    val HEADLESS = line("headless", "Headless arrow: the message on attaching feathers to shafts", "amount")
    val ARROWS = line("arrows", "Bronze arrow and the other arrows: the message on attaching the tips", "amount", "item")
    val DARTS = line("darts", "Bronze dart and the other darts: the message on attaching feathers", "amount", "item")
    val DART_SETS_TITLE = line("dart_sets_title", "Bronze dart and the other darts: the Make-X box's title with the Make-X for darts setting on, in sets of 10; guessed to follow the arrows' title")

    /** The line called [name] in fletching.json. */
    fun named(name: String): Line = lines.firstOrNull { it.key == "$prefix.$name" } ?: error("No fletching line '$name'")
}

/**
 * fletching.json: what a knife makes from each kind of log, by log, in the Make-X box's order; stringing by unstrung bow;
 * headless arrows; arrows by arrowtips; darts by dart tips.
 */
class FletchingData(
    val knife: Int,
    val knifeOnLogs: Map<Int, List<MakeRecipe>>,
    val bowString: Int = -1,
    val stringing: Map<Int, MakeRecipe> = emptyMap(),
    val feather: Int = -1,
    val headless: MakeRecipe? = null,
    val arrows: Map<Int, MakeRecipe> = emptyMap(),
    val darts: Map<Int, MakeRecipe> = emptyMap(),
) {
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
            val stringing = root.getValue("stringing").jsonObject
            val bowString = stringing.int("bowString")
            val bows = LinkedHashMap<Int, MakeRecipe>()
            for (bow in stringing.getValue("bows").jsonArray.map { it.jsonObject }) {
                val recipe = MakeRecipe(
                    "string:${bow.getValue("name").jsonPrimitive.content}", Skill.FLETCHING, bow.int("bow"), 1, bow.int("level"),
                    bow.int("xpTenths"), listOf(ObjCount(bow.int("unstrung"), 1), ObjCount(bowString, 1)), emptyList(), bow.int("anim"),
                    stringing.int("first"), stringing.int("interval"), FletchingLines.STRING, FletchingLines.LEVEL,
                )
                require(bows.put(bow.int("unstrung"), recipe) == null) { "Unstrung bow ${bow.int("unstrung")} is listed twice" }
            }
            val arrows = root.getValue("arrows").jsonObject
            val feather = arrows.int("feather")
            val amount = arrows.int("amount")
            val headlessId = arrows.int("headless")
            fun batch(key: String, product: Int, level: Int, xp: Int, inputs: List<ObjCount>, made: Line) = MakeRecipe(
                key, Skill.FLETCHING, product, amount, level, xp, inputs, emptyList(), NO_ANIM, arrows.int("first"),
                arrows.int("interval"), made, FletchingLines.LEVEL, batch = true,
            )
            val headless = batch(
                "headless_arrow", headlessId, arrows.int("headlessLevel"), arrows.int("headlessXpTenths"),
                listOf(ObjCount(arrows.int("shaft"), 1), ObjCount(feather, 1)), FletchingLines.HEADLESS,
            )
            val tipped = LinkedHashMap<Int, MakeRecipe>()
            for (arrow in arrows.getValue("tipped").jsonArray.map { it.jsonObject }) {
                val recipe = batch(
                    arrow.getValue("name").jsonPrimitive.content, arrow.int("arrow"), arrow.int("level"), arrow.int("xpTenths"),
                    listOf(ObjCount(headlessId, 1), ObjCount(arrow.int("tips"), 1)), FletchingLines.ARROWS,
                )
                require(tipped.put(arrow.int("tips"), recipe) == null) { "Arrowtips ${arrow.int("tips")} are listed twice" }
            }
            val darts = root.getValue("darts").jsonObject
            val dartRecipes = LinkedHashMap<Int, MakeRecipe>()
            for (dart in darts.getValue("darts").jsonArray.map { it.jsonObject }) {
                val recipe = MakeRecipe(
                    dart.getValue("name").jsonPrimitive.content, Skill.FLETCHING, dart.int("dart"), darts.int("amount"),
                    dart.int("level"), dart.int("xpTenths"), listOf(ObjCount(dart.int("tips"), 1), ObjCount(darts.int("feather"), 1)),
                    emptyList(), NO_ANIM, 1, 1, FletchingLines.DARTS, FletchingLines.LEVEL, batch = true,
                )
                require(dartRecipes.put(dart.int("tips"), recipe) == null) { "Dart tips ${dart.int("tips")} are listed twice" }
            }
            return FletchingData(knife, logs, bowString, bows, feather, headless, tipped, dartRecipes)
        }

        /** Guessed: attaching feathers and tips plays no animation. */
        const val NO_ANIM = -1

        private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
    }
}

/**
 * Fletching: a knife on logs (or logs on a knife) opens the Make-X box over what those logs make; a bow string on an
 * unstrung bow strings it; feathers on arrow shafts make headless arrows and arrowtips on those make arrows, in sets of
 * up to 15 under their own box title; all made through [Making]. A feather on dart tips makes 10 darts at once, on every
 * click, with no box ([Making.makeNow]).
 *
 * Sources: the OSRS Wiki's Fletching, Arrow shaft, Javelin shafts, bow (u) and stock pages (read 2026-10-30) for the
 * levels, XP, amounts and ticks (see fletching.json), and Shortbow, Headless arrow, the arrows' and Dart's pages for the
 * rest. Guessed: the box always opening, even for one log or one bow, and its order; no animation for arrows and darts.
 * Out: the redwood shield, and the crossbows and javelins the stocks and javelin shafts go into (the knife makes them,
 * as on live, so the box matches).
 */
class Fletching(private val world: World, val data: FletchingData) {
    /**
     * The All Settings toggle "Make-X for darts and bolts" (varbit option_fletch_dart_or_bolt_makex): on, feathers on
     * dart tips open the Make-X box instead of making one set at once. Off is the default. Bolts aren't built.
     */
    private fun dartsMakeX(player: Player): Boolean =
        player.vars.types.varbit(VARBIT_DARTS_MAKEX) != null && player.vars.getVarbit(VARBIT_DARTS_MAKEX) == 1

    fun register() {
        for ((log, recipes) in data.knifeOnLogs) {
            world.handlers.onObjOnObj(data.knife, log) { world.making.choose(this, recipes) }
        }
        for ((unstrung, recipe) in data.stringing) {
            world.handlers.onObjOnObj(data.bowString, unstrung) { world.making.choose(this, listOf(recipe)) }
        }
        data.headless?.let { headless ->
            world.handlers.onObjOnObj(data.feather, headless.inputs[0].obj) {
                world.making.choose(this, listOf(headless), FletchingLines.SETS_TITLE)
            }
        }
        for ((tips, recipe) in data.arrows) {
            world.handlers.onObjOnObj(tips, recipe.inputs[0].obj) { world.making.choose(this, listOf(recipe), FletchingLines.SETS_TITLE) }
        }
        for ((tips, recipe) in data.darts) {
            world.handlers.onObjOnObj(data.feather, tips) {
                if (dartsMakeX(player)) world.making.choose(this, listOf(recipe), FletchingLines.DART_SETS_TITLE) else world.making.makeNow(this, recipe)
            }
        }
    }

    companion object {
        /** option_fletch_dart_or_bolt_makex. */
        const val VARBIT_DARTS_MAKEX = 10971
    }
}
