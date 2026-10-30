package osrsps.testclient

import java.nio.file.Path
import osrsps.game.Inventory
import osrsps.game.ItemPrices
import osrsps.game.MemorySaveStore
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerSave
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.trade.Trades
import osrsps.map.CollisionMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Trades that try to go wrong: the ways players have duped or scammed through a trade window, each one held to
 * "everything is exactly where it was, or exactly swapped".
 */
class HeadlessTradeSafetyTest {
    private fun server(dir: Path, saves: MemorySaveStore = MemorySaveStore()) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(AXE + 1)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true) // no Grand Exchange flag, as in the cache
        objs[AXE] = ObjType(AXE, "Bronze axe", tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), cycleMillis = millis,
            itemPrices = ItemPrices(mapOf(AXE to ItemPrices.Entry("Bronze axe", 16, null))),
            saves = saves,
        )
    }

    /** As [server], with the two Accept trade delay varbits in the var types. */
    private fun delayServer(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(AXE + 1)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), cycleMillis = millis,
            varTypes = VarTypes(
                6_000,
                mapOf(Trades.VARBIT_ACCEPT_DELAY to VarbitType(VARP_DELAY, 0, 3), Trades.VARBIT_DELAY_DISABLED to VarbitType(VARP_DELAY_SETTING, 0, 0)),
            ),
        )
    }

    /** Puts [bob] beside [alice] and opens a trade between them. */
    private fun open(alice: HeadlessClient, bob: HeadlessClient) {
        bob.walk(3223, 3218)
        bob.await("bob's walk") { bob.position?.takeIf { it.x == 3223 } }
        alice.playerOp(bob.index, 4)
        bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
        bob.playerOp(alice.index, 4)
        alice.await("trading") { alice.openInterfaces().takeIf { Trades.MAIN in it } }
        bob.await("trading") { bob.openInterfaces().takeIf { Trades.MAIN in it } }
    }

    private fun HeadlessClient.give(command: String, count: Int) {
        command(command)
        await("the items") { inventory(Inventory.INV).filterNotNull().takeIf { it.size == count } }
    }

    private fun HeadlessClient.sawMessage(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `an Offer-X prompt answered after both accepted adds nothing to the second screen`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.give("::item $COINS 1000", 1)
                    open(alice, bob)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 3, sub = 0, obj = COINS) // Offer-10
                    alice.await("10 coins") { alice.inventory(Trades.TRADEOFFER).firstOrNull()?.takeIf { it == InvObj(COINS, 10) } }
                    bob.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                    bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    alice.await("he accepted") { alice.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                    // Offer-X asks for an amount, and Alice accepts before answering it.
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 5, sub = 0, obj = COINS)
                    alice.awaitTicks(1)
                    alice.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                    alice.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    bob.await("the second screen") { bob.openInterfaces().takeIf { Trades.CONFIRM in it } }
                    alice.resumeCount(900)
                    alice.awaitTicks(2)
                    val p = server.world.playerNamed("alice")!!
                    assertTrue(p.trade != null)
                    assertEquals(listOf(osrsps.game.InvObj(COINS, 10)), osrsps.game.trade.offeredBy(p).map { it.first })
                    assertEquals(990L, p.inventory.count(COINS))
                }
            }
        }
    }

    @Test
    fun `after a change Accept waits 3 seconds, counted down on the client`(@TempDir dir: Path) {
        delayServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.give("::item $COINS 1000", 1)
                    open(alice, bob)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 3, sub = 0, obj = COINS) // Offer-10
                    bob.await("the countdown") { bob.varp(VARP_DELAY).takeIf { it > 0 } }
                    // Too soon: nothing.
                    bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    alice.awaitTicks(2)
                    assertTrue(server.world.playerNamed("bob")!!.trade!!.sideOf(server.world.playerNamed("bob")!!).accepted.not())
                    bob.await("the countdown's end") { bob.varp(VARP_DELAY).takeIf { it == 0 } }
                    bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    alice.await("he accepted") { alice.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }

                }
            }
        }
    }

    @Test
    fun `a player who turned the Accept trade delay off can accept straight after a change`(@TempDir dir: Path) {
        delayServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.give("::item $COINS 1000", 1)
                    // The setting, trade_delay_disabled; set before the trade, as an admin command ends one.
                    bob.command("::varbit ${Trades.VARBIT_DELAY_DISABLED} 1")
                    bob.await("the setting") { bob.varp(VARP_DELAY_SETTING).takeIf { it == 1 } }
                    open(alice, bob)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 3, sub = 0, obj = COINS)
                    alice.await("10 coins") { alice.inventory(Trades.TRADEOFFER).firstOrNull()?.takeIf { it == InvObj(COINS, 10) } }
                    bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    alice.await("he accepted at once") { alice.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                    assertEquals(0, bob.varp(VARP_DELAY))
                }
            }
        }
    }

    @Test
    fun `a dropped connection ends the trade at once, everything back where it was and saved so`(@TempDir dir: Path) {
        val saves = MemorySaveStore()
        server(dir, saves).use { server ->
            server.login("bob").use { bob ->
                val alice = server.login("alice")
                alice.give("::item $AXE 3", 3)
                open(alice, bob)
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = 0, obj = AXE) // Offer-All
                alice.await("her offer") { alice.inventory(Trades.TRADEOFFER).filterNotNull().takeIf { it.size == 3 } }
                bob.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                alice.close()
                bob.sawMessage("[trade.declined]")
                bob.await("his screen shut") { bob.openInterfaces().takeIf { Trades.MAIN !in it } }
                val p = server.world.playerNamed("alice")!!
                assertEquals(3L, p.inventory.count(AXE))
                assertNull(p.trade)
                assertNull(server.world.playerNamed("bob")!!.trade)
                assertEquals(3, PlayerSave.of(p).inventory.values.count { it.id == AXE })
            }
        }
    }

    @Test
    fun `a reconnect before the drop is noticed ends the trade, everything back where it was`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { bob ->
                val alice = server.login("alice")
                alice.give("::item $AXE 3", 3)
                open(alice, bob)
                alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = 0, obj = AXE) // Offer-All
                alice.await("her offer") { alice.inventory(Trades.TRADEOFFER).filterNotNull().takeIf { it.size == 3 } }
                // A new connection comes back with the old one's seed while the old one is still open.
                server.reconnect("alice", alice).use { back ->
                    bob.sawMessage("[trade.declined]")
                    bob.await("his screen shut") { bob.openInterfaces().takeIf { Trades.MAIN !in it } }
                    back.await("her axes back") { back.inventory(Inventory.INV).filterNotNull().takeIf { it.size == 3 } }
                    val p = server.world.playerNamed("alice")!!
                    assertNull(p.trade)
                    assertNull(server.world.playerNamed("bob")!!.trade)
                    assertEquals(3L, p.inventory.count(AXE))
                }
                alice.close()
            }
        }
    }

    @Test
    fun `a trade that would take a stack past the most a slot holds is refused for room, and nothing moves`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.give("::item $COINS 10", 1)
                    bob.give("::item $COINS ${Int.MAX_VALUE}", 1)
                    open(alice, bob)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 1, sub = 0, obj = COINS)
                    alice.await("1 coin") { alice.inventory(Trades.TRADEOFFER).firstOrNull() }
                    alice.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                    alice.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    bob.await("she accepted") { bob.text(Trades.MAIN, Trades.MAIN_STATUS)?.takeIf { it == "[trade.other_accepted]" } }
                    bob.awaitTicks(Trades.ACCEPT_DELAY_TICKS) // the Accept trade delay
                    bob.ifButton(Trades.MAIN, Trades.MAIN_ACCEPT, 1)
                    bob.sawMessage("[trade.no_room]")
                    alice.sawMessage("[trade.other_no_room]")
                    assertTrue(Trades.CONFIRM !in alice.openInterfaces())
                    assertEquals(Int.MAX_VALUE.toLong(), server.world.playerNamed("bob")!!.inventory.count(COINS))
                }
            }
        }
    }

    @Test
    fun `a click naming the wrong item for the slot, or a slot past the end, moves nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.give("::item $COINS 100", 1)
                    open(alice, bob)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = 0, obj = AXE)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = 40, obj = COINS)
                    alice.ifButton(Trades.SIDE, Trades.SIDE_ITEMS, 4, sub = -1, obj = COINS)
                    // Removing what isn't offered.
                    alice.ifButton(Trades.MAIN, Trades.MAIN_YOUR_OFFER, 4, sub = 0, obj = COINS)
                    alice.awaitTicks(2)
                    val p = server.world.playerNamed("alice")!!
                    assertEquals(100L, p.inventory.count(COINS))
                    assertTrue(osrsps.game.trade.offeredBy(p).isEmpty())
                }
            }
        }
    }

    @Test
    fun `nobody can be in two trades, or trade themselves`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        // Alice's client can't offer herself, but a crafted packet naming her own index does nothing.
                        alice.playerOp(alice.index, 4)
                        alice.awaitTicks(3)
                        assertTrue(alice.messages().none { it.text == "[trade.sending]" || it.type == Trades.CHATTYPE_TRADEREQ })
                        open(alice, bob)
                        carol.walk(3222, 3219)
                        carol.await("carol's walk") { carol.position?.takeIf { it.z == 3219 } }
                        carol.playerOp(alice.index, 4)
                        carol.sawMessage("[trade.busy]")
                        assertTrue(alice.messages().none { it.type == Trades.CHATTYPE_TRADEREQ })
                        val a = server.world.playerNamed("alice")!!
                        assertEquals("bob", a.trade?.partnerOf(a)?.username)
                    }
                }
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val AXE = 1351
        const val VARP_DELAY = 5000
        const val VARP_DELAY_SETTING = 5001
    }
}
