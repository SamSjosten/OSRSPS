package osrsps.game.shop

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes

private val logger = KotlinLogging.logger {}

/** One item a shop starts with: [count] of [type], one more (or less) every [restockTicks] until it is back at [count]. */
class ShopStock(val type: ObjType, val count: Int, val restockTicks: Int) {
    init {
        require(count >= 0) { "Bad stock count $count of $type" }
        require(restockTicks >= 1) { "Bad restock time $restockTicks for $type" }
    }
}

/**
 * A shop as the Wiki's shop infobox gives it: its [title], the inv the client draws it from ([invId], with its cache
 * [size]), what it sells at, buys at and changes by per item of stock (in tenths of a percent, as the Wiki's "Sells at
 * 130.0%" is 1300), whether it is a general store that buys anything tradeable, and its [stock].
 */
class ShopType(
    val key: String,
    val title: String,
    val invId: Int,
    val size: Int,
    val general: Boolean,
    val sellAt: Int,
    val buyAt: Int,
    val changePer: Int,
    val stock: List<ShopStock>,
    /** The NPCs whose Trade opens it. */
    val npcs: List<Int>,
) {
    init {
        require(stock.size <= size) { "$key has ${stock.size} items but $size slots" }
        require(stock.map { it.type.id }.toSet().size == stock.size) { "$key lists an item twice" }
    }

    /** The count the shop restocks [obj] to: its default stock, or 0 for an item it doesn't start with. */
    fun baseStock(obj: Int): Int = stock.firstOrNull { it.type.id == obj }?.count ?: 0

    fun stocks(obj: Int): Boolean = stock.any { it.type.id == obj }

    override fun toString(): String = "$title ($key)"
}

/** Every shop by key, and by the NPCs that run them, from [RESOURCE]. */
class ShopTypes(val all: List<ShopType>, /** What the loader warned about, for tests. */ val warnings: List<String> = emptyList()) {
    private val byNpc: Map<Int, ShopType> = buildMap { for (shop in all) for (npc in shop.npcs) put(npc, shop) }

    private val byKey: Map<String, ShopType> = all.associateBy { it.key }

    operator fun get(key: String): ShopType? = byKey[key]

    fun ofNpc(npc: Int): ShopType? = byNpc[npc]

    companion object {
        val NONE = ShopTypes(emptyList())

        const val RESOURCE = "/shops.json"

        fun load(npcTypes: NpcTypes, objTypes: ObjTypes): ShopTypes {
            val text = ShopTypes::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, npcTypes, objTypes).also { shops -> shops.warnings.forEach { logger.warn { "$RESOURCE: $it" } } }
        }

        /**
         * Reads the shop file: each `shops` entry has a `key`, `title`, `inv` (id) and `size`, `general`, `sellAt`,
         * `buyAt` and `changePer` in tenths of a percent, the `npcs` that run it (ids, gameval names or menu names, as
         * spawn files take) and its `stock`, each an `item` id with its `name`, `count` and `restock` ticks. An item
         * whose name the cache doesn't agree with is warned about; a shop with an item or NPC the cache lacks is left
         * out with a warning, so a test cache with few items still starts.
         */
        fun parse(text: String, npcTypes: NpcTypes, objTypes: ObjTypes, fileName: String = RESOURCE): ShopTypes {
            val root = Json.parseToJsonElement(text).jsonObject
            val warnings = ArrayList<String>()
            val shops = ArrayList<ShopType>()
            val entries = root["shops"]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"shops\" list")
            for (element in entries) {
                val entry = element.jsonObject
                val key = entry.required("key").jsonPrimitive.content
                try {
                    val missing = ArrayList<String>()
                    val stock = entry.required("stock").jsonArray.mapNotNull { line ->
                        val item = line.jsonObject
                        val id = item.required("item").jsonPrimitive.int
                        val name = item.required("name").jsonPrimitive.content
                        val type = objTypes[id]
                        if (type == null) {
                            missing += "item $id ($name)"
                            return@mapNotNull null
                        }
                        if (!type.name.equals(name, ignoreCase = true)) warnings += "$key: $id is ${type.name}, not $name"
                        ShopStock(type, item.required("count").jsonPrimitive.int, item.required("restock").jsonPrimitive.int)
                    }
                    val npcs = entry.required("npcs").jsonArray.mapNotNull { npc ->
                        val name = npc.jsonPrimitive.content
                        npcTypes.lookup(name)?.id ?: null.also { missing += "NPC $name" }
                    }
                    if (missing.isNotEmpty()) {
                        warnings += "$key left out: the cache has no ${missing.joinToString()}"
                        continue
                    }
                    require(entry.required("source").jsonPrimitive.content.isNotBlank()) { "blank source" }
                    shops += ShopType(
                        key = key,
                        title = entry.required("title").jsonPrimitive.content,
                        invId = entry.required("inv").jsonPrimitive.int,
                        size = entry.required("size").jsonPrimitive.int,
                        general = entry.required("general").jsonPrimitive.boolean,
                        sellAt = entry.required("sellAt").jsonPrimitive.int,
                        buyAt = entry.required("buyAt").jsonPrimitive.int,
                        changePer = entry.required("changePer").jsonPrimitive.int,
                        stock = stock,
                        npcs = npcs,
                    )
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: shop $key: ${e.message}", e)
                }
            }
            require(shops.map { it.key }.toSet().size == shops.size) { "$fileName: a key is used twice" }
            val npcs = shops.flatMap { it.npcs }
            require(npcs.toSet().size == npcs.size) { "$fileName: an NPC runs two shops" }
            return ShopTypes(shops, warnings)
        }

        private fun JsonObject.required(name: String) = this[name] ?: throw IllegalArgumentException("no \"$name\"")
    }
}
