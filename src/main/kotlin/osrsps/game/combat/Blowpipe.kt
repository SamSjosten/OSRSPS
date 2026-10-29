package osrsps.game.combat

import java.util.Locale
import osrsps.game.EquipmentTab
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.dialogue.DialogueText
import osrsps.game.script.Handlers
import osrsps.game.script.Op

/**
 * The toxic blowpipe: it holds darts and Zulrah's scales ([Player.charges]) and fires those darts, not what is in the
 * ammo slot ([RangedWeapon.loaded]). From the OSRS Wiki (Toxic blowpipe): up to 16,383 of each; it can't be wielded
 * empty, but stays worn when the scales run out; a shot uses one dart (none come back without an Ava's device, which
 * isn't in yet) and a scale two times in three; it has Check, Unload and Uncharge, and the empty one is dismantled for
 * 20,000 scales. Worn, the 241 cache gives it Check alone, though the Wiki lists all three there too; the worn options
 * below follow the cache.
 *
 * Guessed and not checked on live: loading by using darts or scales on it (the Wiki only describes the bank's
 * Configure-Charges, which isn't in), that darts go in the empty one too, every message's wording, that Uncharge needs
 * no confirmation and leaves the empty blowpipe where it was, and that the charges are the player's, shared by two
 * blowpipes ([osrsps.game.Charges]). A death that loses the last blowpipe puts its darts and scales in the grave
 * ([Deaths]). Not in yet: venom and the special attack.
 */
class Blowpipe(
    private val objTypes: ObjTypes,
    private val weapons: RangedWeapons,
    /** Each player's words for the game messages ([CombatLines]). */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
) {
    /** The dart it holds, null when it holds none. */
    fun dart(player: Player): ObjType? = if (player.charges[DARTS] > 0) objTypes[player.charges[DART_TYPE]] else null

    /**
     * What a shot takes: one dart, and a scale unless the one-in-three comes up ([roll] is that draw). The last scale
     * turns a worn blowpipe into the empty one. Returns false, having told the player through [message], when there is
     * nothing to fire.
     */
    fun use(player: Player, message: (String) -> Unit, roll: () -> Boolean): Boolean {
        if (player.charges[SCALES] == 0) {
            message(words(player)[CombatLines.BLOWPIPE_NO_SCALES])
            return false
        }
        if (player.charges[DARTS] == 0) {
            message(words(player)[CombatLines.BLOWPIPE_NO_DARTS])
            return false
        }
        takeDarts(player, 1)
        if (!roll()) player.charges[SCALES] = player.charges[SCALES] - 1
        if (player.charges[SCALES] == 0) {
            val worn = player.equipment[WearPos.WEAPON]
            if (worn?.id == LOADED) player.equipment[WearPos.WEAPON] = InvObj(EMPTY, 1)
            message(words(player)[CombatLines.BLOWPIPE_RAN_OUT])
        }
        return true
    }

    /** What Check says. */
    fun check(player: Player): String {
        val dart = dart(player)
        val scales = player.charges[SCALES]
        val percent = String.format(Locale.ROOT, "%.1f", scales * 100.0 / MAX_SCALES)
        val text = words(player)
        if (dart == null) return text[CombatLines.BLOWPIPE_CHECK_EMPTY, "scales" to number(scales), "percent" to percent]
        return text[CombatLines.BLOWPIPE_CHECK, "dart" to dart.name, "darts" to number(player.charges[DARTS]), "scales" to number(scales), "percent" to percent]
    }

    fun register(handlers: Handlers, message: (Player, String) -> Unit) {
        val loaded = objTypes[LOADED] ?: return
        val empty = objTypes[EMPTY]
        for (blowpipe in listOfNotNull(loaded, empty)) {
            for (id in weapons.ammoOfKind(AmmoKind.DARTS)) {
                val dart = objTypes[id] ?: continue
                handlers.onObjOnObj(dart.id, blowpipe.id) { op -> addDarts(player, op, dart, message) }
            }
            if (objTypes[SCALE] != null) handlers.onObjOnObj(SCALE, blowpipe.id) { op -> addScales(player, op, message) }
        }
        option(loaded, CHECK)?.let { handlers.onObj(LOADED, it) { message(player, check(player)) } }
        option(loaded, UNLOAD)?.let { handlers.onObj(LOADED, it) { unload(player, message) } }
        option(loaded, UNCHARGE)?.let { handlers.onObj(LOADED, it) { op -> uncharge(player, op.sub, message) } }
        wornOption(loaded, CHECK)?.let { handlers.onWorn(LOADED, it) { message(player, check(player)) } }
        wornOption(loaded, UNLOAD)?.let { handlers.onWorn(LOADED, it) { unload(player, message) } }
        wornOption(loaded, UNCHARGE)?.let { handlers.onWorn(LOADED, it) { uncharge(player, -1, message) } }
        if (empty != null) {
            option(empty, DISMANTLE)?.let { handlers.onObj(EMPTY, it) { op -> dismantle(player, op.sub, message) } }
            for (wield in listOf("Wield", "Wear")) option(empty, wield)?.let { handlers.onObj(EMPTY, it) { message(player, words(player)[CombatLines.BLOWPIPE_NO_SCALES]) } }
        }
    }

    private fun addDarts(player: Player, op: Op, dart: ObjType, message: (Player, String) -> Unit) {
        val slot = if (op.obj == dart.id) op.objSlot else op.sub
        val held = player.inventory[slot]?.takeIf { it.id == dart.id } ?: return
        val have = player.charges[DARTS]
        if (have > 0 && player.charges[DART_TYPE] != dart.id) {
            message(player, words(player)[CombatLines.BLOWPIPE_OTHER_DARTS])
            return
        }
        val added = minOf(held.count, MAX_DARTS - have)
        if (added <= 0) {
            message(player, words(player)[CombatLines.BLOWPIPE_FULL_DARTS])
            return
        }
        player.inventory.remove(slot, added)
        player.charges[DART_TYPE] = dart.id
        player.charges[DARTS] = have + added
        message(player, check(player))
    }

    private fun addScales(player: Player, op: Op, message: (Player, String) -> Unit) {
        val scaleSlot = if (op.obj == SCALE) op.objSlot else op.sub
        val pipeSlot = if (op.obj == SCALE) op.sub else op.objSlot
        val held = player.inventory[scaleSlot]?.takeIf { it.id == SCALE } ?: return
        val have = player.charges[SCALES]
        val added = minOf(held.count, MAX_SCALES - have)
        if (added <= 0) {
            message(player, words(player)[CombatLines.BLOWPIPE_FULL_SCALES])
            return
        }
        player.inventory.remove(scaleSlot, added)
        player.charges[SCALES] = have + added
        if (player.inventory[pipeSlot]?.id == EMPTY) player.inventory[pipeSlot] = InvObj(LOADED, 1)
        message(player, check(player))
    }

    /** The darts back into the inventory, as many as fit. */
    private fun unload(player: Player, message: (Player, String) -> Unit) {
        val dart = dart(player)
        if (dart == null) {
            message(player, words(player)[CombatLines.BLOWPIPE_NO_DARTS])
            return
        }
        val back = player.inventory.add(dart, player.charges[DARTS])
        if (back == 0) {
            message(player, words(player)[CombatLines.NO_ROOM])
            return
        }
        takeDarts(player, back)
    }

    /**
     * Darts and scales back into the inventory, and the blowpipe empty in [slot] (-1 for the worn one). Only when both
     * fit, so nothing is lost.
     */
    private fun uncharge(player: Player, slot: Int, message: (Player, String) -> Unit) {
        val dart = dart(player)
        val scale = objTypes[SCALE] ?: return
        val back = listOfNotNull(dart?.let { it to player.charges[DARTS] }, (scale to player.charges[SCALES]).takeIf { it.second > 0 })
        if (!fits(player, back)) {
            message(player, words(player)[CombatLines.NO_ROOM])
            return
        }
        if (dart != null) takeDarts(player, player.inventory.add(dart, player.charges[DARTS]))
        if (player.charges[SCALES] > 0) player.charges[SCALES] = player.charges[SCALES] - player.inventory.add(scale, player.charges[SCALES])
        if (slot >= 0) {
            if (player.inventory[slot]?.id == LOADED) player.inventory[slot] = InvObj(EMPTY, 1)
        } else if (player.equipment[WearPos.WEAPON]?.id == LOADED) {
            player.equipment[WearPos.WEAPON] = InvObj(EMPTY, 1)
        }
    }

    /** The empty blowpipe into its scales, and any darts loaded into it back too (guessed), only when all of them fit. */
    private fun dismantle(player: Player, slot: Int, message: (Player, String) -> Unit) {
        val scale = objTypes[SCALE] ?: return
        if (player.inventory[slot]?.id != EMPTY) return
        val dart = dart(player)
        // The blowpipe's own slot is free once it is gone.
        if (!fits(player, listOfNotNull(scale to DISMANTLE_SCALES, dart?.let { it to player.charges[DARTS] }), freed = 1)) {
            message(player, words(player)[CombatLines.NO_ROOM])
            return
        }
        player.inventory[slot] = null
        player.inventory.add(scale, DISMANTLE_SCALES)
        if (dart != null) takeDarts(player, player.inventory.add(dart, player.charges[DARTS]))
    }

    /**
     * Whether all of [items] go in at once: onto their stacks without passing [Inventory.MAX_STACK], else a free slot
     * each, with [freed] more slots free than now.
     */
    private fun fits(player: Player, items: List<Pair<ObjType, Int>>, freed: Int = 0): Boolean {
        var slots = 0
        for ((type, count) in items) {
            val stack = player.inventory.contents().firstOrNull { it.second.id == type.id }?.second
            when {
                !type.stackable -> slots += count
                stack == null -> slots++
                stack.count.toLong() + count > Inventory.MAX_STACK -> return false
            }
        }
        return player.inventory.freeSlots + freed >= slots
    }

    private fun takeDarts(player: Player, count: Int) {
        val left = player.charges[DARTS] - count
        player.charges[DARTS] = left
        if (left == 0) player.charges[DART_TYPE] = 0
    }

    companion object {
        /** toxic_blowpipe_loaded and toxic_blowpipe, as the 241 cache names them. */
        const val LOADED = 12926
        const val EMPTY = 12924

        /** snakeboss_scale: Zulrah's scales. */
        const val SCALE = 12934

        const val MAX_DARTS = 16_383
        const val MAX_SCALES = 16_383
        const val DISMANTLE_SCALES = 20_000

        // The charge names.
        const val DART_TYPE = "blowpipe_dart"
        const val DARTS = "blowpipe_darts"
        const val SCALES = "blowpipe_scales"

        private const val CHECK = "Check"
        private const val UNLOAD = "Unload"
        private const val UNCHARGE = "Uncharge"
        private const val DISMANTLE = "Dismantle"

        private fun number(n: Int) = String.format(Locale.ROOT, "%,d", n)

        /** The inventory option called [name] on [type], 1 to 5, or null when it has none. */
        private fun option(type: ObjType, name: String): Int? =
            type.iops.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)

        /** The worn option called [name], 1 to 8 (params 451 to 458), or null. */
        private fun wornOption(type: ObjType, name: String): Int? =
            (1..EquipmentTab.WORN_OPS).firstOrNull { type.stringParam(EquipmentTab.PARAM_WORN_OP1 + it - 1).equals(name, ignoreCase = true) }
    }
}
