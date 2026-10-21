package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner

class EquipmentTabTest {
    private val axe = ObjType(1351, "Bronze axe", desc = "A woodcutter's axe.", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(1 to 4, 10 to 5, 14 to 5), category = 35)
    private val bow = ObjType(841, "Shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, params = mapOf(4 to 8, 14 to 4), category = 64)
    private val whip = ObjType(4151, "Abyssal whip", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(434 to 0, 436 to 70), category = 150)
    private val ring = ObjType(2552, "Ring of dueling(8)", wearpos1 = WearPos.RING, params = mapOf(451 to "Emir's Arena", 452 to "Castle Wars"))
    private val types = ObjTypes.of(axe, bow, whip, ring)
    private val handlers = Handlers()
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val opened = mutableListOf<Pair<FrameSlot, Int>>()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { 0 }
    private val send: (Player, OutgoingGameMessage) -> Unit = { _, message -> sent += message }
    private lateinit var tab: EquipmentTab
    private val inventoryTab = InventoryTab(types, scripts, { it.modals.clear() }, { player, slot -> tab.wear(player, slot) }, send)
    private val openModal: (Player, FrameSlot, Int) -> Unit = { player, slot, id ->
        player.modals[slot] = id
        opened += slot to id
    }

    init {
        tab = EquipmentTab(Equipment(types), types, scripts, inventoryTab, openModal, send)
    }

    // Varbit 357 is bits 0 to 5 of varp 843 in the 241 cache.
    private val player = Player(1, "test", 3222, 3218, 0, VarTypes(6_000, mapOf(357 to VarbitType(843, 0, 5))))

    private fun messages() = sent.filterIsInstance<MessageGame>().map { it.message }

    private fun texts() = sent.filterIsInstance<IfSetText>().associate { it.componentId to it.text }

    @Test
    fun `Wield in the inventory wears the item`() {
        player.inventory.add(axe, 1)
        inventoryTab.click(player, 0, 1351, 3)
        assertEquals(InvObj(1351, 1), player.equipment[WearPos.WEAPON])
        assertTrue(messages().isEmpty())
    }

    @Test
    fun `a content handler for the option comes before wearing`() {
        val ran = mutableListOf<Op>()
        handlers.onObj(1351, 2) { ran += it }
        player.inventory.add(axe, 1)
        inventoryTab.click(player, 0, 1351, 3)
        assertEquals(1, ran.size)
        assertNull(player.equipment[WearPos.WEAPON])
    }

    @Test
    fun `too low a level says so and leaves the item`() {
        player.inventory.add(whip, 1)
        inventoryTab.click(player, 0, 4151, 3)
        assertEquals(listOf(EquipmentTab.LEVEL_TOO_LOW, "You need to have an Attack level of 70."), messages())
        assertEquals(InvObj(4151, 1), player.inventory[0])
    }

    @Test
    fun `Remove on a worn slot puts the item back`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        assertTrue(tab.clickWornItems(player, 18, 1))
        assertNull(player.equipment[WearPos.WEAPON])
        assertEquals(InvObj(1351, 1), player.inventory[0])
    }

    @Test
    fun `a full inventory keeps the worn item on`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        repeat(28) { player.inventory.add(ring, 1) }
        tab.clickWornItems(player, 18, 1)
        assertEquals(InvObj(1351, 1), player.equipment[WearPos.WEAPON])
        assertEquals(listOf(EquipmentTab.NO_SPACE), messages())
    }

    @Test
    fun `a worn item's own options run their handlers, and examine runs the examine script`() {
        val ran = mutableListOf<Op>()
        handlers.onWorn(2552, 2) { ran += it }
        player.equipment[WearPos.RING] = InvObj(2552, 1)
        tab.clickWornItems(player, 24, 3)
        assertEquals(listOf(Op(OpTarget.WORN, 2552, 2, WearPos.RING)), ran)
        tab.clickWornItems(player, 24, 2)
        assertEquals(listOf(InventoryTab.NOTHING_INTERESTING), messages())
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        tab.clickWornItems(player, 18, 10)
        assertEquals(6003, sent.filterIsInstance<RunClientScript>().single().id)
    }

    @Test
    fun `the stats button opens the stats screen with the side inventory and the bonuses`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        assertTrue(tab.clickWornItems(player, EquipmentTab.WORNITEMS_VIEW_STATS, 1))
        assertEquals(listOf(FrameSlot.MAINMODAL to 84, FrameSlot.SIDEMODAL to 85), opened)
        val script = sent.filterIsInstance<RunClientScript>().single()
        assertEquals(149, script.id)
        assertEquals(listOf<Any>((85 shl 16) or 0, 93, 4, 7, 0, -1, "Equip", "", "", "", ""), script.values)
        val events = sent.filterIsInstance<IfSetEventsV2>().single()
        assertEquals(0b10_0000_0001, events.events2)
        val texts = texts()
        assertEquals("Slash: +4", texts[EquipmentTab.SLASH_ATT])
        assertEquals("Stab: +0", texts[EquipmentTab.STAB_ATT])
        assertEquals("Melee strength: +5", texts[EquipmentTab.MELEE_STRENGTH])
        assertEquals("Magic damage: +0.0%", texts[EquipmentTab.MAGIC_DAMAGE])
        assertEquals("Base: 3.0s", texts[EquipmentTab.SPEED_BASE])
        assertEquals("Actual: 3.0s", texts[EquipmentTab.SPEED_ACTUAL])
    }

    @Test
    fun `Equip on the stats screen's inventory wears the item and keeps the screen open`() {
        tab.openStats(player)
        player.inventory.add(axe, 1)
        assertTrue(tab.clickStats(player, EquipmentTab.STATS_SIDE, EquipmentTab.STATS_SIDE_ITEMS, 0, 1351, 1))
        assertEquals(InvObj(1351, 1), player.equipment[WearPos.WEAPON])
        assertEquals(84, player.modals[FrameSlot.MAINMODAL])
        assertTrue(tab.clickStats(player, EquipmentTab.STATS, 13, -1, -1, 1), "slot3 on the stats screen is the weapon")
        assertNull(player.equipment[WearPos.WEAPON])
    }

    @Test
    fun `the combat tab shows the weapon's category and keeps only styles it has`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        assertTrue(tab.clickCombatTab(player, 14))
        assertEquals(2, player.vars[EquipmentTab.VARP_COM_MODE], "Smash")
        tab.updateCombatTab(player)
        assertEquals(Weapons.AXE, player.vars.getVarbit(357))
        assertEquals(2, player.vars[EquipmentTab.VARP_COM_MODE])

        player.equipment[WearPos.WEAPON] = InvObj(841, 1)
        tab.updateCombatTab(player)
        assertEquals(Weapons.BOW, player.vars.getVarbit(357))
        assertEquals(0, player.vars[EquipmentTab.VARP_COM_MODE], "bows have no button 2")
        tab.clickCombatTab(player, 14)
        assertEquals(0, player.vars[EquipmentTab.VARP_COM_MODE], "a button the weapon lacks does nothing")
    }

    @Test
    fun `rapid takes a tick off a bow's actual speed`() {
        player.equipment[WearPos.WEAPON] = InvObj(841, 1)
        tab.clickCombatTab(player, 10)
        tab.sendStats(player)
        assertEquals("Base: 2.4s", texts()[EquipmentTab.SPEED_BASE])
        assertEquals("Actual: 1.8s", texts()[EquipmentTab.SPEED_ACTUAL])
    }

    @Test
    fun `auto retaliate flips option_nodef`() {
        tab.clickCombatTab(player, EquipmentTab.COMBAT_RETALIATE)
        assertEquals(1, player.vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF])
        tab.clickCombatTab(player, EquipmentTab.COMBAT_RETALIATE)
        assertEquals(0, player.vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF])
    }
}
