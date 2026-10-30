package osrsps.game.ge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.PlayerSave

class GrandExchangeTest {
    private val types = ObjTypes.of(
        ObjType(COINS, "Coins", stackable = true, tradeable = true),
        ObjType(TOKEN, "Platinum token", stackable = true, tradeable = true),
        ObjType(AXE, "Bronze axe", tradeable = true, certlink = AXE_NOTE),
        ObjType(AXE_NOTE, "Bronze axe", stackable = true, tradeable = true, certlink = AXE, certtemplate = 799),
        ObjType(LOBSTER, "Lobster", tradeable = true),
        ObjType(WHIP, "Abyssal whip", tradeable = true),
        ObjType(QUEST_ITEM, "Quest item"),
        ObjType(NO_PRICE, "Odd thing", tradeable = true),
        ObjType(ENERGY_4, "Energy potion(4)", tradeable = true),
        ObjType(PRAYER_3, "Prayer potion(3)", tradeable = true),
        ObjType(PRAYER_4, "Prayer potion(4)", tradeable = true),
    )
    private val prices = ItemPrices(
        mapOf(
            AXE to ItemPrices.Entry("Bronze axe", 40, 10),
            LOBSTER to ItemPrices.Entry("Lobster", 150, null),
            WHIP to ItemPrices.Entry("Abyssal whip", 1_500_000_000, 70),
            PRAYER_3 to ItemPrices.Entry("Prayer potion(3)", 100, 2000),
            PRAYER_4 to ItemPrices.Entry("Prayer potion(4)", 100, 2000),
        ),
    )
    private var tick = 0L
    private var now = 1_000_000L
    private val ge = GrandExchange(types, { prices }, { tick }, { now }, taxExempt = GrandExchange.taxExempt(types))
    private val player = Player(1, "test", 3222, 3218, 0)

    private fun give(id: Int, count: Int) = player.inventory.add(types[id]!!, count)

    private fun nextTick() {
        tick++
        ge.process(player)
    }

    @Test
    fun `a buy at or above the guide price fills on the next tick at the guide price, refunding the difference`() {
        give(COINS, 1000)
        assertEquals(PlaceResult.PLACED, ge.buy(player, AXE, 2, 50))
        assertEquals(900L, player.inventory.count(COINS))
        // Not on the tick it was placed.
        ge.process(player)
        assertEquals(0, player.ge[0]!!.completed)
        nextTick()
        assertEquals(GeOffer(OfferType.BUY, AXE, 2, 50, 2, 80, collectItems = 2, collectCoins = 20, placedTick = 0), player.ge[0])
        // Two axes come as notes, the refund as coins, and the slot empties.
        assertTrue(ge.collect(player, 0))
        assertEquals(2L, player.inventory.count(AXE_NOTE))
        assertEquals(920L, player.inventory.count(COINS))
        assertNull(player.ge[0])
    }

    @Test
    fun `a buy below the guide price waits, and an abort returns the coins`() {
        give(COINS, 100)
        ge.buy(player, AXE, 2, 39)
        repeat(5) { nextTick() }
        assertEquals(0, player.ge[0]!!.completed)
        assertTrue(ge.abort(player, 0))
        assertFalse(ge.abort(player, 0))
        assertEquals(78L, player.ge[0]!!.collectCoins)
        ge.collect(player, 0)
        assertEquals(100L, player.inventory.count(COINS))
        assertNull(player.ge[0])
    }

    @Test
    fun `a sell at or below the guide price fills at the guide price less 2 percent tax, rounded down`() {
        give(LOBSTER, 3)
        // Lobster is tax exempt.
        ge.sell(player, LOBSTER, 3, 100)
        give(AXE, 1)
        player.inventory.add(types[AXE_NOTE]!!, 4)
        ge.sell(player, AXE, 5, 40)
        assertEquals(0L, player.inventory.count(AXE) + player.inventory.count(AXE_NOTE))
        nextTick()
        assertEquals(450L, player.ge[0]!!.collectCoins)
        // 2% of 40 is 0.8, rounded down to none.
        assertEquals(200L, player.ge[1]!!.collectCoins)
        assertEquals(200L, player.ge[1]!!.exchanged)
    }

    @Test
    fun `tax is 2 percent rounded down and at most 5m an item`() {
        assertEquals(0, ge.tax(AXE, 49))
        assertEquals(1, ge.tax(AXE, 50))
        assertEquals(1, ge.tax(AXE, 99))
        assertEquals(2, ge.tax(AXE, 100))
        assertEquals(5_000_000, ge.tax(WHIP, 1_500_000_000))
        assertEquals(0, ge.tax(LOBSTER, 1000))
        assertEquals(0, ge.tax(ENERGY_4, 1000))
    }

    @Test
    fun `a buy fills only as far as the buy limit, and the rest after four hours`() {
        give(COINS, 10_000)
        ge.buy(player, AXE, 15, 40)
        nextTick()
        assertEquals(10, player.ge[0]!!.completed)
        now += GrandExchange.LIMIT_WINDOW_MILLIS - 1
        nextTick()
        assertEquals(10, player.ge[0]!!.completed)
        now += 1
        nextTick()
        assertEquals(15, player.ge[0]!!.completed)
        assertEquals(5, player.ge.limits[AXE]!!.bought)
    }

    @Test
    fun `a potion's doses share one buy limit`() {
        give(COINS, 1_000_000)
        ge.buy(player, PRAYER_4, 1500, 100)
        ge.buy(player, PRAYER_3, 1000, 100)
        nextTick()
        assertEquals(1500, player.ge[0]!!.completed)
        assertEquals(500, player.ge[1]!!.completed)
        assertEquals(PRAYER_3, ge.limitKey(PRAYER_4))
        assertEquals(setOf(PRAYER_3), player.ge.limits.keys)
    }

    @Test
    fun `coins go first, then platinum tokens with the change as coins`() {
        give(COINS, 500)
        give(TOKEN, 3_000)
        // 2,000: the 500 coins, then two tokens for the other 1,500, with 500 change.
        assertEquals(PlaceResult.PLACED, ge.buy(player, AXE, 1, 2_000))
        assertEquals(2_998L, player.inventory.count(TOKEN))
        assertEquals(500L, player.inventory.count(COINS))
    }

    @Test
    fun `an offer past 2,147,483,647 coins is paid in tokens`() {
        give(TOKEN, 3_000_000)
        assertEquals(PlaceResult.PLACED, ge.buy(player, WHIP, 2, 1_500_000_000))
        assertEquals(0L, player.inventory.count(TOKEN))
        assertEquals(PlaceResult.TOO_EXPENSIVE, ge.buy(player, WHIP, 2, GrandExchange.MAX_TOTAL))
    }

    @Test
    fun `offers that can't be placed`() {
        give(COINS, 10)
        assertEquals(PlaceResult.NOT_TRADEABLE, ge.buy(player, QUEST_ITEM, 1, 1))
        assertEquals(PlaceResult.NOT_TRADEABLE, ge.buy(player, AXE_NOTE, 1, 1))
        assertEquals(PlaceResult.NOT_ON_EXCHANGE, ge.buy(player, NO_PRICE, 1, 1))
        assertEquals(PlaceResult.NOT_ENOUGH_COINS, ge.buy(player, AXE, 1, 11))
        assertEquals(PlaceResult.NOT_ENOUGH_ITEMS, ge.sell(player, AXE, 1, 1))
        assertEquals(PlaceResult.BAD_AMOUNT, ge.buy(player, AXE, 0, 1))
        repeat(GeAccount.SLOTS) { assertEquals(PlaceResult.PLACED, ge.buy(player, AXE, 1, 1)) }
        assertEquals(PlaceResult.NO_FREE_SLOT, ge.buy(player, AXE, 1, 1))
        assertEquals(2L, player.inventory.count(COINS))
    }

    @Test
    fun `the market can be stopped, and gefill fills at the offer's own price`() {
        give(COINS, 1000)
        ge.buy(player, AXE, 4, 50)
        ge.marketOpen = false
        nextTick()
        assertEquals(0, player.ge[0]!!.completed)
        assertEquals(3, ge.fill(player, 0, 3))
        assertEquals(GeOffer(OfferType.BUY, AXE, 4, 50, 3, 150, collectItems = 3, placedTick = 0), player.ge[0])
        assertEquals(3, player.ge.limits[AXE]!!.bought)
        // Collected as items, three take three slots; the slot stays for the last one.
        assertTrue(ge.collect(player, 0, notes = false))
        assertEquals(3L, player.inventory.count(AXE))
        assertEquals(0, player.ge[0]!!.collectItems)
    }

    @Test
    fun `offers and buy limits survive a save`() {
        give(COINS, 1000)
        ge.buy(player, AXE, 15, 50)
        nextTick()
        give(LOBSTER, 1)
        ge.sell(player, LOBSTER, 1, 1_000)
        ge.abort(player, 1)
        val save = PlayerSave.fromJson(PlayerSave.of(player).toJson("test"))
        val loaded = Player(2, "test", 0, 0, 0)
        save.applyTo(loaded)
        assertEquals(player.ge.contents().map { it.first to it.second.copy(placedTick = -1) }, loaded.ge.contents())
        assertEquals(player.ge.limits, loaded.ge.limits)
    }

    @Test
    fun `the tax exempt list reads every name and its doses`() {
        val exempt = GrandExchange.taxExempt(types)
        assertEquals(setOf(LOBSTER, ENERGY_4), exempt)
    }

    @Test
    fun `items that don't fit stay in the slot`() {
        give(COINS, 1000)
        ge.buy(player, AXE, 3, 40)
        nextTick()
        repeat(26) { player.inventory.add(types[LOBSTER]!!, 1) }
        // Coins and 26 lobsters: one slot left for one axe.
        assertTrue(ge.collect(player, 0, notes = false))
        assertEquals(1L, player.inventory.count(AXE))
        assertEquals(2, player.ge[0]!!.collectItems)
        assertEquals(InvObj(AXE, 1), player.inventory[27])
    }

    private companion object {
        const val COINS = 995
        const val TOKEN = 13204
        const val AXE = 1351
        const val AXE_NOTE = 1352
        const val LOBSTER = 379
        const val WHIP = 4151
        const val QUEST_ITEM = 2000
        const val NO_PRICE = 2001
        const val ENERGY_4 = 3008
        const val PRAYER_4 = 2434
        const val PRAYER_3 = 139
    }
}
