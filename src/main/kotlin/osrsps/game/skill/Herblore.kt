package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines

/** Herblore's words, in the dialogue file. */
object HerbloreLines : Lines("herblore", "Herblore") {
    val CLEAN = line("clean", "Grimy guam leaf and the other grimy herbs: the message on cleaning one", "input")
    val CLEAN_LEVEL = line("clean_level", "Grimy guam leaf and the others: the message when cleaning needs a higher level", "level")
    val UNFINISHED = line("unfinished", "Guam potion (unf) and the others: the message on putting the herb in the vial", "input")
    val LEVEL = line("level", "the message when the item needs a higher level", "level", "item")
    val GRIND = line("grind", "Unicorn horn dust and the other ground items: the message on grinding one", "input")
    val FILL = line("fill", "Vial of water: the message on filling a vial")
}

/** A grimy herb [grimy] that cleans to [clean] at [level] for [xpTenths]. */
data class GrimyHerb(val grimy: Int, val clean: Int, val level: Int, val xpTenths: Int)

/**
 * herblore.json: the grimy herbs by grimy obj; unfinished potions by clean herb; what a pestle and mortar grinds, by what
 * it grinds; filling a vial, and the names of the locs that fill one.
 */
class HerbloreData(
    val cleaning: Map<Int, GrimyHerb>,
    val vialOfWater: Int,
    val unfinished: Map<Int, MakeRecipe>,
    val pestle: Int,
    val grinding: Map<Int, MakeRecipe>,
    val fillVial: MakeRecipe,
    val waterSources: List<String>,
) {
    companion object {
        const val RESOURCE = "/skills/herblore.json"

        fun load(): HerbloreData {
            val text = HerbloreData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): HerbloreData {
            val root = Json.parseToJsonElement(text).jsonObject
            val cleaning = LinkedHashMap<Int, GrimyHerb>()
            for (herb in root.getValue("cleaning").jsonObject.getValue("herbs").jsonArray.map { it.jsonObject }) {
                val grimy = GrimyHerb(herb.int("grimy"), herb.int("clean"), herb.int("level"), herb.int("xpTenths"))
                require(cleaning.put(grimy.grimy, grimy) == null) { "Grimy herb ${grimy.grimy} is listed twice" }
            }
            val unf = root.getValue("unfinished").jsonObject
            val vialOfWater = unf.int("vialOfWater")
            val unfinished = LinkedHashMap<Int, MakeRecipe>()
            for (potion in unf.getValue("potions").jsonArray.map { it.jsonObject }) {
                val recipe = MakeRecipe(
                    "unf:${potion.name()}", Skill.HERBLORE, potion.int("unf"), 1, potion.int("level"), 0,
                    listOf(ObjCount(potion.int("herb"), 1), ObjCount(vialOfWater, 1)), emptyList(), unf.int("anim"),
                    unf.int("first"), unf.int("interval"), HerbloreLines.UNFINISHED, HerbloreLines.LEVEL,
                )
                require(unfinished.put(potion.int("herb"), recipe) == null) { "Herb ${potion.int("herb")} is listed twice" }
            }
            val grind = root.getValue("grinding").jsonObject
            val pestle = grind.int("pestle")
            val grinding = LinkedHashMap<Int, MakeRecipe>()
            for (item in grind.getValue("items").jsonArray.map { it.jsonObject }) {
                val recipe = MakeRecipe(
                    "grind:${item.name()}", Skill.HERBLORE, item.int("to"), 1, 1, 0, listOf(ObjCount(item.int("from"), 1)),
                    listOf(pestle), grind.int("anim"), grind.int("first"), grind.int("interval"), HerbloreLines.GRIND,
                    HerbloreLines.LEVEL, second = grind.int("second"),
                )
                require(grinding.put(item.int("from"), recipe) == null) { "Ground item ${item.int("from")} is listed twice" }
            }
            val vials = root.getValue("vials").jsonObject
            val fill = MakeRecipe(
                "fill_vial", Skill.HERBLORE, vials.int("full"), 1, 1, 0, listOf(ObjCount(vials.int("empty"), 1)), emptyList(),
                vials.int("anim"), vials.int("first"), vials.int("interval"), HerbloreLines.FILL, HerbloreLines.LEVEL,
            )
            val sources = vials.getValue("sources").jsonArray.map { it.jsonPrimitive.content }
            return HerbloreData(cleaning, vialOfWater, unfinished, pestle, grinding, fill, sources)
        }

        private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

        private fun JsonObject.name(): String = getValue("name").jsonPrimitive.content
    }
}

/**
 * Herblore up to the finished potions: Clean on a grimy herb cleans it straight away, one a click; a clean herb and a
 * vial of water, either on the other, open the Make-X box for its unfinished potion; a pestle and mortar on a unicorn
 * horn (or the other things it grinds) opens it for the dust, the first ground at once, the next 2 ticks later and the
 * rest every 3; an empty vial on a sink, a fountain or a water pump fills every vial held, one a tick. The making is
 * [Making]'s.
 *
 * Sources: the OSRS Wiki's grimy herb, unfinished potion, ground item and Vial of water pages (read 2026-10-30), see
 * herblore.json. Guessed: the box opening even for one; filling every vial held without a box; which locs count as
 * water (the cache's sinks, fountains and water pumps, not wells); the filling animation (human_pickuptable) and no
 * animation for cleaning; the messages' arguments. Out: herb sacks, the herb tar and barbarian mixes, Ashes, the
 * Zahur-style and Kandarin-diary shortcuts, and the other ground items (crushed for quests, Varrock's mud runes).
 */
class Herblore(private val world: World, val data: HerbloreData) {
    fun register() {
        for (herb in data.cleaning.values) {
            val option = world.objTypes[herb.grimy]?.iops?.indexOfFirst { it == CLEAN }?.takeIf { it >= 0 }?.plus(1) ?: continue
            world.handlers.onObj(herb.grimy, option) { op -> clean(player, op.sub, herb) }
        }
        for ((herb, recipe) in data.unfinished) {
            world.handlers.onObjOnObj(herb, data.vialOfWater) { world.making.choose(this, listOf(recipe)) }
        }
        for ((from, recipe) in data.grinding) {
            world.handlers.onObjOnObj(data.pestle, from) { world.making.choose(this, listOf(recipe)) }
        }
        for (source in waterSources()) {
            world.handlers.onObjOnLoc(source, data.fillVial.inputs[0].obj) {
                world.making.start(this, data.fillVial, data.fillVial.times(player.inventory))
            }
        }
    }

    /** The locs that fill a vial: those the cache calls one of [HerbloreData.waterSources], in any case. */
    fun waterSources(): List<Int> =
        world.locs.types.all.filter { type -> data.waterSources.any { it.equals(type.name, ignoreCase = true) } }.map { it.id }

    /** [player] cleans the grimy [herb] in [slot]. */
    private fun clean(player: Player, slot: Int, herb: GrimyHerb) {
        if (player.inventory[slot]?.id != herb.grimy) return
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.HERBLORE) < herb.level) {
            player.message(text.get(HerbloreLines.CLEAN_LEVEL, "level" to herb.level))
            return
        }
        player.inventory[slot] = InvObj(herb.clean, 1)
        player.skills.addTenths(Skill.HERBLORE, herb.xpTenths)
        player.message(text.get(HerbloreLines.CLEAN, "input" to (world.objTypes[herb.grimy]?.name ?: "item ${herb.grimy}")))
    }

    companion object {
        const val CLEAN = "Clean"

        /** Lumbridge Castle's kitchen sink, at 3205, 3215. */
        const val LUMBRIDGE_SINK = 14868
    }
}
