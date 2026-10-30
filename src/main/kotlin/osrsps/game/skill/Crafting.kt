package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.FrameSlot
import osrsps.game.GameLines
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Crafting's words, in the dialogue file. */
object CraftingLines : Lines("crafting", "Crafting") {
    val SPIN = line("spin", "Ball of wool and Bow string: the message on spinning one", "item")
    val LEVEL = line("level", "the message when the item needs a higher level", "level", "item")
    val LEATHER = line("leather", "Leather gloves and the other leather and d'hide items: the message on making one", "item")
    val THREAD_USED = line("thread_used", "Thread: the message when a reel of thread is used up")
    val CUT = line("cut", "Opal and the other gems: the message on cutting one", "item")
    val CRUSHED = line("crushed", "Opal, Jade and Red topaz: the message on crushing one", "item")
    val JEWELLERY = line("jewellery", "Gold ring and the other gold jewellery: the message on making one", "item")
    val STRING_AMULET = line("string_amulet", "Gold amulet and the others: the message on stringing one", "item")
    val STUDS = line("studs", "Studded body and Studded chaps: the message on adding the studs", "item")
}

/**
 * A furnace interface that makes jewellery from [bar]: crafting_gold or silver_crafting. The client lays it out from the
 * inventory; [items] are by their button, and the quantity buttons make_1 to make_some start at [quantityFirst].
 */
class JewelleryInterface(val id: Int, val bar: Int, val quantityFirst: Int, val moulds: List<Int>, val items: Map<Int, MakeRecipe>)

/**
 * crafting.json: what a spinning wheel spins, in the Make-X box's order; what a needle makes from each kind of leather;
 * steel studs by the leather item they go on; what a chisel cuts, by uncut gem; gold jewellery by its button at a
 * furnace; amulets strung with wool.
 */
class CraftingData(
    val spinning: List<MakeRecipe>,
    val needle: Int = -1,
    val leather: Map<Int, List<MakeRecipe>> = emptyMap(),
    val studs: Int = -1,
    val studded: Map<Int, MakeRecipe> = emptyMap(),
    val chisel: Int = -1,
    val gems: Map<Int, MakeRecipe> = emptyMap(),
    /** Gold's furnace interface, then silver's. */
    val jewellery: List<JewelleryInterface> = emptyList(),
    val wool: Int = -1,
    /** By unstrung amulet. */
    val amulets: Map<Int, MakeRecipe> = emptyMap(),
) {
    companion object {
        const val RESOURCE = "/skills/crafting.json"

        fun load(): CraftingData {
            val text = CraftingData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): CraftingData {
            val root = Json.parseToJsonElement(text).jsonObject
            val spinning = root.getValue("spinning").jsonObject
            val spun = spinning.getValue("recipes").jsonArray.map { it.jsonObject }.map { recipe ->
                MakeRecipe(
                    "spin:${recipe.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, recipe.int("product"), 1,
                    recipe.int("level"), recipe.int("xpTenths"), listOf(ObjCount(recipe.int("input"), 1)), emptyList(),
                    spinning.int("anim"), spinning.int("first"), spinning.int("interval"), CraftingLines.SPIN, CraftingLines.LEVEL,
                )
            }
            require(spun.map { it.inputs[0].obj }.toSet().size == spun.size) { "A spinning input is listed twice" }
            val leather = root.getValue("leather").jsonObject
            val needle = leather.int("needle")
            val thread = WearingMaterial(leather.int("thread"), leather.int("threadUses"), THREAD_CHARGE, CraftingLines.THREAD_USED)
            val hides = LinkedHashMap<Int, List<MakeRecipe>>()
            for (hide in leather.getValue("hides").jsonArray.map { it.jsonObject }) {
                val id = hide.int("leather")
                hides[id] = hide.getValue("products").jsonArray.map { it.jsonObject }.map { item ->
                    MakeRecipe(
                        "leather:${item.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, item.int("obj"), 1,
                        item.int("level"), item.int("xpTenths"), listOf(ObjCount(id, item.int("leather"))), listOf(needle),
                        leather.int("anim"), leather.int("first"), hide.int("interval"), CraftingLines.LEATHER, CraftingLines.LEVEL,
                        wears = thread,
                    )
                }
            }
            val studs = root.getValue("studs").jsonObject
            val studId = studs.int("studs")
            val studded = studs.getValue("items").jsonArray.map { it.jsonObject }.associate { item ->
                item.int("item") to MakeRecipe(
                    "studs:${item.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, item.int("product"), 1,
                    item.int("level"), item.int("xpTenths"), listOf(ObjCount(item.int("item"), 1), ObjCount(studId, 1)), emptyList(),
                    studs.int("anim"), studs.int("first"), studs.int("interval"), CraftingLines.STUDS, CraftingLines.LEVEL,
                )
            }
            val gems = root.getValue("gems").jsonObject
            val chisel = gems.int("chisel")
            val cuts = gems.getValue("gems").jsonArray.map { it.jsonObject }.associate { gem ->
                val crush = gem["crush"]?.jsonObject?.let {
                    MakeChance(it.int("low"), it.int("high"), gems.int("crushed"), it.int("xpTenths"), CraftingLines.CRUSHED)
                }
                gem.int("uncut") to MakeRecipe(
                    "cut:${gem.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, gem.int("cut"), 1, gem.int("level"),
                    gem.int("xpTenths"), listOf(ObjCount(gem.int("uncut"), 1)), listOf(chisel), gem.int("anim"),
                    gems.int("first"), gems.int("interval"), CraftingLines.CUT, CraftingLines.LEVEL, chance = crush,
                )
            }
            val jewellery = root.getValue("jewellery").jsonArray.map { it.jsonObject }.map { furnace ->
                val bar = furnace.int("bar")
                val moulds = furnace.getValue("moulds").jsonObject.mapValues { it.value.jsonPrimitive.int }
                val pieces = furnace.getValue("items").jsonArray.map { it.jsonObject }.associate { item ->
                    val gem = item.getValue("gem").jsonPrimitive.intOrNull
                    item.int("component") to MakeRecipe(
                        "jewellery:${item.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, item.int("product"), 1,
                        item.int("level"), item.int("xpTenths"), listOfNotNull(ObjCount(bar, 1), gem?.let { ObjCount(it, 1) }),
                        listOf(moulds.getValue(item.getValue("mould").jsonPrimitive.content)), furnace.int("anim"),
                        furnace.int("first"), furnace.int("interval"), CraftingLines.JEWELLERY, CraftingLines.LEVEL,
                    )
                }
                JewelleryInterface(furnace.int("interface"), bar, furnace.int("quantityFirst"), moulds.values.distinct(), pieces)
            }
            val stringing = root.getValue("amuletStringing").jsonObject
            val wool = stringing.int("wool")
            val amulets = stringing.getValue("amulets").jsonArray.map { it.jsonObject }.associate { amulet ->
                amulet.int("unstrung") to MakeRecipe(
                    "string:${amulet.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, amulet.int("strung"), 1,
                    stringing.int("level"), stringing.int("xpTenths"), listOf(ObjCount(amulet.int("unstrung"), 1), ObjCount(wool, 1)),
                    emptyList(), NO_ANIM, stringing.int("first"), stringing.int("interval"), CraftingLines.STRING_AMULET,
                    CraftingLines.LEVEL,
                )
            }
            return CraftingData(spun, needle, hides, studId, studded, chisel, cuts, jewellery, wool, amulets)
        }

        /** The thread uses so far, in [osrsps.game.Charges]. */
        const val THREAD_CHARGE = "thread_uses"

        /** Guessed: stringing an amulet plays no animation. */
        const val NO_ANIM = -1

        private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
    }
}

/**
 * Crafting: Spin on a spinning wheel opens the Make-X box over what the player holds to spin; wool or flax used on a
 * wheel opens it over that alone. A needle on leather, hard leather or dragon leather opens it over what that leather
 * makes, with thread held and used up every 5 items; steel studs on a leather body or chaps stud it. A chisel on an
 * uncut gem cuts it, opal, jade and red topaz sometimes crushing. A gold bar or a mould used on a furnace opens the gold
 * jewellery interface, whose item buttons make rings, necklaces, amulets and bracelets with the quantity buttons'
 * count (kept in the same varp as the anvil's); a silver bar or silver mould opens the silver one (opal, jade and topaz
 * jewellery, symbols, the sickle, the tiara); a ball of wool strings an amulet. The making is [Making]'s. Later steps
 * add glass and pottery.
 *
 * Sources: the OSRS Wiki's Ball of wool, Bow string, Leather, Thread, the d'hide, the studded, the gem and the Jewellery pages (read
 * 2026-10-30), see crafting.json. Guessed: Spin with nothing to spin saying Nothing interesting happens (as cooking's
 * Cook does), the box opening even for one, the spinning animation, the thread counter living on the player rather than
 * the item, and the messages' arguments. Out: sinew, magic roots, yak hair and the other members' spinning; d'hide
 * shields, snakeskin and the costume needle.
 */
class Crafting(private val world: World, val data: CraftingData) {
    fun register() {
        for (wheel in wheels(world)) {
            val option = wheel.ops.indexOfFirst { it == SPIN } + 1
            world.handlers.onLoc(wheel.id, option) {
                val held = data.spinning.filter { it.times(player.inventory) > 0 }
                if (held.isEmpty()) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                world.making.choose(this, held)
            }
            for (recipe in data.spinning) {
                world.handlers.onObjOnLoc(wheel.id, recipe.inputs[0].obj) { world.making.choose(this, listOf(recipe)) }
            }
        }
        for ((hide, recipes) in data.leather) {
            world.handlers.onObjOnObj(data.needle, hide) { world.making.choose(this, recipes) }
        }
        for ((item, recipe) in data.studded) {
            world.handlers.onObjOnObj(data.studs, item) { world.making.choose(this, listOf(recipe)) }
        }
        for ((uncut, recipe) in data.gems) {
            world.handlers.onObjOnObj(data.chisel, uncut) { world.making.choose(this, listOf(recipe)) }
        }
        val opens = data.jewellery.flatMap { it.moulds + it.bar }.toSet()
        for (furnace in Smithing.FURNACES) {
            for (obj in opens) world.handlers.onObjOnLoc(furnace, obj) { openJewellery(obj) }
        }
        for (furnace in data.jewellery) {
            for (component in furnace.items.keys) {
                world.handlers.onButton(furnace.id, component) { op -> makeJewellery(furnace, op.componentId) }
            }
            // As the anvil's: smithing's quantity buttons are 3 to 8, in the same order.
            for ((component, quantity) in Smithing.QUANTITY_BUTTONS) {
                world.handlers.onButton(furnace.id, component - Smithing.QUANTITY_BUTTONS.keys.min() + furnace.quantityFirst) {
                    if (player.modals[FrameSlot.MAINMODAL] != furnace.id || quantity == Smithing.QUANTITY_SOME) return@onButton
                    player.vars[Smithing.VARP_QUANTITY] =
                        if (quantity == Smithing.QUANTITY_X) countDialog().coerceIn(0, Smithing.MAX_QUANTITY) else quantity
                }
            }
        }
        for ((unstrung, recipe) in data.amulets) {
            world.handlers.onObjOnObj(data.wool, unstrung) { world.making.choose(this, listOf(recipe)) }
        }
    }

    /**
     * A bar or a mould used on a furnace: the interface for that bar, or for a mould only silver uses; for a mould both
     * use, gold's unless the player holds silver and no gold (guessed). It draws itself from the inventory.
     */
    private fun ScriptContext.openJewellery(used: Int) {
        val (gold, silver) = data.jewellery
        val furnace = when {
            used == gold.bar -> gold
            used == silver.bar || used !in gold.moulds -> silver
            player.inventory.count(silver.bar) > 0 && player.inventory.count(gold.bar) == 0L -> silver
            else -> gold
        }
        world.interfaces.sendVarpNow(player, Smithing.VARP_QUANTITY)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, furnace.id)
    }

    /** An item clicked on a jewellery interface: as many as the quantity buttons say. */
    private fun ScriptContext.makeJewellery(furnace: JewelleryInterface, component: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != furnace.id) return
        val recipe = furnace.items[component] ?: return
        // Guessed, as at the anvil: a quantity of 0 (X cancelled) makes one.
        val quantity = player.vars[Smithing.VARP_QUANTITY].takeIf { it > 0 } ?: 1
        world.interfaces.closeModals(player)
        world.making.start(this, recipe, quantity)
    }

    companion object {
        const val SPIN = "Spin"

        /** crafting_gold and silver_crafting (osrs-dumps, rev 241). */
        const val JEWELLERY_INTERFACE = 446
        const val SILVER_INTERFACE = 6

        /** Lumbridge Castle's, on the first floor at 3209, 3212. */
        const val LUMBRIDGE_WHEEL = 14889

        /** Every loc the cache calls a spinning wheel that has a Spin option. */
        fun wheels(world: World) = world.locs.types.all.filter { it.name.equals("Spinning wheel", ignoreCase = true) && SPIN in it.ops }
    }
}
