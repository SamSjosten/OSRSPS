package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.combat.AvaDevices
import osrsps.game.dialogue.DialogueText
import osrsps.game.shop.ShopScreen

/** Draynor step 12c: Ava's Devices' costs and refusals, with no words file, so messages show their keys. */
class AvaDevicesShopTest {
    private val objTypes = ObjTypes.of(
        ObjType(ShopScreen.COINS, "Coins", stackable = true),
        ObjType(AvaDevicesShop.STEEL_ARROW, "Steel arrow", stackable = true),
        ObjType(AvaDevicesShop.MITHRIL_ARROW, "Mithril arrow", stackable = true),
        ObjType(AvaDevicesShop.VORKATHS_HEAD, "Vorkath's head"),
        ObjType(AvaDevices.ATTRACTOR, "Ava's attractor"),
        ObjType(AvaDevices.ACCUMULATOR, "Ava's accumulator"),
        ObjType(AvaDevices.ASSEMBLER, "Ava's assembler"),
    )
    private val messages = mutableListOf<String>()

    private fun player(ranged: Int, vararg held: Pair<Int, Int>) = Player(1, "alice", 3093, 3357, 0).apply {
        skills.setLevel(Skill.RANGED, ranged)
        for ((id, count) in held) inventory.add(objTypes[id]!!, count)
    }

    private fun buy(player: Player, device: AvaDevicesShop.Device, wanted: Int) =
        AvaDevicesShop.buy(player, device, wanted, DialogueText.NONE, objTypes, messages::add)

    private fun Player.count(id: Int) = inventory.count(id).toInt()

    @Test
    fun `an attractor costs 999 coins and 30 Ranged`() {
        val low = player(29, ShopScreen.COINS to 5000)
        assertEquals(0, buy(low, AvaDevicesShop.ATTRACTOR, 1))
        assertEquals(listOf("[ava_devices.level level=30 device=Ava's attractor]"), messages)
        messages.clear()
        val alice = player(30, ShopScreen.COINS to 2500)
        assertEquals(2, buy(alice, AvaDevicesShop.ATTRACTOR, Int.MAX_VALUE))
        assertEquals(2, alice.count(AvaDevices.ATTRACTOR))
        assertEquals(502, alice.count(ShopScreen.COINS))
        // Stopping for want of coins after buying some says nothing.
        assertEquals(emptyList(), messages)
        assertEquals(0, buy(alice, AvaDevicesShop.ATTRACTOR, 1))
        assertEquals(listOf("[ava_devices.coins coins=999 device=Ava's attractor]"), messages)
    }

    @Test
    fun `an accumulator takes 75 steel arrows and an attractor, or 999 coins without one`() {
        val alice = player(50, AvaDevices.ATTRACTOR to 1, AvaDevicesShop.STEEL_ARROW to 150, ShopScreen.COINS to 999)
        assertEquals(1, buy(alice, AvaDevicesShop.ACCUMULATOR, 1))
        // The attractor is used, not the coins.
        assertEquals(0, alice.count(AvaDevices.ATTRACTOR))
        assertEquals(999, alice.count(ShopScreen.COINS))
        assertEquals(75, alice.count(AvaDevicesShop.STEEL_ARROW))
        assertEquals(1, buy(alice, AvaDevicesShop.ACCUMULATOR, 5))
        assertEquals(0, alice.count(ShopScreen.COINS))
        assertEquals(2, alice.count(AvaDevices.ACCUMULATOR))
        assertEquals(0, buy(alice, AvaDevicesShop.ACCUMULATOR, 1))
        assertEquals(listOf("[ava_devices.arrows arrows=steel arrows device=Ava's accumulator]"), messages)
    }

    @Test
    fun `an assembler takes 70 Ranged, Vorkath's head, 75 mithril arrows and an accumulator or 4,999 coins`() {
        val noHead = player(70, AvaDevicesShop.MITHRIL_ARROW to 75, ShopScreen.COINS to 5000)
        assertEquals(0, buy(noHead, AvaDevicesShop.ASSEMBLER, 1))
        assertEquals(listOf("[ava_devices.head]"), messages)
        messages.clear()
        val alice = player(70, AvaDevicesShop.VORKATHS_HEAD to 1, AvaDevicesShop.MITHRIL_ARROW to 75, ShopScreen.COINS to 4999)
        assertEquals(1, buy(alice, AvaDevicesShop.ASSEMBLER, 1))
        assertEquals(listOf(0, 0, 0, 1), listOf(AvaDevicesShop.VORKATHS_HEAD, AvaDevicesShop.MITHRIL_ARROW, ShopScreen.COINS, AvaDevices.ASSEMBLER).map { alice.count(it) })
        val bob = player(69, AvaDevicesShop.VORKATHS_HEAD to 1, AvaDevicesShop.MITHRIL_ARROW to 75, ShopScreen.COINS to 4999)
        assertEquals(0, buy(bob, AvaDevicesShop.ASSEMBLER, 1))
        assertEquals("[ava_devices.level level=70 device=Ava's assembler]", messages.last())
    }

    @Test
    fun `a full backpack takes no device bought with coins, but one made from another fits in its place`() {
        val alice = player(50, ShopScreen.COINS to 5000, AvaDevicesShop.STEEL_ARROW to 75)
        repeat(26) { alice.inventory.add(objTypes[AvaDevices.ATTRACTOR]!!, 1) }
        assertEquals(0, alice.inventory.freeSlots)
        assertEquals(0, buy(alice, AvaDevicesShop.ATTRACTOR, 1))
        assertEquals(listOf("[ava_devices.space]"), messages)
        assertEquals(1, buy(alice, AvaDevicesShop.ACCUMULATOR, 1))
        assertEquals(25, alice.count(AvaDevices.ATTRACTOR))
    }

    @Test
    fun `a worn attractor pays for an accumulator and becomes it where it is`() {
        val alice = player(50, AvaDevicesShop.STEEL_ARROW to 75)
        alice.equipment[WearPos.CAPE] = InvObj(AvaDevices.ATTRACTOR, 1)
        assertEquals(1, buy(alice, AvaDevicesShop.ACCUMULATOR, 1))
        assertEquals(InvObj(AvaDevices.ACCUMULATOR, 1), alice.equipment[WearPos.CAPE])
        assertEquals(0, alice.count(AvaDevices.ACCUMULATOR))
        assertEquals(0, alice.count(AvaDevicesShop.STEEL_ARROW))
        assertEquals(emptyList(), messages)
    }

    @Test
    fun `a full backpack takes a device when paying for it empties a slot`() {
        // All 999 coins spent empties the coins' slot.
        val alice = player(30, ShopScreen.COINS to 999)
        repeat(27) { alice.inventory.add(objTypes[AvaDevicesShop.VORKATHS_HEAD]!!, 1) }
        assertEquals(0, alice.inventory.freeSlots)
        assertEquals(1, buy(alice, AvaDevicesShop.ATTRACTOR, 1))
        assertEquals(1, alice.count(AvaDevices.ATTRACTOR))
        // Vorkath's head always takes a slot of its own.
        val bob = player(70, ShopScreen.COINS to 10_000, AvaDevicesShop.MITHRIL_ARROW to 150)
        repeat(26) { bob.inventory.add(objTypes[AvaDevicesShop.VORKATHS_HEAD]!!, 1) }
        assertEquals(0, bob.inventory.freeSlots)
        assertEquals(1, buy(bob, AvaDevicesShop.ASSEMBLER, 1))
        assertEquals(25, bob.count(AvaDevicesShop.VORKATHS_HEAD))
        assertEquals(1, bob.count(AvaDevices.ASSEMBLER))
    }

    @Test
    fun `the Buy ops ask for one, five, a number, or all`() {
        assertEquals(listOf(1, 5, -1, Int.MAX_VALUE), (1..4).map(AvaDevicesShop::wanted))
        assertEquals(InvObj(AvaDevices.ATTRACTOR, 1), InvObj(AvaDevicesShop.ATTRACTOR.id, 1))
    }
}
