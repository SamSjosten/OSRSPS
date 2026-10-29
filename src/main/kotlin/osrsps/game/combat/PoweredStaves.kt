package osrsps.game.combat

import java.util.Locale
import osrsps.game.EquipmentTab
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext

/**
 * A powered staff: it casts its own built-in spell ([MagicCombat]) with charges it holds ([Player.charges], under
 * [charge]), [runesPerCharge] for each, up to [maxCharges]. Worn in its charged form [charged]; [uncharged] is the same
 * staff with none. The built-in spell's max hit is [maxHit] of the visible Magic level, and for it the worn equipment's
 * magic attack and magic damage bonuses are multiplied by [bonusMultiplier] (1 for none), the damage capped at 100%.
 */
class PoweredStaff(
    val charged: Int,
    val uncharged: Int,
    val charge: String,
    val maxCharges: Int,
    val runesPerCharge: List<RuneCost>,
    val anim: Int,
    val casting: Int,
    val travel: Int,
    val impact: Int,
    val bonusMultiplier: Int,
    val maxHit: (magicLevel: Int) -> Int,
)

/**
 * The powered staves that are in: Tumeken's shadow for now. From the OSRS Wiki (Tumeken's shadow, Powered staff, read
 * 2026-10-29): its spell's max hit is Magic / 3 + 1; it multiplies the worn magic attack and magic damage by 3 for that
 * spell alone (by 4 in the Tombs of Amascut, which aren't in), the damage capped at 100%; it holds up to 20,000 charges
 * of 2 soul runes and 5 chaos runes each, all given back when it is uncharged; the charged staff has Check, Charge and
 * Uncharge, the uncharged one Charge, and worn it has Check. Its graphics are the cache's tumekens_shadow_casting,
 * _travel and _impact.
 *
 * Guessed and not checked on live: that the attack animation is toa_sot_cast_b (from memory of its id) and the stand
 * is toa_sot_ready; every message's wording; that Charge asks how many as a count prompt, which is also what using a
 * soul or chaos rune on it does; that Uncharge needs no confirmation and gives the runes back only when all of them
 * fit; that the charged staff turns into the uncharged one as its last charge goes, as the blowpipe does; and that the
 * charges are the player's, as the blowpipe's are ([osrsps.game.Charges]). Aether runes, which can stand in for soul
 * runes, aren't in.
 */
class PoweredStaves(private val objTypes: ObjTypes) {
    private val staves = listOf(TUMEKENS_SHADOW)

    private val byId: Map<Int, PoweredStaff> = staves.flatMap { listOf(it.charged to it, it.uncharged to it) }.toMap()

    /** The powered staff [id] is, charged or not, or null when it isn't one. */
    fun of(id: Int?): PoweredStaff? = id?.let { byId[it] }

    /** The powered staff [player] wields, or null. */
    fun wielded(player: Player): PoweredStaff? = of(player.equipment[WearPos.WEAPON]?.id)

    /** What Check says. */
    fun check(player: Player, staff: PoweredStaff): String {
        val name = objTypes[staff.charged]?.name ?: "staff"
        return "Your $name has ${number(player.charges[staff.charge])} charges remaining."
    }

    /**
     * What a cast takes: one charge. The last one turns the worn staff into the uncharged one. Returns false, having
     * told the player through [message], when there is none.
     */
    fun use(player: Player, staff: PoweredStaff, message: (String) -> Unit): Boolean {
        val left = player.charges[staff.charge]
        val name = objTypes[staff.charged]?.name ?: "staff"
        if (left == 0) {
            message("Your $name has no charges.")
            return false
        }
        player.charges[staff.charge] = left - 1
        if (left == 1) {
            if (player.equipment[WearPos.WEAPON]?.id == staff.charged) player.equipment[WearPos.WEAPON] = InvObj(staff.uncharged, 1)
            message("Your $name has run out of charges.")
        }
        return true
    }

    fun register(handlers: Handlers, message: (Player, String) -> Unit) {
        for (staff in staves) {
            val charged = objTypes[staff.charged] ?: continue
            val uncharged = objTypes[staff.uncharged]
            option(charged, CHECK)?.let { handlers.onObj(staff.charged, it) { message(player, check(player, staff)) } }
            wornOption(charged, CHECK)?.let { handlers.onWorn(staff.charged, it) { message(player, check(player, staff)) } }
            option(charged, UNCHARGE)?.let { handlers.onObj(staff.charged, it) { op -> uncharge(player, staff, op.sub, message) } }
            for (type in listOfNotNull(charged, uncharged)) {
                option(type, CHARGE)?.let { handlers.onObj(type.id, it) { op -> charge(staff, op.sub, message) } }
                for (cost in staff.runesPerCharge) {
                    handlers.onObjOnObj(cost.rune, type.id) { op -> charge(staff, if (op.obj == cost.rune) op.sub else op.objSlot, message) }
                }
            }
        }
    }

    /**
     * Asks how many charges to add to the staff in [slot] and adds them, as many as the runes carried and the room left
     * allow, taking their runes. The uncharged staff becomes the charged one.
     */
    private suspend fun ScriptContext.charge(staff: PoweredStaff, slot: Int, message: (Player, String) -> Unit) {
        val have = player.charges[staff.charge]
        val most = minOf(afford(player, staff), staff.maxCharges - have)
        if (most <= 0) {
            message(player, if (have >= staff.maxCharges) "It can't hold any more charges." else "You don't have the runes to charge it.")
            return
        }
        val wanted = countDialog("How many charges would you like to add? (0 - ${number(most)})")
        // The runes or the staff may have gone while the prompt was up.
        val held = player.inventory[slot]?.id
        if (held != staff.charged && held != staff.uncharged) return
        val added = minOf(wanted, afford(player, staff), staff.maxCharges - have)
        if (added <= 0) return
        for (cost in staff.runesPerCharge) take(player, cost.rune, cost.count * added)
        player.charges[staff.charge] = have + added
        if (held == staff.uncharged) player.inventory[slot] = InvObj(staff.charged, 1)
        message(player, check(player, staff))
    }

    /** All the runes back, when they fit, and the staff in [slot] uncharged. */
    private fun uncharge(player: Player, staff: PoweredStaff, slot: Int, message: (Player, String) -> Unit) {
        if (player.inventory[slot]?.id != staff.charged) return
        val charges = player.charges[staff.charge]
        val back = staff.runesPerCharge.mapNotNull { cost -> objTypes[cost.rune]?.let { it to cost.count.toLong() * charges } }
        val newStacks = back.count { (type, count) -> count > 0 && player.inventory.count(type.id) == 0L }
        if (back.any { (type, count) -> player.inventory.count(type.id) + count > Inventory.MAX_STACK } || player.inventory.freeSlots < newStacks) {
            message(player, NO_ROOM)
            return
        }
        for ((type, count) in back) if (count > 0) player.inventory.add(type, count.toInt())
        player.charges[staff.charge] = 0
        player.inventory[slot] = InvObj(staff.uncharged, 1)
    }

    /** How many charges the runes carried pay for. */
    private fun afford(player: Player, staff: PoweredStaff): Int =
        staff.runesPerCharge.minOf { player.inventory.count(it.rune) / it.count }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Takes [count] of [rune] from the inventory, from the first stacks on. */
    private fun take(player: Player, rune: Int, count: Int) {
        var left = count
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id == rune) left -= player.inventory.remove(slot, left)
        }
    }

    companion object {
        const val SOUL_RUNE = 566
        const val CHAOS_RUNE = 562

        val TUMEKENS_SHADOW = PoweredStaff(
            charged = 27275,
            uncharged = 27277,
            charge = "tumekens_shadow",
            maxCharges = 20_000,
            runesPerCharge = listOf(RuneCost(SOUL_RUNE, 2), RuneCost(CHAOS_RUNE, 5)),
            anim = 9493,
            casting = 2125,
            travel = 2126,
            impact = 2127,
            bonusMultiplier = 3,
            maxHit = { magic -> magic / 3 + 1 },
        )

        private const val CHECK = "Check"
        private const val CHARGE = "Charge"
        private const val UNCHARGE = "Uncharge"

        const val NO_ROOM = "You don't have enough inventory space."

        private fun number(n: Int) = String.format(Locale.ROOT, "%,d", n)

        /** The inventory option called [name] on [type], 1 to 5, or null when it has none. */
        private fun option(type: ObjType, name: String): Int? =
            type.iops.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)

        /** The worn option called [name], 1 to 8 (params 451 to 458), or null. */
        private fun wornOption(type: ObjType, name: String): Int? =
            (1..EquipmentTab.WORN_OPS).firstOrNull { type.stringParam(EquipmentTab.PARAM_WORN_OP1 + it - 1).equals(name, ignoreCase = true) }
    }
}
