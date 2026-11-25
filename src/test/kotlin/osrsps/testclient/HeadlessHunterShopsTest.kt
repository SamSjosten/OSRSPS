package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.HunterShops
import osrsps.map.CollisionMap

/**
 * Hunter step 9 through the headless client: a box trap pack opened into 100 box traps; Asyff's Fur Clothing panel, its
 * Value, and Buy on a larupia top taking the tatty fur before the perfect one and the fee, then a larupia hat refused
 * without its perfect fur once it is used. Asyff stands two tiles east of where the player logs in (3222, 3218).
 */
class HeadlessHunterShopsTest {
    private val asyff = NpcType(HunterShops.ASYFF, "Asyff", ops = listOf("Talk-to", null, "Trade", "Fur Clothing", null), internalName = "tailorp")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        objs[HunterShops.COINS] = ObjType(HunterShops.COINS, "Coins", stackable = true, tradeable = true)
        objs[BOX_TRAP_PACK] = ObjType(BOX_TRAP_PACK, "Box trap pack", stackable = false, tradeable = false, iops = listOf("Open", null, null, null, "Drop"))
        objs[BOX_TRAP] = ObjType(BOX_TRAP, "Box trap", stackable = true, tradeable = true)
        for (piece in HunterShops.PIECES) {
            objs[piece.item] = ObjType(piece.item, "Piece ${piece.item}", stackable = false, tradeable = true, iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.CAPE)
            for (fur in piece.furs) objs[fur] = ObjType(fur, "Fur $fur", stackable = false, tradeable = true)
        }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            npcTypes = NpcTypes.of(asyff),
            npcSpawns = listOf(NpcSpawn(asyff, 3224, 3218, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    @Test
    fun `a box trap pack opens into 100 box traps, and Asyff makes fur clothing for its fee and furs, tatty first`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hs").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BOX_TRAP_PACK 1")
                client.ifButton(osrsps.game.InventoryTab.INTERFACE, osrsps.game.InventoryTab.ITEMS, OPEN_BUTTON_OP, client.slotOf(BOX_TRAP_PACK), BOX_TRAP_PACK)
                client.await("100 box traps") { if (client.count(BOX_TRAP) == 100) Unit else null }
                assertEquals(0, client.count(BOX_TRAP_PACK))
                client.command("empty")
                client.command("item ${HunterShops.COINS} 600")
                client.command("item $TATTY_LARUPIA 1")
                client.command("item $LARUPIA 1")
                client.await("the furs") { if (client.count(LARUPIA) == 1 && client.count(TATTY_LARUPIA) == 1 && client.count(HunterShops.COINS) == 600) Unit else null }
                client.npcOp(client.await("Asyff") { client.npcs().firstOrNull { it.id == asyff.id } }.index, HunterShops.FUR_CLOTHING)
                client.await("the panel") { if (HunterShops.PANEL in server.world.playerNamed("hs")!!.modals.values) Unit else null }
                val top = HunterShops.PIECES.first { it.item == LARUPIA_TOP }
                val hat = HunterShops.PIECES.first { it.item == LARUPIA_HAT }
                client.ifButton(HunterShops.PANEL, HunterShops.PANEL_ITEMS, HunterShops.VALUE, HunterShops.PIECES.indexOf(top), top.item)
                client.seen("[hunter.fur_value fee=100 count=1]")
                client.ifButton(HunterShops.PANEL, HunterShops.PANEL_ITEMS, HunterShops.BUY, HunterShops.PIECES.indexOf(top), top.item)
                client.await("the top") { if (client.count(LARUPIA_TOP) == 1) Unit else null }
                assertEquals(0, client.count(TATTY_LARUPIA))
                assertEquals(1, client.count(LARUPIA))
                assertEquals(500, client.count(HunterShops.COINS))
                // The hat takes the perfect fur and 500: bought, then refused with the fur gone.
                client.ifButton(HunterShops.PANEL, HunterShops.PANEL_ITEMS, HunterShops.BUY, HunterShops.PIECES.indexOf(hat), hat.item)
                client.await("the hat") { if (client.count(LARUPIA_HAT) == 1) Unit else null }
                assertEquals(0, client.count(HunterShops.COINS))
                client.ifButton(HunterShops.PANEL, HunterShops.PANEL_ITEMS, HunterShops.BUY, HunterShops.PIECES.indexOf(hat), hat.item)
                client.seen("[hunter.fur_needed count=1]")
            }
        }
    }

    @Test
    fun `a spottier cape worn before it's bought does no diary task, nor a bought spotted cape, and a bought spottier cape worn does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hs").use { client ->
                client.await("the login rebuild") { client.position }
                fun diaryDone() = client.messages().any { it.text.startsWith("[diary.task_done") }
                fun wear(id: Int) {
                    client.ifButton(osrsps.game.InventoryTab.INTERFACE, osrsps.game.InventoryTab.ITEMS, WEAR_BUTTON_OP, client.slotOf(id), id)
                    client.await("the cape worn") { client.inventory(WORN).getOrNull(WearPos.CAPE)?.takeIf { it.id == id } }
                }
                fun buy(id: Int, fur: Int, fee: Int) {
                    client.command("empty")
                    client.command("item ${HunterShops.COINS} $fee")
                    client.command("item $fur 2")
                    client.await("the furs") { if (client.count(fur) == 2 && client.count(HunterShops.COINS) == fee) Unit else null }
                    client.npcOp(client.await("Asyff") { client.npcs().firstOrNull { it.id == asyff.id } }.index, HunterShops.FUR_CLOTHING)
                    client.await("the panel") { if (HunterShops.PANEL in server.world.playerNamed("hs")!!.modals.values) Unit else null }
                    val cape = HunterShops.PIECES.first { it.item == id }
                    assertEquals(fee, cape.fee)
                    client.ifButton(HunterShops.PANEL, HunterShops.PANEL_ITEMS, HunterShops.BUY, HunterShops.PIECES.indexOf(cape), cape.item)
                    client.await("the cape") { if (client.count(id) == 1) Unit else null }
                    client.closeModal()
                }
                // One from anywhere but Asyff (::item here) does nothing.
                client.command("item $SPOTTIER_CAPE 1")
                wear(SPOTTIER_CAPE)
                assertEquals(false, diaryDone())
                // The cheaper spotted cape, bought and worn, isn't the spottier one (Codex on #952).
                buy(SPOTTED_CAPE, SPOTTED_FUR, 400)
                wear(SPOTTED_CAPE)
                client.awaitTicks(2)
                assertEquals(false, diaryDone(), "a spotted cape")
                buy(SPOTTIER_CAPE, DASHING_FUR, 800)
                assertEquals(false, diaryDone(), "bought, not yet worn")
                wear(SPOTTIER_CAPE)
                client.await("the diary task") { Unit.takeIf { diaryDone() } }
            }
        }
    }

    @Test
    fun `a pack whose 100 would pass the stack's limit stays shut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hs").use { client ->
                client.await("the login rebuild") { client.position }
                val near = Int.MAX_VALUE - 50
                client.command("item $BOX_TRAP $near")
                client.command("item $BOX_TRAP_PACK 1")
                client.await("the stack and the pack") { if (client.count(BOX_TRAP) == near && client.count(BOX_TRAP_PACK) == 1) Unit else null }
                client.ifButton(osrsps.game.InventoryTab.INTERFACE, osrsps.game.InventoryTab.ITEMS, OPEN_BUTTON_OP, client.slotOf(BOX_TRAP_PACK), BOX_TRAP_PACK)
                client.seen("[hunter.pack_space]")
                assertEquals(1, client.count(BOX_TRAP_PACK))
                assertEquals(near, client.count(BOX_TRAP))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val BOX_TRAP_PACK = 12742
        const val BOX_TRAP = 10008
        const val LARUPIA = 10095
        const val TATTY_LARUPIA = 10093
        const val LARUPIA_TOP = 10043
        const val LARUPIA_HAT = 10045
        const val SPOTTED_CAPE = 10069
        const val SPOTTIER_CAPE = 10071
        const val SPOTTED_FUR = 10125
        const val DASHING_FUR = 10127
        const val WORN = 94

        /** iop2 (Wear) is button op 3. */
        const val WEAR_BUTTON_OP = 3

        /** iop1 (Open) is button op 2 (see InventoryTab.click). */
        const val OPEN_BUTTON_OP = 2
    }
}
