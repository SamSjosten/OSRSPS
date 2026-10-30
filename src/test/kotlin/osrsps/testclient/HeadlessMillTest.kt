package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.Crops
import osrsps.game.content.DairyCowLines
import osrsps.game.content.GroatsFarm
import osrsps.game.content.MillLaneMill
import osrsps.game.content.MillLines
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Rest of Lumbridge step 7 through the headless client: wheat picked into grain, grain through the hopper and its
 * controls into the flour bin and from there into a pot, and a dairy cow milked into every bucket. The mill's floors are
 * one level here, as the headless test map has no ladders. The rl-client script test-runner/examples/lumbridge-mill.json
 * does the same in the real client, up and down the mill's ladders.
 */
class HeadlessMillTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Crops.GRAIN + 1)
        fun obj(id: Int, name: String) = ObjType(id, name, iops = listOf(null, null, null, null, "Drop"))
        objs[Crops.GRAIN] = obj(Crops.GRAIN, "Grain")
        objs[MillLaneMill.POT] = obj(MillLaneMill.POT, "Pot")
        objs[MillLaneMill.POT_OF_FLOUR] = obj(MillLaneMill.POT_OF_FLOUR, "Pot of flour")
        objs[GroatsFarm.BUCKET] = obj(GroatsFarm.BUCKET, "Bucket")
        objs[GroatsFarm.BUCKET_OF_MILK] = obj(GroatsFarm.BUCKET_OF_MILK, "Bucket of milk")
        val locs = mapOf(
            WHEAT to LocType(WHEAT, "Wheat", ops = listOf(null, "Pick", null, null, null), collision = LocCollisionType(interactType = 0)),
            MillLaneMill.HOPPER to LocType(MillLaneMill.HOPPER, "Hopper", ops = listOf("Fill", null, null, null, null), collision = LocCollisionType(width = 2)),
            MillLaneMill.CONTROLS to LocType(MillLaneMill.CONTROLS, "Hopper controls", ops = listOf("Operate", null, null, null, null)),
            MillLaneMill.FLOUR_BIN to LocType(MillLaneMill.FLOUR_BIN, collision = LocCollisionType(width = 2, length = 2), multiloc = true),
            COW to LocType(COW, "Dairy cow", ops = listOf("Milk", "Steal-cowbell", null, null, null), collision = LocCollisionType(length = 2)),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(locs),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(WHEAT, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3220, 0))
                add(ZoneLoc(MillLaneMill.HOPPER, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3222, 0))
                add(ZoneLoc(MillLaneMill.CONTROLS, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3220, 0))
                add(ZoneLoc(MillLaneMill.FLOUR_BIN, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3214, 0))
                add(ZoneLoc(COW, LocShape.CENTREPIECE_STRAIGHT, 0, 3217, 3214, 0))
            },
            varTypes = VarTypes(
                6_000,
                mapOf(MillLaneMill.VARBIT_FLOUR to VarbitType(VARP_FLOUR, 0, 4), MillLaneMill.VARBIT_SHOW_FLOUR to VarbitType(VARP_SHOW_FLOUR, 0, 0)),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    @Test
    fun `wheat to grain, grain through the hopper into the bin, and the bin's flour into a pot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // Nothing in the hopper or the bin yet.
                client.locOp(MillLaneMill.CONTROLS, 3226, 3220, MillLaneMill.OPERATE)
                client.seen(DialogueText.NONE[MillLines.EMPTY_HOPPER])
                // That message comes before the arrive delay, and a click while delayed is dropped.
                client.awaitTicks(2)
                client.locOp(MillLaneMill.FLOUR_BIN, 3226, 3214, MillLaneMill.EMPTY)
                client.seen(DialogueText.NONE[MillLines.BIN_EMPTY])
                client.locOp(MillLaneMill.HOPPER, 3226, 3222, MillLaneMill.FILL)
                client.awaitDialogue("the no-grain item box") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)

                client.locOp(WHEAT, 3220, 3220, Crops.PICK)
                client.seen(DialogueText.NONE[Crops.WHEAT.picked])
                client.await("a grain") { if (client.count(Crops.GRAIN) == 1) Unit else null }
                assertTrue(server.world.locs.find(WHEAT, 3220, 3220, 0) == null, "the wheat is still there")

                client.locOp(MillLaneMill.HOPPER, 3226, 3222, MillLaneMill.FILL)
                client.seen(DialogueText.NONE[MillLines.PUT_GRAIN])
                client.await("the grain gone") { if (client.count(Crops.GRAIN) == 0) Unit else null }
                client.command("item ${Crops.GRAIN}")
                client.await("another grain") { if (client.count(Crops.GRAIN) == 1) Unit else null }
                // One grain at a time.
                client.locTarget(MillLaneMill.HOPPER, 3226, 3222, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Crops.GRAIN), Crops.GRAIN)
                client.seen(DialogueText.NONE[MillLines.ALREADY_GRAIN])
                assertEquals(1, client.count(Crops.GRAIN))

                client.locOp(MillLaneMill.CONTROLS, 3226, 3220, MillLaneMill.OPERATE)
                client.seen(DialogueText.NONE[MillLines.OPERATED])
                // One pot's worth in the bin, and the bin shows flour.
                client.await("the flour varbits") { if (client.varp(VARP_FLOUR) == 1 && client.varp(VARP_SHOW_FLOUR) == 1) Unit else null }

                client.locOp(MillLaneMill.FLOUR_BIN, 3226, 3214, MillLaneMill.EMPTY)
                client.seen(DialogueText.NONE[MillLines.NO_POT])
                client.command("item ${MillLaneMill.POT}")
                val pot = client.slotOf(MillLaneMill.POT)
                client.locTarget(MillLaneMill.FLOUR_BIN, 3226, 3214, InventoryTab.INTERFACE, InventoryTab.ITEMS, pot, MillLaneMill.POT)
                client.seen(DialogueText.NONE[MillLines.LAST_POT])
                client.await("a pot of flour") { if (client.count(MillLaneMill.POT_OF_FLOUR) == 1 && client.count(MillLaneMill.POT) == 0) Unit else null }
                client.await("the bin empty") { if (client.varp(VARP_FLOUR) == 0 && client.varp(VARP_SHOW_FLOUR) == 0) Unit else null }
            }
        }
    }

    @Test
    fun `the bin holds 30 pots, and the hopper keeps its grain while the bin is full`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("varbit ${MillLaneMill.VARBIT_FLOUR} 29")
                client.command("item ${Crops.GRAIN} 2")
                client.await("two grain") { if (client.count(Crops.GRAIN) == 2) Unit else null }
                client.locOp(MillLaneMill.HOPPER, 3226, 3222, MillLaneMill.FILL)
                client.seen(DialogueText.NONE[MillLines.PUT_GRAIN])
                client.locOp(MillLaneMill.CONTROLS, 3226, 3220, MillLaneMill.OPERATE)
                client.seen(DialogueText.NONE[MillLines.BIN_NOW_FULL])
                client.await("30 in the bin") { if (client.varp(VARP_FLOUR) == MillLaneMill.BIN_CAPACITY) Unit else null }

                client.locOp(MillLaneMill.HOPPER, 3226, 3222, MillLaneMill.FILL)
                client.await("the last grain in the hopper") { if (client.count(Crops.GRAIN) == 0) Unit else null }
                client.locOp(MillLaneMill.CONTROLS, 3226, 3220, MillLaneMill.OPERATE)
                client.seen(DialogueText.NONE[MillLines.BIN_FULL])
                assertEquals(MillLaneMill.BIN_CAPACITY, client.varp(VARP_FLOUR))

                // A pot of flour out makes room, and the grain still in the hopper goes down.
                client.command("item ${MillLaneMill.POT}")
                client.locTarget(MillLaneMill.FLOUR_BIN, 3226, 3214, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(MillLaneMill.POT), MillLaneMill.POT)
                client.seen(DialogueText.NONE[MillLines.FILLED_POT])
                client.await("29 in the bin") { if (client.varp(VARP_FLOUR) == 29) Unit else null }
                client.locOp(MillLaneMill.CONTROLS, 3226, 3220, MillLaneMill.OPERATE)
                client.await("30 again") { if (client.varp(VARP_FLOUR) == MillLaneMill.BIN_CAPACITY) Unit else null }
                assertTrue(!MillLaneMill.hopperFull(server.world.playerNamed("bob")!!), "grain left in the hopper")
            }
        }
    }

    @Test
    fun `a dairy cow fills every bucket, one every eight ticks, and wants nothing else`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(COW, 3217, 3214, GroatsFarm.MILK)
                client.seen(DialogueText.NONE[DairyCowLines.NO_BUCKET])
                client.command("item ${MillLaneMill.POT}")
                client.locTarget(COW, 3217, 3214, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(MillLaneMill.POT), MillLaneMill.POT)
                client.seen(DialogueText.NONE[DairyCowLines.NOT_WANTED])

                client.command("item ${GroatsFarm.BUCKET} 3")
                client.await("three buckets") { if (client.count(GroatsFarm.BUCKET) == 3) Unit else null }
                client.locOp(COW, 3217, 3214, GroatsFarm.MILK)
                val first = client.await("the first bucket of milk") { if (client.count(GroatsFarm.BUCKET_OF_MILK) == 1) client.ticksSeen else null }
                val last = client.await("three buckets of milk", timeoutMillis = 20_000) {
                    if (client.count(GroatsFarm.BUCKET_OF_MILK) == 3) client.ticksSeen else null
                }
                assertTrue(last - first in 2 * GroatsFarm.MILK_TICKS - 2..2 * GroatsFarm.MILK_TICKS + 2, "${last - first} ticks for two more")
                assertEquals(3, client.messages().count { it.text == DialogueText.NONE[DairyCowLines.MILKED] })
                assertEquals(0, client.count(GroatsFarm.BUCKET))
            }
        }
    }

    private companion object {
        const val WHEAT = 15506
        const val COW = 60788
        const val VARP_FLOUR = 203
        const val VARP_SHOW_FLOUR = 695
        const val BACKPACK = 93
    }
}
