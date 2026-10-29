package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Rest of Lumbridge step 2, the bank, through the headless client: a booth's Bank, deposits, withdrawals with the
 * amount prompt and as notes, rearranging, and the bank kept over a logout. The rl-client script
 * test-runner/examples/lumbridge-bank.json does the same in the real client.
 */
class HeadlessBankTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(LOGS_NOTE + 1)
        objs[LOGS] = ObjType(LOGS, "Logs", certlink = LOGS_NOTE, internalName = "logs")
        objs[LOGS_NOTE] = ObjType(LOGS_NOTE, "Logs", stackable = true, certlink = LOGS, certtemplate = 799, internalName = "cert_logs")
        objs[COINS] = ObjType(COINS, "Coins", stackable = true, internalName = "coins")
        objs[AXE] = ObjType(AXE, "Bronze axe", internalName = "bronze_axe")
        val booth = LocType(BOOTH, "Bank booth", ops = listOf(null, "Bank", "Collect", null, null), collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(BOOTH to booth)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(BOOTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0)) },
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.openBank() {
        locOp(BOOTH, 3225, 3218, 2)
        // The slots' events come in packets after the opens, so wait for them too.
        await("the bank and its inventory") {
            openInterfaces().takeIf {
                BankScreen.MAIN in it && BankScreen.SIDE in it &&
                    opEnabled(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 0, 1) && opEnabled(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 0, 1)
            }
        }
    }

    private fun HeadlessClient.backpack() = inventory(Inventory.INV).filterNotNull()

    private fun HeadlessClient.bank() = inventory(Inventory.BANK).filterNotNull()

    private fun HeadlessClient.awaitBank(vararg objs: InvObj) =
        await("the bank to hold ${objs.toList()}") { if (bank() == objs.toList()) Unit else null }

    private fun HeadlessClient.awaitBackpack(vararg objs: InvObj) =
        await("the backpack to hold ${objs.toList()}") { if (backpack() == objs.toList()) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    @Test
    fun `a booth's Bank opens the bank with every slot's options and drags, and deposits go in`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOGS 3")
                client.command("item $COINS 250")
                client.await("the items") { if (client.backpack().size == 4) Unit else null }
                client.openBank()
                assertTrue(client.opEnabled(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 1409, 11), "Examine, op 11, on the last bank slot")
                assertEquals(2, client.dragDepth(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 0))
                assertTrue(client.opEnabled(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 27, 8), "Deposit-All on the last inventory slot")
                assertEquals("900", client.text(BankScreen.MAIN, BankScreen.MAIN_CAPACITY))

                // Deposit-1 (op 3) on a log, then Deposit-All (op 8) on the coins and on another log, which takes both.
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 3, client.slotOf(LOGS), LOGS)
                client.awaitBank(InvObj(LOGS, 1))
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 8, client.slotOf(COINS), COINS)
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 8, client.slotOf(LOGS), LOGS)
                client.awaitBank(InvObj(LOGS, 3), InvObj(COINS, 250))
                client.awaitBackpack()
            }
        }
    }

    @Test
    fun `Withdraw-X asks for an amount, notes come out as one stack, and a drag rearranges`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOGS 5")
                client.command("item $AXE")
                client.await("the items") { if (client.backpack().size == 6) Unit else null }
                client.openBank()
                client.ifButton(BankScreen.MAIN, BankScreen.DEPOSIT_INVENTORY, 1)
                client.awaitBank(InvObj(LOGS, 5), InvObj(AXE, 1))

                client.ifButton(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 6, 0, LOGS)
                client.await("the amount prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(2)
                client.awaitBackpack(InvObj(LOGS, 1), InvObj(LOGS, 1))
                // bank_requestedquantity is bits 1 to 31 of varp bankinsert.
                client.await("Withdraw-2 offered") { if (client.varp(VARP_BANKINSERT) == 2 shl 1) Unit else null }

                client.ifButton(BankScreen.MAIN, BankScreen.NOTE, 1)
                client.ifButton(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 7, 0, LOGS)
                client.awaitBackpack(InvObj(LOGS, 1), InvObj(LOGS, 1), InvObj(LOGS_NOTE, 3))
                client.awaitBank(InvObj(AXE, 1))

                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 8, client.slotOf(LOGS_NOTE), LOGS_NOTE)
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 8, client.slotOf(LOGS), LOGS)
                client.awaitBank(InvObj(AXE, 1), InvObj(LOGS, 5))
                client.ifButtonD(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 1, LOGS, BankScreen.MAIN, BankScreen.MAIN_ITEMS, 0)
                client.awaitBank(InvObj(LOGS, 5), InvObj(AXE, 1))
            }
        }
    }

    @Test
    fun `walking away closes the bank and its amount prompt, and a late amount does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 100")
                client.await("the coins") { if (client.backpack().isNotEmpty()) Unit else null }
                client.openBank()
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 7, 0, COINS)
                client.await("the amount prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                client.walk(3222, 3214)
                client.await("the prompt closed") {
                    client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_CLOSE && it.args == listOf<Any>(ScriptContext.MESLAYER_COUNT) }
                }
                client.await("the bank closed") { client.openInterfaces().takeIf { BankScreen.MAIN !in it } }
                client.resumeCount(50)
                client.awaitTicks(2)
                client.awaitBackpack(InvObj(COINS, 100))
            }
        }
    }

    @Test
    fun `what is banked is still there after a logout`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 1000")
                client.await("the coins") { if (client.backpack().isNotEmpty()) Unit else null }
                client.openBank()
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 8, 0, COINS)
                client.awaitBank(InvObj(COINS, 1000))
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("carol").use { client ->
                client.await("the login rebuild") { client.position }
                assertTrue(client.backpack().isEmpty())
                client.openBank()
                client.ifButton(BankScreen.MAIN, BankScreen.MAIN_ITEMS, 7, 0, COINS)
                client.awaitBackpack(InvObj(COINS, 1000))
            }
        }
    }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }

    private companion object {
        const val LOGS = 1511
        const val LOGS_NOTE = 1512
        const val COINS = 995
        const val AXE = 1351
        const val BOOTH = 18491
        const val VARP_BANKCERT = 115
        const val VARP_BANKINSERT = 304

        /** The bank's varbits as the 241 cache lays them out; the two whose varps nothing else here uses sit on spare ones. */
        val VARS = VarTypes(
            6_000,
            mapOf(
                BankScreen.VARBIT_WITHDRAW_NOTES to VarbitType(VARP_BANKCERT, 0, 0),
                BankScreen.VARBIT_INSERT_MODE to VarbitType(VARP_BANKINSERT, 0, 0),
                BankScreen.VARBIT_REQUESTED_QUANTITY to VarbitType(VARP_BANKINSERT, 1, 31),
                BankScreen.VARBIT_QUANTITY_TYPE to VarbitType(5_000, 2, 4),
                BankScreen.VARBIT_LEAVE_PLACEHOLDERS to VarbitType(5_001, 29, 29),
            ),
        )
    }
}
