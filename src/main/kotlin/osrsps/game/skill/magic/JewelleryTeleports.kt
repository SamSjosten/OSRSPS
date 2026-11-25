package osrsps.game.skill.magic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.EquipmentTab
import osrsps.game.InvObj
import osrsps.game.ObjTypes
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.HomeTeleportLines
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** What the teleport jewellery says; its words are on the server (data/dialogue.txt), not in git. */
object JewelleryTeleportLines : Lines("jewellery_teleport", "Ring of dueling") {
    val RUB_RING = line("rub_ring", "teleporting with a ring (Ring of dueling)")
    val RUB_NECKLACE = line("rub_necklace", "teleporting with a necklace (Games necklace)")
    val RUB_AMULET = line("rub_amulet", "teleporting with an amulet; words guessed from the ring's and necklace's")
    val RUB_BRACELET = line("rub_bracelet", "teleporting with a bracelet; words guessed from the ring's and necklace's")
    val USES_LEFT = line("uses_left", "a ring of dueling or games necklace after a teleport, the uses left as a word (Ring of dueling)", "item", "count")
    val ONE_USE_LEFT = line("one_use_left", "a ring of dueling or games necklace with one use left after a teleport; words guessed from uses_left", "item")
    val CRUMBLES = line("crumbles", "a ring of dueling or games necklace after its last teleport (Ring of dueling)", "item")
    val CHARGES_LEFT = line("charges_left", "dragonstone jewellery after a teleport, the charges left as a word; words guessed", "item", "count")
    val LAST_CHARGE = line("last_charge", "dragonstone jewellery after the teleport that uses its last charge; words guessed", "item")
    val NO_CHARGES = line("no_charges", "rubbing dragonstone jewellery with no charges; words guessed", "item")
    val MENU_TITLE = line("menu_title", "the title of the menu rubbing the jewellery opens; words guessed")
    val NOWHERE = line("nowhere", "the menu's option that teleports nowhere; words guessed")
    val MORE = line("more", "the menu's option showing the rest of the places when they don't all fit; words guessed")
    val WILDERNESS_30 = line("wilderness_30", "dragonstone jewellery rubbed above level 30 Wilderness, after home_teleport.wilderness; words guessed")
}

/** Where a piece of jewellery takes the player: its worn option's [name] (the cache's) and the [landing]. */
class JewelleryDestination(val name: String, val landing: Landing)

/**
 * One kind of teleport jewellery: its [forms] from most charges to one (one form that never runs out when [unlimited]),
 * the [empty] form the last charge leaves (-1 when the piece crumbles), what it is ([kind], for the rub message), the
 * deepest Wilderness level it works at ([wilderness]) and its [destinations], in the order of its worn options.
 */
class TeleportJewellery(
    val key: String,
    val kind: String,
    val wilderness: Int,
    val forms: List<Int>,
    val empty: Int,
    val unlimited: Boolean,
    val destinations: List<JewelleryDestination>,
) {
    /** The form one charge after [obj], or null when [obj] goes ([empty] being -1); [obj] itself when [unlimited]. */
    fun after(obj: Int): Int? {
        if (unlimited) return obj
        val index = forms.indexOf(obj)
        require(index >= 0) { "$obj isn't a form of $key" }
        return forms.getOrNull(index + 1) ?: empty.takeIf { it >= 0 }
    }

    /** The charges [obj] has: its place from the end of [forms]. */
    fun charges(obj: Int): Int = forms.size - forms.indexOf(obj)
}

/** The jewellery of `skills/jewellery-teleports.json`; see [parse]. */
class JewelleryTeleportData(val jewellery: List<TeleportJewellery>) {
    companion object {
        const val RESOURCE = "/skills/jewellery-teleports.json"

        val KINDS = setOf("ring", "necklace", "amulet", "bracelet")

        fun load(objTypes: ObjTypes): JewelleryTeleportData {
            val text = JewelleryTeleportData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * Parses
         *
         * ```
         * { "jewellery": [ { "key": "glory", "kind": "amulet", "wilderness": 30,
         *                    "forms": ["amulet_of_glory_6", ...], "empty": "amulet_of_glory",
         *                    "destinations": [ { "name": "Edgeville", "x": 3087, "z": 3496, "r": 2, "level": 0 } ] },
         *                  { "key": "eternal_glory", ..., "forms": ["amulet_of_glory_inf"], "unlimited": true, "destinationsOf": "glory" } ] }
         * ```
         *
         * Items are gameval names. A destination lands within "r" (0 when left out) of its tile on "level" (0 when left
         * out); "destinationsOf" borrows an earlier entry's. Each destination's name must be the worn option the cache
         * gives every charged form in that place (params 451 on). Jewellery whose forms the cache lacks is skipped (a
         * test's handful of objs).
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): JewelleryTeleportData {
            val root = Json.parseToJsonElement(text).jsonObject
            val list = ArrayList<TeleportJewellery>()
            val byKey = HashMap<String, List<JewelleryDestination>>()
            root.required("jewellery").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val key = entry.required("key").jsonPrimitive.content
                    val kind = entry.required("kind").jsonPrimitive.content.also { require(it in KINDS) { "kind $it" } }
                    val wilderness = entry.required("wilderness").jsonPrimitive.int
                    val destinations = entry["destinationsOf"]?.let { byKey[it.jsonPrimitive.content] ?: throw IllegalArgumentException("no earlier \"${it.jsonPrimitive.content}\"") }
                        ?: entry.required("destinations").jsonArray.map { destination(it.jsonObject) }
                    require(destinations.isNotEmpty()) { "no destinations" }
                    byKey[key] = destinations
                    val formNames = entry.required("forms").jsonArray.map { it.jsonPrimitive.content }
                    val unlimited = entry["unlimited"]?.jsonPrimitive?.boolean ?: false
                    require(!unlimited || formNames.size == 1) { "unlimited jewellery has one form" }
                    val forms = formNames.map { objTypes.find(it)?.id ?: return@forEachIndexed }
                    val empty = entry["empty"]?.jsonPrimitive?.content?.let { objTypes.find(it)?.id ?: return@forEachIndexed } ?: -1
                    for (form in forms) {
                        val type = objTypes[form]!!
                        destinations.forEachIndexed { d, destination ->
                            val option = type.stringParam(EquipmentTab.PARAM_WORN_OP1 + d)
                            require(option == destination.name) { "${type.internalName}'s worn option ${d + 1} is $option, not ${destination.name}" }
                        }
                    }
                    list += TeleportJewellery(key, kind, wilderness, forms, empty, unlimited, destinations)
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: jewellery entry ${i + 1}: ${e.message}", e)
                }
            }
            val forms = list.flatMap { it.forms + listOf(it.empty).filter { e -> e >= 0 } }
            require(forms.size == forms.toSet().size) { "$fileName: an item is listed twice" }
            return JewelleryTeleportData(list)
        }

        private fun destination(entry: JsonObject): JewelleryDestination {
            val x = entry.required("x").jsonPrimitive.int
            val z = entry.required("z").jsonPrimitive.int
            val r = entry["r"]?.jsonPrimitive?.int ?: 0
            val level = entry["level"]?.jsonPrimitive?.int ?: 0
            require(level in 0..3 && r >= 0) { "level $level, r $r" }
            return JewelleryDestination(entry.required("name").jsonPrimitive.content, Landing(level, listOf(intArrayOf(x - r, z - r, x + r, z + r))))
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}

/**
 * The teleport jewellery (Magic utility step U4): the amulet of glory (with its trimmed and eternal forms), ring of
 * dueling, games necklace, combat bracelet, skills necklace and ring of wealth (with its imbued form). Rub in the
 * inventory, or Rub on the worn item when it has no charges, opens a menu of its places; a charged worn item has one
 * option for each place. Each teleport uses a charge: the piece becomes its next form, then its uncharged form
 * (dragonstone jewellery) or crumbles (the ring of dueling and games necklace).
 *
 * Verified on the OSRS Wiki (each item's page, read 2026-11-19): the places and their tiles (the Wiki's tile, within its
 * radius where it gives one), the charges, the uncharged forms, the rub message and charges message of the ring of
 * dueling and games necklace and their crumbling, and that the dragonstone pieces work up to level 30 Wilderness while
 * the others stop above 20. The places' names and their order are the cache's worn options (params 451 on), which the
 * data file is checked against.
 *
 * Guessed: the teleport's animation, graphic and timing are the standard spellbook's ([Teleports.CAST_ANIM], 3 ticks);
 * the amulet's and bracelet's rub messages, the one-use-left message, the dragonstone charges messages, the menu's
 * title and its Nowhere option (left off when the places fill the menu), places past the fifth behind a More option,
 * and the refusal above level 30; the messages coming as the teleport starts; and the Corporeal Beast's cave (plane 2,
 * the only one whose map is walkable there) and Tears of Guthix (plane 2) planes. Every place is open as in a
 * post-quest world: the Wiki's Tears of Guthix, Miscellania and Dondakan quests, Zeah for Wintertodt and the Colosseum's
 * Hero title for Fortis Colosseum all count as done, and the Farming Guild teleport lands at the Wiki's tile whatever
 * the Farming level (the Wiki puts a player under 45 outside, at a tile it doesn't give).
 *
 * Not built here: the ring of wealth's Features, Boss Log and Coin Collection, and its coin collecting and drop effects
 * (step U6), the amulet of glory's mounted form, and recharging (the Fountain of Rune's is [osrsps.game.FountainOfRune]).
 */
class JewelleryTeleports(private val world: World, private val data: JewelleryTeleportData) {
    fun register() {
        for (jewellery in data.jewellery) {
            for (form in jewellery.forms) {
                world.handlers.onObj(form, RUB) { op -> rub(jewellery, Held.Inventory(op.sub), form) }
                jewellery.destinations.forEachIndexed { d, destination ->
                    world.handlers.onWorn(form, d + 1) { op -> teleport(jewellery, Held.Worn(op.sub), form, destination) }
                }
            }
            if (jewellery.empty >= 0) {
                world.handlers.onObj(jewellery.empty, RUB) { noCharges(jewellery.empty) }
                world.handlers.onWorn(jewellery.empty, 1) { noCharges(jewellery.empty) }
            }
        }
    }

    /** Where the piece is: an inventory slot or a worn slot. */
    sealed interface Held {
        data class Inventory(val slot: Int) : Held
        data class Worn(val wearpos: Int) : Held
    }

    private fun ScriptContext.objAt(held: Held): Int? = when (held) {
        is Held.Inventory -> player.inventory[held.slot]?.id
        is Held.Worn -> player.equipment[held.wearpos]?.id
    }

    private fun ScriptContext.noCharges(obj: Int) {
        player.message(world.textFor(player)[JewelleryTeleportLines.NO_CHARGES, "item" to itemName(obj)])
    }

    private suspend fun ScriptContext.rub(jewellery: TeleportJewellery, held: Held, obj: Int) {
        if (objAt(held) != obj) return
        val destination = pick(jewellery.destinations) ?: return
        // The menu waited: the piece must still be where it was.
        if (objAt(held) != obj) return
        teleport(jewellery, held, obj, destination)
    }

    /**
     * The place picked from the menu, or null for Nowhere or a closed menu. Up to five places fill one menu (with
     * Nowhere after them when there's room); more go four to a page, each page ending with More.
     */
    private suspend fun ScriptContext.pick(destinations: List<JewelleryDestination>): JewelleryDestination? {
        val text = world.textFor(player)
        var picked: JewelleryDestination? = null
        world.dialogues.start(this) {
            val title = text(JewelleryTeleportLines.MENU_TITLE)
            if (destinations.size <= MENU_SIZE) {
                val options = destinations.map { it.name } + if (destinations.size < MENU_SIZE) listOf(text(JewelleryTeleportLines.NOWHERE)) else emptyList()
                picked = destinations.getOrNull(choice(*options.toTypedArray(), title = title) - 1)
            } else {
                val pages = destinations.chunked(MENU_SIZE - 1)
                var page = 0
                while (picked == null) {
                    val shown = pages[page]
                    val chosen = choice(*(shown.map { it.name } + text(JewelleryTeleportLines.MORE)).toTypedArray(), title = title)
                    if (chosen !in 1..shown.size + 1) break
                    if (chosen <= shown.size) picked = shown[chosen - 1] else page = (page + 1) % pages.size
                }
            }
        }
        world.interfaces.closeChatModal(player)
        return picked
    }

    private suspend fun ScriptContext.teleport(jewellery: TeleportJewellery, held: Held, obj: Int, destination: JewelleryDestination) {
        if (objAt(held) != obj) return
        val tooDeep = if (jewellery.wilderness > Wilderness.TELEPORT_LIMIT) listOf(HomeTeleportLines.WILDERNESS, JewelleryTeleportLines.WILDERNESS_30) else listOf(HomeTeleportLines.WILDERNESS, HomeTeleportLines.WILDERNESS_2)
        val teleports = world.teleports
        if (!teleports.allowedHere(player, jewellery.wilderness, tooDeep)) return
        teleports.start(this)
        val text = world.textFor(player)
        player.message(text[rubLine(jewellery.kind)])
        val name = itemName(obj)
        val next = jewellery.after(obj)
        if (!jewellery.unlimited) {
            when (held) {
                is Held.Inventory -> player.inventory[held.slot] = next?.let { InvObj(it, 1) }
                is Held.Worn -> player.equipment[held.wearpos] = next?.let { InvObj(it, 1) }
            }
            val left = jewellery.charges(obj) - 1
            player.message(
                when {
                    left == 1 && jewellery.empty < 0 -> text[JewelleryTeleportLines.ONE_USE_LEFT, "item" to name]
                    left > 0 && jewellery.empty < 0 -> text[JewelleryTeleportLines.USES_LEFT, "item" to name, "count" to word(left)]
                    left > 0 -> text[JewelleryTeleportLines.CHARGES_LEFT, "item" to name, "count" to word(left)]
                    jewellery.empty < 0 -> text[JewelleryTeleportLines.CRUMBLES, "item" to name]
                    else -> text[JewelleryTeleportLines.LAST_CHARGE, "item" to name]
                },
            )
        }
        player.anim(Teleports.CAST_ANIM)
        player.spotanim(Teleports.CAST_SPOTANIM, Teleports.CAST_HEIGHT)
        delay(Teleports.CAST_TICKS)
        teleports.land(this, destination.landing)
        player.anim(-1)
    }

    /** The piece's name without its charges, as the messages have it: ring of dueling, amulet of glory. */
    private fun itemName(obj: Int): String =
        (world.objTypes[obj]?.name ?: "jewellery").replace(CHARGES_SUFFIX, "").lowercase()

    private fun rubLine(kind: String): Line = when (kind) {
        "ring" -> JewelleryTeleportLines.RUB_RING
        "necklace" -> JewelleryTeleportLines.RUB_NECKLACE
        "amulet" -> JewelleryTeleportLines.RUB_AMULET
        else -> JewelleryTeleportLines.RUB_BRACELET
    }

    companion object {
        /** The inventory's Rub, iop 4. */
        const val RUB = 4

        /** A chat menu's most options ([osrsps.game.dialogue.Dialogues.MAX_OPTIONS]). */
        const val MENU_SIZE = 5

        private val CHARGES_SUFFIX = Regex("""\s*\((t|i)?\d*\)$""")

        private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight")

        /** [count] as the charges messages spell it (the Wiki's "four uses left"). */
        fun word(count: Int): String = WORDS.getOrNull(count) ?: count.toString()
    }
}
