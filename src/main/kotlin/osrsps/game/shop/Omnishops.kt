package osrsps.game.shop

/** A currency of the omnishop interface: `omnishop_currency_data` (db table 41). [obj] is -1 for a saved balance. */
data class OmnishopCurrency(val row: Int, val obj: Int, val singular: String, val plural: String)

/**
 * One entry of an omnishop's list: `omnishop_stock_data` (db table 40). [cost] is the first currency's price, [multiplier]
 * how many one purchase hands over; [viewOnly] entries (a shop's note) show but sell nothing.
 */
data class OmnishopStock(
    val row: Int,
    val obj: Int,
    val cost: Int,
    val buyable: Boolean = true,
    val viewOnly: Boolean = false,
    val multiplier: Int = 1,
)

/**
 * A shop of the omnishop interface: `omnishop_shop_data` (db table 39). The client draws the whole window from the
 * cache's row; the server only needs what a click buys. [inv] is the inv the client counts each entry's stock from
 * (`inv_total`), -1 for none. [amounts] are what the list's options 2 to 5 and the info panel's four buttons buy,
 * the row's `omnishop_buy_q1` to `q4` through `enum_5844`.
 */
data class OmnishopType(
    val row: Int,
    val name: String,
    val inv: Int,
    val currency: OmnishopCurrency?,
    val stock: List<OmnishopStock>,
    val amounts: List<Int>,
)

/** The cache's omnishops by db row. */
class Omnishops(private val byRow: Map<Int, OmnishopType>) {
    operator fun get(row: Int): OmnishopType? = byRow[row]

    val size: Int get() = byRow.size

    companion object {
        val NONE = Omnishops(emptyMap())

        fun of(vararg shops: OmnishopType) = Omnishops(shops.associateBy { it.row })

        /**
         * `enum_5844` (241 cache): a row's `omnishop_buy_q` code to the amount it buys. The client shows an option only
         * for a code with an amount above 0.
         */
        val AMOUNTS = mapOf(0 to 0, 1 to 1, 5 to 2, 6 to 3, 2 to 5, 3 to 10, 4 to 50)
    }
}
