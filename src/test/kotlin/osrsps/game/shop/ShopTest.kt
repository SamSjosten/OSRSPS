package osrsps.game.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes

class ShopTest {
    /** The items of the shop file with their values in the 241 cache (the Wiki's item pages agree). */
    private val values = mapOf(
        1931 to 1, 1935 to 1, 20742 to 140, 1735 to 1, 946 to 6, 1925 to 2, 22660 to 500, 1923 to 4, 1887 to 10, 590 to 1,
        1755 to 1, 952 to 3, 2347 to 1, 550 to 1, 9003 to 2,
        1265 to 1, 1351 to 16, 1349 to 56, 1353 to 200, 1363 to 182, 1365 to 650, 1369 to 1690,
    )

    private val shopFile = ShopTest::class.java.getResource(ShopTypes.RESOURCE)!!.readText()

    /** The shop file read against item and NPC types made up from its own names, as the 241 cache has them. */
    private fun realShops(): ShopTypes {
        val names = Regex("\"item\": (\\d+),\\s*\"name\": \"([^\"]+)\"").findAll(shopFile).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, cost = values.getValue(id)) }.toTypedArray())
        val npcs = NpcTypes.of(
            NpcType(2813, "Shop keeper", internalName = "generalshopkeeper1"),
            NpcType(2814, "Shop assistant", internalName = "generalassistant1"),
            NpcType(10619, "Bob", internalName = "bob"),
        )
        return ShopTypes.parse(shopFile, npcs, objs)
    }

    /** The Wiki's "Price sold at" and "Price bought at" for each item at its starting stock, read 2026-10-29. */
    private val wikiPrices = mapOf(
        "lumbridge_general_store" to listOf(
            1 to 0, 1 to 0, 182 to 56, 1 to 0, 7 to 2, 2 to 0, 650 to 200, 5 to 1, 13 to 4, 1 to 0, 1 to 0, 3 to 1, 1 to 0, 1 to 0, 2 to 0,
        ),
        "bobs_brilliant_axes" to listOf(1 to 0, 16 to 9, 56 to 33, 200 to 120, 182 to 109, 650 to 390, 1690 to 1014),
    )

    @Test
    fun `the shop file reads with every item and keeper, and its prices are the Wiki's`() {
        val shops = realShops()
        assertEquals(emptyList(), shops.warnings)
        assertEquals(listOf("lumbridge_general_store", "bobs_brilliant_axes"), shops.all.map { it.key })
        assertEquals(shops["lumbridge_general_store"], shops.ofNpc(2814))
        assertEquals(shops["bobs_brilliant_axes"], shops.ofNpc(10619))
        for (type in shops.all) {
            val shop = Shop(type)
            val prices = type.stock.map { shop.buyPrice(it.type.id, it.type.cost, it.count) to shop.sellPrice(it.type.id, it.type.cost, it.count) }
            assertEquals(wikiPrices.getValue(type.key), prices, type.key)
        }
    }

    @Test
    fun `a shop missing from the cache is left out with a warning`() {
        val shops = ShopTypes.parse(shopFile, NpcTypes.NONE, ObjTypes.NONE)
        assertEquals(emptyList(), shops.all)
        assertEquals(2, shops.warnings.size)
    }

    @Test
    fun `prices move with the stock as the Wiki's example at Bob's has them`() {
        val bob = Shop(realShops()["bobs_brilliant_axes"]!!)
        val steelAxe = 1353
        // "Selling with 3 in stock pays 120 (60%); with 4, 116 (58%). Buying with 3 costs 200; with 4, 196 (98%)."
        assertEquals(120, bob.sellPrice(steelAxe, 200, 3))
        assertEquals(116, bob.sellPrice(steelAxe, 200, 4))
        assertEquals(200, bob.buyPrice(steelAxe, 200, 3))
        assertEquals(196, bob.buyPrice(steelAxe, 200, 4))
        assertEquals(204, bob.buyPrice(steelAxe, 200, 2))
        // However overstocked, a shop pays at least 10% (the Wiki), and charges at least 1 coin.
        assertEquals(20, bob.sellPrice(steelAxe, 200, 1000))
        assertEquals(1, bob.buyPrice(1265, 1, 1000))
        // The change is held at +500% below the starting stock.
        assertEquals(Shop.price(200, 1000, 20, -250), Shop.price(200, 1000, 20, -1000))
    }

    @Test
    fun `a general store prices an item it doesn't stock from none`() {
        val store = Shop(realShops()["lumbridge_general_store"]!!)
        val bronzeAxe = 1351
        assertEquals(6, store.sellPrice(bronzeAxe, 16, 0))
        assertEquals(20, store.buyPrice(bronzeAxe, 16, 1))
        // 130% less 3% per item, held at 30% of the value (1300 less the +100% cap).
        assertEquals(4, store.buyPrice(bronzeAxe, 16, 1000))
    }

    @Test
    fun `stock comes back one at a time on its timer, and an item it doesn't stock goes`() {
        val store = Shop(realShops()["lumbridge_general_store"]!!)
        val pot = 1931
        store.take(0, 5)
        assertEquals(ShopSlot(pot, 0), store[0])
        store.restock(9)
        assertEquals(0, store.count(pot))
        store.restock(10)
        assertEquals(1, store.count(pot))
        // A jug (every 100 ticks) overstocked by 2 goes down one a time.
        store.put(1935, 2)
        store.restock(100)
        assertEquals(3, store.count(1935))

        // Sold items it doesn't stock take the first free slots; one sold out goes and the next moves up.
        assertTrue(store.put(1351, 1))
        assertTrue(store.put(1349, 2))
        assertEquals(ShopSlot(1351, 1), store[15])
        assertEquals(ShopSlot(1349, 2), store[16])
        store.restock(Shop.NON_STOCK_TICKS.toLong())
        assertEquals(ShopSlot(1349, 1), store[15])
        assertNull(store[16])
    }

    @Test
    fun `a specialist shop has no room for an item it doesn't stock`() {
        val bob = Shop(realShops()["bobs_brilliant_axes"]!!)
        assertTrue(bob.hasRoom(1353))
        assertFalse(bob.hasRoom(1931))
        assertFalse(bob.put(1931, 1))
        val version = bob.version
        bob.take(3, 1)
        assertTrue(bob.version > version)
    }
}
