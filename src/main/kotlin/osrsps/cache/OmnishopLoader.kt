package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.shop.OmnishopCurrency
import osrsps.game.shop.OmnishopStock
import osrsps.game.shop.OmnishopType
import osrsps.game.shop.Omnishops

private val logger = KotlinLogging.logger {}

/**
 * Reads the omnishops from the cache's db tables 39 (`omnishop_shop_data`), 40 (`omnishop_stock_data`) and 41
 * (`omnishop_currency_data`), by their columns' places in osrs-dumps' `config/dump.dbtable`, with its defaults for what a
 * row leaves out. Checked against the 241 cache from abextm/osrs-cache on 2026-11-11: row 17150
 * (`tfa_midraid_supplies_shop`) has inv 1030, currency 17149 and ten stock rows, and its Saradomin brew(4) row 17159
 * costs 3 of currency 17149.
 */
object OmnishopLoader {
    const val SHOP_TABLE = 39
    const val STOCK_TABLE = 40
    const val CURRENCY_TABLE = 41

    private const val SHOP_NAME = 0
    private const val SHOP_INV = 1
    private const val SHOP_CURRENCY = 2
    private const val SHOP_STOCK = 5
    private const val SHOP_BUY_Q1 = 20

    /** The `omnishop_buy_q1` to `q4` codes a row without its own has. */
    private val DEFAULT_BUY_Q = listOf(1, 2, 3, 4)

    private const val STOCK_OBJ = 0
    private const val STOCK_COST = 7
    private const val STOCK_MULTIPLIER = 11
    private const val STOCK_BUYABLE = 18
    private const val STOCK_VIEW_ONLY = 21

    private const val CURRENCY_OBJ = 0
    private const val CURRENCY_SINGULAR = 1
    private const val CURRENCY_PLURAL = 2

    fun load(library: CacheLibrary): Omnishops =
        build(DbRows.load(library, SHOP_TABLE), DbRows.load(library, STOCK_TABLE), DbRows.load(library, CURRENCY_TABLE))

    internal fun build(shops: Map<Int, DbRow>, stock: Map<Int, DbRow>, currencies: Map<Int, DbRow>): Omnishops {
        val currencyTypes = currencies.mapValues { (row, it) ->
            OmnishopCurrency(row, it.int(CURRENCY_OBJ) ?: -1, it.string(CURRENCY_SINGULAR).orEmpty(), it.string(CURRENCY_PLURAL).orEmpty())
        }
        val stockTypes = stock.mapValues { (row, it) ->
            OmnishopStock(
                row = row,
                obj = it.int(STOCK_OBJ) ?: -1,
                // (currency, amount) pairs; the first is the price.
                cost = (it.columns[STOCK_COST]?.getOrNull(1) as? Int) ?: 0,
                buyable = (it.int(STOCK_BUYABLE) ?: 1) != 0,
                viewOnly = (it.int(STOCK_VIEW_ONLY) ?: 0) != 0,
                multiplier = it.int(STOCK_MULTIPLIER) ?: 1,
            )
        }
        val types = shops.mapValues { (row, it) ->
            OmnishopType(
                row = row,
                name = it.string(SHOP_NAME).orEmpty(),
                inv = it.int(SHOP_INV) ?: -1,
                currency = it.int(SHOP_CURRENCY)?.let(currencyTypes::get),
                stock = it.columns[SHOP_STOCK].orEmpty().mapNotNull { id -> stockTypes[id as Int] },
                amounts = (0 until 4).map { q -> Omnishops.AMOUNTS[it.int(SHOP_BUY_Q1 + q) ?: DEFAULT_BUY_Q[q]] ?: 0 },
            )
        }
        logger.info { "Loaded ${types.size} omnishops from the cache's db tables" }
        return Omnishops(types)
    }

    private fun DbRow.int(column: Int): Int? = columns[column]?.firstOrNull() as? Int

    private fun DbRow.string(column: Int): String? = columns[column]?.firstOrNull() as? String
}
