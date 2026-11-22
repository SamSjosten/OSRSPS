package osrsps.game.collection

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.dialogue.DialogueText

class CollectionLogWindowTest {
    private val essence = 22372
    private val visage = 11286
    private val pickaxe = 11920
    private val club = 20756
    private val dummy = 30000

    private val bryophyta = CollectionLogCatalog.Page(478, 0, 0, listOf(essence), completedVarbit = null)
    private val kbd = CollectionLogCatalog.Page(491, 0, 1, listOf(pickaxe, visage), completedVarbit = null)
    private val slayer = CollectionLogCatalog.Page(527, 4, 0, listOf(visage, club), completedVarbit = null)
    private val catalog = CollectionLogCatalog(
        listOf(CollectionLogCatalog.Tab(0, 471, listOf(bryophyta, kbd)), CollectionLogCatalog.Tab(4, 475, listOf(slayer))),
        displayed = mapOf(club to dummy),
    )
    private val log = CollectionLog(catalog, { "Item $it" }, { DialogueText.NONE }, now = { Instant.parse("2026-11-19T04:00:00Z") }, message = { _, _ -> })

    private val scripts = mutableListOf<Pair<Int, List<Any>>>()
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val window = CollectionLogWindow(
        log,
        openModal = { player, slot, id -> player.modals[slot] = id },
        closeModals = { player -> player.modals.clear() },
        runScript = { _, id, args -> scripts += id to args },
        setVarbit = { player, id, value -> player.vars.setVarbit(id, value) },
        setVarp = { player, id, value -> player.vars[id] = value },
        send = { _, message -> sent += message },
        now = { tick },
    )
    private var tick = 0L

    private fun player(): Player {
        val varbits = mapOf(
            CollectionLogWindow.VARBIT_LAST_TAB to VarbitType(100, 0, 2),
            CollectionLogWindow.VARBIT_LAST_PAGE to VarbitType(100, 3, 10),
        )
        return Player(1, "test", 3222, 3218, 0, VarTypes(6_000, varbits))
    }

    private fun inv(): List<Pair<Int, Int>> {
        val update = sent.filterIsInstance<UpdateInvFull>().last()
        assertEquals(CollectionLogWindow.TRANSMIT_INV, update.inventoryId)
        return (0 until update.capacity).map { update.getObject(it) }.filter { it != InventoryObject.NULL }.map { InventoryObject.getId(it) to InventoryObject.getCount(it) }
    }

    private fun packed(component: Int) = (CollectionLogWindow.LOG shl 16) or component

    @Test
    fun `the log opens on its first page with the page's items and kill count, its lists clickable`() {
        val player = player()
        log.add(player, pickaxe, 1)
        log.add(player, essence, 3)
        player.vars[1733] = 12
        window.open(player)
        assertEquals(CollectionLogWindow.LOG, player.modals[FrameSlot.MAINMODAL])
        assertEquals(listOf(essence to 3), inv())
        assertEquals(12, player.vars[2048])
        assertEquals(CollectionLogWindow.DRAW to listOf<Any>(0, packed(10), packed(11), packed(12), packed(13), 471, 0), scripts.last())
        val events = sent.filterIsInstance<IfSetEventsV2>().map { Triple(it.componentId, it.start, it.end) }
        assertTrue(Triple(11, 0, 1) in events, "the bosses' page list")
        assertTrue(Triple(34, 0, 0) in events, "the other tab's page list")
        assertTrue(Triple(CollectionLogWindow.BURGER_FRAME, 10, 12) in events)
    }

    @Test
    fun `a page clicked is sent and drawn, remembered, and the log reopens on it`() {
        val player = player()
        log.add(player, pickaxe, 1)
        player.vars[1514] = 40
        window.open(player)
        window.click(player, CollectionLogWindow.LOG, 11, 1, 1)
        assertEquals(listOf(pickaxe to 1), inv())
        assertEquals(40, player.vars[2048])
        assertEquals(CollectionLogWindow.DRAW_LIST to listOf<Any>(0, packed(10), packed(11), packed(12), packed(13), 471, 1), scripts.last())
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.CLOSE, -1, 1)
        assertEquals(null, player.modals[FrameSlot.MAINMODAL])
        window.open(player)
        assertEquals(471, scripts.last().second[5])
        assertEquals(1, scripts.last().second[6])
    }

    @Test
    fun `a tab button opens its first page, showing the dummy the page draws, and a page past the list is ignored`() {
        val player = player()
        log.add(player, club, 2)
        window.open(player)
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.TAB_BUTTONS[4], -1, 1)
        assertEquals(listOf(dummy to 2), inv())
        assertEquals(0, player.vars[2048], "no kill count for the page")
        assertEquals(CollectionLogWindow.DRAW_TABS to listOf<Any>(4), scripts[scripts.size - 2])
        assertEquals(CollectionLogWindow.DRAW_LIST to listOf<Any>(4, packed(29), packed(34), packed(35), packed(30), 475, 0), scripts.last())
        val before = scripts.size
        window.click(player, CollectionLogWindow.LOG, 34, 5, 1)
        assertEquals(before, scripts.size)
    }

    @Test
    fun `search lights every item the player has, by the obj it shows`() {
        val player = player()
        log.add(player, visage, 4)
        log.add(player, club, 1)
        window.open(player)
        scripts.clear()
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.SEARCH_TOGGLE, -1, 1)
        assertEquals(
            listOf<Pair<Int, List<Any>>>(
                CollectionLogWindow.DELAYED_TRANSMIT to listOf(visage, 4, 1, 491),
                CollectionLogWindow.DELAYED_TRANSMIT to listOf(visage, 4, 0, 527),
                CollectionLogWindow.DELAYED_TRANSMIT to listOf(dummy, 1, 1, 527),
            ),
            scripts,
        )

        // Again on the same tick, or with another op: nothing more (exploit review 44, R44B-4). The next tick, again.
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.SEARCHBAR, -1, 1)
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.SEARCH_TOGGLE, -1, 2)
        assertEquals(3, scripts.size)
        tick++
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.SEARCH_TOGGLE, -1, 2)
        assertEquals(3, scripts.size)
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.SEARCHBAR, -1, 1)
        assertEquals(6, scripts.size)
    }

    @Test
    fun `the burger menu, the overview's tab buttons and the summary row move between the windows`() {
        val player = player()
        window.open(player)
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.BURGER_FRAME, CollectionLogWindow.VIEW_OVERVIEW, 1)
        assertEquals(CollectionLogWindow.OVERVIEW, player.modals[FrameSlot.MAINMODAL])
        window.click(player, CollectionLogWindow.OVERVIEW, CollectionLogWindow.OVERVIEW_TAB_BUTTONS, 4, 1)
        assertEquals(CollectionLogWindow.LOG, player.modals[FrameSlot.MAINMODAL])
        assertEquals(475, scripts.last().second[5])
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.CLOSE, -1, 1)
        assertTrue(window.click(player, CollectionLogWindow.SUMMARY, CollectionLogWindow.SUMMARY_CLICK_LAYER, CollectionLogWindow.SUMMARY_ROW, 2))
        assertEquals(CollectionLogWindow.OVERVIEW, player.modals[FrameSlot.MAINMODAL])
        assertTrue(window.click(player, CollectionLogWindow.SUMMARY, CollectionLogWindow.SUMMARY_CLICK_LAYER, CollectionLogWindow.SUMMARY_ROW, 1))
        assertEquals(CollectionLogWindow.LOG, player.modals[FrameSlot.MAINMODAL])
        // Other summary rows aren't the log's.
        assertEquals(false, window.click(player, CollectionLogWindow.SUMMARY, CollectionLogWindow.SUMMARY_CLICK_LAYER, 5, 1))
    }

    @Test
    fun `clicks with the window closed do nothing`() {
        val player = player()
        window.click(player, CollectionLogWindow.LOG, CollectionLogWindow.TAB_BUTTONS[0], -1, 1)
        window.click(player, CollectionLogWindow.OVERVIEW, CollectionLogWindow.OVERVIEW_TAB_BUTTONS, 0, 1)
        assertEquals(emptyList(), scripts)
        assertEquals(null, player.modals[FrameSlot.MAINMODAL])
    }
}
