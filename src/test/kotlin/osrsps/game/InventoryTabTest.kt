package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner

class InventoryTabTest {
    private val axe = ObjType(1351, "Bronze axe", desc = "A woodcutter's axe.", cost = 16, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"))
    private val coins = ObjType(995, "Coins", desc = "Lovely money!", stackable = true)
    private val handlers = Handlers()
    private val sent = mutableListOf<OutgoingGameMessage>()
    private var modalsClosed = 0
    private val scripts = ScriptRunner(handlers, { modalsClosed++ }) { 0 }
    private val dropped = mutableListOf<Int>()
    private val tab = InventoryTab(
        ObjTypes.of(axe, coins), scripts, { modalsClosed++ },
        send = { _, message -> sent += message },
        onDrop = { _, slot -> dropped += slot },
    )
    private val player = Player(1, "test", 3222, 3218, 0)

    private fun messages() = sent.filterIsInstance<MessageGame>().map { it.message }

    @Test
    fun `the slots allow ops 2, 3, 4, 6, 7 and 10, drag and use`() {
        tab.open(player)
        val events = sent.single() as IfSetEventsV2
        assertEquals((149 shl 16) or 0, events.combinedId)
        assertEquals(0, events.start)
        assertEquals(27, events.end)
        // Matches RSMod's mask for inventory:items: TgtObj to TgtCom, Depth1, DragTarget, Target; Op2-4, 6, 7, 10.
        assertEquals(0x33F800, events.events1)
        assertEquals(0b10_0110_1110, events.events2)
    }

    @Test
    fun `an item option runs its handler with the slot`() {
        val ran = mutableListOf<Op>()
        handlers.onObj(1351, 2) { ran += it }
        player.inventory.add(axe, 1)
        tab.click(player, 0, 1351, 3)
        assertEquals(listOf(Op(OpTarget.OBJ, 1351, 2, 0)), ran)
        assertEquals(1, modalsClosed)
        assertTrue(messages().isEmpty())
    }

    @Test
    fun `an option nothing handles says nothing interesting happens`() {
        player.inventory.add(axe, 1)
        tab.click(player, 0, 1351, 3)
        assertEquals(listOf("Nothing interesting happens."), messages())
    }

    @Test
    fun `Drop drops the slot, unless a handler takes the option`() {
        player.inventory.add(axe, 1)
        player.inventory.add(axe, 1)
        tab.click(player, 1, 1351, 7)
        assertEquals(listOf(1), dropped)
        assertTrue(messages().isEmpty())

        val ran = mutableListOf<Op>()
        handlers.onObj(1351, 5) { ran += it }
        tab.click(player, 0, 1351, 7)
        assertEquals(listOf(1), dropped)
        assertEquals(listOf(Op(OpTarget.OBJ, 1351, 5, 0)), ran)
    }

    @Test
    fun `clicks on an option the item lacks, or on what the slot doesn't hold, are dropped`() {
        handlers.onObj(1351, 1) { error("iop1 is empty for an axe") }
        player.inventory.add(axe, 1)
        tab.click(player, 0, 1351, 2)
        tab.click(player, 0, 995, 7)
        tab.click(player, 5, 1351, 7)
        tab.click(player, 40, 1351, 7)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `examine runs the examine script with the item's values`() {
        player.inventory.add(coins, 250)
        player.inventory.add(axe, 1)
        tab.click(player, 0, 995, 10)
        tab.click(player, 1, 1351, 10)
        val scripts = sent.filterIsInstance<RunClientScript>()
        assertEquals(listOf(6003, 6003), scripts.map { it.id })
        assertEquals(listOf<Any>(995, 250, "Lovely money!", 0, 1L, 1, 0, 0), scripts[0].values)
        assertEquals(listOf<Any>(1351, 1, "A woodcutter's axe.", 1, 16L, 1, 9, 6), scripts[1].values)
    }

    @Test
    fun `dragging swaps slots, unless the player is delayed`() {
        player.inventory.add(axe, 1)
        player.inventory.add(coins, 5)
        tab.drag(player, 0, 1351, 10)
        assertNull(player.inventory[0])
        assertEquals(InvObj(1351, 1), player.inventory[10])
        tab.drag(player, 1, 1351, 2)
        assertEquals(InvObj(995, 5), player.inventory[1], "the client thought slot 1 held an axe")
    }

    @Test
    fun `a delayed drag is refused and both slots are sent again`() {
        player.inventory.add(axe, 1)
        player.inventory.flush({}, {})
        scripts.start(player) { delay(2) }
        tab.drag(player, 0, 1351, 4)
        assertEquals(InvObj(1351, 1), player.inventory[0])
        assertEquals(0, modalsClosed, "refused input leaves modals open")
        val resent = mutableListOf<Int>()
        player.inventory.flush({}, { changed -> resent += changed.map { it.first } })
        assertEquals(listOf(0, 4), resent)
    }

    @Test
    fun `a delayed player's item clicks do nothing, but examine still works`() {
        player.inventory.add(axe, 1)
        scripts.start(player) { delay(2) }
        tab.click(player, 0, 1351, 7)
        tab.click(player, 0, 1351, 10)
        assertEquals(listOf(6003), sent.filterIsInstance<RunClientScript>().map { it.id })
        assertTrue(messages().isEmpty())
    }

    @Test
    fun `using one item on another does nothing interesting yet`() {
        player.inventory.add(axe, 1)
        player.inventory.add(coins, 1)
        tab.use(player, 0, 1351, 1, 995)
        assertEquals(listOf("Nothing interesting happens."), messages())
    }

    @Test
    fun `using items while delayed does nothing`() {
        player.inventory.add(axe, 1)
        player.inventory.add(coins, 1)
        scripts.start(player) { delay(2) }
        tab.use(player, 0, 1351, 1, 995)
        assertTrue(sent.isEmpty())
        assertEquals(0, modalsClosed)
    }
}
