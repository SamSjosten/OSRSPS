package osrsps.game.content

import java.util.Collections
import java.util.WeakHashMap
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.DepositBox
import osrsps.game.dialogue.Lines

/** The parcel service's messages: the first two are the Wiki's (Rionasta); the third is held for a recording. */
object TaiBwoParcelLines : Lines("tbw_parcel", "Rionasta") {
    val CURRENCY = line("currency", "a currency put in the parcel service")
    val RUM = line("rum", "Karamjan rum put in the parcel service")
    val CANT_AFFORD = line("cant_afford", "INFERRED words wanted: an item put in with fewer than 10 trading sticks left")
}

/**
 * Rionasta's parcel service (Karamja step K4.2d; OSRS Wiki: Rionasta and Tai Bwo Wannai Cleanup § Rionasta's Parcel
 * Service, read 2026-11-19): items from the inventory sent to the bank for [FEE] trading sticks each, a stack counted
 * per item. Currencies are refused ([TaiBwoParcelLines.CURRENCY]; the Wiki's list of them, [CURRENCIES], by name), and
 * so is Karamjan rum ([TaiBwoParcelLines.RUM]), and whatever can't be banked. His talk's parcel option or his
 * Send-parcel (op 3) opens it;
 * with fewer than [FEE] sticks he says it costs at least that.
 *
 * Guessed: the screen, which is the bank deposit box ([DepositBox], gated) for the Wiki's own parcel screen, whose ids
 * aren't known; nothing worn sent; as many of a stack sent as the sticks pay for, the rest left; Send-parcel opening it
 * straight away when the player has the sticks; the fee taken from the inventory after the items go.
 */
object TaiBwoParcels {
    const val FEE = 10
    const val SEND_PARCEL = 3

    /** The Wiki's ineligible currencies, by their cache names (the newer ones it says can be sent aren't here). */
    val CURRENCIES = setOf(
        "coins", "ecto-token", "frog token", "glistening tear", "golden nugget", "mark of grace", "mermaid's tear", "minnow",
        "molch pearl", "numulite", "paramaya ticket", "pieces of eight", "platinum token", "reward token", "tokkul",
        "trading sticks", "unidentified minerals", "warrior guild token",
        "agility arena ticket", "archery ticket", "blood money", "castle wars ticket",
    )
    const val KARAMJAN_RUM = "karamjan rum"

    /** Players who asked Rionasta to send a parcel, for the box to open once his talk ends. */
    val wanted: MutableSet<Player> = Collections.newSetFromMap(WeakHashMap())

    private class Gate(private val world: World) : DepositBox.Gate {
        override fun allow(player: Player, id: Int, count: Int): Int {
            val type = world.objTypes[id] ?: return 0
            val name = (if (type.isCert) world.objTypes[type.certlink]?.name else type.name)?.lowercase() ?: return 0
            if (name in CURRENCIES || name.removeSuffix("s") in CURRENCIES) {
                world.message(player, TaiBwoParcelLines.CURRENCY)
                return 0
            }
            if (name == KARAMJAN_RUM) {
                world.message(player, TaiBwoParcelLines.RUM)
                return 0
            }
            val affordable = (player.inventory.count(TaiBwoWannai.TRADING_STICKS) / FEE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (affordable == 0) {
                world.message(player, TaiBwoParcelLines.CANT_AFFORD)
                return 0
            }
            return minOf(count, affordable)
        }

        override fun moved(player: Player, id: Int, count: Int) {
            Ned.take(player, TaiBwoWannai.TRADING_STICKS, count * FEE)
        }
    }

    fun open(world: World, player: Player) = world.depositBox.open(player, Gate(world))
}
