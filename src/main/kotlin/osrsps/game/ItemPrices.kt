package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

/**
 * Grand Exchange guide prices and buy limits by item id: a fixed snapshot of the OSRS Wiki's, kept in [FILE_NAME] in
 * the data folder, outside git, where it can be edited by hand (Sam, 2026-10-26). The guide price is the figure Examine
 * shows on live, which the Wiki's infoboxes show too; it is not the Wiki's real-time trade price.
 *
 * The server downloads the snapshot once, on the first start without the file ([loadOrDownload]); after that only
 * `::itemprices refresh` downloads it again.
 */
class ItemPrices(private val byId: Map<Int, Entry>, val snapshot: String? = null) {
    class Entry(val name: String, val price: Long?, val limit: Int?)

    val size: Int get() = byId.size

    /** [type]'s guide price, a note's being its item's; null for an item with none in the snapshot. */
    fun price(type: ObjType): Long? = byId[itemId(type)]?.price

    /** [type]'s Grand Exchange buy limit per four hours, null for none in the snapshot. */
    fun limit(type: ObjType): Int? = byId[itemId(type)]?.limit

    /**
     * What [type] is worth when keeping items on death: the Grand Exchange price, else the alchemy value (Wiki, Items
     * Kept on Death). Guessed: which alchemy value; high alchemy here.
     */
    fun deathValue(type: ObjType): Long = price(type) ?: type.highAlch.toLong()

    private fun itemId(type: ObjType) = if (type.isCert) type.certlink else type.id

    /** The file's text: a header, then one item a line (id, name, price, limit), tab-separated, by id. */
    fun format(): String = buildString {
        appendLine("# Grand Exchange guide prices and buy limits: a snapshot of the OSRS Wiki's Module:GEPrices, GELimits")
        appendLine("# and GEIDs, ${snapshot ?: "date unknown"}. Edit by hand and run ::itemprices to read it again;")
        appendLine("# ::itemprices refresh downloads a new snapshot over it. Columns: id, name, price, limit (empty: none).")
        appendLine("$SNAPSHOT_PREFIX${snapshot.orEmpty()}")
        for ((id, entry) in byId.toSortedMap()) {
            appendLine("$id\t${entry.name}\t${entry.price ?: ""}\t${entry.limit ?: ""}")
        }
    }

    companion object {
        val NONE = ItemPrices(emptyMap())

        const val FILE_NAME = "item-prices.tsv"
        private const val SNAPSHOT_PREFIX = "# snapshot: "

        const val PRICES_URL = "https://oldschool.runescape.wiki/?title=Module:GEPrices/data.json&action=raw"
        const val LIMITS_URL = "https://oldschool.runescape.wiki/?title=Module:GELimits/data.json&action=raw"
        const val IDS_URL = "https://oldschool.runescape.wiki/?title=Module:GEIDs/data.json&action=raw"

        /** The Wiki asks API users for a User-Agent that says who they are. */
        const val USER_AGENT = "OSRSPS private test server item price snapshot (github.com/SamSjosten/OSRSPS)"

        /** Reads a file [format] wrote. A line it can't read fails it, so a hand edit gone wrong shows. */
        fun parse(text: String): ItemPrices {
            val byId = HashMap<Int, Entry>()
            var snapshot: String? = null
            text.lineSequence().forEachIndexed { index, line ->
                if (line.startsWith(SNAPSHOT_PREFIX)) snapshot = line.removePrefix(SNAPSHOT_PREFIX).ifBlank { null }
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                val columns = line.split('\t')
                val id = columns[0].trim().toIntOrNull()
                require(id != null && columns.size == 4) { "$FILE_NAME:${index + 1}: expected id, name, price and limit" }
                val price = columns[2].trim().takeIf { it.isNotEmpty() }?.let {
                    it.toLongOrNull() ?: throw IllegalArgumentException("$FILE_NAME:${index + 1}: bad price \"$it\"")
                }
                val limit = columns[3].trim().takeIf { it.isNotEmpty() }?.let {
                    it.toIntOrNull() ?: throw IllegalArgumentException("$FILE_NAME:${index + 1}: bad limit \"$it\"")
                }
                byId[id] = Entry(columns[1], price, limit)
            }
            return ItemPrices(byId, snapshot)
        }

        /**
         * Builds the snapshot from the Wiki's three data modules, each a JSON object by item name: GEIDs gives the id,
         * GEPrices the price and GELimits the limit. `%LAST_UPDATE_F%` is when the Wiki last updated the prices.
         */
        fun fromWiki(pricesJson: String, limitsJson: String, idsJson: String): ItemPrices {
            fun read(json: String): Map<String, JsonPrimitive> =
                Json.parseToJsonElement(json).jsonObject.mapValues { it.value as JsonPrimitive }
            val prices = read(pricesJson)
            val limits = read(limitsJson)
            val byId = HashMap<Int, Entry>()
            for ((name, id) in read(idsJson)) {
                if (name.startsWith("%")) continue
                val itemId = id.longOrNull?.toInt() ?: continue
                byId[itemId] = Entry(name, prices[name]?.longOrNull, limits[name]?.longOrNull?.toInt())
            }
            return ItemPrices(byId, prices["%LAST_UPDATE_F%"]?.content)
        }

        /** Downloads a new snapshot from the Wiki. */
        fun download(get: (String) -> String = ::httpGet): ItemPrices = fromWiki(get(PRICES_URL), get(LIMITS_URL), get(IDS_URL))

        /**
         * The snapshot in [file], downloading it first if there is no file. A failed download leaves no prices (Examine
         * then shows the item's value), logged, and is tried again on the next start.
         */
        fun loadOrDownload(file: Path, get: (String) -> String = ::httpGet): ItemPrices {
            if (file.exists()) return parse(file.readText())
            return try {
                refresh(file, get).also { logger.info { "Downloaded ${it.size} Grand Exchange prices (${it.snapshot}) into $file" } }
            } catch (e: Exception) {
                logger.warn(e) { "Could not download the Grand Exchange prices; Examine shows item values until it works" }
                NONE
            }
        }

        /** Downloads a new snapshot and writes it to [file], keeping the old one as `.bak`. */
        fun refresh(file: Path, get: (String) -> String = ::httpGet): ItemPrices {
            val prices = download(get)
            require(prices.size > 0) { "The Wiki's snapshot has no items" }
            Files.createDirectories(file.toAbsolutePath().parent)
            if (file.exists()) Files.copy(file, file.resolveSibling("${file.fileName}.bak"), StandardCopyOption.REPLACE_EXISTING)
            file.writeText(prices.format())
            return prices
        }

        private val http: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

        private fun httpGet(url: String): String {
            val request = HttpRequest.newBuilder(URI(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(30)).build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) { "$url answered ${response.statusCode()}" }
            return response.body()
        }
    }
}
