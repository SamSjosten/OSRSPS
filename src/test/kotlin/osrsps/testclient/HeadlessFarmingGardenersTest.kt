package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.FarmsRoads
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Farming step 8 through the headless client, at Lumbridge's tree patch with Fayeth and a Tool Leprechaun beside it:
 * Fayeth asks a basket of tomatoes to look after an oak, takes it noted through Pay, and the paid oak grows up with
 * diseases on; a grown oak chopped down for 200 coins from her menu and through Pay; her advice, her shop, and what
 * she says of items shown her; and the leprechaun noting potatoes and refusing what isn't grown. The patch is
 * `farming_transmit_a`, bits 0 to 7 of varp 529. The rl-client script test-runner/examples/farming-gardeners.json
 * does the same in the real client.
 */
class HeadlessFarmingGardenersTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun gardener(id: Int, name: String, internal: String) =
        NpcType(id, name, ops = listOf("Talk-to", null, "Pay", null, "Trade"), internalName = internal)

    private val fayeth = gardener(FarmsRoads.FAYETH, "Fayeth", "farming_gardener_tree_4")
    private val others = listOf(
        gardener(FarmsRoads.DREVEN, "Dreven", "farming_gardener_bush_1"),
        gardener(FarmsRoads.VASQUEN, "Vasquen", "farming_gardener_hops_3"),
        gardener(FarmsRoads.TREZNOR, "Treznor", "farming_gardener_tree_3"),
    )
    private val leprechaun = NpcType(FarmsRoads.TOOL_LEPRECHAUN, "Tool Leprechaun", ops = listOf("Talk-to", null, "Exchange", "Deposit-all", null), internalName = "farming_tools_leprechaun")

    private val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }.also { objs ->
        for ((id, name) in mapOf(RAKE to "Rake", SPADE to "Spade", WEEDS to "Weeds", SAPLING to "Oak sapling", WATERMELON to "Watermelon")) {
            objs[id] = ObjType(id, name)
        }
        objs[COINS] = ObjType(COINS, "Coins", stackable = true, tradeable = true)
        // Items with banknotes: the payment, potatoes, logs.
        for ((id, name) in mapOf(TOMATOES_5 to "Tomatoes(5)", POTATO to "Potato", LOGS to "Logs")) {
            objs[id] = ObjType(id, name, tradeable = true, certlink = id + 1)
            objs[id + 1] = ObjType(id + 1, name, stackable = true, tradeable = true, certlink = id, certtemplate = 799)
        }
        for ((id, name, price) in listOf(Triple(6036, "Plant cure", 40), Triple(6032, "Compost", 20), Triple(5350, "Empty plant pot", 1), Triple(5340, "Watering can(8)", 8), Triple(5325, "Gardening trowel", 12), Triple(5343, "Seed dibber", 6))) {
            objs[id] = ObjType(id, name, cost = price)
        }
    }.let { ObjTypes(it) }

    private val npcTypes = NpcTypes.of(fayeth, leprechaun, *others.toTypedArray())

    private val shops = ShopTypes.parse(ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), npcTypes, objs)
        .let { all -> ShopTypes(listOf(all["farming_supplies"]!!)) }

    private fun server(dir: Path, diseases: Boolean = false) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = objs, npcTypes = npcTypes,
            npcSpawns = listOf(NpcSpawn(fayeth, X + 2, Z, source = "test"), NpcSpawn(leprechaun, X + 3, Z + 2, source = "test")),
            shopTypes = shops,
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_A to VarbitType(529, 0, 7), ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(TREE to LocType(TREE, "null", List(5) { null }, LocCollisionType(width = 3, length = 3), multiloc = true))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(TREE, LocShape.CENTREPIECE_STRAIGHT, 0, TREE_X, TREE_Z, 0)) },
            farmingClock = clock::get,
            farmingDiseases = diseases,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.tree() = varp(529) and 0xFF

    private fun HeadlessClient.awaitTree(value: Int) = await("the tree patch at $value (${tree()})") { if (tree() == value) Unit else null }

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) =
        await("$count of item $id (${count(id)})") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.npc(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.op(id: Int, op: Int) = npcOp(npc(id).index, op)

    private fun HeadlessClient.useOnNpc(id: Int, item: Int) =
        npcTarget(npc(id).index, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    /** Each of [keys] in turn, an NPC's or the player's line, continued past. */
    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String): DialogueView.Menu {
        val menu = awaitDialogue("the menu ${keys.toList()}") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
        return menu
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.setUp() {
        await("the login rebuild") { position }
        command("tele $X $Z")
        await("beside the tree patch") { position?.takeIf { it.x == X && it.z == Z } }
        command("setlevel farming 99")
        await("99 Farming") { if (stat(FARMING)?.level == 99) Unit else null }
        command("farmclock pause")
    }

    /** Rakes the patch and plants an oak sapling in it. */
    private fun HeadlessClient.plantOak() {
        give(RAKE)
        give(SPADE)
        locOp(TREE, TREE_X, TREE_Z, 1)
        awaitTree(3)
        give(SAPLING)
        locTarget(TREE, TREE_X, TREE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(SAPLING), SAPLING)
        awaitTree(8)
    }

    @Test
    fun `Fayeth looks after an oak for a basket of tomatoes, noted, and the paid oak grows up with diseases on`(@TempDir dir: Path) {
        server(dir, diseases = true).use { server ->
            server.login("alice").use { client ->
                client.setUp()
                // Nothing planted: no deal.
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*MENU)
                client.chooseOption(1)
                client.chats("gardener.look_after", "gardener.no_sapling")
                client.awaitClosed()

                // An oak, and no tomatoes: she won't work for free.
                client.plantOak()
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*MENU)
                client.chooseOption(1)
                client.chats("gardener.look_after")
                client.awaitDialogue("the price") { it is DialogueView.Chat && it.text == "[gardener.want payment=[gardener.payment_oak]]" }
                client.continueDialogue()
                client.chats("gardener.dont_have", "gardener.not_free")
                client.awaitClosed()
                client.op(FarmsRoads.FAYETH, FarmsRoads.PAY)
                client.awaitDialogue("Pay's price") { it is DialogueView.Chat && it.text == "[gardener.pay_want payment=[gardener.payment_oak]]" }
                client.continueDialogue()
                client.awaitClosed()

                // A noted basket does: Pay takes it.
                client.give(TOMATOES_5 + 1, 2)
                client.op(FarmsRoads.FAYETH, FarmsRoads.PAY)
                client.chats("gardener.paid")
                client.awaitClosed()
                client.awaitCount(TOMATOES_5 + 1, 1)
                client.op(FarmsRoads.FAYETH, FarmsRoads.PAY)
                client.chats("gardener.pay_already")
                client.awaitClosed()
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*MENU)
                client.chooseOption(1)
                client.chats("gardener.look_after", "gardener.already", "gardener.forgot")
                client.awaitClosed()

                // Diseases are on, and the paid oak grows through all four 40-minute growths.
                client.command("farmtick 32")
                client.awaitTree(12)
            }
        }
    }

    @Test
    fun `a sick oak is refused, and a grown oak is chopped down for 200 coins from the menu and through Pay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.setUp()
                client.plantOak()
                client.command("farmtick 8")
                client.awaitTree(9)
                client.command("farmdisease lumbridge_tree")
                client.await("the oak ill") { if (client.tree() != 9) Unit else null }
                client.op(FarmsRoads.FAYETH, FarmsRoads.PAY)
                client.chats("gardener.diseased")
                client.awaitClosed()
                // Cleared and planted again, grown this time.
                client.command("farmtick 8")
                client.await("the oak dead") { if (client.tree() in 137..141) Unit else null }
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.awaitTree(3)
                client.give(SAPLING)
                client.locTarget(TREE, TREE_X, TREE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(SAPLING), SAPLING)
                client.awaitTree(8)
                client.command("farmtick 32")
                client.awaitTree(12)

                // Woodcutting 1 is too low for an oak: straight to her price, and no coins.
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*CHOP_MENU)
                client.chooseOption(2)
                client.chats("gardener.chop", "gardener.hard_work", "gardener.no_coins")
                client.awaitClosed()
                client.give(COINS, 450)
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*CHOP_MENU)
                client.chooseOption(2)
                client.chats("gardener.chop", "gardener.hard_work")
                client.menu("gardener.heres_200", "gardener.too_much")
                client.chooseOption(1)
                client.chats("gardener.heres_200")
                client.awaitClosed()
                client.awaitTree(3)
                client.awaitCount(COINS, 250)

                // Again with the level to chop it: she asks why; then Pay does it with no talk.
                client.give(SAPLING)
                client.locTarget(TREE, TREE_X, TREE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(SAPLING), SAPLING)
                client.awaitTree(8)
                client.command("farmtick 32")
                client.awaitTree(12)
                client.command("setlevel woodcutting 99")
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*CHOP_MENU)
                client.chooseOption(2)
                client.chats("gardener.chop", "gardener.chop_yourself")
                client.menu("gardener.do_it_myself", "gardener.rather_pay")
                client.chooseOption(1)
                client.chats("gardener.do_it_myself")
                client.awaitClosed()
                assertEquals(12, client.tree())
                client.op(FarmsRoads.FAYETH, FarmsRoads.PAY)
                val menu = client.menu("gardener.yes", "gardener.no")
                assertEquals("[gardener.chop_title]", menu.title)
                client.chooseOption(1)
                client.awaitTree(3)
                client.awaitCount(COINS, 50)
            }
        }
    }

    @Test
    fun `Fayeth gives advice, opens her shop, and tells compost from supercompost`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.setUp()
                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*MENU)
                client.chooseOption(2)
                client.chats("gardener.advice")
                val advice = client.awaitDialogue("her advice") { it is DialogueView.Chat } as DialogueView.Chat
                assertTrue(Regex("""\[gardener\.advice_([1-9]|1[0-7])]""").matches(advice.text), advice.text)
                client.continueDialogue()
                client.menu(*MENU)
                client.chooseOption(4)
                client.chats("gardener.another_time")
                client.awaitClosed()

                client.op(FarmsRoads.FAYETH, FarmsRoads.TALK_TO)
                client.menu(*MENU)
                client.chooseOption(3)
                client.chats("gardener.sell", "gardener.sell_what")
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("the stock") { client.inventory(SHOP_INV).firstOrNull()?.takeIf { it.id == 6036 && it.count == 1000 } }
                client.closeModal()
                client.await("the shop closed") { if (ShopScreen.MAIN !in client.openInterfaces()) Unit else null }
                client.op(FarmsRoads.FAYETH, FarmsRoads.TRADE)
                client.await("the shop through Trade") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.closeModal()
                client.await("the shop closed") { if (ShopScreen.MAIN !in client.openInterfaces()) Unit else null }

                for ((item, key) in listOf(WEEDS to "compostable", WATERMELON to "supercompostable", SPADE to "not_compostable")) {
                    client.give(item)
                    client.useOnNpc(FarmsRoads.FAYETH, item)
                    client.chats("gardener.$key")
                    client.awaitClosed()
                }
            }
        }
    }

    @Test
    fun `a Tool Leprechaun notes every potato in the pack, and refuses banknotes, logs and a spade`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.setUp()
                client.give(POTATO, 5)
                client.useOnNpc(FarmsRoads.TOOL_LEPRECHAUN, POTATO)
                client.awaitCount(POTATO + 1, 5)
                assertEquals(0, client.count(POTATO))

                for ((item, key) in listOf(POTATO + 1 to "is_note", LOGS to "no_notes", SPADE to "no_such_note")) {
                    if (client.count(item) == 0) client.give(item)
                    client.useOnNpc(FarmsRoads.TOOL_LEPRECHAUN, item)
                    client.chats("tool_leprechaun_note.$key")
                    client.awaitClosed()
                }
                assertEquals(1, client.count(LOGS))
            }
        }
    }

    private companion object {
        val MENU = arrayOf("gardener.look_after", "gardener.advice", "gardener.sell", "gardener.another_time")
        val CHOP_MENU = arrayOf("gardener.look_after", "gardener.chop", "gardener.advice", "gardener.sell", "gardener.another_time")
        const val OBJS = 25_000
        const val BACKPACK = 93
        const val SHOP_INV = 894
        const val FARMING = 19
        const val TRANSMIT_A = 4771
        const val TREE = 8391
        const val TREE_X = 3192
        const val TREE_Z = 3230
        const val X = 3193
        const val Z = 3229
        const val RAKE = 5341
        const val SPADE = 952
        const val WEEDS = 6055
        const val SAPLING = 5370
        const val COINS = 995
        const val TOMATOES_5 = 5968
        const val POTATO = 1942
        const val LOGS = 1511
        const val WATERMELON = 5982
    }
}
