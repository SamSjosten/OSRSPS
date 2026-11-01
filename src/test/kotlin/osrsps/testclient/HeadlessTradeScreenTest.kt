package osrsps.testclient

import java.nio.file.Path
import osrsps.game.Inventory
import osrsps.game.ItemPrices
import osrsps.game.MemorySaveStore
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerSave
import osrsps.game.SaveStore
import osrsps.game.World
import osrsps.game.trade.Trades
import osrsps.map.CollisionMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** The first trade screen over the real protocol: offering, removing, the partner's view, values and accepts. */
class HeadlessTradeScreenTest {
    private fun server(dir: Path, saves: SaveStore = MemorySaveStore()) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(QUEST_ITEM + 1)
        // As in the cache: coins lack the Grand Exchange flag (opcode 65), yet players trade them.
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        objs[AXE] = ObjType(AXE, "Bronze axe", tradeable = true)
        objs[QUEST_ITEM] = ObjType(QUEST_ITEM, "Ghostspeak amulet")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), cycleMillis = millis,
            // Coins have no Grand Exchange price: they count 1 each anyway.
            itemPrices = ItemPrices(mapOf(AXE to ItemPrices.Entry("Bronze axe", 16, null))),
            saves = saves,
        )
    }

    /**
     * Logs both in beside each other, gives Alice her items (unless [again], a second trade in a row), and opens the
     * trade.
     */
    private fun trading(server: TestServer, again: Boolean = false, body: (alice: HeadlessClient, bob: HeadlessClient) -> Any?) {
        server.login("alice").use { alice ->
            server.login("bob").use { bob ->
                bob.walk(3223, 3218)
                bob.await("bob's walk") { bob.position?.takeIf { it.x == 3223 } }
                if (!again) {
                    for (command in listOf("::item $COINS 1000", "::item $AXE 2", "::item $QUEST_ITEM")) alice.command(command)
                    alice.await("her items") { alice.inventory(Inventory.INV).filterNotNull().takeIf { it.size == 4 } }
                }
                alice.playerOp(bob.index, 4)
                bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                bob.playerOp(alice.index, 4)
                alice.await("trading") { alice.openInterfaces().takeIf { Trades.MAIN in it } }
                bob.await("trading") { bob.openInterfaces().takeIf { Trades.MAIN in it } }
                body(alice, bob)
                for (client in listOf(alice, bob)) {
                    client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                }
            }
        }
    }

    @Test
    fun `the offer panels give ops 1 to 5 and Examine, op 10`() {
        assertEquals(0b10_0001_1111, Trades.OFFER_OPS)
        assertEquals(1 shl 9, Trades.EXAMINE_OP)
    }

    @Test
    fun `offered items leave the inventory, show on both screens with their value, and come back on a decline`(@TempDir dir: Path) {
        server(dir).use { server ->
            trading(server) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 2, sub = 0, obj = COINS) // Offer-5
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = 1, obj = AXE) // Offer-All: both axes
                alice.await("her offer") { alice.inventory(Trades.TRADEOFFER).filterNotNull().takeIf { it.size == 3 } }
                assertEquals(listOf(InvObj(COINS, 5), InvObj(AXE, 1), InvObj(AXE, 1)), alice.inventory(Trades.TRADEOFFER).filterNotNull())
                val other = bob.await("bob's view of it") { bob.inventory(Trades.TRADEOFFER + HeadlessClient.OTHER_INV).filterNotNull().takeIf { it.size == 3 } }
                assertEquals(listOf(InvObj(COINS, 5), InvObj(AXE, 1), InvObj(AXE, 1)), other)
                alice.await("her inventory") { alice.inventory(Inventory.INV).takeIf { it.getOrNull(0) == InvObj(COINS, 995) && it.getOrNull(1) == null } }
                // 5 coins at 1 (fixed, as they aren't on the Grand Exchange) and two axes at 16.
                bob.await("the value") { bob.text(Trades.MAIN, Trades.MAIN_OTHER_HEADER)?.takeIf { it == "[trade.other_offer name=alice value=37]" } }
                assertEquals("[trade.your_offer value=0]", bob.text(Trades.MAIN, Trades.MAIN_YOUR_HEADER))
                // Alice now has three free slots more: 27 - 4 + 3... her inventory has coins and the quest item left.
                bob.await("her free slots") { bob.text(Trades.MAIN, Trades.MAIN_FREE_SPACE)?.takeIf { it == "[trade.free_slots name=alice count=26]" } }
                // While trading, a save counts the offer as still hers.
                val save = PlayerSave.of(server.world.playerNamed("alice")!!)
                assertEquals(1000L, save.inventory.values.filter { it.id == COINS }.sumOf { it.count.toLong() })
                assertEquals(2, save.inventory.values.count { it.id == AXE })
                bob.ifButton(Trades.MAIN, Trades.MAIN_DECLINE, 1)
                alice.awaitMessage("declined") { it == "[trade.declined]" }
                alice.await("her coins back") { alice.inventory(Inventory.INV).firstOrNull()?.takeIf { it == InvObj(COINS, 1000) } }
                val p = server.world.playerNamed("alice")!!
                assertEquals(1000L, p.inventory.count(COINS))
                assertEquals(2L, p.inventory.count(AXE))
                assertNull(p.trade)
            }
        }
    }

    @Test
    fun `untradeable items are refused, and removing marks the slot and clears both accepts`(@TempDir dir: Path) {
        server(dir).use { server ->
            trading(server) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 3, obj = QUEST_ITEM)
                alice.awaitMessage("refused") { it == "[trade.untradeable]" }
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 3, sub = 0, obj = COINS) // Offer-10
                alice.await("10 coins") { alice.inventory(Trades.TRADEOFFER).firstOrNull()?.takeIf { it == InvObj(COINS, 10) } }
                alice.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                alice.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                alice.await("waiting") { alice.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.waiting]" } }
                bob.await("she accepted") { bob.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                // Remove-5 from her offer: the accept is off, and the slot flashes on both screens.
                alice.ifButton(Trades.MAIN, Trades.MAIN_YOUR_OFFER, 2, sub = 0, obj = COINS)
                alice.await("5 left") { alice.inventory(Trades.TRADEOFFER).firstOrNull()?.takeIf { it == InvObj(COINS, 5) } }
                alice.await("not waiting") { alice.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "" } }
                bob.await("not accepted") { bob.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "" } }
                assertTrue(alice.clientScripts().any { it.id == Trades.TRADE_SLOT_CHANGED_SCRIPT && it.args == listOf(0, 0) })
                bob.await("bob's flasher") { bob.clientScripts().firstOrNull { it.id == Trades.TRADE_SLOT_CHANGED_SCRIPT && it.args == listOf(1, 0) } }
                // Offer-X: 300 more coins through the amount prompt.
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 5, sub = 0, obj = COINS)
                alice.awaitTicks(1)
                alice.resumeCount(300)
                alice.await("305") { alice.inventory(Trades.TRADEOFFER).firstOrNull()?.takeIf { it == InvObj(COINS, 305) } }
            }
        }
    }

    @Test
    fun `clicking elsewhere in the world or an admin command leaves the trade with everything given back`(@TempDir dir: Path) {
        server(dir).use { server ->
            trading(server) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = 0, obj = COINS)
                alice.await("all the coins") { alice.inventory(Trades.TRADEOFFER).firstOrNull()?.takeIf { it == InvObj(COINS, 1000) } }
                alice.command("::item 995 5")
                bob.awaitMessage("declined") { it == "[trade.declined]" }
                alice.await("her coins back, and the command's") { alice.inventory(Inventory.INV).firstOrNull()?.takeIf { it == InvObj(COINS, 1005) } }
            }
        }
    }

    @Test
    fun `both accepting twice swaps the offers in one go and saves both players together`(@TempDir dir: Path) {
        val saves = MemorySaveStore()
        server(dir, saves).use { server ->
            trading(server) { alice, bob ->
                bob.command("::item $AXE 1")
                // The decline can reach Alice before Bob's command reply, so look through everything she has.
                alice.await("declined") { alice.messages().firstOrNull { it.text == "[trade.declined]" } }
            }
            // Trade again, Bob with an axe this time, offered both ways.
            trading(server, again = true) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 3, sub = 0, obj = COINS) // Offer-10
                bob.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 0, obj = AXE)
                bob.await("both offers") { bob.inventory(Trades.TRADEOFFER + HeadlessClient.OTHER_INV).firstOrNull()?.takeIf { it == InvObj(COINS, 10) } }
                alice.await("bob's offer") { alice.inventory(Trades.TRADEOFFER + HeadlessClient.OTHER_INV).firstOrNull()?.takeIf { it == InvObj(AXE, 1) } }
                alice.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                alice.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                bob.await("she accepted") { bob.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                bob.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                alice.await("the second screen") { alice.openInterfaces().takeIf { Trades.CONFIRM in it } }
                bob.await("the second screen") { bob.openInterfaces().takeIf { Trades.CONFIRM in it } }
                assertEquals("[trade.give value=10]", alice.await("her give header") { alice.text(Trades.CONFIRM, Trades.CONFIRM_GIVE) })
                assertEquals("[trade.receive value=16]", alice.text(Trades.CONFIRM, Trades.CONFIRM_RECEIVE))
                assertEquals("[trade.are_you_sure]", alice.text(Trades.CONFIRM, Trades.CONFIRM_TITLE))
                // The first screen's Offer does nothing now.
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 0, obj = COINS)
                alice.ifButton(Trades.CONFIRM, Trades.CONFIRM_ACCEPT, 1)
                bob.await("she accepted again") { bob.text(Trades.CONFIRM, Trades.CONFIRM_TITLE)?.takeIf { it == "[trade.other_accepted]" } }
                bob.ifButton(Trades.CONFIRM, Trades.CONFIRM_ACCEPT, 1)
                for (client in listOf(alice, bob)) client.await("done") { client.messages().firstOrNull { it.text == "[trade.accepted]" } }
                alice.await("screens shut") { alice.openInterfaces().takeIf { Trades.CONFIRM !in it && Trades.SIDE !in it } }
                alice.await("her new inventory") { alice.inventory(Inventory.INV).filterNotNull().takeIf { InvObj(COINS, 990) in it && it.count { o -> o.id == AXE } == 3 } }
                bob.await("his new inventory") { bob.inventory(Inventory.INV).filterNotNull().takeIf { it == listOf(InvObj(COINS, 10)) } }
                // Both saves were written as it completed, naming the same trade, and the journal is gone.
                val aliceSave = PlayerSave.fromJson(saves.saves.getValue("alice"))
                val bobSave = PlayerSave.fromJson(saves.saves.getValue("bob"))
                assertEquals(990L, aliceSave.inventory.values.filter { it.id == COINS }.sumOf { it.count.toLong() })
                assertEquals(3, aliceSave.inventory.values.count { it.id == AXE })
                assertEquals(listOf(osrsps.game.InvObj(COINS, 10)), bobSave.inventory.values.toList())
                assertTrue(aliceSave.lastTrade != null && aliceSave.lastTrade == bobSave.lastTrade)
                assertNull(saves.journal)
            }
        }
    }

    /** Saves in memory, but the trade journal can't be written: a full disk. */
    private class NoJournalStore(val inner: MemorySaveStore = MemorySaveStore()) : SaveStore by inner {
        override fun writeJournal(text: String) = throw java.io.IOException("disk full (test)")
    }

    @Test
    fun `a trade whose journal can't be written is undone and ends as a decline, everything back`(@TempDir dir: Path) {
        val saves = NoJournalStore()
        server(dir, saves).use { server ->
            trading(server) { alice, bob ->
                bob.command("::item $AXE 1")
                alice.await("declined") { alice.messages().firstOrNull { it.text == "[trade.declined]" } }
            }
            trading(server, again = true) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 3, sub = 0, obj = COINS) // Offer-10
                bob.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 0, obj = AXE)
                bob.await("both offers") { bob.inventory(Trades.TRADEOFFER + HeadlessClient.OTHER_INV).firstOrNull()?.takeIf { it == InvObj(COINS, 10) } }
                alice.await("bob's offer") { alice.inventory(Trades.TRADEOFFER + HeadlessClient.OTHER_INV).firstOrNull()?.takeIf { it == InvObj(AXE, 1) } }
                alice.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                alice.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                bob.await("she accepted") { bob.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                bob.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                alice.await("the second screen") { alice.openInterfaces().takeIf { Trades.CONFIRM in it } }
                alice.ifButton(Trades.CONFIRM, Trades.CONFIRM_ACCEPT, 1)
                bob.await("she accepted again") { bob.text(Trades.CONFIRM, Trades.CONFIRM_TITLE)?.takeIf { it == "[trade.other_accepted]" } }
                bob.ifButton(Trades.CONFIRM, Trades.CONFIRM_ACCEPT, 1)
                alice.await("screens shut") { alice.openInterfaces().takeIf { Trades.CONFIRM !in it } }
                bob.await("screens shut") { bob.openInterfaces().takeIf { Trades.CONFIRM !in it } }
                alice.await("her coins back") { alice.inventory(Inventory.INV).filterNotNull().takeIf { InvObj(COINS, 1000) in it } }
                bob.await("his axe back") { bob.inventory(Inventory.INV).filterNotNull().takeIf { it == listOf(InvObj(AXE, 1)) } }
                assertTrue(alice.messages().none { it.text == "[trade.accepted]" } && bob.messages().none { it.text == "[trade.accepted]" })
                assertNull(saves.inner.journal)
            }
        }
    }

    @Test
    fun `a player without room for the other's offer can't move on, and both accepts go off`(@TempDir dir: Path) {
        server(dir).use { server ->
            trading(server) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 1, obj = AXE)
                alice.await("her offer") { alice.inventory(Trades.TRADEOFFER).firstOrNull() }
                // Bob's inventory is full of axes.
                bob.command("::item $AXE 28")
                alice.await("declined") { alice.messages().firstOrNull { it.text == "[trade.declined]" } }
                bob.await("his axes") { bob.inventory(Inventory.INV).filterNotNull().takeIf { it.size == 28 } }
            }
            trading(server, again = true) { alice, bob ->
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 1, obj = AXE)
                alice.await("her offer") { alice.inventory(Trades.TRADEOFFER).firstOrNull() }
                alice.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                alice.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                bob.await("she accepted") { bob.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                bob.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                bob.await("no room") { bob.messages().firstOrNull { it.text == "[trade.no_room]" } }
                alice.await("his no room") { alice.messages().firstOrNull { it.text == "[trade.other_no_room]" } }
                alice.await("her accept off") { alice.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "" } }
                assertTrue(Trades.CONFIRM !in alice.openInterfaces())
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val AXE = 1351
        const val QUEST_ITEM = 4250
    }
}
