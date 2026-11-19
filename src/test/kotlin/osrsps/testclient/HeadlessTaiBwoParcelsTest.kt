package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.bank.DepositBox
import osrsps.game.content.TaiBwoParcels
import osrsps.game.content.TaiBwoWannai
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Karamja step K4.2d through the headless client: Rionasta's Send-parcel refusing a player without 10 trading sticks,
 * then the parcel service sending as many logs as 25 sticks pay for (2, for 20), refusing coins as a currency, and then
 * the rest of the logs for want of sticks. test-runner/examples/tai-bwo-wannai-parcels.json does the same on the real map.
 */
class HeadlessTaiBwoParcelsTest {
    private val rionasta = NpcType(RIONASTA, "Rionasta", ops = listOf("Talk-to", null, "Send-parcel", null, null), internalName = "tbwcu_rionasta")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(TaiBwoWannai.TRADING_STICKS + 1)
        objs[LOGS] = ObjType(LOGS, "Logs", internalName = "logs")
        objs[COINS] = ObjType(COINS, "Coins", stackable = true, internalName = "coins")
        objs[TaiBwoWannai.TRADING_STICKS] = ObjType(TaiBwoWannai.TRADING_STICKS, "Trading sticks", stackable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(rionasta), npcSpawns = listOf(NpcSpawn(rionasta, 3223, 3218, wander = 0, source = "test")),
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.message(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `the parcel service charges 10 sticks an item and refuses currency`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val npc = client.await("Rionasta in view") { client.npcs().firstOrNull { it.id == RIONASTA } }.index
                client.npcOp(npc, TaiBwoParcels.SEND_PARCEL)
                client.awaitDialogue("at least 10") { it is DialogueView.Chat && it.text == "[rionasta.at_least]" }
                client.continueDialogue()
                client.await("the talk over") { if (client.dialogueView == null) Unit else null }

                client.command("item ${TaiBwoWannai.TRADING_STICKS} 25")
                client.command("item $COINS 100")
                client.command("item $LOGS 5")
                client.await("the logs") { if (client.count(LOGS) == 5L) Unit else null }
                client.npcOp(npc, TaiBwoParcels.SEND_PARCEL)
                client.await("the parcel service") { if (DepositBox.INTERFACE in client.openInterfaces()) Unit else null }

                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, 6, client.slotOf(LOGS), LOGS)
                client.await("2 logs sent") { if (client.count(LOGS) == 3L) Unit else null }
                client.await("20 sticks paid") { if (client.count(TaiBwoWannai.TRADING_STICKS) == 5L) Unit else null }

                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, 6, client.slotOf(COINS), COINS)
                client.message("[tbw_parcel.currency]")
                client.ifButton(DepositBox.INTERFACE, DepositBox.ITEMS, 6, client.slotOf(LOGS), LOGS)
                client.message("[tbw_parcel.cant_afford]")
                client.ifButton(DepositBox.INTERFACE, DepositBox.DEPOSIT_INVENTORY, 1)
                client.await("nothing more sent") { if (client.count(COINS) == 100L && client.count(LOGS) == 3L && client.count(TaiBwoWannai.TRADING_STICKS) == 5L) Unit else null }
                val bank = server.world.onlinePlayers.single().bank
                kotlin.test.assertEquals(2L, bank.count(LOGS), "the 2 logs paid for, in the bank")
                kotlin.test.assertEquals(0L, bank.count(COINS))
            }
        }
    }

    private companion object {
        const val RIONASTA = 6432
        const val LOGS = 1511
        const val COINS = 995

        val VARS = VarTypes(
            6_000,
            TaiBwoWannai.Villager.entries.withIndex().associate { (i, v) -> v.varbit to VarbitType(534, i * 3, i * 3 + 2) } +
                (TaiBwoWannai.FAVOUR_VARBIT to VarbitType(535, 2, 11)) +
                (DepositBox.VARBIT_MODE to VarbitType(5_000, 0, 2)) + (DepositBox.VARBIT_NO_QUANTITY_CHECK to VarbitType(5_001, 0, 0)),
        )
    }
}
