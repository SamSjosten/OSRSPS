package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.bank.DepositBox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Rest of Lumbridge step 3b through the headless client: the bank's worn items view (its bonus lines, worn slots and
 * inventory), the inventory's extra option (Wield), and the deposit box with its amounts, buttons and an item used on
 * it. The rl-client script test-runner/examples/lumbridge-deposit-box.json does the same in the real client.
 */
class HeadlessDepositBoxTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(LOGS + 1)
        objs[LOGS] = ObjType(LOGS, "Logs", internalName = "logs")
        objs[COINS] = ObjType(COINS, "Coins", stackable = true, internalName = "coins")
        objs[AXE] = ObjType(
            AXE, "Bronze axe", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
            params = mapOf(SLASH_ATTACK_PARAM to 4), internalName = "bronze_axe",
        )
        val booth = LocType(BOOTH, "Bank booth", ops = listOf(null, "Bank", "Collect", null, null), collision = LocCollisionType(width = 1, length = 1))
        val box = LocType(BOX, "Bank Deposit Box", ops = listOf("Deposit", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(BOOTH to booth, BOX to box)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(BOOTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0))
                add(ZoneLoc(BOX, LocShape.CENTREPIECE_STRAIGHT, 0, 3219, 3218, 0))
            },
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.backpack() = inventory(Inventory.INV).filterNotNull()

    private fun HeadlessClient.bank() = inventory(Inventory.BANK).filterNotNull()

    private fun HeadlessClient.worn(wearpos: Int) = inventory(Inventory.WORN).getOrNull(wearpos)

    private fun HeadlessClient.awaitBank(vararg objs: InvObj) =
        await("the bank to hold ${objs.toList()}") { if (bank() == objs.toList()) Unit else null }

    private fun HeadlessClient.awaitBackpack(vararg objs: InvObj) =
        await("the backpack to hold ${objs.toList()}") { if (backpack() == objs.toList()) Unit else null }

    private fun HeadlessClient.awaitWielded(id: Int?) =
        await("the weapon slot to hold $id") { if (worn(WearPos.WEAPON)?.id == id) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    /** The client only hears of the bank while it is open, so the deposit box's deposits show in the backpack. */
    private fun HeadlessClient.awaitHeld(id: Int, count: Int) =
        await("the backpack to hold $count of $id") { if (backpack().filter { it.id == id }.sumOf { it.count } == count) Unit else null }

    /** Opens the bank at the booth to see what is in it. */
    private fun HeadlessClient.awaitBankAtBooth(vararg objs: InvObj) {
        locOp(BOOTH, 3225, 3218, 2)
        await("the bank") { openInterfaces().takeIf { BankScreen.MAIN in it } }
        awaitBank(*objs)
    }

    @Test
    fun `the bank's worn items view shows the bonuses, wields, removes and banks worn items`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOGS")
                client.command("item $AXE")
                client.await("the items") { if (client.backpack().size == 2) Unit else null }
                client.locOp(BOOTH, 3225, 3218, 2)
                // The events come in packets after the opens.
                client.await("the bank and the worn view's Deposit on the last slot") {
                    if (BankScreen.MAIN in client.openInterfaces() && client.opEnabled(BankScreen.SIDE, BankScreen.SIDE_WORNOPS, 27, 9)) Unit else null
                }
                // Varp if2 has the axe's slot (1) and not the logs'.
                client.await("Wield offered on the axe only") { if (client.varp(BankScreen.VARP_EXTRAOPS_WEAR) == 1 shl 1) Unit else null }
                client.await("the bonus lines") {
                    val slash = client.text(BankScreen.MAIN, BankScreen.WORN_SLASH_ATT)
                    val undead = client.text(BankScreen.MAIN, BankScreen.WORN_TYPE_MULTIPLIER)
                    if (slash == "Slash: +0" && undead == "Undead: +0% ") Unit else null
                }

                // The extra option (op 9) wields it; the bonus line follows and the bit goes.
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, BankScreen.SIDE_EXTRAOP, client.slotOf(AXE), AXE)
                client.awaitWielded(AXE)
                client.await("the slash bonus") { client.text(BankScreen.MAIN, BankScreen.WORN_SLASH_ATT)?.takeIf { it == "Slash: +4" } }
                client.await("no Wield offered") { if (client.varp(BankScreen.VARP_EXTRAOPS_WEAR) == 0) Unit else null }

                // The weapon slot (wornslot3, 71): Remove (op 1), then the worn view's Wear (op 1) and Bank (op 2).
                client.ifButton(BankScreen.MAIN, WORN_WEAPON, 1)
                client.awaitWielded(null)
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_WORNOPS, 1, client.slotOf(AXE), AXE)
                client.awaitWielded(AXE)
                client.ifButton(BankScreen.MAIN, WORN_WEAPON, 2)
                client.awaitBank(InvObj(AXE, 1))
                client.awaitWielded(null)

                // The worn view's Deposit (op 9) puts the whole slot in.
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_WORNOPS, BankScreen.SIDE_EXTRAOP, client.slotOf(LOGS), LOGS)
                client.awaitBank(InvObj(AXE, 1), InvObj(LOGS, 1))
                client.awaitBackpack()
            }
        }
    }

    @Test
    fun `a deposit box takes items by its amounts, the worn items and the inventory`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOGS 8")
                client.command("item $COINS 250")
                client.command("item $AXE")
                client.await("the items") { if (client.backpack().size == 10) Unit else null }
                client.locOp(BOX, 3219, 3218, 1)
                client.await("the deposit box and Examine on its last slot") {
                    if (DepositBox.INTERFACE in client.openInterfaces() && client.opEnabled(DepositBox.INTERFACE, DepositBox.ITEMS, 27, 10)) Unit else null
                }
                assertEquals(1, client.dragDepth(DepositBox.INTERFACE, DepositBox.ITEMS, 0))

                // Deposit-1 (op 2); then the 5 button makes op 1 Deposit-5.
                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, 2, client.slotOf(LOGS), LOGS)
                client.awaitHeld(LOGS, 7)
                client.ifButton(DepositBox.INTERFACE, DepositBox.QUANTITY_5, 1)
                client.await("mode 5") { if (client.varp(VARP_MODE) == DepositBox.MODE_5) Unit else null }
                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, 1, client.slotOf(LOGS), LOGS)
                client.awaitHeld(LOGS, 2)

                // Deposit-X (op 5) asks; the amount becomes the X button's.
                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, 5, client.slotOf(COINS), COINS)
                client.await("the amount prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(50)
                client.awaitHeld(COINS, 200)
                client.await("X is 50") { if (client.varp(DepositBox.VARP_REQUESTED_QUANTITY) == 50) Unit else null }

                // Wield from the extra option, then Deposit worn items; Deposit inventory takes the rest.
                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, BankScreen.SIDE_EXTRAOP, client.slotOf(AXE), AXE)
                client.awaitWielded(AXE)
                client.ifButton(DepositBox.INTERFACE, DepositBox.DEPOSIT_WORN, 1)
                client.awaitWielded(null)
                client.awaitHeld(AXE, 0)
                client.ifButton(DepositBox.INTERFACE, DepositBox.DEPOSIT_INVENTORY, 1)
                client.awaitBackpack()
                client.awaitBankAtBooth(InvObj(LOGS, 8), InvObj(COINS, 250), InvObj(AXE, 1))
            }
        }
    }

    @Test
    fun `an item used on a deposit box goes in, asking how many when there are more`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $AXE")
                client.command("item $LOGS 3")
                client.await("the items") { if (client.backpack().size == 4) Unit else null }
                client.locTarget(BOX, 3219, 3218, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(AXE), AXE)
                client.awaitHeld(AXE, 0)
                client.locTarget(BOX, 3219, 3218, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(LOGS), LOGS)
                client.await("the amount prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(2)
                client.awaitBackpack(InvObj(LOGS, 1))
                client.awaitBankAtBooth(InvObj(AXE, 1), InvObj(LOGS, 2))
            }
        }
    }

    @Test
    fun `with the quantity check off, an item used on a deposit box goes in, all of them, without asking`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dora").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("varbit ${DepositBox.VARBIT_NO_QUANTITY_CHECK} 1")
                client.command("item $AXE")
                client.command("item $LOGS 3")
                client.await("the items") { if (client.backpack().size == 4) Unit else null }
                client.locTarget(BOX, 3219, 3218, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(LOGS), LOGS)
                client.awaitBackpack(InvObj(AXE, 1))
                assertTrue(client.clientScripts().none { it.id == ScriptContext.MESLAYER_MODE7 }, "it asked how many")
                client.awaitBankAtBooth(InvObj(LOGS, 3))
            }
        }
    }

    private companion object {
        const val LOGS = 1511
        const val COINS = 995
        const val AXE = 1351
        const val BOOTH = 18491
        const val BOX = 10529
        const val SLASH_ATTACK_PARAM = 1
        const val WORN_WEAPON = 71

        /** depositbox_mode sits alone on a spare varp here, so the varp is the mode. */
        const val VARP_MODE = 5_100

        val VARS = VarTypes(
            6_000,
            mapOf(
                DepositBox.VARBIT_MODE to VarbitType(VARP_MODE, 0, 2),
                DepositBox.VARBIT_NO_QUANTITY_CHECK to VarbitType(VARP_MODE + 1, 0, 0),
            ),
        )
    }
}
