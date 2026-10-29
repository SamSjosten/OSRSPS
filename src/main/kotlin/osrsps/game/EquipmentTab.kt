package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.combat.Autocast
import osrsps.game.combat.Spells
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * The worn equipment as the player sees it: the equipment tab (wornitems, 387), the Equipment Stats screen
 * (equipment, 84, with its side inventory equipment_side, 85), the combat tab's weapon category, style buttons and spell
 * buttons (combat_interface, 593) with the autocast menu they open (autocast, 201), and the worn models and stance on
 * the player's model.
 *
 * Interface and component ids are from the 241 cache's gameval names. The tab's slots and the stats screen's slots
 * have every op in their cache click mask, so they need no events from the server; the stats screen's side inventory
 * is built by client script 149 (6 ints, 5 strings in the 241 cache), as RSMod does.
 */
class EquipmentTab(
    private val equipment: Equipment,
    private val objTypes: ObjTypes,
    private val scripts: ScriptRunner,
    private val inventoryTab: InventoryTab,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
    /** The spells the autocast menu can pick. */
    private val spells: Spells = Spells.NONE,
) {
    /** A click on the equipment tab: its stats button or one of its slots, which are a component each. */
    fun clickWornItems(player: Player, component: Int, op: Int): Boolean {
        if (component == WORNITEMS_VIEW_STATS) {
            openStats(player)
            return true
        }
        val wearpos = WORNITEMS_SLOTS.indexOf(component).takeIf { it >= 0 }?.let { SLOT_WEARPOS[it] } ?: return false
        clickWorn(player, wearpos, op)
        return true
    }

    /** A click on the Equipment Stats screen or its side inventory. */
    fun clickStats(player: Player, interfaceId: Int, component: Int, sub: Int, obj: Int, op: Int): Boolean {
        if (interfaceId == STATS_SIDE && component == STATS_SIDE_ITEMS) {
            val held = player.inventory.takeIf { sub in 0 until Inventory.INV_CAPACITY }?.get(sub)
            if (held == null || held.id != obj) return true
            val type = objTypes[held.id] ?: return true
            when (op) {
                1 -> if (!player.scripts.delayed) wear(player, sub)
                10 -> inventoryTab.examine(player, type, held.count)
            }
            return true
        }
        if (interfaceId != STATS) return false
        val wearpos = STATS_SLOTS.indexOf(component).takeIf { it >= 0 }?.let { SLOT_WEARPOS[it] } ?: return false
        clickWorn(player, wearpos, op)
        return true
    }

    /**
     * Op 1 is Remove and op 10 Examine; ops 2 to 9 are the item's own options, its params 451 to 458, as the ring
     * of dueling's "Emir's Arena" (451) in the 241 cache. Inferred from the cache's ops and RSMod, not seen on live.
     */
    private fun clickWorn(player: Player, wearpos: Int, op: Int) {
        val worn = player.equipment[wearpos] ?: return
        val type = objTypes[worn.id] ?: return
        if (op == 10) {
            inventoryTab.examine(player, type, worn.count)
            return
        }
        if (player.scripts.delayed) return
        if (op == 1) {
            report(player, equipment.remove(player, wearpos))
            return
        }
        val option = op - 1
        if (option !in 1..WORN_OPS || type.stringParam(PARAM_WORN_OP1 + option - 1) == null) return
        if (scripts.trigger(player, Op(OpTarget.WORN, type.id, option, wearpos))) return
        logger.info { "${player.username} picked worn op $option on $type (not handled)" }
        send(player, MessageGame(CHATTYPE_GAMEMESSAGE, InventoryTab.NOTHING_INTERESTING))
    }

    /** Wears the item in inventory [slot], from the inventory's Wear/Wield/Equip or the stats screen's Equip. */
    fun wear(player: Player, slot: Int) = report(player, equipment.wear(player, slot))

    /**
     * Tells the player why a wear or remove didn't happen. The wording is from memory of live and not checked: live
     * may say it differently.
     */
    private fun report(player: Player, result: EquipResult) {
        val lines = when (result) {
            EquipResult.Done, EquipResult.Ignored -> return
            EquipResult.NoSpace -> listOf(NO_SPACE)
            is EquipResult.LevelTooLow -> {
                val skill = result.requirement.skill.displayName
                val article = if (skill.first().lowercaseChar() in "aeiou") "an" else "a"
                listOf(LEVEL_TOO_LOW, "You need to have $article $skill level of ${result.requirement.level}.")
            }
        }
        for (line in lines) send(player, MessageGame(CHATTYPE_GAMEMESSAGE, line))
    }

    /** Opens Equipment Stats over the game view, with the side inventory in place of the side panels. */
    fun openStats(player: Player) {
        openModal(player, FrameSlot.MAINMODAL, STATS)
        openModal(player, FrameSlot.SIDEMODAL, STATS_SIDE)
        // Guessed arguments, after RSMod: the component, inv 93, 4 columns by 7 rows, no dragging, one option.
        val items = (STATS_SIDE shl 16) or STATS_SIDE_ITEMS
        send(player, RunClientScript(INV_INIT_SCRIPT, listOf(items, Inventory.INV, 4, 7, 0, -1, "Equip", "", "", "", "")))
        send(player, IfSetEventsV2(STATS_SIDE, STATS_SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, STATS_SIDE_EVENTS))
        sendStats(player)
    }

    /** Fills in the stats screen's bonuses. The text of each line is from memory of live, not checked. */
    fun sendStats(player: Player) {
        val bonuses = equipment.bonuses(player)
        fun text(component: Int, value: String) = send(player, IfSetText(STATS, component, value))
        text(STAB_ATT, "Stab: ${signed(bonuses.stabAttack)}")
        text(SLASH_ATT, "Slash: ${signed(bonuses.slashAttack)}")
        text(CRUSH_ATT, "Crush: ${signed(bonuses.crushAttack)}")
        text(MAGIC_ATT, "Magic: ${signed(bonuses.magicAttack)}")
        text(RANGE_ATT, "Range: ${signed(bonuses.rangedAttack)}")
        text(STAB_DEF, "Stab: ${signed(bonuses.stabDefence)}")
        text(SLASH_DEF, "Slash: ${signed(bonuses.slashDefence)}")
        text(CRUSH_DEF, "Crush: ${signed(bonuses.crushDefence)}")
        text(MAGIC_DEF, "Magic: ${signed(bonuses.magicDefence)}")
        text(RANGE_DEF, "Range: ${signed(bonuses.rangedDefence)}")
        text(MELEE_STRENGTH, "Melee strength: ${signed(bonuses.meleeStrength)}")
        text(RANGED_STRENGTH, "Ranged strength: ${signed(bonuses.rangedStrength)}")
        text(MAGIC_DAMAGE, "Magic damage: ${signedTenths(bonuses.magicDamage)}%")
        text(PRAYER, "Prayer: ${signed(bonuses.prayer)}")
        // Salve amulet and black mask boosts aren't item bonuses; they come with combat.
        text(TYPE_MULTIPLIER, "Undead: 0.0%")
        text(SLAYER_MULTIPLIER, "Slayer: 0.0%")
        text(SPEED_BASE, "Base: ${seconds(bonuses.attackSpeed)}")
        text(SPEED_ACTUAL, "Actual: ${seconds(actualSpeed(player, bonuses.attackSpeed))}")
    }

    /** The rapid style shortens a ranged weapon's attack by a tick (OSRS Wiki, from memory). */
    private fun actualSpeed(player: Player, base: Int): Int {
        val category = Weapons.category(equipment.weapon(player))
        val ranged = category == Weapons.BOW || category == Weapons.CROSSBOW || category == Weapons.THROWN || category == Weapons.GRENADE
        return if (ranged && player.vars[VARP_COM_MODE] == RAPID_BUTTON) base - 1 else base
    }

    /**
     * Brings the client in line with the equipment after it changed: the worn inventory, the model, the stance and an
     * open stats screen ([updateVars] does the combat tab). Run at the end of every cycle; does nothing when nothing
     * changed.
     */
    fun flush(player: Player) {
        val changed = player.equipment.hasChanges
        sendInventory(player, player.equipment, send)
        if (!changed && player.sentStance != null) return
        updateAppearance(player)
        if (player.modals[FrameSlot.MAINMODAL] == STATS) sendStats(player)
    }

    /**
     * Sets the combat tab's weapon category when the equipment changed, before the cycle's varps are sent, so the
     * varbit reaches the client in the same cycle as the worn inventory. On the test VM (2026-10-28) the varbit a
     * cycle behind left the style buttons unarmed (Punch, Kick, Block) under an axe. Run before [flush].
     */
    fun updateVars(player: Player) {
        if (player.equipment.hasChanges || player.sentStance == null) updateCombatTab(player)
    }

    private fun updateAppearance(player: Player) {
        val appearance = player.infos.playerInfo.avatar.extendedInfo
        // RSProt's appearance has wearpos 0 to 11; the ring and ammo don't show on the model.
        for (wearpos in 0 until APPEARANCE_SLOTS) {
            val type = player.equipment[wearpos]?.let { objTypes[it.id] }
            if (type == null) {
                appearance.setWornObj(wearpos, -1, -1, -1)
            } else {
                // RSProt hides the identity kit or worn item in the covered slots (a full helm's hair and jaw).
                appearance.setWornObj(wearpos, type.id, type.wearpos2, type.wearpos3)
            }
        }
        val stance = Weapons.stance(equipment.weapon(player))
        if (stance != player.sentStance) {
            appearance.setBaseAnimationSet(stance.ready, stance.turn, stance.walk, stance.walkBack, stance.walkLeft, stance.walkRight, stance.run)
            player.sentStance = stance
        }
    }

    /**
     * Sets the combat tab's weapon category. A picked style the new weapon lacks goes back to the first; live's
     * choice there is guessed, not checked.
     */
    internal fun updateCombatTab(player: Player) {
        val weapon = equipment.weapon(player)
        val category = Weapons.category(weapon)
        if (player.vars.types.varbit(VARBIT_WEAPON_CATEGORY) != null) player.vars.setVarbit(VARBIT_WEAPON_CATEGORY, category)
        // A picked spell is kept under a weapon that can't cast it, and autocast is back on with the next staff (OSRS
        // Wiki, Autocast: remembered across weapon changes). Guessed: the menu closes with a weapon that has no spell
        // buttons.
        if (Autocast.active(player, category)) {
            player.vars[VARP_COM_MODE] = Autocast.COM_MODE
        } else if (player.vars[VARP_COM_MODE] !in Weapons.styleButtons(category)) {
            player.vars[VARP_COM_MODE] = 0
        }
        if (player.autocastMenu != null && category !in Autocast.CATEGORIES) {
            closeAutocastMenu(player)
            return
        }
        // No client script writes these two; live sends them. "Unarmed" as the title with no weapon is guessed.
        send(player, IfSetText(COMBAT, COMBAT_TITLE, weapon?.name ?: Weapons.categoryName(Weapons.UNARMED)))
        send(player, IfSetText(COMBAT, COMBAT_CATEGORY, "Category: ${Weapons.categoryName(category)}"))
    }

    /** A click on the combat tab: an attack style button, a spell button or Auto retaliate. */
    fun clickCombatTab(player: Player, component: Int): Boolean {
        val button = STYLE_BUTTONS.indexOf(component)
        if (button >= 0) {
            if (button in Weapons.styleButtons(Weapons.category(equipment.weapon(player)))) {
                // A style picked turns autocast off (from memory of live, not checked).
                Autocast.clear(player)
                player.vars[VARP_COM_MODE] = button
                if (player.modals[FrameSlot.MAINMODAL] == STATS) sendStats(player)
            }
            return true
        }
        if (component == COMBAT_AUTOCAST_NORMAL || component == COMBAT_AUTOCAST_DEFENSIVE) {
            if (Weapons.category(equipment.weapon(player)) in Autocast.CATEGORIES) openAutocastMenu(player, component == COMBAT_AUTOCAST_DEFENSIVE)
            return true
        }
        if (component == COMBAT_RETALIATE) {
            // option_nodef: 1 turns auto retaliate off, as the combat tab's script 325 reads it.
            player.vars[VARP_AUTO_RETALIATE_OFF] = 1 - player.vars[VARP_AUTO_RETALIATE_OFF].coerceIn(0, 1)
            return true
        }
        return false
    }

    /**
     * "Choose spell": the autocast menu takes the combat tab's place. Its onload script lays the spells out by varp 664
     * (spitfire_coord): null for the standard spells, an item for the few weapons with a list of their own, which come
     * later. Its spells are client-made children of autocast:spells, one per enum 1986 key, so their op needs events from
     * the server. Inferred from the 241 client scripts (autocast_init, autocast_setup); the opening itself (in the side
     * panel's place, as an overlay) is guessed.
     */
    private fun openAutocastMenu(player: Player, defensive: Boolean) {
        player.autocastMenu = defensive
        player.vars[VARP_SPITFIRE_COORD] = -1
        // Sent now so the menu's onload script reads it; the end-of-cycle flush resends it.
        send(player, VarpSmall(VARP_SPITFIRE_COORD, -1))
        openSide(player, AUTOCAST)
        send(player, IfSetEventsV2(AUTOCAST, AUTOCAST_SPELLS, 0, AUTOCAST_SLOTS, 0, Interfaces.IF_EVENT_OP1))
    }

    /**
     * A click in the autocast menu: Cancel (slot 0) or a spell (the slot is its enum 1986 key). A spell is picked whatever
     * the player's level and runes (OSRS Wiki, Autocast: since April 2025 it stays selected without them); the cast
     * checks them. Either way the combat tab comes back.
     */
    fun clickAutocastMenu(player: Player, component: Int, sub: Int): Boolean {
        if (component != AUTOCAST_SPELLS) return false
        val defensive = player.autocastMenu ?: return true
        if (sub != 0) {
            val spell = spells.autocast(sub)
            if (spell == null) {
                logger.info { "${player.username} picked autocast slot $sub, which isn't a spell here" }
                return true
            }
            Autocast.set(player, spell, defensive)
        }
        closeAutocastMenu(player)
        return true
    }

    private fun closeAutocastMenu(player: Player) {
        player.autocastMenu = null
        openSide(player, COMBAT)
        updateCombatTab(player)
    }

    /** Opens [interfaceId] in the combat tab's side panel slot, in place of what is there. */
    private fun openSide(player: Player, interfaceId: Int) {
        val frame = player.frame
        send(player, IfOpenSub(frame.interfaceId, frame.component(FrameSlot.SIDE0), interfaceId, Interfaces.OVERLAY))
    }

    private fun signed(value: Int): String = if (value >= 0) "+$value" else "$value"

    /** 50 as "+5.0", -5 as "-0.5". */
    private fun signedTenths(value: Int): String {
        val abs = Math.abs(value)
        return "${if (value >= 0) "+" else "-"}${abs / 10}.${abs % 10}"
    }

    private fun seconds(ticks: Int): String = String.format(Locale.ROOT, "%.1fs", ticks * 0.6)

    companion object {
        /** wornitems, its "View equipment stats" button (equipment) and its slot components slot0 to slot13. */
        const val WORNITEMS = 387
        const val WORNITEMS_VIEW_STATS = 1
        val WORNITEMS_SLOTS = intArrayOf(15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)

        /** The wearpos of each slot component, in the order of [WORNITEMS_SLOTS] and [STATS_SLOTS]: slot0 to slot13. */
        val SLOT_WEARPOS = intArrayOf(
            WearPos.HEAD, WearPos.CAPE, WearPos.NECK, WearPos.WEAPON, WearPos.BODY, WearPos.SHIELD, WearPos.LEGS,
            WearPos.HANDS, WearPos.FEET, WearPos.RING, WearPos.AMMO,
        )

        /** equipment (the stats screen), its slot0 to slot13 and its bonus lines. */
        const val STATS = 84
        val STATS_SLOTS = intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        const val STAB_ATT = 24
        const val SLASH_ATT = 25
        const val CRUSH_ATT = 26
        const val MAGIC_ATT = 27
        const val RANGE_ATT = 28
        const val STAB_DEF = 30
        const val SLASH_DEF = 31
        const val CRUSH_DEF = 32
        const val MAGIC_DEF = 33
        const val RANGE_DEF = 34
        const val MELEE_STRENGTH = 36
        const val RANGED_STRENGTH = 37
        const val MAGIC_DAMAGE = 38
        const val PRAYER = 39
        const val TYPE_MULTIPLIER = 41
        const val SLAYER_MULTIPLIER = 42
        // 241.4 (2026-10-20) moved these up one, from 53 and 54, when it dropped a component before them.
        const val SPEED_BASE = 52
        const val SPEED_ACTUAL = 53

        /** equipment_side and its items component. */
        const val STATS_SIDE = 85
        const val STATS_SIDE_ITEMS = 0

        /** Ops 1 (Equip) and 10 (Examine) in the high event word, where op n is bit n - 1. Guessed. */
        const val STATS_SIDE_EVENTS = (1 shl 0) or (1 shl 9)

        /** interface_inv_init in RSMod's names: builds an inventory grid of items with the given options. */
        const val INV_INIT_SCRIPT = 149

        /** combat_interface's weapon name, its category line, style buttons 0 to 3 and auto retaliate. */
        const val COMBAT = 593
        const val COMBAT_TITLE = 3
        const val COMBAT_CATEGORY = 5
        val STYLE_BUTTONS = intArrayOf(6, 10, 14, 18)
        const val COMBAT_RETALIATE = 32

        /** combat_interface's two "Choose spell" buttons: the shield one (defensive) and the other. */
        const val COMBAT_AUTOCAST_DEFENSIVE = 23
        const val COMBAT_AUTOCAST_NORMAL = 28

        /** autocast, the menu of spells, and its spells layer; the spells are its children 1 to 58, Cancel child 0. */
        const val AUTOCAST = 201
        const val AUTOCAST_SPELLS = 1
        const val AUTOCAST_SLOTS = 58

        /** spitfire_coord, an obj varp: which weapon's spell layout the autocast menu shows, null (-1) for the standard one. */
        const val VARP_SPITFIRE_COORD = 664

        const val VARBIT_WEAPON_CATEGORY = 357
        const val VARP_COM_MODE = 43
        const val VARP_AUTO_RETALIATE_OFF = 172
        const val RAPID_BUTTON = 1

        /** Worn item options 1 to 8 are params 451 to 458. */
        const val PARAM_WORN_OP1 = 451
        const val WORN_OPS = 8

        /** Wearpos 0 to 11 show on the model. */
        const val APPEARANCE_SLOTS = 12

        /**
         * Every component id above with its gameval name in the 241 cache, by interface, so [osrsps.cache.GameFrameCheck]
         * can warn when a revision moves one.
         */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            WORNITEMS to ("wornitems" to buildMap {
                put(WORNITEMS_VIEW_STATS, "equipment")
                WORNITEMS_SLOTS.forEachIndexed { index, component -> put(component, "slot${SLOT_WEARPOS[index]}") }
            }),
            STATS to ("equipment" to buildMap {
                STATS_SLOTS.forEachIndexed { index, component -> put(component, "slot${SLOT_WEARPOS[index]}") }
                putAll(
                    mapOf(
                        STAB_ATT to "stabatt", SLASH_ATT to "slashatt", CRUSH_ATT to "crushatt", MAGIC_ATT to "magicatt",
                        RANGE_ATT to "rangeatt", STAB_DEF to "stabdef", SLASH_DEF to "slashdef", CRUSH_DEF to "crushdef",
                        MAGIC_DEF to "magicdef", RANGE_DEF to "rangedef", MELEE_STRENGTH to "meleestrength",
                        RANGED_STRENGTH to "rangestrength", MAGIC_DAMAGE to "magicdamage", PRAYER to "prayer",
                        TYPE_MULTIPLIER to "typemultiplier", SLAYER_MULTIPLIER to "slayermultiplier",
                        SPEED_BASE to "attackspeedbase", SPEED_ACTUAL to "attackspeedactual",
                    ),
                )
            }),
            STATS_SIDE to ("equipment_side" to mapOf(STATS_SIDE_ITEMS to "items")),
            COMBAT to ("combat_interface" to buildMap {
                put(COMBAT_TITLE, "title")
                put(COMBAT_CATEGORY, "category")
                STYLE_BUTTONS.forEachIndexed { button, component -> put(component, "$button") }
                put(COMBAT_RETALIATE, "retaliate")
                put(COMBAT_AUTOCAST_DEFENSIVE, "autocast_defensive")
                put(COMBAT_AUTOCAST_NORMAL, "autocast_normal")
            }),
            AUTOCAST to ("autocast" to mapOf(AUTOCAST_SPELLS to "spells")),
        )

        const val LEVEL_TOO_LOW = "You are not a high enough level to use this item."
        const val NO_SPACE = "You don't have enough free inventory space to do that."
        private const val CHATTYPE_GAMEMESSAGE = 0
    }
}
