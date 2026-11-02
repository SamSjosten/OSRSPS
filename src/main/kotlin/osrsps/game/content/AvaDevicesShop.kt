package osrsps.game.content

import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AvaDevices
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.FrameSlot
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen
import osrsps.game.skill.Making

/**
 * What the Ava's Devices screen says. No transcript has its words: every one is guessed, and its words file says so.
 */
object AvaDevicesLines : Lines("ava_devices", "Ava's device; Ava's attractor; Ava's accumulator (the costs); no transcript of the screen") {
    val ATTRACTOR = line("attractor", "the attractor's cost, beside its picture (guessed)")
    val ACCUMULATOR = line("accumulator", "the accumulator's cost (guessed)")
    val ASSEMBLER = line("assembler", "the assembler's cost (guessed)")
    val LEVEL = line("level", "buying a device without the Ranged level for it (guessed)", "level", "device")
    val COINS = line("coins", "buying one without the coins (or the device it replaces) (guessed)", "coins", "device")
    val ARROWS = line("arrows", "buying one without the 75 arrows it takes (guessed)", "arrows", "device")
    val HEAD = line("head", "buying the assembler without Vorkath's head (guessed)")
    val SPACE = line("space", "buying one with a full backpack (guessed)")
}

/**
 * Ava's Devices (Draynor piece step 12c), the 241 cache's `anma_reward` screen (67), opened by Ava's Devices option and
 * her "Devices, please." Each of its three devices has Buy-1, Buy-5, Buy-X and Buy-All (the Wiki's Ava's attractor:
 * "You can now choose to buy-X devices at once"). As the Wiki has them (Ava; Ava's accumulator; Ava's device, read
 * 2026-11-02), with Animal Magnetism and Dragon Slayer II counted as done:
 *
 * - The attractor: 30 Ranged, and 999 coins (the fee for a lost device, which every account's is taken as).
 * - The accumulator: 50 Ranged, 75 steel arrows, and an attractor, or 999 coins without one (the attractor is used when
 *   the player brings both).
 * - The assembler: 70 Ranged, Vorkath's head, 75 mithril arrows, and an accumulator, or 4,999 coins without one.
 *
 * Guessed: every message ([AvaDevicesLines]), that the cost text sits in each device's contents component, that a buy
 * stops at the first one the player can't pay for, and that the screen stays open after a buy.
 */
object AvaDevicesShop {
    const val INTERFACE = 67
    const val ATTRACTOR_BUTTON = 2
    const val ACCUMULATOR_BUTTON = 7
    const val ASSEMBLER_BUTTON = 12
    private const val ATTRACTOR_TEXT = 6
    private const val ACCUMULATOR_TEXT = 11
    private const val ASSEMBLER_TEXT = 16

    const val STEEL_ARROW = 886
    const val MITHRIL_ARROW = 888
    const val VORKATHS_HEAD = 21907
    const val ARROWS = 75

    /** A device on the screen: what it takes, and the device it can be made from instead of [coins]. */
    data class Device(
        val id: Int,
        val name: String,
        val ranged: Int,
        val coins: Int,
        val from: Int? = null,
        val arrows: Int? = null,
        val arrowsName: String = "",
        val head: Boolean = false,
    )

    val ATTRACTOR = Device(AvaDevices.ATTRACTOR, "Ava's attractor", ranged = 30, coins = 999)
    val ACCUMULATOR = Device(AvaDevices.ACCUMULATOR, "Ava's accumulator", ranged = 50, coins = 999, from = AvaDevices.ATTRACTOR, arrows = STEEL_ARROW, arrowsName = "steel arrows")
    val ASSEMBLER = Device(AvaDevices.ASSEMBLER, "Ava's assembler", ranged = 70, coins = 4999, from = AvaDevices.ACCUMULATOR, arrows = MITHRIL_ARROW, arrowsName = "mithril arrows", head = true)

    val BUTTONS: Map<Int, Device> = mapOf(ATTRACTOR_BUTTON to ATTRACTOR, ACCUMULATOR_BUTTON to ACCUMULATOR, ASSEMBLER_BUTTON to ASSEMBLER)

    fun open(world: World, player: Player) {
        val text = world.textFor(player)
        player.session.queue(IfSetText(INTERFACE, ATTRACTOR_TEXT, text[AvaDevicesLines.ATTRACTOR]))
        player.session.queue(IfSetText(INTERFACE, ACCUMULATOR_TEXT, text[AvaDevicesLines.ACCUMULATOR]))
        player.session.queue(IfSetText(INTERFACE, ASSEMBLER_TEXT, text[AvaDevicesLines.ASSEMBLER]))
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
    }

    /** How many a Buy op asks for: Buy-1, Buy-5, Buy-All, or -1 for Buy-X's count. */
    fun wanted(op: Int): Int = when (op) {
        1 -> 1
        2 -> 5
        3 -> -1
        else -> Int.MAX_VALUE
    }

    /** Buys up to [wanted] of [device] for [player], one at a time, until one can't be paid for; returns how many. */
    internal fun buy(player: Player, device: Device, wanted: Int, text: DialogueText, objTypes: ObjTypes, message: (String) -> Unit): Int {
        val type = objTypes[device.id] ?: return 0
        var bought = 0
        while (bought < wanted) {
            val refusal = refusal(player, device, text)
            if (refusal != null) {
                // The first refusal is told; a run of buys that stops partway says nothing more.
                if (bought == 0) message(refusal)
                break
            }
            if (device.from != null && player.inventory.count(device.from) > 0) Making.removeObj(player.inventory, device.from, 1)
            else AlKharidTraders.pay(player, device.coins)
            device.arrows?.let { Making.removeObj(player.inventory, it, ARROWS) }
            if (device.head) Making.removeObj(player.inventory, VORKATHS_HEAD, 1)
            player.inventory.add(type, 1)
            bought++
        }
        return bought
    }

    /** Why [player] can't buy one [device] now, or null when they can. */
    private fun refusal(player: Player, device: Device, text: DialogueText): String? {
        val inv = player.inventory
        val hasFrom = device.from != null && inv.count(device.from) > 0
        return when {
            player.skills.baseLevel(Skill.RANGED) < device.ranged -> text[AvaDevicesLines.LEVEL, "level" to device.ranged, "device" to device.name]
            device.head && inv.count(VORKATHS_HEAD) < 1 -> text[AvaDevicesLines.HEAD]
            device.arrows != null && inv.count(device.arrows) < ARROWS -> text[AvaDevicesLines.ARROWS, "arrows" to device.arrowsName, "device" to device.name]
            !hasFrom && inv.count(ShopScreen.COINS) < device.coins -> text[AvaDevicesLines.COINS, "coins" to "%,d".format(device.coins), "device" to device.name]
            // A device paid for with another frees that one's slot.
            !hasFrom && inv.freeSlots < 1 -> text[AvaDevicesLines.SPACE]
            else -> null
        }
    }

    private suspend fun ScriptContext.button(world: World, device: Device, op: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        val amount = wanted(op)
        val count = if (amount < 0) countDialog() else amount
        if (count < 1 || player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        buy(player, device, count, world.textFor(player), world.objTypes, player::message)
    }

    fun register(world: World) {
        for ((component, device) in BUTTONS) {
            world.handlers.onButton(INTERFACE, component) { op -> button(world, device, op.option) }
        }
    }
}
