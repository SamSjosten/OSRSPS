package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.bank.getVarbitIfKnown
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The Wilderness boss lairs' fee's words (keys only; the words are in data/dialogue.txt). */
object WildernessLairFeeLines : Lines("wildy_lair_fee", "Wilderness boss lairs") {
    val TITLE = line("title", "the menu before a Wilderness boss lair's way in takes the fee (no source yet)", "fee")
    val YES = line("yes", "the fee menu's option that pays and goes in (no source yet)")
    val NO = line("no", "the fee menu's option that doesn't (no source yet)")
    val NO_COINS = line("no_coins", "a lair's way in with fewer coins than the fee in the backpack and in the bank (no source yet)", "fee")
}

/**
 * The fee for the Wilderness boss lairs (Wilderness step W4.7; the Wiki's Escape Caves, Callisto's Den and Hunter's End,
 * read 2026-11-15): "Entering the boss lairs or the entrances to the Escape Caves requires players to pay a fee of 50,000
 * coins; this fee does not need to be brought along in the inventory, and can be deducted from the bank. This fee is
 * shared across Callisto's Den, Hunter's End, the Skeletal Tomb, Vet'ion's Rest, the Silk Chasm, the Web Chasm and the
 * Escape Caves. The fee does not need to be paid again, unless the player dies to another player without having left
 * the Wilderness since their most recent visit to a boss lair or the Escape Caves. If this happens, the fee is dropped
 * as loot for the killer ... each kill in which the player deals the most damage to it reduces the fee by 10,000 coins."
 *
 * Paid is the cache's `wbr_entrance_fee_paid` and the kills off it `wbr_entrance_discount` (bits 1 to 3 of
 * `wbr_perm_int_1`). Each lair's way in calls [payToEnter] (the lairs come with steps W4.8 to W4.14), each lair boss is a
 * [WildernessBoss] with `lair` set, whose top damage dealer gets [discount], and a PvP death [forfeit]s the fee.
 *
 * INFERRED: the discount a count of top-damage kills (its varbit holds a count, not which bosses), up to
 * [MAX_DISCOUNTS], so the fee goes no lower than 0; the coins dropped for the killer the fee as it stands then; ties
 * for the most damage going to whoever hit first. GUESSED: the menu before paying and its words; the fee from the
 * backpack when it holds it all, else from the bank; no menu at a fee of 0; "left the Wilderness" as standing outside
 * it or in Ferox Enclave; the visit kept in a varp of our own ([CustomVars.lairVisitVarp]). Not built: the fee's
 * warning toggle (`wbr_entrance_fee_warning`).
 */
object WildernessLairFee {
    const val FEE = 50_000
    const val DISCOUNT = 10_000
    const val MAX_DISCOUNTS = FEE / DISCOUNT
    const val COINS = 995

    const val VARBIT_PAID = 14693
    const val VARBIT_DISCOUNT = 14694

    fun discounts(player: Player): Int = player.vars.getVarbitIfKnown(VARBIT_DISCOUNT)

    /** What going in costs [player] now. */
    fun fee(player: Player): Int = maxOf(0, FEE - DISCOUNT * discounts(player))

    fun paid(player: Player): Boolean = player.vars.getVarbitIfKnown(VARBIT_PAID) != 0

    /** Whether [player] has been in a lair since they last left the Wilderness, so a PvP death takes the fee. */
    fun visited(player: Player): Boolean = visitVarp(player)?.let { player.vars[it] != 0 } ?: false

    private fun visitVarp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.LAIR_VISIT]

    private fun setVisited(player: Player, visited: Boolean) {
        val varp = visitVarp(player) ?: return
        player.vars[varp] = if (visited) 1 else 0
    }

    /**
     * A lair's way in: true when [player] may go in, the fee paid already or paid now, after a menu, from the backpack
     * or the bank; false (nothing taken) when they say no or can't pay. Going in counts as a visit.
     */
    suspend fun ScriptContext.payToEnter(world: World): Boolean {
        if (!paid(player)) {
            val fee = fee(player)
            if (fee > 0) {
                if (!canPay(player, fee)) {
                    world.message(player, WildernessLairFeeLines.NO_COINS, "fee" to fee)
                    return false
                }
                var yes = false
                world.dialogues.start(this) {
                    yes = choice(WildernessLairFeeLines.YES, WildernessLairFeeLines.NO, title = text(WildernessLairFeeLines.TITLE, "fee" to fee)) == 1
                }
                if (!yes) return false
                if (!pay(world, player, fee)) {
                    world.message(player, WildernessLairFeeLines.NO_COINS, "fee" to fee)
                    return false
                }
            }
            player.vars.setVarbitIfKnown(VARBIT_PAID, 1)
        }
        setVisited(player, true)
        return true
    }

    fun canPay(player: Player, fee: Int): Boolean = player.inventory.count(COINS) >= fee || player.bank.count(COINS) >= fee

    /** Takes [fee]: from the backpack if it holds it all, else from the bank; false (nothing taken) if neither does. */
    fun pay(world: World, player: Player, fee: Int): Boolean {
        if (player.inventory.count(COINS) >= fee) {
            Ned.take(player.inventory, COINS, fee)
            return true
        }
        if (player.bank.count(COINS) < fee) return false
        world.bank.take(player.bank, world.bankScreen.tabs(player), COINS, fee)
        return true
    }

    /** [player] dealt the most damage in a lair boss's kill: 10,000 off the fee, down to nothing. */
    fun discount(player: Player) {
        val now = discounts(player)
        if (now < MAX_DISCOUNTS) player.vars.setVarbitIfKnown(VARBIT_DISCOUNT, now + 1)
    }

    /** Each tick: out of the Wilderness (or in Ferox Enclave), the last visit no longer puts the fee at risk. */
    fun update(player: Player) {
        if (!visited(player)) return
        if (!Wilderness.inside(player.x, player.z) || (player.level == 0 && Wilderness.inFerox(player.x, player.z))) setVisited(player, false)
    }

    /**
     * [player] died to another player: if the fee is paid and they haven't left the Wilderness since their last visit,
     * it is lost and the coins it stands at go to the killer; returns how many (0 for none).
     */
    fun forfeit(player: Player): Int {
        if (!paid(player) || !visited(player)) return 0
        val coins = fee(player)
        player.vars.setVarbitIfKnown(VARBIT_PAID, 0)
        setVisited(player, false)
        return coins
    }
}
