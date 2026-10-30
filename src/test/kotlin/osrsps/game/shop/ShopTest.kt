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
        // Draynor's market.
        1993 to 1, 7919 to 500,
        5318 to 2, 5319 to 3, 5324 to 3, 5322 to 4, 5320 to 8, 5323 to 18, 5321 to 56, 5305 to 2, 5306 to 5, 5097 to 4, 5096 to 2,
        5307 to 2, 5308 to 3, 5309 to 6, 5310 to 8, 5311 to 14,
        2520 to 100, 2522 to 100, 2524 to 100, 2526 to 100, 4613 to 50, 11705 to 50, 11706 to 50, 11707 to 30, 11708 to 30,
        11709 to 30, 12727 to 150, 13188 to 500, 13328 to 49, 13660 to 200, 13658 to 100, 13679 to 100, 13681 to 200, 13215 to 100,
        13216 to 100, 13217 to 100, 13218 to 100, 21314 to 80, 22355 to 1, 22358 to 1, 22361 to 1, 19699 to 10, 21354 to 0,
        13203 to 1, 12600 to 1, 981 to 120, 22364 to 40,
        // Al Kharid's shops.
        1321 to 32, 1323 to 112, 1325 to 400, 1329 to 1040,
        1592 to 5, 1597 to 5, 1595 to 5, 1733 to 1, 1734 to 1, 1599 to 5, 2976 to 10, 5523 to 100, 9434 to 25, 11065 to 5,
        1075 to 80, 1067 to 280, 1069 to 1000, 1077 to 1920, 1071 to 2600, 1073 to 6400,
        1087 to 80, 1081 to 280, 1083 to 1000, 1089 to 1920, 1085 to 2600, 1091 to 6400,
        1623 to 25, 1621 to 50, 1619 to 100, 1617 to 200, 1607 to 250, 1605 to 500, 1603 to 1000, 1601 to 2000,
        1825 to 27, 1833 to 40, 1837 to 20, 4593 to 1, 4591 to 1, 970 to 10, 2138 to 1,
        // Varrock's weapon and armour shops.
        1277 to 26, 1279 to 91, 1281 to 325, 1283 to 624, 1285 to 845, 1287 to 2080,
        1291 to 40, 1293 to 140, 1295 to 500, 1297 to 960, 1299 to 1300, 1301 to 3200,
        1205 to 10, 1203 to 35, 1207 to 125, 1217 to 240, 1209 to 325, 1211 to 800,
        1103 to 60, 1101 to 210, 1105 to 750, 1109 to 1950, 1117 to 160, 1115 to 560, 1119 to 2000, 1125 to 3840, 1121 to 5200,
        1133 to 850, 1097 to 750,
        882 to 1, 884 to 3, 886 to 12, 888 to 32, 890 to 80, 877 to 0, 841 to 50, 839 to 80, 843 to 100, 845 to 160, 849 to 200,
        847 to 320, 853 to 400, 851 to 640, 837 to 70,
        1391 to 7000, 1379 to 15, 1389 to 200, 1381 to 1500, 1383 to 1500, 1385 to 1500, 1387 to 1500,
        // Varrock's other shops.
        556 to 4, 554 to 4, 555 to 4, 557 to 4, 558 to 3, 559 to 3, 562 to 90, 560 to 180,
        12734 to 430, 12730 to 430, 12728 to 430, 12732 to 430, 12736 to 330, 12738 to 9950,
        950 to 30, 1005 to 2, 1129 to 21, 1059 to 6, 1061 to 6, 1757 to 2, 1013 to 2, 1015 to 2, 1011 to 2, 1007 to 2, 428 to 5, 426 to 5,
        1949 to 2, 579 to 2, 1023 to 32, 958 to 50, 948 to 10, 1025 to 2,
        10088 to 10, 10090 to 13, 10091 to 15, 10089 to 16, 10087 to 20, 1978 to 10,
    )

    /**
     * Diango's items the 241 cache has untradeable (the Wiki's N/A for "bought at"): the spinning plate to the Chronicle,
     * the cabbage cape to the rainbow scarf, and the Oculus orb.
     */
    private val untradeable = setOf(
        4613, 11705, 11706, 11707, 11708, 11709, 12727, 13188, 13328, 13660, 13679, 13681, 13215, 13216, 13217, 13218, 21314, 22364,
    )

    private val shopFile = ShopTest::class.java.getResource(ShopTypes.RESOURCE)!!.readText()

    /** The shop file read against item and NPC types made up from its own names, as the 241 cache has them. */
    private fun realShops(): ShopTypes {
        val names = Regex("\"item\": (\\d+),\\s*\"name\": \"([^\"]+)\"").findAll(shopFile).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, cost = values.getValue(id), tradeable = id !in untradeable) }.toTypedArray())
        val npcs = NpcTypes.of(
            NpcType(2813, "Shop keeper", internalName = "generalshopkeeper1"),
            NpcType(2814, "Shop assistant", internalName = "generalassistant1"),
            NpcType(10619, "Bob", internalName = "bob"),
            NpcType(1260, "Fortunato", internalName = "rag_wine_merchant"),
            NpcType(500, "Olivia", internalName = "seed_merchant"),
            NpcType(8693, "Diango", internalName = "aprilfoolshorsesalesman"),
            NpcType(2817, "Shop keeper", internalName = "generalshopkeeper3"),
            NpcType(2818, "Shop assistant", internalName = "generalassistant3"),
            NpcType(2875, "Zeke", internalName = "zeke"),
            NpcType(2879, "Dommik", internalName = "dommik"),
            NpcType(2876, "Louie Legs", internalName = "louie_legs"),
            NpcType(2878, "Ranael", internalName = "ranael"),
            NpcType(2874, "Gem trader", internalName = "gem_trader"),
            NpcType(3533, "Ali Morrisane", internalName = "feud_ali_m"),
            NpcType(2884, "Shop keeper", internalName = "swordshop1"),
            NpcType(2885, "Shop assistant", internalName = "swordshop2"),
            NpcType(2882, "Horvik", internalName = "horvik_the_armourer"),
            NpcType(2883, "Lowe", internalName = "lowe"),
            NpcType(2880, "Zaff", internalName = "zaff"),
            NpcType(2815, "Shop keeper", internalName = "generalshopkeeper2"),
            NpcType(2816, "Shop assistant", internalName = "generalassistant2"),
            NpcType(11434, "Aubury", internalName = "aubury_2op"),
            NpcType(11435, "Aubury", internalName = "aubury_3op"),
            NpcType(10477, "Thessalia", internalName = "thessalia_normal"),
            NpcType(10478, "Thessalia", internalName = "thessalia_league"),
            NpcType(2887, "Asyff", internalName = "tailorp"),
            NpcType(8687, "Tea Seller", internalName = "tea_seller"),
        )
        return ShopTypes.parse(shopFile, npcs, objs)
    }

    /** The Wiki's "Price sold at" and "Price bought at" for each item at its starting stock, read 2026-10-29. */
    private val wikiPrices = mapOf(
        "lumbridge_general_store" to listOf(
            1 to 0, 1 to 0, 182 to 56, 1 to 0, 7 to 2, 2 to 0, 650 to 200, 5 to 1, 13 to 4, 1 to 0, 1 to 0, 3 to 1, 1 to 0, 1 to 0, 2 to 0,
        ),
        "bobs_brilliant_axes" to listOf(1 to 0, 16 to 9, 56 to 33, 200 to 120, 182 to 109, 650 to 390, 1690 to 1014),
        // Read 2026-10-30.
        "draynor_wine_shop" to listOf(1 to 0, 1 to 0, 140 to 84, 500 to 300),
        "draynor_seed_market" to listOf(
            2 to 1, 3 to 1, 3 to 1, 4 to 2, 9 to 4, 21 to 10, 67 to 33, 2 to 1, 6 to 3, 4 to 2, 2 to 1, 2 to 1, 3 to 1, 7 to 3, 9 to 4, 16 to 8,
        ),
        // The Wiki's N/A (untradeable, so never bought) is null.
        "diangos_toy_store" to listOf(
            150 to 50, 150 to 50, 150 to 50, 150 to 50, 75 to null, 75 to null, 75 to null, 45 to null, 45 to null, 45 to null,
            225 to null, 750 to null, 73 to null, 300 to null, 150 to 50, 150 to null, 300 to null, 150 to null, 150 to null,
            150 to null, 150 to null, 120 to null, 1 to 0, 1 to 0, 1 to 0, 15 to 5, 1 to 0, 1 to 0, 1 to 0, 21000 to 60, 60 to null,
        ),
    )

    /** The Wiki's "Price sold at" for each item of Al Kharid's shops at its starting stock, read 2026-10-30. */
    private val wikiSoldAt = mapOf(
        "al_kharid_general_store" to listOf(1, 1, 182, 1, 2, 650, 5, 13, 1, 1, 1, 1, 2),
        "zekes_superior_scimitars" to listOf(32, 112, 400, 1040),
        "dommiks_crafting_store" to listOf(1, 5, 5, 5, 1, 1, 5, 10, 100, 25, 5),
        "louies_armoured_legs_bazaar" to listOf(80, 280, 1000, 1920, 2600, 6400),
        "ranaels_super_skirt_store" to listOf(80, 280, 1000, 1920, 2600, 6400),
        "gem_trader" to listOf(25, 50, 100, 200, 250, 500, 1000, 2000),
        "alis_discount_wares" to listOf(1, 1, 140, 27, 40, 20, 2, 500, 1, 1, 10, 6, 1, 1, 1),
        // Varrock's weapon and armour shops, read 2026-10-30.
        "varrock_swordshop" to listOf(26, 91, 325, 624, 845, 2080, 40, 140, 500, 960, 1300, 3200, 10, 35, 125, 240, 325, 800),
        "horviks_armour_shop" to listOf(60, 210, 750, 1950, 160, 560, 2000, 3840, 5200, 280, 850, 750),
        "lowes_archery_emporium" to listOf(1, 3, 12, 32, 80, 1, 50, 80, 100, 160, 200, 320, 400, 640, 70),
        "zaffs_superior_staffs" to listOf(7000, 15, 200, 1500, 1500, 1500, 1500),
        // Varrock's other shops, read 2026-10-30. The Fancy Clothes Store's items that start at 0 cost the Wiki's price at 0.
        "varrock_general_store" to listOf(1, 1, 182, 1, 2, 650, 5, 13, 1, 1, 1, 1, 2),
        "auburys_rune_shop" to listOf(4, 4, 4, 4, 3, 3, 90, 180, 430, 430, 430, 430, 330, 9950),
        "thessalias_fine_clothes" to listOf(2, 21, 6, 6, 2, 2, 2, 2, 2, 30, 5, 5),
        "fancy_clothes_store" to listOf(2, 2, 41, 65, 13, 1, 1, 7, 7, 6, 6, 2, 2, 2, 2, 2, 2, 13, 16, 19, 20, 26),
        "ye_olde_tea_shoppe" to listOf(10),
    )

    @Test
    fun `the shop file reads with every item and keeper, and its prices are the Wiki's`() {
        val shops = realShops()
        assertEquals(emptyList(), shops.warnings)
        assertEquals(
            listOf("lumbridge_general_store", "bobs_brilliant_axes", "draynor_wine_shop", "draynor_seed_market", "diangos_toy_store") + wikiSoldAt.keys,
            shops.all.map { it.key },
        )
        assertEquals(shops["diangos_toy_store"], shops.ofNpc(8693))
        assertEquals(shops["al_kharid_general_store"], shops.ofNpc(2818))
        assertEquals(shops["alis_discount_wares"], shops.ofNpc(3533))
        // The Al Kharid General Store won't buy silk (the Wiki's page); no other shop refuses anything.
        assertEquals(mapOf("al_kharid_general_store" to setOf(950)), shops.all.filter { it.refuses.isNotEmpty() }.associate { it.key to it.refuses })
        assertEquals(shops["lumbridge_general_store"], shops.ofNpc(2814))
        assertEquals(shops["bobs_brilliant_axes"], shops.ofNpc(10619))
        for (type in shops.all) {
            val shop = Shop(type)
            val sold = wikiSoldAt[type.key]
            if (sold != null) {
                assertEquals(sold, type.stock.map { shop.buyPrice(it.type.id, it.type.cost, it.count) }, type.key)
                continue
            }
            val wiki = wikiPrices.getValue(type.key)
            val prices = type.stock.map {
                // ShopScreen.canSell refuses an untradeable item, so it has no "bought at" price.
                shop.buyPrice(it.type.id, it.type.cost, it.count) to if (it.type.tradeable) shop.sellPrice(it.type.id, it.type.cost, it.count) else null
            }
            assertEquals(wiki, prices, type.key)
        }
    }

    @Test
    fun `a shop missing from the cache is left out with a warning`() {
        val shops = ShopTypes.parse(shopFile, NpcTypes.NONE, ObjTypes.NONE)
        assertEquals(emptyList(), shops.all)
        assertEquals(21, shops.warnings.size)
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
    fun `Diango's prices move 0,2% an item, and he sells the Chronicle one to a player`() {
        val diango = Shop(realShops()["diangos_toy_store"]!!)
        val brownHorsey = 2520
        // 150% at 5 in stock; with one bought, 150.2% of 100 still rounds to 150 (2% would make it 152).
        assertEquals(150, diango.buyPrice(brownHorsey, 100, 5))
        assertEquals(150, diango.buyPrice(brownHorsey, 100, 4))
        assertEquals(151, diango.buyPrice(brownHorsey, 100, 0))
        assertTrue(diango.type.onePerPlayer(13660))
        assertFalse(diango.type.onePerPlayer(brownHorsey))
    }

    @Test
    fun `Olivia's overstock over 200 comes down 1% a minute, and one seed a minute below that`() {
        val olivia = Shop(realShops()["draynor_seed_market"]!!)
        val potato = 5318
        // 20 to start; 1,000 more is 1,000 over: 10 a minute.
        olivia.put(potato, 1000)
        olivia.restock(100)
        assertEquals(1010, olivia.count(potato))
        olivia.restock(200)
        assertEquals(1001, olivia.count(potato))
        // 201 over is still over 200: 1% rounds down to 2. At 200 over, one at a time.
        olivia.take(0, 1001 - 221)
        olivia.restock(300)
        assertEquals(219, olivia.count(potato))
        olivia.restock(400)
        assertEquals(218, olivia.count(potato))
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
