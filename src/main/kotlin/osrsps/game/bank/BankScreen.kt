package osrsps.game.bank

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.EquipmentBonuses
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.PlayerVars
import osrsps.game.WearPos
import osrsps.game.sendInventory
import osrsps.game.script.ScriptContext
import osrsps.game.script.ScriptRunner

private val logger = KotlinLogging.logger {}

/**
 * The bank as the player sees it: bankmain (12) over the game view and bankside (15), the inventory beside it, as
 * RSMod opens them. Withdrawing, depositing, the amount prompt, notes, rearranging, tabs, placeholders, bank fillers,
 * the incinerator, depositing the inventory and worn items, the buttons along the bottom and the settings menu.
 *
 * Interface, component, varp and varbit ids are the 241 cache's (gameval names in [CACHE_NAMES] and the comments). The
 * menus are the client's own: bankmain_drawitem and script669 build a bank slot's (Withdraw-1, -5, -10, -X, -All,
 * -All-but-1, Placeholder, then Examine as op 11; a placeholder's Release is op 8, a filler's Clear and Clear-All ops 7
 * and 8), bankmain_tabicon a tab's (View tab, Collapse tab as op 6, Remove placeholders as op 7) and bankside_drawitem
 * an inventory slot's (Deposit-1 as op 3 up to Deposit-All as op 8, Examine as op 10), from the varbits set here. The
 * buttons carry their ops in the cache (transmitop), so they need no events; the slots and tabs get theirs on opening,
 * as RSMod sends them.
 *
 * The client keeps some of the bank's varbits itself: it sets bank_currenttab when a tab or Search is clicked, and
 * flips the settings menu's toggles, before telling the server. The server sets the same values, so the varp it next
 * sends doesn't undo them. Search is the client's alone (meslayer mode 11, filtering what it draws); the server only
 * closes it with the bank, as RSMod does.
 *
 * The worn items view (the button under the settings menu's) is the client's too: bankmain_viewbuttons swaps the
 * items for the worn slots and sets bank_wornmode, a varc, and bankside_build then draws the inventory with Wear as
 * op 1 (bankside:wornops). The server fills in the bonus lines and answers the slots' ops. The inventory's extra option
 * (op 9: Wear, Wield) shows where varp if2 has the slot's bit, which [updateVars] keeps up as RSMod does.
 *
 * Not built at all yet: the inventory's Eat and Drink (varp if3, once there is food), potion storage, deposit locks,
 * bank tags' storage, Collect and buying bank space.
 */
class BankScreen(
    private val bank: Bank,
    private val objTypes: ObjTypes,
    private val scripts: ScriptRunner,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    /** Shows an item's examine text and values, as the inventory's Examine does. */
    private val examine: (Player, Int, Int) -> Unit,
    /** The worn items' bonuses, for the worn items view. */
    private val bonuses: (Player) -> EquipmentBonuses,
    /** Wears the item in an inventory slot, as its Wear does, and says why not. */
    private val wear: (Player, Int) -> Unit,
    /** Takes off what is worn in a wearpos, as the equipment tab's Remove does, and says why not. */
    private val remove: (Player, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    /** The player's bank tabs, kept in their varbits. */
    fun tabs(player: Player): BankTabs = VarbitTabs(player.vars)

    /** Opens the bank, as a booth's Bank or a banker's do. */
    fun open(player: Player) {
        send(player, RunClientScript(MAINMODAL_BACKGROUND_SCRIPT, listOf(-1, -2)))
        // RSMod's emulation: every opening starts withdrawing items, not notes. Not checked against live.
        player.vars.setVarbitIfKnown(VARBIT_WITHDRAW_NOTES, 0)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        // Before the events, which tests wait for as the sign the bank is ready.
        send(player, IfSetText(MAIN, MAIN_CAPACITY, bank.capacity().toString()))
        val lastSlot = Inventory.BANK_CAPACITY - 1
        send(player, IfSetEventsV2(MAIN, MAIN_ITEMS, 0, lastSlot, EVENTS_DRAG_DEPTH2 or EVENTS_DRAG_TARGET, opMask(1..11)))
        // The space after each tab's last item, which the client numbers past the slots (script509): main tab first.
        send(player, IfSetEventsV2(MAIN, MAIN_ITEMS, TAB_END_SUBS.first, TAB_END_SUBS.last, EVENTS_DRAG_TARGET, 0))
        send(player, IfSetEventsV2(MAIN, TABS, TAB_SUB_MAIN, TAB_SUB_MAIN, EVENTS_DRAG_TARGET, opMask(1..1) or opMask(7..7)))
        send(player, IfSetEventsV2(MAIN, TABS, TAB_SUB_MAIN + 1, TAB_SUB_MAIN + Bank.TABS, EVENTS_DRAG_DEPTH1 or EVENTS_DRAG_TARGET, opMask(1..1) or opMask(6..7)))
        send(player, IfSetEventsV2(MAIN, INCINERATOR_CONFIRM, 1, Inventory.BANK_CAPACITY, 0, opMask(1..1)))
        // The tab display dropdown's options, which script2076 numbers 1, 3, 5 and 7.
        send(player, IfSetEventsV2(MAIN, DROPDOWN_CONTENT, 1, 2 * TAB_DISPLAY_OPTIONS - 1, 0, opMask(1..1)))
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, EVENTS_DRAG_DEPTH1 or EVENTS_DRAG_TARGET, opMask(1..10)))
        // The worn items view's inventory: Wear (op 1), Deposit (op 9) and Examine (op 10), as bankside_worn_drawitem has them.
        send(player, IfSetEventsV2(SIDE, SIDE_WORNOPS, 0, Inventory.INV_CAPACITY - 1, EVENTS_DRAG_DEPTH1 or EVENTS_DRAG_TARGET, opMask(1..1) or opMask(9..10)))
        sendWornBonuses(player)
        setExtraOps(player)
        player.bank.resendAll()
    }

    /**
     * Keeps up what the bank's client scripts read from varps and texts while it is open: the inventory's extra option
     * bits and, after the equipment changed, the worn items view's bonuses. Run every cycle before the varps are sent.
     */
    fun updateVars(player: Player) {
        if (!isOpen(player)) return
        setExtraOps(player)
        if (player.equipment.hasChanges) sendWornBonuses(player)
    }

    /**
     * Varp if2, a bit for each inventory slot whose item bankside_extraop offers to wear (op 9), with the item's wear
     * option (param wear_op_index, 2 unless set) as its name: wearable items with that option and no extra option of
     * their own (param bankside_extraop), as RSMod sets it. Shared with the deposit box, whose client scripts read it too.
     */
    internal fun setExtraOps(player: Player) {
        var bits = 0
        for ((slot, obj) in player.inventory.contents()) {
            if (wearOption(obj.id) != null) bits = bits or (1 shl slot)
        }
        player.vars[VARP_EXTRAOPS_WEAR] = bits
    }

    /** The item's wear option for the bank's extra option, null when it has none there. */
    private fun wearOption(id: Int): String? {
        val type = objTypes[id] ?: return null
        if (!type.wearable || type.stringParam(PARAM_BANKSIDE_EXTRAOP) != null) return null
        return type.iop(type.intParam(PARAM_WEAR_OP_INDEX, DEFAULT_WEAR_OP_INDEX))
    }

    /**
     * Fills in the worn items view's bonus lines. The labels are RSMod's (rev 233, from live): they differ from the
     * Equipment Stats screen's ("Melee STR", "Magic DMG"), and the Undead line ends in a space while it is 0.
     */
    private fun sendWornBonuses(player: Player) {
        val bonuses = bonuses(player)
        fun text(component: Int, value: String) = send(player, IfSetText(MAIN, component, value))
        fun signed(value: Int) = if (value < 0) "$value" else "+$value"
        text(WORN_STAB_ATT, "Stab: ${signed(bonuses.stabAttack)}")
        text(WORN_SLASH_ATT, "Slash: ${signed(bonuses.slashAttack)}")
        text(WORN_CRUSH_ATT, "Crush: ${signed(bonuses.crushAttack)}")
        text(WORN_MAGIC_ATT, "Magic: ${signed(bonuses.magicAttack)}")
        text(WORN_RANGE_ATT, "Range: ${signed(bonuses.rangedAttack)}")
        text(WORN_STAB_DEF, "Stab: ${signed(bonuses.stabDefence)}")
        text(WORN_SLASH_DEF, "Slash: ${signed(bonuses.slashDefence)}")
        text(WORN_CRUSH_DEF, "Crush: ${signed(bonuses.crushDefence)}")
        text(WORN_MAGIC_DEF, "Magic: ${signed(bonuses.magicDefence)}")
        text(WORN_RANGE_DEF, "Range: ${signed(bonuses.rangedDefence)}")
        text(WORN_MELEE_STRENGTH, "Melee STR: ${signed(bonuses.meleeStrength)}")
        text(WORN_RANGED_STRENGTH, "Ranged STR: ${signed(bonuses.rangedStrength)}")
        text(WORN_MAGIC_DAMAGE, "Magic DMG: +${bonuses.magicDamage / 10.0}%")
        text(WORN_PRAYER, "Prayer: ${signed(bonuses.prayer)}")
        // Salve amulet and black mask boosts aren't item bonuses; they come with combat.
        text(WORN_TYPE_MULTIPLIER, "Undead: +0% ")
        text(WORN_SLAYER_MULTIPLIER, "Slayer: +0%")
    }

    /** What the client keeps open past the bank: the search line. RSMod closes it as the bank closes. */
    fun closed(player: Player) {
        send(player, RunClientScript(ScriptContext.MESLAYER_CLOSE, listOf(MESLAYER_SEARCH)))
    }

    /** Sends the bank's changes while it is open. Run at the end of every cycle, after the cycle's clicks. */
    fun flush(player: Player) {
        if (isOpen(player)) sendInventory(player, player.bank, send)
    }

    /** Empties the player's bank and its tabs, for `::scenario`. */
    fun clear(player: Player) = bank.clear(player.bank, tabs(player))

    /** A click on the bank or its inventory. Returns false when the click isn't on either. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, obj: Int, op: Int): Boolean {
        if (interfaceId == SIDE) {
            when (component) {
                SIDE_ITEMS -> if (isOpen(player)) clickInventory(player, sub, obj, op)
                SIDE_WORNOPS -> if (isOpen(player)) clickWornInventory(player, sub, obj, op)
                else -> return false
            }
            return true
        }
        if (interfaceId != MAIN) return false
        if (!isOpen(player)) return true
        when (component) {
            MAIN_ITEMS -> clickBank(player, sub, obj, op)
            TABS -> clickTab(player, sub, op)
            SEARCH -> tabs(player).current = Bank.MAIN_TAB
            SWAP_INSERT -> toggle(player, VARBIT_INSERT_MODE)
            NOTE -> toggle(player, VARBIT_WITHDRAW_NOTES)
            PLACEHOLDER -> toggle(player, VARBIT_LEAVE_PLACEHOLDERS)
            QUANTITY_1 -> setQuantityType(player, QUANTITY_TYPE_1)
            QUANTITY_5 -> setQuantityType(player, QUANTITY_TYPE_5)
            QUANTITY_10 -> setQuantityType(player, QUANTITY_TYPE_10)
            QUANTITY_ALL -> setQuantityType(player, QUANTITY_TYPE_ALL)
            QUANTITY_X -> if (op == 2) {
                start(player) {
                    val amount = countDialog()
                    if (amount > 0 && isOpen(player)) {
                        player.vars.setVarbitIfKnown(VARBIT_REQUESTED_QUANTITY, amount)
                        setQuantityType(player, QUANTITY_TYPE_X)
                    }
                }
            } else {
                setQuantityType(player, QUANTITY_TYPE_X)
            }
            DEPOSIT_INVENTORY -> depositAll(player, player.inventory)
            DEPOSIT_WORN -> depositAll(player, player.equipment)
            INCINERATOR_CONFIRM -> incinerate(player, sub - 1, obj)
            DROPDOWN_CONTENT -> if (sub % 2 == 1 && sub / 2 < TAB_DISPLAY_OPTIONS) player.vars.setVarbitIfKnown(VARBIT_TAB_DISPLAY, sub / 2)
            RELEASE_PLACEHOLDERS -> releaseAllPlaceholders(player)
            FILLER_1 -> setFillerMode(player, FILLER_MODE_1)
            FILLER_10 -> setFillerMode(player, FILLER_MODE_10)
            FILLER_50 -> setFillerMode(player, FILLER_MODE_50)
            FILLER_X -> setFillerMode(player, FILLER_MODE_X)
            FILLER_ALL -> setFillerMode(player, FILLER_MODE_ALL)
            FILLER_CONFIRM -> fill(player)
            in SETTINGS_TOGGLES -> toggle(player, SETTINGS_TOGGLES.getValue(component))
            in WORN_SLOTS -> clickWorn(player, WORN_SLOT_WEARPOS.getValue(component), op)
            else -> logger.info { "${player.username} clicked bank component $component op $op (not built yet)" }
        }
        return true
    }

    /**
     * The player dragged a bank slot onto another, onto the space after a tab or onto a tab, a tab onto another, or an
     * inventory slot onto another. Returns false for any other drag.
     */
    fun drag(player: Player, interfaceId: Int, component: Int, from: Int, fromObj: Int, toInterfaceId: Int, toComponent: Int, to: Int): Boolean {
        val items = interfaceId == MAIN && component == MAIN_ITEMS
        val tabIcon = interfaceId == MAIN && component == TABS
        val side = interfaceId == SIDE && component == SIDE_ITEMS
        if (!items && !tabIcon && !side) return false
        if (!isOpen(player) || toInterfaceId != interfaceId) return true
        val tabs = tabs(player)
        if (tabIcon) {
            if (toComponent == TABS && !player.scripts.delayed) bank.moveTab(player.bank, tabs, from - TAB_SUB_MAIN, to - TAB_SUB_MAIN)
            return true
        }
        val inv = if (items) player.bank else player.inventory
        val held = inv.takeIf { from in 0 until it.capacity }?.get(from)
        if (held == null || held.id != fromObj || player.scripts.delayed) {
            // The client may have moved it already; put both slots back as they are.
            if (from in 0 until inv.capacity) inv.resend(from)
            if (to in 0 until inv.capacity) inv.resend(to)
            return true
        }
        if (side) {
            if (toComponent == SIDE_ITEMS && to in 0 until Inventory.INV_CAPACITY && to != from) inv.swap(from, to)
            return true
        }
        val moved = when {
            toComponent == TABS -> bank.moveToTab(inv, tabs, from, to - TAB_SUB_MAIN)
            toComponent != MAIN_ITEMS -> false
            to in TAB_END_SUBS -> bank.moveToTab(inv, tabs, from, to - TAB_END_SUBS.first)
            else -> bank.move(inv, tabs, from, to, insert = player.vars.getVarbitIfKnown(VARBIT_INSERT_MODE) == 1)
        }
        if (!moved) {
            inv.resend(from)
            if (to in 0 until inv.capacity) inv.resend(to)
        }
        return true
    }

    private fun clickBank(player: Player, slot: Int, obj: Int, op: Int) {
        val held = player.bank.takeIf { slot in 0 until Inventory.BANK_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) {
            // Seen on the test VM: a click sent before the client redrew the bank after a change carries the old item.
            logger.info { "${player.username} clicked bank slot $slot as $obj, but it holds $held" }
            return
        }
        val type = objTypes[held.id] ?: return
        if (op == MAIN_EXAMINE) {
            examine(player, held.id, held.count)
            return
        }
        if (player.scripts.delayed) return
        val tabs = tabs(player)
        if (held.id == Bank.FILLER) {
            when (op) {
                MAIN_CLEAR -> bank.discard(player.bank, tabs, slot)
                MAIN_CLEAR_ALL -> start(player) {
                    if (confirm(player, CLEAR_FILLERS_TITLE, CLEAR_FILLERS_TEXT, CLEAR_FILLERS_CONFIRM)) bank.clearFillers(player.bank, tabs)
                }
            }
            return
        }
        if (type.isPlaceholder) {
            if (op == MAIN_RELEASE) bank.discard(player.bank, tabs, slot)
            return
        }
        val leave = player.vars.getVarbitIfKnown(VARBIT_LEAVE_PLACEHOLDERS) == 1
        when (op) {
            1 -> withdraw(player, slot, held.id, defaultQuantity(player, held.count), leave)
            2 -> withdraw(player, slot, held.id, 1, leave)
            3 -> withdraw(player, slot, held.id, 5, leave)
            4 -> withdraw(player, slot, held.id, 10, leave)
            5 -> withdraw(player, slot, held.id, requested(player), leave)
            6 -> start(player) {
                val amount = askAmount()
                if (amount > 0) withdraw(player, slot, held.id, amount, leave)
            }
            7 -> withdraw(player, slot, held.id, held.count, leave)
            8 -> if (held.count > 1) withdraw(player, slot, held.id, held.count - 1, leave)
            10 -> withdraw(player, slot, held.id, held.count, placeholder = true)
            else -> logger.info { "${player.username} picked bank op $op on $type (not built yet)" }
        }
    }

    /** A click on tab icon [sub]: 10 the main tab ("View all items"), 11 to 19 tabs 1 to 9, and the "New tab" after. */
    private fun clickTab(player: Player, sub: Int, op: Int) {
        val tab = sub - TAB_SUB_MAIN
        if (tab !in 0..Bank.TABS) return
        val tabs = tabs(player)
        if (tab != Bank.MAIN_TAB && tabs[tab] == 0) {
            message(player, NEW_TAB)
            return
        }
        // RSMod closes the search line when a numbered tab is clicked, which the client's switch to it ends anyway.
        if (tab != Bank.MAIN_TAB) send(player, RunClientScript(ScriptContext.MESLAYER_CLOSE, listOf(MESLAYER_SEARCH)))
        when (op) {
            1 -> tabs.current = tab
            6 -> if (tab != Bank.MAIN_TAB && !player.scripts.delayed) bank.collapse(player.bank, tabs, tab)
            // RSMod: the main tab's takes only the main tab's placeholders, not every tab's.
            7 -> if (!player.scripts.delayed && bank.releasePlaceholders(player.bank, tabs, tab) == 0) message(player, NO_PLACEHOLDERS)
        }
    }

    private fun clickInventory(player: Player, slot: Int, obj: Int, op: Int) {
        val held = player.inventory.takeIf { slot in 0 until Inventory.INV_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) return
        if (op == SIDE_EXAMINE) {
            examine(player, held.id, held.count)
            return
        }
        if (player.scripts.delayed) return
        when (op) {
            2 -> deposit(player, slot, held.id, defaultQuantity(player, Int.MAX_VALUE))
            3 -> deposit(player, slot, held.id, 1)
            4 -> deposit(player, slot, held.id, 5)
            5 -> deposit(player, slot, held.id, 10)
            6 -> deposit(player, slot, held.id, requested(player))
            7 -> start(player) {
                val amount = askAmount()
                if (amount > 0) deposit(player, slot, held.id, amount)
            }
            8 -> deposit(player, slot, held.id, Int.MAX_VALUE)
            SIDE_EXTRAOP -> clickExtraOp(player, slot, held.id)
            else -> logger.info { "${player.username} picked bank inventory op $op on ${held.id} (not built yet)" }
        }
    }

    /** The inventory's extra option, here and in the deposit box: Wear, unless the settings menu hides it. */
    internal fun clickExtraOp(player: Player, slot: Int, id: Int) {
        if (player.vars.getVarbitIfKnown(VARBIT_HIDE_SIDEOPS) == 0 && wearOption(id) != null) wear(player, slot)
    }

    /** The worn items view's inventory: Wear, Deposit (all of the slot's stack) and Examine. */
    private fun clickWornInventory(player: Player, slot: Int, obj: Int, op: Int) {
        val held = player.inventory.takeIf { slot in 0 until Inventory.INV_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) return
        when (op) {
            SIDE_EXAMINE -> examine(player, held.id, held.count)
            1 -> if (!player.scripts.delayed && wearOption(held.id) != null) wear(player, slot)
            SIDE_EXTRAOP -> if (!player.scripts.delayed) deposit(player, slot, held.id, Int.MAX_VALUE)
        }
    }

    /**
     * A worn slot of the worn items view (or the deposit box's, which [DepositBox] sends here): Remove (op 1), Bank
     * (op 2, the whole stack) and Examine (op 10), as wear_updateslot names them.
     */
    internal fun clickWorn(player: Player, wearpos: Int, op: Int) {
        val worn = player.equipment[wearpos] ?: return
        if (op == WORN_EXAMINE) {
            examine(player, worn.id, worn.count)
            return
        }
        if (player.scripts.delayed) return
        when (op) {
            1 -> remove(player, wearpos)
            2 -> depositFrom(player, player.equipment, wearpos, worn.id, Int.MAX_VALUE)
        }
    }

    /**
     * Withdraws from [slot], if it still holds [id]: the click may have waited on an amount prompt. The messages are
     * RSMod's, as it has them from live.
     */
    private fun withdraw(player: Player, slot: Int, id: Int, count: Int, placeholder: Boolean) {
        if (!isOpen(player) || player.bank[slot]?.id != id) return
        val notes = player.vars.getVarbitIfKnown(VARBIT_WITHDRAW_NOTES) == 1
        val result = bank.withdraw(player.bank, tabs(player), player.inventory, slot, count, notes, placeholder)
        if (result.count == 0) {
            if (result.short) message(player, NO_INVENTORY_SPACE)
            return
        }
        if (result.notNoted) message(player, CANNOT_NOTE)
        if (result.short) message(player, NOT_ENOUGH_SPACE_FOR_ALL)
    }

    private fun deposit(player: Player, slot: Int, id: Int, count: Int) {
        if (isOpen(player)) depositFrom(player, player.inventory, slot, id, count)
    }

    /** Deposits [count] from [slot] of [from], if it still holds [id], and says why not all of it went. */
    internal fun depositFrom(player: Player, from: Inventory, slot: Int, id: Int, count: Int) {
        if (from[slot]?.id != id) return
        when (bank.deposit(player.bank, tabs(player), from, slot, count)) {
            Bank.Deposit.NotBankable -> message(player, NOT_BANKABLE)
            Bank.Deposit.BankFull -> message(player, BANK_FULL)
            Bank.Deposit.FullStack, is Bank.Deposit.Partly -> message(player, FULL_STACK)
            is Bank.Deposit.Done, Bank.Deposit.Nothing -> Unit
        }
    }

    internal fun depositAll(player: Player, from: Inventory) {
        if (player.scripts.delayed) return
        if (from.isEmpty()) {
            message(player, NOTHING_TO_DEPOSIT)
            return
        }
        val result = bank.depositAll(player.bank, tabs(player), from)
        when {
            result.noRoom > 0 -> message(player, CANNOT_HOLD)
            result.notBankable > 0 && result.deposited == 0 -> message(player, NONE_BANKABLE)
            result.notBankable > 0 -> message(player, SOME_NOT_BANKABLE)
        }
    }

    /**
     * The incinerator's Burn on bank [slot]: the client asks first and sends it from incinerator_confirm, numbered from
     * 1. RSMod plays a sound and says nothing; placeholders can't be burnt.
     */
    private fun incinerate(player: Player, slot: Int, obj: Int) {
        if (player.scripts.delayed) return
        val held = player.bank.takeIf { slot in 0 until Inventory.BANK_CAPACITY }?.get(slot)
        if (held == null || held.id != obj || objTypes[held.id]?.isPlaceholder != false) {
            if (slot in 0 until Inventory.BANK_CAPACITY) player.bank.resend(slot)
            return
        }
        bank.discard(player.bank, tabs(player), slot, any = true)
    }

    private fun releaseAllPlaceholders(player: Player) {
        if (!bank.hasPlaceholders(player.bank)) return
        start(player) {
            if (confirm(player, RELEASE_TITLE, RELEASE_TEXT, RELEASE_CONFIRM)) bank.releasePlaceholders(player.bank, tabs(player), null)
        }
    }

    /** The settings menu's Fill: as many fillers as the filler mode says, asking for X. Messages are RSMod's. */
    private fun fill(player: Player) {
        if (bank.used(player.bank) >= bank.capacity()) {
            message(player, BANK_ALREADY_FULL)
            return
        }
        start(player) {
            val count = when (player.vars.getVarbitIfKnown(VARBIT_FILLER_MODE)) {
                FILLER_MODE_1 -> 1
                FILLER_MODE_10 -> 10
                FILLER_MODE_50 -> 50
                FILLER_MODE_X -> countDialog()
                else -> Int.MAX_VALUE
            }
            if (count <= 0 || !isOpen(player)) return@start
            val added = bank.addFillers(player.bank, tabs(player), count)
            message(player, if (added == 0) BANK_ALREADY_FULL else "You add $added ${if (added == 1) "bank filler" else "bank fillers"} to your bank.")
        }
    }

    /**
     * Asks to confirm in popupoverlay over the bank, as RSMod's confirmOverlay does: script4212 fills it in, and its
     * buttons send resume_p_countdialog 0 (Cancel) or 1. False when cancelled or the bank closed meanwhile.
     */
    private suspend fun ScriptContext.confirm(player: Player, title: String, text: String, confirm: String): Boolean {
        send(player, IfOpenSub(MAIN, POPUP, POPUPOVERLAY, OVERLAY))
        send(player, RunClientScript(CONFIRM_OVERLAY_SCRIPT, listOf("$title|$text|$CANCEL|$confirm", MAIN shl 16 or POPUP)))
        val answer = waitForCount()
        if (!isOpen(player)) return false
        send(player, IfCloseSub(MAIN, POPUP))
        return answer != 0
    }

    /** What the left-click option takes: the quantity type's amount, All being [all]. */
    private fun defaultQuantity(player: Player, all: Int): Int = when (player.vars.getVarbitIfKnown(VARBIT_QUANTITY_TYPE)) {
        QUANTITY_TYPE_5 -> 5
        QUANTITY_TYPE_10 -> 10
        QUANTITY_TYPE_X -> requested(player)
        QUANTITY_TYPE_ALL -> all
        else -> 1
    }

    /** The last amount typed, at least 1, as the client's own Withdraw-X label has it (script2579). */
    private fun requested(player: Player): Int = maxOf(1, player.vars.getVarbitIfKnown(VARBIT_REQUESTED_QUANTITY))

    /** Asks for an amount, which becomes the requested quantity the client offers next to Withdraw-X (op 5). */
    internal suspend fun ScriptContext.askAmount(): Int {
        val amount = countDialog()
        if (amount > 0) player.vars.setVarbitIfKnown(VARBIT_REQUESTED_QUANTITY, amount)
        return amount
    }

    internal fun start(player: Player, script: suspend ScriptContext.() -> Unit) {
        if (!player.scripts.delayed) scripts.start(player, script)
    }

    private fun toggle(player: Player, varbit: Int) {
        player.vars.setVarbitIfKnown(varbit, 1 - player.vars.getVarbitIfKnown(varbit))
    }

    private fun setQuantityType(player: Player, type: Int) = player.vars.setVarbitIfKnown(VARBIT_QUANTITY_TYPE, type)

    private fun setFillerMode(player: Player, mode: Int) = player.vars.setVarbitIfKnown(VARBIT_FILLER_MODE, mode)

    private fun message(player: Player, text: String) = send(player, MessageGame(CHATTYPE_GAMEMESSAGE, text))

    /** The tab sizes and the tab being viewed, in the bank_tab_1 to 9 and bank_currenttab varbits. */
    private class VarbitTabs(private val vars: PlayerVars) : BankTabs {
        override fun get(tab: Int): Int = vars.getVarbitIfKnown(VARBIT_TAB_1 + tab - 1)

        override fun set(tab: Int, size: Int) = vars.setVarbitIfKnown(VARBIT_TAB_1 + tab - 1, size)

        override var current: Int
            get() = vars.getVarbitIfKnown(VARBIT_CURRENT_TAB)
            set(value) = vars.setVarbitIfKnown(VARBIT_CURRENT_TAB, value)
    }

    companion object {
        /** bankmain and its components. */
        const val MAIN = 12
        const val MAIN_CAPACITY = 8
        const val TABS = 10
        const val MAIN_ITEMS = 12
        const val SWAP_INSERT = 23
        const val NOTE = 25
        const val QUANTITY_1 = 29
        const val QUANTITY_5 = 31
        const val QUANTITY_10 = 33
        const val QUANTITY_X = 35
        const val QUANTITY_ALL = 37
        const val PLACEHOLDER = 40
        const val SEARCH = 42
        const val DEPOSIT_INVENTORY = 47
        const val DEPOSIT_WORN = 49
        const val INCINERATOR_CONFIRM = 55
        const val POPUP = 109
        const val DROPDOWN_CONTENT = 121
        const val INCINERATOR_TOGGLE = 130
        const val BANKTUT_TOGGLE = 131
        const val SIDEOPS_TOGGLE = 132
        const val BANKOPS_TOGGLE = 133
        const val DEPOSITINV_TOGGLE = 134
        const val DEPOSITWORN_TOGGLE = 135
        const val DEPOSITCONTAINERS_TOGGLE = 136
        const val POPUP_TAB_FORGETPOS = 137
        const val DEPOSITPOTION_TOGGLE = 138
        const val CHUGGINGBARREL_TOGGLE = 139
        const val RELEASE_PLACEHOLDERS = 140
        const val FILLER_1 = 143
        const val FILLER_10 = 145
        const val FILLER_50 = 147
        const val FILLER_X = 149
        const val FILLER_ALL = 151
        const val FILLER_CONFIRM = 153

        /** The tab icons' subs: 10 the main tab, then tabs 1 to 9 (bankmain_tabicon makes tab n's as n + 10). */
        const val TAB_SUB_MAIN = 10

        /**
         * The space after a tab's last item, drawn past the bank's 1,410 slots and the 18 separators: 1428 for the main
         * tab, then tabs 1 to 9 (script509's 1410 + 9 * 2 + tab).
         */
        val TAB_END_SUBS = (Inventory.BANK_CAPACITY + 18)..(Inventory.BANK_CAPACITY + 18 + Bank.TABS)

        /** bankside and its inventory. */
        const val SIDE = 15
        const val SIDE_ITEMS = 3
        const val SIDE_WORNOPS = 4

        /** bankmain's worn slots in the worn items view, wornslot0 to wornslot13, by wearpos (the number in the name). */
        val WORN_SLOT_WEARPOS = mapOf(
            68 to WearPos.HEAD, 69 to WearPos.CAPE, 70 to WearPos.NECK, 71 to WearPos.WEAPON, 72 to WearPos.BODY,
            73 to WearPos.SHIELD, 74 to WearPos.LEGS, 75 to WearPos.HANDS, 76 to WearPos.FEET, 77 to WearPos.RING,
            78 to WearPos.AMMO,
        )
        val WORN_SLOTS = WORN_SLOT_WEARPOS.keys
        const val WORN_EXAMINE = 10

        /** The worn items view's bonus lines (stat_group). 241 has no attack speed lines here, which RSMod's 233 had. */
        const val WORN_STAB_ATT = 82
        const val WORN_SLASH_ATT = 83
        const val WORN_CRUSH_ATT = 84
        const val WORN_MAGIC_ATT = 85
        const val WORN_RANGE_ATT = 86
        const val WORN_STAB_DEF = 88
        const val WORN_SLASH_DEF = 89
        const val WORN_CRUSH_DEF = 90
        const val WORN_MAGIC_DEF = 91
        const val WORN_RANGE_DEF = 92
        const val WORN_MELEE_STRENGTH = 94
        const val WORN_RANGED_STRENGTH = 95
        const val WORN_MAGIC_DAMAGE = 96
        const val WORN_PRAYER = 97
        const val WORN_TYPE_MULTIPLIER = 99
        const val WORN_SLAYER_MULTIPLIER = 100

        /** The inventory's extra option (bankside_extraop), op 9. */
        const val SIDE_EXTRAOP = 9

        /** Varp if2: a bit per inventory slot whose item the extra option wears. */
        const val VARP_EXTRAOPS_WEAR = 262

        /** bank_hidesideops, the settings menu's toggle that hides the extra option. */
        const val VARBIT_HIDE_SIDEOPS = 10079

        /** Params wear_op_index (which inventory option wears the item; 2 by default) and bankside_extraop. */
        const val PARAM_WEAR_OP_INDEX = 148
        const val DEFAULT_WEAR_OP_INDEX = 2
        const val PARAM_BANKSIDE_EXTRAOP = 595

        /** A bank slot's Examine (op 11 in 241; 10 in RSMod's rev 233), a placeholder's Release, a filler's Clear and Clear-All. */
        const val MAIN_EXAMINE = 11
        const val MAIN_RELEASE = 8
        const val MAIN_CLEAR = 7
        const val MAIN_CLEAR_ALL = 8
        const val SIDE_EXAMINE = 10

        /** toplevel_mainmodal_background, which RSMod runs with -1, -2 before the bank opens. */
        const val MAINMODAL_BACKGROUND_SCRIPT = 917

        /** popupoverlay, opened over bankmain:popup, and script4212, which fills in its question and buttons. */
        const val POPUPOVERLAY = 289
        const val CONFIRM_OVERLAY_SCRIPT = 4212
        private const val OVERLAY = 1

        /** The search line's meslayer mode, which meslayer_close is given to close it. */
        const val MESLAYER_SEARCH = 11

        const val VARBIT_LEAVE_PLACEHOLDERS = 3755
        const val VARBIT_WITHDRAW_NOTES = 3958
        const val VARBIT_INSERT_MODE = 3959
        const val VARBIT_REQUESTED_QUANTITY = 3960
        const val VARBIT_CURRENT_TAB = 4150
        const val VARBIT_TAB_DISPLAY = 4170

        /** bank_tab_1; tabs 2 to 9 follow it, to 4179. */
        const val VARBIT_TAB_1 = 4171
        const val VARBIT_FILLER_MODE = 6281
        const val VARBIT_QUANTITY_TYPE = 6590

        /** The settings menu's toggles and the varbits the client flips for them (script1428's op scripts). */
        val SETTINGS_TOGGLES = mapOf(
            INCINERATOR_TOGGLE to 5102, // bank_showincinerator
            BANKTUT_TOGGLE to 10336, // bank_hidebanktut
            SIDEOPS_TOGGLE to 10079, // bank_hidesideops
            BANKOPS_TOGGLE to 16125, // bank_bankops_toggle_on
            DEPOSITINV_TOGGLE to 8352, // bank_hidedepositinv
            DEPOSITWORN_TOGGLE to 5364, // bank_hidedepositworn
            DEPOSITCONTAINERS_TOGGLE to 19721, // bank_hidedepositcontainers
            POPUP_TAB_FORGETPOS to 19723, // bank_setting_forget_popup
            DEPOSITPOTION_TOGGLE to 11437, // bank_depositpotion
            CHUGGINGBARREL_TOGGLE to 17871, // bank_chugging_fill_open
        )

        /** bank_quantity_type's values, as script2579 reads them. */
        const val QUANTITY_TYPE_1 = 0
        const val QUANTITY_TYPE_5 = 1
        const val QUANTITY_TYPE_10 = 2
        const val QUANTITY_TYPE_X = 3
        const val QUANTITY_TYPE_ALL = 4

        /** bank_fillermode's values, as enum_1907 gives them for the buttons (All is its default, 0). */
        const val FILLER_MODE_ALL = 0
        const val FILLER_MODE_1 = 1
        const val FILLER_MODE_10 = 2
        const val FILLER_MODE_50 = 3
        const val FILLER_MODE_X = 4

        /** Tab display's options in enum_5586: first item, digit, Roman numeral, hide the tab bar. */
        const val TAB_DISPLAY_OPTIONS = 4

        /** Event bits as RSMod's IfEvent has them: drag depth in bits 17 to 19, drag target bit 20, op n in the high word's bit n - 1. */
        const val EVENTS_DRAG_DEPTH1 = 1 shl 17
        const val EVENTS_DRAG_DEPTH2 = 2 shl 17
        const val EVENTS_DRAG_TARGET = 1 shl 20

        fun opMask(ops: IntRange): Int = ops.fold(0) { mask, op -> mask or (1 shl (op - 1)) }

        const val NO_INVENTORY_SPACE = "You don't have enough inventory space."
        const val NOT_ENOUGH_SPACE_FOR_ALL = "You don't have enough inventory space to withdraw that many."
        const val CANNOT_NOTE = "This item cannot be withdrawn as a note."
        const val NOT_BANKABLE = "A magical force prevents you from banking this item!"
        const val BANK_FULL = "You don't have enough space in your bank account."
        const val FULL_STACK = "You already have a full stack of that item in the bank."
        const val NOTHING_TO_DEPOSIT = "You have nothing to deposit."
        const val CANNOT_HOLD = "Your bank cannot hold your items."
        const val NONE_BANKABLE = "Your items cannot be stored in the bank."
        const val SOME_NOT_BANKABLE = "Some of your items cannot be stored in the bank."
        const val NEW_TAB = "To create a new tab, drag items from your bank onto this tab."
        const val NO_PLACEHOLDERS = "You don't have any placeholders to release."
        const val BANK_ALREADY_FULL = "Your bank is already full, so there is no reason to add any bank fillers."
        const val RELEASE_TITLE = "Release all placeholders?"
        const val RELEASE_TEXT = "This option will release <col=ffb83f>all</col> the placeholders throughout your <col=ffb83f>whole bank</col>.<br><br>Are you sure?"
        const val RELEASE_CONFIRM = "<col=0dc10d>Release them all</col>"
        const val CLEAR_FILLERS_TITLE = "Clear all fillers?"
        const val CLEAR_FILLERS_TEXT = "This option will clear <col=ffb83f>all</col> the bank fillers throughout your <col=ffb83f>whole bank</col>.<br><br>Are you sure?"
        const val CLEAR_FILLERS_CONFIRM = "<col=0dc10d>Clear them all</col>"
        private const val CANCEL = "<col=ff0000>Cancel</col>"
        private const val CHATTYPE_GAMEMESSAGE = 0

        /** Every id above with its gameval name in the 241 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to (
                "bankmain" to mapOf(
                    MAIN_CAPACITY to "capacity", TABS to "tabs", MAIN_ITEMS to "items", SWAP_INSERT to "swap_insert", NOTE to "note",
                    QUANTITY_1 to "quantity1", QUANTITY_5 to "quantity5", QUANTITY_10 to "quantity10", QUANTITY_X to "quantityx",
                    QUANTITY_ALL to "quantityall", PLACEHOLDER to "placeholder", SEARCH to "search", DEPOSIT_INVENTORY to "depositinv",
                    DEPOSIT_WORN to "depositworn", INCINERATOR_CONFIRM to "incinerator_confirm", POPUP to "popup",
                    DROPDOWN_CONTENT to "dropdown_content", INCINERATOR_TOGGLE to "incinerator_toggle", BANKTUT_TOGGLE to "banktut_toggle",
                    SIDEOPS_TOGGLE to "sideops_toggle", BANKOPS_TOGGLE to "bankops_toggle", DEPOSITINV_TOGGLE to "depositinv_toggle",
                    DEPOSITWORN_TOGGLE to "depositworn_toggle", DEPOSITCONTAINERS_TOGGLE to "depositcontainers_toggle",
                    POPUP_TAB_FORGETPOS to "popup_tab_forgetpos", DEPOSITPOTION_TOGGLE to "depositpotion_toggle",
                    CHUGGINGBARREL_TOGGLE to "chuggingbarrel_toggle", RELEASE_PLACEHOLDERS to "release_placeholders",
                    FILLER_1 to "bank_filler_1", FILLER_10 to "bank_filler_10", FILLER_50 to "bank_filler_50", FILLER_X to "bank_filler_x",
                    FILLER_ALL to "bank_filler_all", FILLER_CONFIRM to "bank_filler_confirm",
                ) + WORN_SLOT_WEARPOS.mapValues { (_, wearpos) -> "wornslot$wearpos" } + mapOf(
                    WORN_STAB_ATT to "stabatt", WORN_SLASH_ATT to "slashatt", WORN_CRUSH_ATT to "crushatt", WORN_MAGIC_ATT to "magicatt",
                    WORN_RANGE_ATT to "rangeatt", WORN_STAB_DEF to "stabdef", WORN_SLASH_DEF to "slashdef", WORN_CRUSH_DEF to "crushdef",
                    WORN_MAGIC_DEF to "magicdef", WORN_RANGE_DEF to "rangedef", WORN_MELEE_STRENGTH to "meleestrength",
                    WORN_RANGED_STRENGTH to "rangestrength", WORN_MAGIC_DAMAGE to "magicdamage", WORN_PRAYER to "prayer",
                    WORN_TYPE_MULTIPLIER to "typemultiplier", WORN_SLAYER_MULTIPLIER to "slayermultiplier",
                )
                ),
            SIDE to ("bankside" to mapOf(SIDE_ITEMS to "items", SIDE_WORNOPS to "wornops")),
            POPUPOVERLAY to ("popupoverlay" to emptyMap()),
        )
    }
}

/** The varbit's value, 0 when the cache has no such varbit (tests). */
internal fun PlayerVars.getVarbitIfKnown(id: Int): Int = if (types.varbit(id) == null) 0 else getVarbit(id)

/** Sets the varbit; does nothing when the cache has no such varbit (tests). */
internal fun PlayerVars.setVarbitIfKnown(id: Int, value: Int) {
    if (types.varbit(id) != null) setVarbit(id, value)
}
