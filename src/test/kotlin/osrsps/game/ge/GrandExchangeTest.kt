package osrsps.game.ge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.message.OutgoingGameMessage
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
        // A sale that finishes, so the history has a trade to keep.
        give(PRAYER_4, 1)
        ge.sell(player, PRAYER_4, 1, 100)
        nextTick()
        assertEquals(1, player.ge.history.size)
        val save = PlayerSave.fromJson(PlayerSave.of(player).toJson("test"))
        val loaded = Player(2, "test", 0, 0, 0)
        save.applyTo(loaded)
        assertEquals(player.ge.contents().map { it.first to it.second.copy(placedTick = -1) }, loaded.ge.contents())
        assertEquals(player.ge.limits, loaded.ge.limits)
        assertEquals(player.ge.history.toList(), loaded.ge.history.toList())
    }

    @Test
    fun `finished offers go in the history, newest first, with a sale's tax`() {
        give(COINS, 1000)
        give(PRAYER_4, 3)
        ge.marketOpen = false
        ge.buy(player, AXE, 2, 50)
        ge.sell(player, PRAYER_4, 3, 150)
        // Aborted with nothing bought: no trade.
        ge.buy(player, AXE, 1, 40)
        ge.abort(player, 2)
        assertTrue(player.ge.history.isEmpty())
        ge.fill(player, 0, 2)
        assertEquals(listOf(GeTrade(OfferType.BUY, AXE, 2, 100)), player.ge.history.toList())
        // Two potions sold at 150, 3 tax each, then the rest aborted: in as the two sold.
        ge.fill(player, 1, 2)
        ge.abort(player, 1)
        assertEquals(GeTrade(OfferType.SELL, PRAYER_4, 2, 300, 6), player.ge.history.first())
        assertEquals(6L, player.ge[1]!!.taxPaid)
        // A save keeps the tax paid.
        val loaded = Player(2, "test", 0, 0, 0)
        PlayerSave.fromJson(PlayerSave.of(player).toJson("test")).applyTo(loaded)
        assertEquals(6L, loaded.ge[1]!!.taxPaid)
        assertEquals(player.ge.history.toList(), loaded.ge.history.toList())
    }

    @Test
    fun `the history keeps the last 40`() {
        repeat(45) { player.ge.record(GeTrade(OfferType.BUY, AXE, it + 1, 40)) }
        assertEquals(40, player.ge.history.size)
        assertEquals(45, player.ge.history.first().count)
        assertEquals(6, player.ge.history.last().count)
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

    @Test
    fun `a reconnect sends a reset for a slot emptied while the old connection was dying`() {
        val sent = mutableListOf<OutgoingGameMessage>()
        val screen = GrandExchangeScreen(ge, { _, _, _ -> }, send = { _, message -> sent += message })
        fun slotsSent() = sent.filterIsInstance<UpdateStockMarketSlotV2>().map { it.slot }

        // At login only slots in use go.
        screen.flush(player)
        assertEquals(emptyList(), slotsSent())

        player.ge[0] = GeOffer(OfferType.BUY, AXE, 2, 50, placedTick = 0)
        screen.flush(player)
        assertEquals(listOf(0), slotsSent())

        // The slot empties and its reset goes to a connection that dies before it arrives.
        player.ge[0] = null
        screen.flush(player)
        sent.clear()
        player.geSent.resendAll()
        screen.flush(player)
        assertEquals((0 until GeAccount.SLOTS).toList(), slotsSent())

        // Once resynchronised, unchanged slots aren't sent again.
        sent.clear()
        screen.flush(player)
        assertEquals(emptyList(), slotsSent())
    }

    @Test
    fun `the two boxes collect apart, and to the bank`() {
        give(COINS, 1000)
        ge.buy(player, AXE, 2, 50)
        nextTick()
        // The refund only: the axes stay.
        assertTrue(ge.collect(player, 0, items = false))
        assertEquals(920L, player.inventory.count(COINS))
        assertEquals(2, player.ge[0]!!.collectItems)
        // Then the axes, to a bank that takes one.
        val banked = mutableListOf<Pair<Int, Int>>()
        assertTrue(ge.collect(player, 0, coins = false, toBank = { _, id, count -> banked += id to count; 1 }))
        assertEquals(listOf(AXE to 2), banked)
        assertEquals(1, player.ge[0]!!.collectItems)
        assertTrue(ge.collectAll(player, toBank = { _, _, count -> count }))
        assertNull(player.ge[0])
    }

    @Test
    fun `coins past what a stack holds are paid as platinum tokens and the rest in coins`() {
        assertEquals(500L to 0L, GrandExchange.payout(500))
        assertEquals(Int.MAX_VALUE.toLong() to 0L, GrandExchange.payout(Int.MAX_VALUE.toLong()))
        // The most an offer holds is a full stack of tokens and a full stack of coins.
        assertEquals(2_147_483_647L to 2_147_483_647L, GrandExchange.payout(GrandExchange.MAX_TOTAL))
        assertEquals(1L to 2_147_483_647L, GrandExchange.payout(2_147_483_647_001L))
        // Past both stacks, a full stack of each now and the rest left to collect later.
        assertEquals(2_147_483_647L to 2_147_483_647L, GrandExchange.payout(3_920_000_000_000L))
        give(TOKEN, 3_000_000)
        ge.buy(player, WHIP, 2, 1_500_000_000)
        assertTrue(ge.abort(player, 0))
        assertTrue(ge.collect(player, 0))
        assertEquals(3_000_000L, player.inventory.count(TOKEN))
        assertEquals(0L, player.inventory.count(COINS))
        assertNull(player.ge[0])
    }

    @Test
    fun `Collect-note gives a note even for one item`() {
        give(COINS, 100)
        ge.buy(player, AXE, 1, 50)
        nextTick()
        assertTrue(ge.collect(player, 0, notes = true, coins = false))
        assertEquals(1L, player.inventory.count(AXE_NOTE))
        assertEquals(0L, player.inventory.count(AXE))
    }

    @Test
    fun `an abort collected in the same tick still gets its message`() {
        val sent = mutableListOf<OutgoingGameMessage>()
        val screen = GrandExchangeScreen(ge, { _, _, _ -> }, send = { _, message -> sent += message })
        give(COINS, 100)
        ge.buy(player, AXE, 2, 39)
        screen.flush(player)
        sent.clear()
        assertTrue(ge.abort(player, 0))
        assertTrue(ge.collect(player, 0))
        screen.flush(player)
        assertEquals(listOf("[ge.aborted]"), sent.filterIsInstance<MessageGame>().map { it.message })
        // Said once: the next flush has nothing more to say.
        sent.clear()
        screen.flush(player)
        assertEquals(emptyList(), sent.filterIsInstance<MessageGame>())
    }

    @Test
    fun `a box past both stacks pays one of each and keeps the rest`() {
        give(TOKEN, 1)
        ge.buy(player, AXE, 1, 1_000)
        player.ge[0] = player.ge[0]!!.copy(collectCoins = 3_920_000_000_000L)
        assertTrue(ge.collect(player, 0, items = false))
        assertEquals(Int.MAX_VALUE.toLong(), player.inventory.count(TOKEN))
        assertEquals(Int.MAX_VALUE.toLong(), player.inventory.count(COINS))
        assertEquals(3_920_000_000_000L - 2_149_631_130_647L, player.ge[0]!!.collectCoins)
    }

    @Test
    fun `the coins collect even when the tokens don't fit`() {
        give(COINS, 1_000)
        ge.buy(player, AXE, 1, 1_000)
        player.ge[0] = player.ge[0]!!.copy(collectCoins = 3_000_000_001L)
        // A full backpack with a stack of coins in it and no tokens.
        give(COINS, 5)
        while (player.inventory.contents().size < player.inventory.capacity) give(LOBSTER, 1)
        assertTrue(ge.collect(player, 0, items = false))
        assertEquals(6L, player.inventory.count(COINS))
        assertEquals(0L, player.inventory.count(TOKEN))
        assertEquals(3_000_000_000L, player.ge[0]!!.collectCoins)
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
