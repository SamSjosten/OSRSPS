package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.ItemPrices
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.ge.PriceChecker
import osrsps.map.CollisionMap

/**
 * The guide prices screen with the backpack refilled behind it (fourth exploit review, 30 Oct 2026): taking off a worn
 * item into the slot the checked items left. What no longer fits used to go to the bank, from anywhere, and a death with
 * the screen open put it in the bank instead of the grave.
 */
class HeadlessPriceCheckerOverflowTest {
    private val logs = ObjType(LOGS, "Logs", cost = 4, tradeable = true, certlink = NOTED_LOGS, internalName = "logs")
    private val notedLogs = ObjType(NOTED_LOGS, "Logs", cost = 4, stackable = true, tradeable = true, certlink = LOGS, certtemplate = 799)
    private val axe = ObjType(
        AXE, "Bronze axe", cost = 16, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        internalName = "bronze_axe",
    )
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))

    private fun server(dir: Path, prices: ItemPrices = PRICES) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes.of(coins, logs, notedLogs, axe), npcTypes = NpcTypes.of(grave), itemPrices = prices,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    /**
     * Wields the axe, then fills the backpack: 27 unnoted logs and a stack of 1,000 noted logs, 28 slots. Opens the
     * guide prices screen and puts the noted stack in it (Add-All), then takes the axe off into the slot that freed:
     * the backpack is full again with the 1,000 noted logs still in the price checker.
     */
    private fun HeadlessClient.fillBehindThePriceChecker() {
        await("the login rebuild") { position }
        command("item $AXE 1")
        val axeSlot = slotOf(AXE)
        // Wield is iop2, IF_BUTTONX op 3 on the inventory.
        ifButton(INVENTORY, INVENTORY_ITEMS, 3, axeSlot, AXE)
        await("the axe worn") { inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == AXE } }
        command("item $LOGS 27")
        command("item $NOTED_LOGS 1000")
        await("a full backpack") { if (inventory(INV).count { it != null } == 28) Unit else null }

        ifButton(EquipmentTab.WORNITEMS, PriceChecker.WORNITEMS_BUTTON, 1)
        await("the guide prices screen") { if (PriceChecker.MAIN in openInterfaces()) Unit else null }
        val noteSlot = slotOf(NOTED_LOGS)
        ifButton(PriceChecker.SIDE, PriceChecker.SIDE_ITEMS, 4, noteSlot, NOTED_LOGS)
        await("the notes in the price checker") { if (held(NOTED_LOGS) == 0L) Unit else null }

        // Remove on the worn axe: nothing closes the price checker, and the axe takes the notes' slot.
        ifButton(EquipmentTab.WORNITEMS, WORNITEMS_WEAPON, 1)
        await("the axe back in the full backpack") { if (held(AXE) == 1L && inventory(INV).count { it != null } == 28) Unit else null }
        assertTrue(PriceChecker.MAIN in openInterfaces(), "the price checker is still open")
    }

    @Test
    fun `what no longer fits drops under the player, never into the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.fillBehindThePriceChecker()
                val player = server.world.playerNamed("alice")!!

                client.closeModal()
                client.await("the price checker closed") { if (PriceChecker.MAIN in client.openInterfaces()) null else Unit }
                client.await("the notes under the player") {
                    val under = server.world.groundObjs.visibleAt(player, player.x, player.z, player.level)
                    if (under.filter { it.id == NOTED_LOGS }.sumOf { it.count } == 1_000) Unit else null
                }
                assertEquals(0L, player.bank.count(LOGS))
                assertEquals(0L, player.bank.count(NOTED_LOGS))
                assertEquals(27L, client.held(LOGS))
            }
        }
    }

    @Test
    fun `a death with the screen open sorts the checked items with the rest, into the grave`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.fillBehindThePriceChecker()
                val player = server.world.playerNamed("alice")!!

                assertEquals("Hit yourself for 10: 0/10 hp, dead", client.commandReply("hit 99 me") { it.startsWith("Hit yourself") })
                client.await("the respawn", timeoutMillis = 10_000) { if (!player.dead) Unit else null }
                assertEquals(0L, player.bank.count(LOGS))
                assertEquals(0L, player.bank.count(NOTED_LOGS))
                assertTrue(player.priceChecker.contents().isEmpty())
                // Every log is worth the same; the backpack's come first, so three of those are kept and every note
                // goes to the grave with the other 24.
                assertEquals(3L, player.inventory.count(LOGS))
                assertEquals(0L, player.inventory.count(NOTED_LOGS))
                val grave = server.world.deaths.graveOf(player)!!.items
                assertEquals(1_000L, grave.count(NOTED_LOGS))
                assertEquals(24L, grave.count(LOGS))
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val LOGS = 1511
        const val NOTED_LOGS = 1512
        const val AXE = 1351
        const val INV = 93
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0

        /** wornitems:slot3, the weapon. */
        const val WORNITEMS_WEAPON = 18

        val PRICES = ItemPrices(
            mapOf(
                LOGS to ItemPrices.Entry("Logs", 1_000, 15_000),
                AXE to ItemPrices.Entry("Bronze axe", 50, 40),
            ),
        )
    }
}
