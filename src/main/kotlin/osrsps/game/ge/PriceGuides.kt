package osrsps.game.ge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.sendInventory

private val logger = KotlinLogging.logger {}

/** The price guides' window titles, after "Grand Exchange: ". */
object PriceGuideLines : Lines("price_guide", "Grand Exchange price guides") {
    val RUNES = line("runes", "Murky Matt's price list's title, after \"Grand Exchange: \"")
    val LOGS = line("logs", "Relobo Blinyo's price list's title (the Wiki: \"Grand Exchange: Logs\")")
    val WEAPONS_AND_ARMOUR = line("weapons_and_armour", "Hofuthand's price list's title (the Wiki: \"Grand Exchange: Weapons and Armour\")")
    val ORES_AND_BARS = line("ores_and_bars", "Farid Morrisane's price list's title")
    val HERBS = line("herbs", "Bob Barter's price list's title")
}

/**
 * The Grand Exchange's price guides: Prices on one of the market experts opens `ge_pricelist` (237), titled by
 * script `ge_pricelist` (799) "Grand Exchange: " and the guide's name, listing the guide's items with their guide
 * prices as the counts of an inv the script is handed (osrs-dumps, rev 241: `ge_pricelist_draw` draws each count as
 * the price, 1 as "1 coin" and the largest int as "Lots!").
 *
 * The lists are the Wiki's "Market guide prices" tables, in resources/ge-price-guides.tsv. Which inv live hands the
 * script isn't known (the 241 cache names none for it): [INV] borrows `xbows_grapple_inv` (434, 100 slots), which
 * nothing here uses, as the list needs one as large as the longest guide. An item with no guide price is left out.
 */
class PriceGuides(
    private val lists: Map<Guide, List<Int>>,
    private val guidePrice: (Int) -> Long?,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    enum class Guide(val key: String) {
        RUNES("runes"), LOGS("logs"), WEAPONS_AND_ARMOUR("weapons_and_armour"), ORES_AND_BARS("ores_and_bars"), HERBS("herbs");

        val title get() = when (this) {
            RUNES -> PriceGuideLines.RUNES
            LOGS -> PriceGuideLines.LOGS
            WEAPONS_AND_ARMOUR -> PriceGuideLines.WEAPONS_AND_ARMOUR
            ORES_AND_BARS -> PriceGuideLines.ORES_AND_BARS
            HERBS -> PriceGuideLines.HERBS
        }
    }

    /** The items [guide] lists, with a guide price, as the window shows them. */
    fun items(guide: Guide): List<InvObj> = lists[guide].orEmpty().mapNotNull { id ->
        guidePrice(id)?.takeIf { it > 0 }?.let { InvObj(id, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
    }

    fun open(player: Player, guide: Guide) {
        closeModals(player)
        val inv = Inventory(INV, INV_CAPACITY)
        for ((slot, obj) in items(guide).take(INV_CAPACITY).withIndex()) inv[slot] = obj
        // The list before the script that draws it.
        sendInventory(player, inv, send)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        send(player, RunClientScript(INIT_SCRIPT, listOf(INV, words(player)[guide.title])))
    }

    companion object {
        /** ge_pricelist. */
        const val MAIN = 237

        /** ge_pricelist (the client script). */
        const val INIT_SCRIPT = 799

        /** xbows_grapple_inv, borrowed: see the class. */
        const val INV = 434
        const val INV_CAPACITY = 100

        const val RESOURCE = "/ge-price-guides.tsv"

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to ("ge_pricelist" to mapOf(0 to "universe", 2 to "list")),
        )

        /**
         * Reads resources/ge-price-guides.tsv: each name is the lowest-id tradeable item of that name that isn't a note
         * or a placeholder, ignoring case. Names with no such item are skipped (and logged).
         */
        fun load(objTypes: ObjTypes): Map<Guide, List<Int>> {
            val byName = HashMap<String, Int>()
            for (id in 0 until objTypes.size) {
                val type = objTypes[id] ?: continue
                if (!type.tradeable || type.isCert || type.isPlaceholder) continue
                byName.putIfAbsent(type.name.lowercase(), id)
            }
            val text = PriceGuides::class.java.getResource(RESOURCE)?.readText() ?: return emptyMap()
            val lists = LinkedHashMap<Guide, MutableList<Int>>()
            val missing = ArrayList<String>()
            for (line in text.lines()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val (key, name) = line.split('\t').takeIf { it.size == 2 } ?: continue
                val guide = Guide.entries.firstOrNull { it.key == key } ?: continue
                val id = byName[name.lowercase()]
                if (id == null) missing += name else lists.getOrPut(guide) { ArrayList() } += id
            }
            if (objTypes.size > 0 && missing.isNotEmpty()) logger.info { "Price guides: no tradeable item in the cache for ${missing.size} name(s): $missing" }
            return lists
        }
    }
}
